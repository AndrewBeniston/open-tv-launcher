package app.opentvlauncher.system

import android.content.Context
import android.os.SystemClock
import java.io.File

/**
 * The rules of the Home override (home-override artifact, "Option A design"), apart from the
 * service so that tests can run them. Google's home screen is Google TV Home's HomeActivity. Its
 * other screens, such as voice search results and the profile screens, stay in front, and so does
 * every other app.
 *
 * Every start waits a moment ([DELAY_MILLIS]) as a [Request]. Any other screen that comes to the
 * front before then, such as Netflix from the remote, a cast or Settings, cancels it
 * ([stillWanted]).
 *
 * [ownPackage] and [ownActivity] name the launcher, whose window shows that a start arrived.
 */
class HomeOverride(
    private val ownPackage: String,
    private val ownActivity: String,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    /** What the service does about a window: nothing, start the launcher, or check again later. */
    enum class Step { None, Start, Check }

    /** A start the service will make: [explicit] for the Home key, and the front screen it was asked for. */
    data class Request(val explicit: Boolean, val front: Int)

    /** Goes up each time a screen other than Google's home comes to the front. */
    private var front = 0

    /** Whether Google's home screen was the last screen to come to the front. */
    private var googleHomeInFront = false

    /** Google's home, opened on purpose from All Apps. It stays until another app or the Home key. */
    private var staying = false

    /** Each pass keeps one visit only. */
    private var usedPass: Long? = null

    private var lastStart: Long? = null

    /** Since when the launcher has been in front without a break, or null while another screen is. */
    private var inFrontSince: Long? = null

    /**
     * Automatic starts with only the launcher and Google's home in front between them, since the
     * launcher last stayed in front for 2 s: a run of these is a loop.
     */
    private val automaticStarts = ArrayDeque<Long>()

    /**
     * A window came to the front. The launcher comes back over Google's home screen, unless the
     * launcher opened it on purpose in the last 10 s ([passedAt]).
     *
     * Google's home reports its window about half a second after it resumes, so after a Home
     * press its window often comes after the launcher's start. Within 1.5 s of a start the answer
     * is [Step.Check]: look again later, and start the launcher only if Google's home is still in
     * front ([stillOnGoogleHome]).
     */
    fun onWindow(packageName: CharSequence?, className: CharSequence?, passedAt: () -> Long?): Step {
        val name = className?.toString()
        when {
            // The volume panel, notifications and toasts float over the screen in front.
            packageName == null || packageName == SYSTEM_UI || name?.contains("Toast") == true -> return Step.None
            packageName == ownPackage -> {
                // Each arrival starts the 2 s again: it may follow a cover this service did not see.
                if (name == ownActivity) inFrontSince = clock()
                leaveGoogleHome()
                staying = false
                return Step.None
            }
            packageName != SystemRoutes.GOOGLE_TV_HOME -> {
                // Another app in front ends any back and forth between the launcher and Google's home.
                inFrontSince = null
                automaticStarts.clear()
                leaveGoogleHome()
                staying = false
                return Step.None
            }
            name == GOOGLE_HOME_ACTIVITY -> leaveLauncher()
            // Another Google TV Home screen, such as voice search results.
            name?.startsWith("${SystemRoutes.GOOGLE_TV_HOME}.") == true -> {
                leaveLauncher()
                leaveGoogleHome()
                return Step.None
            }
            // A view inside the Google TV Home screen that is already in front.
            else -> return Step.None
        }
        googleHomeInFront = true
        if (passed(passedAt)) return Step.None
        val last = lastStart
        return if (last != null && clock() - last < SETTLE_MILLIS) Step.Check else Step.Start
    }

    /**
     * The service connected. [googleHomeShown] is true only when the active window is known to be
     * Google's home screen. A Google TV Home screen that cannot be named waits for its next
     * window, as does Google's home opened on purpose.
     */
    fun onConnected(googleHomeShown: Boolean, passedAt: () -> Long?): Step {
        if (!googleHomeShown) return Step.None
        googleHomeInFront = true
        return if (passed(passedAt)) Step.None else Step.Start
    }

    /** True while Google's home stays on purpose: the pass is new, or the visit it began goes on. */
    private fun passed(passedAt: () -> Long?): Boolean {
        val passed = passedAt()
        if (passed != null && passed != usedPass && clock() - passed in 0..PASS_MILLIS) {
            staying = true
            usedPass = passed
        }
        return staying
    }

    private fun leaveGoogleHome() {
        googleHomeInFront = false
        front++
    }

    /** Another screen covers the launcher. If it had stayed in front for 2 s, its starts worked: the count starts again. */
    private fun leaveLauncher() {
        val since = inFrontSince ?: return
        if (clock() - since >= STABLE_MILLIS) automaticStarts.clear()
        inFrontSince = null
    }

    /**
     * The Home key, on release. True when the launcher comes back: always, after a short press.
     * A long press opens the TV's own panel, which stays.
     */
    fun onHomeKey(heldMillis: Long, canceled: Boolean): Boolean {
        if (canceled || heldMillis >= LONG_PRESS_MILLIS) return false
        staying = false
        return true
    }

    /** A start to make after [DELAY_MILLIS], for the screen in front now. */
    fun request(explicit: Boolean) = Request(explicit, front)

    /** True when no other screen has come to the front since [request] was made. */
    fun stillWanted(request: Request) = request.front == front

    /** For a [Step.Check]: true when Google's home is still in front, so the launcher starts again. */
    fun stillOnGoogleHome(): Boolean = googleHomeInFront && !staying

    /**
     * Counts a start. A Home key press is asked for: it always starts and is left out of the
     * count. An automatic start counts towards a loop until another app comes to the front, or the
     * launcher stays in front for 2 s without a break. Time on Google's home, or an arrival from
     * before it, does not count as time in front. The fifth automatic start in 10 s with neither
     * is a loop (false), and the override stops.
     */
    fun mayStart(explicit: Boolean): Boolean {
        val now = clock()
        lastStart = now
        if (explicit) return true
        // Still in front after 2 s: that counts once, and the next 2 s start from here.
        val since = inFrontSince
        if (since != null && now - since >= STABLE_MILLIS) {
            automaticStarts.clear()
            inFrontSince = now
        }
        while (automaticStarts.isNotEmpty() && now - automaticStarts.first() > LOOP_MILLIS) automaticStarts.removeFirst()
        if (automaticStarts.size >= LOOP_STARTS) return false
        automaticStarts.addLast(now)
        return true
    }

    companion object {
        const val GOOGLE_HOME_ACTIVITY = "com.google.android.apps.tv.launcherx.home.HomeActivity"
        private const val SYSTEM_UI = "com.android.systemui"

        /**
         * After Google's home has started, so the launcher comes up over it, not under it. On
         * the Chromecast Google's home resumes about 45 ms after the Home key.
         */
        const val DELAY_MILLIS = 120L

        const val SETTLE_MILLIS = 1_500L
        const val PASS_MILLIS = 10_000L
        const val LONG_PRESS_MILLIS = 500L
        const val STABLE_MILLIS = 2_000L
        const val LOOP_STARTS = 4
        const val LOOP_MILLIS = 10_000L
    }
}

/**
 * The launcher's note that it opened Google's home on purpose (All Apps, Google Launcher), so that
 * the override lets it stay. The service runs in its own process, so the note is a file.
 */
object GoogleHomePass {
    private fun file(context: Context) = File(context.noBackupFilesDir, "google-home-pass")

    fun give(context: Context) = file(context).writeText(SystemClock.elapsedRealtime().toString())

    /** When the launcher last gave the pass, on the elapsedRealtime clock, or null. */
    fun given(context: Context): Long? = file(context).takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()
}
