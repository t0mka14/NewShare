package org.example.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.dp
import org.example.app.domain.video.PtzAction
import org.example.app.domain.video.VideoError
import org.example.app.navigation.TaskComponent

/**
 * VIDEO task body: the live preview, with the PTZ cluster beside it when the host platform
 * has a PTZ backend and the task asked for one.
 */
@Composable
fun VideoTaskBody(
    content: TaskComponent.Content.Video,
    onPtz: (PtzAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            VideoSurface(
                frames = content.frames,
                modifier = Modifier.fillMaxHeight(),
                testTag = TestTags.Task.VIDEO_PREVIEW,
            )

            when {
                content.error != null -> Text(
                    text = describe(content.error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp).testTag(TestTags.Task.VIDEO_ERROR),
                )

                // Entering the screen opens the camera; until the first frame arrives the
                // surface is black, so say why rather than looking broken.
                !content.ready -> Text(
                    text = "Starting camera…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp).testTag(TestTags.Task.VIDEO_STARTING),
                )
            }
        }

        if (content.ptzAvailable) {
            PtzControls(
                zoom = content.zoom.collectAsState().value,
                onPtz = onPtz,
                modifier = Modifier.testTag(TestTags.Task.PTZ_CONTROLS),
            )
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

private fun describe(error: VideoError): String = when (error) {
    is VideoError.CaptureBackendUnavailable -> "Camera support is not installed."
    is VideoError.DeviceUnavailable -> "Camera unavailable — it may be in use by another program."
    is VideoError.CaptureStartFailed -> "The camera could not be started."
    is VideoError.CaptureInterrupted -> "The camera stopped unexpectedly."
    is VideoError.DiskWriteFailed -> "The video could not be written to disk."
}
