package com.fitlens.ai

import androidx.lifecycle.repeatOnLifecycle

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
import kotlinx.coroutines.*

/** Website sign-in and manual preview; durable diary batches run in MfpSyncWorker. */
class MfpExperimentActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLanguage.wrap(base))

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitLensTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val manager = remember { androidx.work.WorkManager.getInstance(this) }
                val preferences = remember { getSharedPreferences("mfp_experiment", MODE_PRIVATE) }
                var syncing by remember { mutableStateOf(false) }
                var batchProgress by remember { mutableStateOf("") }
                var historyVisible by remember { mutableStateOf(false) }
                var historyText by remember { mutableStateOf("") }
                var batchConfirm by remember { mutableStateOf(false) }
                val historyStore = remember { MfpHistoryStore(this) }
                var date by remember { mutableStateOf(LocalDate.now().toString()) }
                var username by remember { mutableStateOf(preferences.getString("username", historyStore.account()).orEmpty()) }
                var status by remember { mutableStateOf("") }
                var origin by remember { mutableStateOf("www.myfitnesspal.com") }
                var loaded by remember { mutableStateOf(false) }
                var loading by remember { mutableStateOf(false) }
                var failed by remember { mutableStateOf(false) }
                var progress by remember { mutableIntStateOf(0) }
                var preview by remember { mutableStateOf<MfpDiaryPreview?>(null) }
                var confirmed by remember { mutableStateOf(false) }
                var controlsVisible by remember { mutableStateOf(true) }
                var scrollTest by remember { mutableStateOf(false) }
                LaunchedEffect(manager) {
                    lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                        while (true) {
                            val info = withContext(Dispatchers.IO) { manager.getWorkInfosForUniqueWork(MfpSyncWorker.NAME).get().maxByOrNull { item -> item.tags.firstOrNull { it.startsWith("mfp-start:") }?.substringAfter(':')?.toLongOrNull() ?: 0L } }
                            syncing = info != null && !info.state.isFinished
                            if (info != null) {
                                val done = info.progress.getInt("done", info.outputData.getInt("done", 0))
                                val total = info.progress.getInt("total", info.outputData.getInt("total", 30))
                                batchProgress = getString(R.string.mfp_sync_progress, done, total, mfpWorkState(this@MfpExperimentActivity, info.state))
                                if (info.state == androidx.work.WorkInfo.State.FAILED) status = getString(R.string.mfp_background_failed, info.outputData.getString("error").orEmpty())
                            }
                            delay(1000)
                        }
                    }
                }
                val web = remember {
                    WebView(this).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                        isNestedScrollingEnabled = true
                        isVerticalScrollBarEnabled = true
                        isHorizontalScrollBarEnabled = true
                        // Preserve native WebView handling; only prevent the Compose parent taking its gesture.
                        setOnTouchListener { view, event ->
                            when (event.actionMasked) {
                                android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)
                                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.setSupportMultipleWindows(false)
                        settings.saveFormData = false
                        settings.safeBrowsingEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (!request.isForMainFrame) return false
                                val allowed = isMfpPage(request.url.toString())
                                if (!allowed) { status = getString(R.string.mfp_external_blocked); loading = false }
                                return !allowed
                            }
                            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                loaded = false; preview = null; loading = url != "about:blank"; failed = false; progress = 0
                                if (url != null && url != "about:blank" && !(scrollTest && url.startsWith("data:text/html")) && !isMfpPage(url)) {
                                    view.stopLoading(); loading = false; failed = true
                                    status = getString(R.string.mfp_external_blocked); return
                                }
                                origin = url?.let { android.net.Uri.parse(it).host }.orEmpty()
                            }
                            override fun onPageFinished(view: WebView, url: String?) {
                                loading = false; loaded = !failed && isMfpPage(url.orEmpty())
                                CookieManager.getInstance().flush()
                            }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) {
                                    failed = true; loading = false; loaded = false
                                    status = getString(R.string.mfp_network_error, error.errorCode)
                                }
                            }
                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                if (request.isForMainFrame) {
                                    failed = true; loading = false; loaded = false
                                    status = getString(R.string.mfp_http_error, response.statusCode)
                                }
                            }
                            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                                handler.cancel()
                                failed = true; loading = false; loaded = false
                                status = getString(R.string.mfp_tls_error)
                            }
                        }
                    }
                }
                DisposableEffect(web) { onDispose { web.stopLoading(); web.destroy() } }
                LaunchedEffect(web) {
                    if (username.isNotBlank()) web.loadUrl("https://www.myfitnesspal.com/food/diary/$username?date=$date")
                    else web.loadUrl("https://www.myfitnesspal.com/account/login")
                }
                LaunchedEffect(loading) {
                    if (loading) {
                        kotlinx.coroutines.delay(20_000)
                        if (loading && status.isBlank()) status = getString(R.string.mfp_loading_slow)
                    }
                }
                Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(stringResource(R.string.mfp_experiment), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { finish() }) { Text(stringResource(R.string.mfp_close)) }
                    }
                    Text(if (scrollTest) stringResource(R.string.mfp_scroll_test_local) else "HTTPS · $origin", style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(R.string.mfp_selected_day, date), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !controlsVisible, onClick = { controlsVisible = false },
                            label = { Text(stringResource(R.string.mfp_website_tab)) })
                        FilterChip(selected = controlsVisible, onClick = { controlsVisible = true },
                            label = { Text(stringResource(R.string.mfp_controls_tab)) })
                    }
                    if (loading) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 88.dp).verticalScroll(rememberScrollState()))
                    if (batchProgress.isNotBlank()) Text(batchProgress, style = MaterialTheme.typography.bodySmall)
                    if (syncing) {
                        OutlinedButton(onClick = { manager.cancelUniqueWork(MfpSyncWorker.NAME) }) { Text(stringResource(R.string.mfp_batch_stop)) }
                    }
                    if (syncing) {
                        Column(Modifier.fillMaxWidth().weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(stringResource(R.string.mfp_tab_help))
                        }
                    } else if (controlsVisible) {
                        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = {
                                historyText = historyStore.report(language = java.util.Locale.getDefault().language)
                                historyVisible = true
                            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mfp_history)) }
                            Text(stringResource(R.string.mfp_controls_help), style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(date, { date = it.take(10); preview = null }, label = { Text(stringResource(R.string.mfp_date)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(username, { username = it.take(100); preferences.edit().putString("username", username).apply(); preview = null }, label = { Text(stringResource(R.string.mfp_username)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                if (runCatching { LocalDate.parse(date) }.isFailure || !username.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) {
                                    status = getString(R.string.mfp_invalid_input)
                                } else {
                                    preview = null; status = ""; controlsVisible = false; scrollTest = false
                                    web.loadUrl("https://www.myfitnesspal.com/food/diary/$username?date=$date")
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mfp_open_diary)) }
                            OutlinedButton(onClick = {
                                if (!username.matches(Regex("[A-Za-z0-9_.-]{1,100}")) || runCatching { LocalDate.parse(date) }.isFailure) status = getString(R.string.mfp_invalid_input)
                                else batchConfirm = true
                            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mfp_batch_start)) }
                            OutlinedButton(onClick = {
                                status = ""; preview = null; controlsVisible = false; scrollTest = false
                                web.loadUrl("https://www.myfitnesspal.com/account/login")
                            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mfp_login)) }
                            OutlinedButton(onClick = {
                                status = ""; preview = null; controlsVisible = false
                                val current = if (scrollTest) "" else web.url.orEmpty()
                                scrollTest = false
                                web.loadUrl(if (isMfpPage(current)) current else "https://www.myfitnesspal.com/account/login")
                            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mfp_retry_loading)) }
                            OutlinedButton(onClick = {
                                web.stopLoading(); preview = null; loaded = false; status = ""; scrollTest = true; controlsVisible = false
                                val heading = getString(R.string.mfp_scroll_test_local)
                                val bottom = getString(R.string.mfp_scroll_test_bottom)
                                val rows = (1..25).joinToString("") { "<p style='padding:36px;border-bottom:1px solid #355b35'>${it}</p>" }
                                val html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body style='font:24px sans-serif;background:#faf6e9;color:#1b3023'><h2>$heading</h2>$rows<h2>$bottom</h2></body></html>"
                                web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mfp_scroll_test_button)) }
                            TextButton(onClick = {
                                manager.cancelUniqueWork(MfpSyncWorker.NAME)
                                scrollTest = false
                                username = ""
                                web.stopLoading(); web.loadUrl("about:blank")
                                CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
                                WebStorage.getInstance().deleteAllData(); web.clearCache(true); web.clearHistory()
                                getSharedPreferences("mfp_experiment", MODE_PRIVATE).edit().clear().apply()
                                historyStore.clear()
                                preview = null; status = getString(R.string.mfp_session_cleared)
                            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mfp_clear_session)) }
                            Text(stringResource(R.string.mfp_experiment_note), style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        AndroidView(factory = { web }, modifier = Modifier.fillMaxWidth().weight(1f))
                        Button(onClick = {
                        val page = web.url.orEmpty(); val requestedDate = date
                        if (!isMfpDiaryPage(page)) { status = getString(R.string.mfp_wrong_page); return@Button }
                        status = ""
                        // Captures only a diary table, never document HTML, scripts, cookies or storage.
                        web.evaluateJavascript(MFP_DIARY_SCRIPT) { result ->
                            if (web.url != page || date != requestedDate) { status = getString(R.string.mfp_wrong_page); return@evaluateJavascript }
                            val html = runCatching { JSONTokener(result).nextValue() as? String }.getOrNull().orEmpty()
                            runCatching { parseMfpDiary(html, page, requestedDate) }.onSuccess { preview = it; confirmed = false }
                                .onFailure {
                                    val count = org.jsoup.Jsoup.parse(html).select("tr").size
                                    status = if (it is MfpDiaryDateMismatch) getString(R.string.mfp_date_mismatch, requestedDate)
                                        else getString(R.string.mfp_read_failed_details, html.length, count)
                                }
                        }
                    }, enabled = loaded && !syncing, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mfp_read_day)) }
                    }
                }
                if (historyVisible) AlertDialog(onDismissRequest = { historyVisible = false },
                    title = { Text(stringResource(R.string.mfp_history)) },
                    text = { Text(historyText.ifBlank { getString(R.string.mfp_history_empty) }, modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) },
                    confirmButton = { TextButton(onClick = { historyVisible = false }) { Text(stringResource(R.string.mfp_close)) } })
                if (batchConfirm) AlertDialog(onDismissRequest = { batchConfirm = false },
                    title = { Text(stringResource(R.string.mfp_batch_start)) },
                    text = { Text(stringResource(R.string.mfp_batch_consent)) },
                    confirmButton = { TextButton(onClick = {
                        batchConfirm = false
                        val account = username
                        val last = LocalDate.parse(date)
                        controlsVisible = false; scrollTest = false; status = ""
                        preferences.edit().putString("username", account).apply()
                        val request = androidx.work.OneTimeWorkRequestBuilder<MfpSyncWorker>()
                            .addTag("mfp-start:${System.currentTimeMillis()}")
                            .setInputData(androidx.work.workDataOf("account" to account, "end" to last.toString(), "agent" to web.settings.userAgentString, "generation" to historyStore.generation()))
                            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        manager.enqueueUniqueWork(MfpSyncWorker.NAME, androidx.work.ExistingWorkPolicy.KEEP, request)
                        controlsVisible = true
                        syncing = true

                    }) { Text(stringResource(R.string.mfp_batch_start)) } },
                    dismissButton = { TextButton(onClick = { batchConfirm = false }) { Text(stringResource(R.string.mfp_close)) } })
                preview?.let { data ->
                    var expandedMeal by remember(data) { mutableStateOf<String?>(null) }
                    AlertDialog(onDismissRequest = { preview = null }, title = { Text(stringResource(R.string.mfp_preview)) },
                        text = {
                            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(data.date)
                                Text(stringResource(R.string.mfp_meal_counts, data.foods.map { it.meal }.distinct().size, data.foods.size))
                                if (data.foods.isEmpty()) Text(stringResource(R.string.mfp_no_food_rows))
                                data.totals.forEach { (key, value) -> Text("$key: $value") }
                                data.foods.groupBy { it.meal }.forEach { (meal, foods) ->
                                    HorizontalDivider()
                                    TextButton(onClick = { expandedMeal = if (expandedMeal == meal) null else meal }, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.mfp_meal_group, meal, foods.size) + if (expandedMeal == meal) " −" else " +")
                                    }
                                    if (expandedMeal == meal) foods.forEach { food ->
                                        Text(food.name, style = MaterialTheme.typography.titleSmall)
                                        food.nutrients.forEach { (key, value) -> Text("$key: $value") }
                                    }
                                }
                                Text(stringResource(R.string.mfp_preview_note))
                                Text(stringResource(R.string.mfp_verify_date))
                                Row { Checkbox(confirmed, { confirmed = it }); Text(stringResource(R.string.mfp_confirm_date)) }
                            }
                        }, confirmButton = {
                            TextButton(enabled = confirmed, onClick = {
                                val saved = historyStore.save(username, data)
                                if (saved) preview = null
                                status = getString(if (saved) R.string.mfp_saved else R.string.mfp_save_failed)
                            }) { Text(stringResource(R.string.mfp_save_preview)) }
                        }, dismissButton = { TextButton(onClick = { preview = null }) { Text(stringResource(R.string.mfp_close)) } })
                }
                }
            }
        }
    }
}

private val MFP_DIARY_SCRIPT = """(function(){
                            const tables=Array.from(document.querySelectorAll('table'));
                            const candidates=new Set();
                            document.querySelectorAll('tr.meal_header, tr.meal-header').forEach(row => { const t=row.closest('table'); if(t) candidates.add(t); });
                            tables.forEach(t => {
                                const rows=Array.from(t.querySelectorAll('tr')).filter(r => r.closest('table') === t);
                                const text=rows.map(r => r.innerText).join(' ');
                                const heading=rows.some(r => {
                                    const c=Array.from(r.children).filter(x => x.tagName === 'TD' || x.tagName === 'TH');
                                    return c.length && (/^(breakfast|lunch|dinner|snacks|закуска|обяд|вечеря)$/i.test(c[0].innerText.trim()) || c.length === 1 && (c[0].colSpan > 1 || c[0].querySelector('h1,h2,h3,h4')));
                                });
                                const numeric=rows.some(r => Array.from(r.children).filter(c => /[0-9]/.test(c.innerText)).length >= 2);
                                const total=rows.some(r => r.classList.contains('total') && !r.classList.contains('alt') && !r.classList.contains('remaining'));
                                if(t.id === 'diary-table' || total || heading && numeric || /calories/i.test(text) && /breakfast|lunch|dinner|snacks|totals/i.test(text)) candidates.add(t);
                            });
                            if(!candidates.size) return '';
                            const clean=document.createElement('div');
                            const dateField=document.querySelector('input#date_selector[name="hidden_date_selector"]');
                            if(dateField && /^\d{4}-\d{2}-\d{2}$/.test(dateField.value)) clean.setAttribute('data-diary-date',dateField.value);
                            for(const table of tables.filter(t => candidates.has(t))) {
                                const output=document.createElement('table');
                                for(const row of table.querySelectorAll('tr')) {
                                    if(row.closest('table') !== table) continue;
                                    const copy=document.createElement('tr');
                                    copy.className=row.className;
                                    const cells=Array.from(row.children).filter(c => c.tagName === 'TD' || c.tagName === 'TH');
                                    if(cells.length === 1 && (cells[0].colSpan > 1 || cells[0].querySelector('h1,h2,h3,h4'))) copy.setAttribute('data-meal-heading','true');
                                    for(const cell of cells) {
                                        const c=document.createElement(cell.tagName.toLowerCase());
                                        const value=cell.querySelector('.macro-value');
                                        const text=cell.cloneNode(true);
                                        text.querySelectorAll('.macro-percentage').forEach(part => part.remove());
                                        c.textContent=value ? value.textContent.trim() : (cell.querySelector('.macro-percentage') ? text.textContent.trim() : cell.innerText.trim());
                                        copy.appendChild(c);
                                    }
                                    if(copy.children.length) output.appendChild(copy);
                                }
                                if(output.children.length) clean.appendChild(output);
                            }
                            return clean.outerHTML.length <= 250000 ? clean.outerHTML : '';
                        })()"""
