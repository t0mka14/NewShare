package org.example.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.example.app.domain.video.PtzAction
import org.example.app.domain.video.VideoError
import org.example.app.navigation.TaskComponent
import org.example.app.navigation.VideoFeed

/**
 * VIDEO task body: the live preview, with the PTZ cluster beside it when the host platform
 * has a PTZ backend and the task asked for one.
 */
@Composable
fun VideoTaskBody(
    content: TaskComponent.Content.Video,
    localization: UiLocalization,
    onPtz: (PtzAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoPreview(content.feed, localization, Modifier.weight(1f).fillMaxHeight())

        if (content.ptzAvailable) {
            PtzControls(
                zoom = content.zoom.collectAsState().value,
                onPtz = onPtz,
                modifier = Modifier.testTag(TestTags.Task.PTZ_CONTROLS),
            )
        }
    }
}

/** Height reserved under the picture for the camera message: two lines in the narrow preview, one line in the wide one. */
internal val CAMERA_MESSAGE_SLOT_NARROW = 48.dp
internal val CAMERA_MESSAGE_SLOT = 32.dp

/**
 * The live camera preview of any filming task: a black frame with the picture, and below it a
 * line saying why there is none. A VOCAL task with `recordVideo` shows this beside its level
 * indicator, with [pictureHeight] fixing the picture's size. After a failure the picture is
 * cleared and exactly one message is shown, never over the picture.
 */
@Composable
fun VideoPreview(feed: VideoFeed, localization: UiLocalization, modifier: Modifier = Modifier, pictureHeight: Dp? = null) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = (if (pictureHeight != null) Modifier.fillMaxWidth().height(pictureHeight) else Modifier.fillMaxWidth().weight(1f))
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            VideoSurface(
                frames = feed.frames,
                decodeDispatcher = feed.decodeDispatcher,
                modifier = Modifier.fillMaxHeight(),
                testTag = TestTags.Task.VIDEO_PREVIEW,
                active = feed.error == null,
            )

            // Entering the screen opens the camera; until the first frame arrives the surface is
            // black, so say why rather than looking broken. Past the wait of an audio-only
            // fallback, the line below the picture says what happens instead.
            if (feed.error == null && !feed.ready && !feed.audioOnly) {
                Text(
                    text = localization.resolvePlain("task.video.starting"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(8.dp).testTag(TestTags.Task.VIDEO_STARTING),
                )
            }
        }

        // F-133: the line for the camera message is always reserved, so its appearance (or the
        // message wrapping to two lines in the narrow 200 dp preview) moves nothing else.
        Box(
            modifier = Modifier.fillMaxWidth().height(if (pictureHeight != null) CAMERA_MESSAGE_SLOT_NARROW else CAMERA_MESSAGE_SLOT),
            contentAlignment = Alignment.TopCenter,
        ) {
            val error = feed.error
            when {
                // VOCAL task: the take goes on without video; one line says so (error + audioOnly coincide).
                feed.audioOnly && (error != null || !feed.ready) -> Text(
                    text = localization.resolvePlain(
                        if (error is VideoError.CaptureInterrupted) "task.video.cameraLost" else "task.video.audioOnly",
                    ),
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp).testTag(TestTags.Task.VIDEO_AUDIO_ONLY),
                )

                error != null -> Text(
                    text = localization.resolvePlain(error.messageKey()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp).testTag(TestTags.Task.VIDEO_ERROR),
                )
            }
        }
    }
}

@Composable
private fun PtzControls(zoom: Int?, onPtz: (PtzAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(200.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PtzButton(PtzAction.TiltUp, Icons.Filled.KeyboardArrowUp, onPtz)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PtzButton(PtzAction.PanLeft, Icons.Filled.KeyboardArrowLeft, onPtz)
            PtzButton(PtzAction.PanRight, Icons.Filled.KeyboardArrowRight, onPtz)
        }
        PtzButton(PtzAction.TiltDown, Icons.Filled.KeyboardArrowDown, onPtz)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PtzButton(PtzAction.ZoomIn, Icons.Filled.Add, onPtz)
            PtzButton(PtzAction.ZoomOut, Icons.Filled.Remove, onPtz)
        }
        if (zoom != null) {
            Text(text = "$zoom", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Press-and-hold: the camera moves while the button is held and stops on release, so
 * [PtzAction.Stop] must be sent even if the pointer leaves the button. The interaction source
 * reports that release; `onClick` would not.
 */
@Composable
private fun PtzButton(action: PtzAction, icon: ImageVector, onPtz: (PtzAction) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    DisposableEffect(pressed) {
        onPtz(if (pressed) action else PtzAction.Stop)
        onDispose { if (pressed) onPtz(PtzAction.Stop) }
    }

    Button(
        onClick = {},
        interactionSource = interactionSource,
        modifier = Modifier.testTag(TestTags.Task.ptzButton(action.name)),
    ) {
        Icon(icon, contentDescription = action.name)
    }
}

private fun VideoError.messageKey(): String = when (this) {
    is VideoError.CaptureBackendUnavailable -> "task.video.error.backendUnavailable"
    is VideoError.DeviceUnavailable -> "task.video.error.deviceUnavailable"
    is VideoError.CaptureStartFailed -> "task.video.error.startFailed"
    is VideoError.CaptureInterrupted -> "task.video.error.interrupted"
    is VideoError.DiskWriteFailed -> "task.video.error.diskWriteFailed"
}
