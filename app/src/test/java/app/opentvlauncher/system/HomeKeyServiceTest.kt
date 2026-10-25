package app.opentvlauncher.system

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.opentvlauncher.MainActivity
import app.opentvlauncher.OfflineLauncherApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** The Home override service: what starts the launcher, when, and how. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OfflineLauncherApp::class)
class HomeKeyServiceTest {
    private val service = Robolectric.buildService(HomeKeyService::class.java).create().get()
    private val app = RuntimeEnvironment.getApplication()

    private fun windowEvent(packageName: String, className: String) = AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
        this.packageName = packageName
        this.className = className
    }

    private fun googleHomeComesUp() = service.onAccessibilityEvent(windowEvent(SystemRoutes.GOOGLE_TV_HOME, HomeOverride.GOOGLE_HOME_ACTIVITY))

    private fun homeKey(action: Int): Boolean {
        val down = SystemClock.uptimeMillis()
        return service.onKeyEvent(KeyEvent(down, down + if (action == KeyEvent.ACTION_UP) 100 else 0, action, KeyEvent.KEYCODE_HOME, 0))
    }

    private fun pass(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun nextStart(): Intent? = shadowOf(app).nextStartedActivity

    @Test
    fun googlesHomeBringsTheLauncherBackALittleLater() {
        googleHomeComesUp()
        pass(HomeOverride.DELAY_MILLIS - 20)
        assertNull(nextStart())
        pass(20)
        val intent = nextStart()!!
        assertEquals(ComponentName(app, MainActivity::class.java), intent.component)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertNull(nextStart())
    }

    /** As the Home process, the launcher would block Google TV Home's starts and the remote's app buttons. */
    @Test
    fun theLauncherStartsWithAnExplicitIntentNotAHomeIntent() {
        googleHomeComesUp()
        pass(250)
        val intent = nextStart()!!
        assertNull(intent.action)
        assertNull(intent.categories)
    }

    @Test
    fun theHomeKeyGoesOnToAndroidAndBringsTheLauncherBack() {
        assertFalse(homeKey(KeyEvent.ACTION_DOWN))
        pass(250)
        assertNull(nextStart())
        assertFalse(homeKey(KeyEvent.ACTION_UP))
        pass(250)
        assertEquals(ComponentName(app, MainActivity::class.java), nextStart()!!.component)
        // Google's home reports its window late, from the same press, then the launcher's window comes.
        googleHomeComesUp()
        launcherArrives()
        pass(2_000)
        assertNull(nextStart())
    }

    /** When the launcher's start lands under Google's home, the check starts it again. */
    @Test
    fun aStartThatLandsUnderGooglesHomeIsMadeAgain() {
        homeKey(KeyEvent.ACTION_UP)
        pass(250)
        assertTrue(nextStart() != null)
        googleHomeComesUp()
        pass(HomeOverride.SETTLE_MILLIS - 100)
        assertNull(nextStart())
        pass(100)
        assertTrue(nextStart() != null)
    }

    @Test
    fun otherKeysAndScreensDoNothing() {
        val down = SystemClock.uptimeMillis()
        assertFalse(service.onKeyEvent(KeyEvent(down, down, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0)))
        service.onAccessibilityEvent(windowEvent(SystemRoutes.GOOGLE_TV_HOME, VOICE_RESULTS))
        service.onAccessibilityEvent(windowEvent("com.netflix.ninja", "com.netflix.ninja.MainActivity"))
        pass(1_000)
        assertNull(nextStart())
    }

    /** All Apps, Google Launcher: the launcher gives a pass, so Google's home stays. */
    @Test
    fun googlesHomeOpenedFromAllAppsStays() {
        GoogleHomePass.give(app)
        googleHomeComesUp()
        pass(1_000)
        assertNull(nextStart())
    }

    private fun launcherArrives() = service.onAccessibilityEvent(windowEvent(app.packageName, MainActivity::class.java.name))

    /** Review O3: Google's home that keeps coming back over the launcher is a loop, and the service stops. */
    @Test
    fun aLauncherThatKeepsBeingCoveredStopsAfterFourStarts() {
        repeat(6) {
            googleHomeComesUp()
            pass(HomeOverride.SETTLE_MILLIS + 100)
            launcherArrives()
            pass(200)
        }
        repeat(4) { assertTrue(nextStart() != null) }
        assertNull(nextStart())
    }

    /** Review Q2: returns to Google's home from an app, with a short stop on the launcher before each app, never turn the service off. */
    @Test
    fun appReturnsToGooglesHomeNeverTurnTheServiceOff() {
        repeat(10) { visit ->
            googleHomeComesUp()
            pass(HomeOverride.DELAY_MILLIS)
            assertTrue("return ${visit + 1}", nextStart() != null)
            launcherArrives()
            pass(300)
            // An app opens, and Back leaves it. No Home key is pressed.
            service.onAccessibilityEvent(windowEvent("com.netflix.ninja", "com.netflix.ninja.MainActivity"))
            pass(1_300)
        }
    }

    /** Home, an app within 2 s, then Home again, twenty times: every press brings the launcher back. */
    @Test
    fun quickHomeAndAppCyclesNeverTurnTheServiceOff() {
        googleHomeComesUp()
        pass(HomeOverride.DELAY_MILLIS)
        assertTrue(nextStart() != null)
        launcherArrives()
        repeat(20) {
            pass(300)
            service.onAccessibilityEvent(windowEvent("com.netflix.ninja", "com.netflix.ninja.MainActivity"))
            pass(200)
            homeKey(KeyEvent.ACTION_UP)
            pass(45)
            googleHomeComesUp()
            pass(HomeOverride.DELAY_MILLIS)
            assertTrue("Home cycle ${it + 1}", nextStart() != null)
            launcherArrives()
        }
    }

    /** Review O3: five Home presses, 2 s apart, all bring the launcher back. */
    @Test
    fun fiveHomePressesAllWork() {
        repeat(5) {
            homeKey(KeyEvent.ACTION_UP)
            pass(250)
            assertTrue("Home press ${it + 1}", nextStart() != null)
            launcherArrives()
            pass(1_750)
        }
    }

    /** Review O1: an app that opens before the start is made stays in front: Netflix, a cast, Settings. */
    @Test
    fun aStartWaitingDoesNotCoverAnAppThatOpened() {
        listOf("com.netflix.ninja", "com.google.android.apps.mediashell", "com.android.tv.settings").forEach { app ->
            googleHomeComesUp()
            pass(60)
            service.onAccessibilityEvent(windowEvent(app, "$app.MainActivity"))
            pass(HomeOverride.DELAY_MILLIS)
            assertNull(app, nextStart())
        }
    }

    /** Review O1: Home, then the remote's Netflix button straight away: Netflix stays. */
    @Test
    fun aHomePressThenAnAppButtonLeavesTheApp() {
        homeKey(KeyEvent.ACTION_UP)
        pass(60)
        service.onAccessibilityEvent(windowEvent("com.netflix.ninja", "com.netflix.ninja.MainActivity"))
        pass(2_000)
        assertNull(nextStart())
    }

    /** Review O1: the late check starts nothing over voice search results. */
    @Test
    fun theCheckDoesNotCoverAnotherGoogleScreen() {
        homeKey(KeyEvent.ACTION_UP)
        pass(250)
        assertTrue(nextStart() != null)
        googleHomeComesUp()
        pass(250)
        service.onAccessibilityEvent(windowEvent(SystemRoutes.GOOGLE_TV_HOME, VOICE_RESULTS))
        pass(HomeOverride.SETTLE_MILLIS)
        assertNull(nextStart())
    }

    private fun connectOver(className: String) {
        shadowOf(service).setRootInActiveWindow(
            AccessibilityNodeInfo.obtain().apply {
                packageName = SystemRoutes.GOOGLE_TV_HOME
                this.className = className
            },
        )
        service.onServiceConnected()
        pass(250)
    }

    @Test
    fun connectingOverGooglesHomeBringsTheLauncherBack() {
        connectOver(HomeOverride.GOOGLE_HOME_ACTIVITY)
        assertTrue(nextStart() != null)
    }

    /** Review O2: a Google TV Home screen that is not known to be Google's home waits for its next window. */
    @Test
    fun connectingOverAnotherOrUnknownGoogleScreenStartsNothing() {
        connectOver(VOICE_RESULTS)
        assertNull(nextStart())
        connectOver("android.widget.FrameLayout")
        assertNull(nextStart())
    }

    /** Review O2: Google's home opened on purpose stays when the service connects. */
    @Test
    fun connectingKeepsTheGoogleHomePass() {
        GoogleHomePass.give(app)
        connectOver(HomeOverride.GOOGLE_HOME_ACTIVITY)
        assertNull(nextStart())
    }

    /** It is an accessibility service only the system can bind, in its own process. */
    @Test
    fun theServiceIsDeclaredForTheSystemInItsOwnProcess() {
        val info = app.packageManager.getServiceInfo(ComponentName(app, HomeKeyService::class.java), PackageManager.GET_META_DATA)
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", info.permission)
        assertEquals("${app.packageName}:override", info.processName)
        assertTrue(info.metaData.containsKey("android.accessibilityservice"))
    }

    private companion object {
        const val VOICE_RESULTS = "com.google.android.apps.tv.launcherx.gateway.LauncherDeepLink"
    }
}
