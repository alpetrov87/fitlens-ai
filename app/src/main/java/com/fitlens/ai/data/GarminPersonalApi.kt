package com.fitlens.ai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** Garmin Personal API client for one person's own account. Never logs credentials or responses. */
class GarminPersonalApi(
    private val keyStore: GarminPersonalKeyStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun connect(apiKey: String): ImportedDailyMetrics = withContext(Dispatchers.IO) {
        val token = exchangeKey(apiKey)
        val today = LocalDate.now().toString()
        val metrics = fetchDailySummary(token, today)
        keyStore.save(apiKey)
        metrics
    }

    suspend fun sync(): ImportedDailyMetrics = withContext(Dispatchers.IO) {
        val apiKey = keyStore.load() ?: error("Добави Garmin Personal API ключ, за да синхронизираш.")
        val token = exchangeKey(apiKey) // Garmin access tokens last 24h; there is no refresh token.
        fetchDailySummary(token, LocalDate.now().toString())
    }

    fun disconnect() = keyStore.clear()
    fun isConnected(): Boolean = keyStore.hasKey()

    private fun exchangeKey(apiKey: String): String {
        val request = Request.Builder()
            .url("$BASE_URL/personalapitoken-service/personal-api/personal-api-key/token")
            .header("Authorization", "ApiKey $apiKey")
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw apiError(response.code, "ключа")
            val body = response.body?.string().orEmpty()
            val token = runCatching { JSONObject(body).optString("access_token") }.getOrNull()
            require(!token.isNullOrBlank() && token != "null") {
                "Garmin не върна access token. Провери дали ключът е копиран изцяло."
            }
            return token
        }
    }

    private fun fetchDailySummary(token: String, date: String): ImportedDailyMetrics {
        val request = Request.Builder()
            .url("$BASE_URL/usersummary-service/usersummary/daily?calendarDate=$date")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 204) return ImportedDailyMetrics(date = date, garminPersonalApiData = true)
            if (!response.isSuccessful) throw apiError(response.code, "данните")
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            val actualDate = json.optString("calendarDate", date).takeIf { it.isNotBlank() } ?: date
            val meters = json.optNullableDouble("totalDistanceMeters")
            val activeSeconds = json.optNullableDouble("activeSeconds")
            return ImportedDailyMetrics(
                date = actualDate,
                steps = json.optNullableInt("totalSteps"),
                activeMinutes = activeSeconds?.let { (it / 60).toInt() },
                distanceKm = meters?.div(1000.0),
                garminPersonalApiData = true,
            )
        }
    }

    private fun apiError(code: Int, resource: String): IllegalStateException = when (code) {
        401, 403 -> IllegalStateException("Garmin отказа достъп до $resource ($code). Провери Personal API ключа и дали той не е изтекъл или отнет.")
        429 -> IllegalStateException("Garmin временно ограничи заявките. Изчакай малко и опитай отново.")
        else -> IllegalStateException("Garmin не върна $resource (HTTP $code). Опитай отново по-късно.")
    }

    private fun JSONObject.optNullableInt(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
    private fun JSONObject.optNullableDouble(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key)

    private companion object { const val BASE_URL = "https://personal.connectapi.garmin.com" }
}
