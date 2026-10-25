package app.opentvlauncher.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Colour roles from the design, section 1 "Colour roles". */
object LauncherColors {
    val surface = Color(0xFF0E0F11)
    val surfaceContainerLowest = Color(0xFF08090A)
    val surfaceContainerLow = Color(0xFF141518)
    val surfaceContainer = Color(0xFF1A1B1F)
    val surfaceContainerHigh = Color(0xFF232428)
    val surfaceContainerHighest = Color(0xFF2C2D32)
    val onSurface = Color(0xFFECE9E3)
    val onSurfaceVariant = Color(0xFFA8A49C)

    /** Hints and meta under cards without focus. Use it only at 12 dp or larger. */
    val onSurfaceFaint = Color(0xFF6E6B65)

    /** Progress bars only. Amber has one meaning. */
    val primary = Color(0xFFE6BB74)
    val inverseSurface = Color(0xFFECE9E3)
    val inverseOnSurface = Color(0xFF141517)
    val track = Color(0xFFECE9E3).copy(alpha = 0.22f)

    /** A button at rest: on-surface at 14 %. */
    val buttonRest = Color(0xFFECE9E3).copy(alpha = 0.14f)
    val dot = Color(0xFFECE9E3).copy(alpha = 0.24f)
    val dotFill = Color(0xFFECE9E3).copy(alpha = 0.88f)

    /** The time that has run on a focused dot: the surface colour at 40 %, over the bright dot. */
    val dotFocusedFill = Color(0xFF0E0F11).copy(alpha = 0.4f)
}

/** A tonal container that gets one tone lighter when it has focus. */
@Immutable
data class Tone(val rest: Color, val focused: Color)

object TileTones {
    val youTube = Tone(Color(0xFF2A1716), Color(0xFF4A221F))
    val stremio = Tone(Color(0xFF201C33), Color(0xFF372E5C))
    val netflix = Tone(Color(0xFF1E1415), Color(0xFF3B2022))
    val iPlayer = Tone(Color(0xFF2B1629), Color(0xFF4C2245))
    val input = Tone(Color(0xFF1A2024), Color(0xFF2E3A41))
    val allApps = Tone(Color(0xFF232428), Color(0xFF3A3B41))

    /** Small tiles for apps that are not on Home, and system tiles, in All Apps. */
    val other = Tone(Color(0xFF1A1B1F), Color(0xFF34353B))
}
