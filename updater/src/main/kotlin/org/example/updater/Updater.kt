package org.example.updater

import kotlinx.serialization.json.Json
import org.example.shared.model.AppVersion
import org.example.updater.model.InstalledState
import org.example.updater.model.MarkedComponent
import org.example.updater.model.UpdateMarker
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Orchestrates the §9 flow: startup recovery → version check → (download, verify, swap) → launch.
 *
 * A release is a set of components; only those whose artifact checksum differs from what the
 * ledger recorded are fetched, so a release that only changes ffmpeg does not re-download the app.
 *
 * Two rules shape everything here:
 *
 * 1. **Nothing is swapped until every payload is downloaded and verified.** Otherwise an `app.jar`
 *    could land while the runtime it needs never arrives.
 * 2. **Every exit path ends in exactly one [launcher] call** — §9 pt 4 and §11: unreachable
 *    server, failed download, bad checksum and failed swap all fall back to launching whatever is
 *    installed, never leaving the user stranded.
 */
class Updater(
    private val layout: InstallLayout,
    private val fetcher: VersionFetcher,
    private val downloader: Downloader,
    private val launcher: AppLauncher,
    private val replacer: Replacer,
    private val log: UpdaterLog,
    private val stateStore: InstalledStateStore = InstalledStateStore(layout.stateFile, log),
    private val now: () -> Instant = Instant::now,
    private val usableSpace: (Path) -> Long = { Files.getFileStore(it).usableSpace },
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun run() {
        recoverFromPreviousRun()
        planUpdate()?.let { applyUpdate(it) }
        launcher.launch(layout)
    }

    // -- recovery ---------------------------------------------------------------------------

    /**
     * §11 "app failed to launch post-update ⇒ restore backup on next run".
     *
     * The marker is written *before* the first swap, so its presence means "an apply was in
     * flight" — covering both a swap that finished but was never confirmed by a launch, and one
     * that died partway. Both want the same remedy.
     */
    private fun recoverFromPreviousRun() {
        // Nothing half-downloaded may survive a run boundary.
        runCatching { replacer.discard(layout.stagingDir) }

        val marker = readMarker()
        var state = stateStore.load()
        var restoredAny = false

        for (marked in recoverableComponents(marker, state)) {
            val target = ComponentTarget.resolve(layout.installDir, marked.target) ?: continue
            val backup = replacer.backupPathFor(target)
            val namedByMarker = marker?.components?.any { it.target == marked.target } == true
            when (BackupDecision.decide(namedByMarker, Files.exists(backup))) {
                StartupRecoveryAction.RESTORE_BACKUP -> {
                    log.warn("Unconfirmed apply of ${marked.id}: restoring ${marked.target}")
                    replacer.restore(target, backup)
                    restoredAny = true
                    state = revertLedger(state, marked)
                }
                StartupRecoveryAction.DELETE_STALE_BACKUP -> {
                    log.info("Deleting stale backup for ${marked.target} left by a confirmed update")
                    replacer.discard(backup)
                }
                StartupRecoveryAction.NONE -> {
                    // Named by the marker but nothing to restore: the component had no previous
                    // version, so the new one stays. Drop the ledger entry so it is re-evaluated.
                    if (namedByMarker) state = revertLedger(state, marked)
                }
            }
        }

        if (marker != null) {
            if (restoredAny) {
                log.warn("Release ${marker.release} never confirmed a launch; it will not be retried")
            }
            // Blacklisted whether or not anything was restorable: the launch was not confirmed.
            state = state.withFailedRelease(marker.release).copy(release = marker.previousRelease)
            stateStore.save(state)
            runCatching { Files.deleteIfExists(layout.markerFile) }
        }
    }

    /** Components recovery should examine: those the marker names, plus any the ledger knows about
     * (to catch a stale backup left by an older, confirmed update). */
    private fun recoverableComponents(marker: UpdateMarker?, state: InstalledState): List<MarkedComponent> {
        val fromMarker = marker?.components.orEmpty()
        val fromLedger = state.components
            .filterKeys { id -> fromMarker.none { it.id == id } }
            .map { (id, c) -> MarkedComponent(id = id, target = c.target, previousChecksum = c.checksum) }
        // An install predating the ledger knows nothing; `app/` is the only target it can have.
        val legacy = if (fromMarker.isEmpty() && fromLedger.isEmpty()) {
            listOf(MarkedComponent(id = "app", target = "app"))
        } else {
            emptyList()
        }
        return fromMarker + fromLedger + legacy
    }

    private fun revertLedger(state: InstalledState, marked: MarkedComponent): InstalledState =
        if (marked.previousChecksum == null) {
            state.copy(components = state.components - marked.id)
        } else {
            state.withComponent(marked.id, marked.target, marked.previousChecksum, now().toString())
        }

    // -- planning ---------------------------------------------------------------------------

    private fun planUpdate(): UpdatePlan? {
        val state = stateStore.load()
        val response = when (val result = fetcher.fetchLatest()) {
            is VersionCheckResult.Unreachable -> return null
            is VersionCheckResult.Available -> result.response
        }

        val remote = AppVersion.parse(response.release)
        if (remote == null) {
            log.warn("Malformed remote release '${response.release}'; treating as not newer")
            return null
        }
        val installed = state.release?.let { AppVersion.parse(it) }
        if (installed != null && remote <= installed) return null
        if (response.release in state.failedReleases) {
            log.warn("Release ${response.release} previously failed to launch; skipping it")
            return null
        }
        if (response.components.isEmpty()) {
            log.warn("Release ${response.release} lists no components; nothing to do")
            return null
        }

        val planned = mutableListOf<PlannedComponent>()
        for (descriptor in response.components) {
            val target = ComponentTarget.resolve(layout.installDir, descriptor.target)
            if (target == null) {
                // Abort the whole release rather than apply part of it: a rejected component may
                // be exactly the one the others depend on.
                log.error("Release ${response.release} names an invalid target " +
                    "'${descriptor.target}' for component '${descriptor.id}'; ignoring the release")
                return null
            }
            val current = state.checksumOf(descriptor.id)
            if (current == descriptor.checksum && Files.isDirectory(target)) continue
            planned += PlannedComponent(
                descriptor = descriptor,
                target = target,
                backup = replacer.backupPathFor(target),
                previousChecksum = current,
            )
        }

        if (planned.isEmpty()) {
            log.info("Release ${response.release} matches what is installed; nothing to download")
            return null
        }
        // `app` last: if a swap fails it is the component most likely to be needed to launch at all.
        return UpdatePlan(response.release, planned.sortedBy { it.id == "app" })
    }

    // -- applying ---------------------------------------------------------------------------

    private fun applyUpdate(plan: UpdatePlan) {
        val names = plan.components.joinToString(", ") { it.id }
        log.info("Release ${plan.release}: updating $names")

        val free = runCatching { usableSpace(layout.installDir) }.getOrDefault(Long.MAX_VALUE)
        if (free < REQUIRED_FREE_BYTES) {
            log.error("Only $free bytes free at ${layout.installDir}; need $REQUIRED_FREE_BYTES. Skipping the update")
            return
        }

        try {
            if (!acquire(plan)) return
            commit(plan)
        } finally {
            runCatching { replacer.discard(layout.stagingDir) }
        }
    }

    /** Downloads and verifies every payload. Nothing is swapped until this returns true. */
    private fun acquire(plan: UpdatePlan): Boolean {
        Files.createDirectories(layout.stagingDir)
        for (component in plan.components) {
            val payload = layout.stagingDir.resolve(component.payloadName)
            log.info("Downloading ${component.id} from ${component.descriptor.url}")
            if (!downloader.download(component.descriptor.url, payload)) {
                log.warn("Download of ${component.id} failed; applying nothing")
                return false
            }
            if (!Sha256.matches(payload, component.descriptor.checksum)) {
                log.warn("Checksum mismatch for ${component.id}; applying nothing")
                return false
            }
        }
        return true
    }

    /** Swaps every component, rolling all of them back if any one fails. */
    private fun commit(plan: UpdatePlan) {
        writeMarker(plan)
        val swapped = mutableListOf<Pair<PlannedComponent, Boolean>>() // component to hadPrevious
        // The component being swapped right now is not yet in `swapped`, but its directory has
        // already been moved aside — so a failure mid-install has to undo it too, or the target is
        // left holding a partial unzip with its backup stranded.
        var inFlight: Pair<PlannedComponent, Boolean>? = null
        try {
            for (component in plan.components) {
                val hadPrevious = replacer.moveAside(component.target, component.backup)
                inFlight = component to hadPrevious
                replacer.install(component.target, layout.stagingDir.resolve(component.payloadName))
                swapped += component to hadPrevious
                inFlight = null
            }
        } catch (e: IOException) {
            log.error("Swap of release ${plan.release} failed; rolling back", e)
            rollback(listOfNotNull(inFlight) + swapped.asReversed())
            runCatching { Files.deleteIfExists(layout.markerFile) }
            return
        }

        var state = stateStore.load().copy(release = plan.release)
        val at = now().toString()
        for ((component, _) in swapped) {
            state = state.withComponent(component.id, component.descriptor.target, component.descriptor.checksum, at)
        }
        stateStore.save(state)
        log.info("Release ${plan.release} applied; awaiting confirmed launch")
    }

    /** [order] is already the order to undo in: the in-flight component first, then the completed
     * ones most-recent-first. */
    private fun rollback(order: List<Pair<PlannedComponent, Boolean>>) {
        for ((component, hadPrevious) in order) {
            runCatching {
                if (hadPrevious) {
                    replacer.restore(component.target, component.backup)
                } else {
                    // Nothing was there before, so the rollback is to remove what we just put in.
                    replacer.discard(component.target)
                }
            }.onFailure { log.error("Could not roll back ${component.id}", it) }
        }
    }

    // -- marker -----------------------------------------------------------------------------

    private fun readMarker(): UpdateMarker? {
        if (!Files.isRegularFile(layout.markerFile)) return null
        return runCatching {
            json.decodeFromString(UpdateMarker.serializer(), Files.readString(layout.markerFile))
        }.getOrElse {
            log.warn("Could not parse ${layout.markerFile}: ${it.describe()}")
            // A marker we cannot read still means an apply was in flight; treat it as one with no
            // components so the release is not blindly re-applied.
            UpdateMarker(release = "", appliedAt = "")
        }
    }

    private fun writeMarker(plan: UpdatePlan) {
        val marker = UpdateMarker(
            release = plan.release,
            appliedAt = now().toString(),
            previousRelease = stateStore.load().release,
            components = plan.components.map {
                MarkedComponent(it.id, it.descriptor.target, it.previousChecksum)
            },
        )
        Files.writeString(layout.markerFile, json.encodeToString(UpdateMarker.serializer(), marker))
    }

    private companion object {
        /** A full release is roughly 200 MB of payloads plus the same again unpacked. One flat
         * figure rather than per-component arithmetic: the point is to refuse obviously doomed
         * updates before touching anything, not to predict usage exactly. */
        const val REQUIRED_FREE_BYTES = 1_000_000_000L
    }
}
