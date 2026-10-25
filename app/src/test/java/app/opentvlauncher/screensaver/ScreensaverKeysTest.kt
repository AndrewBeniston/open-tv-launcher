package app.opentvlauncher.screensaver

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** 24 Oct 2026: the screensaver was slow to close on Back. Any key now closes it at once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreensaverKeysTest {
    private fun screensaver() = Robolectric.buildService(ScreensaverService::class.java).create().get()

    /** DreamService's gentle wake sets mWaking. finish() closes the screensaver without it. */
    private fun ScreensaverService.wokeGently() = ReflectionHelpers.getField<Boolean>(this, "mWaking")

    @Test
    fun anyKeyClosesTheScreensaverAtOnce() {
        for (key in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_HOME)) {
            val screensaver = screensaver()
            assertTrue(screensaver.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)))
            assertTrue("key $key", shadowOf(screensaver).isStoppedBySelf)
            assertFalse("key $key", screensaver.wokeGently())
        }
    }

    @Test
    fun theKeyIsNotPassedOn() {
        val screensaver = screensaver()
        assertTrue(screensaver.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)))
        assertFalse(shadowOf(screensaver).isStoppedBySelf)
    }
}
