package com.fitlens.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitlens.ai.data.CalendarEvent
import com.fitlens.ai.data.CalendarEventStore
import com.fitlens.ai.data.EventDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CalendarState(val events: List<CalendarEvent> = emptyList(), val loading: Boolean = true, val error: String? = null)

class CalendarViewModel(private val store: CalendarEventStore) : ViewModel() {
    private val mutableState = MutableStateFlow(CalendarState())
    private var operationRunning = false
    val state = mutableState.asStateFlow()
    init { update(action = {}) }

    fun add(draft: EventDraft, onSaved: () -> Unit) = update(action = { store.add(draft) }, onSuccess = onSaved)
    fun delete(id: String) = update(action = { store.delete(id) })
    fun retry() = update(action = {})

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

    class Factory(private val store: CalendarEventStore) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CalendarViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return CalendarViewModel(store) as T
        }
    }
}
