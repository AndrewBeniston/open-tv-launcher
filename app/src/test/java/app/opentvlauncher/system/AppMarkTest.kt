package app.opentvlauncher.system

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 25 Oct 2026: the icons on the Stremio and Netflix buttons had a square box round them.
 * The drawn icon is the mark alone: its corners are clear, and the mark keeps its own colours.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppMarkTest {
    private val px = 44

    private fun Bitmap.corners() = listOf(getPixel(0, 0), getPixel(width - 1, 0), getPixel(0, height - 1), getPixel(width - 1, height - 1))

    private fun assertCornersClear(mark: Bitmap) =
        mark.corners().forEach { assertEquals("corner alpha in ${mark.corners().map(Integer::toHexString)}", 0, Color.alpha(it)) }

    /** Like Stremio's: a dark square background layer under a purple mark. */
    @Test
    fun anAdaptiveIconShowsItsForegroundWithoutItsSquare() {
        val mark = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888).apply {
            // A purple diamond in the middle of the 108-unit layer, inside its 72-unit view.
            Canvas(this).drawPath(
                android.graphics.Path().apply {
                    moveTo(54f, 24f)
                    lineTo(84f, 54f)
                    lineTo(54f, 84f)
                    lineTo(24f, 54f)
                    close()
                },
                Paint().apply { color = PURPLE },
            )
        }
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val icon = AdaptiveIconDrawable(ColorDrawable(NAVY), BitmapDrawable(resources, mark))
        val drawn = AppMark.of(icon, px)
        assertEquals(px, drawn.width)
        assertCornersClear(drawn)
        // Nowhere is the background layer's navy.
        for (x in 0 until px) for (y in 0 until px) assertTrue("navy at $x,$y", drawn.getPixel(x, y) != NAVY)
        assertEquals(PURPLE, drawn.getPixel(px / 2, px / 2))
        // Trimmed to the diamond: its points reach the edges of the square.
        assertTrue(Color.alpha(drawn.getPixel(px / 2, 1)) > 0)
        assertTrue(Color.alpha(drawn.getPixel(1, px / 2)) > 0)
    }

    /** Like Netflix's TV icon: a white square with the red name across its middle. */
    @Test
    fun aPlainIconLosesTheBackgroundRoundItsMark() {
        val square = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            Canvas(this).drawRect(20f, 66f, 140f, 94f, Paint().apply { color = RED })
        }
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val drawn = AppMark.of(BitmapDrawable(resources, square), px)
        assertCornersClear(drawn)
        // The red bar fills the width; above and below it is clear.
        assertEquals(RED, drawn.getPixel(px / 2, px / 2))
        assertEquals(0, Color.alpha(drawn.getPixel(px / 2, 2)))
        assertEquals(0, Color.alpha(drawn.getPixel(px / 2, px - 3)))
        assertTrue(Color.alpha(drawn.getPixel(1, px / 2)) > 0)
        // No white is left anywhere.
        for (x in 0 until px) for (y in 0 until px) {
            val pixel = drawn.getPixel(x, y)
            assertTrue("white at $x,$y", Color.alpha(pixel) < 200 || Color.green(pixel) < 128)
        }
    }

    @Test
    fun anIconThatIsAllBackgroundIsKept() {
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val blank = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        assertEquals(Color.WHITE, AppMark.of(BitmapDrawable(resources, blank), px).getPixel(0, 0))
    }

    /** The icons the buttons draw, as MainActivity reads them. */
    @Test
    fun theButtonIconsHaveClearCorners() {
        installTitleApps()
        val icons = readTitleApps().icons.values.filterNotNull()
        assertEquals(2, icons.size)
        icons.forEach { assertCornersClear(Bitmap.createBitmap(it.width, it.height, Bitmap.Config.ARGB_8888).also { b -> it.readPixelsInto(b) }) }
    }

    private fun androidx.compose.ui.graphics.ImageBitmap.readPixelsInto(bitmap: Bitmap) {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private companion object {
        val NAVY = Color.rgb(11, 9, 40)
        val PURPLE = Color.rgb(123, 91, 245)
        val RED = Color.rgb(229, 9, 20)
    }
}
