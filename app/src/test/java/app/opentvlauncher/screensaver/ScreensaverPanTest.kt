package app.opentvlauncher.screensaver

import app.opentvlauncher.ui.theme.LauncherMotion
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 24 Oct 2026: a black edge showed at the side of the screensaver while the art panned. */
class ScreensaverPanTest {
    @Test
    fun theArtCoversTheScreenThroughTheWholeSlide() {
        assertTrue(ScreensaverMotion.START_SCALE >= 1 + 2 * (LauncherMotion.SCREENSAVER_PAN / 2))
        for (step in 0..100) {
            val progress = step / 100f
            // How far the scaled art reaches past each side, as a fraction of the screen width.
            val overhang = (ScreensaverMotion.scale(progress) - 1) / 2
            for (direction in listOf(1f, -1f)) {
                assertTrue("at $progress", overhang > abs(ScreensaverMotion.offset(progress, direction)))
            }
        }
        assertTrue(ScreensaverMotion.scale(1f) > ScreensaverMotion.START_SCALE)
    }
}
