package app.opentvlauncher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/** Checks the colour tokens against the contrast ratios in the design's "Colour roles" table. */
class ContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    private fun check(expected: Float, text: Color, background: Color) {
        assertEquals(expected, contrast(text, background), 0.06f)
    }

    @Test
    fun textOnSurface() {
        check(15.8f, LauncherColors.onSurface, LauncherColors.surface)
        check(7.7f, LauncherColors.onSurfaceVariant, LauncherColors.surface)
        check(3.6f, LauncherColors.onSurfaceFaint, LauncherColors.surface)
        check(10.7f, LauncherColors.primary, LauncherColors.surface)
    }

    @Test
    fun focusedButtonLabel() {
        check(15.1f, LauncherColors.inverseOnSurface, LauncherColors.inverseSurface)
    }

    @Test
    fun appTileLabels() {
        val label = LauncherColors.onSurface
        check(14.1f, label, TileTones.youTube.rest)
        check(11.3f, label, TileTones.youTube.focused)
        check(13.6f, label, TileTones.stremio.rest)
        check(10.1f, label, TileTones.stremio.focused)
        check(13.9f, label, TileTones.iPlayer.rest)
        check(10.7f, label, TileTones.iPlayer.focused)
        check(12.8f, label, TileTones.allApps.rest)
        check(9.2f, label, TileTones.allApps.focused)
    }
}
