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
                sport = fields.sport ?: "other", location = fields.location.orEmpty().take(200), sourceUrl = normalized),
            dateWasFound = fields.date != null,
        )
    }

    private fun download(start: HttpUrl): String {
        var current = start
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
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
                .header("User-Agent", "FitLensAI/0.4 (event link import)").get().build()
            safeClient.newCall(request).execute().use { response ->
                if (response.code in REDIRECT_CODES) {
                    require(redirectCount < MAX_REDIRECTS) { "Страницата препраща през прекалено много адреси." }
                    val location = response.header("Location") ?: error("Страницата върна невалидно пренасочване.")
                    current = current.resolve(location) ?: error("Линкът за пренасочване е невалиден.")
                    require(current.isHttps) { "Страницата препраща към незащитен адрес." }
                    return@use
                }
                if (!response.isSuccessful) throw IllegalStateException("Страницата на събитието не се отвори (HTTP ${response.code}).")
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

    private fun extractFields(html: String): Fields {
        var event: JSONObject? = null
        val scripts = SCRIPT_REGEX.findAll(html)
        for (script in scripts) {
            val type = script.groupValues[1].substringBefore(';').trim().lowercase(Locale.ROOT)
            if (type != "application/ld+json") continue
            val parsed = runCatching { JSONTokener(script.groupValues[2].trim()).nextValue() }.getOrNull()
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
        }.orEmpty() + " " + (event?.optString("name") ?: "")
        return Fields(title = title?.let(::decodeHtml), date = dateTime?.first, time = dateTime?.second,
            sport = inferSport(sportText), location = location?.let(::decodeHtml))
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
            listOf("run", "race", "маратон", "бяган", "маратонец").any(value::contains) -> "running"
            listOf("trail", "планинск", "mountain").any(value::contains) -> "hiking"
            listOf("walk", "ходен").any(value::contains) -> "walking"
            listOf("ski", "ски").any(value::contains) -> "skiing"
            else -> null
        }
    }

    private fun decodeHtml(value: String): String = value.replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&", true).replace("&quot;", "\"", true).replace("&#39;", "'", true)
        .replace("&lt;", "<", true).replace("&gt;", ">", true).replace("&nbsp;", " ", true)
        .replace(Regex("\\s+"), " ").trim()

    private data class Fields(val title: String?, val date: String?, val time: String?, val sport: String?, val location: String?)

    private companion object {
        const val MAX_URL_LENGTH = 500
        const val MAX_PAGE_BYTES = 2_000_000L
        const val MAX_REDIRECTS = 3
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        val SCRIPT_REGEX = Regex("<script\\b[^>]*type=[\"']([^\"']+)[\"'][^>]*>(.*?)</script\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val META_TAG_REGEX = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
        val META_ATTRIBUTE_REGEX = Regex("([\\w:-]+)\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
        val TITLE_REGEX = Regex("<title\\b[^>]*>(.*?)</title\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    }
}
