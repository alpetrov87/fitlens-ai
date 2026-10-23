package com.fitlens.ai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the user's Gemini API key locally, encrypted with a dedicated Android Keystore key. */
class GeminiApiKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("gemini_api_key", Context.MODE_PRIVATE)

    init {
        // Clean up the credential from the previous OpenAI-backed app version after the provider switch.
        runCatching {
            context.getSharedPreferences("openai_api_key", Context.MODE_PRIVATE)
                .edit().remove("api_key_aes_gcm_v1").commit()
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                .takeIf { it.containsAlias("fitlens_openai_api_key_v1") }
                ?.deleteEntry("fitlens_openai_api_key_v1")
        }
    }

    @Synchronized fun save(value: String) {
        val key = value.trim()
        require(key.length in 20..512) { "Въведи валиден Gemini API ключ от Google AI Studio." }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, encryptionKey()) }
        val encrypted = cipher.iv + cipher.doFinal(key.toByteArray(StandardCharsets.UTF_8))
        check(prefs.edit().putString(KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) {
            "Gemini ключът не можа да се запази на устройството."
        }
    }

    @Synchronized fun load(): String? {
        val encoded = prefs.getString(KEY, null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > IV_BYTES) { "Локалният Gemini ключ е повреден. Добави го отново." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(TAG_BITS, bytes.copyOfRange(0, IV_BYTES)))
        return String(cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)), StandardCharsets.UTF_8)
    }

    @Synchronized fun clear() {
        check(prefs.edit().remove(KEY).commit()) { "Gemini ключът не можа да бъде изтрит." }
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
    }

    fun hasKey() = prefs.contains(KEY)

    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "fitlens_gemini_api_key_v1"
        const val KEY = "api_key_aes_gcm_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

class GeminiAnalysis(private val keyStore: GeminiApiKeyStore) {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).callTimeout(75, TimeUnit.SECONDS).build()

    suspend fun analyze(activities: List<GarminActivity>, recovery: List<GarminRecoveryDay>, event: CalendarEvent?): String = withContext(Dispatchers.IO) {
        val apiKey = keyStore.load() ?: error("Добави Gemini API ключ в Настройки.")
        require(activities.isNotEmpty()) { "Няма Garmin активности за анализа. Синхронизирай историята от Настройки." }
        val payload = JSONObject().put("activities", JSONArray().apply {
            activities.takeLast(100).forEach { a ->
                put(JSONObject().put("date", a.startTimeLocal.take(10)).put("sport", a.activityTypeKey)
                    .put("name", a.name.take(100)).put("duration_minutes", a.durationSeconds?.div(60))
                    .put("distance_meters", a.distanceMeters).put("average_heart_rate", a.averageHeartRate)
                    .put("max_heart_rate", a.maxHeartRate).put("elevation_gain_meters", a.elevationGainMeters))
            }
        }).put("recovery", JSONArray().apply {
            recovery.forEach { day -> put(JSONObject().put("date", day.date).put("sleep_minutes", day.sleepMinutes)
                .put("overnight_hrv_ms", day.hrvLastNightMs).put("hrv_status", day.hrvStatus)) }
        }).put("upcoming_event", event?.let {
            JSONObject().put("title", it.title.take(100)).put("date", it.date).put("sport", it.sport)
                .put("location", it.location.take(100))
        } ?: JSONObject.NULL)
        val prompt = """You are a cautious endurance-training analysis assistant. Analyze the supplied Garmin summaries for the user's personal review. """ +
            "Analyze training frequency, duration, sport specificity, sleep, HRV, and recovery spacing. " +
            "If an upcoming event is supplied, discuss preparation as a tentative, evidence-limited assessment and practical next steps. " +
            "Use sleep/HRV only when supplied and state if recent measurements are missing. Activity summaries do not contain full workload, training zones, injury, or subjective fatigue context. " +
            "Do not diagnose, prescribe treatment, or claim certainty that the user is ready. Reply in Bulgarian as plain text without Markdown, with a short headline, 2-4 evidence-based observations, " +
            "a cautious event-readiness paragraph, and 2-3 practical suggestions. Never invent metrics.\n\n" + payload
        val requestBody = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("temperature", 0.4).put("maxOutputTokens", 800))
            .toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
            .header("x-goog-api-key", apiKey).header("Accept", "application/json")
            .post(requestBody).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException(when (response.code) {
                400, 403 -> "Google Gemini отказа заявката. Провери API ключа, достъпа до модела и AI Studio настройките."
                429 -> "Безплатната Gemini квота е изчерпана или ограничена. Изчакай обновяването ѝ или провери актуалните квоти."
                else -> "Gemini анализът не успя (HTTP ${response.code}). Опитай отново."
            })
            val candidates = JSONObject(response.body?.string().orEmpty()).optJSONArray("candidates") ?: JSONArray()
            val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            val text = (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optString("text") }
                .filter(String::isNotBlank).joinToString("\n").trim()
            text.takeIf(String::isNotBlank) ?: error("Gemini не върна текстов анализ. Опитай отново.")
        }
    }
}
