package com.fitlens.ai.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object AppLanguage {
    fun selection(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(android.app.LocaleManager::class.java).applicationLocales.toLanguageTags()
    } else context.getSharedPreferences("fitlens_language", 0).getString("language", "").orEmpty()

    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val tag = selection(context)
        val locale = if (tag.isBlank()) android.content.res.Resources.getSystem().configuration.locales[0] else Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(LocaleList(locale)) })
    }

    fun select(context: Context, tag: String) {
        require(tag in listOf("", "bg", "en"))
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(android.app.LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            check(context.getSharedPreferences("fitlens_language", 0).edit().putString("language", tag).commit())
            var current = context
            while (current is android.content.ContextWrapper) {
                if (current is android.app.Activity) { current.recreate(); break }
                current = current.baseContext
            }
        }
    }
}
