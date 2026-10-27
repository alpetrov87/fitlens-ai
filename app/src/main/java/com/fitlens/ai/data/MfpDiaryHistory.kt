package com.fitlens.ai.data

import org.json.JSONObject
import java.time.LocalDate

/** Includes gaps in the rolling window and recent days that may still be edited. */
internal fun mfpSyncDates(end: LocalDate, saved: Set<String>, full: Boolean = false): List<String> =
    (0..29).map { end.minusDays(it.toLong()).toString() }
        .filterIndexed { offset, date -> full || offset < 3 || date !in saved }

internal fun readMfpSnapshot(raw: String): MfpDiaryPreview {
    require(raw.length <= 1_000_000)
    val json = JSONObject(raw)
    require(json.optInt("parserVersion", 0) == MFP_DIARY_PARSER_VERSION) { "Diary requires re-import" }
    val date = json.getString("date")
    require(LocalDate.parse(date).toString() == date)
    fun nutrients(values: JSONObject): Map<String, Double> = values.keys().asSequence().mapNotNull { key ->
        val number = values.optDouble(key, Double.NaN)
        if (number.isFinite() && number >= 0) key to number else null
    }.toMap()
    val foods = json.getJSONArray("foods")
    return MfpDiaryPreview(date, (0 until foods.length()).map { index ->
        val food = foods.getJSONObject(index)
        MfpFoodPreview(food.getString("meal"), food.getString("food"), nutrients(food.getJSONObject("nutrients")))
    }, nutrients(json.getJSONObject("totals")))
}
