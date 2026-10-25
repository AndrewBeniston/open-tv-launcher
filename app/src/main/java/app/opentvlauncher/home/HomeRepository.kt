package app.opentvlauncher.home

import app.opentvlauncher.netflix.NetflixRules
import app.opentvlauncher.netflix.NetflixSource
import app.opentvlauncher.trakt.PlayResult
import app.opentvlauncher.trakt.TraktApi
import app.opentvlauncher.trakt.TraktRowsSource
import app.opentvlauncher.trakt.TraktSession
import app.opentvlauncher.tv.TvChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/** The saved HomeState: one JSON file in app storage, replaced in one step after each refresh. */
class HomeStateStore(private val file: File) {
    fun read(): HomeState = runCatching {
        if (file.exists()) TraktApi.json.decodeFromString(HomeState.serializer(), file.readText()) else HomeState()
    }.getOrDefault(HomeState())

    fun write(state: HomeState) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(TraktApi.json.encodeToString(HomeState.serializer(), state))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }
}

/**
 * The home screen's data. It starts from the saved file, so the first frame never waits for
 * the network. [refresh] reads Trakt and the TV channels, keeps the last good Trakt rows when
 * Trakt fails, saves the result and updates [state] in place.
 */
class HomeRepository(
    private val store: HomeStateStore,
    private val trakt: TraktRowsSource,
    private val session: TraktSession,
    private val tvRows: suspend () -> TvChannels.Rows,
    private val now: () -> Long = System::currentTimeMillis,
    /** The daily Netflix check (netflix-play). Null: no check, as in tests that do not need it. */
    private val netflix: NetflixSource? = null,
) {
    private val mutable = MutableStateFlow(store.read())
    val state: StateFlow<HomeState> = mutable.asStateFlow()

    private val refreshing = Mutex()
    private var lastRefresh = 0L

    /** A refresh, unless one finished less than [minAgeMillis] ago (for "return to the front"). */
    suspend fun refreshIfOlderThan(minAgeMillis: Long) {
        if (now() - lastRefresh >= minAgeMillis) refresh()
    }

    suspend fun refresh() = refreshing.withLock {
        val tv = runCatching { tvRows() }.getOrDefault(TvChannels.Rows(emptyList(), emptyList()))
        val withoutTrakt = mutable.value.copy(youtube = tv.youtube, stremio = tv.stremio, trakt = null, traktFailedAt = null)
        // What is on Netflix belongs to one sign-in's account, so it goes with the account.
        val signedOut = withoutTrakt.copy(netflix = withoutTrakt.netflix.signedOut())
        // The account and its sign-in are read together, so a sign-out that is half done is not seen as signed in.
        val owner = session.signedIn()
        if (owner == null) {
            save(signedOut)
        } else {
            // Another sign-in's answer is not this account's, even when it is less than a day old.
            // It goes at once, before the network work, not when the refresh ends.
            val ownNetflix = withoutTrakt.netflix.signIn == owner.id
            if (!ownNetflix) {
                session.whileCurrent(owner.generation) {
                    val shown = mutable.value
                    if (shown.netflix != shown.netflix.signedOut()) save(shown.copy(netflix = shown.netflix.signedOut()))
                }
            }
            val own = if (ownNetflix) withoutTrakt else signedOut
            val next = try {
                // Null: the account went during the fetch.
                trakt.fetch(now(), owner.generation)?.let { own.copy(trakt = it.withoutJustWatched(owner.generation)) } ?: own
            } catch (_: IOException) {
                // A network failure, or an answer that cannot be read (TraktApi.BadResponse):
                // the last good rows stay.
                own.copy(trakt = mutable.value.trakt, traktFailedAt = now())
            }
            val checked = withNetflix(next, owner)
            // Account rows are saved only while their account is current; a sign-in or sign-out waits.
            session.whileCurrent(owner.generation) { save(checked) } ?: save(signedOut)
        }
        lastRefresh = now()
    }

    /**
     * The daily Netflix check (NetflixRules.due) for [owner]'s account, on [state]'s Trakt rows. A
     * Trakt failure keeps the last answer for 7 days from the last one (NetflixRules.known), and
     * the next refresh tries again. A Wikidata failure saves what is on Netflix, but not the
     * check's time, so the next refresh tries again ([finish]). Ids are asked for only once: they
     * do not change.
     */
    private suspend fun withNetflix(state: HomeState, owner: TraktSession.SignedIn): HomeState {
        val source = netflix ?: return state
        val rows = state.trakt ?: return state
        if (!NetflixRules.due(state.netflix, now())) return withNewContinue(state, rows, source, owner)
        val available = try {
            source.available(rows.continueWatching, owner.generation)
        } catch (_: IOException) {
            null
        }
        if (available == null) {
            return if (NetflixRules.known(state.netflix, now(), owner.id)) state else state.copy(netflix = state.netflix.unknown())
        }
        val found = state.netflix.copy(signIn = owner.id, availableAt = now(), available = available, provisional = emptySet())
        val checked = rows.continueWatching.mapNotNull { it.key }.toSet()
        return finish(state, found, rows.continueWatching + rows.picks + rows.watchlistRow, source) {
            it.copy(checkedAt = now(), continueChecked = checked)
        }
    }

    /**
     * Between daily checks, the titles new to Continue Watching, such as a show's next episode,
     * are checked at once, each through its own Trakt history (25 Oct 2026). Only while
     * the last answer counts for [owner] (NetflixRules.known). The answer keeps its time, so its 7
     * days still run from the daily check. A Trakt or Wikidata failure leaves the titles for the
     * next refresh ([finish]).
     */
    private suspend fun withNewContinue(state: HomeState, rows: TraktRows, source: NetflixSource, owner: TraktSession.SignedIn): HomeState {
        val netflix = state.netflix
        if (!NetflixRules.known(netflix, now(), owner.id)) return state
        val new = NetflixRules.unchecked(rows.continueWatching, netflix).ifEmpty { return state }
        val available = try {
            source.continueAvailable(new, owner.generation)
        } catch (_: IOException) {
            null
        } ?: return state
        val rechecked = new.mapNotNull { it.key }.toSet()
        // This answer replaces an earlier try's provisional one for the same titles. Other answers stay.
        val confirmed = netflix.available - netflix.provisional
        val earlier = netflix.provisional - rechecked
        val found = netflix.copy(available = confirmed + earlier + available, provisional = earlier + (available - confirmed))
        return finish(state, found, new, source) {
            it.copy(continueChecked = it.continueChecked + rechecked, provisional = it.provisional - rechecked)
        }
    }

    /**
     * The end of both Netflix checks: [found] holds what is on Netflix, then Wikidata gives the
     * missing ids of [titles]. Only then is the check [done]. When Wikidata fails, [found] is
     * saved without it, so the next refresh runs the same check again (merge gate V2: a new title
     * was marked checked before its id came, and then never got it).
     */
    private suspend fun finish(
        state: HomeState,
        found: NetflixState,
        titles: List<Title>,
        source: NetflixSource,
        done: (NetflixState) -> NetflixState,
    ): HomeState {
        val ids = try {
            source.ids(NetflixRules.missingIds(titles, found))
        } catch (_: IOException) {
            return state.copy(netflix = found)
        }
        return state.copy(netflix = done(found.copy(ids = found.ids + ids)))
    }

    /**
     * Adds [title] to the Trakt watchlist ([on]) or removes it, then updates the saved rows by the
     * rules a refresh uses: an added title leaves Top Picks and leads From Your Watchlist, unless
     * it is watched, rated or in Continue Watching; a removed title leaves the rows and the
     * screensaver. [generation] is the session generation the change was asked for in: now, when
     * null. False after a sign-in or sign-out since then, or if Trakt refused or failed.
     */
    suspend fun setWatchlisted(title: Title, on: Boolean, generation: Long? = null): Boolean {
        val owner = generation ?: session.generation.value
        val accepted = try {
            trakt.setWatchlisted(title, on, owner)
        } catch (_: IOException) {
            false
        }
        if (!accepted) return false
        refreshing.withLock {
            val rows = mutable.value.trakt ?: return@withLock
            val key = title.key ?: return@withLock
            // Trakt leaves watchlist titles out of its recommendations, so an added title leaves Top Picks.
            val picks = TraktRules.filmsAndShowsInTurn(rows.picks.filterNot { it.key == key })
            val next = if (on) {
                val listed = title.copy(source = PickSource.Watchlist)
                val shown = if (TraktRules.mayShow(listed, rows.seen, rows.continueWatching)) listOf(listed) else emptyList()
                rows.copy(
                    picks = picks,
                    watchlistRow = (shown + rows.watchlistRow.filterNot { it.key == key }).take(TraktRules.WATCHLIST_LIMIT),
                    // The screensaver's watchlist too, by its own rule, so it is never left empty.
                    watchlist = TraktRules.screensaverWatchlist(listOf(listed) + rows.watchlist.filterNot { it.key == key }),
                    watchlistKeys = rows.watchlistKeys + key,
                )
            } else {
                rows.copy(
                    picks = picks,
                    watchlistRow = rows.watchlistRow.filterNot { it.key == key },
                    watchlist = rows.watchlist.filterNot { it.key == key },
                    watchlistKeys = rows.watchlistKeys - key,
                )
            }
            session.whileCurrent(owner) { save(mutable.value.copy(trakt = next)) }
        }
        return true
    }

    /**
     * Plays whose answer from Trakt was lost or unclear, with the time each was sent with. Trakt
     * adds a second play of the same title if asked, so a retry first looks for this play in
     * Trakt's history. The sign-in is part of the key: a request from an earlier sign-in that
     * ends late reads and changes only its own plays.
     */
    private val unclearPlays = ConcurrentHashMap<UnclearPlay, String>()

    private data class UnclearPlay(val generation: Long, val title: TitleKey, val season: Int?, val number: Int?)

    /**
     * The Continue Watching menu: [change] for [title], asked for in session [generation]. The
     * saved rows follow what Trakt confirmed. Failed after a sign-in or sign-out since then, or
     * if Trakt refused or failed; a part that Trakt confirmed still shows.
     * - Mark as Watched clears the paused items first, then adds a play ([played]).
     * - Reset Progress clears the paused items, so the title leaves Continue Watching.
     * - Mark as Unwatched removes every play from the history, or finds none to remove. The rows
     *   wait for the next refresh.
     */
    suspend fun change(title: Title, change: TitleChange, generation: Long): ChangeResult {
        val key = title.key ?: return ChangeResult.Failed
        return when (change) {
            TitleChange.RemoveFromWatchlist -> setWatchlisted(title, on = false, generation).result()
            TitleChange.ResetProgress -> clearProgress(title, key, generation).result()
            TitleChange.MarkUnwatched -> unwatched(title, generation)
            TitleChange.MarkWatched -> (
                clearProgress(title, key, generation) &&
                    played(title, key, generation).also { played ->
                        // Watched: Top Picks and From Your Watchlist leave it out, as after a refresh.
                        if (played) {
                            title.episode?.let { justWatched[key] = JustWatched(generation, it, now()) }
                            updateRows(generation) { rows ->
                                rows.copy(
                                    seen = rows.seen + key,
                                    picks = TraktRules.filmsAndShowsInTurn(rows.picks.filterNot { it.key == key }),
                                    watchlistRow = rows.watchlistRow.filterNot { it.key == key },
                                )
                            }
                        }
                    }
                ).result()
        }
    }

    private fun Boolean.result() = if (this) ChangeResult.Done else ChangeResult.Failed

    /**
     * Episodes marked watched on the TV in the last [JUST_WATCHED_MILLIS], by show, for the
     * sign-in that marked them. Trakt's up next can still name such an episode just after the
     * play, and its card would come back with the refresh that follows (TitleChanges).
     */
    private val justWatched = ConcurrentHashMap<TitleKey, JustWatched>()

    private data class JustWatched(val generation: Long, val episode: EpisodeRef, val at: Long)

    private fun TraktRows.withoutJustWatched(generation: Long): TraktRows {
        justWatched.values.removeIf { it.generation != generation || now() - it.at > JUST_WATCHED_MILLIS }
        if (justWatched.isEmpty()) return this
        return copy(continueWatching = TraktRules.withoutWatched(continueWatching, justWatched.mapValues { it.value.episode }))
    }

    /** Mark as Unwatched. A title with no plays has nothing to remove, which the menu says. */
    private suspend fun unwatched(title: Title, generation: Long): ChangeResult {
        val removed = try {
            trakt.removePlays(title, generation)
        } catch (_: IOException) {
            null
        }
        return when (removed) {
            null -> ChangeResult.Failed
            0 -> ChangeResult.NothingToRemove
            else -> ChangeResult.Done
        }
    }

    /**
     * Adds one play of [title], true when Trakt counted it. After an unclear answer the play may
     * already be on Trakt: the retry looks for it in the history at the time it was sent with,
     * and sends it again, with the same time, only when Trakt says it is not there. A title or
     * episode Trakt does not know is a failure, whatever the HTTP status.
     */
    private suspend fun played(title: Title, key: TitleKey, generation: Long): Boolean {
        // Plays from earlier sign-ins cannot be checked in this account's history. Only the current
        // sign-in drops them, and never its own or a later one's.
        if (session.generation.value == generation) unclearPlays.keys.removeIf { it.generation < generation }
        val play = UnclearPlay(generation, key, title.episode?.season, title.episode?.number)
        val unclear = unclearPlays[play]
        if (unclear != null) {
            when (trakt.hasPlay(title, unclear, generation)) {
                true -> {
                    unclearPlays.remove(play)
                    return true
                }
                null -> return false
                false -> Unit
            }
        }
        val watchedAt = unclear ?: Instant.ofEpochMilli(now()).truncatedTo(ChronoUnit.SECONDS).toString()
        return when (trakt.addPlay(title, watchedAt, generation)) {
            PlayResult.Added -> {
                unclearPlays.remove(play)
                true
            }
            PlayResult.Unclear -> {
                // After another sign-in no retry can use it.
                if (session.generation.value == generation) unclearPlays[play] = watchedAt
                false
            }
            PlayResult.NotFound -> {
                unclearPlays.remove(play)
                false
            }
            null -> false
        }
    }

    private suspend fun clearProgress(title: Title, key: TitleKey, generation: Long): Boolean {
        if (!sent { trakt.removePlayback(title.playbackIds, generation) }) return false
        updateRows(generation) { rows -> rows.copy(continueWatching = rows.continueWatching.filterNot { it.key == key }) }
        return true
    }

    /** False when the request failed on the network or Trakt's answer could not be read. */
    private suspend fun sent(request: suspend () -> Boolean): Boolean = try {
        request()
    } catch (_: IOException) {
        false
    }

    /** Changes the saved Trakt rows, only while [owner] is the current session generation. */
    private suspend fun updateRows(owner: Long, change: (TraktRows) -> TraktRows) = refreshing.withLock {
        val rows = mutable.value.trakt ?: return@withLock
        session.whileCurrent(owner) { save(mutable.value.copy(trakt = change(rows))) }
    }

    private suspend fun save(next: HomeState) {
        withContext(Dispatchers.IO) { runCatching { store.write(next) } }
        mutable.value = next
    }
}

/** What the Continue Watching menu can change on Trakt (HomeRepository.change). */
enum class TitleChange { MarkWatched, MarkUnwatched, ResetProgress, RemoveFromWatchlist }

private const val JUST_WATCHED_MILLIS = 10 * 60 * 1000L

/** How a [TitleChange] ended: done, nothing to remove (Mark as Unwatched with no plays), or failed. */
enum class ChangeResult { Done, NothingToRemove, Failed }

/**
 * "Remove from Watchlist" with 10 s to undo (design, details page). [phases] says where each
 * removal is, and the details page draws its button from it. The removal runs in [scope], so it
 * still happens if the details page closes first. [scope] must run on one thread, as MainScope
 * does, so an undo and the end of the undo window cannot overlap.
 *
 * The undo window ends before the request is sent. Trakt may accept a request whose answer has
 * not arrived, and cancelling the coroutine cannot take that back, so a sent request always
 * finishes and its result shows.
 *
 * A removal belongs to the session generation it was asked for in. A sign-out or a new sign-in
 * cancels every removal that is waiting and clears every phase, and the request itself checks
 * the generation again. A sent request that finishes after that, or after its title has a newer
 * removal, changes nothing here: each removal clears only its own entry.
 */
class WatchlistUndo(
    private val scope: CoroutineScope,
    private val repository: HomeRepository,
    private val session: TraktSession,
    private val delayMillis: Long = 10_000,
) {
    enum class Phase {
        /** In the undo window: nothing is sent yet. */
        Waiting,

        /** The request is sent. It can no longer be undone. */
        Removing,

        /** Trakt refused the request or no answer came, so the removal is not confirmed. */
        Failed,
    }

    private class Removal(val generation: Long, val job: Job)

    private val removals = mutableMapOf<TitleKey, Removal>()
    private val mutablePhases = MutableStateFlow<Map<TitleKey, Phase>>(emptyMap())
    val phases: StateFlow<Map<TitleKey, Phase>> = mutablePhases.asStateFlow()

    init {
        scope.launch {
            session.generation.collect { now ->
                val stale = removals.filterValues { it.generation != now }.keys
                stale.forEach { removals.remove(it)?.job?.cancel() }
                mutablePhases.value = mutablePhases.value.filterKeys { it in removals }
            }
        }
    }

    fun remove(title: Title) {
        val key = title.key ?: return
        if (key in removals) return
        val generation = session.generation.value
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var failed = false
            try {
                delay(delayMillis)
                mutablePhases.value += key to Phase.Removing
                failed = !withContext(NonCancellable) { repository.setWatchlisted(title, on = false, generation) }
            } finally {
                if (removals[key]?.job === coroutineContext.job) {
                    removals.remove(key)
                    val current = generation == session.generation.value
                    mutablePhases.value = if (failed && current) mutablePhases.value + (key to Phase.Failed) else mutablePhases.value - key
                }
            }
        }
        removals[key] = Removal(generation, job)
        mutablePhases.value += key to Phase.Waiting
        job.start()
    }

    /** "Add Back to Watchlist": works only in the undo window. */
    fun undo(title: Title) {
        val key = title.key ?: return
        if (mutablePhases.value[key] != Phase.Waiting) return
        removals.remove(key)?.job?.cancel()
        mutablePhases.value -= key
    }
}
