package app.opentvlauncher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import app.opentvlauncher.system.LocalTitleApps
import app.opentvlauncher.system.TitleAppIcons
import app.opentvlauncher.ui.art.ArtLoader
import app.opentvlauncher.ui.art.LocalArtLoader

val LocalLauncherType = staticCompositionLocalOf<LauncherType> { error("LauncherTheme is missing") }

/**
 * The launcher's tokens, plus the same values mapped onto Compose for TV's MaterialTheme
 * so that any androidx.tv.material3 component matches the design. [art] loads real artwork;
 * without it only sample art draws.
 */
@Composable
fun LauncherTheme(art: ArtLoader? = null, titleApps: TitleAppIcons = TitleAppIcons.Unknown, content: @Composable () -> Unit) {
    val assets = LocalContext.current.assets
    val type = remember(assets) { LauncherType(autospawnSans(assets)) }
    CompositionLocalProvider(LocalLauncherType provides type, LocalArtLoader provides art, LocalTitleApps provides titleApps) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = LauncherColors.primary,
                background = LauncherColors.surface,
                onBackground = LauncherColors.onSurface,
                surface = LauncherColors.surface,
                onSurface = LauncherColors.onSurface,
                surfaceVariant = LauncherColors.surfaceContainerHigh,
                onSurfaceVariant = LauncherColors.onSurfaceVariant,
                inverseSurface = LauncherColors.inverseSurface,
                inverseOnSurface = LauncherColors.inverseOnSurface,
                border = Color.Transparent,
                borderVariant = Color.Transparent,
            ),
            typography = Typography(
                displayMedium = type.signInCode,
                displaySmall = type.bannerTitle,
                headlineMedium = type.pageTitle,
                headlineSmall = type.focusedRowHeader,
                titleLarge = type.clock,
                titleMedium = type.label,
                titleSmall = type.cardTitle,
                bodyLarge = type.synopsis,
                bodyMedium = type.meta,
                bodySmall = type.cardMeta,
                labelLarge = type.label,
                labelMedium = type.chip,
                labelSmall = type.cardMeta,
            ),
            // tv-material's Shapes takes CornerBasedShape, which cannot be a squircle. No tv-material
            // component uses these today. A dialog or any new surface uses SquircleShape (Squircle.kt).
            shapes = Shapes(
                extraSmall = RoundedCornerShape(LauncherShapes.extraSmall),
                medium = RoundedCornerShape(LauncherShapes.medium),
                large = RoundedCornerShape(LauncherShapes.large),
                extraLarge = RoundedCornerShape(LauncherShapes.extraLarge),
            ),
            content = content,
        )
    }
}
