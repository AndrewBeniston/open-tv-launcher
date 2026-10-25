package app.opentvlauncher.ui.art

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.opentvlauncher.data.Art
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A new size or a new artwork loads its own bitmap. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtImageTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var size by mutableStateOf(DpSize(197.dp, 111.dp))
    private var art by mutableStateOf(SampleArt.Fern)
    private lateinit var density: Density

    private fun showArt() {
        compose.setContent {
            density = LocalDensity.current
            ArtImage(Art(art), size)
        }
    }

    /** Lets Compose recompose, then waits for the background render of this key. */
    private fun waitForBitmap(art: SampleArt, size: DpSize) {
        compose.waitForIdle()
        val key = ArtCache.key(art, size, density)
        compose.waitUntil(timeoutMillis = 5_000) { ArtCache.peek(key) != null }
    }

    @Test
    fun aChangedSizeLoadsItsOwnBitmap() {
        showArt()
        waitForBitmap(SampleArt.Fern, DpSize(197.dp, 111.dp))
        size = DpSize(333.dp, 222.dp)
        waitForBitmap(SampleArt.Fern, DpSize(333.dp, 222.dp))
    }

    @Test
    fun aChangedArtworkLoadsItsOwnBitmap() {
        showArt()
        waitForBitmap(SampleArt.Fern, size)
        art = SampleArt.Glacier
        waitForBitmap(SampleArt.Glacier, size)
    }
}
