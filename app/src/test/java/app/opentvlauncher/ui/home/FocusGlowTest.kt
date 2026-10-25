package app.opentvlauncher.ui.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.core.graphics.ColorUtils
import app.opentvlauncher.data.Art
import app.opentvlauncher.data.SampleHome
import app.opentvlauncher.ui.focus.FocusGlow
import app.opentvlauncher.ui.sendTvKey
import app.opentvlauncher.ui.theme.LauncherColors
import app.opentvlauncher.ui.theme.LauncherTheme
import app.opentvlauncher.ui.theme.TileTones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The focus glow prototype: light in the art's colour round the focused item only, with no hard edge. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FocusGlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager

    @Before
    fun setUp() {
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            focusManager = LocalFocusManager.current
            // The window paints the surface colour on the TV (themes.xml).
            LauncherTheme { HomeScreen(SampleHome.content, homeRequests = 0, onOpenApp = {}, modifier = Modifier.background(LauncherColors.surface)) }
        }
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    private fun press(keyCode: Int) {
        compose.sendTvKey(keyCode, focusManager)
        settle()
    }

    private fun hsl(color: Color) = FloatArray(3).also { ColorUtils.colorToHSL(color.toArgb(), it) }

    @Test
    fun theGlowIsTheArtsHueMadeLighter() {
        val tint = Color.hsl(20f, 0.30f, 0.34f, alpha = 0.2f)
        val (hue, saturation, lightness) = hsl(FocusGlow.colour(tint)).toList()
        assertEquals(20f, hue, 1f)
        assertEquals(0.40f, saturation, 0.01f)
        assertEquals(0.55f, lightness, 0.01f)
        assertEquals(1f, FocusGlow.colour(tint).alpha)
    }

    @Test
    fun greyArtGlowsWarmWhiteAtHalfStrength() {
        assertEquals(Color(0xFFECE9E3).copy(alpha = 0.5f), FocusGlow.colour(Art.NeutralTint))
        assertEquals(Color(0xFFECE9E3).copy(alpha = 0.5f), FocusGlow.colour(TileTones.allApps.focused))
    }

    /** The red channel 16 dp below YouTube's tile, near its left end, away from Stremio's glow. */
    private fun belowYouTube(): Float {
        val tile = compose.onNodeWithText("YouTube").getBoundsInRoot()
        val d = compose.density.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val x = ((tile.left.value + 30f) * d).toInt()
        val y = ((tile.bottom.value + 16f) * d).toInt()
        return pixels[x, y].red
    }

    @Test
    fun onlyTheFocusedTileGlows() {
        val focused = belowYouTube()
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        val unfocused = belowYouTube()
        assertTrue("focused $focused, unfocused $unfocused", focused > unfocused + 0.03f)
    }

    /** A lazy row clips its content. Its bounds leave the glow's full reach above and below the cards. */
    @Test
    fun aRowLeavesRoomForTheGlowToFadeOut() {
        val d = compose.density.density
        val card = compose.onNodeWithContentDescription("Tales of the Quiet Coast").getBoundsInRoot()
        val cardTop = card.top.value * d
        val rows = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).fetchSemanticsNodes()
        val row = rows.map { it.boundsInRoot }.first { it.contains(Offset(card.left.value * d + 1f, cardTop + 1f)) }
        val room = (FocusGlow.reach + FocusGlow.drop).value * d
        assertTrue("row top ${row.top}, card top $cardTop", row.top <= cardTop - room + 1f)
    }
}
