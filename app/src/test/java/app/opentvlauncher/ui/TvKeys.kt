package app.opentvlauncher.ui

import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.junit4.AndroidComposeTestRule

/**
 * Sends a D-pad key as the TV does: Compose's key handlers see it first. When none of them uses
 * it, [focusManager] moves focus that way, as ViewRootImpl does on a device; without one, focus
 * stays. The test settles the clock and checks the screen itself.
 *
 * A state write from a key handler is applied at once. With the test clock stopped and nothing
 * else running, such as while the banner has focus, it would otherwise wait for an unrelated frame.
 */
fun <A : ComponentActivity> AndroidComposeTestRule<*, A>.sendTvKey(keyCode: Int, focusManager: FocusManager? = null) = runOnUiThread {
    val root = activity.window.decorView.composeRoot() ?: error("The activity shows no Compose content")
    val used = root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    if (!used) direction(keyCode)?.let { focusManager?.moveFocus(it) }
    Snapshot.sendApplyNotifications()
}

/**
 * OK, or another key, held until the remote repeats it: the key down, then its first repeat. The
 * release comes from [releaseTvKey], so a test can look at the screen in between.
 */
fun <A : ComponentActivity> AndroidComposeTestRule<*, A>.holdTvKey(keyCode: Int) = runOnUiThread {
    val root = activity.window.decorView.composeRoot() ?: error("The activity shows no Compose content")
    val time = SystemClock.uptimeMillis()
    root.dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0))
    root.dispatchKeyEvent(KeyEvent(time, time + 500, KeyEvent.ACTION_DOWN, keyCode, 1, 0, 0, 0, KeyEvent.FLAG_LONG_PRESS))
    Snapshot.sendApplyNotifications()
}

/**
 * More repeats of a key held with [holdTvKey], as the remote sends them every 50 ms until the
 * release. On a TV they go on after a long press has moved focus, so they reach the new item.
 */
fun <A : ComponentActivity> AndroidComposeTestRule<*, A>.repeatTvKey(keyCode: Int, times: Int) = runOnUiThread {
    val root = activity.window.decorView.composeRoot() ?: error("The activity shows no Compose content")
    val down = SystemClock.uptimeMillis() - 500
    repeat(times) { root.dispatchKeyEvent(KeyEvent(down, down + 550 + 50L * it, KeyEvent.ACTION_DOWN, keyCode, it + 2)) }
    Snapshot.sendApplyNotifications()
}

/** The key's first down only: the press stays open until [releaseTvKey]. */
fun <A : ComponentActivity> AndroidComposeTestRule<*, A>.downTvKey(keyCode: Int) = runOnUiThread {
    val root = activity.window.decorView.composeRoot() ?: error("The activity shows no Compose content")
    root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    Snapshot.sendApplyNotifications()
}

/** The release. [canceled] is a release Android cancelled, as when the window loses focus during the press. */
fun <A : ComponentActivity> AndroidComposeTestRule<*, A>.releaseTvKey(keyCode: Int, canceled: Boolean = false) = runOnUiThread {
    val root = activity.window.decorView.composeRoot() ?: error("The activity shows no Compose content")
    val time = SystemClock.uptimeMillis()
    root.dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, keyCode, 0, 0, 0, 0, if (canceled) KeyEvent.FLAG_CANCELED else 0))
    Snapshot.sendApplyNotifications()
}

/**
 * The activity's Compose focus manager, for tests of the real activity, which cannot read
 * LocalFocusManager. Compose's view keeps it as its focus owner.
 */
val <A : ComponentActivity> AndroidComposeTestRule<*, A>.activityFocusManager: FocusManager
    get() {
        val root = activity.window.decorView.composeRoot() ?: error("The activity shows no Compose content")
        return root.javaClass.getMethod("getFocusOwner").invoke(root) as FocusManager
    }

/** The view that holds the Compose content, wherever setContent put it. */
private fun View.composeRoot(): View? = when (this) {
    is ViewRootForTest -> this
    is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).composeRoot() }
    else -> null
}

private fun direction(keyCode: Int) = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_UP -> FocusDirection.Up
    KeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.Down
    KeyEvent.KEYCODE_DPAD_LEFT -> FocusDirection.Left
    KeyEvent.KEYCODE_DPAD_RIGHT -> FocusDirection.Right
    else -> null
}
