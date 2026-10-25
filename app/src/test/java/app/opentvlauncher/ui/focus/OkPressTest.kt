package app.opentvlauncher.ui.focus

import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * OK on a card (FocusSurface) and a pill button (review c8a9021, K1): a click needs a press that
 * began on the item and ended there with a real release.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OkPressTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var clicks = 0
    private var holds = 0
    private var pressBegan = 0L

    @Before
    fun setUp() {
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
    }

    /** A card with a long press, or a pill button, above another button. */
    private fun show(pill: Boolean = false) {
        compose.setContent {
            LauncherTheme {
                Column {
                    if (pill) {
                        PillButton("Target", { clicks++ })
                    } else {
                        FocusSurface({ clicks++ }, 12.dp, 12.dp, Modifier.size(140.dp, 80.dp), description = "Target", onLongPress = { holds++ }) {}
                    }
                    PillButton("Other", {})
                }
            }
        }
        focusTarget(pill)
    }

    private fun focusTarget(pill: Boolean) {
        (if (pill) compose.onNodeWithText("Target") else compose.onNodeWithContentDescription("Target")).requestFocus()
        compose.waitForIdle()
    }

    private fun focusOther() {
        compose.onNodeWithText("Other").requestFocus()
        compose.waitForIdle()
    }

    private fun root(view: View): View? = when (view) {
        is ViewRootForTest -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { root(view.getChildAt(it)) }
        else -> null
    }

    /** One key event of the press that began at its first down, as the TV sends it. */
    private fun key(action: Int, repeat: Int = 0, flags: Int = 0, code: Int = KeyEvent.KEYCODE_DPAD_CENTER) {
        compose.runOnUiThread {
            if (action == KeyEvent.ACTION_DOWN && repeat == 0) pressBegan = SystemClock.uptimeMillis()
            root(compose.activity.window.decorView)!!.dispatchKeyEvent(KeyEvent(pressBegan, SystemClock.uptimeMillis(), action, code, repeat, 0, 0, 0, flags))
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
    }

    /**
     * The window gains or loses focus as Android reports it: through its ViewRootImpl, which tells
     * the views and the ViewTreeObserver that Compose's window focus follows.
     */
    private fun windowFocus(focused: Boolean) {
        compose.runOnUiThread {
            val viewRoot = compose.activity.window.decorView.parent
            viewRoot.javaClass.getMethod("windowFocusChanged", Boolean::class.javaPrimitiveType).invoke(viewRoot, focused)
        }
        compose.waitForIdle()
    }

    @Test
    fun aPressAndReleaseClicks() {
        show()
        key(KeyEvent.ACTION_DOWN)
        key(KeyEvent.ACTION_UP)
        assertEquals(1, clicks)
    }

    /** Review K1: Android cancels the release. Nothing is clicked, and the next press works. */
    @Test
    fun aCanceledReleaseDoesNotClickACard() {
        show()
        key(KeyEvent.ACTION_DOWN)
        key(KeyEvent.ACTION_UP, flags = KeyEvent.FLAG_CANCELED)
        assertEquals(0, clicks)
        key(KeyEvent.ACTION_DOWN)
        key(KeyEvent.ACTION_UP)
        assertEquals(1, clicks)
    }

    @Test
    fun aCanceledReleaseDoesNotClickAButton() {
        show(pill = true)
        key(KeyEvent.ACTION_DOWN)
        key(KeyEvent.ACTION_UP, flags = KeyEvent.FLAG_CANCELED)
        assertEquals(0, clicks)
        key(KeyEvent.ACTION_DOWN)
        key(KeyEvent.ACTION_UP)
        assertEquals(1, clicks)
    }

    /** Review K1: the window loses focus and gets it back during the press, with no release. The old press does nothing. */
    @Test
    fun aWindowFocusChangeEndsTheCardsPress() {
        show()
        windowFocus(true)
        key(KeyEvent.ACTION_DOWN)
        windowFocus(false)
        windowFocus(true)
        key(KeyEvent.ACTION_DOWN, repeat = 1)
        key(KeyEvent.ACTION_UP)
        assertEquals(0, holds)
        assertEquals(0, clicks)
        key(KeyEvent.ACTION_DOWN)
        key(KeyEvent.ACTION_UP)
        assertEquals(1, clicks)
    }

    @Test
    fun aWindowFocusChangeEndsTheButtonsPress() {
        show(pill = true)
        windowFocus(true)
        key(KeyEvent.ACTION_DOWN)
        windowFocus(false)
        windowFocus(true)
        key(KeyEvent.ACTION_DOWN, repeat = 1)
        key(KeyEvent.ACTION_UP)
        assertEquals(0, clicks)
    }

    /** A release that never came does not leave the item stuck: the next press clicks. */
    @Test
    fun aFreshPressWorksAfterALostReleaseOnACard() {
        show()
        key(KeyEvent.ACTION_DOWN)
        key(KeyEvent.ACTION_DOWN)
        key(KeyEvent.ACTION_UP)
        assertEquals(1, clicks)
    }

    @Test
    fun aFreshPressWorksAfterALostReleaseOnAButton() {
        show(pill = true)
        key(KeyEvent.ACTION_DOWN)
        key(KeyEvent.ACTION_DOWN)
        key(KeyEvent.ACTION_UP)
        assertEquals(1, clicks)
    }

    /** Focus leaves and comes back during the press: no long press, no click, and the next press works. */
    @Test
    fun focusThatLeavesDuringThePressEndsIt() {
        show()
        key(KeyEvent.ACTION_DOWN)
        focusOther()
        focusTarget(pill = false)
        key(KeyEvent.ACTION_DOWN, repeat = 1)
        key(KeyEvent.ACTION_UP)
        assertEquals(0, holds)
        assertEquals(0, clicks)
        key(KeyEvent.ACTION_DOWN)
        key(KeyEvent.ACTION_UP)
        assertEquals(1, clicks)
    }

    @Test
    fun aReleaseThatWentElsewhereLeavesTheNextPressWorking() {
        show()
        key(KeyEvent.ACTION_DOWN)
        focusOther()
        key(KeyEvent.ACTION_UP)
        focusTarget(pill = false)
        key(KeyEvent.ACTION_DOWN)
        key(KeyEvent.ACTION_UP)
        assertEquals(1, clicks)
        assertEquals(0, holds)
    }

    @Test
    fun aLongPressActsOnceAndDoesNotClick() {
        show()
        key(KeyEvent.ACTION_DOWN)
        repeat(20) { key(KeyEvent.ACTION_DOWN, repeat = it + 1) }
        key(KeyEvent.ACTION_UP)
        assertEquals(1, holds)
        assertEquals(0, clicks)
        key(KeyEvent.ACTION_DOWN)
        key(KeyEvent.ACTION_UP)
        assertEquals(1, clicks)
    }

    @Test
    fun everyOkKeyNeedsItsOwnPress() {
        show()
        listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER).forEach { code ->
            key(KeyEvent.ACTION_DOWN, code = code)
            focusOther()
            focusTarget(pill = false)
            key(KeyEvent.ACTION_DOWN, repeat = 1, code = code)
            key(KeyEvent.ACTION_UP, code = code)
        }
        assertEquals(0, clicks)
        assertEquals(0, holds)
    }

    @Test
    fun theMenuKeyOpensTheLongPressOnce() {
        show()
        key(KeyEvent.ACTION_DOWN, code = KeyEvent.KEYCODE_MENU)
        key(KeyEvent.ACTION_DOWN, repeat = 1, code = KeyEvent.KEYCODE_MENU)
        key(KeyEvent.ACTION_UP, code = KeyEvent.KEYCODE_MENU)
        assertEquals(1, holds)
        assertEquals(0, clicks)
    }
}
