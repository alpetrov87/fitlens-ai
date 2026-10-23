package com.fitlens.ai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/** Garmin Personal API client for one person's own account. Never logs credentials or responses. */
data class GarminDayData(val summary: ImportedDailyMetrics, val activities: List<GarminActivity>)

/** Parses Garmin's access token without logging or exposing the response value. */
internal fun parseGarminAccessToken(body: String): String? {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return null
    val parsed = runCatching { JSONObject(trimmed) }.getOrNull()
    if (parsed != null) {
        fun findToken(json: JSONObject, depth: Int): String? {
            val acceptedNames = setOf(
                "access_token", "accesstoken", "token", "bearertoken", "apitoken", "personalapitoken", "tokenvalue",
            )
            val keys = json.keys().asSequence().toList()
            for (key in keys) {
                val value = json.opt(key)
                if (key.lowercase() in acceptedNames && value is String && value.isNotBlank()) return value.trim()
            }
            if (depth > 0) {
                for (key in keys) {
                    val child = json.optJSONObject(key) ?: continue
                    findToken(child, depth - 1)?.let { return it }
                }
            }
            return null
        }
        return findToken(parsed, 2)
    }
    if (trimmed.startsWith('"') && trimmed.endsWith('"')) {
        runCatching { org.json.JSONTokener(trimmed).nextValue() as? String }.getOrNull()
            ?.takeIf { it.isNotBlank() }?.let { return it.trim() }
    }
    val formToken = Regex("(?:^|&)access_token=([^&]+)").find(trimmed)?.groupValues?.get(1)
        ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }
    if (!formToken.isNullOrBlank()) return formToken
    return trimmed.takeIf { it.length >= 16 && it.matches(Regex("[A-Za-z0-9._~+/=-]+")) }
}

class GarminPersonalApi(
    private val keyStore: GarminPersonalKeyStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun connect(apiKey: String): GarminDayData = withContext(Dispatchers.IO) {
        val normalizedKey = apiKey.trim()
        require(normalizedKey.isNotEmpty()) { "Постави Garmin Personal API ключа." }
        val token = exchangeKey(normalizedKey)
        val today = LocalDate.now().toString()
        val data = fetchDay(token, today)
        keyStore.save(normalizedKey)
        data
    }

    suspend fun sync(): GarminDayData = withContext(Dispatchers.IO) {
        val apiKey = keyStore.load() ?: error("Добави Garmin Personal API ключ, за да синхронизираш.")
        val token = exchangeKey(apiKey) // Garmin access tokens last 24h; there is no refresh token.
        fetchDay(token, LocalDate.now().toString())
    }

    fun disconnect() = keyStore.clear()
    fun isConnected(): Boolean = keyStore.hasKey()

    private fun fetchDay(token: String, date: String): GarminDayData {
        val summaryJson = getJson(
            token,
            "$BASE_URL/usersummary-service/usersummary/daily?calendarDate=$date",
            "дневното обобщение",
        ) as? JSONObject
        val summary = ImportedDailyMetrics(
            date = summaryJson?.optString("calendarDate")?.takeIf { it.isNotBlank() } ?: date,
            steps = summaryJson?.optNullableInt("totalSteps"),
            activeMinutes = summaryJson?.optNullableDouble("activeSeconds")?.let { (it / 60).toInt() },
            distanceKm = summaryJson?.optNullableDouble("totalDistanceMeters")?.div(1000.0),
            garminPersonalApiData = true,
            garminCaloriesConsumed = summaryJson?.optNullableDouble("consumedKilocalories")?.toInt(),
        )
        val activities = mutableListOf<GarminActivity>()
        var start = 0
        do {
            val page = getJson(
                token,
                "$BASE_URL/activitylist-service/activities/search/activities?start=$start&limit=$ACTIVITY_PAGE_SIZE&sortOrder=asc&startDate=$date&endDate=$date&excludeChildren=false",
                "дневните активности",
            ) as? JSONArray ?: JSONArray()
            for (index in 0 until page.length()) {
                val item = page.optJSONObject(index) ?: continue
                activities += item.toGarminActivity()
            }
            start += page.length()
        } while (page.length() == ACTIVITY_PAGE_SIZE && start < MAX_DAILY_ACTIVITIES)
        return GarminDayData(summary, activities)
    }

    private fun exchangeKey(apiKey: String): String {
        val request = Request.Builder()
            .url("$BASE_URL/personalapitoken-service/personal-api/personal-api-key/token")
            .header("Authorization", "ApiKey $apiKey")
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw apiError(response.code, "ключа")
            val body = response.body?.string().orEmpty()
            val token = parseGarminAccessToken(body)
            if (token == null) {
                val fields = runCatching { JSONObject(body).keys().asSequence().sorted().joinToString(", ") }.getOrNull()
                val hint = fields?.takeIf { it.isNotBlank() }?.let { " Получени JSON полета: $it." }.orEmpty()
                throw IllegalStateException("Garmin прие заявката, но отговорът не съдържа разпознат token.$hint Провери актуалната версия на приложението и съобщи тези имена на полетата, без да изпращаш ключа или token.")
            }
            return token
        }
    }

    /** Empty 204 responses represent an available resource with no records for the requested day. */
    private fun getJson(token: String, url: String, resource: String): Any? {
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
        client.newCall(request).execute().use { response ->
            if (response.code == 204) return null
            if (!response.isSuccessful) throw apiError(response.code, resource)
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            return if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body)
        }
    }

    private fun JSONObject.toGarminActivity(): GarminActivity {
        val sport = optJSONObject("activityType")?.optString("typeKey")?.takeIf { it.isNotBlank() }
            ?: optString("sportTypeId", "other")
        return GarminActivity(
            activityId = optLong("activityId"),
            name = optString("activityName").ifBlank { sport.replace('_', ' ').replaceFirstChar { it.uppercase() } },
            activityTypeKey = sport,
            startTimeLocal = optString("startTimeLocal"),
            durationSeconds = optNullableDouble("duration"),
            distanceMeters = optNullableDouble("distance"),
            calories = optNullableDouble("calories")?.toInt(),
            steps = optNullableInt("steps"),
            averageHeartRate = optNullableDouble("averageHR")?.toInt(),
            maxHeartRate = optNullableDouble("maxHR")?.toInt(),
            elevationGainMeters = optNullableDouble("elevationGain"),
        )
    }

    private fun apiError(code: Int, resource: String): IllegalStateException = when (code) {
        401, 403 -> IllegalStateException("Garmin отказа достъп до $resource ($code). Провери Personal API ключа и дали той не е изтекъл или отнет.")
        429 -> IllegalStateException("Garmin временно ограничи заявките. Изчакай малко и опитай отново.")
        else -> IllegalStateException("Garmin не върна $resource (HTTP $code). Опитай отново по-късно.")
    }

    private fun JSONObject.optNullableInt(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
    private fun JSONObject.optNullableDouble(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key)

    private companion object {
        const val BASE_URL = "https://personal.connectapi.garmin.com"
        const val ACTIVITY_PAGE_SIZE = 99
        const val MAX_DAILY_ACTIVITIES = 990
    }
}
