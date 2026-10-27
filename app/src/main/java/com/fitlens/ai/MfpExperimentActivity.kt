package com.fitlens.ai

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fitlens.ai.data.*
import com.fitlens.ai.ui.FitLensTheme
import org.json.JSONTokener
import java.time.LocalDate

/** Isolated manual experiment. No native HTTP calls or JS bridge receives login credentials. */
class MfpExperimentActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLanguage.wrap(base))

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitLensTheme {
                var date by remember { mutableStateOf(LocalDate.now().toString()) }
                var username by remember { mutableStateOf("") }
                var status by remember { mutableStateOf("") }
                var origin by remember { mutableStateOf("www.myfitnesspal.com") }
                var loaded by remember { mutableStateOf(false) }
                var preview by remember { mutableStateOf<MfpDiaryPreview?>(null) }
                var confirmed by remember { mutableStateOf(false) }
                val web = remember {
                    WebView(this).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.setSupportMultipleWindows(false)
                        settings.saveFormData = false
                        settings.safeBrowsingEnabled = true
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (!request.isForMainFrame) return false
                                val allowed = isMfpPage(request.url.toString())
                                if (!allowed) status = getString(R.string.mfp_external_blocked)
                                return !allowed
                            }
                            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                loaded = false; preview = null
                                if (url != null && url != "about:blank" && !isMfpPage(url)) {
                                    view.stopLoading(); status = getString(R.string.mfp_external_blocked); return
                                }
                                origin = url?.let { android.net.Uri.parse(it).host }.orEmpty()
                            }
                            override fun onPageFinished(view: WebView, url: String?) { loaded = isMfpPage(url.orEmpty()) }
                        }
                    }
                }
                DisposableEffect(web) { onDispose { web.stopLoading(); web.destroy() } }
                Column(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.mfp_experiment), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.mfp_experiment_note), style = MaterialTheme.typography.bodySmall)
                    Text("HTTPS · $origin", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(date, { date = it.take(10); preview = null }, label = { Text(stringResource(R.string.mfp_date)) }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(username, { username = it.take(100); preview = null }, label = { Text(stringResource(R.string.mfp_username)) }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { preview = null; web.loadUrl("https://www.myfitnesspal.com/account/login") }) { Text(stringResource(R.string.mfp_login)) }
                        TextButton(onClick = {
                            if (runCatching { LocalDate.parse(date) }.isFailure || !username.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) {
                                status = getString(R.string.mfp_invalid_input)
                            } else {
                                preview = null; status = ""
                                web.loadUrl("https://www.myfitnesspal.com/food/diary/$username?date=$date")
                            }
                        }) { Text(stringResource(R.string.mfp_open_diary)) }
                        TextButton(onClick = {
                            web.stopLoading(); web.loadUrl("about:blank")
                            CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
                            WebStorage.getInstance().deleteAllData(); web.clearCache(true); web.clearHistory()
                            getSharedPreferences("mfp_experiment", MODE_PRIVATE).edit().clear().apply()
                            preview = null; status = getString(R.string.mfp_session_cleared)
                        }) { Text(stringResource(R.string.mfp_clear_session)) }
                    }
                    Button(onClick = {
                        val page = web.url.orEmpty(); val requestedDate = date
                        if (!isMfpDiaryForDate(page, requestedDate)) { status = getString(R.string.mfp_wrong_page); return@Button }
                        status = ""
                        // Captures only a diary table, never document HTML, scripts, cookies or storage.
                        web.evaluateJavascript("""(function(){ const row=document.querySelector('tr.meal_header'); const t=row ? row.closest('table') : null; return t && t.outerHTML.length <= 250000 ? t.outerHTML : ''; })()""") { result ->
                            if (web.url != page || date != requestedDate) { status = getString(R.string.mfp_wrong_page); return@evaluateJavascript }
                            val html = runCatching { JSONTokener(result).nextValue() as? String }.getOrNull().orEmpty()
                            runCatching { parseMfpDiary(html, page, requestedDate) }.onSuccess { preview = it; confirmed = false }
                                .onFailure { status = getString(R.string.mfp_read_failed) }
                        }
                    }, enabled = loaded, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mfp_read_day)) }
                    if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
                    AndroidView(factory = { web }, modifier = Modifier.fillMaxWidth().weight(1f))
                    TextButton(onClick = { finish() }) { Text(stringResource(R.string.mfp_close)) }
                }
                preview?.let { data ->
                    AlertDialog(onDismissRequest = { preview = null }, title = { Text(stringResource(R.string.mfp_preview)) },
                        text = {
                            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(data.date)
                                Text(stringResource(R.string.mfp_verify_date))
                                Row { Checkbox(confirmed, { confirmed = it }); Text(stringResource(R.string.mfp_confirm_date)) }
                                Text(stringResource(R.string.mfp_preview_note))
                                data.totals.forEach { (key, value) -> Text("$key: $value") }
                                data.foods.forEach { food ->
                                    HorizontalDivider(); Text("${food.meal} · ${food.name}")
                                    food.nutrients.forEach { (key, value) -> Text("$key: $value") }
                                }
                            }
                        }, confirmButton = {
                            TextButton(enabled = confirmed, onClick = {
                                val rows = org.json.JSONArray().apply {
                                    data.foods.forEach { f -> put(org.json.JSONObject().put("meal", f.meal).put("food", f.name).put("nutrients", org.json.JSONObject(f.nutrients))) }
                                }
                                val snapshot = org.json.JSONObject().put("date", data.date).put("totals", org.json.JSONObject(data.totals)).put("foods", rows)
                                val saved = getSharedPreferences("mfp_experiment", MODE_PRIVATE).edit().putString("last_preview", snapshot.toString()).commit()
                                if (saved) preview = null
                                status = getString(if (saved) R.string.mfp_saved else R.string.mfp_save_failed)
                            }) { Text(stringResource(R.string.mfp_save_preview)) }
                        }, dismissButton = { TextButton(onClick = { preview = null }) { Text(stringResource(R.string.mfp_close)) } })
                }
            }
        }
    }
}
