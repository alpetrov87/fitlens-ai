package com.fitlens.ai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.InetAddress
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit

data class EventLinkImport(
    val draft: EventDraft,
    val dateWasFound: Boolean,
)

/** Reads public HTTPS event pages that expose schema.org Event JSON-LD or basic Open Graph metadata. */
class EventLinkImporter {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun import(url: String, fallbackDate: String): EventLinkImport = withContext(Dispatchers.IO) {
        val normalized = url.trim()
        require(normalized.length <= MAX_URL_LENGTH) { "Линкът е прекалено дълъг." }
        val initial = runCatching { normalized.toHttpUrl() }.getOrElse {
            throw IllegalArgumentException("Постави пълен HTTPS линк към страницата на събитието.")
        }
        require(initial.isHttps) { "За импортиране използвай защитен HTTPS линк." }
        val html = download(initial)
        val fields = extractFields(html)
        val title = fields.title?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Не намерих име на събитие в страницата. Попълни го ръчно.")
        val date = fields.date ?: fallbackDate
        EventLinkImport(
            EventDraft(title = title.take(120), date = date, time = fields.time.orEmpty(),
                sport = fields.sport ?: "other", location = fields.location.orEmpty().take(200), sourceUrl = normalized,
                distanceKm = fields.distanceKm, cutoffTime = fields.cutoffTime.orEmpty().take(80),
                elevationGainMeters = fields.elevationGainMeters),
            dateWasFound = fields.date != null,
        )
    }

    internal fun download(start: HttpUrl, allowedDomain: String? = null): String {
        var current = start
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            require(allowedDomain == null || current.host == allowedDomain || current.host.endsWith(".$allowedDomain")) {
                "Профилът препраща извън сайта на доставчика."
            }
            rejectPrivateHost(current.host)
            val pinnedAddresses = InetAddress.getAllByName(current.host).toList()
            require(pinnedAddresses.isNotEmpty() && pinnedAddresses.all(::isPublicAddress)) {
                "Линкът трябва да води към публичен уеб адрес."
            }
            val requestHost = current.host
            val safeDns = object : Dns {
                override fun lookup(hostname: String): List<InetAddress> = if (hostname.equals(requestHost, true)) pinnedAddresses
                else InetAddress.getAllByName(hostname).toList().also { addresses ->
                    require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) { "Линкът сочи към непубличен адрес." }
                }
            }
            val safeClient = client.newBuilder().dns(safeDns).build()
            val request = Request.Builder().url(current).header("Accept", "text/html,application/xhtml+xml")
                .header("User-Agent", "FitLensAI/0.6 (event link import)").get().build()
            safeClient.newCall(request).execute().use { response ->
                if (response.code in REDIRECT_CODES) {
                    require(redirectCount < MAX_REDIRECTS) { "Страницата препраща през прекалено много адреси." }
                    val location = response.header("Location") ?: error("Страницата върна невалидно пренасочване.")
                    current = current.resolve(location) ?: error("Линкът за пренасочване е невалиден.")
                    require(current.isHttps) { "Страницата препраща към незащитен адрес." }
                    return@use
                }
                if (!response.isSuccessful) throw IllegalStateException("Страницата не се отвори (HTTP ${response.code}).")
                val type = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                require(type.isBlank() || type.contains("text/html") || type.contains("application/xhtml+xml")) {
                    "Линкът трябва да води към уеб страница, не към файл."
                }
                val body = response.body ?: error("Страницата е празна.")
                require((body.contentLength() < 0L || body.contentLength() <= MAX_PAGE_BYTES)) { "Страницата е прекалено голяма за импорт." }
                val bytes = body.source().readByteArray(MAX_PAGE_BYTES + 1L)
                require(bytes.size <= MAX_PAGE_BYTES) { "Страницата е прекалено голяма за импорт." }
                return bytes.toString(Charsets.UTF_8)
            }
        }
        error("Страницата на събитието не може да бъде отворена.")
    }

    private fun rejectPrivateHost(host: String) {
        val normalized = host.lowercase(Locale.ROOT).trimEnd('.')
        require(normalized != "localhost" && !normalized.endsWith(".localhost") && !normalized.endsWith(".local")) {
            "Използвай публичен линк към страницата на събитието."
        }
        val addresses = runCatching { InetAddress.getAllByName(normalized.trim('[', ']')) }.getOrNull()
        require(!addresses.isNullOrEmpty() && addresses.all(::isPublicAddress)) { "Локални мрежови адреси не могат да се използват." }
    }

    private fun isPublicAddress(address: InetAddress): Boolean = !address.isAnyLocalAddress &&
        !address.isLoopbackAddress && !address.isLinkLocalAddress && !address.isSiteLocalAddress && !address.isMulticastAddress

    internal fun extractFields(html: String): Fields {
        var event: JSONObject? = null
        for ((attributes, body) in scriptBlocks(html)) {
            val type = SCRIPT_TYPE_REGEX.find(attributes)?.groupValues?.get(1)
                ?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
            if (type != "application/ld+json") continue
            val parsed = runCatching { JSONTokener(body.trim()).nextValue() }.getOrNull()
            event = findEvent(parsed) ?: event
        }
        val title = event?.optString("name")?.takeIf(String::isNotBlank)
            ?: metaContent(html, "og:title")
            ?: TITLE_REGEX.find(html)?.groupValues?.get(1)?.let(::decodeHtml)
        val startDate = event?.optString("startDate")?.takeIf(String::isNotBlank)
        val dateTime = parseDate(startDate)
        val place = event?.opt("location")
        val location = when (place) {
            is JSONObject -> listOfNotNull(place.optString("name").takeIf(String::isNotBlank),
                place.optJSONObject("address")?.let(::formatAddress)).joinToString(", ").takeIf(String::isNotBlank)
            is String -> place
            else -> null
        } ?: metaContent(html, "event:location")
        val sportText = event?.opt("sport")?.let { value ->
            when (value) {
                is String -> value
                is JSONObject -> value.optString("name")
                is JSONArray -> (0 until value.length()).joinToString(" ") { value.optString(it) }
                else -> ""
            }
        }.orEmpty() + " " + title.orEmpty()
        val pageText = decodeHtml(html.replace(SCRIPT_OR_STYLE_REGEX, " "))
        val properties = event?.let(::collectProperties).orEmpty()
        val distance = metricValue(properties, DISTANCE_LABELS, ::parseDistanceKm)
            ?: DISTANCE_TEXT_REGEX.find(pageText)?.value?.let(::parseDistanceKm)
            ?: title?.let { TITLE_DISTANCE_REGEX.find(decodeHtml(it))?.value?.let(::parseDistanceKm) }
        val elevation = metricValue(properties, ELEVATION_LABELS, ::parseElevationMeters)
            ?: ELEVATION_TEXT_REGEX.find(pageText)?.value?.let(::parseElevationMeters)
        val cutoff = textValue(properties, CUTOFF_LABELS)?.let(::cleanMetricText)
            ?: CUTOFF_TEXT_REGEX.find(pageText)?.groupValues?.get(1)?.let(::cleanMetricText)
        val textStartTime = START_TIME_REGEX.find(pageText)?.groupValues?.get(1)?.let(::normalizeClock)
        return Fields(title = title?.let(::decodeHtml), date = dateTime?.first, parsedTime = dateTime?.second,
            sport = inferSport(sportText), location = location?.let(::decodeHtml), distanceKm = distance,
            cutoffTime = cutoff, elevationGainMeters = elevation, fallbackTime = textStartTime)
    }

    private fun findEvent(value: Any?): JSONObject? = when (value) {
        is JSONArray -> (0 until value.length()).asSequence().mapNotNull { findEvent(value.opt(it)) }.firstOrNull()
        is JSONObject -> {
            val types = value.opt("@type")
            val typeNames = when (types) {
                is String -> listOf(types)
                is JSONArray -> (0 until types.length()).map(types::optString)
                else -> emptyList()
            }
            if (typeNames.any { it.equals("Event", true) || it.endsWith("Event", true) }) value
            else findEvent(value.opt("@graph")) ?: findEvent(value.opt("itemListElement"))
        }
        else -> null
    }

    private fun scriptBlocks(html: String): List<Pair<String, String>> = buildList {
        var cursor = 0
        while (cursor < html.length) {
            val open = html.indexOf("<script", cursor, ignoreCase = true).takeIf { it >= 0 } ?: break
            val bodyStart = html.indexOf('>', open).takeIf { it >= 0 } ?: break
            val close = html.indexOf("</script", bodyStart + 1, ignoreCase = true).takeIf { it >= 0 } ?: break
            add(html.substring(open + 7, bodyStart) to html.substring(bodyStart + 1, close))
            cursor = close + 8
        }
    }

    private fun parseDate(value: String?): Pair<String, String?>? {
        if (value.isNullOrBlank()) return null
        val date = runCatching { OffsetDateTime.parse(value).toLocalDateTime() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(value).toLocalDateTime() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value) }.getOrNull()
        if (date != null) return date.toLocalDate().toString() to date.toLocalTime().takeUnless { it.toString() == "00:00" }?.toString()?.take(5)
        val justDate = runCatching { LocalDate.parse(value.take(10)) }.getOrNull() ?: return null
        return justDate.toString() to null
    }

    private fun formatAddress(address: JSONObject): String = listOf("streetAddress", "addressLocality", "addressRegion", "addressCountry")
        .mapNotNull { address.optString(it).takeIf(String::isNotBlank) }.joinToString(", ")

    private fun metaContent(html: String, property: String): String? = META_TAG_REGEX.findAll(html).firstNotNullOfOrNull { match ->
        val tag = match.value
        val attributes = META_ATTRIBUTE_REGEX.findAll(tag).associate { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[2] }
        attributes["content"]?.takeIf { attributes["property"].equals(property, true) || attributes["name"].equals(property, true) }
            ?.let(::decodeHtml)
    }?.takeIf(String::isNotBlank)

    private fun inferSport(text: String): String? {
        val value = text.lowercase(Locale.ROOT)
        return when {
            listOf("triathlon", "триатлон").any(value::contains) -> "triathlon"
            listOf("swim", "плуван").any(value::contains) -> "swimming"
            listOf("cycl", "bike", "велосип", "колоездач").any(value::contains) -> "cycling"
            listOf("trail", "трейл", "планинско бягане").any(value::contains) -> "trail_running"
            listOf("run", "race", "маратон", "бяган", "маратонец").any(value::contains) -> "running"
            listOf("trail", "планинск", "mountain").any(value::contains) -> "hiking"
            listOf("walk", "ходен").any(value::contains) -> "walking"
            listOf("ski", "ски").any(value::contains) -> "skiing"
            else -> null
        }
    }

    private fun collectProperties(root: JSONObject): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        fun visit(value: Any?) {
            when (value) {
                is JSONObject -> {
                    val propertyName = value.optString("name").takeIf(String::isNotBlank)
                    val propertyValue = value.opt("value")?.toString()?.takeIf(String::isNotBlank)
                    if (propertyName != null && propertyValue != null) result += propertyName to propertyValue
                    value.keys().forEach { key ->
                        val child = value.opt(key)
                        if (child is String || child is Number) result += key to child.toString()
                        else visit(child)
                    }
                }
                is JSONArray -> (0 until value.length()).forEach { visit(value.opt(it)) }
            }
        }
        visit(root)
        return result
    }

    private fun <T> metricValue(properties: List<Pair<String, String>>, labels: List<String>, parser: (String) -> T?): T? =
        properties.firstNotNullOfOrNull { (name, value) ->
            val normalized = name.lowercase(Locale.ROOT).replace(Regex("[^a-zа-я0-9+]"), "")
            value.takeIf { labels.any { label -> normalized.contains(label) } }?.let(parser)
        }

    private fun textValue(properties: List<Pair<String, String>>, labels: List<String>): String? =
        properties.firstNotNullOfOrNull { (name, value) ->
            val normalized = name.lowercase(Locale.ROOT).replace(Regex("[^a-zа-я0-9+]"), "")
            value.takeIf { labels.any { label -> normalized.contains(label) } }
        }

    private fun parseDistanceKm(value: String): Double? {
        val match = NUMBER_UNIT_REGEX.find(value.lowercase(Locale.ROOT)) ?: return null
        val number = match.groupValues[1].replace(" ", "").replace(',', '.').toDoubleOrNull() ?: return null
        return when (match.groupValues[2].lowercase(Locale.ROOT)) {
            "m", "meter", "meters", "метър", "метра" -> number / 1000
            "mi", "mile", "miles", "миля", "мили" -> number * 1.609344
            else -> number
        }.takeIf { it in 0.01..2_000.0 }
    }

    private fun parseElevationMeters(value: String): Int? {
        val match = NUMBER_UNIT_REGEX.find(value.lowercase(Locale.ROOT)) ?: return null
        val number = match.groupValues[1].replace(" ", "").replace(',', '.').toDoubleOrNull() ?: return null
        val meters = if (match.groupValues[2].lowercase(Locale.ROOT) in listOf("ft", "foot", "feet")) number * 0.3048 else number
        return meters.toInt().takeIf { it in 0..100_000 }
    }

    private fun cleanMetricText(value: String): String = decodeHtml(value).trim(' ', ':', '-', '–', '—').take(80)
    private fun normalizeClock(value: String): String? {
        val parts = value.split(':')
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return "%02d:%02d".format(Locale.ROOT, hour, minute).takeIf { hour in 0..23 && minute in 0..59 }
    }

    private fun decodeHtml(value: String): String = value.replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&", true).replace("&quot;", "\"", true).replace("&#39;", "'", true)
        .replace("&lt;", "<", true).replace("&gt;", ">", true).replace("&nbsp;", " ", true)
        .replace(Regex("\\s+"), " ").trim()

    internal data class Fields(
        val title: String?, val date: String?, private val parsedTime: String?, val sport: String?, val location: String?,
        val distanceKm: Double?, val cutoffTime: String?, val elevationGainMeters: Int?, val fallbackTime: String?,
    ) {
        val time: String? get() = parsedTime ?: fallbackTime
    }

    private companion object {
        const val MAX_URL_LENGTH = 500
        const val MAX_PAGE_BYTES = 2_000_000L
        const val MAX_REDIRECTS = 3
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        val SCRIPT_TYPE_REGEX = Regex("type\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val META_TAG_REGEX = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
        val META_ATTRIBUTE_REGEX = Regex("([\\w:-]+)\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
        val TITLE_REGEX = Regex("<title\\b[^>]*>(.*?)</title\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val SCRIPT_OR_STYLE_REGEX = Regex("<(script|style)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val NUMBER_UNIT_REGEX = Regex("([0-9]+(?:[ .,][0-9]+)?)\\s*(km|км|kilometers?|m|meters?|метър|метра|mi|miles?|миля|мили|ft|feet|foot)?", RegexOption.IGNORE_CASE)
        val DISTANCE_TEXT_REGEX = Regex("(?:distance|дистанция|дължина)\\s*[:–—-]?\\s*[0-9]+(?:[.,][0-9]+)?\\s*(?:km|км|kilometers?|mi|miles?|мили?)", RegexOption.IGNORE_CASE)
        val TITLE_DISTANCE_REGEX = Regex("[0-9]+(?:[.,][0-9]+)?\\s*(?:km|км|kilometers?|mi|miles?|мили?)", RegexOption.IGNORE_CASE)
        val ELEVATION_TEXT_REGEX = Regex("(?:elevation\\s*gain|vertical\\s*gain|positive\\s*elevation|денивелация|изкачване|d\\+)\\s*[:–—-]?\\s*[0-9][0-9 .]*(?:m|meters?|метра|ft|feet)?", RegexOption.IGNORE_CASE)
        val CUTOFF_TEXT_REGEX = Regex("(?:cut[ -]?off(?:\\s*time)?|time\\s*limit|control\\s*time|контролно\\s*време|времеви\\s*лимит|лимит)\\s*[:–—-]?\\s*([^|•;,.]{1,40})", RegexOption.IGNORE_CASE)
        val START_TIME_REGEX = Regex("(?:start(?:\\s*time)?|начален\\s*час|час\\s*на\\s*старта|старт)\\s*[:–—-]?\\s*([0-2]?[0-9]:[0-5][0-9])", RegexOption.IGNORE_CASE)
        val DISTANCE_LABELS = listOf("distance", "дистанция", "дължина", "length")
        val ELEVATION_LABELS = listOf("elevationgain", "verticalgain", "positiveelevation", "денивелация", "изкачване", "d+")
        val CUTOFF_LABELS = listOf("cutoff", "timelimit", "controltime", "контролновреме", "времевилимит")
    }
}
