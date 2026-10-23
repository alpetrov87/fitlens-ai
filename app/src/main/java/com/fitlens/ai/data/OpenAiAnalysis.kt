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

/** The OpenAI API key is kept locally, encrypted with a separate Android Keystore key. */
class OpenAiApiKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("openai_api_key", Context.MODE_PRIVATE)

    @Synchronized fun save(value: String) {
        val key = value.trim()
        require(key.startsWith("sk-") && key.length in 20..4096) { "Въведи валиден OpenAI API ключ." }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, encryptionKey()) }
        val encrypted = cipher.iv + cipher.doFinal(key.toByteArray(StandardCharsets.UTF_8))
        check(prefs.edit().putString(KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) {
            "OpenAI ключът не можа да се запази на устройството."
        }
    }

    @Synchronized fun load(): String? {
        val encoded = prefs.getString(KEY, null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > IV_BYTES) { "Локалният OpenAI ключ е повреден. Добави го отново." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(TAG_BITS, bytes.copyOfRange(0, IV_BYTES)))
        return String(cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)), StandardCharsets.UTF_8)
    }

    @Synchronized fun clear() {
        check(prefs.edit().remove(KEY).commit()) { "OpenAI ключът не можа да бъде изтрит." }
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
        const val ALIAS = "fitlens_openai_api_key_v1"
        const val KEY = "api_key_aes_gcm_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

class OpenAiAnalysis(private val keyStore: OpenAiApiKeyStore) {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).callTimeout(75, TimeUnit.SECONDS).build()

    suspend fun analyze(activities: List<GarminActivity>, recovery: List<GarminRecoveryDay>, event: CalendarEvent?): String = withContext(Dispatchers.IO) {
        val apiKey = keyStore.load() ?: error("Добави OpenAI API ключ в Настройки.")
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
        val prompt = """You are a cautious endurance-training analysis assistant. The user has shared Garmin summaries for their own review. """ +
            "Analyze trends in training frequency, duration, sport specificity, sleep, HRV, and recovery spacing from the supplied summaries. " +
            "If an event is supplied, discuss preparation as a tentative, evidence-limited assessment and practical next steps. " +
            "Use sleep/HRV only when supplied and explicitly state when recent nights or HRV values are missing. Activity summaries do not contain full workload, training zones, injury, or subjective fatigue context. " +
            "Do not diagnose, prescribe treatment, or claim certainty that the user is ready. Reply in Bulgarian as plain text without Markdown, with a short headline, " +
            "2-4 evidence-based observations, one cautious event-readiness paragraph, and 2-3 practical suggestions. Never invent metrics.\n\n" + payload
        val requestBody = JSONObject().put("model", "gpt-4.1-mini").put("input", prompt)
            .put("max_output_tokens", 800).toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("https://api.openai.com/v1/responses")
            .header("Authorization", "Bearer $apiKey").header("Accept", "application/json")
            .post(requestBody).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(when (response.code) {
                    401 -> "OpenAI отказа ключа (401). Провери API ключа в Настройки."
                    429 -> "OpenAI ограничи заявките или няма наличен API кредит (429). Провери API billing настройките."
                    else -> "OpenAI анализът не успя (HTTP ${response.code}). Опитай отново."
                })
            }
            val result = JSONObject(response.body?.string().orEmpty())
            val output = result.optJSONArray("output") ?: JSONArray()
            val text = buildString {
                for (i in 0 until output.length()) {
                    val content = output.optJSONObject(i)?.optJSONArray("content") ?: continue
                    for (j in 0 until content.length()) content.optJSONObject(j)?.optString("text")
                        ?.takeIf(String::isNotBlank)?.let { append(it).append("\n") }
                }
            }.trim()
            text.takeIf(String::isNotBlank) ?: error("OpenAI не върна текстов анализ. Опитай отново.")
        }
    }
}
