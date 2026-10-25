package app.opentvlauncher.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import app.opentvlauncher.MainActivity

/**
 * Brings the launcher back over Google's home (option A in the home-override artifact). Google TV
 * Home stays enabled and stays the TV's Home app. When its home screen comes to the front, or the
 * Home key is pressed, this service starts the launcher over it 120 ms later. The Home key itself
 * still goes on to Android.
 *
 * It starts the launcher with an explicit intent, never with a Home intent. As the Home process,
 * the launcher would make Android block Google TV Home's own starts, and the remote's YouTube and
 * Netflix buttons would stop working (cleanyourtv #1274).
 *
 * It runs in its own process, so its key filter answers at once while the launcher draws. When
 * automatic starts keep failing to keep the launcher in front, it is a loop: the service turns
 * itself off. Home key presses never count. It is turned on in the TV's accessibility settings
 * or with scripts/home-override.sh. Turning it off there is the whole undo.
 */
class HomeKeyService : AccessibilityService() {
    private val rules by lazy { HomeOverride(packageName, MainActivity::class.java.name) }
    private val handler = Handler(Looper.getMainLooper())
    private var pending: HomeOverride.Request? = null
    private val start = Runnable {
        val request = pending ?: return@Runnable
        pending = null
        if (rules.stillWanted(request)) startLauncher(request.explicit) else Log.i(TAG, "Another screen came to the front: no start")
    }
    private val check = Runnable { if (rules.stillOnGoogleHome()) startLauncher(explicit = false) }

    /**
     * At boot the service connects after Google's home has come up, so it looks at the active
     * window. It starts the launcher only when that window is known to be Google's home screen;
     * otherwise it waits for the next window. Public, not protected, so that tests can connect it.
     */
    public override fun onServiceConnected() {
        val root = rootInActiveWindow
        val shown = root?.packageName == SystemRoutes.GOOGLE_TV_HOME &&
            (root.className == HomeOverride.GOOGLE_HOME_ACTIVITY || windows.any { it.isActive && it.title?.contains(HomeOverride.GOOGLE_HOME_ACTIVITY) == true })
        Log.i(TAG, "Connected over ${root?.packageName}/${root?.className}: Google's home ${if (shown) "known" else "not known"}")
        if (rules.onConnected(shown) { GoogleHomePass.given(this) } == HomeOverride.Step.Start) schedule("connected over Google's home", explicit = false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        when (rules.onWindow(event.packageName, event.className) { GoogleHomePass.given(this) }) {
            HomeOverride.Step.Start -> schedule("Google's home came up", explicit = false)
            HomeOverride.Step.Check -> {
                Log.i(TAG, "Google's home came up just after a start: check in ${HomeOverride.SETTLE_MILLIS} ms")
                handler.removeCallbacks(check)
                handler.postDelayed(check, HomeOverride.SETTLE_MILLIS)
            }
            HomeOverride.Step.None -> Unit
        }
    }

    /** Public, not protected, so that tests can send keys. */
    public override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_HOME && event.action == KeyEvent.ACTION_UP) {
            val canceled = event.flags and KeyEvent.FLAG_CANCELED != 0
            if (rules.onHomeKey(event.eventTime - event.downTime, canceled)) schedule("Home key", explicit = true)
        }
        return false
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(start)
        handler.removeCallbacks(check)
        super.onDestroy()
    }

    /** A start in [HomeOverride.DELAY_MILLIS], for the screen in front now. A Home key press stays asked for. */
    private fun schedule(reason: String, explicit: Boolean) {
        Log.i(TAG, "Launcher in ${HomeOverride.DELAY_MILLIS} ms: $reason")
        pending = rules.request(explicit || pending?.explicit == true)
        handler.removeCallbacks(start)
        handler.postDelayed(start, HomeOverride.DELAY_MILLIS)
    }

    private fun startLauncher(explicit: Boolean) {
        if (!rules.mayStart(explicit)) {
            Log.w(TAG, "The launcher did not stay in front after ${HomeOverride.LOOP_STARTS} starts: the override turns itself off")
            disableSelf()
            return
        }
        Log.i(TAG, "Starting the launcher")
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
    }

    private companion object {
        const val TAG = "HomeOverride"
    }
}
