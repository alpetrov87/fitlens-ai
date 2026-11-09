package com.fitlens.ai.data

import java.net.URI
import org.json.JSONObject

internal fun utmbIndexValue(score: String, label: String): Int? =
    Regex("(?:^| · )${Regex.escape(label)}: ([1-9][0-9]{0,3})(?= · |$)").find(score)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..1000 }

internal fun utmbRaceUrl(raw: String): String? = runCatching {
    val uri = URI(raw)
    require(uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1,443) &&
        uri.host?.lowercase(java.util.Locale.ROOT) in setOf("utmb.world", "www.utmb.world") &&
        uri.path.matches(Regex("/(?:[a-z]{2}/)?(?:utmb-index/)?races/[^/]+/?")))
    URI(uri.scheme, null, uri.host, uri.port, uri.path, null, null).toString()
}.getOrNull()

/** A human-readable stable row fits the existing profile store and the reviewed chat prompt. */
internal fun utmbRaceRow(row: JSONObject): String? {
    fun text(key: String, max: Int) = (row.opt(key) as? String)?.replace(Regex("[\\r\\n\\t|]+"), " ")?.trim()?.take(max).orEmpty()
    val name = text("title", 160)
    val date = text("date", 30).takeIf { it.matches(Regex("(?:20\\d{2}(?:[-/.]\\d{1,2}[-/.]\\d{1,2})?|\\d{1,2}[/.-]\\d{1,2}[/.-]20\\d{2})")) }
    val url = utmbRaceUrl(text("url", 500)) ?: return null
    if (name.isBlank() || date == null) return null
    val number = (row.opt("score") as? Number)?.toDouble()
    val score = number?.takeIf { it.isFinite() && it == it.toInt().toDouble() && it in 1.0..1000.0 }?.toInt()
    val time = text("time", 20).takeIf { it.matches(Regex("\\d{1,3}:[0-5]\\d(?::[0-5]\\d)?")) }
    val distance = text("distance", 30).takeIf { it.matches(Regex("\\d{1,4}(?:[.,]\\d{1,2})?\\s*(?:km|км)", RegexOption.IGNORE_CASE)) }
    return listOfNotNull("$date · $name", "UTMB Score: ${score ?: "—"}", time, distance, url).joinToString(" | ")
}

internal data class UtmbRaceCardData(val title: String, val date: String?, val score: Int?, val details: String, val url: String?)
internal fun utmbRaceCardData(raw: String): UtmbRaceCardData {
    val parts = raw.split(" | ")
    val first = parts.firstOrNull().orEmpty()
    val date = Regex("^(20\\d{2}(?:[-/.]\\d{1,2}[-/.]\\d{1,2})?|\\d{1,2}[/.-]\\d{1,2}[/.-]20\\d{2}) · ").find(first)?.groupValues?.get(1)
    val score = Regex("UTMB(?:®)?\\s*(?:Score|Index)\\s*[:=]\\s*([1-9][0-9]{0,3})(?![0-9])", RegexOption.IGNORE_CASE)
        .find(raw)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..1000 }
    val url = parts.firstNotNullOfOrNull { utmbRaceUrl(it.trim()) }
    val details = parts.drop(1).filterNot { it.startsWith("UTMB Score:") || utmbRaceUrl(it) != null }.joinToString(" · ")
    return UtmbRaceCardData(if (date != null) first.substringAfter(" · ") else first, date, score, details, url)
}

/** Merge paginated reads by official race URL; an unavailable score never erases a known one. */
internal fun mergeUtmbRaceRows(previous: String, incoming: String?): String? {
    if (incoming.isNullOrBlank()) return previous.takeIf { it.isNotBlank() }
    val rows = linkedMapOf<String, String>()
    fun add(raw: String, replace: Boolean) {
        val row = raw.trim().take(600)
        if (row.isBlank()) return
        val parsed = utmbRaceCardData(row)
        val key = parsed.url ?: row
        val old = rows[key]
        if (old != null && replace && parsed.score == null && utmbRaceCardData(old).score != null) return
        rows.remove(key); rows[key] = row
    }
    previous.lineSequence().take(30).forEach { add(it, false) }
    incoming.orEmpty().lineSequence().take(30).forEach { add(it, true) }
    val kept = mutableListOf<String>()
    rows.values.toList().asReversed().forEach { row ->
        if (kept.size < 30 && kept.sumOf { it.length + 1 } + row.length <= 4000) kept.add(row)
    }
    return kept.joinToString("\n").takeIf { it.isNotBlank() }
}
