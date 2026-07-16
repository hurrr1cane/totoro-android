package com.totoro.assistant.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.totoro.assistant.prefs.TotoroPrefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Виконавець команд: intent-launch, HA, Telegram, Obsidian, таймери. */
object CommandRouter {

    private val main = Handler(Looper.getMainLooper())

    fun playYT(ctx: Context, raw: String) {
        val q = extractQuery(raw, listOf("увімкни", "запусти", "постав", "ютуб", "youtube", "торо"))
        val uri = if (q.isBlank()) Uri.parse("vnd.youtube://")
                 else Uri.parse("vnd.youtube://" + Uri.encode(q))
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun playYTMusic(ctx: Context, raw: String) {
        val q = extractQuery(raw, listOf("увімкни", "запусти", "постав", "ютуб", "муз", "youtube", "music", "торо"))
        val pkg = "com.google.android.apps.youtube.music"
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=" + Uri.encode(q)))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    fun playSpotify(ctx: Context, raw: String) {
        val q = extractQuery(raw, listOf("увімкни", "запусти", "постав", "spotify", "спотіф", "торо"))
        val pkg = "com.spotify.music"
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/" + Uri.encode(q)))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    fun openUrl(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }

    /** Home Assistant перемикач (світло, розетка, штори). */
    fun haSwitch(prefs: TotoroPrefs, entityIds: List<String>, on: Boolean) {
        if (prefs.haToken.isBlank()) return
        val domain = entityIds.first().substringBefore(".")
        val svc = "${domain}/${if (on) "turn_on" else "turn_off"}"
        val url = prefs.haUrl.trimEnd('/') + "/api/services/" + svc
        val body = JSONArray().put(JSONObject().put("entity_id", entityIds))
            .toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(url)
            .post(body)
            .header("Authorization", "Bearer " + prefs.haToken)
            .build()
        OkHttpClient().newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
        })
    }

    fun setTimer(ctx: Context, raw: String) {
        // Парсимо «на N хвилин»
        val m = Regex("(\\d+)\\s*(хвилин|хв|мін|годин|секунд)?").find(raw)
        val n = m?.groupValues?.get(1)?.toIntOrNull() ?: 5
        val unit = m?.groupValues?.get(2) ?: "хвилин"
        val millis = when {
            unit.startsWith("годин") -> n.toLong() * 3_600_000
            unit.startsWith("секунд") -> n.toLong() * 1_000
            else -> n.toLong() * 60_000
        }
        // Простий AlarmManager через WorkManager або Handler
        main.postDelayed({
            Toast.makeText(ctx, "⏰ Таймер вийшов!", Toast.LENGTH_LONG).show()
            try {
                val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                vibrator?.vibrate(500)
            } catch (_: Exception) {}
        }, millis)
    }

    fun sendTelegram(prefs: TotoroPrefs, raw: String) {
        if (prefs.telegramToken.isBlank() || prefs.telegramChat.isBlank()) return
        val msg = raw.replace(Regex("(надішли|відправ)\\s+в\\s+телеграм:?\\s*"), "")
        val url = "https://api.telegram.org/bot${prefs.telegramToken}/sendMessage"
        val body = JSONObject().put("chat_id", prefs.telegramChat).put("text", "🌳 $msg").toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).post(body).build()
        OkHttpClient().newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
        })
    }

    fun noteToObsidian(prefs: TotoroPrefs, raw: String) {
        if (prefs.obsidianUrl.isBlank()) return
        val body = JSONObject().put("text", raw).put("source", "totoro-tablet")
            .toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(prefs.obsidianUrl).post(body).build()
        OkHttpClient().newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
        })
    }

    private fun extractQuery(text: String, drop: List<String>): String {
        var t = text
        drop.forEach { t = t.replace(Regex(it, RegexOption.IGNORE_CASE), "") }
        return t.replace(Regex("\\s+"), " ").trim()
    }
}
