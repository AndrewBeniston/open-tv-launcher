package app.opentvlauncher.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import app.opentvlauncher.home.HomeMapper
import org.robolectric.Shadows.shadowOf

/**
 * Installs the apps row's apps ([packages]) in Robolectric's package manager as TV apps, each
 * with a TV launcher activity, as on the TVs. Without them the apps row shows only All Apps.
 */
fun installHomeApps(vararg packages: String = HomeMapper.apps.mapNotNull { it.packageName }.toTypedArray()) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    packages.forEach { packageName ->
        val label = HomeMapper.apps.firstOrNull { it.packageName == packageName }?.label ?: packageName
        val info = ActivityInfo().apply {
            this.packageName = packageName
            name = "$packageName.Main"
            nonLocalizedLabel = label
            exported = true
            applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
        }
        shadowOf(context.packageManager).apply {
            addOrUpdateActivity(info)
            addIntentFilterForActivity(
                ComponentName(packageName, info.name),
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) },
            )
        }
    }
}
