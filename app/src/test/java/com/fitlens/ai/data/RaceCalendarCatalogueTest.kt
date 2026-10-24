package com.fitlens.ai.data

import org.junit.Assert.*
import org.junit.Test

class RaceCalendarCatalogueTest {
    @Test fun expandsTrailAndMtbDistancesAndSkipsRoadEvents() {
        val response = org.json.JSONObject("""{"results":[
          {"id":1290,"name":"ZARA ULTRA Bike & Run","nameId":"zara-trail-run-2026","country":"България","city":"Стара Загора",
           "terrain":5,"tags":"Trail run, Хоби MTB, XCM","distances":[
             {"id":1,"name":"Trail run","distance":21,"startDate":"2026-11-01","startTime":"09:10:00","elevationGain":800,"type":1},
             {"id":2,"name":"Хоби MTB","distance":30,"startDate":"2026-10-31","startTime":"09:00:00","elevationGain":1200,"type":2}]},
          {"id":5,"name":"City road run","nameId":"city-road","country":"България","city":"София","terrain":2,
           "distances":[{"id":9,"distance":10,"startDate":"2026-12-01","type":1}]}
        ]}""")
        val events = RaceCalendarCatalogue().parseApi(response)
        assertEquals(2, events.size)
        assertEquals("mountain_biking", events[0].sport)
        assertEquals("2026-10-31", events[0].date)
        assertEquals(30.0, events[0].distanceKm!!, 0.01)
        assertEquals("trail_running", events[1].sport)
        assertEquals(800, events[1].elevationGainMeters)
        assertEquals("https://racecalendar.bg/run-race/zara-trail-run-2026", events[1].sourceUrl)
    }

    @Test fun extractsMultipleStructuredEventsWithDetailsAndLinks() {
        val html = """<script type="application/ld+json">{"@graph":[
            {"@type":"SportsEvent","name":"Mountain Trail 21 km","startDate":"2027-05-09T08:30:00+03:00",
             "url":"/races/mountain-trail","additionalProperty":[{"name":"Elevation gain","value":"1100 m"}]},
            {"@type":"Event","name":"City Run","startDate":"2027-06-06","url":"https://example.org/city-run"}
        ]}</script>"""
        val events = RaceCalendarCatalogue().parse(html)
        assertEquals(2, events.size)
        assertEquals("trail_running", events[0].sport)
        assertEquals("08:30", events[0].time)
        assertEquals(21.0, events[0].distanceKm!!, 0.01)
        assertEquals(1100, events[0].elevationGainMeters)
        assertEquals("https://racecalendar.bg/races/mountain-trail", events[0].sourceUrl)
    }

    @Test fun extractsDatedBulgarianCardsAndDeduplicates() {
        val card = """<article><h2><a href="/trail">Трейл 30 km</a></h2><p>12 май 2027</p>
            <p>Старт: 07:00 • Денивелация: 1500 m • Контролно време: 8 часа</p></article>"""
        val events = RaceCalendarCatalogue().parse(card + card)
        assertEquals(1, events.size)
        assertEquals("2027-05-12", events[0].date)
        assertEquals("07:00", events[0].time)
        assertEquals("trail_running", events[0].sport)
        assertEquals("8 часа", events[0].cutoffTime)
    }

    @Test fun doesNotAssignGlobalDatesToUndatedCardsOrImportUnsafeLinks() {
        val html = """<header>2027-05-01</header><article><h2><a href="/undated">No date</a></h2></article>
            <script type="application/ld+json">{"@type":"Event","name":"Invalid date","startDate":"2027-02-30"}</script>"""
        assertTrue(RaceCalendarCatalogue().parse(html).isEmpty())
    }

    @Test fun recognizesEventTimeAttribute() {
        val html = """<article><h3><a href="/run">City Run</a></h3><time datetime="2027-11-14T09:00:00">14 November</time></article>"""
        val events = RaceCalendarCatalogue().parse(html)
        assertEquals("2027-11-14", events.single().date)
        assertEquals("09:00", events.single().time)
    }
}
