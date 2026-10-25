package app.opentvlauncher.system

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf

/** Installs [apps] in Robolectric's package manager, as on the TVs, where both are installed. */
fun installTitleApps(vararg apps: TitleApp = TitleApp.entries.toTypedArray()) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    apps.forEach { app ->
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = app.packageName
                applicationInfo = ApplicationInfo().apply {
                    packageName = app.packageName
                    name = app.label
                }
            },
        )
    }
}

/** The title apps as MainActivity reads them, after [installTitleApps]. */
fun readTitleApps(): TitleAppIcons = TitleAppIcons.read(ApplicationProvider.getApplicationContext(), 44)
