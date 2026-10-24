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
import java.util.Locale

/** Public Bulgarian trail and MTB catalogue. Entries are drafts until the user reviews and saves. */
class RaceCalendarCatalogue {
    suspend fun load(url: String = HOME): List<EventDraft> = withContext(Dispatchers.IO) {
        if (url == HOME) return@withContext loadApi()
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

    private fun loadApi(): List<EventDraft> {
        val from = LocalDate.now()
        val to = from.plusYears(2)
        val all = mutableListOf<EventDraft>()
        var page = 1
        var pageCount: Int
        do {
            val url = API_BASE.toHttpUrl().newBuilder()
                .addQueryParameter("fromDate", from.toString())
                .addQueryParameter("toDate", to.toString())
                .addQueryParameter("page", page.toString())
                .addQueryParameter("pageSize", PAGE_SIZE.toString())
                .addQueryParameter("distanceType", "3")
                .build()
            val response = EventLinkImporter().download(url, API_DOMAIN, allowJson = true)
            val root = JSONObject(response)
            all += parseApi(root)
            pageCount = root.optInt("pageCount", 1).coerceIn(1, MAX_PAGES)
            page++
        } while (page <= pageCount)
        require(all.isNotEmpty()) { "В момента не са публикувани предстоящи трейл или MTB състезания." }
        all.distinctBy { listOf(it.title.lowercase(), it.date, it.time, it.sport, it.distanceKm, it.sourceUrl) }
            .sortedWith(compareBy(EventDraft::date, EventDraft::time, EventDraft::title))
    }

    /** Parses the documented data returned to racecalendar.bg's public calendar page. */
    internal fun parseApi(root: JSONObject): List<EventDraft> = buildList {
        val races = root.optJSONArray("results") ?: JSONArray()
        for (raceIndex in 0 until races.length()) {
            val race = races.optJSONObject(raceIndex) ?: continue
            val raceName = race.optString("name").trim().takeIf(String::isNotBlank) ?: continue
            val nameId = race.optString("nameId").trim()
            val sourceUrl = if (nameId.isNotBlank()) "${HOME}run-race/$nameId" else HOME
            val city = race.optString("city").trim()
            val country = race.optString("country").trim()
            val location = listOfNotNull(city.takeIf(String::isNotBlank),
                country.takeIf { it.isNotBlank() && !it.equals("България", true) }).joinToString(", ").take(200)
            val terrain = race.optInt("terrain", 0)
            val raceText = listOf(raceName, race.optString("tags")).joinToString(" ")
            val distances = race.optJSONArray("distances") ?: JSONArray()
            for (distanceIndex in 0 until distances.length()) {
                val distance = distances.optJSONObject(distanceIndex) ?: continue
                val routeName = distance.optString("name").trim()
                val type = distance.optInt("type", 0)
                val combinedText = "$raceText $routeName"
                val sport = when {
                    type == 1 && isTrail(terrain, combinedText) -> "trail_running"
                    type == 2 && isMtb(terrain, combinedText) -> "mountain_biking"
                    else -> continue
                }
                val date = distance.optString("startDate").takeIf(String::isNotBlank)
                    ?: race.optString("startDate").takeIf(String::isNotBlank) ?: continue
                if (runCatching { LocalDate.parse(date) }.isFailure) continue
                val km = distance.optDouble("distance").takeUnless(Double::isNaN)?.takeIf { it in 0.01..2_000.0 }
                val routeLabel = buildList {
                    routeName.takeIf { it.isNotBlank() && !it.equals(raceName, true) }?.let(::add)
                    km?.let { add("${formatDistance(it)} km") }
                }.distinct().joinToString(" · ")
                val title = if (routeLabel.isBlank() || distances.length() == 1) raceName else "$raceName · $routeLabel"
                val elevation = distance.optInt("elevationGain", -1).takeIf { it in 0..100_000 }
                val time = distance.optString("startTime").take(5).takeIf { it.matches(Regex("\\d{2}:\\d{2}")) }.orEmpty()
                val draft = EventDraft(title.take(120), date, time, sport, location, sourceUrl, km,
                    elevationGainMeters = elevation)
                if (runCatching { draft.validate() }.isSuccess) add(draft)
            }
        }
    }

    private fun isTrail(terrain: Int, text: String): Boolean = terrain and TRAIL_TERRAIN != 0 ||
        listOf("trail", "трейл", "планинско бягане", "ultra").any(text.lowercase(Locale.ROOT)::contains)

    private fun isMtb(terrain: Int, text: String): Boolean {
        val normalized = text.lowercase(Locale.ROOT)
        return terrain and TRAIL_TERRAIN != 0 || listOf("mtb", "мтб", "xcm", "xcc", "xco", "xc ",
            "downhill", "dh ", "enduro", "планинско колоездене").any(normalized::contains)
    }

    private fun formatDistance(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')

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
        private const val API_DOMAIN = "api.racecalendar.bg"
        private const val API_BASE = "https://api.racecalendar.bg/v1/api/race/search"
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 10
        private const val TRAIL_TERRAIN = 1
    }
}
