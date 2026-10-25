package app.opentvlauncher.netflix

import app.opentvlauncher.home.HomeState
import app.opentvlauncher.home.NetflixState
import app.opentvlauncher.home.Title
import app.opentvlauncher.home.TitleKey
import app.opentvlauncher.home.TitleKind
import app.opentvlauncher.home.TraktRows
import app.opentvlauncher.system.TitleApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Which titles get a Netflix button, and the Wikidata answer that gives their Netflix ids (netflix-play research). */
class NetflixRulesTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    /** The current sign-in's id. */
    private val signIn = "sign-in-a"

    private fun film(trakt: Int, imdb: String? = "tt$trakt") = Title(TitleKind.Movie, "Film $trakt", imdb = imdb, traktId = trakt)

    private fun show(trakt: Int) = Title(TitleKind.Show, "Show $trakt", imdb = "tt$trakt", traktId = trakt)

    private fun state(available: Set<TitleKey>, ids: Map<String, String>) = NetflixState(signIn = signIn, checkedAt = now, availableAt = now, available = available, ids = ids)

    // Wikidata's SPARQL answer, cut down to the fields the launcher reads.
    private fun bindings(vararg pairs: Pair<String, String>) = """{"head":{"vars":["imdb","netflix"]},"results":{"bindings":[${
        pairs.joinToString(",") { (imdb, netflix) -> """{"imdb":{"type":"literal","value":"$imdb"},"netflix":{"type":"literal","value":"$netflix"}}""" }
    }]}}"""

    @Test
    fun aTitleOnNetflixWithAnIdGetsTheId() {
        val netflix = state(setOf(TitleKey(TitleKind.Movie, 1)), mapOf("tt1" to "81424086"))
        assertEquals("81424086", NetflixRules.id(film(1), netflix, now, signIn))
    }

    @Test
    fun aTitleThatIsNotOnNetflixGetsNoId() {
        // Those Who Climb at Dusk: Wikidata could know an id, but Trakt says it is not on Netflix UK.
        val netflix = state(emptySet(), mapOf("tt1" to "81424086"))
        assertNull(NetflixRules.id(film(1), netflix, now, signIn))
    }

    @Test
    fun aTitleOnNetflixWithNoKnownIdGetsNoId() {
        assertNull(NetflixRules.id(film(1), state(setOf(TitleKey(TitleKind.Movie, 1)), emptyMap()), now, signIn))
    }

    @Test
    fun aTitleWithNoImdbIdGetsNoId() {
        assertNull(NetflixRules.id(film(1, imdb = null), state(setOf(TitleKey(TitleKind.Movie, 1)), mapOf("tt1" to "81424086")), now, signIn))
    }

    @Test
    fun aFilmAndAShowWithTheSameTraktNumberAreDifferentTitles() {
        val netflix = state(setOf(TitleKey(TitleKind.Show, 1)), mapOf("tt1" to "81424086"))
        assertNull(NetflixRules.id(film(1), netflix, now, signIn))
        assertEquals("81424086", NetflixRules.id(show(1), netflix, now, signIn))
    }

    @Test
    fun theScreenGetsTheIdOnEveryTraktListItsTitlesComeFrom() {
        val netflix = state(setOf(TitleKey(TitleKind.Movie, 1), TitleKey(TitleKind.Movie, 2), TitleKey(TitleKind.Show, 3)), mapOf("tt1" to "1", "tt2" to "2", "tt3" to "3"))
        val rows = TraktRows(savedAt = now, continueWatching = listOf(film(1), film(9)), picks = listOf(film(2)), watchlistRow = listOf(show(3)))
        val shown = NetflixRules.withIds(HomeState(trakt = rows, netflix = netflix), now, signIn).trakt!!
        assertEquals(listOf("1", null), shown.continueWatching.map { it.netflixId })
        assertEquals(listOf("2"), shown.picks.map { it.netflixId })
        assertEquals(listOf("3"), shown.watchlistRow.map { it.netflixId })
    }

    @Test
    fun withoutTraktRowsTheStateIsUnchanged() {
        val home = HomeState(netflix = state(setOf(TitleKey(TitleKind.Movie, 1)), mapOf("tt1" to "1")))
        assertEquals(home, NetflixRules.withIds(home, now, signIn))
    }

    @Test
    fun wikidataGivesEachImdbIdItsNetflixId() {
        assertEquals(
            mapOf("tt9100002" to "81424086", "tt91000004" to "81252357"),
            NetflixRules.parseWikidata(bindings("tt9100002" to "81424086", "tt91000004" to "81252357")),
        )
    }

    @Test
    fun aTitleWithTwoNetflixIdsGetsNone() {
        // Which one is the UK title cannot be told, and a wrong id opens a wrong page.
        assertEquals(mapOf("tt2" to "2"), NetflixRules.parseWikidata(bindings("tt1" to "100", "tt1" to "200", "tt2" to "2")))
    }

    @Test
    fun theSameIdTwiceIsStillOneId() {
        assertEquals(mapOf("tt1" to "100"), NetflixRules.parseWikidata(bindings("tt1" to "100", "tt1" to "100")))
    }

    @Test
    fun anIdThatIsNotANumberIsLeftOut() {
        // The id goes into a netflix.com address, so only digits are kept.
        assertEquals(emptyMap<String, String>(), NetflixRules.parseWikidata(bindings("tt1" to "81424086/../x", "tt2" to "", "bad" to "1")))
    }

    @Test
    fun aRowWithoutBothFieldsIsLeftOut() {
        val body = """{"results":{"bindings":[{"imdb":{"type":"literal","value":"tt1"}},{"netflix":{"type":"literal","value":"2"}}]}}"""
        assertEquals(emptyMap<String, String>(), NetflixRules.parseWikidata(body))
    }

    @Test
    fun anAnswerThatCannotBeReadIsAFailedRequest() {
        assertThrows(IOException::class.java) { NetflixRules.parseWikidata("<html>busy</html>") }
        assertThrows(IOException::class.java) { NetflixRules.parseWikidata("""{"results":{}}""") }
    }

    @Test
    fun theQueryAsksOnlyForWellFormedImdbIds() {
        val query = NetflixRules.wikidataQuery(listOf("tt9100002", "tt1\" } DELETE {", "nm0000001", "tt9100002"))!!
        assertTrue(query.contains("VALUES ?imdb { \"tt9100002\" }"))
        assertFalse(query.contains("DELETE"))
        assertTrue(query.contains("wdt:P345 ?imdb"))
        assertTrue(query.contains("wdt:P1874 ?netflix"))
    }

    @Test
    fun noWellFormedIdsMeansNoQuery() {
        assertNull(NetflixRules.wikidataQuery(listOf("x", "")))
    }

    @Test
    fun theFirstCheckIsDueAndThenOnceADay() {
        assertTrue(NetflixRules.due(NetflixState(), now))
        assertFalse(NetflixRules.due(NetflixState(checkedAt = now - day + 1), now))
        assertTrue(NetflixRules.due(NetflixState(checkedAt = now - day), now))
    }

    @Test
    fun aCheckFromTheFutureIsDue() {
        // The TV's clock went back: the saved check cannot be trusted.
        assertTrue(NetflixRules.due(NetflixState(checkedAt = now + 60_000), now))
    }

    /** The rule: after failed checks the last answer counts for 7 days from the last one that worked. */
    @Test
    fun theLastAnswerCountsForSevenDaysThenTheButtonHides() {
        val netflix = state(setOf(TitleKey(TitleKind.Movie, 1)), mapOf("tt1" to "81424086"))
        assertEquals("81424086", NetflixRules.id(film(1), netflix, now + 6 * day, signIn))
        assertEquals("81424086", NetflixRules.id(film(1), netflix, now + 7 * day, signIn))
        assertNull(NetflixRules.id(film(1), netflix, now + 7 * day + 1, signIn))
        val rows = TraktRows(savedAt = now, watchlistRow = listOf(film(1)))
        assertNull(NetflixRules.withIds(HomeState(trakt = rows, netflix = netflix), now + 8 * day, signIn).trakt!!.watchlistRow.single().netflixId)
    }

    @Test
    fun withNoAnswerFromTraktNoTitleIsOnNetflix() {
        val netflix = NetflixState(available = setOf(TitleKey(TitleKind.Movie, 1)), ids = mapOf("tt1" to "81424086"))
        assertFalse(NetflixRules.known(netflix, now, signIn))
        assertNull(NetflixRules.id(film(1), netflix, now, signIn))
    }

    /** Review R7-2: another sign-in's answer gives no button, from the first frame, before any refresh. */
    @Test
    fun onlyTheCurrentSignInsAnswerCounts() {
        val netflix = state(setOf(TitleKey(TitleKind.Movie, 1)), mapOf("tt1" to "81424086"))
        assertEquals("81424086", NetflixRules.id(film(1), netflix, now, signIn))
        assertNull(NetflixRules.id(film(1), netflix, now, "sign-in-b"))
        // Signed out.
        assertNull(NetflixRules.id(film(1), netflix, now, null))
        // An answer with no sign-in, as after a sign-out, counts for none.
        assertNull(NetflixRules.id(film(1), netflix.copy(signIn = null), now, signIn))
    }

    @Test
    fun theAnswerExpiresSevenDaysAfterItCame() {
        assertEquals(now + 7 * day, NetflixRules.expiresAt(state(emptySet(), emptyMap())))
        assertNull(NetflixRules.expiresAt(NetflixState()))
    }

    @Test
    fun anAnswerFromTheFutureDoesNotCount() {
        // The TV's clock went back: the saved answer cannot be trusted.
        val netflix = state(setOf(TitleKey(TitleKind.Movie, 1)), mapOf("tt1" to "81424086")).copy(availableAt = now + 60_000)
        assertNull(NetflixRules.id(film(1), netflix, now, signIn))
    }

    @Test
    fun titlesThatNeedAnIdAreTheOnesOnNetflixWithoutOne() {
        val netflix = NetflixState(available = setOf(TitleKey(TitleKind.Movie, 1), TitleKey(TitleKind.Movie, 2)), ids = mapOf("tt1" to "1"))
        val titles = listOf(film(1), film(2), film(3), film(2))
        assertEquals(listOf("tt2"), NetflixRules.missingIds(titles, netflix))
    }

    @Test
    fun aSignOutForgetsWhatIsOnNetflixButKeepsTheIds() {
        // Availability is for the Trakt account's region. A title's Netflix id does not change.
        val signedOut = state(setOf(TitleKey(TitleKind.Movie, 1)), mapOf("tt1" to "1")).copy(signIn = "a").signedOut()
        assertEquals(NetflixState(ids = mapOf("tt1" to "1")), signedOut)
    }

    private val both = setOf(TitleApp.Netflix.packageName, TitleApp.Stremio.packageName)

    @Test
    fun stremioComesFirstThenNetflix() {
        assertEquals(listOf(TitleApp.Stremio, TitleApp.Netflix), TitleApp.forTitle(film(1).copy(netflixId = "81424086"), both))
    }

    @Test
    fun noNetflixIdHidesTheNetflixButton() {
        assertEquals(listOf(TitleApp.Stremio), TitleApp.forTitle(film(1), both))
    }

    @Test
    fun netflixNotInstalledHidesTheNetflixButton() {
        assertEquals(listOf(TitleApp.Stremio), TitleApp.forTitle(film(1).copy(netflixId = "81424086"), setOf(TitleApp.Stremio.packageName)))
    }

    @Test
    fun stremioNotInstalledHidesTheStremioButton() {
        val onNetflix = film(1).copy(netflixId = "81424086")
        assertEquals(listOf(TitleApp.Netflix), TitleApp.forTitle(onNetflix, setOf(TitleApp.Netflix.packageName)))
        assertEquals(emptyList<TitleApp>(), TitleApp.forTitle(film(1), emptySet()))
    }

    @Test
    fun beforeTheAppsAreReadStremioShowsAndNetflixWaits() {
        assertEquals(listOf(TitleApp.Stremio), TitleApp.forTitle(film(1).copy(netflixId = "81424086"), installed = null))
    }
}
