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

/** Garmin's official client reads the token endpoint's response body as a raw JWT string. */
internal fun parseGarminAccessToken(body: String): String? = body.trim().takeIf(::isGarminJwt)

/** Matches Garmin's official client: the token response is a JWT whose payload contains exp. */
private fun isGarminJwt(token: String): Boolean {
    val parts = token.split('.')
    if (parts.size != 3 || parts.any { it.isEmpty() }) return false
    val payload = runCatching {
        String(java.util.Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
    }.getOrNull() ?: return false
    val expiration = Regex("\\\"exp\\\"\\s*:\\s*\\\"?([0-9]+(?:\\.[0-9]+)?)\\\"?")
        .find(payload)?.groupValues?.get(1)?.toDoubleOrNull()
    return expiration != null && expiration > 0.0
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

    suspend fun fetchActivities(startDate: String, endDate: String): List<GarminActivity> = withContext(Dispatchers.IO) {
        require(startDate <= endDate) { "Периодът за Garmin историята е невалиден." }
        val apiKey = keyStore.load() ?: error("Добави Garmin Personal API ключ, за да синхронизираш историята.")
        val token = exchangeKey(apiKey)
        fetchActivities(token, startDate, endDate)
    }

    suspend fun fetchTrainingHistory(startDate: String, endDate: String): GarminTrainingHistory = withContext(Dispatchers.IO) {
        require(startDate <= endDate) { "Периодът за Garmin историята е невалиден." }
        val apiKey = keyStore.load() ?: error("Добави Garmin Personal API ключ, за да синхронизираш историята.")
        val token = exchangeKey(apiKey)
        val activities = fetchActivities(token, startDate, endDate)
        val sleep = getJson(token,
            "$BASE_URL/sleep-service/sleep/dailySleepSummariesByDate?startDate=$startDate&endDate=$endDate",
            "обобщенията за сън")
        val recovery = mutableMapOf<String, GarminRecoveryDay>()
        fun addSleep(item: JSONObject) {
            val date = item.optString("calendarDate").takeIf(String::isNotBlank) ?: return
            recovery[date] = (recovery[date] ?: GarminRecoveryDay(date)).copy(
                sleepMinutes = item.optNullableDouble("sleepTimeSeconds")?.div(60)?.toInt())
        }
        when (sleep) {
            is JSONArray -> for (i in 0 until sleep.length()) sleep.optJSONObject(i)?.let(::addSleep)
            is JSONObject -> if (sleep.has("calendarDate")) addSleep(sleep) else {
                val list = sleep.optJSONArray("dailySleepDTOs") ?: sleep.optJSONArray("sleepSummaries")
                if (list != null) for (i in 0 until list.length()) list.optJSONObject(i)?.let(::addSleep)
            }
        }
        val hrv = getJson(token, "$BASE_URL/hrv-service/hrv/daily/$startDate/$endDate", "обобщенията за HRV") as? JSONObject
        val hrvDays = hrv?.optJSONArray("hrvSummaries") ?: JSONArray()
        for (i in 0 until hrvDays.length()) {
            val item = hrvDays.optJSONObject(i) ?: continue
            val date = item.optString("calendarDate").takeIf(String::isNotBlank) ?: continue
            recovery[date] = (recovery[date] ?: GarminRecoveryDay(date)).copy(
                hrvLastNightMs = item.optNullableDouble("lastNightAvg")?.toInt(),
                hrvStatus = item.optString("status").takeIf(String::isNotBlank))
        }
        GarminTrainingHistory(activities, recovery.values.sortedBy(GarminRecoveryDay::date))
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
        val activities = fetchActivities(token, date, date)
        return GarminDayData(summary, activities)
    }

    private fun fetchActivities(token: String, startDate: String, endDate: String): List<GarminActivity> {
        val activities = mutableListOf<GarminActivity>()
        var start = 0
        do {
            val page = getJson(
                token,
                "$BASE_URL/activitylist-service/activities/search/activities?start=$start&limit=$ACTIVITY_PAGE_SIZE&sortOrder=asc&startDate=$startDate&endDate=$endDate&excludeChildren=false",
                "активностите за периода",
            ) as? JSONArray ?: JSONArray()
            for (index in 0 until page.length()) {
                val item = page.optJSONObject(index) ?: continue
                activities += item.toGarminActivity()
            }
            start += page.length()
        } while (page.length() == ACTIVITY_PAGE_SIZE && start < MAX_HISTORY_ACTIVITIES)
        return activities
    }

    private fun exchangeKey(apiKey: String): String {
        val request = Request.Builder()
            .url("$BASE_URL/personalapitoken-service/personal-api/personal-api-key/token")
            .header("Accept", "*/*")
            .header("Authorization", "ApiKey $apiKey")
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw apiError(response.code, "ключа")
            val body = response.body?.string().orEmpty()
            val token = parseGarminAccessToken(body)
            if (token == null) {
                throw IllegalStateException("Garmin отговори успешно, но не върна JWT с валиден exp claim. Провери версията на приложението; не изпращай API ключа или token.")
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
        const val MAX_HISTORY_ACTIVITIES = 2970
    }
}
