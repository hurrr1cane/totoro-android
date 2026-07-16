package com.totoro.assistant.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf

/**
 * Live-лог подій для UI. Singleton — бо HermesReporter не має UI-контексту.
 *
 * MutableStateListOf вимагає додавання з UI-потоку. Усі інші потоки
 * (WakeListener тощо) кличуть info/warn/event/error — вони пересилають
 * подію на main через post().
 */
object LiveLog {
    val events = mutableStateListOf<String>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun postAdd(line: String) {
        // Якщо ми вже на main потоці — додаємо напряму, інакше — пост
        if (Looper.myLooper() == Looper.getMainLooper()) {
            doAdd(line)
        } else {
            mainHandler.post { doAdd(line) }
        }
    }

    private fun doAdd(line: String) {
        try {
            events.add(line)
            if (events.size > 200) events.removeAt(0)
        } catch (_: Throwable) {
            // mutableStateListOf може кинути якщо виклик поза Composition;
            // ігноруємо, бо лог не критичний
        }
    }

    fun info(tag: String, msg: String)  { postAdd("🔵 [${ts()}] $tag: $msg") }
    fun warn(tag: String, msg: String)  { postAdd("🟡 [${ts()}] $tag: $msg") }
    fun error(tag: String, msg: String) { postAdd("🔴 [${ts()}] $tag: $msg") }
    fun event(tag: String, msg: String) { postAdd("🟢 [${ts()}] $tag: $msg") }

    private fun ts(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        return sdf.format(java.util.Date())
    }
}
