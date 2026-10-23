package com.fitlens.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitlens.ai.data.Dashboard
import com.fitlens.ai.data.CsvImportRepository
import com.fitlens.ai.data.CsvImportSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DashboardState {
    data object Loading : DashboardState
    data class Ready(val dashboard: Dashboard) : DashboardState
    data object Error : DashboardState
}
class DashboardViewModel(private val repository: CsvImportRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<DashboardState>(DashboardState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableImportMessage = MutableStateFlow<String?>(null)
    val importMessage = mutableImportMessage.asStateFlow()
    private val mutableGarminConnected = MutableStateFlow(repository.isGarminConnected())
    val garminConnected = mutableGarminConnected.asStateFlow()
    init { refresh() }

    fun importCsv(source: CsvImportSource, csv: String) {
        viewModelScope.launch {
            mutableImportMessage.value = null
            try {
                val count = withContext(Dispatchers.IO) { repository.importCsv(source, csv) }
                mutableImportMessage.value = when (source) {
                    CsvImportSource.GARMIN -> "Импортирани са данни от Garmin за $count дати."
                    CsvImportSource.MYFITNESSPAL -> "Импортирани са хранителни записи от MyFitnessPal за $count дати."
                }
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableImportMessage.value = failure.message ?: "CSV файлът не може да бъде импортиран."
            }
        }
    }

    fun reportImportError(message: String) { mutableImportMessage.value = message }

    fun connectGarmin(apiKey: String) = runGarminAction("Garmin е свързан и днешните данни са синхронизирани.") {
        repository.connectGarmin(apiKey)
        mutableGarminConnected.value = true
    }

    fun syncGarmin() = runGarminAction("Данните от Garmin са обновени.") { repository.syncGarmin() }

    fun disconnectGarmin() = runGarminAction("Garmin ключът и синхронизираните Garmin данни са изтрити от устройството.") {
        repository.disconnectGarmin()
        mutableGarminConnected.value = false
    }

    private fun runGarminAction(success: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            mutableImportMessage.value = "Свързване с Garmin…"
            try {
                withContext(Dispatchers.IO) { action() }
                mutableImportMessage.value = success
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableImportMessage.value = failure.message ?: "Garmin синхронизацията неуспешна."
            }
        }
    }

    fun clearImportedMetrics() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.clearImports() }
                mutableImportMessage.value = "Импортираните данни са изтрити от устройството."
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableImportMessage.value = failure.message ?: "Данните не могат да бъдат изтрити."
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = DashboardState.Loading
            try {
                mutableState.value = DashboardState.Ready(repository.dashboard())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = DashboardState.Error
            }
        }
    }
    class Factory(private val repository: CsvImportRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DashboardViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(repository) as T
        }
    }
}
