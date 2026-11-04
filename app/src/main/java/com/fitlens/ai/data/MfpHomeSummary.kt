package com.fitlens.ai.data

/** Only a verified snapshot for the requested date may supply Home numbers. */
internal fun mfpHomeDay(days: List<MfpDiaryPreview>, today: String): MfpDiaryPreview? = days.firstOrNull { it.date == today }

/** Use explicit MFP totals; sum foods only when that field is absent, never both. */
internal fun mfpHomeNutrient(day: MfpDiaryPreview, key: String): Double? =
    day.totals[key] ?: day.foods.mapNotNull { it.nutrients[key] }.takeIf { it.isNotEmpty() }?.sum()
