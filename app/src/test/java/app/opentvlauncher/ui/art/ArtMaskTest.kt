package app.opentvlauncher.ui.art

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Design note: the bar sits on dark ground over bright art, as on Google TV. The banner art's mask
 * darkens its top with Google's own shade: #0E0E0F at 70 % at the top edge, clear 360 dp down.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtMaskTest {
    /** White art, 960 × 420 dp at 1 px per dp, with the banner mask baked in. */
    private fun maskedWhite(): android.graphics.Bitmap {
        val bitmap = ImageBitmap(960, 420)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(960f, 420f)) {
            drawRect(Color.White)
            ArtMask.Hero.draw(this)
        }
        return bitmap.asAndroidBitmap()
    }

    @Test
    fun theTopOfTheBannerArtIsDarkenedAsOnGoogleTv() {
        val art = maskedWhite()
        // The right side, where the art is opaque.
        val top = art.getPixel(900, 0)
        assertEquals(255, top ushr 24)
        // 30 % of white and 70 % of #0E0E0F.
        assertEquals(0.3 * 255 + 0.7 * 0x0E, ((top shr 8) and 0xFF).toDouble(), 3.0)
        // 360 dp down, the shade has gone: the colour is white again, under the bottom fade.
        val below = art.getPixel(900, 362)
        assertEquals(255.0, ((below shr 8) and 0xFF).toDouble(), 2.0)
    }
}
