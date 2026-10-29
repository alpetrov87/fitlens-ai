package com.fitlens.ai.workouts

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal fun workoutBackendUrl(raw: String): String {
    val url = raw.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid backend URL")
    require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.encodedPath == "/" && url.query == null && url.fragment == null) { "HTTPS origin required" }
    return url.toString().removeSuffix("/")
}
data class WorkoutBackendSettings(val url: String, val token: String)
data class WorkoutHttpResponse(val code: Int, val body: String)
fun interface WorkoutHttpExecutor { fun execute(request: Request): WorkoutHttpResponse }
class BackendWorkoutTransport(
    private val settings: WorkoutBackendSettings,
    private val executor: WorkoutHttpExecutor = defaultExecutor(),
) : WorkoutUploadTransport {
    private val origin = workoutBackendUrl(settings.url)
    private var owner: String? = null
    init { require(settings.token.length in 40..512 && settings.token.matches(Regex("[A-Za-z0-9_-]+"))) }
    private fun request(method: String, path: String, payload: JSONObject? = null, key: String? = null): JSONObject {
        val boundOwner = if (path != "/v1/session") owner ?: throw WorkoutRemoteFailure("authentication") else null
        val body = payload?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(origin + path).header("Authorization", "Bearer ${settings.token}")
            .apply { boundOwner?.let { header("X-Bair-Owner", it) }; key?.let { require(it.matches(Regex("[a-f0-9]{64}"))); header("Idempotency-Key", it) } }
            .method(method, body).build()
        val response = try { executor.execute(request) } catch (_: Exception) { throw WorkoutRemoteFailure("network") }
        if (response.body.length > 65536) throw WorkoutRemoteFailure("invalid_response")
        if (response.code !in 200..299) throw WorkoutRemoteFailure(when(response.code) {
            401 -> "expired"; 403 -> "authentication"; 412 -> "account_mismatch"; 429 -> "rate_limit"; 422 -> "validation"; 409 -> "uncertain"; else -> "network"
        })
        return try { JSONObject(response.body) } catch (_: Exception) { throw WorkoutRemoteFailure("invalid_response") }
    }
    override fun authentication(): WorkoutConnection {
        val response = request("GET", "/v1/session")
        if (response.opt("connected") != true) { owner = null; return WorkoutConnection.SETUP_REQUIRED }
        val identity = response.optString("owner")
        if (!identity.matches(Regex("[a-f0-9]{64}"))) throw WorkoutRemoteFailure("invalid_response")
        owner = identity
        return WorkoutConnection.CONNECTED
    }
    override fun identity(): String? = owner?.let { "$origin#$it" }
    override fun disconnect() {
        if (request("DELETE", "/v1/session").opt("connected") != false) throw WorkoutRemoteFailure("invalid_response")
        owner = null
    }
    override fun create(payload: JSONObject, requestKey: String) = request("POST", "/v1/workouts", payload, requestKey)
    override fun update(id: String, payload: JSONObject, requestKey: String): JSONObject {
        require(id.matches(Regex("[1-9][0-9]{0,17}")))
        return request("PUT", "/v1/workouts/$id", payload, requestKey)
    }
    override fun lookup(requestKey: String): JSONObject {
        require(requestKey.matches(Regex("[a-f0-9]{64}")))
        return request("GET", "/v1/requests/$requestKey")
    }
    companion object {
        private fun defaultExecutor(): WorkoutHttpExecutor {
            val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).callTimeout(75, TimeUnit.SECONDS).build()
            return WorkoutHttpExecutor { request ->
                client.newCall(request).execute().use { response ->
                    val stream = response.body?.byteStream()
                    val bytes = java.io.ByteArrayOutputStream()
                    if (stream != null) {
                        val buffer = ByteArray(8192)
                        while (bytes.size() <= 65536) {
                            val count = stream.read(buffer, 0, minOf(buffer.size, 65537 - bytes.size()))
                            if (count < 0) break
                            bytes.write(buffer, 0, count)
                        }
                    }
                    WorkoutHttpResponse(response.code, bytes.toString("UTF-8"))
                }
            }
        }
    }
}
