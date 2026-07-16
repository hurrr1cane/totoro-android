package com.totoro.assistant.diagnostics

import android.content.Context
import android.util.Log
import com.totoro.assistant.diagnostics.LogStore
import com.totoro.assistant.prefs.TotoroPrefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Надсилає логи (старт служби, crash, помилки) у Hermes endpoint.
 * Використовується CrashHandler-ом та під час старту, щоб ми знали що відбувається.
 *
 * URL за замовчуванням — http://10.0.2.2:8765/totoro (стандарт для емулятора),
 * але в налаштуваннях додатку можна вказати свій (наприклад,
 * ваш cloudflared-тунель).
 */
object HermesReporter {
    private const val TAG = "TotoroReport"
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    /** Надіслати короткий лог. Не блокує UI. Завжди повертається одразу. */
    fun report(ctx: Context, level: String, tag: String, message: String, extra: String? = null) {
        LogStore.append(level, tag, "$message${extra?.let { " :: $it" } ?: ""}")
        Thread {
            try {
                val prefs = TotoroPrefs(ctx)
                val url = prefs.hermesUrl.trim().ifBlank {
                    // Якщо користувач не налаштував — пишемо лише в logcat
                    Log.println(priorityFor(level), tag, "[no-hermes] $message${extra?.let { " :: $it" } ?: ""}")
                    return@Thread
                }
                val body = JSONObject().apply {
                    put("source", "totoro-tablet")
                    put("level", level)
                    put("tag", tag)
                    put("message", message)
                    if (!extra.isNullOrBlank()) put("extra", extra)
                    put("ts", System.currentTimeMillis())
                }.toString().toRequestBody(JSON)

                val req = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("User-Agent", "TotoroAndroid/0.1.1")
                    .build()
                try { client.newCall(req).execute().close() } catch (_: Throwable) {}
            } catch (e: Throwable) {
                Log.e(TAG, "report failure", e)
            }
        }.start()
    }

    private fun priorityFor(level: String): Int = when (level) {
        "ERROR" -> Log.ERROR
        "WARN" -> Log.WARN
        else -> Log.INFO
    }
}
