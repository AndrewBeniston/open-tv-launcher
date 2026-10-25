package app.opentvlauncher.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvChannelsTest {
    @Test
    fun theImdbIdIsFoundInAnAppsLinkOrContentId() {
        assertEquals("tt9100001", TvChannels.imdbId("stremio:///detail/series/tt9100001/tt9100001:2:5", null))
        assertEquals("tt9100005", TvChannels.imdbId(null, "movie-tt9100005"))
        assertNull(TvChannels.imdbId("https://youtu.be/abc", "tt12"))
    }
}
