package app.opentvlauncher.system

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Netflix button opens the title's page in Netflix's TV app (25 Oct 2026). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetflixRouteTest {
    @Test
    fun theNetflixButtonOpensTheTitlePageInNetflixsTvApp() {
        val intent = SystemRoutes.netflixTitle("81424086")!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://www.netflix.com/title/81424086", intent.dataString)
        assertEquals("com.netflix.ninja", intent.`package`)
    }

    /**
     * The form that opened the title page on the TV, with Netflix running and with it stopped: the
     * extra source=30 and no flags, so a running Netflix is not restarted.
     */
    @Test
    fun theLinkCarriesSourceThirtyAndNoFlags() {
        val intent = SystemRoutes.netflixTitle("81682935")!!
        assertEquals(setOf("source"), intent.extras!!.keySet())
        assertEquals("30", intent.getStringExtra("source"))
        assertEquals(0, intent.flags)
        assertNull(intent.component)
        assertNull(intent.categories)
    }

    @Test
    fun anIdThatIsNotANumberOpensNothing() {
        assertNull(SystemRoutes.netflixTitle("81424086/../watch/1"))
        assertNull(SystemRoutes.netflixTitle(""))
    }
}
