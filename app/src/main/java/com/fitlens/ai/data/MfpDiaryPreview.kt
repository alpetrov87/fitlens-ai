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

internal fun isMfpDiaryForDate(url: String, date: String): Boolean = runCatching {
    LocalDate.parse(date)
    val uri = URI(url)
    isMfpPage(url) && uri.path.matches(Regex("/food/diary(?:/[A-Za-z0-9_.-]+)?/?")) &&
        uri.rawQuery?.split('&')?.count { it == "date=$date" } == 1 &&
        uri.rawQuery?.split('&')?.count { it.substringBefore('=') == "date" } == 1
}.getOrDefault(false)

/** Reads only the diary table supplied by an explicit user action; never reads login forms/cookies. */
internal fun parseMfpDiary(html: String, url: String, date: String): MfpDiaryPreview {
    require(isMfpDiaryForDate(url, date)) { "Invalid diary date or origin" }
    require(html.length <= 250_000) { "Diary table too large" }
    val table = Jsoup.parse(html).selectFirst("table")
        ?: error("Unsupported diary layout")
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
    var headers: List<String?> = emptyList()
    var totals: Map<String, Double> = emptyMap()
    for (row in table.select("tr")) {
        val cells = row.children().filter { it.tagName() == "td" || it.tagName() == "th" }
        val headerKeys = cells.map { key(it.text()) }
        val mealHeader = row.hasClass("meal_header") || row.hasClass("meal-header") || headerKeys.drop(1).count { it != null } >= 2
        val firstText = cells.firstOrNull()?.text()?.lowercase(java.util.Locale.ROOT).orEmpty()
        if (mealHeader) {
            meal = cells.firstOrNull()?.text().orEmpty()
            headers = headerKeys
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
