package com.fitlens.ai.data

import java.net.URI
import org.json.JSONObject
import org.jsoup.Jsoup

private val UTMB_RUNNER_PATH = Regex("^/(?:[a-z]{2}/)?runner/([1-9][0-9]{0,11}\\.[A-Za-z0-9._~-]{1,180})/?$")
internal fun utmbSlugFromPath(path: String): String? = UTMB_RUNNER_PATH.matchEntire(path)?.groupValues?.get(1)
internal fun utmbProfileSlug(url: String): String {
    val uri = runCatching { URI(url.trim()) }.getOrNull()
    require(uri?.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
        uri.host?.lowercase(java.util.Locale.ROOT) in setOf("utmb.world", "www.utmb.world") && uri.rawQuery == null && uri.rawFragment == null) {
        "Use an HTTPS UTMB runner profile URL."
    }
    return utmbSlugFromPath(uri!!.rawPath) ?: error("Use the full UTMB runner profile link, including its numeric ID and name.")
}

/** Reads only the exact profile's Next.js props; never arbitrary page numbers or search results. */
internal fun parseUtmbProfile(html: String, expectedSlug: String): RunnerProfileImport {
    require(html.length <= 2_000_000)
    val document = Jsoup.parse(html)
    document.selectFirst("link[rel=canonical]")?.attr("href")?.takeIf { it.isNotBlank() }?.let { canonical ->
        val slug = utmbProfileSlug(canonical)
        require(slug.substringBefore('.') == expectedSlug.substringBefore('.')) { "UTMB profile identity mismatch" }
    }
    val script = document.selectFirst("script#__NEXT_DATA__[type=application/json]")?.data()
        ?: return RunnerProfileImport(null, null, "")
    // Limit JSON nesting before org.json recursion, ignoring braces in quoted strings.
    var depth = 0; var quoted = false; var escape = false
    script.forEach { c ->
        if (quoted) {
            if (escape) escape = false else if (c == '\\') escape = true else if (c == '"') quoted = false
        } else when (c) {
            '"' -> quoted = true
            '{', '[' -> { depth++; require(depth <= 32) }
            '}', ']' -> depth--
        }
    }
    val root = JSONObject(script)
    val query = root.optJSONObject("query")
    listOf("uri", "slug", "runner").forEach { key ->
        query?.optString(key)?.takeIf { it.matches(Regex("[1-9][0-9]*\\..+")) }?.let {
            require(it.substringBefore('.') == expectedSlug.substringBefore('.')) { "UTMB profile identity mismatch" }
        }
    }
    val props = root.optJSONObject("props")?.optJSONObject("pageProps") ?: return RunnerProfileImport(null, null, "")
    val indexes = props.optJSONArray("performanceIndexes") ?: return RunnerProfileImport(null, null, "")
    val values = mutableMapOf<String, Int>()
    val allowed = listOf("general", "20k", "50k", "100k", "100m")
    for (i in 0 until minOf(indexes.length(), 100)) {
        val entry = indexes.optJSONObject(i) ?: continue
        val category = entry.optString("piCategory").lowercase(java.util.Locale.ROOT)
        val raw = entry.opt("index") as? Number ?: continue
        val value = raw.toDouble()
        if (category in allowed && value.isFinite() && value == value.toInt().toDouble() && value in 1.0..1000.0) {
            require(category !in values || values[category] == value.toInt()) { "Conflicting UTMB indexes" }
            values[category] = value.toInt()
        }
    }
    // UTMB's null/zero category index is unavailable, never a real zero rating.
    val score = allowed.joinToString(" · ") { key ->
        "${if (key == "general") "UTMB Index" else key.uppercase(java.util.Locale.ROOT)}: ${values[key]?.toString() ?: "—"}"
    }
    return RunnerProfileImport(score, null, "")
}
