package com.totoro.assistant.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Локальне сховище логів. Додаток пише у ring-buffer-style файл,
 * MainActivity / Settings показує його вміст.
 *
 * HermesReporter.report(...) тепер дзеркалить виклики сюди.
 */
object LogStore {
    private const val TAG = "TotoroLog"
    private const val MAX_BYTES = 200 * 1024  // 200 КБ
    private val SDF = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Volatile private var file: File? = null

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "totoro.log")
    }

    fun append(level: String, tag: String, msg: String) {
        try {
            val f = file ?: return
            val line = "${SDF.format(Date())} [$level/$tag] $msg\n"
            synchronized(this) {
                f.appendText(line)
                if (f.length() > MAX_BYTES) {
                    // Ротація: залишити останню половину
                    val all = f.readText()
                    val tail = all.takeLast(MAX_BYTES / 2)
                    f.writeText("-- rotated --\n$tail")
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "append failed", e)
        }
    }

    fun readAll(): String = try {
        file?.readText() ?: "(no log)"
    } catch (e: Throwable) {
        "(error: ${e.message})"
    }

    fun path(): String? = file?.absolutePath

    fun share(ctx: Context) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, readAll())
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Totoro logs")
        }
        ctx.startActivity(android.content.Intent.createChooser(intent, "Поділитися логом"))
    }

    fun clear() {
        try { file?.writeText("") } catch (_: Throwable) {}
    }
}
