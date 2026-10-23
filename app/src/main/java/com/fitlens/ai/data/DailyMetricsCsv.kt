package com.fitlens.ai.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Imports daily summaries and food-log CSV rows chosen by the user via Android's file picker. */
object DailyMetricsCsv {
    private const val MAX_FILE_CHARS = 10_000_000
    private const val MAX_ROWS = 100_000

    fun parse(source: CsvImportSource, contents: String): List<ImportedDailyMetrics> {
        require(contents.length <= MAX_FILE_CHARS) { "CSV файлът е прекалено голям (максимум 10 MB текст)." }
        val delimiter = detectDelimiter(contents)
        val records = records(contents, delimiter)
        require(records.size in 2..MAX_ROWS) { "CSV файлът трябва да съдържа заглавен ред и поне един запис." }
        val headers = records.first().map(::headerKey)
        val dateIndex = headers.firstIndexOf(DATE_HEADERS)
        require(dateIndex >= 0) { "Не откривам колона за дата (например Date или Calendar Date)." }

        val stepsIndex = headers.firstIndexOf(STEP_HEADERS)
        val activeMinutesIndex = headers.firstIndexOf(ACTIVE_MINUTE_HEADERS)
        val distanceKmIndex = headers.firstIndexOf(DISTANCE_KM_HEADERS)
        val distanceMetersIndex = headers.firstIndexOf(DISTANCE_METER_HEADERS)
        val caloriesIndex = headers.firstIndexOf(CALORIE_HEADERS)
        val goalIndex = headers.firstIndexOf(CALORIE_GOAL_HEADERS)
        val proteinIndex = headers.firstIndexOf(PROTEIN_HEADERS)
        val carbsIndex = headers.firstIndexOf(CARBS_HEADERS)
        val fatIndex = headers.firstIndexOf(FAT_HEADERS)

        if (source == CsvImportSource.GARMIN) {
            require(stepsIndex >= 0 || activeMinutesIndex >= 0 || distanceKmIndex >= 0 || distanceMetersIndex >= 0) {
                "Garmin CSV трябва да съдържа Steps, Active Minutes и/или Distance (km или m)."
            }
        } else {
            require(caloriesIndex >= 0 || proteinIndex >= 0 || carbsIndex >= 0 || fatIndex >= 0) {
                "MyFitnessPal CSV трябва да съдържа Calories, Protein, Carbs и/или Fat."
            }
        }

        val grouped = linkedMapOf<LocalDate, ImportedDailyMetrics>()
        for (record in records.drop(1)) {
            if (record.all(String::isBlank)) continue
            val date = record.getOrNull(dateIndex)?.let(::parseDate)
                ?: throw IllegalArgumentException("Записът няма дата в разпознаваем формат (YYYY-MM-DD, M/D/YYYY или DD.MM.YYYY).")
            fun intAt(index: Int): Int? = record.getOrNull(index)?.let(::number)?.toInt()
            fun decimalAt(index: Int): Double? = record.getOrNull(index)?.let(::number)
            val values = if (source == CsvImportSource.GARMIN) {
                ImportedDailyMetrics(
                    date = date.toString(),
                    steps = intAt(stepsIndex),
                    activeMinutes = intAt(activeMinutesIndex),
                    distanceKm = decimalAt(distanceKmIndex) ?: decimalAt(distanceMetersIndex)?.div(1000.0),
                    garminData = true,
                )
            } else {
                ImportedDailyMetrics(
                    date = date.toString(),
                    calories = intAt(caloriesIndex),
                    calorieGoal = intAt(goalIndex),
                    proteinGrams = intAt(proteinIndex),
                    carbsGrams = intAt(carbsIndex),
                    fatGrams = intAt(fatIndex),
                    myFitnessPalData = true,
                )
            }
            if (listOf(
                    values.steps, values.activeMinutes, values.distanceKm, values.calories,
                    values.proteinGrams, values.carbsGrams, values.fatGrams,
                ).all { it == null }
            ) continue
            val previous = grouped[date]
            grouped[date] = if (source == CsvImportSource.MYFITNESSPAL && previous != null) {
                previous.copy(
                    calories = (previous.calories ?: 0) + (values.calories ?: 0),
                    calorieGoal = values.calorieGoal ?: previous.calorieGoal,
                    proteinGrams = (previous.proteinGrams ?: 0) + (values.proteinGrams ?: 0),
                    carbsGrams = (previous.carbsGrams ?: 0) + (values.carbsGrams ?: 0),
                    fatGrams = (previous.fatGrams ?: 0) + (values.fatGrams ?: 0),
                    myFitnessPalData = true,
                )
            } else {
                values
            }
        }
        require(grouped.isNotEmpty()) { "CSV не съдържа валидни записи за импорт." }
        return grouped.values.toList()
    }

    private fun detectDelimiter(text: String): Char {
        val header = text.substringBefore('\n').substringBefore('\r')
        return listOf(',', ';', '\t').maxBy { delimiter -> countOutsideQuotes(header, delimiter) }
    }

    private fun countOutsideQuotes(line: String, delimiter: Char): Int {
        var quoted = false
        var count = 0
        var index = 0
        while (index < line.length) {
            when {
                line[index] == '"' && quoted && line.getOrNull(index + 1) == '"' -> index++
                line[index] == '"' -> quoted = !quoted
                line[index] == delimiter && !quoted -> count++
            }
            index++
        }
        return count
    }

    private fun records(text: String, delimiter: Char): List<List<String>> {
        val output = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = if (text.startsWith('\uFEFF')) 1 else 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' && quoted && text.getOrNull(index + 1) == '"' -> { field.append('"'); index++ }
                char == '"' -> quoted = !quoted
                char == delimiter && !quoted -> { row.add(field.toString().trim()); field.setLength(0) }
                (char == '\n' || char == '\r') && !quoted -> {
                    row.add(field.toString().trim()); field.setLength(0)
                    if (row.any(String::isNotBlank)) output.add(row)
                    row = mutableListOf()
                    if (char == '\r' && text.getOrNull(index + 1) == '\n') index++
                }
                else -> field.append(char)
            }
            index++
        }
        require(!quoted) { "CSV съдържа незатворени кавички." }
        row.add(field.toString().trim())
        if (row.any(String::isNotBlank)) output.add(row)
        return output
    }

    private fun headerKey(value: String) = value.trim().removePrefix("\uFEFF")
        .lowercase(Locale.ROOT).replace(Regex("[^a-z0-9а-я]+"), "")

    private fun List<String>.firstIndexOf(candidates: Set<String>): Int = indexOfFirst { it in candidates }

    private fun number(value: String): Double? {
        val cleaned = value.trim().replace("\u00a0", "").replace(" ", "").replace(',', '.')
        return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    }

    private fun parseDate(value: String): LocalDate? {
        val datePart = value.trim().substringBefore('T').substringBefore(' ').substringBefore(' ')
        for (formatter in DATE_FORMATS) {
            try { return LocalDate.parse(datePart, formatter) } catch (_: DateTimeParseException) { }
        }
        return null
    }

    private val DATE_FORMATS = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("M/d/uuuu", Locale.US),
        DateTimeFormatter.ofPattern("MM/dd/uuuu", Locale.US),
        DateTimeFormatter.ofPattern("d.M.uuuu", Locale.ROOT),
        DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.ROOT),
    )

    private val DATE_HEADERS = setOf("date", "calendardate", "activitydate", "дата")
    private val STEP_HEADERS = setOf("steps", "totalsteps", "stepcount", "крачки")
    private val ACTIVE_MINUTE_HEADERS = setOf("activeminutes", "intensityminutes", "minutesactive", "активниминути")
    private val DISTANCE_KM_HEADERS = setOf("distancekm", "distance", "kilometers", "km", "разстояниекм")
    private val DISTANCE_METER_HEADERS = setOf("distancemeters", "distanceinmeters", "meters", "разстоянием")
    private val CALORIE_HEADERS = setOf("calories", "calorie", "energykcal", "kcal", "калории")
    private val CALORIE_GOAL_HEADERS = setOf("caloriegoal", "goalcalories", "targetcalories", "целкалории")
    private val PROTEIN_HEADERS = setOf("protein", "proteing", "proteingrams", "протеин", "белтъчини")
    private val CARBS_HEADERS = setOf("carbs", "carbohydrates", "carbsg", "carbohydratesg", "въглехидрати")
    private val FAT_HEADERS = setOf("fat", "fatg", "fatgrams", "мазнини")
}
