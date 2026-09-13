/**
 * Assembles the §9 install layout and the artifacts published to the update server.
 *
 * Lives in its own module because it consumes outputs from *both* `:app` and `:updater` and so
 * belongs to neither, and because it gets its own `build/` directory — release artifacts are
 * never mixed into a module's normal outputs.
 *
 * Two things come out of `releaseLinuxX64`, in `build/release/<version>/`:
 *
 *  - `app.zip` — the §9 update package. What the updater downloads and `Replacer` unzips.
 *  - `install-linux-x64.tar.gz` — the whole install layout, for a first-time install on a
 *    machine with no JDK.
 */

plugins {
    base
    // Brings in JavaToolchainService, which jlinkRuntime uses to locate a JDK's jdeps/jlink.
    // `base` alone does not provide it and this module has no Java sources of its own.
    id("jvm-toolchains")
}

val javaToolchains = extensions.getByType<JavaToolchainService>()

val appJar by configurations.creating { isCanBeConsumed = false; isCanBeResolved = true }
val appVersionJson by configurations.creating { isCanBeConsumed = false; isCanBeResolved = true }
val videoNatives by configurations.creating { isCanBeConsumed = false; isCanBeResolved = true }
val updaterBinary by configurations.creating { isCanBeConsumed = false; isCanBeResolved = true }

dependencies {
    appJar(project(path = ":app", configuration = "installAppJar"))
    appVersionJson(project(path = ":app", configuration = "installVersionJson"))
    videoNatives(project(path = ":app", configuration = "installVideoNatives"))
    updaterBinary(project(path = ":updater", configuration = "installUpdaterBinary"))
}

// --------------------------------------------------------------------------------------
// runtime/ — the jlink JRE the app is launched on
// --------------------------------------------------------------------------------------

/**
 * Builds `<install_dir>/runtime` (§9 pt 3).
 *
 * The module list is the union of what `jdeps` can see in the uber jar and an explicit list of
 * what it structurally cannot. That second half is the important one: modules reached through
 * `ServiceLoader` or `Class.forName` are invisible to bytecode analysis, and a missing one does
 * not fail this build — it fails at app launch on the packaged runtime, long after anyone is
 * looking. `jdk.crypto.ec` is the canonical example.
 */
abstract class JlinkRuntimeTask : DefaultTask() {
    @get:InputFile abstract val applicationJar: RegularFileProperty

    @get:Input abstract val extraModules: SetProperty<String>

    @get:Nested abstract val launcher: Property<JavaLauncher>

    @get:OutputDirectory abstract val runtimeDir: DirectoryProperty

    @get:Inject abstract val execOps: ExecOperations

    @get:Inject abstract val fsOps: FileSystemOperations

    @TaskAction
    fun build() {
        val metadata = launcher.get().metadata
        val jdkHome = metadata.installationPath.asFile
        val feature = metadata.languageVersion.asInt().toString()

        val detected = runCatching {
            val captured = java.io.ByteArrayOutputStream()
            execOps.exec {
                commandLine(
                    File(jdkHome, "bin/jdeps").absolutePath,
                    "--print-module-deps",
                    // The uber jar carries optional dependencies that are simply absent; without
                    // this, jdeps fails the whole analysis over them.
                    "--ignore-missing-deps",
                    "--multi-release", feature,
                    applicationJar.get().asFile.absolutePath,
                )
                standardOutput = captured
            }
            captured.toString().trim().split(",").map(String::trim).filter { it.isNotEmpty() }
        }.getOrElse { failure ->
            logger.warn("jdeps could not analyse the uber jar ({}); falling back to the explicit " +
                "module list alone", failure.message)
            emptyList()
        }

        val modules = (detected + extraModules.get()).toSortedSet()
        logger.lifecycle("jlink modules ({} from jdeps + {} explicit): {}",
            detected.size, extraModules.get().size, modules.joinToString(","))

        fsOps.delete { delete(runtimeDir) }   // jlink refuses to write into an existing directory
        execOps.exec {
            commandLine(
                File(jdkHome, "bin/jlink").absolutePath,
                "--add-modules", modules.joinToString(","),
                "--output", runtimeDir.get().asFile.absolutePath,
                "--strip-debug", "--no-header-files", "--no-man-pages",
                "--compress=zip-6",
            )
        }
    }
}

val jlinkRuntime = tasks.register<JlinkRuntimeTask>("jlinkRuntime") {
    group = "share packaging"
    description = "Builds <install_dir>/runtime — a jlink JRE sized to app.jar's actual modules."
    applicationJar.set(layout.file(provider { appJar.singleFile }))
    dependsOn(appJar)
    launcher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    runtimeDir.set(layout.buildDirectory.dir("jlink/runtime"))
    extraModules.set(
        listOf(
            "java.base",
            "jdk.unsupported",  // skiko -> sun.misc.Unsafe
            "java.desktop",     // AWT/Swing for the Compose window, javax.sound for audio
            "java.logging",
            "java.naming",      // logback context selector
            "java.management",  // logback JMX configurator
            "java.sql",         // referenced by logback-core's db package
            "java.xml",         // logback.xml parsing
            "java.prefs",
            "java.net.http",
            "jdk.crypto.ec",    // SunEC / TLS ECDHE — ServiceLoader-provided, invisible to jdeps
            "jdk.charsets",
        )
    )
}

// --------------------------------------------------------------------------------------
// The install layout
// --------------------------------------------------------------------------------------

val generateUpdaterProperties = tasks.register("generateUpdaterProperties") {
    group = "share packaging"
    description = "Writes <install_dir>/updater.properties from the root updateEndpoint property."
    val endpoint = providers.gradleProperty("updateEndpoint")
    val out = layout.buildDirectory.file("generated/updater.properties")
    inputs.property("endpoint", endpoint)
    outputs.file(out)
    doLast {
        out.get().asFile.writeText(
            "# <install_dir>/updater.properties — read by UpdaterConfig (§9).\n" +
                "endpoint=${endpoint.get()}\n"
        )
    }
}

/**
 * Seeds `<install_dir>/installed.json` so a fresh install already knows what it has.
 *
 * Without it the updater's ledger is empty on first run, every component reads as stale, and a
 * brand-new install re-downloads ~140 MB it already has on disk.
 */
val generateInstalledState = tasks.register("generateInstalledState") {
    group = "share packaging"
    description = "Writes the updater's installed.json for a fresh install."
    val out = layout.buildDirectory.file("generated/installed.json")
    val components = mapOf(
        "app" to ("app" to packageAppZip.flatMap { it.archiveFile }),
        "runtime" to ("runtime" to packageRuntimeZip.flatMap { it.archiveFile }),
        "ffmpeg" to ("native/ffmpeg/$platformToken" to packageFfmpegZip.flatMap { it.archiveFile }),
    )
    inputs.property("release", releaseVersion)
    inputs.files(components.values.map { it.second })
    outputs.file(out)
    doLast {
        // The same identity the updater compares against: the sha256 of the artifact, not a version.
        fun sha256(file: File): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        val at = java.time.Instant.now().toString()
        val entries = components.entries.joinToString(",\n") { (id, pair) ->
            val (target, artifact) = pair
            """    "$id": {"target":"$target","checksum":"${sha256(artifact.get().asFile)}","installedAt":"$at"}"""
        }
        out.get().asFile.writeText(
            """{
  "version": 1,
  "release": "$releaseVersion",
  "components": {
$entries
  },
  "failedReleases": []
}
"""
        )
    }
}

val stageInstallDir = tasks.register<Sync>("stageInstallDir") {
    group = "share packaging"
    description = "Assembles the §9 install layout for linux-x64 under build/install/linux-x64."
    into(layout.buildDirectory.dir("install/linux-x64"))

    from(updaterBinary) { filePermissions { unix("0755") } }  // -> updater
    from(generateUpdaterProperties)                           // -> updater.properties
    from(generateInstalledState)                              // -> installed.json
    from(jlinkRuntime.flatMap { it.runtimeDir }) {
        into("runtime")
        // Map each file's mode from its source rather than taking a blanket setting: bin/java and
        // the .so files must stay executable, while jlink writes legal/* as read-only (0444),
        // which a second Sync into the same destination cannot overwrite in place.
        eachFile { permissions { unix(if (file.canExecute()) "0755" else "0644") } }
    }
    from(appJar) { into("app"); rename { "app.jar" } }
    from(appVersionJson) { into("app") }                      // -> app/version.json
    // The directory name is bytedeco's classifier, which is also what HostOs.nativeDirName
    // resolves to at runtime. Do not "normalise" it to Compose's linux-x64 target id — these are
    // two different naming schemes and FfmpegBinaryLocator looks up this one.
    from(videoNatives) {
        into("native/ffmpeg")
        include("linux-x86_64/**")
        eachFile { permissions { unix(if (file.canExecute()) "0755" else "0644") } }
    }

    doFirst {
        // Defensive: an install staged before the permission mapping above still holds read-only
        // legal/ files, and Sync overwrites in place rather than replacing.
        destinationDir.walkTopDown().filter { it.isFile && !it.canWrite() }.forEach { it.setWritable(true) }
    }

    // data/ is deliberately NOT shipped: DefaultAppDirectories creates config/, sessions/ and
    // logs/ under <user.dir>/data on first run, and §9 pt 5 forbids the updater from touching it.
    // Shipping the directory would be the one way to accidentally ship state with a release.

    doLast {
        // Copy preserves source unix modes, but an install whose updater is not executable fails
        // in a confusing way much later, so assert it here.
        listOf("updater", "runtime/bin/java", "native/ffmpeg/linux-x86_64/ffmpeg").forEach { path ->
            val file = destinationDir.resolve(path)
            check(file.canExecute()) { "$file is missing its executable bit" }
        }
    }
}

// --------------------------------------------------------------------------------------
// Release artifacts
// --------------------------------------------------------------------------------------

/** The platform token the updater asks for, and the directory name FfmpegBinaryLocator looks
 * under at runtime. Bytedeco's classifier vocabulary — not Compose's `linux-x64` target id. */
val platformToken = "linux-x86_64"

/** A release may bump without the app bumping (a natives-only release), which is exactly what
 * keeps app.zip byte-identical so clients skip re-downloading it. */
val releaseVersion: String = providers.gradleProperty("releaseVersion")
    .orElse(provider { project.version.toString() }).get()

val releaseDir: Provider<Directory> =
    layout.buildDirectory.dir("release/$releaseVersion")

/**
 * The §9 update package: exactly `app.jar` + `version.json` **at the archive root**.
 *
 * `Replacer.unzip` resolves each entry name directly against `<install_dir>/app/`, so an `app/`
 * prefix inside the zip would land the jar at `<install_dir>/app/app/app.jar`. `Replacer.replace`
 * would still report success and the app would simply stop launching after the first update, with
 * nothing in any log to say why — hence the assertion below rather than a comment.
 */
/** Every component zip must unpack *into* its target, so its entries live at the archive root. */
fun assertRootEntries(archive: File, mustContain: String, forbiddenPrefix: String) {
    val names = java.util.zip.ZipFile(archive).use { zip -> zip.entries().asSequence().map { it.name }.toList() }
    check(names.any { it == mustContain }) {
        "${archive.name} must contain '$mustContain' at its root, got: ${names.take(10)}"
    }
    check(names.none { it.startsWith(forbiddenPrefix) }) {
        "${archive.name} entries must be at the archive root — the updater unzips a component " +
            "straight into its target directory, so a '$forbiddenPrefix' prefix would nest it twice"
    }
}

val packageAppZip = tasks.register<Zip>("packageAppZip") {
    group = "share packaging"
    description = "Builds the §9 update package (app.jar + version.json)."
    archiveFileName.set("app.zip")
    destinationDirectory.set(releaseDir)
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
    from(appJar) { rename { "app.jar" } }
    from(appVersionJson)

    doLast {
        val entries = java.util.zip.ZipFile(archiveFile.get().asFile).use { zip ->
            zip.entries().asSequence().map { it.name }.toSortedSet()
        }
        check(entries == sortedSetOf("app.jar", "version.json")) {
            "app.zip must contain exactly app.jar and version.json at its root " +
                "(Replacer.unzip resolves entry names straight into <install_dir>/app/), got: $entries"
        }
    }
}

val packageInstallBundle = tasks.register<Tar>("packageInstallBundle") {
    group = "share packaging"
    description = "Builds the self-contained first-install bundle for linux-x64."
    archiveFileName.set("install-linux-x64.tar.gz")
    destinationDirectory.set(releaseDir)
    compression = Compression.GZIP
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
    from(stageInstallDir) {
        into("share-${project.version}")
        // Archive tasks normalise permissions for reproducibility, which would ship a
        // non-executable updater and runtime/bin/java — the tar is what a user actually unpacks,
        // so the modes have to be carried across explicitly.
        eachFile { permissions { unix(if (file.canExecute()) "0755" else "0644") } }
    }
}

/**
 * Writes a `.sha256` sidecar next to each artifact, in `sha256sum(1)` format so `sha256sum -c`
 * verifies it verbatim on both ends of the upload. The server serves the sidecar's value as the
 * checksum the updater verifies against.
 */
abstract class Sha256SidecarTask : DefaultTask() {
    @get:InputFiles abstract val artifacts: ConfigurableFileCollection

    @get:OutputFiles
    val sidecars: Provider<List<File>>
        get() = artifacts.elements.map { files -> files.map { File(it.asFile.path + ".sha256") } }

    @TaskAction
    fun write() {
        artifacts.forEach { file ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            File(file.path + ".sha256").writeText("$hex  ${file.name}\n")
            logger.lifecycle("$hex  ${file.name}")
        }
    }
}

/**
 * The runtime component. Entries sit at the archive ROOT because the updater unzips a component
 * straight into its target directory — an enclosing `runtime/` folder would produce
 * `<install_dir>/runtime/runtime/bin/java`.
 */
val packageRuntimeZip = tasks.register<Zip>("packageRuntimeZip") {
    group = "share packaging"
    description = "Builds the runtime component (the jlink JRE)."
    archiveFileName.set("runtime-$platformToken.zip")
    destinationDirectory.set(releaseDir)
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
    from(jlinkRuntime.flatMap { it.runtimeDir })
    doLast { assertRootEntries(archiveFile.get().asFile, mustContain = "bin/java", forbiddenPrefix = "runtime/") }
}

/** The ffmpeg component: the contents of one platform directory, again at the archive root. */
val packageFfmpegZip = tasks.register<Zip>("packageFfmpegZip") {
    group = "share packaging"
    description = "Builds the ffmpeg component for $platformToken."
    archiveFileName.set("ffmpeg-$platformToken.zip")
    destinationDirectory.set(releaseDir)
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
    from(provider { videoNatives.singleFile.resolve(platformToken) })
    dependsOn(videoNatives)
    doLast { assertRootEntries(archiveFile.get().asFile, mustContain = "ffmpeg", forbiddenPrefix = "$platformToken/") }
}

/**
 * What this release contains, for the server to serve. Deliberately carries no URLs or checksums:
 * only the server knows the URL it is reached on, and it already reads the `.sha256` sidecars —
 * keeping one source for each fact.
 */
val generateReleaseManifest = tasks.register("generateReleaseManifest") {
    group = "share packaging"
    description = "Writes manifest-$platformToken.json describing this release's components."
    val out = releaseDir.map { it.file("manifest-$platformToken.json") }
    val appArtifact = packageAppZip.flatMap { it.archiveFileName }
    val runtimeArtifact = packageRuntimeZip.flatMap { it.archiveFileName }
    val ffmpegArtifact = packageFfmpegZip.flatMap { it.archiveFileName }
    inputs.property("release", releaseVersion)
    inputs.property("platform", platformToken)
    inputs.property("artifacts", provider { listOf(appArtifact.get(), runtimeArtifact.get(), ffmpegArtifact.get()) })
    outputs.file(out)
    doLast {
        val components = listOf(
            Triple("app", "app", appArtifact.get()),
            Triple("runtime", "runtime", runtimeArtifact.get()),
            Triple("ffmpeg", "native/ffmpeg/$platformToken", ffmpegArtifact.get()),
        ).joinToString(",\n") { (id, target, artifact) ->
            """    {"id":"$id","target":"$target","artifact":"$artifact"}"""
        }
        out.get().asFile.writeText(
            """{
  "version": 1,
  "release": "$releaseVersion",
  "platform": "$platformToken",
  "components": [
$components
  ]
}
"""
        )
    }
}

val releaseChecksums = tasks.register<Sha256SidecarTask>("releaseChecksums") {
    group = "share packaging"
    description = "Writes .sha256 sidecars for the release artifacts."
    artifacts.from(packageAppZip, packageRuntimeZip, packageFfmpegZip, packageInstallBundle, generateReleaseManifest)
}

tasks.register("releaseLinuxX64") {
    group = "share packaging"
    description = "Builds build/release/<version>/{app.zip,install-linux-x64.tar.gz} + sidecars."
    dependsOn(releaseChecksums)
}
