package app.opentvlauncher.ui.art

import androidx.compose.ui.graphics.Color
import app.opentvlauncher.data.Art
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The content tint from a picture (design, "Colour roles"): the hue of the art at 16 to 30 %
 * alpha. Colourful art gets the stronger wash. Nearly grey art gets the neutral tint.
 *
 * The hue is an average of the pixels' hues, weighted by their chroma, so grey areas count for
 * little. Saturation and lightness are fixed near the design's sample tints, so the wash stays
 * a muted colour on the dark page.
 */
object ArtTint {
    private const val GREY_LIMIT = 0.05f

    fun of(pixels: IntArray): Color {
        if (pixels.isEmpty()) return Art.NeutralTint
        var x = 0.0
        var y = 0.0
        var chroma = 0.0
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF) / 255.0
            val g = (pixel shr 8 and 0xFF) / 255.0
            val b = (pixel and 0xFF) / 255.0
            val max = maxOf(r, g, b)
            val weight = max - minOf(r, g, b)
            if (weight == 0.0) continue
            val sector = when (max) {
                r -> (g - b) / weight
                g -> 2 + (b - r) / weight
                else -> 4 + (r - g) / weight
            }
            val angle = Math.toRadians(sector * 60)
            x += cos(angle) * weight
            y += sin(angle) * weight
            chroma += weight
        }
        val mean = (chroma / pixels.size).toFloat()
        if (mean < GREY_LIMIT) return Art.NeutralTint
        val hue = ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
        val strength = (mean * 3f).coerceAtMost(1f)
        return Color.hsl(
            hue = hue,
            saturation = 0.30f + 0.22f * strength,
            lightness = 0.34f,
            alpha = 0.16f + 0.14f * strength,
        )
    }
}
