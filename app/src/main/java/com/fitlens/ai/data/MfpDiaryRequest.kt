package com.fitlens.ai.data

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/** Redirects are explicit so WebView cookies are refreshed per URL, never sent off-site. */
internal suspend fun requestMfpDiary(
    client: OkHttpClient,
    initialUrl: HttpUrl,
    agent: String,
    cookies: suspend (String) -> String,
    saveCookies: suspend (String, List<String>) -> Unit,
): Response {
    var url = initialUrl
    val visited = mutableSetOf<String>()
    repeat(6) {
        if (!visited.add(url.toString())) throw IOException("MFP redirect loop")
        val cookie = cookies(url.toString())
        val request = Request.Builder().url(url).header("User-Agent", agent)
            .header("Accept", "text/html").apply {
                if (cookie.isNotBlank()) header("Cookie", cookie)
            }.build()
        val response = client.newCall(request).execute()
        try {
            saveCookies(url.toString(), response.headers.values("Set-Cookie"))
            if (response.code !in listOf(301, 302, 303, 307, 308)) return response
            val next = response.header("Location")?.let(url::resolve)
            // Only canonical MFP diary URLs. Login, consent and challenges require user interaction.
            if (next == null || next.scheme != "https" || next.port != 443 ||
                next.host !in setOf("www.myfitnesspal.com", "myfitnesspal.com") ||
                next.username.isNotEmpty() || next.password.isNotEmpty() ||
                next.encodedPath.trimEnd('/') != initialUrl.encodedPath.trimEnd('/') ||
                next.queryParameterValues("date").let { dates ->
                    dates.isNotEmpty() && dates != initialUrl.queryParameterValues("date")
                }) return response
            url = next
        } catch (error: Throwable) {
            response.close()
            throw error
        }
        response.close()
    }
    throw IOException("MFP redirect limit")
}
