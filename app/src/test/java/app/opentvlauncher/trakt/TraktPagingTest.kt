package app.opentvlauncher.trakt

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors

/**
 * Trakt splits the watched, watchlist and ratings lists into pages (seen on 25 Oct 2026). The
 * launcher read only the first page: 100 of 912 watched films.
 */
class TraktPagingTest {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    /** Answers [path] with [pages] pages of three films each, numbered from 1. */
    private fun pagedFilms(path: String, pages: Int) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.url.encodedPath != path) return json(404)
                val page = request.url.queryParameter("page")?.toInt() ?: 1
                val films = (1..3).joinToString(",") { n -> """{"movie":{"title":"Film ${(page - 1) * 3 + n}","ids":{"trakt":${(page - 1) * 3 + n}}}}""" }
                return MockResponse.Builder()
                    .code(200)
                    .body("[$films]")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Pagination-Page", page.toString())
                    .addHeader("X-Pagination-Page-Count", pages.toString())
                    .build()
            }
        }
    }

    @Test
    fun everyPageIsRead() = runBlocking {
        pagedFilms("/sync/watched/movies", pages = 4)
        val films = testApi(server).getAll("sync/watched/movies", "token", TraktWatched.serializer())
        assertEquals((1..12).map { "Film $it" }, films.map { it.movie!!.title })
        val requests = (1..4).map { server.takeRequest().url }
        assertEquals(listOf("1", "2", "3", "4"), requests.map { it.queryParameter("page") })
        assertEquals(setOf("250"), requests.map { it.queryParameter("limit") }.toSet())
    }

    @Test
    fun aListWithoutPagesIsReadOnce() = runBlocking {
        server.enqueue(json(200, """[{"rating":10,"movie":{"title":"Gearman","ids":{"trakt":7}}}]"""))
        val rated = testApi(server).getAll("sync/ratings/movies", "token", TraktRated.serializer())
        assertEquals(listOf(10), rated.map { it.rating })
        assertEquals(1, server.requestCount)
    }

    /** Records the thread that decodes each item. */
    private class ThreadRecorder<T>(private val inner: KSerializer<T>) : KSerializer<T> by inner {
        val threads: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

        override fun deserialize(decoder: Decoder): T {
            threads += Thread.currentThread().name
            return inner.deserialize(decoder)
        }
    }

    /**
     * The refresh starts on the main thread, and every page was decoded there. The decoding must
     * run off the caller's thread.
     */
    @Test
    fun pagesAreDecodedOffTheCallersThread() {
        pagedFilms("/sync/watched/movies", pages = 3)
        val paged = ThreadRecorder(TraktWatched.serializer())
        val single = ThreadRecorder(TraktWatched.serializer())
        val caller = Executors.newSingleThreadExecutor { Thread(it, "ui-caller") }
        try {
            runBlocking(caller.asCoroutineDispatcher()) {
                assertEquals(9, testApi(server).getAll("sync/watched/movies", "token", paged).size)
                testApi(server).get("sync/watched/movies", "token", ListSerializer(single))
            }
        } finally {
            caller.shutdown()
        }
        assertTrue(paged.threads.isNotEmpty() && single.threads.isNotEmpty())
        // Coroutine debug mode adds " @coroutine#n" to the thread name.
        assertFalse(paged.threads.toString(), paged.threads.any { it.startsWith("ui-caller") })
        assertFalse(single.threads.toString(), single.threads.any { it.startsWith("ui-caller") })
    }
}
