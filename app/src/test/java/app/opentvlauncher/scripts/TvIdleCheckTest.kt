package app.opentvlauncher.scripts

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * scripts/check-tv-idle.sh, which the screenshot pass runs before it takes over the TV. It must
 * stop when the app in front cannot be read, and when the media dump is empty, missing, an error,
 * a heading with no body, or cut short by a timeout.
 */
class TvIdleCheckTest {
    /** The tests run in the app folder; the scripts are one level up. */
    private val script = listOf(File("../scripts/check-tv-idle.sh"), File("scripts/check-tv-idle.sh")).first { it.exists() }

    private val dir = Files.createTempDirectory("tv-idle").toFile()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** The script's exit code for these dumps. Null leaves that file out. */
    private fun check(activities: String?, media: String? = PAUSED): Int {
        val a = File(dir, "activities").apply { delete(); activities?.let(::writeText) }
        val m = File(dir, "media").apply { delete(); media?.let(::writeText) }
        val builder = ProcessBuilder("bash", script.path, a.path, m.path).redirectErrorStream(true)
        builder.environment()["OPEN_TV_LAUNCHER_ID"] = "app.opentvlauncher"
        val process = builder.start()
        process.inputStream.readBytes()
        return process.waitFor()
    }

    private fun resumed(component: String) = ACTIVITIES_HEADING +
        "  ResumedActivity: ActivityRecord{5f22034 u0 $component t16105}\n      topResumedActivity=ActivityRecord{5f22034 u0 $component t16105}\n"

    private val home = resumed("com.google.android.apps.tv.launcherx/.home.HomeActivity")

    /** No line in the expected form, so the app in front is unknown. */
    @Test
    fun aDumpWithoutTheAppInFrontStops() {
        assertEquals(1, check(ACTIVITIES_HEADING))
        assertEquals(1, check(ACTIVITIES_HEADING + "  mFocusedApp=Token{a1b2 ActivityRecord{c3 u0 com.google.android.youtube.tv/.MainActivity t9}}\n"))
    }

    /** With Google home in front, a media dump that cannot be read still stops. */
    @Test
    fun aMissingEmptyOrErrorMediaDumpStops() {
        assertEquals(1, check(home, media = null))
        assertEquals(1, check(home, media = ""))
        assertEquals(1, check(home, media = "Can't find service: media_session\n"))
        assertEquals(1, check(home, media = MEDIA_HEADING + "Error dumping service info: (java.io.IOException) Broken pipe\n"))
        assertEquals(1, check(home, media = "      state=PlaybackState {state=PAUSED(2), position=0}\n"))
    }

    /** A heading alone, and Android's timeout marker, which dumpsys writes before it exits 0. */
    @Test
    fun aMediaDumpWithoutABodyOrThatTimedOutStops() {
        assertEquals(1, check(home, media = "MEDIA SESSION SERVICE (dumpsys media_session)\n"))
        assertEquals(1, check(home, media = "MEDIA SESSION SERVICE (dumpsys media_session)\n\n*** SERVICE 'media_session' DUMP TIMEOUT (10000ms) EXPIRED ***\n"))
        assertEquals(1, check(home, media = PAUSED + "\n*** SERVICE 'media_session' DUMP TIMEOUT (10000ms) EXPIRED ***\n"))
    }

    /** A session list cut short: two sessions, one state line. */
    @Test
    fun aMediaDumpCutShortStops() {
        assertEquals(1, check(home, media = mediaDump("state=PlaybackState {state=PAUSED(2), position=0}").replace("have 1 sessions", "have 2 sessions")))
    }

    @Test
    fun anUnrecognisedPlaybackStateStops() {
        assertEquals(1, check(home, media = mediaDump("state=PlaybackState {state=WARPING(42), position=0}")))
        assertEquals(1, check(home, media = mediaDump("state=PlaybackState {state=, position=0}")))
        assertEquals(1, check(home, media = mediaDump("state=paused")))
    }

    @Test
    fun bufferingOrSkippingCountsAsPlaying() {
        assertEquals(1, check(home, media = mediaDump("state=PlaybackState {state=BUFFERING(6), position=0}")))
        assertEquals(1, check(home, media = mediaDump("state=null", "state=PlaybackState {state=SKIPPING_TO_NEXT(10), position=0}")))
    }

    /** The idle states Android writes: null, STOPPED, PAUSED and ERROR, as on the Chromecast on 24 Oct 2026. */
    @Test
    fun idleSessionsMayStart() {
        assertEquals(0, check(home, media = mediaDump("state=PlaybackState {state=ERROR(7), position=0}", "state=null", "state=PlaybackState {state=STOPPED(1), position=0}")))
        assertEquals(0, check(home, media = mediaDump("state=PlaybackState {state=2, position=0, buffered position=0}")))
    }

    @Test
    fun aMissingEmptyOrErrorActivityDumpStops() {
        assertEquals(1, check(null))
        assertEquals(1, check(""))
        assertEquals(1, check("Can't find service: activity\n"))
        assertEquals(1, check(home + "Failed to write while dumping service activity: Broken pipe\n"))
    }

    @Test
    fun anotherAppInFrontStops() {
        assertEquals(1, check(resumed("com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.MainActivity")))
    }

    @Test
    fun googleHomeTheLauncherOrTheScreensaverMayStart() {
        assertEquals(0, check(home))
        assertEquals(0, check(resumed("app.opentvlauncher/.MainActivity")))
        assertEquals(0, check(resumed("com.google.android.apps.tv.dreamx/android.service.dreams.DreamActivity")))
    }

    /** A complete dump with no sessions at all: nothing plays. */
    @Test
    fun aCompleteMediaDumpWithNoSessionsMayStart() {
        assertEquals(0, check(home, media = mediaDump()))
    }

    @Test
    fun theAndroid9FormIsReadToo() {
        assertEquals(0, check(ACTIVITIES_HEADING + "    mResumedActivity: ActivityRecord{1c2d u0 com.google.android.apps.tv.launcherx/.home.HomeActivity t4}\n"))
        assertEquals(1, check(ACTIVITIES_HEADING + "    mResumedActivity: ActivityRecord{1c2d u0 com.netflix.ninja/.MainActivity t4}\n"))
    }

    @Test
    fun somethingPlayingStopsEvenOnAHomeScreen() {
        assertEquals(1, check(home, media = mediaDump("state=PlaybackState {state=PLAYING(3), position=1200, speed=1.0}")))
        assertEquals(1, check(home, media = mediaDump("state=PlaybackState {state=3, position=1200, buffered position=0, speed=1.0}")))
    }

    @Test
    fun theScriptIsExecutable() = assertTrue(script.canExecute())

    private companion object {
        const val ACTIVITIES_HEADING = "ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)\nDisplay #0 (activities from top to bottom):\n"
        const val MEDIA_HEADING = "MEDIA SESSION SERVICE (dumpsys media_session)\n"

        /** A complete media dump in the Chromecast's form (24 Oct 2026), with one session for each of [states]. */
        fun mediaDump(vararg states: String) = buildString {
            append(MEDIA_HEADING)
            append("\n8 sessions listeners.\nGlobal priority session is null\nUser Records:\nRecord for full_user=0, profile_user=10\n")
            append("  Media button session is null\n")
            append("  Sessions Stack - have ${states.size} sessions:\n")
            states.forEachIndexed { i, state ->
                append("    Session $i com.example.app$i/Session (userId=0)\n")
                append("      active=false\n      controllers: 0\n")
                append("      $state\n")
                append("      metadata: null\n")
            }
            append("Audio playback (lastly played comes first)\n  uid=10038 packages=com.google.android.apps.tv.launcherx\n")
            append("Media session config:\n  media_button_receiver_fgs_allowlist_duration_ms: [cur: 10000, def: 10000]\n")
        }

        val PAUSED = mediaDump("state=PlaybackState {state=PAUSED(2), position=1200, speed=0.0}")
    }
}
