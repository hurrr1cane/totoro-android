package com.totoro.assistant.ui

import androidx.compose.runtime.mutableStateListOf

/**
 * Live-лог подій для UI. Singleton — бо HermesReporter не має UI-контексту.
 *
 * UI читає `events` через Compose (mutableStateListOf → реактивно).
 * Будь-який фрагмент коду може писати через info/warn/event/error.
 */
object LiveLog {
    val events = mutableStateListOf<String>()

    fun add(line: String) {
        synchronized(this) {
            events.add(line)
            if (events.size > 200) events.removeAt(0)
        }
    }

    fun info(tag: String, msg: String)  { add("🔵 [${ts()}] $tag: $msg") }
    fun warn(tag: String, msg: String)  { add("🟡 [${ts()}] $tag: $msg") }
    fun error(tag: String, msg: String) { add("🔴 [${ts()}] $tag: $msg") }
    fun event(tag: String, msg: String) { add("🟢 [${ts()}] $tag: $msg") }

    private fun ts(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        return sdf.format(java.util.Date())
    }
}
