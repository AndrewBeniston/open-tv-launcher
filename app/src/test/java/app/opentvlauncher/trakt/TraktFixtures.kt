package app.opentvlauncher.trakt

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A Trakt API that talks to [server]. The id and secret are test values, not the real app's. */
fun testApi(server: MockWebServer) = TraktApi(OkHttpClient(), "test-client", "test-secret", server.url("/"))

fun json(code: Int, body: String = "{}"): MockResponse =
    MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build()

/** Holds each answer until [release] opens, so a test can act while a request is in flight. */
fun heldAnswers(release: CountDownLatch, answer: (RecordedRequest) -> MockResponse) = object : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        release.await(10, TimeUnit.SECONDS)
        return answer(request)
    }
}

fun tokenJson(access: String, refresh: String, createdAt: Long, expiresIn: Long = 86_400) =
    """{"access_token":"$access","token_type":"bearer","expires_in":$expiresIn,"refresh_token":"$refresh","scope":"public","created_at":$createdAt}"""

fun tempStore(): DataStore<Preferences> {
    val dir: File = Files.createTempDirectory("trakt-test").toFile()
    return PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
        File(dir, "trakt.preferences_pb")
    }
}

/**
 * A DataStore whose next write waits while [hold] is set: [entered] opens when a write is waiting.
 * A test uses it to act while a sign-in or sign-out is part way through.
 */
class HeldStore(private val inner: DataStore<Preferences> = tempStore()) : DataStore<Preferences> {
    @Volatile var hold: CountDownLatch? = null
    val entered = CountDownLatch(1)

    override val data: Flow<Preferences> get() = inner.data

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        hold?.let { latch ->
            hold = null
            entered.countDown()
            withContext(Dispatchers.IO) { latch.await(10, TimeUnit.SECONDS) }
        }
        return inner.updateData(transform)
    }
}

/** Trakt's answer to `POST /sync/history/remove`: the plays it removed. */
fun historyRemoved(movies: Int = 0, episodes: Int = 0): MockResponse = json(
    200,
    """{"deleted":{"movies":$movies,"episodes":$episodes},"not_found":{"movies":[],"shows":[],"seasons":[],"episodes":[]}}""",
)

/** Trakt's answer to `POST /sync/history` when it counted the plays. */
fun historyAdded(movies: Int = 0, episodes: Int = 0): MockResponse = json(
    201,
    """{"added":{"movies":$movies,"episodes":$episodes},"not_found":{"movies":[],"shows":[],"seasons":[],"episodes":[]}}""",
)
