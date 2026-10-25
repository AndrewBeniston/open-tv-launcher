package app.opentvlauncher.system

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** A plain red N: two darker uprights and the band across them, in an 18 by 28 box. */
fun DrawScope.netflixN() {
    val bar = size.width / 3f
    val upright = Color(0xFFB1060F)
    drawRect(upright, size = Size(bar, size.height))
    drawRect(upright, topLeft = Offset(size.width - bar, 0f), size = Size(bar, size.height))
    val band = Path().apply {
        moveTo(0f, 0f)
        lineTo(bar, 0f)
        lineTo(size.width, size.height)
        lineTo(size.width - bar, size.height)
        close()
    }
    drawPath(band, Color(0xFFE50914))
}

/** The N's width for each unit of its height. */
private const val N_ASPECT = 18f / 28f

/**
 * The Netflix button's mark (25 Oct 2026): the same red N as the apps row's Netflix tile,
 * [px] high and centred in a square of [px]. Netflix's own TV icon is a white square with its
 * name, which showed as a box on the button.
 */
fun netflixMark(px: Int): ImageBitmap {
    val bitmap = ImageBitmap(px, px)
    val side = px.toFloat()
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(side, side)) {
        inset(horizontal = (side - side * N_ASPECT) / 2f, vertical = 0f) { netflixN() }
    }
    return bitmap
}
