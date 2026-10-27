package com.fitlens.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fitlens.ai.R
import com.fitlens.ai.data.GarminNutrition
import com.fitlens.ai.data.numberOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat

@Composable
internal fun GarminNutritionContent(data: GarminNutrition) {
    var expanded by rememberSaveable(data.date) { mutableStateOf(false) }
    fun number(value: Double?): String = value?.let { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(it) } ?: "—"
    Text("${number(data.consumedCalories)} kcal", style = MaterialTheme.typography.headlineMedium)
    Text("Garmin · ${data.date}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.nutrition_adjusted_goal, number(data.adjustedCalories)))
    val remaining = data.remainingCalories
    Text(stringResource(if (remaining != null && remaining < 0) R.string.nutrition_over else R.string.nutrition_remaining,
        number(remaining?.let { kotlin.math.abs(it) })))
    if (data.adjustedCalories != null && data.adjustedCalories!! > 0 && data.consumedCalories != null) {
        LinearProgressIndicator(progress = { (data.consumedCalories!! / data.adjustedCalories!!).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("protein" to R.string.extra_100, "carbs" to R.string.extra_101, "fat" to R.string.extra_102).forEach { (key, label) ->
            Column { Text("${number(data.content.numberOrNull(key))} g", style = MaterialTheme.typography.titleLarge); Text(stringResource(label), style = MaterialTheme.typography.labelMedium) }
        }
    }
    if (data.adjustedCalories == null) Text(stringResource(R.string.nutrition_goal_missing), style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.nutrition_less else R.string.nutrition_more)) }
    if (expanded) {
        Text(stringResource(R.string.nutrition_explanation), style = MaterialTheme.typography.bodySmall)
        NutritionTree(data.root)
    }
}

/** Show every returned field, including unknown future fields, without downloading external media. */
@Composable
private fun NutritionTree(value: Any?, key: String = "", depth: Int = 0) {
    if (value == null || value == JSONObject.NULL) return
    val labels = mapOf(
        "dailyNutritionContent" to R.string.nutrition_totals, "dailyNutritionGoals" to R.string.nutrition_goals,
        "mealDetails" to R.string.nutrition_meals, "loggedFoodsWithServingSizes" to R.string.nutrition_foods,
        "calories" to R.string.nutrient_calories, "protein" to R.string.extra_100,
        "carbs" to R.string.extra_101, "fat" to R.string.extra_102,
        "adjustedCalories" to R.string.nutrient_adjusted_calories, "adjustedCarbs" to R.string.nutrient_adjusted_carbs,
        "adjustedProtein" to R.string.nutrient_adjusted_protein, "adjustedFat" to R.string.nutrient_adjusted_fat,
        "fiber" to R.string.nutrient_fiber, "sugar" to R.string.nutrient_sugar, "addedSugars" to R.string.nutrient_added_sugars,
        "sodium" to R.string.nutrient_sodium, "potassium" to R.string.nutrient_potassium,
        "calcium" to R.string.nutrient_calcium, "iron" to R.string.nutrient_iron,
        "cholesterol" to R.string.nutrient_cholesterol, "saturatedFat" to R.string.nutrient_saturated,
        "monounsaturatedFat" to R.string.nutrient_mono, "polyunsaturatedFat" to R.string.nutrient_poly,
        "transFat" to R.string.nutrient_trans, "vitaminA" to R.string.nutrient_a,
        "vitaminC" to R.string.nutrient_c, "vitaminD" to R.string.nutrient_d,
        "foodName" to R.string.nutrition_food_name, "mealName" to R.string.nutrition_meal_name,
        "servingQty" to R.string.nutrition_servings, "nutritionContent" to R.string.nutrition_nutrients,
        "nutritionContents" to R.string.nutrition_nutrients, "mealDate" to R.string.nutrition_date,
        "foodMetaData" to R.string.nutrition_food_info, "mealTime" to R.string.nutrition_time,
    )
    val title = labels[key]?.let { stringResource(it) } ?: key.replace(Regex("([a-z])([A-Z])"), "$1 $2")
    when (value) {
        is JSONObject -> {
            if (key.isNotBlank()) { HorizontalDivider(); Text(title, style = MaterialTheme.typography.titleSmall) }
            Column(Modifier.padding(start = if (depth > 0) 8.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                value.keys().forEach { child -> NutritionTree(value.opt(child), child, depth + 1) }
            }
        }
        is JSONArray -> {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (value.length() == 0) Text(stringResource(R.string.nutrition_no_entries), style = MaterialTheme.typography.bodySmall)
            for (i in 0 until value.length()) NutritionTree(value.opt(i), "${i + 1}", depth + 1)
        }
        else -> {
            val unit = when (key) {
                "calories", "adjustedCalories" -> " kcal"
                "protein", "carbs", "fat", "adjustedProtein", "adjustedCarbs", "adjustedFat", "fiber", "sugar", "addedSugars", "saturatedFat", "monounsaturatedFat", "polyunsaturatedFat", "transFat" -> " g"
                "sodium", "potassium", "calcium", "iron", "cholesterol", "vitaminC" -> " mg"
                "vitaminA", "vitaminD" -> " mcg"
                else -> ""
            }
            val text = if (value is Number) NumberFormat.getNumberInstance().apply { maximumFractionDigits = 3 }.format(value) else value.toString()
            Text("$title: $text$unit", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
