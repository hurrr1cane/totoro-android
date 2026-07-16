package com.totoro.assistant.hermes

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Клієнт до Hermes (або сумісний webhook). Використовується коли wake-word
 * розпізнано і треба виконати розумну команду. Повертає JSON:
 *  { "reply": "...", "action": "...", "speak": "...", "url": "..." }
 */
class HermesClient(private val endpoint: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun ask(text: String, source: String = "totoro-tablet"): HermesReply {
        if (endpoint.isBlank()) return HermesReply(reply = "Hermes endpoint не налаштовано.")
        val body = JSONObject().apply {
            put("text", text)
            put("source", source)
            put("ts", System.currentTimeMillis())
        }.toString().toRequestBody(JSON)

        val req = Request.Builder()
            .url(endpoint)
            .post(body)
            .header("User-Agent", "Totoro/0.1 Android")
            .build()

        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return HermesReply(reply = "Hermes: ${resp.code}")
                val s = resp.body?.string().orEmpty()
                val j = JSONObject(s)
                HermesReply(
                    reply = j.optString("reply", ""),
                    action = j.optString("action", ""),
                    speak = j.optString("speak", ""),
                    url = j.optString("url", "")
                )
            }
        } catch (e: Exception) {
            HermesReply(reply = "Hermes недоступний: ${e.message}")
        }
    }
}

data class HermesReply(
    val reply: String = "",
    val action: String = "",
    val speak: String = "",
    val url: String = ""
)
