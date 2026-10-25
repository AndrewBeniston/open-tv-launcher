package app.opentvlauncher.trakt

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.serializer
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

/** Sign-in that lasts: tokens are renewed before they expire, and the rotated pair is kept. */
class TraktSessionTest {
    private val server = MockWebServer()
    private var nowMillis = 0L
    private val store = tempStore()
    private lateinit var session: TraktSession

    @Before
    fun setUp() {
        server.start()
        session = TraktSession(store, testApi(server), now = { nowMillis })
    }

    @After
    fun tearDown() = server.close()

    private fun signIn(createdAtSeconds: Long, lifetimeSeconds: Long = 86_400) = runBlocking {
        session.save(TokenResponse("access-1", "refresh-1", lifetimeSeconds, createdAtSeconds))
    }

    @Test
    fun aFreshTokenIsUsedWithoutAnyRequest() = runBlocking {
        signIn(createdAtSeconds = 1_000)
        nowMillis = 1_000_000L + 60 * 60 * 1000 // One hour into a 24-hour token.
        assertEquals("access-1", session.accessToken())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aTokenWithAQuarterOfItsLifeLeftIsRenewedAndTheNewRefreshTokenIsKept() = runBlocking {
        signIn(createdAtSeconds = 1_000)
        nowMillis = (1_000L + 19 * 60 * 60) * 1000 // 19 hours in: 5 hours left of 24.
        server.enqueue(json(200, tokenJson("access-2", "refresh-2", createdAt = 1_000 + 19 * 60 * 60)))
        assertEquals("access-2", session.accessToken())
        val sent = server.takeRequest().body!!.utf8()
        assertTrue(sent.contains("\"refresh_token\":\"refresh-1\""))
        assertTrue(sent.contains("\"grant_type\":\"refresh_token\""))

        // The next renewal must send the rotated refresh token.
        nowMillis += 20L * 60 * 60 * 1000
        server.enqueue(json(200, tokenJson("access-3", "refresh-3", createdAt = nowMillis / 1000)))
        assertEquals("access-3", session.accessToken())
        assertTrue(server.takeRequest().body!!.utf8().contains("\"refresh_token\":\"refresh-2\""))
    }

    /** Saved data for one account, such as what is on Netflix, is kept by this id. */
    @Test
    fun eachSignInHasItsOwnIdWhichARenewalKeeps() = runBlocking {
        signIn(createdAtSeconds = 1_000)
        val first = session.signedIn()!!
        // The screen reads the same id, to draw only this sign-in's Netflix buttons.
        assertEquals(first.id, session.signInId.first())
        nowMillis = (1_000L + 19 * 60 * 60) * 1000
        server.enqueue(json(200, tokenJson("access-2", "refresh-2", createdAt = 1_000 + 19 * 60 * 60)))
        assertEquals("access-2", session.accessToken())
        assertEquals(first, session.signedIn())
        // After a restart: the generation starts again, the id does not.
        assertEquals(first.id, TraktSession(store, testApi(server), now = { nowMillis }).signedIn()!!.id)
        signIn(createdAtSeconds = 2_000)
        val second = session.signedIn()!!
        assertTrue(second.id != first.id && second.generation > first.generation)
        session.signOut()
        assertNull(session.signedIn())
        assertNull(session.signInId.first())
    }

    @Test
    fun aRefusedRefreshTokenSignsTheTvOut() = runBlocking {
        signIn(createdAtSeconds = 1_000)
        nowMillis = (1_000L + 25 * 60 * 60) * 1000
        server.enqueue(json(401))
        assertNull(session.accessToken())
        assertEquals(SignIn.SignedOut, session.state.first())
    }

    @Test
    fun withNoNetworkATokenThatHasNotExpiredIsStillUsed() = runBlocking {
        signIn(createdAtSeconds = 1_000)
        nowMillis = (1_000L + 20 * 60 * 60) * 1000
        server.close() // Every request now fails.
        assertEquals("access-1", session.accessToken())
    }

    @Test
    fun aWrongLifetimeFromTheServerIsTreatedAsOneDay() = runBlocking {
        // A server bug once returned a timestamp in expires_in. The token must still be renewed.
        signIn(createdAtSeconds = 1_000, lifetimeSeconds = 1_900_000_000)
        nowMillis = (1_000L + 23 * 60 * 60) * 1000
        server.enqueue(json(200, tokenJson("access-2", "refresh-2", createdAt = nowMillis / 1000)))
        assertEquals("access-2", session.accessToken())
    }

    @Test
    fun aCallThatGets401RenewsOnceAndTriesAgain() = runBlocking {
        signIn(createdAtSeconds = 1_000)
        nowMillis = 1_000_000L
        server.enqueue(json(401))
        server.enqueue(json(200, tokenJson("access-2", "refresh-2", createdAt = 1_000)))
        server.enqueue(json(200, "\"hello\""))
        val api = testApi(server)
        val result = session.call { token -> api.get("users/settings", token, String.serializer()) }
        assertEquals("hello", result)
        assertEquals("Bearer access-1", server.takeRequest().headers["Authorization"])
        server.takeRequest() // The refresh.
        assertEquals("Bearer access-2", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun skippingAndSigningInChangeTheState() = runBlocking {
        assertEquals(SignIn.SignedOut, session.state.first())
        session.skip()
        assertEquals(SignIn.Skipped, session.state.first())
        signIn(createdAtSeconds = 1_000)
        assertEquals(SignIn.SignedIn, session.state.first())
    }

    /** Settings > Trakt Account on a TV that skipped: the sign-in screen shows again. */
    @Test
    fun signingOutAfterASkipShowsTheSignInScreenAgain() = runBlocking {
        session.skip()
        session.signOut()
        assertEquals(SignIn.SignedOut, session.state.first())
    }

    // A refresh answer that arrives late must only change the session that sent it.

    private fun renewalDue() {
        signIn(createdAtSeconds = 1_000)
        nowMillis = (1_000L + 19 * 60 * 60) * 1000
    }

    @Test
    fun aLateRefreshAnswerDoesNotUndoASignOut() = runBlocking {
        renewalDue()
        val release = CountDownLatch(1)
        server.dispatcher = heldAnswers(release) { json(200, tokenJson("access-2", "refresh-2", createdAt = nowMillis / 1000)) }
        val renewal = async(Dispatchers.IO) { session.accessToken() }
        server.takeRequest()
        session.signOut()
        release.countDown()
        assertNull(renewal.await())
        assertEquals(SignIn.SignedOut, session.state.first())
    }

    @Test
    fun aLateRefreshAnswerDoesNotReplaceANewSignIn() = runBlocking {
        renewalDue()
        val release = CountDownLatch(1)
        server.dispatcher = heldAnswers(release) { json(200, tokenJson("access-2", "refresh-2", createdAt = nowMillis / 1000)) }
        val renewal = async(Dispatchers.IO) { session.accessToken() }
        server.takeRequest()
        session.save(TokenResponse("access-B", "refresh-B", 86_400, nowMillis / 1000))
        release.countDown()
        assertNull(renewal.await())
        assertEquals("access-B", session.accessToken())
    }

    @Test
    fun aLateRefusalDoesNotSignOutANewSignIn() = runBlocking {
        renewalDue()
        val release = CountDownLatch(1)
        server.dispatcher = heldAnswers(release) { json(401) }
        val renewal = async(Dispatchers.IO) { session.accessToken() }
        server.takeRequest()
        session.save(TokenResponse("access-B", "refresh-B", 86_400, nowMillis / 1000))
        release.countDown()
        assertNull(renewal.await())
        assertEquals(SignIn.SignedIn, session.state.first())
        assertEquals("access-B", session.accessToken())
    }

    @Test
    fun aCallForOneSessionDoesNotRunInTheNext() = runBlocking {
        signIn(createdAtSeconds = 1_000)
        nowMillis = 1_000_000L
        val first = session.generation.value
        session.save(TokenResponse("access-B", "refresh-B", 86_400, 1_000))
        assertNull(session.call(first) { it })
        assertEquals("access-B", session.call(session.generation.value) { it })
    }

    /**
     * The caller is cancelled while Trakt rotates the pair. The new pair must still be saved,
     * because Trakt refuses the old refresh token after one use.
     */
    @Test
    fun aCancelledCallerDoesNotLoseTheRotatedPair() = runBlocking {
        renewalDue()
        val release = CountDownLatch(1)
        val spent = AtomicBoolean(false)
        server.dispatcher = heldAnswers(release) { request ->
            if (request.body!!.utf8().contains("refresh-1") && spent.getAndSet(true)) {
                json(400)
            } else {
                json(200, tokenJson("access-2", "refresh-2", createdAt = nowMillis / 1000))
            }
        }
        val caller = launch(Dispatchers.IO) { session.accessToken() }
        server.takeRequest()
        caller.cancelAndJoin()
        release.countDown()
        assertEquals("access-2", session.accessToken())
        assertEquals(SignIn.SignedIn, session.state.first())
    }

    @Test
    fun callersThatNeedARenewalAtTheSameTimeShareOne() = runBlocking {
        renewalDue()
        val release = CountDownLatch(1)
        server.dispatcher = heldAnswers(release) { json(200, tokenJson("access-2", "refresh-2", createdAt = nowMillis / 1000)) }
        val callers = List(3) { async(Dispatchers.IO) { session.accessToken() } }
        server.takeRequest()
        release.countDown()
        assertEquals(List(3) { "access-2" }, callers.awaitAll())
        assertEquals(1, server.requestCount)
    }

    // A generation and its tokens must change and be read as one.

    /** With the real DataStore: the moment the generation rises, it is B's token. */
    @Test
    fun theNewGenerationNeverServesTheOldAccountsToken() = runBlocking {
        signIn(createdAtSeconds = 1_000)
        nowMillis = 1_000_000L
        val a = session.generation.value
        val held = CountDownLatch(1)
        val holding = CountDownLatch(1)
        // An ordinary DataStore transaction that B's sign-in has to wait behind.
        val transaction = launch(Dispatchers.IO) {
            store.edit {
                holding.countDown()
                held.await()
            }
        }
        holding.await()
        val watcher = async(Dispatchers.IO) {
            val risen = session.generation.first { it != a }
            session.call(risen) { it }
        }
        val saving = async(Dispatchers.IO) { session.save(TokenResponse("access-B", "refresh-B", 86_400, 1_000)) }
        try {
            delay(200)
            // B's tokens are not written yet, so the generation has not risen.
            assertEquals(a, session.generation.value)
        } finally {
            held.countDown()
        }
        saving.await()
        transaction.join()
        assertEquals("access-B", watcher.await())
        assertNull(session.call(a) { it })
    }

    /** The renewal fails after a sign-out, so the old token must not be used offline. */
    @Test
    fun aSignOutDuringAFailedRenewalDoesNotAuthoriseTheOldToken() = runBlocking {
        renewalDue()
        val release = CountDownLatch(1)
        server.dispatcher = heldAnswers(release) { json(500) }
        val ran = AtomicBoolean(false)
        val call = async(Dispatchers.IO) {
            session.call(session.generation.value) {
                ran.set(true)
                it
            }
        }
        server.takeRequest()
        session.signOut()
        release.countDown()
        assertNull(call.await())
        assertFalse(ran.get())
    }

    @Test
    fun aSignOutDuringASuccessfulRenewalDoesNotHandOutTheNewToken() = runBlocking {
        renewalDue()
        val release = CountDownLatch(1)
        server.dispatcher = heldAnswers(release) { json(200, tokenJson("access-2", "refresh-2", createdAt = nowMillis / 1000)) }
        val ran = AtomicBoolean(false)
        val call = async(Dispatchers.IO) {
            session.call {
                ran.set(true)
                it
            }
        }
        server.takeRequest()
        session.signOut()
        release.countDown()
        assertNull(call.await())
        assertFalse(ran.get())
        assertEquals(SignIn.SignedOut, session.state.first())
    }

    /** A retry after a 401 stays with the account of the first try. */
    @Test
    fun aRetryAfter401DoesNotMoveToTheNextAccount() = runBlocking {
        signIn(createdAtSeconds = 1_000)
        nowMillis = 1_000_000L
        val used = mutableListOf<String>()
        val result = session.call { token ->
            used += token
            if (used.size == 1) {
                session.save(TokenResponse("access-B", "refresh-B", 86_400, 1_000))
                throw TraktApi.Unauthorized()
            }
            token
        }
        assertNull(result)
        assertEquals(listOf("access-1"), used)
    }

    // DataStore can commit before a cancelled caller resumes.
    // The generation must still rise with the tokens it belongs to.

    /** Runs its tasks only when the test says, so a test can hold a caller after DataStore commits. */
    private class HeldDispatcher : CoroutineDispatcher() {
        private val tasks = LinkedBlockingQueue<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) = tasks.put(block)

        fun runOne() = (tasks.poll(10, TimeUnit.SECONDS) ?: error("Nothing to run")).run()
    }

    private val accessKey = stringPreferencesKey("access_token")

    /**
     * Starts [transition] on a held caller, waits until the real DataStore has [committed] it,
     * then cancels the caller before it resumes, and lets it finish. The caller runs two tasks
     * first: its start, and DataStore's edit, which DataStore runs in the caller's context.
     */
    private fun cancelAfterCommit(committed: (Preferences) -> Boolean, transition: suspend () -> Unit) {
        val caller = HeldDispatcher()
        val job = CoroutineScope(caller).launch { transition() }
        caller.runOne()
        caller.runOne()
        runBlocking { withTimeout(10_000) { store.data.first { committed(it) } } }
        job.cancel()
        while (!job.isCompleted) caller.runOne()
    }

    /** B's tokens are committed, the caller is cancelled, and A's generation must not get B. */
    @Test
    fun aSignInCancelledAfterItsWriteStillChangesTheGeneration() = runBlocking {
        signIn(createdAtSeconds = 1_000)
        nowMillis = 1_000_000L
        val a = session.generation.value
        cancelAfterCommit({ it[accessKey] == "access-B" }) {
            session.save(TokenResponse("access-B", "refresh-B", 86_400, 1_000))
        }
        assertNull(session.call(a) { it })
        assertEquals("access-B", session.call(session.generation.value) { it })
    }

    /** A cancelled sign-out, then the renewal fails. The old token must not be used offline. */
    @Test
    fun aSignOutCancelledAfterItsWriteStillEndsTheOldAccount() = runBlocking {
        renewalDue()
        val release = CountDownLatch(1)
        server.dispatcher = heldAnswers(release) { json(500) }
        val ran = AtomicBoolean(false)
        val call = async(Dispatchers.IO) {
            session.call(session.generation.value) {
                ran.set(true)
                it
            }
        }
        server.takeRequest()
        try {
            cancelAfterCommit({ it[accessKey] == null }) { session.signOut() }
        } finally {
            release.countDown()
        }
        assertNull(call.await())
        assertFalse(ran.get())
        assertEquals(SignIn.SignedOut, session.state.first())
    }
}
