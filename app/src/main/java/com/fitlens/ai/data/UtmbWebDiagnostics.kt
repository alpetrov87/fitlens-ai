package com.fitlens.ai.data

import java.net.URI

internal enum class UtmbWebEvent { START, FINISH, HTTP, NETWORK, TLS, BLOCKED, POPUP, POPUP_BLOCKED, JS_ERROR, BLANK, COOKIES }

/** Codes and hostnames only. Never retain URLs, login text, cookies, console messages or form values. */
internal class UtmbWebDiagnostics {
    private val events = ArrayDeque<String>()
    fun record(type: UtmbWebEvent, url: String, code: Int? = null) {
        val host = runCatching { URI(url).host?.lowercase(java.util.Locale.ROOT) }.getOrNull()
            ?.takeIf { it.matches(Regex("[a-z0-9.-]{1,253}")) } ?: "unknown"
        events.addLast("${type.name} | $host${code?.let { " | $it" }.orEmpty()}")
        while (events.size > 20) events.removeFirst()
    }
    fun report(webViewVersion: String, thirdPartyCookies: Boolean): String = buildString {
        appendLine("bAIr UTMB sign-in diagnostics")
        appendLine("WebView: " + webViewVersion.take(60).replace(Regex("[^a-zA-Z0-9 ._-]"), ""))
        appendLine("Third-party cookies: $thirdPartyCookies")
        events.forEach { appendLine(it) }
    }
}
