package org.example.app.ui

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * [VideoFrameHolder] with real JPEG bytes. The stress test cannot prove memory safety (a native
 * use-after-free is undefined behaviour, not a reliable exception), only exercise the race and check
 * the observable end state; the lock and flag are what make it safe, and test (a) pins the flag.
 */
class VideoFrameHolderTest {

    private fun jpeg(width: Int = 960, height: Int = 540, seed: Int = 1): ByteArray {
        val surface = Surface.makeRasterN32Premul(width, height)
        val random = Random(seed)
        val paint = Paint()
        surface.canvas.clear(Color.makeRGB(20, 40, 60))
        repeat(300) {
            paint.color = Color.makeRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            surface.canvas.drawCircle(random.nextFloat() * width, random.nextFloat() * height, 5f + random.nextFloat() * 60f, paint)
        }
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 85)!!.bytes
    }

    @Test
    fun `a decode produces a frame at a scaled size`() {
        val holder = VideoFrameHolder()
        assertTrue(holder.decode(jpeg()))
        assertNotNull(holder.current)
        val image = holder.current!!
        assertEquals(480, image.width)
        assertEquals(270, image.height)
        assertTrue(holder.hasBitmaps)
        holder.close()
    }

    @Test
    fun `after close decode is a no-op that returns false, keeps current null and allocates nothing`() {
        val holder = VideoFrameHolder()
        val bytes = jpeg()
        assertTrue(holder.decode(bytes))
        holder.close()
        assertNull(holder.current)
        assertFalse(holder.hasBitmaps)

        assertFalse(holder.decode(bytes), "a decode after close must not report a new frame")
        assertNull(holder.current, "a decode after close must not publish a frame")
        assertFalse(holder.hasBitmaps, "a decode after close must not allocate bitmaps")
        assertFalse(holder.decode(null))
        holder.close() // closing twice is harmless
        assertNull(holder.current)
    }

    @Test
    fun `a frame handed to the draw phase stays readable after a size change`() {
        val holder = VideoFrameHolder()
        assertTrue(holder.decode(jpeg(960, 540)))
        val shown = holder.current!!
        assertTrue(holder.decode(jpeg(640, 360))) // different size: a new pool is allocated; the old bitmaps are dropped, not closed
        // The old image wraps (does not copy) a pool bitmap, so that bitmap must not have been closed under it.
        // Reading a closed bitmap crashes the JVM in native code (seen with the old close-on-resize),
        // so check the flag first: a regression then fails cleanly instead.
        val shownBitmap = (shown.asSkiaBitmap())
        assertFalse(shownBitmap.isClosed, "the shown frame's bitmap was closed by the size change")
        val pixels = shown.toPixelMap()
        assertEquals(480, pixels.width)
        // jpeg() clears to (20, 40, 60) and draws coloured circles; a read of freed or blank memory would not give an opaque pixel of a real colour.
        val pixel = pixels[0, 0]
        assertEquals(1f, pixel.alpha)
        assertTrue(pixel.red + pixel.green + pixel.blue > 0f, "pixel $pixel")
        holder.close()
    }

    @Test
    fun `close racing a decode loop ends in a consistent closed state, repeatedly`() {
        val bytes = jpeg()
        val random = Random(42)
        repeat(150) { round ->
            val holder = VideoFrameHolder()
            val failure = AtomicReference<Throwable?>(null)
            val stop = AtomicBoolean(false)
            val worker = Thread {
                try {
                    while (!stop.get()) holder.decode(bytes)
                } catch (t: Throwable) {
                    failure.set(t)
                }
            }
            worker.start()
            try {
                val delayNanos = random.nextInt(0, 4_000_000) // 0-4 ms: lands before, inside and between decodes
                Thread.sleep(delayNanos / 1_000_000L, delayNanos % 1_000_000)
                holder.close()
                Thread.sleep(2) // the loop keeps decoding against the closed holder
            } finally {
                stop.set(true)
            }
            worker.join(10_000)
            assertFalse(worker.isAlive, "round $round: the decode loop did not finish")
            assertNull(failure.get(), "round $round: decode threw ${failure.get()}")
            assertNull(holder.current, "round $round: a frame was published after close")
            assertFalse(holder.hasBitmaps, "round $round: bitmaps were allocated after close")
        }
    }
}
