package app.opentvlauncher.netflix

import app.opentvlauncher.home.HomeState
import app.opentvlauncher.home.NetflixState
import app.opentvlauncher.home.Title
import app.opentvlauncher.trakt.TraktApi
import kotlinx.serialization.Serializable

/**
 * Which titles get a Netflix button (netflix-play research, option 1, 25 Oct 2026):
 * a title that Trakt says is on Netflix, with a Netflix id from Wikidata. A title with no id,
 * or with two ids, gets no button.
 */
object NetflixRules {
    /** Trakt's Netflix lists are read once a day. Netflix says a month before a title leaves. */
    const val CHECK_EVERY_MILLIS = 24L * 60 * 60 * 1000

    /**
     * When the daily check fails, the last answer is kept for 7 days from the last one that
     * worked. After that what is on Netflix is not known, and no title has a Netflix button.
     */
    const val KEEP_MILLIS = 7 * CHECK_EVERY_MILLIS

    private val IMDB = Regex("tt\\d{1,10}")
    private val NETFLIX_ID = Regex("\\d{1,12}")

    fun due(state: NetflixState, now: Long): Boolean {
        val checked = state.checkedAt ?: return true
        // A check from the future means the TV's clock went back: it cannot be trusted.
        return checked > now || now - checked >= CHECK_EVERY_MILLIS
    }

    /**
     * True while Trakt's last answer on what is on Netflix still counts: it is for [signIn], the
     * current sign-in (TraktSession.signInId), and it is no older than [KEEP_MILLIS]. Null
     * [signIn]: the TV is not signed in.
     */
    fun known(state: NetflixState, now: Long, signIn: String?): Boolean {
        if (signIn == null || state.signIn != signIn) return false
        val answered = state.availableAt ?: return false
        // An answer from the future means the TV's clock went back: it cannot be trusted.
        return answered <= now && now - answered <= KEEP_MILLIS
    }

    /** When Trakt's last answer stops counting, so a screen that shows it can draw again then. */
    fun expiresAt(state: NetflixState): Long? = state.availableAt?.let { it + KEEP_MILLIS }

    /**
     * The title's Netflix id, or null when it is not on Netflix, that is not known for [signIn]
     * ([known]), or its id is not known.
     */
    fun id(title: Title, state: NetflixState, now: Long, signIn: String?): String? {
        val key = title.key ?: return null
        if (key !in state.available || !known(state, now, signIn)) return null
        return title.imdb?.let(state.ids::get)
    }

    /** [title] with its Netflix id as it is now, for a page that holds a title it was given earlier. */
    fun withId(title: Title, state: NetflixState, now: Long, signIn: String?) = title.copy(netflixId = id(title, state, now, signIn))

    /** The saved state with each Trakt title's Netflix id set, for the screen to draw. */
    fun withIds(state: HomeState, now: Long, signIn: String?): HomeState {
        val rows = state.trakt ?: return state
        fun List<Title>.ids() = map { withId(it, state.netflix, now, signIn) }
        return state.copy(
            trakt = rows.copy(
                continueWatching = rows.continueWatching.ids(),
                picks = rows.picks.ids(),
                watchlistRow = rows.watchlistRow.ids(),
            ),
        )
    }

    /** The [continueWatching] titles that have joined the row since they were last checked for Netflix. */
    fun unchecked(continueWatching: List<Title>, state: NetflixState): List<Title> =
        continueWatching.filter { title -> title.key.let { it != null && it !in state.continueChecked } }

    /** IMDb ids of [titles] that are on Netflix with no known Netflix id, each once. */
    fun missingIds(titles: List<Title>, state: NetflixState): List<String> = titles
        .filter { it.key in state.available }
        .mapNotNull { it.imdb }
        .filter { it !in state.ids }
        .distinct()

    /**
     * The Wikidata query for the Netflix ids (property P1874) of these IMDb ids (P345). Only ids
     * of the form tt1234567 go into it. Null when none do.
     */
    fun wikidataQuery(imdbIds: List<String>): String? {
        val ids = imdbIds.filter(IMDB::matches).distinct()
        if (ids.isEmpty()) return null
        val values = ids.joinToString(" ") { "\"$it\"" }
        return "SELECT ?imdb ?netflix WHERE { VALUES ?imdb { $values } ?item wdt:P345 ?imdb ; wdt:P1874 ?netflix . }"
    }

    /**
     * Wikidata's answer as Netflix ids by IMDb id. A title with two different ids is left out:
     * which one is the UK title cannot be told. Only ids made of digits are kept, because the id
     * goes into a netflix.com address. An answer that cannot be read throws [TraktApi.BadResponse].
     */
    fun parseWikidata(body: String): Map<String, String> {
        val answer = try {
            TraktApi.json.decodeFromString(WikidataAnswer.serializer(), body)
        } catch (e: IllegalArgumentException) {
            throw TraktApi.BadResponse("Wikidata sent an answer that cannot be read", e)
        }
        return answer.results.bindings
            .mapNotNull { row ->
                val imdb = row.imdb?.value ?: return@mapNotNull null
                val netflix = row.netflix?.value ?: return@mapNotNull null
                (imdb to netflix).takeIf { IMDB.matches(imdb) && NETFLIX_ID.matches(netflix) }
            }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (imdb, ids) -> ids.distinct().singleOrNull()?.let { imdb to it } }
            .toMap()
    }

    fun validId(id: String) = NETFLIX_ID.matches(id)
}

// The parts of Wikidata's SPARQL JSON that the launcher reads.

@Serializable
private data class WikidataAnswer(val results: WikidataResults)

@Serializable
private data class WikidataResults(val bindings: List<WikidataRow>)

@Serializable
private data class WikidataRow(val imdb: WikidataValue? = null, val netflix: WikidataValue? = null)

@Serializable
private data class WikidataValue(val value: String)
