package org.example.app.ui.previews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.app.domain.DefaultCoroutineDispatchers
import org.example.app.domain.video.VideoCaptureFormat
import org.example.app.domain.video.VideoInputDevice
import org.example.app.domain.video.VideoRecorderState
import org.example.app.infrastructure.video.FfmpegBinaryLocator
import org.example.app.infrastructure.video.FfmpegDeviceEnumerator
import org.example.app.infrastructure.video.FfmpegSessionVideoRecorder
import org.example.app.navigation.TaskComponent
import org.example.app.navigation.TaskScreenState
import org.example.app.ui.VideoTaskBody
import org.example.app.ui.theme.ShareTheme
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

/**
 * The VIDEO task body over a **real camera**, with the production [FfmpegSessionVideoRecorder]
 * and [FfmpegDeviceEnumerator] — `./gradlew :app:previewCamera`, or under Compose Hot Reload:
 *
 * ```
 * ./gradlew :app:hotRun --mainClass=org.example.app.ui.previews.CameraLiveHarnessKt --auto
 * ```
 *
 * `hotRun` rather than `hotDev` because the hot-reload MCP server only watches the `main` run
 * scope, which is also why this harness lives in `main` and not in `app/src/dev` (see
 * `docs/dev/hot-reload.md`).
 *
 * Why it exists: camera faults on this path are quiet. Negotiation walks several modes, every
 * failure is a log line rather than a throw, and a capture that delivers frames far faster than
 * the requested rate starves the UI thread instead of reporting anything. So the harness carries
 * a stats panel, and **also logs the same numbers once a second** — when the UI is starved the
 * console is the only thing still moving.
 *
 * It builds no `AppContainer`, so it takes no `app/data/app.lock` and can run beside the app.
 */
fun main() = singleWindowApplication(title = "Camera — live harness") {
    ShareTheme { CameraLive() }
}

private val dispatchers = DefaultCoroutineDispatchers()

/** Outlives composition so a recorder swapped out by the format picker can still be closed. */
private val harnessScope = CoroutineScope(SupervisorJob() + dispatchers.default)

@Composable
private fun CameraLive() {
    var devices by remember { mutableStateOf<List<VideoInputDevice>?>(null) }
    var selected by remember { mutableStateOf<VideoInputDevice?>(null) }
    var requested by remember { mutableStateOf(FORMAT_CHOICES.first()) }

    // Never on the EDT: `availableDevices` spawns ffmpeg and blocks until it exits, which is
    // exactly the call the app makes from a Decompose childFactory.
    LaunchedEffect(Unit) {
        val found = withContext(dispatchers.io) { FfmpegDeviceEnumerator().availableDevices() }
        devices = found
        selected = found.firstOrNull { it.eligible } ?: found.firstOrNull()
        logger.info { "harness enumerated ${found.size} camera(s): ${found.joinToString { it.name }}" }
    }

    // Keyed on the requested format because it is a constructor argument. The outgoing recorder
    // is stopped off the composition so a format switch cannot leave a camera open.
    val recorder = remember(requested) {
        FfmpegSessionVideoRecorder(dispatchers, FfmpegBinaryLocator(), requested.format)
    }
    DisposableEffect(recorder) { onDispose { harnessScope.launch { recorder.stop() } } }

    // `-Dharness.autostart=true` (`./gradlew :app:previewCamera -Pautostart`) opens the camera as
    // soon as one is found, so the harness can be driven from a terminal — the stats it logs every
    // second are then enough to judge a capture without clicking anything.
    LaunchedEffect(recorder, selected) {
        val device = selected
        if (device != null && System.getProperty("harness.autostart").toBoolean()) {
            logger.info { "harness autostart: opening '${device.name}'" }
            recorder.startPreview(device)
        }
    }

    val state by recorder.state.collectAsState()
    val captureFormat by recorder.captureFormat.collectAsState()
    val framesWritten by recorder.framesWritten.collectAsState()
    val throughput = rememberThroughput(recorder)
    val frameClock = rememberFrameClockLag()

    var recordingTo by remember { mutableStateOf<Path?>(null) }
    // Lets the harness put the JPEG decode back on the UI thread on demand: that is the shape the
    // preview had when the VIDEO screen locked up, and the only way to check it stays fixed.
    var decodeOnEdt by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Camera:", style = MaterialTheme.typography.labelLarge)
            when (val list = devices) {
                null -> Text("enumerating…")
                else -> if (list.isEmpty()) {
                    Text("no camera found")
                } else {
                    list.forEach { device ->
                        FilterChip(
                            selected = device.id == selected?.id,
                            onClick = { selected = device },
                            label = { Text(device.name) },
                            modifier = Modifier.testTag("harness.camera.${device.platformIndex}"),
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Request:", style = MaterialTheme.typography.labelLarge)
            FORMAT_CHOICES.forEach { choice ->
                FilterChip(
                    selected = choice == requested,
                    onClick = { requested = choice },
                    label = { Text(choice.label) },
                    modifier = Modifier.testTag("harness.format.${choice.label}"),
                )
            }
            FilterChip(
                selected = decodeOnEdt,
                onClick = { decodeOnEdt = !decodeOnEdt },
                label = { Text("decode on EDT") },
                modifier = Modifier.testTag("harness.decodeOnEdt"),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { selected?.let { device -> scope.launch { recorder.startPreview(device) } } },
                enabled = selected != null,
                modifier = Modifier.testTag("harness.startPreview"),
            ) { Text("Start preview") }

            Button(
                onClick = { scope.launch { recorder.stop() } },
                modifier = Modifier.testTag("harness.stopPreview"),
            ) { Text("Stop preview") }

            Button(
                onClick = {
                    val file = tempCaptureFile()
                    recordingTo = file
                    scope.launch { recorder.startRecording(file) }
                },
                enabled = state == VideoRecorderState.Previewing,
                modifier = Modifier.testTag("harness.startRecording"),
            ) { Text("Start recording") }

            Button(
                onClick = { scope.launch { recorder.stopRecording() } },
                enabled = state == VideoRecorderState.Recording,
                modifier = Modifier.testTag("harness.stopRecording"),
            ) { Text("Stop recording") }
        }

        Text(
            text = buildString {
                appendLine("state          $state")
                appendLine("negotiated     ${captureFormat?.let { "${it.width}x${it.height}@${it.fps}" } ?: "—"}")
                appendLine("preview rate   ${throughput.fps} fps   ${throughput.megabytesPerSecond} MB/s")
                appendLine("frame clock    ${frameClock.lastMillis} ms   worst ${frameClock.worstMillis} ms")
                appendLine("frames written $framesWritten${recordingTo?.let { "  ->  $it (${sizeOf(it)})" } ?: ""}")
                append("ffmpeg         ${FfmpegBinaryLocator().locate() ?: "not found"}")
            },
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.testTag(HARNESS_STATS),
        )

        Box(Modifier.fillMaxWidth().weight(1f)) {
            VideoTaskBody(
                content = TaskComponent.Content.Video(
                    screenState = if (state == VideoRecorderState.Recording) {
                        TaskScreenState.Capturing
                    } else {
                        TaskScreenState.Idle
                    },
                    takeNumber = 1,
                    frames = recorder.previewFrames,
                    decodeDispatcher = if (decodeOnEdt) dispatchers.main else dispatchers.default,
                    ready = state == VideoRecorderState.Previewing || state == VideoRecorderState.Recording,
                    ptzAvailable = false,
                    zoom = remember { MutableStateFlow(null) },
                    error = (state as? VideoRecorderState.Failed)?.error,
                ),
                onPtz = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** What [rememberThroughput] publishes; all three values are refreshed once a second. */
private data class Throughput(val fps: Int, val megabytesPerSecond: String)

/**
 * Counts what the recorder actually delivers.
 *
 * The collector runs on the `default` dispatcher, not the composition's: a capture that
 * overwhelms the UI thread has to stay measurable, and a counter sharing the thread it is
 * measuring would simply stop along with it.
 */
@Composable
private fun rememberThroughput(recorder: FfmpegSessionVideoRecorder): Throughput {
    var published by remember(recorder) { mutableStateOf(Throughput(0, "0.00")) }

    LaunchedEffect(recorder) {
        val frames = AtomicLong()
        val bytes = AtomicLong()
        launch(dispatchers.default) {
            recorder.previewFrames.collect { jpeg ->
                if (jpeg != null) {
                    frames.incrementAndGet()
                    bytes.addAndGet(jpeg.size.toLong())
                }
            }
        }
        launch(dispatchers.default) {
            while (true) {
                delay(1_000)
                val fps = frames.getAndSet(0)
                val megabytes = bytes.getAndSet(0) / 1_048_576.0
                val next = Throughput(fps.toInt(), "%.2f".format(megabytes))
                published = next
                // Logged as well as shown: when the UI thread is starved this is the only
                // surviving evidence of what the camera is doing.
                logger.info { "harness preview: ${next.fps} fps, ${next.megabytesPerSecond} MB/s" }
            }
        }
    }

    return published
}

/** Latest and worst interval between composition frames — the UI-starvation meter. */
private data class FrameClockLag(val lastMillis: Long, val worstMillis: Long)

@Composable
private fun rememberFrameClockLag(): FrameClockLag {
    var lag by remember { mutableStateOf(FrameClockLag(0, 0)) }

    LaunchedEffect(Unit) {
        var previous = 0L
        var worst = 0L
        while (true) {
            withFrameMillis { now ->
                if (previous != 0L) {
                    val elapsed = now - previous
                    if (elapsed > worst) worst = elapsed
                    lag = FrameClockLag(elapsed, worst)
                }
                previous = now
            }
        }
    }

    return lag
}

private class FormatChoice(val label: String, val format: VideoCaptureFormat)

private val FORMAT_CHOICES = listOf(
    FormatChoice("1080p30", VideoCaptureFormat.PREFERRED),
    FormatChoice("720p30", VideoCaptureFormat(1280, 720, 30)),
    FormatChoice("480p30", VideoCaptureFormat(640, 480, 30)),
)

private const val HARNESS_STATS = "harness.stats"

private fun tempCaptureFile(): Path =
    Files.createTempDirectory("camera-harness").resolve("harness.mjpeg")

private fun sizeOf(file: Path): String =
    if (Files.exists(file)) "%.2f MB".format(Files.size(file) / 1_048_576.0) else "0 MB"
