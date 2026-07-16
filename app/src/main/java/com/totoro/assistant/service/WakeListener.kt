package com.totoro.assistant.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.totoro.assistant.prefs.TotoroPrefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Слухач wake-word + capture-command.
 *
 * Стратегія:
 *   1) За замовчуванням (usePorcupine=false) використовуємо Android SpeechRecognizer
 *      у режимі «trigger word» через «Гей, Тоторо, …». Економно, без інтернету (зазвичай —
 *      Google STT через інтернет).
 *   2) Якщо в prefs увімкнено Porcupine і в BuildConfig.PICOVOICE_KEY є ключ —
 *      використовуємо офлайн Porcupine. Для wake-word треба згенерувати
 *      .ppn файл через console.picovoice.ai (слово "Тоторо") і покласти в assets.
 */
class WakeListener(
    private val context: Context,
    private val language: String,
    private val usePorcupine: Boolean,
    private val picovoiceKey: String,
    private val onWake: (String) -> Unit
) {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start() {
        if (running.getAndSet(true)) return
        if (usePorcupine && picovoiceKey.isNotBlank()) {
            startPorcupine()
        } else {
            startSpeechRecognizer()
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        thread?.interrupt()
        thread = null
    }

    /**
     * Захопити команду після wake. Повертає розпізнаний текст.
     */
    fun captureCommand(timeoutMs: Long = 6500): String? {
        val ctx = context
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return null
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return null

        // createSpeechRecognizer() має викликатися з main-потоку
        val srHolder = arrayOfNulls<SpeechRecognizer>(1)
        val createReady = Object()
        mainHandler.post {
            srHolder[0] = SpeechRecognizer.createSpeechRecognizer(ctx)
            synchronized(createReady) { createReady.notifyAll() }
        }
        synchronized(createReady) {
            try { createReady.wait(2000) } catch (_: InterruptedException) { return null }
        }
        val sr = srHolder[0] ?: return null
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
                sr.destroy()
            }
            override fun onError(e: Int) {
                synchronized(done) { done.notifyAll() }
                sr.destroy()
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partial: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        // startListening() теж з main-потоку
        mainHandler.post { sr.startListening(intent) }
        return synchronized(done) {
            try { done.wait(timeoutMs) } catch (_: InterruptedException) {}
            result
        }
    }

    // ────────────── Speech Recognizer шлях ──────────────
    private fun startSpeechRecognizer() {
        thread = thread(name = "totoro-wake") {
            while (running.get()) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED
                ) return@thread

                // createSpeechRecognizer() — з main-потоку
                val srHolder = arrayOfNulls<SpeechRecognizer>(1)
                val createReady = Object()
                mainHandler.post {
                    srHolder[0] = SpeechRecognizer.createSpeechRecognizer(context)
                    synchronized(createReady) { createReady.notifyAll() }
                }
                synchronized(createReady) {
                    try { createReady.wait(2000) } catch (_: InterruptedException) { return@thread }
                }
                val sr = srHolder[0] ?: continue
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                val started = Object()
                var triggered = false
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onPartialResults(partial: Bundle?) {
                        val list = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
                        list.firstOrNull()?.let { text ->
                            val low = text.lowercase()
                            if (low.contains("торо") && low.length > 4) {
                                triggered = true
                                val cmd = text.substringAfter("торо", "").trim()
                                    .ifBlank { text }
                                onWake(cmd)
                                try { mainHandler.post { sr.stopListening() } } catch (_: Throwable) {}
                            }
                        }
                    }
                    override fun onResults(r: Bundle?) {
                        val list = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
                        if (!triggered) {
                            list.firstOrNull()?.let { text ->
                                if (text.lowercase().contains("торо")) {
                                    onWake(text)
                                }
                            }
                        }
                        synchronized(started) { started.notifyAll() }
                        sr.destroy()
                    }
                    override fun onError(e: Int) {
                        synchronized(started) { started.notifyAll() }
                        sr.destroy()
                    }
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                // startListening() — з main-потоку
                var startErr: Throwable? = null
                val startReady = Object()
                mainHandler.post {
                    try { sr.startListening(intent) }
                    catch (t: Throwable) { startErr = t }
                    synchronized(startReady) { startReady.notifyAll() }
                }
                synchronized(startReady) {
                    try { startReady.wait(2000) } catch (_: InterruptedException) {}
                }
                if (startErr != null) { sr.destroy(); Thread.sleep(2000); continue }
                synchronized(started) {
                    try { started.wait(7_000) } catch (_: InterruptedException) {}
                }
                if (!running.get()) { sr.destroy(); return@thread }
                Thread.sleep(200)
            }
        }
    }

    // ────────────── Porcupine шлях (offline) ──────────────
    private fun startPorcupine() {
        thread = thread(name = "totoro-porcupine") {
            try {
                val porcupine = ai.picovoice.porcupine.Porcupine.Builder()
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

                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (record.state != AudioRecord.STATE_INITIALIZED) return@thread
                record.startRecording()

                val buf = ShortArray(frameLength)
                while (running.get()) {
                    val read = record.read(buf, 0, frameLength)
                    if (read == frameLength) {
                        val keywordIndex = porcupine.process(buf)
                        if (keywordIndex >= 0) onWake("Торо")
                    }
                }
                record.stop(); record.release()
                porcupine.delete()
            } catch (e: Throwable) {
                // Fallback до SpeechRecognizer якщо Porcupine не вдалося
                startSpeechRecognizer()
            }
        }
    }
}
