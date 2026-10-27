package com.fitlens.ai.data

import android.content.Context
import android.webkit.CookieManager
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** No rendered pages, passwords, or persisted copies of session cookies. */
class MfpSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val account = inputData.getString("account").orEmpty()
        val end = runCatching { LocalDate.parse(inputData.getString("end")) }.getOrNull()
        if (!account.matches(Regex("[A-Za-z0-9_.-]{1,100}")) || end == null) return@withContext Result.failure()
        val store = MfpHistoryStore(applicationContext)
        val generation = inputData.getInt("generation", -1)
        if (store.generation() != generation) return@withContext failure("cancelled")
        val checkpoint = applicationContext.getSharedPreferences("mfp_sync", Context.MODE_PRIVATE)
        val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .callTimeout(20, TimeUnit.SECONDS).build()
        try {
            val planKey = "$id:plan"
            val dates = checkpoint.getString(planKey, null)?.let { raw ->
                val array = org.json.JSONArray(raw)
                (0 until array.length()).map { array.getString(it) }
            } ?: mfpSyncDates(end, store.days(account).map { it.date }.toSet(),
                full = !inputData.getBoolean("incremental", false)).also {
                check(checkpoint.edit().putString(planKey, org.json.JSONArray(it).toString()).commit())
            }
            for (offset in checkpoint.getInt(id.toString(), 0) until dates.size) {
                if (isStopped) return@withContext Result.failure()
                val day = dates[offset]
                setProgress(workDataOf("done" to offset, "date" to day, "total" to dates.size))
                val url = "https://www.myfitnesspal.com/food/diary/$account?date=$day"
                val cookie = withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(url) }.orEmpty()
                if (cookie.isBlank()) return@withContext failure("login")
                val request = Request.Builder().url(url).header("Cookie", cookie)
                    .header("User-Agent", inputData.getString("agent") ?: "Mozilla/5.0")
                    .header("Accept", "text/html").build()
                val response = client.newCall(request).execute()
                val html = response.use {
                    if (it.code == 429 || it.code >= 500) return@withContext if (runAttemptCount < 3) Result.retry() else failure("http_${it.code}")
                    if (!it.isSuccessful) return@withContext failure("http_${it.code}")
                    val updatedCookies = it.headers.values("Set-Cookie")
                    if (updatedCookies.isNotEmpty()) withContext(Dispatchers.Main) {
                        val jar = CookieManager.getInstance()
                        if (!isStopped && store.generation() == generation) {
                            updatedCookies.forEach { value -> jar.setCookie(url, value) }
                            jar.flush()
                        }
                    }
                    val body = it.body ?: return@withContext failure("page")
                    // Bound both decompressed bytes and resulting diary table size.
                    val bytes = java.io.ByteArrayOutputStream().use { output ->
                        val stream = body.byteStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= 2_000_000) {
                            val count = stream.read(buffer, 0, minOf(buffer.size, 2_000_001 - output.size()))
                            if (count == -1) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    if (bytes.size > 2_000_000) return@withContext failure("page")
                    String(bytes, Charsets.UTF_8)
                }
                val data = parseMfpDiary(mfpDiaryTables(html), url, day)
                if (isStopped) return@withContext Result.failure()
                check(MfpHistoryStore(applicationContext).save(account, data, inputData.getInt("generation", -1)))
                check(checkpoint.edit().putInt(id.toString(), offset + 1).commit())
                setProgress(workDataOf("done" to offset + 1, "date" to day, "total" to dates.size))
                delay(350)
            }
            Result.success(workDataOf("done" to dates.size, "total" to dates.size))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: java.io.IOException) {
            if (runAttemptCount < 3) Result.retry() else failure("network")
        } catch (_: Exception) { failure("page") }
    }
    private fun failure(reason: String): Result {
        val checkpoint = applicationContext.getSharedPreferences("mfp_sync", Context.MODE_PRIVATE)
        val total = runCatching { org.json.JSONArray(checkpoint.getString("$id:plan", "[]")).length() }.getOrDefault(0)
        return Result.failure(workDataOf("error" to reason, "done" to checkpoint.getInt(id.toString(), 0), "total" to total))
    }
    companion object { const val NAME = "mfp-diary-history" }
}

/** Sanitizes diary text only, preserving headings and selected-date evidence. */
internal fun mfpDiaryTables(html: String): String {
    val document = Jsoup.parse(html)
    val clean = Element("div")
    document.selectFirst("input#date_selector[name=hidden_date_selector]")?.attr("value")
        ?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }?.let { clean.attr("data-diary-date", it) }
    document.select("table").forEach { table ->
        val rows = table.select("tr").filter { it.closest("table") === table }
        val candidate = table.id() == "diary-table" || rows.any { row ->
            val cells = row.children().filter { it.tagName() in listOf("td", "th") }
            row.hasClass("meal_header") || row.hasClass("meal-header") ||
                row.hasClass("total") && !row.hasClass("alt") && !row.hasClass("remaining") ||
                cells.firstOrNull()?.text()?.lowercase(java.util.Locale.ROOT) in listOf("breakfast", "lunch", "dinner", "snack", "snacks", "закуска", "обяд", "вечеря") ||
                cells.size == 1 && (cells[0].attr("colspan").toIntOrNull()?.let { it > 1 } == true || cells[0].select("h1,h2,h3,h4").isNotEmpty())
        }
        if (!candidate) return@forEach
        val output = Element("table")
        rows.forEach { row ->
            val cells = row.children().filter { it.tagName() in listOf("td", "th") }
            val copy = Element("tr").attr("class", row.className())
            if (cells.size == 1 && (cells[0].attr("colspan").toIntOrNull()?.let { it > 1 } == true || cells[0].select("h1,h2,h3,h4").isNotEmpty())) copy.attr("data-meal-heading", "true")
            cells.forEach { copy.appendChild(Element(it.tagName()).text(mfpNutrientCellText(it))) }
            if (cells.isNotEmpty()) output.appendChild(copy)
        }
        if (output.childrenSize() > 0) clean.appendChild(output)
    }
    return clean.outerHtml().also { require(it.length <= 250_000) }
}

internal fun mfpWorkState(context: Context, state: WorkInfo.State): String = context.getString(when (state) {
    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> com.fitlens.ai.R.string.mfp_state_waiting
    WorkInfo.State.RUNNING -> com.fitlens.ai.R.string.mfp_state_running
    WorkInfo.State.SUCCEEDED -> com.fitlens.ai.R.string.mfp_state_done
    WorkInfo.State.FAILED -> com.fitlens.ai.R.string.mfp_state_failed
    WorkInfo.State.CANCELLED -> com.fitlens.ai.R.string.mfp_state_cancelled
})
