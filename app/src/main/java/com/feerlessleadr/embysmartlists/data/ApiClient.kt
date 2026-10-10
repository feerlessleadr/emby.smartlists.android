package com.feerlessleadr.embysmartlists.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A failed call. [status] is the HTTP status, or 0 when the server could not be reached. */
class ApiException(val status: Int, message: String) : Exception(message) {
    val isUnauthorized: Boolean get() = status == 401
}

/** Result of signing in. */
data class SignInResult(val token: String, val userId: String, val userName: String, val isAdministrator: Boolean)

/**
 * Talks to Emby and the SmartLists plugin (see docs/api.md in the plugin repository). Every call carries the
 * access token in the X-Emby-Token header, never in the URL.
 */
class ApiClient(
    serverUrl: String,
    private val token: String? = null,
    private val appName: String = "Emby SmartLists",
    private val appVersion: String = "0",
    private val deviceId: String = "unknown",
    private val deviceName: String = "Android",
    private val http: OkHttpClient = defaultHttpClient(),
) {
    private val server = serverUrl.trimEnd('/')
    private val pluginBase = "$server/emby/Plugins/SmartLists"

    // ----- sign in -------------------------------------------------------------------------------------------

    suspend fun signIn(userName: String, password: String): SignInResult {
        val body = buildJsonObject {
            put("Username", userName)
            put("Pw", password)
        }
        val text = execute(
            Request.Builder()
                .url("$server/emby/Users/AuthenticateByName".toHttpUrl())
                .header("X-Emby-Authorization", authorizationHeader())
                .post(body.toString().toRequestBody(JSON)),
            authenticated = false,
        )
        val root = AppJson.parseToJsonElement(text).jsonObject
        val user = root["User"]?.jsonObject
        val policy = user?.get("Policy")?.jsonObject
        return SignInResult(
            token = (root["AccessToken"] as? JsonPrimitive)?.contentOrNull ?: throw ApiException(0, "The server did not return an access token."),
            userId = (user?.get("Id") as? JsonPrimitive)?.contentOrNull.orEmpty(),
            userName = (user?.get("Name") as? JsonPrimitive)?.contentOrNull ?: userName,
            isAdministrator = (policy?.get("IsAdministrator") as? JsonPrimitive)?.contentOrNull == "true",
        )
    }

    // ----- plugin --------------------------------------------------------------------------------------------

    suspend fun info(): PluginInfo = AppJson.decodeFromString(PluginInfo.serializer(), get("$pluginBase/info"))

    suspend fun catalog(): Catalog = AppJson.decodeFromString(Catalog.serializer(), get("$pluginBase/catalog"))

    suspend fun lists(): List<SmartListDoc> = SmartListDoc.parseArray(get("$pluginBase/"))

    suspend fun list(id: String): SmartListDoc = SmartListDoc.parse(get("$pluginBase/$id"))

    suspend fun create(doc: JsonObject, skipRefresh: Boolean = false): SmartListDoc =
        SmartListDoc.parse(send("POST", "$pluginBase/?skipRefresh=$skipRefresh", doc.toString()))

    suspend fun update(id: String, doc: JsonObject, skipRefresh: Boolean = false): SmartListDoc =
        SmartListDoc.parse(send("PUT", "$pluginBase/$id?skipRefresh=$skipRefresh", doc.toString()))

    suspend fun delete(id: String, deleteEmbyList: Boolean) {
        send("DELETE", "$pluginBase/$id?deleteEmbyList=$deleteEmbyList", null)
    }

    suspend fun enable(id: String) {
        send("POST", "$pluginBase/$id/enable", "")
    }

    suspend fun disable(id: String) {
        send("POST", "$pluginBase/$id/disable", "")
    }

    suspend fun refresh(id: String) {
        send("POST", "$pluginBase/$id/refresh", "")
    }

    suspend fun refreshAll() {
        send("POST", "$pluginBase/refresh", "")
    }

    suspend fun users(): List<EmbyUser> =
        AppJson.parseToJsonElement(get("$pluginBase/users")).jsonArray.map {
            val o = it.jsonObject
            EmbyUser((o["Id"] as? JsonPrimitive)?.contentOrNull.orEmpty(), (o["Name"] as? JsonPrimitive)?.contentOrNull.orEmpty())
        }

    suspend fun libraries(): List<ValueOption> =
        AppJson.parseToJsonElement(get("$pluginBase/libraries")).jsonArray.map {
            val o = it.jsonObject
            val name = (o["Name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            ValueOption(name, name)
        }

    /** Ids of lists that are refreshing or queued right now (best effort; empty when the shape is unfamiliar). */
    suspend fun ongoingListIds(): Set<String> {
        val ongoing = AppJson.parseToJsonElement(get("$pluginBase/Status/Ongoing"))
        val array = (ongoing as? kotlinx.serialization.json.JsonArray) ?: return emptySet()
        return array.mapNotNull { (it as? JsonObject)?.get("listId")?.let { v -> (v as? JsonPrimitive)?.contentOrNull } }.toSet()
    }

    // ----- plumbing ------------------------------------------------------------------------------------------

    private suspend fun get(url: String): String = execute(Request.Builder().url(url.toHttpUrl()).get())

    private suspend fun send(method: String, url: String, json: String?): String {
        val body = (json ?: "").toRequestBody(JSON)
        val builder = Request.Builder().url(url.toHttpUrl())
        when (method) {
            "POST" -> builder.post(body)
            "PUT" -> builder.put(body)
            "DELETE" -> if (json == null) builder.delete() else builder.delete(body)
            else -> error("Unsupported method $method")
        }
        return execute(builder)
    }

    private suspend fun execute(builder: Request.Builder, authenticated: Boolean = true): String = withContext(Dispatchers.IO) {
        if (authenticated) {
            val t = token ?: throw ApiException(401, "You are signed out.")
            builder.header("X-Emby-Token", t)
        }
        builder.header("Accept", "application/json")
        try {
            http.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ApiException(response.code, describeError(response.code, text))
                text
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: javax.net.ssl.SSLException) {
            throw ApiException(0, "Could not make a secure connection: ${e.message ?: "certificate problem"}.")
        } catch (e: IOException) {
            throw ApiException(0, "Could not reach the server: ${e.message ?: "network error"}.")
        }
    }

    private fun authorizationHeader(): String =
        "MediaBrowser Client=\"$appName\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"$appVersion\""

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        /** Turns an error response into a sentence for a person. The plugin sends {title,status,detail}. */
        fun describeError(status: Int, body: String): String {
            val detail = try {
                (AppJson.parseToJsonElement(body).jsonObject["detail"] as? JsonPrimitive)?.contentOrNull
            } catch (e: Exception) {
                null
            }
            return when {
                !detail.isNullOrBlank() -> detail
                status == 401 -> "Your session has expired. Sign in again."
                status == 403 -> "This account is not an Emby administrator."
                status == 404 -> "Not found. Is the SmartLists plugin installed and up to date?"
                body.isNotBlank() && body.length < 200 && !body.trimStart().startsWith("<") -> body.trim()
                else -> "The server answered with HTTP $status."
            }
        }
    }
}
