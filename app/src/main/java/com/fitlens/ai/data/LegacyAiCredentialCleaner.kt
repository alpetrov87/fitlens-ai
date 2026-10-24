package com.fitlens.ai.data

import android.content.Context
import java.security.KeyStore

/** Removes credentials left by the retired Gemini/OpenAI direct-API integrations. */
object LegacyAiCredentialCleaner {
    fun run(context: Context) {
        listOf("gemini_api_key", "openai_api_key").forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
        }
        runCatching {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            listOf("fitlens_gemini_api_key_v1", "fitlens_openai_api_key_v1").forEach { alias ->
                if (store.containsAlias(alias)) store.deleteEntry(alias)
            }
        }
    }
}
