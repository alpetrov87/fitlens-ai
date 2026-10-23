package com.fitlens.ai.data

import android.content.Context
import java.net.URI
import java.time.LocalDate

data class RunnerProfile(val provider: String, val url: String = "", val score: String = "", val results: String = "", val updated: String = "")

class RunnerProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("runner_profiles", Context.MODE_PRIVATE)
    fun all(): List<RunnerProfile> = listOf("BTR", "ITRA").map { provider ->
        RunnerProfile(provider, prefs.getString("$provider.url", "").orEmpty(), prefs.getString("$provider.score", "").orEmpty(),
            prefs.getString("$provider.results", "").orEmpty(), prefs.getString("$provider.updated", "").orEmpty())
    }
    fun save(profile: RunnerProfile) {
        require(profile.provider in listOf("BTR", "ITRA"))
        val url = profile.url.trim()
        if (url.isNotBlank()) {
            val uri = runCatching { URI(url) }.getOrNull()
            val host = uri?.host?.lowercase().orEmpty()
            val domain = if (profile.provider == "BTR") "my-btr.com" else "itra.run"
            require(uri?.scheme == "https" && uri.userInfo == null && (host == domain || host.endsWith(".$domain"))) {
                "Въведи HTTPS профилен линк от $domain."
            }
        }
        require(profile.score.length <= 120 && profile.results.length <= 4000) { "Показателят е до 120 знака, резултатите до 4000." }
        check(prefs.edit().putString("${profile.provider}.url", url).putString("${profile.provider}.score", profile.score.trim())
            .putString("${profile.provider}.results", profile.results.trim()).putString("${profile.provider}.updated", LocalDate.now().toString()).commit())
    }
}
