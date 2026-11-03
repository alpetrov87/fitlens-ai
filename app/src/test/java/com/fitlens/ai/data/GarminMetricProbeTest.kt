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
        val json = JSONObject().put("days", org.json.JSONArray((1..20).map { JSONObject().put("hrvAverage", it) }))
        assertEquals(5, garminProbeFields(json).size)
        assertTrue(garminProbeFields(null).isEmpty())
        assertTrue(garminProbeFields(JSONObject().put("vo2", JSONObject.NULL)).isEmpty())
    }
}
