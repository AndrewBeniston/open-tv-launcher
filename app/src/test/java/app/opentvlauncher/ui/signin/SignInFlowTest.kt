package app.opentvlauncher.ui.signin

import app.opentvlauncher.trakt.SignIn
import app.opentvlauncher.trakt.TraktApi
import app.opentvlauncher.trakt.TraktSession
import app.opentvlauncher.trakt.json
import app.opentvlauncher.trakt.tempStore
import app.opentvlauncher.trakt.testApi
import app.opentvlauncher.trakt.tokenJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The device-code flow, against a fake Trakt server. Polling waits run on virtual time. */
class SignInFlowTest {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private val deviceCode =
        """{"device_code":"dev-123","user_code":"8F3A21C7","verification_url":"https://trakt.tv/activate","expires_in":600,"interval":5}"""

    @Test
    fun approvalSavesTheTokens() = runTest {
        val session = TraktSession(tempStore(), testApi(server))
        server.enqueue(json(200, deviceCode))
        server.enqueue(json(400)) // Pending.
        server.enqueue(json(429)) // Slow down.
        server.enqueue(json(200, tokenJson("access-1", "refresh-1", createdAt = 1_000)))

        val states = SignInFlow(testApi(server), session, now = { testScheduler.currentTime }).states().toList()

        assertEquals(SignInState.Loading, states[0])
        val code = states[1] as SignInState.Code
        assertEquals("8F3A21C7", code.userCode)
        assertEquals("https://trakt.tv/activate", code.verificationUrl)
        assertEquals(SignInState.Approved, states.last())
        assertEquals(SignIn.SignedIn, session.state.first())

        server.takeRequest() // Device code.
        val poll = server.takeRequest().body!!.utf8()
        assertTrue(poll.contains("\"code\":\"dev-123\""))
        assertTrue(poll.contains("\"client_secret\":\"test-secret\""))
    }

    @Test
    fun anExpiredCodeIsReplacedByANewCode() = runTest {
        val session = TraktSession(tempStore(), testApi(server))
        server.enqueue(json(200, deviceCode.replace("\"expires_in\":600", "\"expires_in\":12")))
        server.enqueue(json(410)) // Expired.
        server.enqueue(json(200, deviceCode.replace("8F3A21C7", "11112222").replace("dev-123", "dev-456")))
        server.enqueue(json(200, tokenJson("access-1", "refresh-1", createdAt = 1_000)))

        val states = SignInFlow(testApi(server), session, now = { testScheduler.currentTime }).states().toList()

        val codes = states.filterIsInstance<SignInState.Code>().map { it.userCode }
        assertEquals(listOf("8F3A21C7", "11112222"), codes)
        assertEquals(SignInState.Approved, states.last())
    }

    @Test
    fun aCodeThatRunsOutAfterTheLastRenewalEndsInExpired() = runTest {
        val session = TraktSession(tempStore(), testApi(server))
        repeat(2) {
            server.enqueue(json(200, deviceCode.replace("\"expires_in\":600", "\"expires_in\":12")))
            repeat(3) { server.enqueue(json(400)) }
        }

        val states = SignInFlow(testApi(server), session, renewals = 1, now = { testScheduler.currentTime }).states().toList()

        assertEquals(2, states.count { it is SignInState.Code })
        assertEquals(SignInState.Expired, states.last())
        assertEquals(SignIn.SignedOut, session.state.first())
    }

    @Test
    fun aRefusedCodeEndsInDenied() = runTest {
        val session = TraktSession(tempStore(), testApi(server))
        server.enqueue(json(200, deviceCode))
        server.enqueue(json(418))
        val states = SignInFlow(testApi(server), session, now = { testScheduler.currentTime }).states().toList()
        assertEquals(SignInState.Denied, states.last())
    }

    @Test
    fun aBuildWithoutKeysSaysSo() = runTest {
        val api = TraktApi(OkHttpClient(), "", "", server.url("/"))
        val states = SignInFlow(api, TraktSession(tempStore(), api)).states().toList()
        assertEquals(listOf(SignInState.NotConfigured), states)
        assertEquals(0, server.requestCount)
    }

    // A 200 answer without the fields the launcher reads.

    @Test
    fun anUnreadableDeviceCodeEndsInFailed() = runTest {
        val session = TraktSession(tempStore(), testApi(server))
        server.enqueue(json(200, "{}"))
        val states = SignInFlow(testApi(server), session, now = { testScheduler.currentTime }).states().toList()
        assertEquals(listOf(SignInState.Loading, SignInState.Failed), states)
    }

    @Test
    fun anUnreadableTokenEndsInFailedAndSavesNothing() = runTest {
        val session = TraktSession(tempStore(), testApi(server))
        server.enqueue(json(200, deviceCode))
        server.enqueue(json(200, """{"access_token":"access-1","token_type":"bearer"}"""))
        val states = SignInFlow(testApi(server), session, now = { testScheduler.currentTime }).states().toList()
        assertEquals(SignInState.Failed, states.last())
        assertEquals(SignIn.SignedOut, session.state.first())
    }

    @Test
    fun aTokenWithBlankFieldsIsRefused() = runTest {
        val session = TraktSession(tempStore(), testApi(server))
        server.enqueue(json(200, deviceCode))
        server.enqueue(json(200, tokenJson("", "refresh-1", createdAt = 1_000)))
        val states = SignInFlow(testApi(server), session, now = { testScheduler.currentTime }).states().toList()
        assertEquals(SignInState.Failed, states.last())
        assertEquals(SignIn.SignedOut, session.state.first())
    }

    @Test
    fun theCodeIsShownInTwoGroups() {
        assertEquals("8F3A 21C7", formatUserCode("8F3A21C7"))
        assertEquals("ABC", formatUserCode("ABC"))
    }
}
