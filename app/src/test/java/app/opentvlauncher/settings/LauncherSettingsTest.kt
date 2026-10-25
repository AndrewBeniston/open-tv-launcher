package app.opentvlauncher.settings

import app.opentvlauncher.trakt.tempStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSettingsTest {
    @Test
    fun selectingATitleStartsOnShowDetailsAndKeepsAChange() = runBlocking {
        val store = tempStore()
        assertEquals(TitleAction.ShowDetails, LauncherSettings(store).titleAction.first())
        LauncherSettings(store).setTitleAction(TitleAction.OpenInStremio)
        assertEquals(TitleAction.OpenInStremio, LauncherSettings(store).titleAction.first())
    }

    /** 25 Oct 2026: without Stremio, OK opens Details. With Stremio, the setting counts. */
    @Test
    fun withoutStremioATitleOpensDetails() {
        assertEquals(TitleAction.ShowDetails, TitleAction.OpenInStremio.withStremio(installed = false))
        assertEquals(TitleAction.OpenInStremio, TitleAction.OpenInStremio.withStremio(installed = true))
        assertEquals(TitleAction.ShowDetails, TitleAction.ShowDetails.withStremio(installed = true))
    }
}
