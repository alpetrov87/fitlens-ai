package com.fitlens.ai.workouts

import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WorkoutBackendTransportTest {
    private val token = "synthetic_pairing_key_for_tests_only_" + "x".repeat(32)
    private val owner = "a".repeat(64)
    private val key = "b".repeat(64)
    private fun transport(execute: (Request) -> WorkoutHttpResponse) = BackendWorkoutTransport(
        WorkoutBackendSettings("https://personal.example.test", token), WorkoutHttpExecutor(execute))
    private fun authenticated(execute: (Request) -> WorkoutHttpResponse): BackendWorkoutTransport = transport {
        if (it.url.encodedPath == "/v1/session") WorkoutHttpResponse(200, """{"connected":true,"owner":"$owner"}""") else execute(it)
    }.also { it.authentication() }
    @Test fun onlyTrustedHttpsOriginCanBeConfigured() {
        assertEquals("https://personal.example.test", workoutBackendUrl("https://personal.example.test/"))
        listOf("http://host.test", "https://user:pass@host.test", "https://host.test/api", "https://host.test?token=x", "https://host.test/#part").forEach {
            assertTrue(it, runCatching { workoutBackendUrl(it) }.isFailure)
        }
    }
    @Test fun requestsStayOnConfiguredOriginAndCarryOnlyBackendCredentials() {
        val requests = mutableListOf<Request>()
        val t = transport { request -> requests += request; WorkoutHttpResponse(200, if (request.method == "GET") """{"connected":true,"owner":"$owner"}""" else """{"workoutId":123}""") }
        assertEquals(WorkoutConnection.CONNECTED, t.authentication())
        assertEquals("https://personal.example.test#$owner", t.identity())
        t.create(JSONObject().put("workoutName", "Synthetic"), key)
        t.update("123", JSONObject(), key)
        assertEquals(listOf("GET", "POST", "PUT"), requests.map { it.method })
        assertEquals("/v1/workouts/123", requests.last().url.encodedPath)
        assertTrue(requests.all { it.url.isHttps && it.url.host == "personal.example.test" && it.header("Authorization") == "Bearer $token" })
        assertEquals(key, requests[1].header("Idempotency-Key"))
        assertEquals(owner, requests[1].header("X-Bair-Owner"))
        assertTrue(runCatching { t.update("123/../../other", JSONObject(), key) }.isFailure)
    }
    @Test fun httpFailuresAreSanitizedAndNeverExposeBodies() {
        for ((status, code) in listOf(401 to "expired", 403 to "authentication", 429 to "rate_limit", 422 to "validation", 409 to "uncertain", 503 to "network", 302 to "network")) {
            val failure = runCatching { authenticated { WorkoutHttpResponse(status, "private-server-message") }.create(JSONObject(), key) }.exceptionOrNull()
            assertEquals(code, (failure as WorkoutRemoteFailure).code)
            assertFalse(failure.message.orEmpty().contains("private-server-message"))
        }
        assertEquals("network", (runCatching { transport { throw java.io.IOException("private") }.authentication() }.exceptionOrNull() as WorkoutRemoteFailure).code)
    }
    @Test fun invalidSessionAndMalformedResponsesCannotBecomeConnected() {
        for (body in listOf("not JSON", "{}", """{"connected":true}""", """{"connected":true,"owner":"wrong"}""")) {
            val result = runCatching { transport { WorkoutHttpResponse(200, body) }.authentication() }
            assertFalse(result.getOrNull() == WorkoutConnection.CONNECTED)
        }
        assertEquals(WorkoutConnection.SETUP_REQUIRED, transport { WorkoutHttpResponse(200, """{"connected":false}""") }.authentication())
    }
    @Test fun lookupNeverSubmitsAnotherWorkout() {
        val requests = mutableListOf<Request>()
        val t = authenticated { requests += it; WorkoutHttpResponse(200, """{"workoutId":123}""") }
        assertEquals(123, t.lookup(key).getInt("workoutId"))
        assertEquals("GET", requests.single().method)
        assertEquals("/v1/requests/$key", requests.single().url.encodedPath)
    }
    @Test fun oversizedResponseCannotBecomeAnUploadResult() {
        assertEquals("invalid_response", (runCatching { authenticated { WorkoutHttpResponse(200, "x".repeat(65537)) }.create(JSONObject(), key) }.exceptionOrNull() as WorkoutRemoteFailure).code)
    }
}
