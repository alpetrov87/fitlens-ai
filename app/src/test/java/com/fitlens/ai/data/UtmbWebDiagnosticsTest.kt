package com.fitlens.ai.data

import org.junit.Assert.*
import org.junit.Test

class UtmbWebDiagnosticsTest {
    @Test fun stripsCredentialsPathsQueriesAndFragments() {
        val log = UtmbWebDiagnostics()
        log.record(UtmbWebEvent.HTTP, "https://email:password@auth.example.test/private/secret?code=OAUTH_SECRET&email=private#TOKEN", 403)
        val report = log.report("130.0.1", false)
        assertTrue(report.contains("HTTP | auth.example.test | 403"))
        listOf("password", "OAUTH_SECRET", "private", "TOKEN", "email:", "/secret").forEach { assertFalse(it, report.contains(it)) }
    }
    @Test fun malformedAndNonWebSourcesDoNotExposeTheirContents() {
        val log = UtmbWebDiagnostics()
        log.record(UtmbWebEvent.JS_ERROR, "data:text/html,PRIVATE_PAGE")
        log.record(UtmbWebEvent.BLOCKED, "NOT A URI WITH PRIVATE TEXT")
        val report = log.report("130.0.1", true)
        assertTrue(report.contains("JS_ERROR | unknown"))
        assertFalse(report.contains("PRIVATE"))
        assertTrue(report.contains("Third-party cookies: true"))
    }
    @Test fun eventHistoryIsBounded() {
        val log = UtmbWebDiagnostics()
        (1..100).forEach { log.record(UtmbWebEvent.NETWORK, "https://utmb.world/runner/synthetic", it) }
        val events = log.report("130", false).lines().filter { it.startsWith("NETWORK") }
        assertEquals(20, events.size)
        assertTrue(events.last().endsWith("100"))
        assertTrue(events.first().endsWith("81"))
    }
}
