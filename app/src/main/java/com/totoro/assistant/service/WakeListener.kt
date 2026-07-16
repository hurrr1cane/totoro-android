package com.totoro.assistant.service

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.totoro.assistant.R
import com.totoro.assistant.TotoroNotificationChannel
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Слухач wake-word «торо» (або «тоторо») + capture команди.
 *
 * Гнучка стратегія розпізнавання wake:
 *  • приймаємо як «торо», так і «тоторо», «тору», тощо;
 *  • реагуємо на onResults І на onPartialResults, щоб не залежати від
 *    того, чи увімкнений EXTRA_PARTIAL_RESULTS на пристрої;
 *  • коли wake розпізнано — відправляємо wake-повідомлення, щоб користувач бачив, що нас почули.
 */
class WakeListener(
    private val context: Context,
    private val language: String,
    private val usePorcupine: Boolean,
    private val picovoiceKey: String,
    private val onWake: (String) -> Unit
) {
    companion object { private const val TAG = "TotoroWake" }

    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    /** Співпадає з «тоторо», «торо», «тору» тощо — будь-який варіант wake. */
    private val WAKE_RE = Regex("[а-яіїєґa-z]*тор[а-яіїєґa-z]+|торо|тоторо|таро", RegexOption.IGNORE_CASE)

    fun start() {
        if (running.getAndSet(true)) return
        Log.i(TAG, "start() usePorcupine=$usePorcupine language=$language")

        val audioGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!audioGranted) {
            Log.w(TAG, "No RECORD_AUDIO permission, wake listener disabled")
            notify("Немає дозволу на мікрофон")
            running.set(false)
            return
        }

        if (usePorcupine && picovoiceKey.isNotBlank()) {
            startPorcupine()
        } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
            startSpeechRecognizer()
        } else {
            Log.w(TAG, "SpeechRecognizer unavailable and no Porcupine key")
            notify("SR недоступний - встановіть Google")
            running.set(false)
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        thread?.interrupt()
        thread = null
    }

    fun captureCommand(timeoutMs: Long = 6500): String? {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) return null
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return null

        val sr = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Throwable) {
            Log.w(TAG, "createSpeechRecognizer for capture failed", e)
            return null
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        var result: String? = null
        val done = Object()
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) {
                val list = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                result = list?.firstOrNull()
                synchronized(done) { done.notifyAll() }
                try { sr.destroy() } catch (_: Throwable) {}
            }
            override fun onError(e: Int) {
                Log.w(TAG, "capture SR onError=$e")
                synchronized(done) { done.notifyAll() }
                try { sr.destroy() } catch (_: Throwable) {}
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partial: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try { sr.startListening(intent) } catch (e: Throwable) {
            Log.w(TAG, "startListening for capture failed", e)
            try { sr.destroy() } catch (_: Throwable) {}
            return null
        }
        return synchronized(done) {
            try { done.wait(timeoutMs) } catch (_: InterruptedException) {}
            result
        }
    }

    private fun startSpeechRecognizer() {
        thread = thread(name = "totoro-wake-sr", isDaemon = true) {
            Log.i(TAG, "wake thread started")
            while (running.get()) {
                val sr = try {
                    SpeechRecognizer.createSpeechRecognizer(context)
                } catch (e: Throwable) {
                    Log.e(TAG, "createSpeechRecognizer failed", e)
                    return@thread
                }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                }
                val started = Object()
                var sessionTriggered = false
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "SR ready")
                    }
                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "SR speech start")
                    }
                    override fun onPartialResults(partial: Bundle?) {
                        if (!running.get()) return
                        val list = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
                        val text = list.firstOrNull() ?: return
                        Log.d(TAG, "SR partial: $text")
                        if (!sessionTriggered && looksLikeWake(text)) {
                            sessionTriggered = true
                            val cmd = extractCommand(text)
                            Log.i(TAG, "Wake by partial: $text -> '$cmd'")
                            notify("Чую: $text")
                            onWake(cmd)
                            try { sr.stopListening() } catch (_: Throwable) {}
                        }
                    }
                    override fun onResults(r: Bundle?) {
                        if (!running.get()) return
                        val list = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
                        val text = list.firstOrNull() ?: ""
                        Log.i(TAG, "SR final: '$text' (triggered=$sessionTriggered)")
                        if (!sessionTriggered && looksLikeWake(text)) {
                            val cmd = extractCommand(text)
                            Log.i(TAG, "Wake by final: $text -> '$cmd'")
                            notify("Чую: $text")
                            onWake(cmd)
                        }
                        synchronized(started) { started.notifyAll() }
                    }
                    override fun onError(e: Int) {
                        Log.w(TAG, "SR onError=$e")
                        synchronized(started) { started.notifyAll() }
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                try { sr.startListening(intent) } catch (e: Throwable) {
                    Log.w(TAG, "startListening failed", e)
                    try { sr.destroy() } catch (_: Throwable) {}
                    Thread.sleep(2000)
                    continue
                }
                synchronized(started) {
                    try { started.wait(8_000) } catch (_: InterruptedException) { return@thread }
                }
                try { sr.destroy() } catch (_: Throwable) {}
                if (!running.get()) return@thread
                Thread.sleep(300)
            }
        }
    }

    private fun extractCommand(text: String): String {
        val cleaned = text
            .replace(Regex("^(гей|окей|hey|ok|okay)\\s+", RegexOption.IGNORE_CASE), "")
        val m = WAKE_RE.find(cleaned)
        if (m == null) return ""
        val after = cleaned.substring(m.range.last + 1)
            .replace(Regex("^[,.:\\-\\s]+", RegexOption.IGNORE_CASE), "")
            .trim()
        return after.replace(Regex("\\bбудь\\s*-?\\s*ласка\\b", RegexOption.IGNORE_CASE), "").trim()
    }

    private fun looksLikeWake(text: String): Boolean {
        val low = text.lowercase()
        return when {
            WAKE_RE.containsMatchIn(low) -> true
            low.contains("toro") -> true
            low.contains("торо") || low.contains("таро") -> true
            else -> false
        }
    }

    private fun notify(text: String) {
        try {
            val notif = NotificationCompat.Builder(context, TotoroNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentTitle("Тоторо")
                .setContentText(text)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.notify(System.currentTimeMillis().toInt() and 0x7fffffff, notif)
        } catch (_: Throwable) {}
    }

    private fun startPorcupine() {
        thread = thread(name = "totoro-wake-pv", isDaemon = true) {
            var record: AudioRecord? = null
            var porcupine: ai.picovoice.porcupine.Porcupine? = null
            try {
                porcupine = ai.picovoice.porcupine.Porcupine.Builder()
                    .setAccessKey(picovoiceKey)
                    .setKeywordPaths(arrayOf("totoro_android.ppn"))
                    .setSensitivity(0.7f)
                    .build(context)

                val sampleRate = porcupine.sampleRate
                val frameLength = porcupine.frameLength
                val bufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(frameLength * 2)

                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord not initialized")
                    return@thread
                }
                record.startRecording()
                Log.i(TAG, "Porcupine listening")

                val buf = ShortArray(frameLength)
                while (running.get()) {
                    val read = record.read(buf, 0, frameLength)
                    if (read == frameLength) {
                        val kw = porcupine.process(buf)
                        if (kw >= 0) {
                            Log.i(TAG, "Porcupine wake detected")
                            onWake("Торо")
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Porcupine failed", e)
            } finally {
                try { record?.stop() } catch (_: Throwable) {}
                try { record?.release() } catch (_: Throwable) {}
                try { porcupine?.delete() } catch (_: Throwable) {}
                if (running.get() && SpeechRecognizer.isRecognitionAvailable(context)) {
                    startSpeechRecognizer()
                }
            }
        }
    }
}
