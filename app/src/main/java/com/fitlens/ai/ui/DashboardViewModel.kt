package com.fitlens.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitlens.ai.data.Dashboard
import com.fitlens.ai.data.CsvImportRepository
import com.fitlens.ai.data.CsvImportSource
import com.fitlens.ai.data.CalendarEvent
import com.fitlens.ai.data.GarminTrainingHistory
import com.fitlens.ai.data.ChatGptShareReport
import java.time.LocalDate
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
class DashboardViewModel(
    private val repository: CsvImportRepository,
    private val runnerProfiles: com.fitlens.ai.data.RunnerProfileStore,
    private val onGarminConnectionChanged: (Boolean) -> Unit = {},
) : ViewModel() {
    private val mutableState = MutableStateFlow<DashboardState>(DashboardState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableImportMessage = MutableStateFlow<String?>(null)
    val importMessage = mutableImportMessage.asStateFlow()
    private val mutableGarminConnected = MutableStateFlow(repository.isGarminConnected())
    val garminConnected = mutableGarminConnected.asStateFlow()
    private val mutableGarminBusy = MutableStateFlow(false)
    val garminBusy = mutableGarminBusy.asStateFlow()
    private val mutableHistoryBusy = MutableStateFlow(false)
    val historyBusy = mutableHistoryBusy.asStateFlow()
    private val mutableHistoryCount = MutableStateFlow(0)
    val historyCount = mutableHistoryCount.asStateFlow()
    private val mutableReportBusy = MutableStateFlow(false)
    val reportBusy = mutableReportBusy.asStateFlow()
    private val mutableShareReport = MutableStateFlow<String?>(null)
    val shareReport = mutableShareReport.asStateFlow()
    private var lastForegroundSyncMillis = 0L
    init {
        if (mutableGarminConnected.value) syncGarminIfNeeded() else refresh()
    }

    fun syncGarminIfNeeded() {
        if (!mutableGarminConnected.value || mutableGarminBusy.value) return
        val now = System.currentTimeMillis()
        if (now - lastForegroundSyncMillis < FOREGROUND_SYNC_INTERVAL_MILLIS) return
        lastForegroundSyncMillis = now
        mutableGarminBusy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.syncGarmin() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableImportMessage.value = com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.auto_sync_failed)
            } finally {
                mutableGarminBusy.value = false
                refresh()
            }
        }
    }

    fun importCsv(source: CsvImportSource, csv: String) {
        viewModelScope.launch {
            mutableImportMessage.value = null
            try {
                val count = withContext(Dispatchers.IO) { repository.importCsv(source, csv) }
                mutableImportMessage.value = when (source) {
                    CsvImportSource.GARMIN -> com.fitlens.ai.data.LocalizedText.format(com.fitlens.ai.R.string.dynamic_017, count)
                    CsvImportSource.MYFITNESSPAL -> com.fitlens.ai.data.LocalizedText.format(com.fitlens.ai.R.string.dynamic_018, count)
                }
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableImportMessage.value = failure.message ?: com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_154)
            }
        }
    }

    fun reportImportError(message: String) { mutableImportMessage.value = message }

    fun connectGarmin(apiKey: String) = runGarminAction(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_155)) {
        repository.connectGarmin(apiKey)
        mutableGarminConnected.value = true
        onGarminConnectionChanged(true)
    }

    fun syncGarmin() = runGarminAction(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_156)) { repository.syncGarmin() }

    fun disconnectGarmin() = runGarminAction(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_157)) {
        repository.disconnectGarmin()
        mutableGarminConnected.value = false
        onGarminConnectionChanged(false)
    }

    fun syncTrainingHistory(days: Int = 30) {
        if (mutableHistoryBusy.value || !mutableGarminConnected.value) return
        mutableHistoryBusy.value = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { repository.syncTrainingHistory(days) }
                mutableHistoryCount.value = result.activities.size
                mutableImportMessage.value = com.fitlens.ai.data.LocalizedText.format(com.fitlens.ai.R.string.dynamic_019, result.activities.size, days)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableImportMessage.value = failure.message ?: com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_158)
            } finally { mutableHistoryBusy.value = false }
        }
    }

    fun prepareChatGptReport(event: CalendarEvent?) {
        if (mutableReportBusy.value) return
        mutableReportBusy.value = true
        mutableShareReport.value = null
        mutableHistoryBusy.value = true
        viewModelScope.launch {
            try {
                val to = LocalDate.now()
                val from = to.minusDays(29)
                val history = withContext(Dispatchers.IO) {
                    repository.syncTrainingHistory(30)
                    GarminTrainingHistory(
                        repository.garminActivitiesBetween(from.toString(), to.toString()),
                        repository.garminRecoveryBetween(from.toString(), to.toString()),
                    )
                }
                mutableHistoryCount.value = history.activities.size
                require(history.activities.isNotEmpty()) { com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_159) }
                mutableShareReport.value = ChatGptShareReport.build(history.activities, history.recovery, event, runnerProfiles.all(), java.util.Locale.getDefault().language)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableImportMessage.value = failure.message ?: com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_160)
            } finally { mutableHistoryBusy.value = false; mutableReportBusy.value = false }
        }
    }

    fun dismissShareReport() { mutableShareReport.value = null }

    private fun runGarminAction(success: String, action: suspend () -> Unit) {
        if (mutableGarminBusy.value) return
        mutableGarminBusy.value = true
        viewModelScope.launch {
            mutableImportMessage.value = com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_161)
            try {
                withContext(Dispatchers.IO) { action() }
                mutableImportMessage.value = success
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableImportMessage.value = failure.message ?: com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_162)
            } finally {
                mutableGarminBusy.value = false
            }
        }
    }

    fun clearImportedMetrics() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.clearImports() }
                mutableImportMessage.value = com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_163)
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableImportMessage.value = failure.message ?: com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_164)
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
    class Factory(
        private val repository: CsvImportRepository,
        private val runnerProfiles: com.fitlens.ai.data.RunnerProfileStore,
        private val onGarminConnectionChanged: (Boolean) -> Unit = {},
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DashboardViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(repository, runnerProfiles, onGarminConnectionChanged) as T
        }
    }

    private companion object {
        const val FOREGROUND_SYNC_INTERVAL_MILLIS = 30 * 60 * 1000L
    }
}
