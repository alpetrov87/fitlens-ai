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

    @Test fun readsBtrLabelAndResultsTable() {
        // Representative fixture: live BTR markup remains unverified in this environment.
        val html = """<p>BTR рейтинг: 610</p><table><thead><tr><th>Състезание</th><th>Дата</th><th>Време</th></tr></thead>
            <tbody><tr><td>Mountain 30</td><td>2026-10-01</td><td>3:15:00</td></tr></tbody></table>"""
        val result = RunnerProfileImporter().parse("BTR", html)
        assertEquals("BTR: 610", result.score)
        assertTrue(result.results!!.contains("Mountain 30"))
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
