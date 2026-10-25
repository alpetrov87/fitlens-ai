package com.fitlens.ai.data

import android.content.Context
import java.net.URI

internal fun isChatGptProjectUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme == "https" && uri.host == "chatgpt.com" && uri.userInfo == null && uri.port == -1 &&
        uri.rawQuery == null && uri.rawFragment == null &&
        Regex("/g/g-p-[A-Za-z0-9-]+/project/?").matches(uri.path) && value.length <= 500
}.getOrDefault(false)

class ChatGptProjectStore(context: Context) {
    private val preferences = context.getSharedPreferences("fitlens_chatgpt", Context.MODE_PRIVATE)
    fun load(): String = preferences.getString("project_url", "").orEmpty()
    fun save(value: String) {
        val url = value.trim()
        require(url.isEmpty() || isChatGptProjectUrl(url)) { "Постави HTTPS линк към ChatGPT проект: https://chatgpt.com/g/g-p-…/project" }
        check(preferences.edit().putString("project_url", url).commit()) { "Линкът не можа да бъде запазен." }
    }
}
