package app.opentvlauncher.system

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import java.util.Locale

/** An app from the TV's own apps list, with its icon at the size All Apps draws it. */
@Immutable
data class InstalledApp(val packageName: String, val label: String, val icon: ImageBitmap?)

/**
 * The TV apps that All Apps lists (design, "All Apps"). Other Apps is every TV app that is not on
 * Home and not in the System group. The System group adds the Play Store when the TV has it.
 */
object InstalledApps {
    const val PLAY_STORE = "com.android.vending"
    private val SYSTEM_SETTINGS = setOf("com.android.tv.settings", "com.google.android.tv.settings")

    /**
     * [onHome]: the apps of Home's apps row that the TV can open, so the row hides the others.
     * [homeLabel]: the name of the TV's own home app (SystemRoutes.stockHomeApp), for its System tile.
     */
    data class Groups(
        val other: List<InstalledApp>,
        val playStore: InstalledApp?,
        val onHome: Set<String> = emptySet(),
        val homeLabel: String? = null,
    )

    /** Reads the package manager. Slow (icons), so call it off the main thread. */
    fun read(context: Context, onHome: Set<String>, iconPx: Int): Groups {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map { it.toApp(context, iconPx) }
        val home = SystemRoutes.stockHomeApp(context)
        val system = SYSTEM_SETTINGS + SystemRoutes.GOOGLE_TV_HOME + listOfNotNull(home?.first)
        val left = apps.filter { it.packageName !in onHome && it.packageName !in system }
        val collator = Collator.getInstance(Locale.UK)
        return Groups(
            other = left.filter { it.packageName != PLAY_STORE }.sortedWith(compareBy(collator) { it.label }),
            playStore = left.firstOrNull { it.packageName == PLAY_STORE },
            // The same test as the tile's own: an app that no intent opens has no tile.
            onHome = onHome.filterTo(mutableSetOf()) { pm.getLeanbackLaunchIntentForPackage(it) != null || pm.getLaunchIntentForPackage(it) != null },
            homeLabel = home?.second,
        )
    }

    private fun ResolveInfo.toApp(context: Context, iconPx: Int): InstalledApp {
        val pm = context.packageManager
        val icon = runCatching { loadIcon(pm).toBitmap(iconPx, iconPx).asImageBitmap().also { it.prepareToDraw() } }.getOrNull()
        return InstalledApp(activityInfo.packageName, loadLabel(pm).toString(), icon)
    }
}
