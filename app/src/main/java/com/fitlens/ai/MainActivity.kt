package com.fitlens.ai

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitlens.ai.data.CsvImportSource
import com.fitlens.ai.data.DefaultHealthRepository
import com.fitlens.ai.data.DemoActivitySource
import com.fitlens.ai.data.DemoNutritionSource
import com.fitlens.ai.data.SharedPreferencesImportedMetricsStore
import com.fitlens.ai.data.GarminPersonalApi
import com.fitlens.ai.data.GarminPersonalKeyStore
import com.fitlens.ai.ui.DashboardScreen
import com.fitlens.ai.ui.DashboardViewModel
import com.fitlens.ai.ui.FitLensTheme
import com.fitlens.ai.ui.CalendarViewModel
import com.fitlens.ai.data.CalendarEventStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = DashboardViewModel.Factory(
            DefaultHealthRepository(
                DemoActivitySource(), DemoNutritionSource(), SharedPreferencesImportedMetricsStore(applicationContext),
                GarminPersonalApi(GarminPersonalKeyStore(applicationContext)),
            ),
        )
        val calendarFactory = CalendarViewModel.Factory(CalendarEventStore(applicationContext))
        setContent {
            FitLensTheme {
                val model: DashboardViewModel = viewModel(factory = factory)
                val calendarModel: CalendarViewModel = viewModel(factory = calendarFactory)
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                var importSource by remember { mutableStateOf(CsvImportSource.GARMIN) }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        val selectedSource = importSource
                        scope.launch {
                            try {
                                val csv = withContext(Dispatchers.IO) { readCsv(context, uri) }
                                model.importCsv(selectedSource, csv)
                            } catch (failure: Exception) {
                                model.reportImportError(failure.message ?: "CSV файлът не може да бъде прочетен.")
                            }
                        }
                    }
                }
                DashboardScreen(
                    state = model.state.collectAsStateWithLifecycle().value,
                    importMessage = model.importMessage.collectAsStateWithLifecycle().value,
                    garminConnected = model.garminConnected.collectAsStateWithLifecycle().value,
                    garminBusy = model.garminBusy.collectAsStateWithLifecycle().value,
                    calendar = calendarModel.state.collectAsStateWithLifecycle().value,
                    onAddEvent = calendarModel::add,
                    onDeleteEvent = calendarModel::delete,
                    onRetryCalendar = calendarModel::retry,
                    onConnectGarmin = model::connectGarmin,
                    onSyncGarmin = model::syncGarmin,
                    onDisconnectGarmin = model::disconnectGarmin,
                    onRefresh = model::refresh,
                    onImport = { source ->
                        importSource = source
                        picker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/vnd.ms-excel", "text/plain", "application/octet-stream"))
                    },
                    onClearImports = model::clearImportedMetrics,
                )
            }
        }
    }

    private fun readCsv(context: android.content.Context, uri: Uri): String {
        val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "Файлът не може да бъде отворен." }
        return input.use { stream ->
            InputStreamReader(stream, StandardCharsets.UTF_8).buffered().use { reader ->
                val contents = StringBuilder()
                val buffer = CharArray(8_192)
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    require(contents.length + read <= 10_000_000) { "CSV файлът е прекалено голям (максимум 10 MB текст)." }
                    contents.append(buffer, 0, read)
                }
                contents.toString()
            }
        }
    }
}
