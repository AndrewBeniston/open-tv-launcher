package app.opentvlauncher.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** No Home registration before gate 2, and a way back that works today. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemRoutesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    private fun addHomeApp(packageName: String, activity: String) {
        val component = ComponentName(packageName, activity)
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(component)
            addIntentFilterForActivity(
                component,
                IntentFilter(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addCategory(Intent.CATEGORY_DEFAULT)
                },
            )
        }
    }

    @Test
    fun theLauncherIsNotAHomeAppUntilGateTwo() {
        val homes = context.packageManager.queryIntentActivities(homeIntent, 0)
        assertTrue(homes.none { it.activityInfo.packageName == context.packageName })
    }

    @Test
    fun theLauncherIsStillInTheTvAppsList() {
        val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(context.packageName)
        val found = context.packageManager.queryIntentActivities(leanback, 0)
        assertEquals(listOf("app.opentvlauncher.MainActivity"), found.map { it.activityInfo.name })
    }

    @Test
    fun stockHomeOpensGoogleTvHome() {
        addHomeApp("com.example.otherhome", "com.example.otherhome.Home")
        addHomeApp(SystemRoutes.GOOGLE_TV_HOME, "com.google.android.apps.tv.launcherx.home.HomeActivity")
        val intent = SystemRoutes.stockHome(context)!!
        assertEquals(SystemRoutes.GOOGLE_TV_HOME, intent.component?.packageName)
        assertTrue(intent.hasCategory(Intent.CATEGORY_HOME))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun stockHomeUsesAnotherHomeAppWithoutGoogleTv() {
        addHomeApp("com.example.philipshome", "com.example.philipshome.Home")
        assertEquals("com.example.philipshome", SystemRoutes.stockHome(context)?.component?.packageName)
    }

    @Test
    fun stockHomeNeverChoosesThisLauncher() {
        addHomeApp(context.packageName, "app.opentvlauncher.MainActivity")
        assertNull(SystemRoutes.stockHome(context))
    }

    /** Android 14 sends Home to the setup recovery screen when Google TV Home is off. It is never the way back. */
    @Test
    fun stockHomeNeverChoosesTheRecoveryOrFallbackScreen() {
        addHomeApp("com.google.android.tungsten.setupwraith", "com.google.android.tungsten.setupwraith.RecoveryActivity")
        addHomeApp("com.android.tv.settings", "com.android.tv.settings.system.FallbackHome")
        assertNull(SystemRoutes.stockHome(context))
    }

    /** Gate 2 failed on the Chromecast (24 Oct 2026): the launcher is never a Home candidate. */
    @Test
    fun theLauncherHasNoHomeIntentFilter() {
        val homes = context.packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DISABLED_COMPONENTS)
        assertTrue(homes.none { it.activityInfo.packageName == context.packageName })
    }

    @Test
    fun settingsOpensSystemSettings() {
        assertEquals(Settings.ACTION_SETTINGS, SystemRoutes.settings().action)
    }
}
