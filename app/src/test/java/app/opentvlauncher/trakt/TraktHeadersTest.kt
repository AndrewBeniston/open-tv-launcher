package app.opentvlauncher.trakt

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Merge gate V5: each signed-in call, GET, POST and DELETE, sends the same Trakt headers. */
class TraktHeadersTest {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    @Test
    fun getPostAndDeleteSendTheSameSignedInHeaders() = runBlocking {
        val api = testApi(server)
        server.enqueue(json(200, "[]"))
        server.enqueue(json(201))
        server.enqueue(json(204, ""))
        api.get("sync/playback", "access", ListSerializer(JsonElement.serializer()))
        api.postAuthorized("sync/watchlist", "access", JsonObject(emptyMap()))
        api.deleteAuthorized("sync/playback/1", "access")
        val requests = List(3) { server.takeRequest() }
        assertEquals(listOf("GET", "POST", "DELETE"), requests.map { it.method })
        requests.forEach { request ->
            assertEquals("2", request.headers["trakt-api-version"])
            assertEquals("test-client", request.headers["trakt-api-key"])
            assertEquals("Bearer access", request.headers["Authorization"])
        }
        assertTrue(requests.all { it.headers["User-Agent"] == requests[0].headers["User-Agent"] && it.headers["User-Agent"].orEmpty().isNotEmpty() })
        assertEquals("application/json", requests[0].headers["Content-Type"])
    }
}
