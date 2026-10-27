# Garmin nutrition test branch — 0.17.0

Branch: `feature/garmin-nutrition-details`. No merge or deployment is performed automatically.

The existing daily Garmin sync now also calls the official Personal API endpoint:

`GET /nutrition-service/food/logs/{date}?includeGoals=true&includeNutrition=true&includeMedia=false`

Reference: [Garmin Personal API Swagger](https://developer.garmin.com/gc-developer-program/personal/swagger-docs/), `DailyNutritionSummaryDTO`, `NutritionGoalsDTO`, `ThinNutritionContentDTO`, `MealDetailsDTO`, `LoggedFoodDTO`, `NutritionContentDTO`.

The compact Nutrition card uses `dailyNutritionContent` and `dailyNutritionGoals.adjustedCalories`. Remaining calories are adjusted goal minus intake; a negative value is labelled over target. Active calories are **not added again**. Missing adjusted goal is shown as unavailable, never silently replaced by the base goal. These numbers describe Garmin's configured goals, not medical advice.

Expand Full nutrition details to inspect all non-null returned fields: totals, base/adjusted macro goals, meals, individual foods/servings, micronutrients, times and metadata. Known nutrient names/units are localized in English and Bulgarian; future/unknown keys are shown by API field name. Per-serving values and serving quantities are presented as received, not multiplied again. Meal/food names stay in their source language. Media is not requested or downloaded.

Only available fields are displayed. A recorded zero remains zero; absent values are shown as — or omitted. Nothing is inferred about MyFitnessPal transfer, subscription eligibility or missing nutrients. The compact detailed card identifies Garmin and the response date; it does not blend Garmin macros with MyFitnessPal CSV. The existing CSV/summary card remains the fallback when no detailed Garmin response is available.

Nutrition is stored in private app preferences alongside the imported date, independently from MyFitnessPal CSV fields. The response size is limited to 2 million characters. Same-day successful responses replace the previous snapshot rather than appending duplicates. Failed nutrition requests preserve same-day cached nutrition with an explicit warning. Empty successful responses clear detailed nutrition for that date. Existing Garmin steps/activity synchronization continues if this optional endpoint is unavailable. Deleting imports or disconnecting Garmin removes its nutrition snapshot as well. API keys and tokens are not included in the nutrition snapshot or logged.

## Phone test

1. Install the artifact from this branch's GitHub Actions run (uses the configured persistent signing key and increasing CI version code).
2. Open MyFitnessPal/Garmin as usual and ensure the relevant food data is visible in Garmin Connect.
3. In bAIr tap Sync today's data.
4. Compare the displayed intake and **adjusted** goal to Garmin for exactly the displayed date.
5. Expand nutrition and check meals, portion quantity, calories, macro/micronutrient units and the base versus adjusted goal fields.
6. Sync again: entries must not duplicate. Restart the app: saved details remain. Check both English and Bulgarian and both themes.
7. If Garmin returns no records or denies this resource, the UI must state that rather than showing fabricated meals. Report this outcome without sharing keys, tokens or your full private food response in public issues.

This implementation has parser tests and build/lint validation. Real-account availability must still be confirmed with the owner's Garmin account; no personal credentials were used during development.
