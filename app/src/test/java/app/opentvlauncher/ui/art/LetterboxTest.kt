package app.opentvlauncher.ui.art

import org.junit.Assert.assertEquals
import org.junit.Test

/** Black bars baked into a backdrop, as on Upgrade's, are left out; a dark sky is not. */
class LetterboxTest {
    @Test
    fun barsAtTheTopAndBottomAreLeftOutButADarkSkyStays() {
        assertEquals(150 until 940, letterbox(1080) { it < 150 || it >= 940 })
        assertEquals(0 until 1080, letterbox(1080) { it < 150 })
        assertEquals(0 until 1080, letterbox(1080) { true })
    }
}
