package com.fitlens.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Private, per-account dated snapshots. Failed reads never replace successful data. */
internal class MfpHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("mfp_history", Context.MODE_PRIVATE)
    fun save(account: String, data: MfpDiaryPreview): Boolean {
        if (!account.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) return false
        LocalDate.parse(data.date)
        val snapshot = mfpSnapshot(data)
        return prefs.edit().putString("active_account", account.lowercase(java.util.Locale.ROOT))
            .putString("${account.lowercase(java.util.Locale.ROOT)}:${data.date}", snapshot.toString()).commit()
    }
    fun clear() { prefs.edit().clear().apply() }
    fun report(today: LocalDate = LocalDate.now(), language: String): String {
        val account = prefs.getString("active_account", null) ?: return ""
        val days = (29 downTo 0).mapNotNull { offset ->
            val day = today.minusDays(offset.toLong()).toString()
            prefs.getString("$account:$day", null)?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
        }
        if (days.isEmpty()) return ""
        return buildString {
            appendLine()
            appendLine(if (language == "bg") "ХРАНЕНЕ — MyFitnessPal: ${days.size}/30 прочетени дни. Липсващите дни не са нулев прием. Стойностите са от дневника, не медицинска оценка." else "NUTRITION — MyFitnessPal: ${days.size}/30 imported days. Missing days do not mean zero intake. These are diary entries, not medical measurements.")
            days.forEach { day ->
                appendLine("${day.getString("date")} | daily totals: ${day.getJSONObject("totals")}")
                val foods = day.getJSONArray("foods")
                val meals = linkedSetOf<String>()
                for (i in 0 until foods.length()) meals.add(foods.getJSONObject(i).optString("meal"))
                appendLine("Meals: ${meals.joinToString(", ").replace(Regex("[\\r\\n]"), " ").take(600)} | foods: ${foods.length()}")
            }
            appendLine(if (language == "bg") "calories=kcal; carbs/protein/fat/sugar/fiber=g; sodium/potassium=mg. Не добавяй повторно калориите от Garmin. Съпостави храненето с натоварването и възстановяването." else "calories=kcal; carbs/protein/fat/sugar/fiber=g; sodium/potassium=mg. Do not double-count Garmin calories. Compare nutrition with training and recovery.")
        }
    }
}

internal fun mfpSnapshot(data: MfpDiaryPreview): JSONObject {
        val rows = JSONArray()
        data.foods.forEach { rows.put(JSONObject().put("meal", it.meal).put("food", it.name).put("nutrients", JSONObject(it.nutrients))) }
        val snapshot = JSONObject().put("date", data.date).put("totals", JSONObject(data.totals)).put("foods", rows)
        return snapshot
}
