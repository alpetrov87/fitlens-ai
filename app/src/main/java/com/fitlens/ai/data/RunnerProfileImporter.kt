package com.fitlens.ai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class RunnerProfileImport(val score: String?, val results: String?, val message: String)

/** Imports publicly visible data only; never supplies login cookies or probes private APIs. */
class RunnerProfileImporter {
    suspend fun import(provider: String, url: String): RunnerProfileImport = withContext(Dispatchers.IO) {
        require(provider in listOf("BTR", "ITRA"))
        val domain = if (provider == "ITRA") "itra.run" else "my-btr.com"
        val page = runCatching { url.trim().toHttpUrl() }.getOrElse { error("Въведи пълен HTTPS профилен линк.") }
        require(page.isHttps && page.username.isEmpty() && page.password.isEmpty() && url.length <= 500 &&
            (page.host == domain || page.host.endsWith(".$domain"))) { "Използвай HTTPS профилен линк от $domain." }
        val downloader = EventLinkImporter()
        val btrId = if (provider == "BTR") BTR_PROFILE_PATH.matchEntire(page.encodedPath)?.groupValues?.get(1) else null
        var extracted = if (btrId != null) {
            val apiBase = "https://api.my-btr.com/api/runner/$btrId"
            val profileJson = downloader.download(apiBase.toHttpUrl(), domain, allowJson = true)
            val resultsJson = runCatching {
                downloader.download("$apiBase/results?page=1&itemsPerPage=30".toHttpUrl(), domain, allowJson = true)
            }.getOrNull()
            parseBtrApi(profileJson, resultsJson)
        } else {
            parse(provider, downloader.download(page, domain))
        }
        // The public ITRA example uses a separate race-results page for the same runner id.
        if (provider == "ITRA" && extracted.results == null) {
            val id = Regex("/RunnerSpace/(?:(?:About|RaceResults)/)?(\\d+)(?:/|$)", RegexOption.IGNORE_CASE).find(page.encodedPath)?.groupValues?.get(1)
            if (id != null) {
                val result = runCatching {
                    parse(provider, downloader.download("https://itra.run/RunnerSpace/RaceResults/$id".toHttpUrl(), domain))
                }.getOrNull()
                if (result?.results != null) extracted = extracted.copy(results = result.results)
            }
        }
        require(extracted.score != null || extracted.results != null) {
            "Не намерих публични показатели или резултати. Страницата може да изисква вход, JavaScript или различен профилен линк. Запазените стойности не са променени."
        }
        extracted.copy(message = "Намерени: ${listOfNotNull(extracted.score?.let { "показател" }, extracted.results?.let { "резултати" }).joinToString(", ")}. Провери и запази. Липсващите полета не се променят.")
    }

    internal fun parseBtrApi(profileJson: String, resultsJson: String?): RunnerProfileImport {
        val root = JSONObject(profileJson)
        val profile = root.optJSONObject("data") ?: root
        val rating = profile.optIntOrNull("rating")
        val place = profile.optIntOrNull("place")
        val total = profile.optIntOrNull("total")
        val group = when (profile.optString("gender").lowercase()) {
            "male", "m" -> "мъже"
            "female", "f" -> "жени"
            else -> null
        }
        val scoreParts = buildList {
            rating?.let { add("BTR рейтинг: $it") }
            place?.let {
                val denominator = total?.let { count -> " / $count" }.orEmpty()
                val suffix = group?.let { value -> " ($value)" }.orEmpty()
                add("Място: $it$denominator$suffix")
            }
        }
        val rows = mutableListOf<String>()
        resultsJson?.let { json ->
            val resultRoot = runCatching { JSONObject(json) }.getOrNull()
            val data = resultRoot?.optJSONObject("data")
            val items = data?.optJSONArray("data") ?: resultRoot?.optJSONArray("data")
            if (items != null) for (index in 0 until minOf(items.length(), 30)) {
                val item = items.optJSONObject(index) ?: continue
                val title = listOf(item.optString("race_name"), item.optString("distance_name"))
                    .filter(String::isNotBlank).joinToString(" · ")
                if (title.isBlank()) continue
                val details = listOfNotNull(
                    item.optString("place").takeIf(String::isNotBlank)?.let { "място $it" },
                    item.optString("time").takeIf(String::isNotBlank),
                    item.optIntOrNull("rating")?.let { "рейтинг $it" },
                )
                val prefix = item.optString("date").takeIf(String::isNotBlank)?.let { "$it · " }.orEmpty()
                rows += "$prefix$title${details.takeIf(List<String>::isNotEmpty)?.joinToString(" | ", prefix = " | ").orEmpty()}"
            }
        }
        return RunnerProfileImport(scoreParts.joinToString(" · ").takeIf(String::isNotBlank),
            rows.distinct().joinToString("\n").take(4000).takeIf(String::isNotBlank), "")
    }

    internal fun parse(provider: String, html: String): RunnerProfileImport {
        val doc = Jsoup.parse(html)
        doc.select("script, style, nav, footer, header").remove()
        val text = doc.body().text()
        val scoreLabels = if (provider == "ITRA") "performance\\s*index|ITRA\\s*(?:index|PI)" else
            "BTR\\s*(?:index|rating|score|points|индекс|рейтинг|точки)|индекс|рейтинг"
        var score = Regex("(?:$scoreLabels)\\s*[:|–—-]?\\s*(\\d{1,4}(?:[.,]\\d{1,2})?)(?![\\d.,])", RegexOption.IGNORE_CASE)
            .find(text)?.let { "${if (provider == "ITRA") "Performance Index" else "BTR"}: ${it.groupValues[1]}" }
        // ITRA places the index in the runner summary's small text; use a scoped selector, not arbitrary numbers.
        if (score == null && provider == "ITRA" && doc.selectFirst(".runner-name h1") != null) {
            score = doc.select(".col-lg-4.d-flex.align-items-start span small").mapNotNull { element ->
                element.text().substringAfterLast('|').trim().takeIf { it.matches(Regex("\\d{1,4}")) }
            }.firstOrNull()?.let { "Performance Index: $it" }
        }
        val rows = mutableListOf<String>()
        doc.select("table").forEach { table ->
            val header = table.select("thead tr").text().ifBlank { table.selectFirst("tr")?.text().orEmpty() }.lowercase()
            val itraResults = provider == "ITRA" && table.id() == "runner-results-table"
            val isResults = itraResults ||
                (listOf("race", "състезание", "event", "събитие").any(header::contains) &&
                    listOf("time", "време", "result", "резултат", "rank", "класиране").any(header::contains))
            if (isResults) {
                table.select("tr").filter { it.select("td").size >= 3 }.take(30).forEach { row ->
                    val cells = row.select("td").map { it.text().trim().take(180) }
                    if (cells.any { it.contains(Regex("\\d")) }) rows += cells.joinToString(" | ")
                }
            }
        }
        // BTR/other modern pages may put the public profile in JSON-LD or framework page state.
        val original = Jsoup.parse(html)
        fun visit(value: Any?, depth: Int = 0) {
            if (depth > 16) return
            when (value) {
                is JSONArray -> (0 until minOf(value.length(), 100)).forEach { visit(value.opt(it), depth + 1) }
                is JSONObject -> {
                    val keys = value.keys().asSequence().toList()
                    if (score == null) {
                        val allowed = if (provider == "ITRA") listOf("performanceindex", "performance_index", "itraindex") else
                            listOf("btrindex", "btr_index", "btrrating", "btr_rating", "btrscore", "btr_score")
                        keys.firstOrNull { it.lowercase() in allowed }?.let { key ->
                            val number = value.optString(key).toDoubleOrNull()
                            if (number != null && number.isFinite() && number in 0.0..9999.0) score = "$key: ${value.optString(key)}"
                        }
                    }
                    val name = listOf("raceName", "race_name", "eventName", "event_name").firstNotNullOfOrNull { value.optString(it).takeIf(String::isNotBlank) }
                    val time = listOf("finishTime", "finish_time", "resultTime", "result_time").firstNotNullOfOrNull { value.optString(it).takeIf(String::isNotBlank) }
                    if (name != null && time != null && rows.size < 30) rows += "$name | $time"
                    keys.forEach { visit(value.opt(it), depth + 1) }
                }
            }
        }
        original.select("script[type=application/ld+json], script[type=application/json]").take(10).forEach {
            runCatching { visit(JSONTokener(it.data()).nextValue()) }
        }
        val resultText = rows.distinct().joinToString("\n").take(4000).takeIf(String::isNotBlank)
        return RunnerProfileImport(score, resultText, "")
    }

    private fun JSONObject.optIntOrNull(key: String): Int? = opt(key)?.toString()?.toIntOrNull()

    private companion object {
        val BTR_PROFILE_PATH = Regex("^/runners/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/?$")
    }
}
