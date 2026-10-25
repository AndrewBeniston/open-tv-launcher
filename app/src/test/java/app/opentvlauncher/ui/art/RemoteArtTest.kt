package app.opentvlauncher.ui.art

import android.graphics.Bitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import app.opentvlauncher.data.Art
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real art: addresses tried in order, drawn size, no enlargement, the tint, and no refetch of a missing image. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemoteArtTest {
    private val density = Density(2f)
    private val card = DpSize(196.dp, 110.dp)
    private val asked = mutableListOf<String>()

    private fun picture(width: Int, height: Int, color: Int) = createBitmap(width, height).apply { eraseColor(color) }

    /** Answers only the addresses in [images]; any other address is missing. */
    private fun loader(images: Map<String, Bitmap>) = ArtLoader { url, _, _, _ ->
        asked += url
        images[url]
    }

    @Test
    fun aMissingMetahubImageFallsBackToTraktAtTheDrawnSize() = runBlocking {
        val art = Art(listOf("metahub/a", "trakt/a"))
        val key = ArtCache.key(art, card, density)!!
        val bitmap = ArtCache.load(key, loader(mapOf("trakt/a" to picture(480, 270, android.graphics.Color.RED))))!!
        assertEquals(listOf("metahub/a", "trakt/a"), asked)
        assertEquals(392 to 220, bitmap.width to bitmap.height)
        assertEquals(bitmap, ArtCache.peek(key))
    }

    @Test
    fun aSmallSourceIsNotEnlargedInMemory() = runBlocking {
        val art = Art(listOf("metahub/b"))
        val bitmap = ArtCache.load(ArtCache.key(art, card, density)!!, loader(mapOf("metahub/b" to picture(160, 90, 0))))!!
        assertEquals(160 to 90, bitmap.width to bitmap.height)
    }

    @Test
    fun aLogoFitsInsideItsBoxWithoutCropping() = runBlocking {
        val art = Art(listOf("metahub/logo"))
        val key = ArtCache.key(art, DpSize(300.dp, 120.dp), density, fit = true)!!
        val bitmap = ArtCache.load(key, loader(mapOf("metahub/logo" to picture(800, 310, 0))))!!
        assertEquals(600 to 233, bitmap.width to bitmap.height)
    }

    @Test
    fun theTintTakesTheHueOfTheArt() = runBlocking {
        val art = Art(listOf("metahub/c"))
        assertEquals(Art.NeutralTint, ArtCache.tint(art))
        ArtCache.load(ArtCache.key(art, card, density)!!, loader(mapOf("metahub/c" to picture(480, 270, android.graphics.Color.BLUE))))
        val tint = ArtCache.tint(art)
        assertTrue("blue wash, got $tint", tint.blue > tint.red && tint.blue > tint.green)
    }

    @Test
    fun aMissingImageIsNotFetchedAgainAtOnce() = runBlocking {
        val art = Art(listOf("metahub/d", "trakt/d"))
        val key = ArtCache.key(art, card, density)!!
        assertNull(ArtCache.load(key, loader(emptyMap())))
        assertNull(ArtCache.load(key, loader(emptyMap())))
        assertEquals(listOf("metahub/d", "trakt/d"), asked)
    }

    @Test
    fun withoutALoaderRealArtDrawsItsPlaceholder() = runBlocking {
        assertNull(ArtCache.load(ArtCache.key(Art(listOf("metahub/e")), card, density)!!, null))
        assertNull(ArtCache.key(Art.None, card, density))
    }
}

/** The tint rule on its own: hue from colourful pixels, 16 to 30 % alpha, neutral for grey art. */
class ArtTintTest {
    @Test
    fun greyArtGetsTheNeutralTint() {
        assertEquals(Art.NeutralTint, ArtTint.of(IntArray(144) { 0xFF808080.toInt() }))
    }

    @Test
    fun colourfulArtGetsItsHueAtThirtyPercent() {
        val tint = ArtTint.of(IntArray(144) { 0xFFE03020.toInt() })
        assertEquals(0.30f, tint.alpha, 0.01f)
        assertTrue("a red wash, got $tint", tint.red > tint.green && tint.red > tint.blue)
    }

    @Test
    fun mostlyGreyArtGetsAWeakerWash() {
        val pixels = IntArray(144) { if (it % 8 == 0) 0xFFE03020.toInt() else 0xFF606060.toInt() }
        val tint = ArtTint.of(pixels)
        assertTrue("alpha ${tint.alpha}", tint.alpha in 0.16f..0.25f)
    }
}
