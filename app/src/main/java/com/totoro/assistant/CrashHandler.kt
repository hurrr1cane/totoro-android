package com.totoro.assistant

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Глобальний crash handler. Не замінює системний, а дублює stacktrace у файл,
 * який можна потім витягнути через Hermes / logcat / шторку.
 */
object CrashHandler {
    private const val TAG = "TotoroCrash"

    @Volatile private var installed = false
    private var logFile: File? = null

    fun install(ctx: Context) {
        if (installed) return
        installed = true

        logFile = File(ctx.filesDir, "crash.log")

        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(thread, throwable)
            } catch (e: Throwable) {
                Log.e(TAG, "crash handler self-failure", e)
            }
            // Передати ланцюг далі — системний handler покаже ANR/crash dialog
            prev?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(thread: Thread, t: Throwable) {
        val f = logFile ?: return
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        val sb = StringBuilder()
        sb.append("--- ").append(sdf.format(Date())).append(" ---\n")
        sb.append("Thread: ").append(thread.name).append('\n')
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        sb.append("SDK: ").append(Build.VERSION.SDK_INT).append('\n')
        sb.append(sw.toString()).append("\n\n")
        try { f.appendText(sb.toString()) } catch (_: Throwable) {}

        Log.e(TAG, "CRASH: ${t.javaClass.simpleName} — ${t.message}", t)
    }
}
