package org.example.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.ImageInfo
import kotlin.math.min
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger {}

/** [stateDescription] of the surface: whether a decoded frame is currently on it (test seam). */
internal const val FRAME_SHOWN = "frame"
internal const val FRAME_EMPTY = "empty"

/**
 * Renders the live camera preview.
 *
 * The recorder hands over JPEG bytes rather than an image, so the decode happens here. Four
 * things keep that affordable at 30 fps:
 *
 * - **Off the UI thread.** [decodeDispatcher] is a background dispatcher, never the composition's.
 *   Collecting on the composition dispatcher puts every decode in direct competition with input
 *   handling and rendering on the single Swing EDT, so a capture that runs faster than expected
 *   degrades into an unresponsive window rather than a dropped frame — which is exactly what a
 *   camera declaring an unestimated frame rate did on macOS.
 * - **Scaled decode.** The destination bitmap is allocated at a 1/2, 1/4 or 1/8 fraction of the
 *   source, and Skia's JPEG codec satisfies exactly those fractions by discarding high-frequency
 *   DCT coefficients rather than reconstructing full resolution and resampling. A 1080p frame
 *   decoded to 480x270 costs roughly a millisecond.
 * - **Double buffering.** Two bitmaps alternate, so the decode never writes into the one Skia is
 *   uploading as a texture. Still exactly one decoder, so the alternation stays correct.
 * - **Draw-phase invalidation.** A frame counter read inside [Canvas] means a new frame reruns
 *   the draw phase only — the task screen around it does not recompose thirty times a second.
 */
@Composable
fun VideoSurface(
    frames: StateFlow<ByteArray?>,
    decodeDispatcher: CoroutineDispatcher,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    /** False once the camera has failed: the last frame is dropped rather than left up as if live. */
    active: Boolean = true,
) {
    val holder = remember { VideoFrameHolder() }
    val revision = remember { mutableIntStateOf(0) }

    DisposableEffect(holder) { onDispose { holder.close() } }

    LaunchedEffect(frames, holder, decodeDispatcher, active) {
        if (!active) {
            if (holder.decode(null)) revision.intValue++
            return@LaunchedEffect
        }
        withContext(decodeDispatcher) {
            frames.collect { jpeg ->
                if (holder.decode(jpeg)) revision.intValue++
            }
        }
    }

    Box(modifier = modifier) {
        Canvas(
            Modifier.fillMaxSize()
                .semantics { stateDescription = if (revision.intValue >= 0 && holder.current != null) FRAME_SHOWN else FRAME_EMPTY }
                .let { if (testTag != null) it.testTag(testTag) else it },
        ) {
            revision.intValue // read so a new frame invalidates the draw phase
            holder.current?.let { drawPreserveAspect(it) }
        }
    }
}

/** Letterboxes the frame: a stretched preview would misrepresent how the subject is framed. */
private fun DrawScope.drawPreserveAspect(image: ImageBitmap) {
    if (image.width == 0 || image.height == 0 || size.width <= 0f || size.height <= 0f) return
    val scale = min(size.width / image.width, size.height / image.height)
    val width = (image.width * scale).roundToInt()
    val height = (image.height * scale).roundToInt()
    val topLeft = Offset((size.width - width) / 2f, (size.height - height) / 2f)
    drawImage(
        image = image,
        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize(width, height),
        filterQuality = FilterQuality.Low,
    )
}

/**
 * Owns the decode target bitmaps. [decode] runs on the decode dispatcher while [close] runs on the
 * UI thread when the surface leaves the composition (or the window crosses the preview threshold
 * mid-take), and cancelling the collecting coroutine does not wait for a native `readPixels` that
 * is already running. So the two exclude each other under [lock], and [closed] makes every decode
 * after the close a no-op that allocates nothing. Only one decoder ever runs, so the lock is
 * uncontended apart from that one race and is held for at most one decode.
 */
internal class VideoFrameHolder {
    private val lock = Any()
    private var closed = false
    private var bitmaps: Array<Bitmap>? = null
    private var info: ImageInfo? = null
    private var next = 0

    /**
     * The most recently decoded frame, or null before the first one arrives. Written on the decode
     * thread, read in the draw phase, hence volatile. It wraps a pool bitmap without copying it.
     */
    @Volatile
    var current: ImageBitmap? = null
        private set

    /** Test seam: whether decode target bitmaps are currently allocated. */
    internal val hasBitmaps: Boolean get() = synchronized(lock) { bitmaps != null }

    /** @return true when [current] changed and the surface should be redrawn. */
    fun decode(jpeg: ByteArray?): Boolean = synchronized(lock) {
        if (closed) return@synchronized false
        if (jpeg == null) {
            val had = current != null
            current = null
            return@synchronized had
        }
        try {
            org.jetbrains.skia.Codec.makeFromData(Data.makeFromBytes(jpeg)).use { codec ->
                val target = targetInfo(codec.imageInfo.width, codec.imageInfo.height)
                val bitmap = bitmapFor(target)
                codec.readPixels(bitmap)
                current = bitmap.asComposeImageBitmap()
            }
            true
        } catch (e: Exception) {
            // A torn frame is not worth failing the screen over; the next one is 33 ms away.
            logger.debug(e) { "dropping an undecodable preview frame (${jpeg.size} bytes)" }
            false
        }
    }

    /** May block the calling (UI) thread for at most the one decode in progress, which is a millisecond or so. */
    fun close() = synchronized(lock) {
        closed = true
        bitmaps?.forEach { it.close() }
        bitmaps = null
        current = null
    }

    /**
     * Picks the largest of Skia's natively supported JPEG scale factors that still lands at or
     * under [PREVIEW_MAX_WIDTH]. Any other ratio would force a full-resolution decode.
     */
    private fun targetInfo(sourceWidth: Int, sourceHeight: Int): ImageInfo {
        var divisor = 1
        while (divisor < 8 && sourceWidth / divisor > PREVIEW_MAX_WIDTH) divisor *= 2
        return ImageInfo(
            width = maxOf(1, sourceWidth / divisor),
            height = maxOf(1, sourceHeight / divisor),
            colorType = ColorType.N32,
            alphaType = ColorAlphaType.OPAQUE,
        )
    }

    private fun bitmapFor(target: ImageInfo): Bitmap {
        val existing = bitmaps
        if (existing == null || info?.width != target.width || info?.height != target.height) {
            // Not closed: `current` wraps one of these very bitmaps (asComposeImageBitmap shares the
            // pixels, it does not copy), and the draw phase may still be painting it. Skia frees a
            // dropped bitmap through its cleaner once nothing, `current` included, references it.
            bitmaps = Array(2) { Bitmap().apply { allocPixels(target) } }
            info = target
            next = 0
        }
        val pool = bitmaps!!
        val bitmap = pool[next]
        next = (next + 1) % pool.size
        return bitmap
    }

    private companion object {
        /** 1920 / 4. Wide enough to judge framing, cheap enough to decode every frame. */
        const val PREVIEW_MAX_WIDTH = 480
    }
}
