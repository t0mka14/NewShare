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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.ImageInfo
import kotlin.math.min
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger {}

/**
 * Renders the live camera preview.
 *
 * The recorder hands over JPEG bytes rather than an image, so the decode happens here. Three
 * things keep that affordable at 30 fps:
 *
 * - **Scaled decode.** The destination bitmap is allocated at a 1/2, 1/4 or 1/8 fraction of the
 *   source, and Skia's JPEG codec satisfies exactly those fractions by discarding high-frequency
 *   DCT coefficients rather than reconstructing full resolution and resampling. A 1080p frame
 *   decoded to 480x270 costs roughly a millisecond.
 * - **Double buffering.** Two bitmaps alternate, so the decode never writes into the one Skia is
 *   uploading as a texture.
 * - **Draw-phase invalidation.** A frame counter read inside [Canvas] means a new frame reruns
 *   the draw phase only — the task screen around it does not recompose thirty times a second.
 */
@Composable
fun VideoSurface(
    frames: StateFlow<ByteArray?>,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val holder = remember { VideoFrameHolder() }
    val revision = remember { mutableIntStateOf(0) }

    DisposableEffect(holder) { onDispose { holder.close() } }

    LaunchedEffect(frames, holder) {
        frames.collect { jpeg ->
            if (holder.decode(jpeg)) revision.intValue++
        }
    }

    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize().let { if (testTag != null) it.testTag(testTag) else it }) {
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
 * Owns the decode target bitmaps. Not a composable concern beyond lifetime, and deliberately
 * not thread-safe: [decode] is only ever called from the collector coroutine.
 */
private class VideoFrameHolder {
    private var bitmaps: Array<Bitmap>? = null
    private var info: ImageInfo? = null
    private var next = 0

    /** The most recently decoded frame, or null before the first one arrives. */
    var current: ImageBitmap? = null
        private set

    /** @return true when [current] changed and the surface should be redrawn. */
    fun decode(jpeg: ByteArray?): Boolean {
        if (jpeg == null) {
            val had = current != null
            current = null
            return had
        }
        return try {
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

    fun close() {
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
            existing?.forEach { it.close() }
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
