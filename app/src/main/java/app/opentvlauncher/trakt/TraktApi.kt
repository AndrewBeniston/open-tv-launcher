package app.opentvlauncher.trakt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * The Trakt API calls the launcher makes. The client id and secret come from BuildConfig, which
 * reads the git-ignored local.properties. Nothing here logs a token, a code or a secret.
 */
class TraktApi(
    private val http: OkHttpClient,
    private val clientId: String,
    private val clientSecret: String,
    private val base: HttpUrl = "https://api.trakt.tv".toHttpUrl(),
) {
    val configured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    /** Step 1 of the device flow: a code for the user to enter at the verification URL. */
    suspend fun deviceCode(): DeviceCode {
        val (code, body) = post("oauth/device/code", buildJsonObject { put("client_id", clientId) })
        if (code != 200) throw IOException("Trakt device code failed: HTTP $code")
        val device = decode(DeviceCode.serializer(), body)
        if (device.deviceCode.isBlank() || device.userCode.isBlank() || device.verificationUrl.isBlank() || device.expiresIn <= 0) {
            throw BadResponse("Trakt sent an incomplete device code")
        }
        return device
    }

    /** Step 2: one poll. Trakt answers 400 until the user approves the code. */
    suspend fun pollDeviceToken(deviceCode: String): DevicePoll {
        val (code, body) = post(
            "oauth/device/token",
            buildJsonObject {
                put("code", deviceCode)
                put("client_id", clientId)
                put("client_secret", clientSecret)
            },
        )
        return when (code) {
            200 -> DevicePoll.Approved(tokens(body))
            400 -> DevicePoll.Pending
            429 -> DevicePoll.SlowDown
            404, 409, 410 -> DevicePoll.Expired
            418 -> DevicePoll.Denied
            else -> throw IOException("Trakt device token failed: HTTP $code")
        }
    }

    /**
     * A new access token. Trakt rotates the refresh token too, so the caller stores both.
     * Returns null when Trakt rejects the refresh token: the TV must sign in again.
     */
    suspend fun refresh(refreshToken: String): TokenResponse? {
        val (code, body) = post(
            "oauth/token",
            buildJsonObject {
                put("refresh_token", refreshToken)
                put("client_id", clientId)
                put("client_secret", clientSecret)
                put("redirect_uri", "urn:ietf:wg:oauth:2.0:oob")
                put("grant_type", "refresh_token")
            },
        )
        return when (code) {
            200 -> tokens(body)
            400, 401, 403 -> null
            else -> throw IOException("Trakt token refresh failed: HTTP $code")
        }
    }

    /**
     * A signed-in GET. Throws [Unauthorized] on 401, so the caller can refresh and try again.
     * The request and the decoding both run on IO, so a caller on the main thread never waits on them.
     */
    suspend fun <T> get(path: String, accessToken: String, serializer: KSerializer<T>, query: Map<String, String> = emptyMap()): T =
        withContext(Dispatchers.IO) { decode(serializer, fetch(path, accessToken, query).first) }

    /**
     * Every item of a list that Trakt splits into pages: the watched, watchlist and ratings lists.
     * Trakt sends 100 items a page unless asked for more, and at most 250 (seen on 24 Oct 2026).
     */
    suspend fun <T> getAll(path: String, accessToken: String, item: KSerializer<T>, query: Map<String, String> = emptyMap()): List<T> =
        withContext(Dispatchers.IO) {
            // Each page is decoded here on IO: a large watched list is four or more pages of 250.
            val items = mutableListOf<T>()
            var page = 1
            while (true) {
                val (body, pages) = fetch(path, accessToken, query + mapOf("page" to page.toString(), "limit" to PAGE_SIZE.toString()))
                items += decode(ListSerializer(item), body)
                // A list without paging counts as one page.
                if (page >= minOf(pages ?: 1, MAX_PAGES)) break
                page++
            }
            items
        }

    /**
     * Every page of a list, for a check that must see all of it, such as whether a play is in the
     * history. A list with more than [maxPages] pages, a page that fails, or a page count that is
     * missing, unreadable or different from the first page's throws: the caller cannot tell what
     * is missing.
     */
    suspend fun <T> getComplete(
        path: String,
        accessToken: String,
        item: KSerializer<T>,
        query: Map<String, String> = emptyMap(),
        maxPages: Int = MAX_PAGES,
    ): List<T> = withContext(Dispatchers.IO) {
        val items = mutableListOf<T>()
        var announced: Int? = null
        var page = 1
        while (true) {
            val (body, pages) = fetch(path, accessToken, query + mapOf("page" to page.toString(), "limit" to PAGE_SIZE.toString()))
            if (pages == null || pages < 0) throw BadResponse("Trakt's page $page of $path has no page count")
            if (announced != null && pages != announced) throw BadResponse("Trakt's page count for $path changed from $announced to $pages")
            announced = pages
            if (pages > maxPages) throw BadResponse("Trakt's list has $pages pages, more than $maxPages")
            items += decode(ListSerializer(item), body)
            if (page >= pages) break
            page++
        }
        items
    }

    /** The body and the page count of one signed-in GET, on the calling thread: call it on IO. The count is null when it is missing or unreadable. */
    private fun fetch(path: String, accessToken: String, query: Map<String, String>): Pair<String, Int?> {
        val url = base.newBuilder().addPathSegments(path).apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val request = authorized(url, accessToken).header("Content-Type", "application/json").build()
        return http.newCall(request).execute().use { response ->
            if (response.code == 401) throw Unauthorized()
            if (!response.isSuccessful) throw IOException("Trakt GET $path failed: HTTP ${response.code}")
            response.body.string() to response.header("X-Pagination-Page-Count")?.toIntOrNull()
        }
    }

    /** A signed-in POST with a JSON body, such as a watchlist change. */
    suspend fun postAuthorized(path: String, accessToken: String, body: JsonObject): Int = postReading(path, accessToken, body).first

    /** A signed-in POST, with Trakt's answer: the status and the body. */
    suspend fun postReading(path: String, accessToken: String, body: JsonObject): Pair<Int, String> {
        val request = authorized(base.newBuilder().addPathSegments(path).build(), accessToken)
            .post(body.toString().toRequestBody(JSON))
            .build()
        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                if (response.code == 401) throw Unauthorized()
                response.code to response.body.string()
            }
        }
    }

    /** Decodes a body that a signed-in call returned. Throws [BadResponse] when it cannot be read. */
    fun <T> read(serializer: KSerializer<T>, body: String): T = decode(serializer, body)

    /** A signed-in DELETE, such as a paused item's progress. */
    suspend fun deleteAuthorized(path: String, accessToken: String): Int {
        val request = authorized(base.newBuilder().addPathSegments(path).build(), accessToken)
            .delete()
            .build()
        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                if (response.code == 401) throw Unauthorized()
                response.code
            }
        }
    }

    /** A request to [url] with Trakt's API headers and the sign-in's token: GET, POST and DELETE add the rest. */
    private fun authorized(url: HttpUrl, accessToken: String) = Request.Builder()
        .url(url)
        .header("trakt-api-version", "2")
        .header("trakt-api-key", clientId)
        .header("Authorization", "Bearer $accessToken")
        .header("User-Agent", USER_AGENT)

    private suspend fun post(path: String, body: JsonObject): Pair<Int, String> {
        val request = Request.Builder()
            .url(base.newBuilder().addPathSegments(path).build())
            .header("User-Agent", USER_AGENT)
            .post(body.toString().toRequestBody(JSON))
            .build()
        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { it.code to it.body.string() }
        }
    }

    /** A token answer with every field the session saves. */
    private fun tokens(body: String): TokenResponse {
        val tokens = decode(TokenResponse.serializer(), body)
        if (tokens.accessToken.isBlank() || tokens.refreshToken.isBlank() || tokens.createdAt <= 0) {
            throw BadResponse("Trakt sent an incomplete token")
        }
        return tokens
    }

    private fun <T> decode(serializer: KSerializer<T>, body: String): T = try {
        json.decodeFromString(serializer, body)
    } catch (e: IllegalArgumentException) {
        // SerializationException is an IllegalArgumentException: missing fields, wrong types, not JSON.
        throw BadResponse("Trakt sent an answer that cannot be read", e)
    }

    class Unauthorized : IOException("Trakt rejected the access token")

    /**
     * Trakt answered, but not with what the launcher reads. It is an IOException, so every caller
     * treats it as a failed request and nothing crashes.
     */
    class BadResponse(message: String, cause: Throwable? = null) : IOException(message, cause)

    companion object {
        // Trakt's firewall refuses requests that carry no User-Agent.
        const val USER_AGENT = "personal-tv-launcher/0.1"
        const val PAGE_SIZE = 250

        /** 10,000 items: a guard against a wrong page count, far above a personal history. */
        const val MAX_PAGES = 40
        private val JSON = "application/json".toMediaType()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    }
}

@Serializable
data class DeviceCode(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_url") val verificationUrl: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("created_at") val createdAt: Long,
)

sealed interface DevicePoll {
    data class Approved(val tokens: TokenResponse) : DevicePoll
    data object Pending : DevicePoll
    data object SlowDown : DevicePoll
    data object Expired : DevicePoll
    data object Denied : DevicePoll
}
