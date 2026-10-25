package app.opentvlauncher.ui.home

import app.opentvlauncher.data.SampleHome
import org.junit.Assert.assertEquals
import org.junit.Test

class BannerStateTest {
    private val slides = SampleHome.content.slides

    @Test
    fun startsOnTheFirstSlide() {
        assertEquals("Tales of the Quiet Coast", BannerState(slides).current.title)
    }

    @Test
    fun nextWrapsFromTheLastSlideToTheFirst() {
        val state = BannerState(slides)
        repeat(slides.size) { state.next() }
        assertEquals(0, state.index)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aBannerNeedsASlide() {
        BannerState(emptyList())
    }
}
