package com.fitlens.ai

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Message
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import com.fitlens.ai.data.*
import com.fitlens.ai.ui.FitLensTheme
import org.json.JSONTokener

/** Manual website sign-in and read-only, explicit preview. No credential bridge or cookie export. */
class UtmbProfileActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLanguage.wrap(base))

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profileUrl = intent.getStringExtra("profile_url").orEmpty()
        val expected = runCatching { utmbProfileSlug(profileUrl) }.getOrNull() ?: run { finish(); return }
        setContent {
            FitLensTheme {
                Surface(Modifier.fillMaxSize()) {
                    var host by remember { mutableStateOf("") }
                    var currentUrl by remember { mutableStateOf("") }
                    var loaded by remember { mutableStateOf(false) }
                    var failed by remember { mutableStateOf(false) }
                    var progress by remember { mutableIntStateOf(0) }
                    var message by remember { mutableStateOf("") }
                    var reading by remember { mutableStateOf(false) }
                    var preview by remember { mutableStateOf<UtmbBrowserPreview?>(null) }
                    var checked by remember { mutableStateOf(false) }
                    var popup by remember { mutableStateOf<WebView?>(null) }
                    var help by remember { mutableStateOf(false) }
                    var thirdPartyCookies by remember { mutableStateOf(false) }
                    var pageFinished by remember { mutableIntStateOf(0) }
                    var copied by remember { mutableStateOf(false) }
                    val diagnostics = remember { UtmbWebDiagnostics() }
                    val web = remember {
                        WebView(this@UtmbProfileActivity).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                            settings.javaScriptEnabled = true
                            settings.setSupportMultipleWindows(true)
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.safeBrowsingEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                            val parent = this
                            webChromeClient = object : WebChromeClient() {
                                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                                    if (!isUserGesture || popup != null) {
                                        diagnostics.record(UtmbWebEvent.POPUP_BLOCKED, view.url.orEmpty())
                                        message = getString(R.string.utmb_popup_blocked); return false
                                    }
                                    val child = WebView(this@UtmbProfileActivity).apply {
                                        layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                                        settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                                        settings.setSupportMultipleWindows(true)
                                        settings.allowFileAccess = false; settings.allowContentAccess = false
                                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                        settings.safeBrowsingEnabled = true
                                        settings.useWideViewPort = true; settings.loadWithOverviewMode = true
                                        webViewClient = parent.webViewClient; webChromeClient = parent.webChromeClient
                                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, thirdPartyCookies)
                                    }
                                    diagnostics.record(UtmbWebEvent.POPUP, view.url.orEmpty())
                                    popup = child
                                    (resultMsg.obj as WebView.WebViewTransport).webView = child
                                    resultMsg.sendToTarget(); return true
                                }
                                override fun onCloseWindow(window: WebView) {
                                    if (window === popup) {
                                        popup = null; window.destroy()
                                        currentUrl = parent.url.orEmpty(); host = Uri.parse(currentUrl).host.orEmpty()
                                        loaded = isUtmbBrowserProfile(currentUrl, expected)
                                    }
                                }
                                override fun onConsoleMessage(event: ConsoleMessage): Boolean {
                                    if (event.messageLevel() == ConsoleMessage.MessageLevel.ERROR)
                                        diagnostics.record(UtmbWebEvent.JS_ERROR, event.sourceId().orEmpty())
                                    return true
                                }
                                override fun onProgressChanged(view: WebView, value: Int) { progress = value }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    if (!request.isForMainFrame) return false
                                    val uri = request.url
                                    val allowed = uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1,443)
                                    if (!allowed) {
                                        diagnostics.record(UtmbWebEvent.BLOCKED, uri.toString())
                                        message = getString(R.string.utmb_web_navigation)
                                    }
                                    return !allowed
                                }
                                override fun onPageStarted(view: WebView, url: String?, icon: android.graphics.Bitmap?) {
                                    failed = false; loaded = false; preview = null; reading = false; currentUrl = url.orEmpty()
                                    host = Uri.parse(url.orEmpty()).host.orEmpty()
                                    diagnostics.record(UtmbWebEvent.START, url.orEmpty())
                                }
                                override fun onPageFinished(view: WebView, url: String?) {
                                    currentUrl = url.orEmpty(); loaded = !failed && isUtmbBrowserProfile(currentUrl, expected)
                                    diagnostics.record(UtmbWebEvent.FINISH, url.orEmpty())
                                    pageFinished++
                                    CookieManager.getInstance().flush()
                                }
                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                    diagnostics.record(UtmbWebEvent.NETWORK, request.url.toString(), error.errorCode)
                                    if (request.isForMainFrame) { failed = true; loaded = false; message = getString(R.string.utmb_web_failed) }
                                }
                                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                    diagnostics.record(UtmbWebEvent.HTTP, request.url.toString(), response.statusCode)
                                    if (request.isForMainFrame) { failed = true; loaded = false; message = getString(R.string.utmb_web_failed) + " HTTP ${response.statusCode}" }
                                }
                                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                                    diagnostics.record(UtmbWebEvent.TLS, error.url.orEmpty())
                                    handler.cancel(); failed = true; loaded = false; message = getString(R.string.utmb_web_failed)
                                }
                            }
                        }
                    }
                    DisposableEffect(web) { onDispose { popup?.stopLoading(); popup?.destroy(); web.stopLoading(); web.destroy() } }
                    LaunchedEffect(web) { web.loadUrl(profileUrl) }
                    LaunchedEffect(pageFinished) {
                        if (pageFinished == 0) return@LaunchedEffect
                        delay(8000)
                        val active = popup ?: web
                        val before = active.url.orEmpty()
                        active.evaluateJavascript("(function(){return Boolean(document.body && document.body.innerText.trim().length<20 && document.querySelectorAll('button,input[type=submit],a').length===0)})()") { blank ->
                            if (blank == "true" && active.url == before) {
                                diagnostics.record(UtmbWebEvent.BLANK, before)
                                message = getString(R.string.utmb_blank_help)
                            }
                        }
                    }
                    BackHandler { if (web.canGoBack()) web.goBack() else finish() }
                    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.utmb_web_title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { finish() }) { Text(stringResource(R.string.mfp_close)) }
                        }
                        Text("HTTPS · $host", style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.utmb_web_help), style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { if (web.canGoBack()) web.goBack() }) { Text(stringResource(R.string.utmb_web_back)) }
                            TextButton(onClick = { message = ""; web.loadUrl(profileUrl) }) { Text(stringResource(R.string.utmb_web_profile)) }
                            TextButton(onClick = { help = true }) { Text(stringResource(R.string.utmb_help)) }
                        }
                        if (progress < 100) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        AndroidView(factory = { web }, modifier = Modifier.fillMaxWidth().weight(1f))
                        Button(enabled = loaded && popup == null && !reading, modifier = Modifier.fillMaxWidth(), onClick = {
                            val page = web.url.orEmpty()
                            if (!isUtmbBrowserProfile(page, expected)) return@Button
                            reading = true; message = ""
                            web.evaluateJavascript(UTMB_BROWSER_SCRIPT) { raw ->
                                reading = false
                                if (web.url != page || !isUtmbBrowserProfile(web.url.orEmpty(), expected)) {
                                    message = getString(R.string.utmb_web_wrong); return@evaluateJavascript
                                }
                                val json = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
                                runCatching { parseUtmbBrowserSnapshot(json.orEmpty(), page, expected) }
                                    .onSuccess { preview = it; checked = false }
                                    .onFailure { message = getString(R.string.utmb_web_empty) }
                            }
                        }) { Text(stringResource(R.string.utmb_web_read)) }
                    }
                    popup?.let { child ->
                        Dialog(onDismissRequest = {
                            popup = null; child.stopLoading(); child.destroy()
                            currentUrl = web.url.orEmpty(); host = Uri.parse(currentUrl).host.orEmpty()
                            loaded = isUtmbBrowserProfile(currentUrl, expected)
                        }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                            Surface(Modifier.fillMaxSize()) {
                                Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(12.dp)) {
                                    Text("HTTPS · $host", style = MaterialTheme.typography.labelSmall)
                                    Row {
                                        TextButton(onClick = { help = true }) { Text(stringResource(R.string.utmb_help)) }
                                        TextButton(onClick = {
                                            popup = null; child.stopLoading(); child.destroy()
                                            currentUrl = web.url.orEmpty(); host = Uri.parse(currentUrl).host.orEmpty()
                                            loaded = isUtmbBrowserProfile(currentUrl, expected)
                                        }) { Text(stringResource(R.string.mfp_close)) }
                                    }
                                    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
                                    if (progress < 100) LinearProgressIndicator(Modifier.fillMaxWidth())
                                    AndroidView(factory = { child }, modifier = Modifier.fillMaxWidth().weight(1f))
                                }
                            }
                        }
                    }
                    if (help) AlertDialog(onDismissRequest = { help = false },
                        title = { Text(stringResource(R.string.utmb_help)) },
                        text = {
                            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                                Text(stringResource(R.string.utmb_help_text))
                                Row {
                                    Checkbox(thirdPartyCookies, { enabled ->
                                        thirdPartyCookies = enabled
                                        CookieManager.getInstance().setAcceptThirdPartyCookies(web, enabled)
                                        popup?.let { CookieManager.getInstance().setAcceptThirdPartyCookies(it, enabled) }
                                        diagnostics.record(UtmbWebEvent.COOKIES, (popup ?: web).url.orEmpty(), if (enabled) 1 else 0)
                                    })
                                    Text(stringResource(R.string.utmb_login_cookies))
                                }
                                TextButton(onClick = { help = false; message = ""; (popup ?: web).reload() }) { Text(stringResource(R.string.utmb_reload)) }
                                TextButton(onClick = {
                                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl))) }
                                        .onFailure { message = getString(R.string.utmb_web_failed) }
                                }) { Text(stringResource(R.string.utmb_external_browser)) }
                                TextButton(onClick = {
                                    val report = diagnostics.report(WebView.getCurrentWebViewPackage()?.versionName.orEmpty(), thirdPartyCookies)
                                    val clip = android.content.ClipData.newPlainText("UTMB diagnostics", report)
                                    (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                                    copied = true
                                }) { Text(stringResource(if (copied) R.string.gp_copied else R.string.utmb_copy_diagnostics)) }
                            }
                        }, confirmButton = { TextButton(onClick = { help = false }) { Text(stringResource(R.string.mfp_close)) } })
                    preview?.let { data ->
                        val previous = RunnerProfileStore(this@UtmbProfileActivity).all().first { it.provider == "UTMB" }
                        val sameRunner = runCatching { utmbProfileSlug(previous.url).substringBefore('.') == expected.substringBefore('.') }.getOrDefault(false)
                        val previewScore = mergeUtmbCategoryScore(previous.score.takeIf { sameRunner }.orEmpty(), data.score).orEmpty()
                        val previewResults = data.results ?: previous.results.takeIf { sameRunner }.orEmpty()
                        AlertDialog(onDismissRequest = { preview = null }, title = { Text(stringResource(R.string.utmb_web_preview)) },
                            text = {
                                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                                    Text(getString(R.string.utmb_web_counts, data.categories, data.rows))
                                    Text(previewScore)
                                    Text(previewResults)
                                    Text(stringResource(R.string.utmb_web_review))
                                    Row { Checkbox(checked, { checked = it }); Text(stringResource(R.string.utmb_web_confirm)) }
                                }
                            }, confirmButton = {
                                TextButton(enabled = checked, onClick = {
                                    runCatching {
                                        val store = RunnerProfileStore(this@UtmbProfileActivity)
                                        store.save(RunnerProfile("UTMB", profileUrl, previewScore, previewResults,
                                            source = getString(R.string.utmb_web_source)))
                                    }.onSuccess { setResult(RESULT_OK); finish() }
                                        .onFailure { message = getString(R.string.mfp_save_failed); preview = null }
                                }) { Text(stringResource(R.string.ui_044)) }
                            }, dismissButton = { TextButton(onClick = { preview = null }) { Text(stringResource(R.string.mfp_close)) } })
                    }
                }
            }
        }
    }
}
