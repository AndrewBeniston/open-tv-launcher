package app.opentvlauncher.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.opentvlauncher.data.SampleHome
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 24 Oct 2026: the app tiles have a little depth, lighter at the top, with no edge line. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppTileDepthTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        compose.mainClock.autoAdvance = false
        compose.setContent { LauncherTheme { HomeScreen(SampleHome.content, homeRequests = 0, onOpenApp = {}) } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun luminance(color: androidx.compose.ui.graphics.Color) = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

    /** Stremio's tile, without focus. The right side has no mark and no label. */
    @Test
    fun aTileIsLighterAtTheTopThanAtTheBottom() {
        val tile = compose.onNodeWithText("Stremio").captureToImage().toPixelMap()
        val x = tile.width * 85 / 100
        val top = luminance(tile[x, tile.height * 12 / 100])
        val middle = luminance(tile[x, tile.height / 2])
        val bottom = luminance(tile[x, tile.height * 88 / 100])
        assertTrue("top $top, middle $middle, bottom $bottom", top > middle && middle > bottom)
    }

    /** No border: just inside the side edge, the tile is the same as a little further in. */
    @Test
    fun aTileHasNoEdgeLine() {
        val tile = compose.onNodeWithText("Stremio").captureToImage().toPixelMap()
        val y = tile.height / 2
        val edge = luminance(tile[tile.width - 3, y])
        val inside = luminance(tile[tile.width - 12, y])
        assertTrue("edge $edge, inside $inside", kotlin.math.abs(edge - inside) < 0.01f)
    }
}
