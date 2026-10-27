package com.fitlens.ai.data

import org.jsoup.Jsoup
import java.net.URI
import java.time.LocalDate

internal data class MfpFoodPreview(val meal: String, val name: String, val nutrients: Map<String, Double>)
internal data class MfpDiaryPreview(val date: String, val foods: List<MfpFoodPreview>, val totals: Map<String, Double>)

internal fun isMfpPage(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
        (uri.host == "myfitnesspal.com" || uri.host?.endsWith(".myfitnesspal.com") == true)
}.getOrDefault(false)

internal fun isMfpDiaryPage(url: String): Boolean = runCatching {
    isMfpPage(url) && URI(url).path.matches(Regex("/food/diary(?:/[A-Za-z0-9_.-]+)?/?"))
}.getOrDefault(false)

internal fun isMfpDiaryForDate(url: String, date: String, documentDate: String? = null): Boolean = runCatching {
    LocalDate.parse(date)
    val uri = URI(url)
    val dates = uri.rawQuery.orEmpty().split('&').filter { it.substringBefore('=') == "date" }
    val verifiedDocument = documentDate?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
    isMfpDiaryPage(url) && (verifiedDocument == null || verifiedDocument == date) &&
        if (dates.isEmpty()) verifiedDocument == date else dates.size == 1 && dates.single() == "date=$date"
}.getOrDefault(false)

internal class MfpDiaryDateMismatch : IllegalArgumentException("Diary date does not match selection")

/** Reads only the diary table supplied by an explicit user action; never reads login forms/cookies. */
internal fun parseMfpDiary(html: String, url: String, date: String): MfpDiaryPreview {
    require(html.length <= 250_000) { "Diary table too large" }
    val table = Jsoup.parse(html).selectFirst("table")
        ?: error("Unsupported diary layout")
    val documentDate = table.attr("data-diary-date").takeIf { it.isNotBlank() }
    if (!isMfpDiaryForDate(url, date, documentDate)) throw MfpDiaryDateMismatch()
    fun key(text: String): String? = when (text.lowercase(java.util.Locale.ROOT).trim()
        .replace(Regex("[\\s\\u00a0]+"), " ")
        .replace(Regex("\\s*\\(?(?:kcal|cal|mg|g|grams|ккал|мг|г)\\)?$"), "").trim()) {
        "calories", "калории" -> "calories"
        "carbs", "carbohydrates", "въглехидрати" -> "carbs"
        "protein", "протеин", "белтъчини" -> "protein"
        "fat", "мазнини" -> "fat"
        "sugar", "захар", "захари" -> "sugar"
        "fiber", "fibre", "фибри" -> "fiber"
        "sodium", "натрий" -> "sodium"
        "potass.", "potassium", "калий" -> "potassium"
        else -> null
    }
    fun number(text: String): Double? {
        val raw = text.trim().replace("\u00a0", "").replace(" ", "")
        if (!raw.matches(Regex("[+-]?[0-9][0-9,.]*"))) return null
        val normalized = if (raw.matches(Regex("[+-]?\\d{1,3}(,\\d{3})+(\\.\\d+)?"))) raw.replace(",", "") else raw.replace(',', '.')
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    }
    val foods = mutableListOf<MfpFoodPreview>()
    var meal = ""
    // Unfilled meal sections may only have a title. Reuse the actual nutrient columns,
    // including the repeated footer header, rather than overwriting them with nulls.
    var headers: List<String?> = table.select("tr").map { row ->
        row.children().filter { it.tagName() in listOf("td", "th") }.map { key(it.text()) }
    }.firstOrNull { columns -> columns.drop(1).count { it != null } >= 2 }.orEmpty()
    var totals: Map<String, Double> = emptyMap()
    for (row in table.select("tr")) {
        val cells = row.children().filter { it.tagName() == "td" || it.tagName() == "th" }
        val headerKeys = cells.map { key(it.text()) }
        val mealHeader = row.hasClass("meal_header") || row.hasClass("meal-header") || headerKeys.drop(1).count { it != null } >= 2
        val firstText = cells.firstOrNull()?.text()?.lowercase(java.util.Locale.ROOT).orEmpty()
        if (mealHeader) {
            meal = cells.firstOrNull()?.text().orEmpty()
            if (headerKeys.drop(1).count { it != null } >= 2) headers = headerKeys
        } else if (row.hasClass("total") || firstText in listOf("totals", "total", "общо", "your daily goal", "remaining")) {
            if (!row.hasClass("alt") && !row.hasClass("remaining") && firstText !in listOf("your daily goal", "remaining") && totals.isEmpty()) {
                totals = cells.mapIndexedNotNull { i, cell -> headers.getOrNull(i)?.let { k -> number(cell.text())?.let { k to it } } }.toMap()
            }
            meal = ""
        } else if (row.hasClass("bottom")) {
            meal = ""
        } else if (!row.hasClass("bottom") && !row.hasClass("meal_total") && !row.hasClass("meal-total") && cells.size > 1 && meal.isNotBlank()) {
            val nutrients = cells.mapIndexedNotNull { i, cell -> headers.getOrNull(i)?.let { k -> number(cell.text())?.let { k to it } } }.toMap()
            if (nutrients.isNotEmpty()) foods += MfpFoodPreview(meal.take(120), cells.first().text().take(300), nutrients)
        }
    }
    require(headers.any { it != null } && (totals.isNotEmpty() || foods.isNotEmpty())) { "No recognized nutrition fields" }
    return MfpDiaryPreview(date, foods, totals)
}
