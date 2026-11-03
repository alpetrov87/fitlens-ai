package com.fitlens.ai.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GarminMetricProbeTest {
    @Test fun extractsNumericMetricsWithoutProfileOrCredentials() {
        val result = garminProbeFields(JSONObject("""{"displayName":"private", "accessToken":"secret", "summaryDTO":{"vO2MaxValue":48.5,"aerobicTrainingEffect":3.2,"trainingStatus":"PRODUCTIVE","activityId":12345,"trainingToken":12345,"trainingId":12345}}"""))
        assertEquals(listOf("summaryDTO.vO2MaxValue = 48.5", "summaryDTO.aerobicTrainingEffect = 3.2").toSet(), result.toSet())
        assertFalse(result.joinToString().contains("private"))
        assertFalse(result.joinToString().contains("secret"))
        assertFalse(result.joinToString().contains("12345"))
    }
    @Test fun boundsArraysDepthAndResults() {
        val json = JSONObject().put("days", org.json.JSONArray((1..40).map { JSONObject().put("hrvAverage", it) }))
        assertEquals(30, garminProbeFields(json).size)
        assertTrue(garminProbeFields(null).isEmpty())
        assertTrue(garminProbeFields(JSONObject().put("vo2", JSONObject.NULL)).isEmpty())
    }
    @Test fun datesAndNestedHrvValuesAreReadableWithoutMetadataIndices() {
        val fields = garminProbeFields(JSONObject("""{"hrvSummaries":[{"calendarDate":"2026-11-03","lastNightAvg":45}],"bodyBatteryValueDescriptorIndex":1}"""))
        assertEquals(listOf("hrvSummaries[2026-11-03].lastNightAvg = 45"), fields)
    }
    @Test fun reportHasClearSectionsAndLineBreaks() {
        val report = garminProbeReport(listOf(GarminProbeResult("HRV 2026-11-03", 200, listOf("lastNightAvg = 45")), GarminProbeResult("Sleep", 204)), "No fields")
        assertTrue(report.contains("\n\n=== HRV 2026-11-03 ===\nHTTP 200\nlastNightAvg = 45\n"))
        assertTrue(report.contains("=== Sleep ===\nHTTP 204\nNo fields"))
    }
    @Test fun activitySelectionPrioritizesRunsAndCyclingAndIsBounded() {
        val list = org.json.JSONArray((1..20).map { JSONObject().put("activityId", it)
            .put("activityType", JSONObject().put("typeKey", if (it == 20) "trail_running" else "walking")) })
        val selected = garminProbeActivities(list)
        assertEquals(12, selected.size)
        assertEquals(20, selected.first().getInt("activityId"))
    }
    @Test fun bodyBatteryReadingsExcludeTimestampsAndMissingSentinels() {
        val fields = garminProbeFields(JSONObject("""{"bodyBatteryValuesArray":[[1000000,72],[1000001,-1],[1000002,48],[1000003,null]]}"""))
        assertEquals(listOf("bodyBatteryValuesArray.minimumFromReadings = 48.0", "bodyBatteryValuesArray.maximumFromReadings = 72.0", "bodyBatteryValuesArray.lastValidReading = 48.0"), fields)
    }
}
