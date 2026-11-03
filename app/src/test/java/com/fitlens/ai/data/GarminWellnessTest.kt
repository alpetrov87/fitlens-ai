package com.fitlens.ai.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GarminWellnessTest {
    @Test fun batteryUsesLatestTimestampNotArrayOrderAndRejectsSentinels() {
        val result = bodyBatteryLevels(JSONObject("""{"bodyBatteryValuesArray":[[300,55],[100,10],[400,-1],[200,78],[500,null]]}"""))
        assertEquals(mapOf("batteryMin" to 10.0, "batteryMax" to 78.0, "batteryLast" to 55.0), result)
        assertTrue(bodyBatteryLevels(JSONObject()).isEmpty())
    }
    @Test fun oldStorageWithoutWellnessIsCompatibleAndUnknownFieldsAreRejected() {
        assertTrue(readWellness(null).isEmpty())
        val original = mapOf("sleepScore" to 67.0, "hrvWeekly" to 48.0, "batteryImpact" to -3.0)
        assertEquals(original, readWellness(JSONObject(JSONObject(original).toString())))
        assertTrue(readWellness(JSONObject("""{"token":123,"fitnessAge":"37.7"}""")).isEmpty())
    }
    @Test fun bilingualPromptIncludesDatedMetricsAndClarifiesLoad() {
        val recovery = listOf(GarminRecoveryDay("2026-11-03", 373, 64,
            wellness = mapOf("sleepScore" to 67.0, "batteryMax" to 78.0)))
        for (lang in listOf("bg", "en")) {
            val report = ChatGptShareReport.build(emptyList(), recovery, null, language = lang)
            assertTrue(report.contains("2026-11-03"))
            assertTrue(report.contains("67.0 /100"))
            assertTrue(report.contains("78.0 /100"))
            assertTrue(report.contains("VO₂ max/Training Readiness"))
        }
    }
    @Test fun activityLoadRemainsPerActivityInPrompt() {
        val activity = GarminActivity(1, "Run", "running", "2026-11-03T10:00", null, null, null, null, null, null, null,
            wellness = mapOf("trainingLoad" to 150.4, "aerobic" to 3.9))
        val report = ChatGptShareReport.build(listOf(activity), emptyList(), null, language = "en")
        assertTrue(report.contains("Activity training load: 150.4 Garmin"))
        assertTrue(report.contains("Aerobic training effect: 3.9 /5"))
    }
}
