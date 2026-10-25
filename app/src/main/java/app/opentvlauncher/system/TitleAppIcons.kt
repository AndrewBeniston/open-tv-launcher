package app.opentvlauncher.system

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * The installed title apps, each with its mark: Stremio's own icon, or for Netflix the red N
 * of the apps row (25 Oct 2026). The launcher reads them each time it comes to the
 * front: an app can be installed or removed meanwhile.
 */
@Immutable
class TitleAppIcons private constructor(val icons: Map<TitleApp, ImageBitmap?>, private val known: Boolean) {
    /** The installed packages, or null before the first read. */
    val installed: Set<String>? get() = if (known) icons.keys.mapTo(mutableSetOf()) { it.packageName } else null

    companion object {
        /** Before the first read: Stremio shows, as it always did, and Netflix waits (TitleApp.forTitle). */
        val Unknown = TitleAppIcons(emptyMap(), known = false)

        /** Reads the package manager. Slow (icons), so call it off the main thread. An app that is not installed is left out. */
        fun read(context: Context, iconPx: Int): TitleAppIcons {
            val pm = context.packageManager
            val icons = TitleApp.entries.mapNotNull { app ->
                val info = runCatching { pm.getApplicationInfo(app.packageName, 0) }.getOrNull() ?: return@mapNotNull null
                // The mark alone: an icon's own background showed as a square on the buttons. Netflix
                // gets the launcher's own N, as on the apps row (25 Oct 2026).
                val icon = runCatching {
                    if (app == TitleApp.Netflix) netflixMark(iconPx) else AppMark.of(pm.getApplicationIcon(info), iconPx).asImageBitmap()
                }.getOrNull()
                app to icon?.also { it.prepareToDraw() }
            }.toMap()
            return TitleAppIcons(icons, known = true)
        }
    }
}

/** The title apps for the banner, the details page and the Continue Watching menu. MainActivity provides them through LauncherTheme. */
val LocalTitleApps = compositionLocalOf { TitleAppIcons.Unknown }
