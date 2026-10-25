package app.opentvlauncher.trakt

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

enum class SignIn { SignedIn, SignedOut, Skipped }

/**
 * The TV's Trakt sign-in. The tokens live in DataStore. Trakt access tokens last 24 hours
 * (since March 2025) and each refresh returns a new refresh token, so the session renews the
 * pair well before the access token expires, at each background refresh.
 *
 * A refresh only saves its new tokens while the stored refresh token is still the one it sent,
 * so a late answer can never bring back a signed-out account or replace a newer sign-in.
 *
 * [generation] goes up at each sign-in and sign-out. Work for one account, such as a watchlist
 * change, reads it when it starts and passes it to [call], which runs only in that generation.
 *
 * A generation and its tokens change together: sign-in, sign-out and a refused refresh hold one
 * lock while they write the tokens, and raise the generation only after the write. The write and
 * the raise are one step that a cancelled caller cannot split. A token is read together with its
 * generation under the same lock, so a token is never handed out under another account's
 * generation. Ownership is checked again after every wait (a renewal, the offline fallback) and
 * just before each signed-in call.
 *
 * A refresh runs in [scope], which lives as long as the app, not in the caller. Trakt's refresh
 * tokens work once, so a rotated pair must be saved even when the screen or worker that asked
 * for it is cancelled during the request.
 */
class TraktSession(
    private val store: DataStore<Preferences>,
    private val api: TraktApi,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** The stored tokens. A file that cannot be read counts as signed out, so the launcher still starts. */
    private val stored: Flow<Preferences> = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    val state: Flow<SignIn> = stored.map { prefs ->
        when {
            prefs[ACCESS] != null -> SignIn.SignedIn
            prefs[SKIPPED] == true -> SignIn.Skipped
            else -> SignIn.SignedOut
        }
    }

    /** The current sign-in's id ([SignedIn.id]), or null when the TV is not signed in. */
    val signInId: Flow<String?> = stored.map { it.signInId() }.distinctUntilChanged()

    private val generations = MutableStateFlow(0L)

    /**
     * Goes up at each sign-in and sign-out, once the new tokens are written. It lives in memory:
     * the work it guards does not outlive the app, and it can be read at once, without a disk read.
     */
    val generation: StateFlow<Long> = generations.asStateFlow()

    /** Held while a sign-in, sign-out or refused refresh writes the tokens, and while a token and its generation are read. */
    private val transitions = Mutex()

    private val renewing = Mutex()

    /** The refresh of one refresh token, shared by every caller that needs it. */
    private var renewal: Pair<String, Deferred<String?>>? = null

    /** A new sign-in: it replaces any old one, and gets its own id ([SignedIn.id]). */
    suspend fun save(tokens: TokenResponse) = transition {
        store.edit {
            it.putTokens(tokens)
            it[SIGN_IN] = UUID.randomUUID().toString()
            it.remove(SKIPPED)
        }
        true
    }

    /**
     * Writes the tokens with [write] and, when it returns true (another account, or none), raises
     * the generation: one step under the lock. It cannot be cancelled part way, because DataStore
     * can commit before a cancelled caller resumes, and the generation must then still rise. It
     * is local work only; network calls stay outside.
     */
    private suspend fun transition(write: suspend () -> Boolean) = transitions.withLock {
        withContext(NonCancellable) {
            if (write()) generations.update { it + 1 }
        }
    }

    private fun MutablePreferences.putTokens(tokens: TokenResponse) {
        // A lifetime over 90 days is a wrong value from the server. Renew as if it were 24 hours.
        val lifetime = if (tokens.expiresIn in 1..MAX_LIFETIME_SECONDS) tokens.expiresIn else DAY_SECONDS
        this[ACCESS] = tokens.accessToken
        this[REFRESH] = tokens.refreshToken
        this[ISSUED_AT] = tokens.createdAt * 1000
        this[EXPIRES_AT] = (tokens.createdAt + lifetime) * 1000
    }

    /** "Skip for Now" on the sign-in screen. Settings, Trakt Account starts sign-in again. */
    suspend fun skip() = store.edit { it[SKIPPED] = true }

    /** Forgets the tokens and any "Skip for Now", so the sign-in screen shows again. */
    suspend fun signOut() = transition {
        store.edit {
            it.removeTokens()
            it.remove(SKIPPED)
        }
        true
    }

    private fun MutablePreferences.removeTokens() {
        remove(SIGN_IN)
        remove(ACCESS)
        remove(REFRESH)
        remove(ISSUED_AT)
        remove(EXPIRES_AT)
    }

    /** A token and the generation it belongs to. */
    private class Owned(val token: String, val generation: Long)

    /** The current generation and tokens, read as one: no sign-in or sign-out is half done. */
    private suspend fun snapshot(): Pair<Long, Preferences> = transitions.withLock { generations.value to stored.first() }

    /** True while [generation] is current. A sign-in or sign-out that is writing its tokens is waited for. */
    private suspend fun isCurrent(generation: Long): Boolean = transitions.withLock { generations.value == generation }

    /**
     * One sign-in: [generation] for work in this run of the app, and [id], which is saved with the
     * tokens, for data saved to disk. A token renewal keeps the id; a new sign-in, even to the same
     * account, gets a new one.
     */
    data class SignedIn(val generation: Long, val id: String)

    /** The current sign-in, read together with the tokens; null when the TV is not signed in. */
    suspend fun signedIn(): SignedIn? = snapshot().let { (generation, prefs) -> prefs.signInId()?.let { SignedIn(generation, it) } }

    // A sign-in saved before sign-ins had ids has the empty id. There can be only that one, until its sign-out.
    private fun Preferences.signInId(): String? = if (this[ACCESS] != null) this[SIGN_IN] ?: "" else null

    /**
     * Runs [block] only while [generation] is current: a sign-in or sign-out waits until it ends.
     * Null when another sign-in or sign-out has happened. [block] must not call this session.
     */
    suspend fun <T> whileCurrent(generation: Long, block: suspend () -> T): T? = transitions.withLock {
        if (generations.value == generation) block() else null
    }

    /**
     * A usable access token, renewed first when a quarter of its life or less is left, or when
     * Trakt has refused [rejected]. Null when the TV is not signed in, when [generation] is set
     * and a sign-in or sign-out has happened since, or when Trakt refuses the refresh token. When
     * the network fails, a token that has not expired is still returned.
     */
    suspend fun accessToken(rejected: String? = null, generation: Long? = null): String? = ownedToken(rejected, generation)?.token

    private suspend fun ownedToken(rejected: String?, generation: Long?): Owned? {
        val (owner, prefs) = snapshot()
        if (generation != null && owner != generation) return null
        val access = prefs[ACCESS] ?: return null
        val refresh = prefs[REFRESH] ?: return null
        val expiresAt = prefs[EXPIRES_AT] ?: 0L
        val issuedAt = prefs[ISSUED_AT] ?: expiresAt
        val margin = maxOf((expiresAt - issuedAt) / 4, HOUR_MILLIS)
        // A token that another call has renewed since Trakt refused it is used as it is.
        if ((rejected == null && expiresAt - now() > margin) || (rejected != null && rejected != access)) return Owned(access, owner)
        val token = try {
            renewalOf(refresh).await()
        } catch (e: IOException) {
            // Offline: the old token still works until it expires, but only for its own account.
            if (!isCurrent(owner)) return null
            if (rejected == null && expiresAt > now()) access else throw e
        }
        // A sign-in or sign-out during the renewal ends this account's turn.
        return token?.takeIf { isCurrent(owner) }?.let { Owned(it, owner) }
    }

    /** The refresh of [refresh] that is running or has worked, or a new one. A failed one is tried again. */
    private suspend fun renewalOf(refresh: String): Deferred<String?> = renewing.withLock {
        renewal?.takeIf { (token, job) -> token == refresh && !job.isCancelled }?.second
            ?: scope.async { renew(refresh) }.also { renewal = refresh to it }
    }

    private suspend fun renew(refresh: String): String? {
        val tokens = api.refresh(refresh)
        // Commit only to the session that sent this refresh token. A sign-out or a new sign-in
        // during the request has changed or removed it.
        var renewed: String? = null
        transition {
            var signedOut = false
            val committed = store.edit {
                if (it[REFRESH] != refresh) return@edit
                if (tokens == null) {
                    it.removeTokens()
                    signedOut = true
                } else {
                    it.putTokens(tokens)
                }
            }
            renewed = if (tokens != null && committed[ACCESS] == tokens.accessToken) tokens.accessToken else null
            signedOut
        }
        return renewed
    }

    /**
     * Runs a signed-in call. After a 401 it renews the token once and tries again, for the same
     * account. With [generation], it runs only in that generation, so work for one account never
     * uses the token of the next. Each try starts only while its account is still current.
     */
    suspend fun <T> call(generation: Long? = null, block: suspend (accessToken: String) -> T): T? {
        val first = ownedToken(rejected = null, generation = generation) ?: return null
        return try {
            if (!isCurrent(first.generation)) return null
            block(first.token)
        } catch (_: TraktApi.Unauthorized) {
            val fresh = ownedToken(rejected = first.token, generation = first.generation) ?: return null
            if (!isCurrent(fresh.generation)) return null
            block(fresh.token)
        }
    }

    private companion object {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val ISSUED_AT = longPreferencesKey("issued_at")
        val EXPIRES_AT = longPreferencesKey("expires_at")
        val SKIPPED = booleanPreferencesKey("skipped")
        val SIGN_IN = stringPreferencesKey("sign_in")
        const val DAY_SECONDS = 24L * 60 * 60
        const val MAX_LIFETIME_SECONDS = 90 * DAY_SECONDS
        const val HOUR_MILLIS = 60L * 60 * 1000
    }
}
