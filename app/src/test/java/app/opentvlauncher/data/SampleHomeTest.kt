package app.opentvlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleHomeTest {
    private val content = SampleHome.content

    @Test
    fun bannerShowsContinueWatchingFirstThenTopPicks() {
        val kinds = content.slides.map { it.kind }
        assertEquals(List(3) { SlideKind.ContinueWatching } + List(3) { SlideKind.TopPick }, kinds)
        assertEquals("Tales of the Quiet Coast", content.slides.first().title)
    }

    @Test
    fun onlyContinueWatchingSlidesHaveProgress() {
        content.slides.forEach { slide ->
            assertEquals(slide.title, slide.kind == SlideKind.ContinueWatching, slide.progress != null)
        }
    }

    @Test
    fun appsRowIsYouTubeStremioNetflixIPlayerThenAllApps() {
        assertEquals(listOf("YouTube", "Stremio", "Netflix", "BBC iPlayer", "All Apps"), content.apps.map { it.label })
        assertEquals(
            listOf("com.google.android.youtube.tv", "com.stremio.one", "com.netflix.ninja", "bbc.iplayer.android", null),
            content.apps.map { it.packageName },
        )
    }

    @Test
    fun homeHasTheFourContentRowsInOrder() {
        assertEquals(
            listOf("Continue Watching", "Recommended on YouTube", "Top Picks for You", "From Your Watchlist"),
            content.rows.map { it.title },
        )
        assertTrue(content.rows[2] is PosterRow && content.rows[3] is PosterRow)
    }

    @Test
    fun noRowIsEmpty() {
        // A D-pad user must never stop on an empty box (design, "Nothing in progress").
        content.rows.forEach { row ->
            val size = when (row) {
                is WideRow -> row.cards.size
                is PosterRow -> row.posters.size
            }
            assertTrue(row.title, size > 0)
        }
    }

    @Test
    fun progressValuesAreFractions() {
        val values = content.slides.mapNotNull { it.progress } +
            content.rows.filterIsInstance<WideRow>().flatMap { row -> row.cards.mapNotNull { it.progress } }
        values.forEach { assertTrue("$it", it in 0f..1f) }
    }
}
