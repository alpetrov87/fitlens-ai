package com.fitlens.ai.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EventLinkImporterTest {
    @Test fun extractsExtendedMetricsFromStructuredEventData() {
        val html = """
            <html><head><script type="application/ld+json">
            {
              "@type": "SportsEvent",
              "name": "Mountain Trail 42 km",
              "startDate": "2026-11-08T07:30:00+02:00",
              "location": {"@type":"Place", "name":"Vitosha"},
              "additionalProperty": [
                {"@type":"PropertyValue", "name":"Distance", "value":"42.195 km"},
                {"@type":"PropertyValue", "name":"Elevation gain", "value":"1850 m"},
                {"@type":"PropertyValue", "name":"Time limit", "value":"8 hours"}
              ]
            }
            </script></head></html>
        """.trimIndent()

        val fields = EventLinkImporter().extractFields(html)

        assertEquals(fields.toString(), "2026-11-08", fields.date)
        assertEquals("07:30", fields.time)
        assertEquals(42.195, fields.distanceKm!!, 0.001)
        assertEquals(1850, fields.elevationGainMeters)
        assertEquals("8 hours", fields.cutoffTime)
        assertEquals("trail_running", fields.sport)
    }

    @Test fun extractsMetricsFromVisibleBulgarianText() {
        val html = """
            <html><head><meta property="og:title" content="Планинско бягане" /></head>
            <body>Дистанция: 21.5 км • Старт: 08:15 • Контролно време: 5 часа • Денивелация: 1250 m</body></html>
        """.trimIndent()

        val fields = EventLinkImporter().extractFields(html)

        assertEquals("08:15", fields.time)
        assertEquals(21.5, fields.distanceKm!!, 0.001)
        assertEquals(1250, fields.elevationGainMeters)
        assertEquals("5 часа", fields.cutoffTime)
        assertEquals("trail_running", fields.sport)
    }
}
