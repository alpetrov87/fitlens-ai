package com.fitlens.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class CalendarEvent(
    val id: String,
    val title: String,
    val date: String,
    val time: String,
    val sport: String,
    val location: String,
    val sourceUrl: String = "",
    val distanceKm: Double? = null,
    val cutoffTime: String = "",
    val elevationGainMeters: Int? = null,
)

data class EventDraft(
    val title: String,
    val date: String,
    val time: String = "",
    val sport: String = "running",
    val location: String = "",
    val sourceUrl: String = "",
    val distanceKm: Double? = null,
    val cutoffTime: String = "",
    val elevationGainMeters: Int? = null,
) {
    fun validate() {
        require(title.trim().length in 1..120) { "Въведи име на събитието (до 120 знака)." }
        require(runCatching { LocalDate.parse(date) }.isSuccess) { "Избери валидна дата." }
        require(time.isBlank() || (time.matches(Regex("\\d{2}:\\d{2}")) && runCatching { LocalTime.parse(time) }.isSuccess)) {
            "Въведи час във формат ЧЧ:ММ, например 09:30."
        }
        require(location.length <= 200) { "Мястото трябва да е до 200 знака." }
        require(sourceUrl.length <= 500 && (sourceUrl.isBlank() || sourceUrl.startsWith("https://", ignoreCase = true))) {
            "Линкът към събитието трябва да е HTTPS и до 500 знака."
        }
        require(distanceKm == null || distanceKm in 0.01..2_000.0) { "Дистанцията трябва да е между 0.01 и 2000 km." }
        require(cutoffTime.length <= 80) { "Контролното време трябва да е до 80 знака." }
        require(elevationGainMeters == null || elevationGainMeters in 0..100_000) { "Денивелацията трябва да е между 0 и 100 000 m." }
    }
}

/** A separate local calendar: deleting health imports does not delete planned events. */
class CalendarEventStore(context: Context) {
    private val preferences = context.getSharedPreferences("fitlens_calendar", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<CalendarEvent> {
        val rows = JSONArray(preferences.getString("events_v1", "[]"))
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            CalendarEvent(row.getString("id"), row.getString("title"), row.getString("date"),
                row.optString("time"), row.optString("sport", "other"), row.optString("location"), row.optString("sourceUrl"),
                row.optDouble("distanceKm").takeUnless(Double::isNaN),
                row.optString("cutoffTime"),
                row.optInt("elevationGainMeters", -1).takeIf { it >= 0 })
        }.sortedWith(compareBy(CalendarEvent::date, CalendarEvent::time, CalendarEvent::title))
    }

    @Synchronized
    fun add(draft: EventDraft) {
        draft.validate()
        write(all() + CalendarEvent(UUID.randomUUID().toString(), draft.title.trim(), draft.date,
            draft.time.trim(), draft.sport, draft.location.trim(), draft.sourceUrl.trim(),
            draft.distanceKm, draft.cutoffTime.trim(), draft.elevationGainMeters))
    }

    @Synchronized
    fun delete(id: String) = write(all().filterNot { it.id == id })

    private fun write(events: List<CalendarEvent>) {
        val rows = JSONArray()
        events.forEach { event ->
            rows.put(JSONObject().put("id", event.id).put("title", event.title).put("date", event.date)
                .put("time", event.time).put("sport", event.sport).put("location", event.location)
                .put("sourceUrl", event.sourceUrl).put("distanceKm", event.distanceKm)
                .put("cutoffTime", event.cutoffTime).put("elevationGainMeters", event.elevationGainMeters))
        }
        check(preferences.edit().putString("events_v1", rows.toString()).commit()) {
            "Календарът не можа да бъде запазен на устройството."
        }
    }
}
