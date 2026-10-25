package app.opentvlauncher.screensaver

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.core.graphics.createBitmap
import app.opentvlauncher.home.Title
import app.opentvlauncher.home.TitleImages
import app.opentvlauncher.home.TitleKind
import app.opentvlauncher.ui.art.ArtLoader
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The screensaver as it draws, with a fake image loader. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensaverScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** A white logo [width] × [height] pixels at [url]; every other image is missing. */
    private fun loader(url: String, width: Int, height: Int) = ArtLoader { asked, _, _, _ ->
        if (asked == url) createBitmap(width, height).apply { eraseColor(android.graphics.Color.WHITE) } else null
    }

    private fun title(logo: String) = Title(
        kind = TitleKind.Movie,
        title = "Ivory",
        year = 1952,
        traktId = 1,
        images = TitleImages(fanart = "https://trakt/fanart-missing", logo = logo),
    )

    private fun show(titles: List<Title>, art: ArtLoader) {
        compose.mainClock.autoAdvance = false
        compose.setContent { LauncherTheme(art = art) { ScreensaverScreen(titles) } }
    }

    /** Advances the clock frame by frame, so loaded art and fades reach the screen. */
    private fun advance(millis: Long) {
        repeat((millis / 16).toInt()) { compose.mainClock.advanceTimeByFrame() }
    }

    /** The columns that hold the logo's light pixels. The year line is dimmer than the logo. */
    private fun logoColumns(): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val columns = (0 until pixels.width).filter { x -> (0 until pixels.height).any { y -> pixels[x, y].red > 0.6f } }
        return if (columns.isEmpty()) 0 else columns.last() - columns.first() + 1
    }

    /** 24 Oct 2026: a logo is never drawn larger than its own pixels, only smaller. */
    @Test
    fun aSmallLogoKeepsItsOwnPixelSize() {
        show(listOf(title("https://trakt/logo-small")), loader("https://trakt/logo-small", 100, 30))
        compose.waitUntil(timeoutMillis = 5_000) {
            advance(100)
            logoColumns() > 0
        }
        advance(3_000)
        assertEquals(100, logoColumns())
    }

    @Test
    fun aLargeLogoShrinksIntoItsBox() {
        show(listOf(title("https://trakt/logo-large")), loader("https://trakt/logo-large", 1600, 240))
        compose.waitUntil(timeoutMillis = 5_000) {
            advance(100)
            logoColumns() > 0
        }
        advance(3_000)
        // The box is 400 × 120 dp, 800 × 240 pixels at this density: the logo shrinks by half.
        assertEquals(800, logoColumns())
    }

    @Test
    fun theYearGenreAndSynopsisShowUnderTheLogo() {
        val title = title("https://trakt/logo-none").copy(genres = listOf("drama"), overview = "A bureaucrat learns he is dying. He looks for meaning. He finds it.")
        show(listOf(title), loader("https://trakt/other", 1, 1))
        advance(3_000)
        compose.onNodeWithText("Drama film from 1952").assertExists()
        compose.onNodeWithText("A bureaucrat learns he is dying. He looks for meaning.").assertExists()
    }

    /** The brightest red channel on screen, 0 to 1. */
    private fun brightest(): Float {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return (0 until pixels.width step 2).maxOf { x -> (0 until pixels.height step 2).maxOf { y -> pixels[x, y].red } }
    }

    /** 24 Oct 2026: the screensaver fades in instead of popping in. */
    @Test
    fun theFirstSlideFadesInFromBlack() {
        // No logo, so the title shows as large text in the logo's place.
        show(listOf(title("https://trakt/logo-none")), loader("https://trakt/other", 1, 1))
        advance(160)
        val start = brightest()
        advance(1_000)
        val middle = brightest()
        advance(3_000)
        val end = brightest()
        assertTrue("start $start, middle $middle, end $end", start < 0.1f && middle > start && end > middle + 0.1f)
    }
}
