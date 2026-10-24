package com.fitlens.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitlens.ai.data.CalendarEvent
import com.fitlens.ai.data.CalendarEventStore
import com.fitlens.ai.data.EventDraft
import com.fitlens.ai.data.EventLinkImport
import com.fitlens.ai.data.EventLinkImporter
import com.fitlens.ai.data.RaceCalendarCatalogue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CalendarState(val events: List<CalendarEvent> = emptyList(), val loading: Boolean = true, val error: String? = null)
data class RaceCatalogueState(val entries: List<EventDraft> = emptyList(), val loading: Boolean = false,
    val loaded: Boolean = false, val error: String? = null)

class CalendarViewModel(private val store: CalendarEventStore, private val linkImporter: EventLinkImporter) : ViewModel() {
    private val mutableState = MutableStateFlow(CalendarState())
    private var operationRunning = false
    val state = mutableState.asStateFlow()
    private val mutableCatalogue = MutableStateFlow(RaceCatalogueState())
    val catalogue = mutableCatalogue.asStateFlow()
    init { update(action = {}) }

    fun add(draft: EventDraft, onSaved: () -> Unit) = update(action = { store.add(draft) }, onSuccess = onSaved)
    fun delete(id: String) = update(action = { store.delete(id) })
    fun retry() = update(action = {})

    fun loadRaceCalendar(url: String) {
        if (mutableCatalogue.value.loading) return
        mutableCatalogue.value = mutableCatalogue.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val entries = RaceCalendarCatalogue().load(url)
                mutableCatalogue.value = RaceCatalogueState(entries, loaded = true)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                mutableCatalogue.value = mutableCatalogue.value.copy(loading = false,
                    error = failure.message ?: "Календарът на racecalendar.bg не може да бъде прочетен.")
            }
        }
    }

    fun importLink(url: String, fallbackDate: String, onImported: (EventLinkImport) -> Unit) {
        if (operationRunning) return
        operationRunning = true
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                val result = withContext(Dispatchers.IO) { linkImporter.import(url, fallbackDate) }
                mutableState.value = mutableState.value.copy(loading = false, error = null)
                onImported(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.value = mutableState.value.copy(loading = false,
                    error = failure.message ?: "Линкът на събитието не може да бъде прочетен.")
            } finally {
                operationRunning = false
            }
        }
    }

    private fun update(action: () -> Unit, onSuccess: () -> Unit = {}) {
        if (operationRunning) return
        operationRunning = true
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                val events = withContext(Dispatchers.IO) { action(); store.all() }
                mutableState.value = CalendarState(events, loading = false)
                onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.value = mutableState.value.copy(loading = false,
                    error = failure.message ?: "Календарът не може да бъде зареден.")
            } finally {
                operationRunning = false
            }
        }
    }

    class Factory(private val store: CalendarEventStore, private val linkImporter: EventLinkImporter) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CalendarViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return CalendarViewModel(store, linkImporter) as T
        }
    }
}
