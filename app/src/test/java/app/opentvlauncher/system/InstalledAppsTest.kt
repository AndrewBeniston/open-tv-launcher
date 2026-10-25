package app.opentvlauncher.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** All Apps' groups: Other Apps leaves out Home's apps and the System group, in label order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstalledAppsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun addTvApp(packageName: String, label: String, category: String = Intent.CATEGORY_LEANBACK_LAUNCHER) {
        val info = ActivityInfo().apply {
            this.packageName = packageName
            name = "$packageName.Main"
            nonLocalizedLabel = label
            exported = true
            applicationInfo = ApplicationInfo().apply {
                this.packageName = packageName
                nonLocalizedLabel = label
            }
        }
        shadowOf(context.packageManager).apply {
            addOrUpdateActivity(info)
            addIntentFilterForActivity(
                ComponentName(packageName, info.name),
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(category) },
            )
        }
    }

    @Test
    fun otherAppsAreTheRestOfTheTvAppsInLabelOrder() {
        addTvApp("com.google.android.youtube.tv", "YouTube")
        addTvApp("com.netflix.ninja", "Netflix")
        addTvApp("com.channel4.ondemand", "Channel 4")
        addTvApp("com.android.tv.settings", "Settings")
        addTvApp(SystemRoutes.GOOGLE_TV_HOME, "Google TV Home")
        addTvApp(InstalledApps.PLAY_STORE, "Google Play Store")

        val groups = InstalledApps.read(context, onHome = setOf("com.google.android.youtube.tv"), iconPx = 44)

        assertEquals(listOf("Channel 4", "Netflix"), groups.other.map { it.label })
        assertEquals(InstalledApps.PLAY_STORE, groups.playStore?.packageName)
    }

    /** 25 Oct 2026: the apps row shows only the apps that a tile can open. */
    @Test
    fun onHomeHasOnlyTheAppsThatTheTvCanOpen() {
        addTvApp("com.google.android.youtube.tv", "YouTube")
        addTvApp("com.netflix.ninja", "Netflix")
        val groups = InstalledApps.read(context, onHome = setOf("com.google.android.youtube.tv", "com.stremio.one", "com.netflix.ninja"), iconPx = 44)
        assertEquals(setOf("com.google.android.youtube.tv", "com.netflix.ninja"), groups.onHome)
    }

    /**
     * 25 Oct 2026: the System tile names the TV's own home app, such as Android TV Home on a TV
     * without Google TV Home. That app is not in Other Apps too.
     */
    @Test
    fun theTvsOwnHomeAppIsNamedAndIsNotAnOtherApp() {
        addTvApp("com.google.android.tvlauncher", "Android TV Home", category = Intent.CATEGORY_HOME)
        addTvApp("com.google.android.tvlauncher", "Android TV Home")
        addTvApp("com.channel4.ondemand", "Channel 4")
        val groups = InstalledApps.read(context, onHome = emptySet(), iconPx = 44)
        assertEquals("Android TV Home", groups.homeLabel)
        assertEquals(listOf("Channel 4"), groups.other.map { it.label })
    }
}
