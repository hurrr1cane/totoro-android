package com.totoro.assistant.prefs

import android.content.Context
import android.content.SharedPreferences
import com.totoro.assistant.BuildConfig

/**
 * Налаштування додатку. Безкоштовно, локально, без хмар.
 * Picovoice AccessKey (ppn) вшивається через local.properties; якщо немає —
 * додаток працює у SpeechRecognizer fallback (російською/англійською/українською).
 */
class TotoroPrefs(ctx: Context) {
    private val sp: SharedPreferences =
        ctx.getSharedPreferences("totoro_prefs", Context.MODE_PRIVATE)

    var hermesUrl: String
        get() = sp.getString("hermes_url", "") ?: ""
        set(v) { sp.edit().putString("hermes_url", v).apply() }

    var haUrl: String
        get() = sp.getString("ha_url", "http://localhost:8123") ?: "http://localhost:8123"
        set(v) { sp.edit().putString("ha_url", v).apply() }

    var haToken: String
        get() = sp.getString("ha_token", "") ?: ""
        set(v) { sp.edit().putString("ha_token", v).apply() }

    var telegramToken: String
        get() = sp.getString("tg_token", "") ?: ""
        set(v) { sp.edit().putString("tg_token", v).apply() }

    var telegramChat: String
        get() = sp.getString("tg_chat", "") ?: ""
        set(v) { sp.edit().putString("tg_chat", v).apply() }

    var obsidianUrl: String
        get() = sp.getString("obsidian_url", "") ?: ""
        set(v) { sp.edit().putString("obsidian_url", v).apply() }

    var language: String
        get() = sp.getString("lang", "uk-UA") ?: "uk-UA"
        set(v) { sp.edit().putString("lang", v).apply() }

    var usePorcupine: Boolean
        get() = sp.getBoolean("use_porcupine", false) && BuildConfig.PICOVOICE_KEY.isNotBlank()
        set(v) { sp.edit().putBoolean("use_porcupine", v).apply() }

    // Команди та справи
    var todos: Set<String>
        get() = sp.getStringSet("todos", emptySet()) ?: emptySet()
        set(v) { sp.edit().putStringSet("todos", v).apply() }
}
