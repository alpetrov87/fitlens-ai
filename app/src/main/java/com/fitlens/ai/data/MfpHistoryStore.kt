package com.fitlens.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Private, per-account dated snapshots. Failed reads never replace successful data. */
internal class MfpHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("mfp_history", Context.MODE_PRIVATE)
    fun account(): String = prefs.getString("active_account", "").orEmpty()
    fun days(account: String = account()): List<MfpDiaryPreview> {
        if (!account.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) return emptyList()
        val prefix = account.lowercase(java.util.Locale.ROOT) + ":"
        return prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith(prefix) || value !is String) null else runCatching {
                readMfpSnapshot(value).takeIf { it.date == key.removePrefix(prefix) }
            }.getOrNull()
        }.sortedByDescending { it.date }
    }
    fun needsReimport(account: String): Boolean {
        val prefix = account.lowercase(java.util.Locale.ROOT) + ":"
        val today = LocalDate.now()
        return prefs.all.any { (key, value) ->
            val date = runCatching { LocalDate.parse(key.removePrefix(prefix)) }.getOrNull()
            key.startsWith(prefix) && value is String && date != null && !date.isBefore(today.minusDays(29)) && !date.isAfter(today) &&
                runCatching { JSONObject(value).optInt("parserVersion", 0) != MFP_DIARY_PARSER_VERSION }.getOrDefault(true)
        }
    }
    fun generation(): Int = prefs.getInt("generation", 0)
    fun save(account: String, data: MfpDiaryPreview, expectedGeneration: Int? = null): Boolean = synchronized(lock) {
        if (expectedGeneration != null && expectedGeneration != generation()) return false
        if (!account.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) return false
        LocalDate.parse(data.date)
        val snapshot = mfpSnapshot(data)
        return prefs.edit().putString("active_account", account.lowercase(java.util.Locale.ROOT))
            .putString("${account.lowercase(java.util.Locale.ROOT)}:${data.date}", snapshot.toString()).commit()
    }
    fun clear() = synchronized(lock) {
        val next = generation() + 1
        prefs.edit().clear().putInt("generation", next).commit()
        Unit
    }
    private companion object { val lock = Any() }
    fun report(today: LocalDate = LocalDate.now(), language: String): String {
        val account = prefs.getString("active_account", null) ?: return ""
        val days = days(account).filter { it.date >= today.minusDays(29).toString() && it.date <= today.toString() }.sortedBy { it.date }
        if (days.isEmpty()) return ""
        return buildString {
            appendLine()
            appendLine(if (language == "bg") "ХРАНЕНЕ — MyFitnessPal: ${days.size}/30 прочетени дни. Липсващите дни не са нулев прием. Стойностите са от дневника, не медицинска оценка." else "NUTRITION — MyFitnessPal: ${days.size}/30 imported days. Missing days do not mean zero intake. These are diary entries, not medical measurements.")
            days.forEach { day ->
                appendLine("${day.date} | daily totals: ${JSONObject(day.totals)}")
                val meals = day.foods.map { it.meal }.distinct()
                appendLine("Meals: ${meals.joinToString(", ").replace(Regex("[\\r\\n]"), " ").take(600)} | foods: ${day.foods.size}")
            }
            appendLine(if (language == "bg") "calories=kcal; carbs/protein/fat/sugar/fiber=g; sodium/potassium=mg. Не добавяй повторно калориите от Garmin. Съпостави храненето с натоварването и възстановяването." else "calories=kcal; carbs/protein/fat/sugar/fiber=g; sodium/potassium=mg. Do not double-count Garmin calories. Compare nutrition with training and recovery.")
        }
    }
}

internal fun mfpSnapshot(data: MfpDiaryPreview): JSONObject {
        val rows = JSONArray()
        data.foods.forEach { rows.put(JSONObject().put("meal", it.meal).put("food", it.name).put("nutrients", JSONObject(it.nutrients))) }
        val snapshot = JSONObject().put("parserVersion", MFP_DIARY_PARSER_VERSION).put("date", data.date).put("totals", JSONObject(data.totals)).put("foods", rows)
        return snapshot
}
