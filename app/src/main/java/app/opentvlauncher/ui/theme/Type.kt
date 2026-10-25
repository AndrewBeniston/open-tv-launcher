package app.opentvlauncher.ui.theme

import android.content.res.AssetManager
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Autospawn Sans from app/src/main/assets/fonts (SIL OFL 1.1, licence in the same folder).
 * The design uses weights 300, 400 and 500. All ten files are declared so that no weight is synthesised.
 */
fun autospawnSans(assets: AssetManager): FontFamily {
    val faces = listOf(
        "Light" to FontWeight.Light,
        "Regular" to FontWeight.Normal,
        "Medium" to FontWeight.Medium,
        "Semibold" to FontWeight.SemiBold,
        "Bold" to FontWeight.Bold,
    )
    return FontFamily(
        faces.flatMap { (name, weight) ->
            listOf(
                Font("fonts/AutospawnSans-$name.ttf", assets, weight, FontStyle.Normal),
                Font("fonts/AutospawnSans-${name}Italic.ttf", assets, weight, FontStyle.Italic),
            )
        },
    )
}

/** The type scale from the design, section 1 "Type scale". Sizes are the design's dp values. */
@Immutable
class LauncherType(family: FontFamily) {
    private val base = TextStyle(
        fontFamily = family,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

    private fun style(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f, tabular: Boolean = false) =
        base.copy(
            fontSize = size.sp,
            lineHeight = line.sp,
            fontWeight = weight,
            letterSpacing = tracking.em,
            fontFeatureSettings = if (tabular) "tnum" else null,
        )

    val bannerTitle = style(40, 44, FontWeight.Medium, tracking = -0.012f)
    val signInCode = style(48, 60, FontWeight.Medium, tracking = 0.16f, tabular = true)
    val pageTitle = style(28, 32, FontWeight.Medium)
    val focusedRowHeader = style(24, 28, FontWeight.Medium)
    val rowHeader = style(16, 20, FontWeight.Medium)
    val label = style(16, 20, FontWeight.Medium)
    val cardTitle = style(14, 18, FontWeight.Medium)
    val synopsis = style(16, 22, FontWeight.Normal)
    val meta = style(14, 18, FontWeight.Normal)
    val eyebrow = style(14, 18, FontWeight.Medium, tracking = 0.01f)
    val cardMeta = style(12, 16, FontWeight.Normal)
    val chip = style(12, 16, FontWeight.Medium)
    val clock = style(18, 22, FontWeight.Normal, tracking = 0.01f, tabular = true)

    /** Placeholder for title logos. TMDB and Trakt logo images replace it. */
    val placeholderLogo = base.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.04.em)
}
