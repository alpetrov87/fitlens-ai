package com.fitlens.ai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.time.LocalDate

/** Public racecalendar.bg catalogue. Entries are drafts until the user reviews and saves. */
class RaceCalendarCatalogue {
    suspend fun load(url: String = HOME): List<EventDraft> = withContext(Dispatchers.IO) {
        val page = url.trim().toHttpUrlOrNull()
        require(page != null && page.isHttps && page.username.isEmpty() && page.password.isEmpty() &&
            (page.host == DOMAIN || page.host.endsWith(".$DOMAIN")) && url.length <= 500) {
            "Използвай HTTPS страница от racecalendar.bg."
        }
        val events = parse(EventLinkImporter().download(page, DOMAIN), page.toString())
        require(events.isNotEmpty()) {
            "Не намерих състезания с публикувана дата в тази страница. Опитай конкретна страница от календара или добави състезанието чрез линк. Съдържание, зареждано само с JavaScript, може да не се прочете."
        }
        events
    }

    internal fun parse(html: String, pageUrl: String = HOME): List<EventDraft> {
        val doc = Jsoup.parse(html, pageUrl)
        val importer = EventLinkImporter()
        val found = mutableListOf<EventDraft>()
        fun append(event: JSONObject, extraHtml: String = "", fallbackUrl: String = pageUrl) {
            val fields = importer.extractFields("<script type=\"application/ld+json\">$event</script>$extraHtml")
            val title = fields.title?.takeIf(String::isNotBlank) ?: return
            val date = fields.date ?: return
            val rawUrl = when (val value = event.opt("url")) {
                is String -> value
                is JSONObject -> value.optString("@id")
                else -> ""
            }
            val url = safeUrl(rawUrl, pageUrl) ?: safeUrl(fallbackUrl, pageUrl) ?: return
            val draft = EventDraft(title.take(120), date, fields.time.orEmpty(), fields.sport ?: "running",
                fields.location.orEmpty().take(200), url, fields.distanceKm, fields.cutoffTime.orEmpty().take(80), fields.elevationGainMeters)
            if (runCatching { draft.validate() }.isSuccess) found += draft
        }
        fun visit(value: Any?, depth: Int = 0) {
            if (depth > 20 || found.size >= 200) return
            when (value) {
                is JSONArray -> (0 until minOf(value.length(), 300)).forEach { visit(value.opt(it), depth + 1) }
                is JSONObject -> {
                    val type = value.opt("@type")
                    val names = if (type is JSONArray) (0 until type.length()).map { type.optString(it) } else listOf(type?.toString().orEmpty())
                    if (names.any { it.substringAfterLast('/').endsWith("Event", true) }) append(value)
                    else value.keys().forEach { visit(value.opt(it), depth + 1) }
                }
            }
        }
        doc.select("script[type=application/ld+json]").forEach { script ->
            runCatching { visit(JSONTokener(script.data()).nextValue()) }
        }
        // Common HTML event cards and rows, scoped so a global page date is never assigned to every link.
        doc.select("article, tr, [itemtype*=Event], .event, .event-card, .race-card, .tribe-events-calendar-list__event, .mec-event-article").take(300).forEach { card ->
            val anchor = card.selectFirst("h1 a[href], h2 a[href], h3 a[href], [itemprop=url][href], a.tribe-events-calendar-list__event-title-link[href]")
                ?: card.selectFirst("a[href]") ?: return@forEach
            val title = card.selectFirst("h1, h2, h3, [itemprop=name]")?.text()?.takeIf(String::isNotBlank)
                ?: anchor.text().takeIf(String::isNotBlank) ?: return@forEach
            val dateElement = card.selectFirst("[itemprop=startDate], time[datetime], [data-start-date]")
            val dateText = dateElement?.let { element -> listOf("content", "datetime", "data-start-date").firstNotNullOfOrNull {
                element.attr(it).takeIf(String::isNotBlank)
            } } ?: dateInText(card.text()) ?: return@forEach
            val event = JSONObject().put("@type", "SportsEvent").put("name", title).put("startDate", dateText)
                .put("sport", card.text().take(1000)).put("url", anchor.absUrl("href"))
            append(event, card.outerHtml(), anchor.absUrl("href"))
        }
        return found.distinctBy { Triple(it.title.lowercase(), it.date, it.sourceUrl) }.sortedBy { it.date }
    }

    private fun safeUrl(value: String, base: String): String? {
        val parsed = base.toHttpUrl().resolve(value)?.takeIf {
            value.isNotBlank() && it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.toString().length <= 500
        }
        return parsed?.toString()
    }

    private fun dateInText(text: String): String? {
        Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b").find(text)?.value?.let { candidate ->
            runCatching { LocalDate.parse(candidate) }.getOrNull()?.let { return it.toString() }
        }
        Regex("\\b(\\d{1,2})[./](\\d{1,2})[./](\\d{4})\\b").find(text)?.let {
            return runCatching { LocalDate.of(it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt()).toString() }.getOrNull()
        }
        val months = listOf("януари", "февруари", "март", "април", "май", "юни", "юли", "август", "септември", "октомври", "ноември", "декември")
        val match = Regex("\\b(\\d{1,2})\\s+(${months.joinToString("|")})\\s+(\\d{4})\\b", RegexOption.IGNORE_CASE).find(text) ?: return null
        return runCatching { LocalDate.of(match.groupValues[3].toInt(), months.indexOf(match.groupValues[2].lowercase()) + 1, match.groupValues[1].toInt()).toString() }.getOrNull()
    }

    companion object {
        const val HOME = "https://racecalendar.bg/"
        private const val DOMAIN = "racecalendar.bg"
    }
}
