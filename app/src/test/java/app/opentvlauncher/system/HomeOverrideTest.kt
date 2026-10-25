package app.opentvlauncher.system

import app.opentvlauncher.system.HomeOverride.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Home override's rules (home-override artifact, "Option A design"). */
class HomeOverrideTest {
    private var now = 100_000L
    private val rules = HomeOverride(OWN, OWN_ACTIVITY) { now }

    private fun googleHome(passedAt: Long? = null) = rules.onWindow(SystemRoutes.GOOGLE_TV_HOME, HomeOverride.GOOGLE_HOME_ACTIVITY) { passedAt }

    private fun window(packageName: String, className: String) = rules.onWindow(packageName, className) { null }

    private fun launcherArrives() = window(OWN, OWN_ACTIVITY)

    @Test
    fun googlesHomeScreenBringsTheLauncherBack() {
        assertEquals(Step.Start, googleHome())
    }

    @Test
    fun otherScreensStay() {
        // Voice search results and other Google TV Home screens, and every other app.
        assertEquals(Step.None, window(SystemRoutes.GOOGLE_TV_HOME, VOICE_RESULTS))
        assertEquals(Step.None, window("com.google.android.youtube.tv", "com.google.android.apps.youtube.tv.activity.ShellActivity"))
        assertEquals(Step.None, rules.onWindow(null, null) { null })
    }

    /** Review O1: a start asked for is dropped when any other screen comes to the front first. */
    @Test
    fun anotherScreenCancelsARequest() {
        googleHome()
        val request = rules.request(explicit = false)
        assertTrue(rules.stillWanted(request))
        // The volume panel and a toast float over it and change nothing.
        window("com.android.systemui", "android.widget.FrameLayout")
        window("com.google.android.gms", "android.widget.Toast\$TN")
        assertTrue(rules.stillWanted(request))
        window("com.netflix.ninja", "com.netflix.ninja.MainActivity")
        assertFalse(rules.stillWanted(request))
    }

    /** Review O1: another Google TV Home screen clears Google's home, so a later check starts nothing. */
    @Test
    fun anotherGoogleScreenEndsGooglesHome() {
        googleHome()
        assertTrue(rules.stillOnGoogleHome())
        window(SystemRoutes.GOOGLE_TV_HOME, VOICE_RESULTS)
        assertFalse(rules.stillOnGoogleHome())
        // A view inside Google's home screen is not a new screen.
        googleHome()
        window(SystemRoutes.GOOGLE_TV_HOME, "android.widget.FrameLayout")
        assertTrue(rules.stillOnGoogleHome())
    }

    /** Google Launcher in All Apps opens Google's home on purpose. It stays until another app shows. */
    @Test
    fun googlesHomeOpenedOnPurposeStaysUntilAnotherApp() {
        assertEquals(Step.None, googleHome(passedAt = now - 2_000))
        now += 60_000
        assertEquals(Step.None, googleHome(passedAt = now - 62_000))
        window("com.android.systemui", "android.widget.FrameLayout")
        assertEquals(Step.None, googleHome(passedAt = now - 62_000))
        window("com.google.android.youtube.tv", "ShellActivity")
        assertEquals(Step.Start, googleHome(passedAt = now - 62_000))
    }

    @Test
    fun anOldPassDoesNotKeepGooglesHome() {
        assertEquals(Step.Start, googleHome(passedAt = now - 11_000))
    }

    @Test
    fun theHomeKeyAlwaysBringsTheLauncherBack() {
        assertEquals(Step.None, googleHome(passedAt = now))
        assertTrue(rules.onHomeKey(heldMillis = 120, canceled = false))
        // The pass kept one visit only.
        assertEquals(Step.Start, googleHome(passedAt = now))
    }

    /** A long press of Home opens the TV's own panel, which stays. */
    @Test
    fun aLongOrCanceledHomePressDoesNothing() {
        assertFalse(rules.onHomeKey(heldMillis = 600, canceled = false))
        assertFalse(rules.onHomeKey(heldMillis = 100, canceled = true))
    }

    /**
     * On the Chromecast, Google's home reported its window 0.3 s after the launcher's start, from
     * the same Home press. That is a check, not a second start.
     */
    @Test
    fun googlesHomeJustAfterAStartIsACheck() {
        assertTrue(rules.mayStart(explicit = true))
        now += 300
        assertEquals(Step.Check, googleHome())
        assertTrue(rules.stillOnGoogleHome())
        launcherArrives()
        assertFalse(rules.stillOnGoogleHome())
        now += 1_500
        assertEquals(Step.Start, googleHome())
    }

    /** Review O2: the service connects over Google's home only when that screen is known, and the pass still counts. */
    @Test
    fun aConnectionStartsOnlyOverAKnownGoogleHome() {
        assertEquals(Step.None, rules.onConnected(googleHomeShown = false) { null })
        assertEquals(Step.None, rules.onConnected(googleHomeShown = true) { now - 1_000 })
        assertEquals(Step.Start, HomeOverride(OWN, OWN_ACTIVITY) { now }.onConnected(googleHomeShown = true) { null })
    }

    /** Review O3: Home key presses never count towards a loop. */
    @Test
    fun homePressesAreNeverALoop() {
        repeat(20) {
            assertTrue(rules.mayStart(explicit = true))
            now += 500
        }
    }

    /** Review O3: automatic starts that keep losing the front within 2 s are a loop. */
    @Test
    fun aLauncherThatKeepsBeingCoveredIsALoop() {
        repeat(4) {
            assertTrue(rules.mayStart(explicit = false))
            now += 300
            launcherArrives()
            now += 300
        }
        assertFalse(rules.mayStart(explicit = false))
    }

    /** Review N1: 600 ms in front each time, with the time on Google's home after it, is still a loop. */
    @Test
    fun timeOnGooglesHomeDoesNotCountAsTimeInFront() {
        assertTrue(rules.mayStart(explicit = false))
        repeat(4) { index ->
            launcherArrives()
            now += 600
            assertEquals(Step.Check, googleHome())
            now += HomeOverride.SETTLE_MILLIS
            assertTrue(rules.stillOnGoogleHome())
            if (index < 3) assertTrue(rules.mayStart(explicit = false)) else assertFalse(rules.mayStart(explicit = false))
        }
    }

    /** Review N1: an old arrival does not clear the count for starts that never reach the front. */
    @Test
    fun anOldArrivalDoesNotHideALoop() {
        launcherArrives()
        now += 3_000
        window("com.netflix.ninja", "com.netflix.ninja.MainActivity")
        repeat(4) {
            googleHome()
            assertTrue(rules.mayStart(explicit = false))
            now += 1_800
        }
        googleHome()
        assertFalse(rules.mayStart(explicit = false))
    }

    /** Review Q2: 300 ms on the launcher, then an app, then Back to Google's home, again and again, is not a loop. */
    @Test
    fun anAppBetweenReturnsIsNotALoop() {
        repeat(10) { visit ->
            googleHome()
            assertTrue("return ${visit + 1}", rules.mayStart(explicit = false))
            launcherArrives()
            now += 300
            window("com.netflix.ninja", "com.netflix.ninja.MainActivity")
            now += 1_300
        }
    }

    /** Another Google TV Home screen is not another app: the back and forth still counts. */
    @Test
    fun anotherGoogleScreenBetweenStartsIsStillALoop() {
        repeat(4) {
            googleHome()
            assertTrue(rules.mayStart(explicit = false))
            launcherArrives()
            now += 300
            window(SystemRoutes.GOOGLE_TV_HOME, VOICE_RESULTS)
            now += 300
        }
        googleHome()
        assertFalse(rules.mayStart(explicit = false))
    }

    /** Home presses in between neither count nor clear the count. */
    @Test
    fun homePressesLeaveTheCountAsItIs() {
        repeat(4) {
            assertTrue(rules.mayStart(explicit = false))
            assertTrue(rules.mayStart(explicit = true))
            now += 500
        }
        assertFalse(rules.mayStart(explicit = false))
    }

    /** The launcher in front for 2 s, then covered: its starts worked, and the count starts again. */
    @Test
    fun twoSecondsInFrontThenACoverClearsTheCount() {
        repeat(4) { assertTrue(rules.mayStart(explicit = false)) }
        launcherArrives()
        now += 2_000
        googleHome()
        repeat(4) { assertTrue(rules.mayStart(explicit = false)) }
        assertFalse(rules.mayStart(explicit = false))
    }

    /** An automatic start after the launcher stayed in front for 2 s starts a new count. */
    @Test
    fun aLauncherThatStaysInFrontClearsTheCount() {
        repeat(10) {
            assertTrue("start ${it + 1}", rules.mayStart(explicit = false))
            now += 300
            launcherArrives()
            now += 2_000
        }
    }

    private companion object {
        const val OWN = "app.opentvlauncher"
        const val OWN_ACTIVITY = "app.opentvlauncher.MainActivity"
        const val VOICE_RESULTS = "com.google.android.apps.tv.launcherx.gateway.LauncherDeepLink"
    }
}
