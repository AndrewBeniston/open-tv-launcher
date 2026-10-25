package app.opentvlauncher.ui.theme

import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The squircle outlines, checked with the real Skia path code. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SquircleTest {
    // Every surface size at 1080p (2 px a dp), with its rest and focused corner and the morph between.
    private val surfaces = listOf(
        Size(392f, 220f) to 32f..40f, // Wide card
        Size(392f, 220f) to 40f..56f, // App tile
        Size(248f, 372f) to 24f..32f, // Poster
        Size(248f, 140f) to 24f..32f, // All Apps tile
        Size(200f, 80f) to 32f..40f, // Banner button, pressed to pill
        Size(90f, 36f) to 8f..8f, // Duration badge
    )

    @Test
    fun everyOutlineIsConvexSoSkiaDrawsItOnItsFastPath() {
        for ((size, corners) in surfaces) {
            var corner = corners.start
            while (corner <= corners.endInclusive) {
                val path = squirclePath(size, corner).asAndroidPath()
                assertTrue("$size corner $corner is not convex", path.isConvex)
                corner += 0.25f
            }
        }
    }

    @Test
    fun anySizeAndCornerIsConvex() {
        for (width in listOf(16f, 22f, 40f, 56f, 80f, 200f, 248f, 392f, 1920f)) {
            for (height in listOf(16f, 28f, 40f, 80f, 140f, 220f, 372f)) {
                var corner = 0.5f
                while (corner <= minOf(width, height) / 2) {
                    val path = squirclePath(Size(width, height), corner).asAndroidPath()
                    assertTrue("$width x $height corner $corner is not convex", path.isConvex)
                    corner += 0.5f
                }
            }
        }
    }

    @Test
    fun outlineFillsItsSize() {
        val bounds = RectF().also { squirclePath(Size(392f, 220f), 32f).asAndroidPath().computeBounds(it, true) }
        assertEquals(0f, bounds.left, 0.05f)
        assertEquals(0f, bounds.top, 0.05f)
        assertEquals(392f, bounds.right, 0.05f)
        assertEquals(220f, bounds.bottom, 0.05f)
    }

    @Test
    fun smoothedCornerMeetsTheEdgeMoreGraduallyThanACircle() {
        // Half a pixel below the top edge, a circular 32 px corner is 26.4 px from the side.
        // The squircle's curve is still that close to the edge further along.
        val path = squirclePath(Size(392f, 220f), 32f).asAndroidPath()
        val strip = android.graphics.Path().apply { addRect(0f, 0f, 60f, 0.5f, android.graphics.Path.Direction.CW) }
        val inside = android.graphics.Path().apply { op(path, strip, android.graphics.Path.Op.INTERSECT) }
        val bounds = RectF().also { inside.computeBounds(it, true) }
        assertTrue("got ${bounds.left}", bounds.left > 27.5f)
    }

    @Test
    fun aPathIsMadeOnceForEachSizeAndCorner() {
        assertSame(squirclePath(Size(392f, 220f), 32f), squirclePath(Size(392f, 220f), 32.05f))
    }
}
