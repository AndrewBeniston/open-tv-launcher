package app.opentvlauncher.ui.art

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Art drawn in code, with no image address. The tests' sample titles use it, so the screen draws
 * without the network. The app itself makes none: real art always has addresses.
 */
@Stable
interface PaintedArt {
    /** The content tint for the page wash when this art has focus (design: "content tint"). */
    val tint: Color

    /** Paints the art into the whole of [scope]. */
    fun draw(scope: DrawScope)

    /** The big words on a wide card's thumbnail, as a real YouTube thumbnail has. */
    val thumbLines: List<ThumbLine> get() = emptyList()

    /** The size of the title printed on a poster, in sp, or null for no title. */
    val posterTitleSize: Int? get() = null
}

/** One line of a thumbnail's words, [size] in sp, on an optional [badge] colour. */
@Immutable
data class ThumbLine(val text: String, val size: Int, val color: Color = Color.White, val badge: Color? = null)
