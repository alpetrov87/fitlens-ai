package com.fitlens.ai.data

import android.content.Context

/** Application resource context; never retains an Activity. Also used by non-Compose callbacks. */
object LocalizedText {
    private lateinit var context: Context
    fun initialize(context: Context) { this.context = context.applicationContext }
    fun nutritionReport(): String = MfpHistoryStore(context).report(language = java.util.Locale.getDefault().language)
    fun format(id: Int, vararg args: Any?): String = String.format(java.util.Locale.getDefault(), get(id), *args)
    fun get(id: Int): String = context.createConfigurationContext(
        android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.getDefault()) }
    ).getString(id)
}
