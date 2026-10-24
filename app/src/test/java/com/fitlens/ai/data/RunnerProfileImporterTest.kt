package com.fitlens.ai.data

import org.junit.Assert.*
import org.junit.Test

class RunnerProfileImporterTest {
    @Test fun readsPublicItraSummaryAndRaceTable() {
        val html = """<div class="runner-name"><h1>Runner</h1></div>
            <p>Performance Index: 520</p><table id="runner-results-table"><tbody>
            <tr><td>2026-10-01</td><td>Trail 42</td><td>Bulgaria</td><td>42km / 1800m+</td><td>5:20:00</td><td>15 / 200</td></tr>
            </tbody></table>"""
        val result = RunnerProfileImporter().parse("ITRA", html)
        assertEquals("Performance Index: 520", result.score)
        assertTrue(result.results!!.contains("42km / 1800m+"))
        assertTrue(result.results!!.contains("5:20:00"))
    }

    @Test fun readsCurrentItraReaderIndexPointCountsAndResults() {
        val content = """
            ##### Finished Races
            #### 5
            ![ITRA](https://itra.run/assets/images/itra_logo_white.svg)
            0
            #### x0
            ![ITRA](https://itra.run/assets/images/itra_logo_white.svg)
            1
            #### x4
            ![ITRA](https://itra.run/assets/images/itra_logo_white.svg)
            2
            #### x1
            ![ITRA](https://itra.run/assets/images/itra_logo_white.svg)
            3
            #### x0
            ![ITRA](https://itra.run/assets/images/itra_logo_white.svg)
            4
            #### x0
            ![ITRA](https://itra.run/assets/images/itra_logo_white.svg)
            5
            #### x0
            ![ITRA](https://itra.run/assets/images/itra_logo_white.svg)
            6
            #### x0
            ![calendar](https://itra.run/assets/images/calendar-icon.svg) 2026-10-04
            Category
            Half Marathon
            Distance/M+
            27 km /1898 m+
            ![ITRA](https://itra.run/assets/images/itra_logo_white.svg)
            2
            Gender Ranking
            182
            Race Time
            7:33:47
        """.trimIndent()
        val json = org.json.JSONObject().put("data", org.json.JSONObject()
            .put("description", "Runner has an ITRA Performance Index is 324.").put("content", content)).toString()

        val result = RunnerProfileImporter().parseItraReader(json)

        assertEquals("Performance Index: 324 · Завършени: 5 · ITRA 1: 4 · ITRA 2: 1 · ITRA 3: 0 · ITRA 4: 0 · ITRA 5: 0 · ITRA 6: 0", result.score)
        assertEquals("2026-10-04 · Half Marathon · 27 km /1898 m+ | ITRA 2 | място 182 | 7:33:47", result.results)
    }

    @Test fun readsBtrLabelAndResultsTable() {
        val html = """<p>BTR рейтинг: 610</p><table><thead><tr><th>Състезание</th><th>Дата</th><th>Време</th></tr></thead>
            <tbody><tr><td>Mountain 30</td><td>2026-10-01</td><td>3:15:00</td></tr></tbody></table>"""
        val result = RunnerProfileImporter().parse("BTR", html)
        assertEquals("BTR: 610", result.score)
        assertTrue(result.results!!.contains("Mountain 30"))
    }

    @Test fun readsCurrentPublicBtrApiRatingPlaceAndResults() {
        val profile = """{"data":{"full_name":"Runner","gender":"male","rating":409,"place":4989,"total":13040}}"""
        val results = """{"data":{"data":[
            {"date":"2026-06-06","race_name":"Salomon Vitosha Trail","distance_name":"Трасе 27км","place":"98","time":"4:43:17","rating":380},
            {"date":"2026-05-30","race_name":"Thracian Ultra","distance_name":"Discovery Race 12K","place":"19","time":"1:38:28","rating":438}
        ],"total":2}}"""
        val result = RunnerProfileImporter().parseBtrApi(profile, results)

        assertEquals("BTR рейтинг: 409 · Място: 4989 / 13040 (мъже)", result.score)
        assertEquals(2, result.results!!.lineSequence().count())
        assertTrue(result.results!!.contains("Salomon Vitosha Trail · Трасе 27км | място 98 | 4:43:17 | рейтинг 380"))
        assertTrue(result.results!!.contains("Thracian Ultra"))
    }

    @Test fun keepsBtrPlaceWhenResultsEndpointIsUnavailable() {
        val result = RunnerProfileImporter().parseBtrApi(
            """{"data":{"gender":"female","rating":500,"place":120,"total":4500}}""", null)

        assertEquals("BTR рейтинг: 500 · Място: 120 / 4500 (жени)", result.score)
        assertNull(result.results)
    }

    @Test fun readsStructuredBtrProfileState() {
        val result = RunnerProfileImporter().parse("BTR", """<script type="application/json">
            {"profile":{"btr_index":605},"results":[{"raceName":"Trail 21","finishTime":"2:10:00"}]}
            </script>""")
        assertEquals("btr_index: 605", result.score)
        assertTrue(result.results!!.contains("Trail 21"))
    }

    @Test fun doesNotInventResultsFromLoginOrUnrelatedTables() {
        val result = RunnerProfileImporter().parse("BTR", """<h1>BTR 2026</h1><p>Please sign in</p>
            <table><tr><th>Price</th><th>Plan</th></tr><tr><td>20</td><td>Premium</td></tr></table>""")
        assertNull(result.score)
        assertNull(result.results)
    }
}
