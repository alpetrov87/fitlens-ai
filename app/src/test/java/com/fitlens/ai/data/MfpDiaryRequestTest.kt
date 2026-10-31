package com.fitlens.ai.data

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class MfpDiaryRequestTest {
    private val url = "https://myfitnesspal.com/food/diary/example?date=2026-10-31".toHttpUrl()
    @Test fun canonicalRedirectRefreshesCookiesBeforeFollowing() = runBlocking {
        val requests = mutableListOf<okhttp3.Request>()
        val jar = mutableMapOf(url.host to "session=old")
        val client = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
            requests.add(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (requests.size == 1) 302 else 200).message("test")
                .apply { if (requests.size == 1) {
                    header("Location", "https://www.myfitnesspal.com/food/diary/example?date=2026-10-31")
                    header("Set-Cookie", "session=renewed; Domain=myfitnesspal.com; Secure")
                } }.body("diary".toResponseBody()).build()
        }.build()
        requestMfpDiary(client, url, "test-agent", { jar[it.toHttpUrl().host].orEmpty() }, { _, values ->
            if (values.isNotEmpty()) jar["www.myfitnesspal.com"] = "session=renewed"
        }).use { assertEquals(200, it.code) }
        assertEquals(2, requests.size)
        assertEquals("session=renewed", requests[1].header("Cookie"))
    }
    @Test fun loginExternalInsecureAndWrongDayRedirectsAreNotFollowed() = runBlocking {
        for (target in listOf("/account/login", "https://example.com/food/diary/example?date=2026-10-31",
            "http://myfitnesspal.com/food/diary/example?date=2026-10-31",
            "/food/diary/example?date=2026-10-30")) {
            var calls = 0
            val client = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
                calls++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(302).message("test").header("Location", target).body("".toResponseBody()).build()
            }.build()
            requestMfpDiary(client, url, "test", { "session=private" }, { _, _ -> }).use {
                assertEquals(302, it.code)
            }
            assertEquals(1, calls)
        }
    }
    @Test fun redirectLoopStopsWithoutRepeatedRequests() = runBlocking {
        var calls = 0
        val client = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
            calls++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(302)
                .message("test").header("Location", url.toString()).body("".toResponseBody()).build()
        }.build()
        try {
            requestMfpDiary(client, url, "test", { "session=test" }, { _, _ -> }).close()
            fail("Expected redirect loop rejection")
        } catch (_: java.io.IOException) { assertEquals(1, calls) }
    }
}
