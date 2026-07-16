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
import android.os.Handler
import android.os.Looper
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
 * Слухач wake-word «торо» + capture команди.
 *
 * Версія 0.1.6 — обов'язково викликає SR API з main-потоку,
 * що вирішує ERROR_CLIENT (5) на Android 10+.
 */
class WakeListener(
    private val context: Context,
    private val language: String,
    private val usePorcupine: Boolean,
    private val picovoiceKey: String,
    private val onWake: (String) -> Unit
) {
    companion object {
        private const val TAG = "TotoroWake"

        @JvmStatic
        fun errorName(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO (recording problem)"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT (other client error)"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH (no speech detected)"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT (no input)"
            else -> "UNKNOWN_ERROR_$code"
        }
    }

    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val WAKE_RE = Regex("[а-яіїєґa-z]*тор[а-яіїєґa-z]+|торо|тоторо|таро", RegexOption.IGNORE_CASE)

    fun start() {
        if (running.getAndSet(true)) return
        Log.i(TAG, "start() usePorcupine=$usePorcupine language=$language")

        val audioGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!audioGranted) {
            Log.w(TAG, "No RECORD_AUDIO permission")
            notify("Немає дозволу на мікрофон")
            running.set(false)
            return
        }

        if (usePorcupine && picovoiceKey.isNotBlank()) {
            startPorcupine()
        } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
            startSpeechRecognizer()
        } else {
            Log.w(TAG, "SpeechRecognizer unavailable")
            notify("SR недоступний — встанови Google Search")
            running.set(false)
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        thread?.interrupt()
        thread = null
    }

    /**
     * Результат captureCommand — або текст, або код помилки (через [errCode]/[errMsg]).
     */
    data class CaptureResult(val text: String? = null, val errCode: Int = 0, val errMsg: String = "")

    fun captureCommand(timeoutMs: Long = 6500): CaptureResult {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) return CaptureResult(errCode = -1, errMsg = "no_RECORD_AUDIO")
        if (!SpeechRecognizer.isRecognitionAvailable(context))
            return CaptureResult(errCode = -2, errMsg = "SR_unavailable")

        val sr = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Throwable) {
            Log.w(TAG, "createSpeechRecognizer for capture failed", e)
            return CaptureResult(errCode = -3, errMsg = "createSR_failed: ${e.message}")
        }
        if (sr == null) return CaptureResult(errCode = -3, errMsg = "createSR_returned_null")

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        var resultText: String? = null
        var resultError: Int = 0
        var resultErrorMsg = ""
        val done = Object()

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.i(TAG, "capture ready: params=$params")
            }
            override fun onBeginningOfSpeech() {
                Log.i(TAG, "capture beginningOfSpeech")
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                Log.i(TAG, "capture endOfSpeech")
            }
            override fun onPartialResults(partial: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onResults(r: Bundle?) {
                val list = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                resultText = list?.firstOrNull()
                Log.i(TAG, "capture results: '$resultText'")
                synchronized(done) { done.notifyAll() }
                try { sr.destroy() } catch (_: Throwable) {}
            }
            override fun onError(e: Int) {
                resultError = e
                resultErrorMsg = errorName(e)
                Log.w(TAG, "capture onError=$e (${errorName(e)})")
                synchronized(done) { done.notifyAll() }
                try { sr.destroy() } catch (_: Throwable) {}
            }
        })

        // startListening має викликатися з main-потоку (Android 10+)
        val started = AtomicBoolean(false)
        val startError = arrayOf<Throwable?>(null)
        mainHandler.post {
            try {
                sr.startListening(intent)
            } catch (t: Throwable) {
                startError[0] = t
                Log.e(TAG, "startListening exception", t)
                synchronized(done) { done.notifyAll() }
            } finally {
                started.set(true)
            }
        }
        synchronized(done) {
            try {
                done.wait(timeoutMs)
            } catch (_: InterruptedException) {
                return CaptureResult(errCode = -4, errMsg = "interrupted")
            }
        }
        // Якщо startListening ще навіть не викликали — почекай трохи
        var wait = 0
        while (!started.get() && wait < 50) {
            try { Thread.sleep(20) } catch (_: InterruptedException) { break }
            wait++
        }
        try { sr.destroy() } catch (_: Throwable) {}

        return when {
            startError[0] != null -> CaptureResult(errCode = -5, errMsg = "startListening: ${startError[0]!!.message}")
            resultText != null -> CaptureResult(text = resultText)
            resultError != 0 -> CaptureResult(errCode = resultError, errMsg = resultErrorMsg)
            else -> CaptureResult(errCode = -100, errMsg = "timeout (no results, no error)")
        }
    }

    private fun startSpeechRecognizer() {
        thread = thread(name = "totoro-wake-sr", isDaemon = true) {
            Log.i(TAG, "wake thread started")
            while (running.get()) {
                val sr = try { SpeechRecognizer.createSpeechRecognizer(context) }
                catch (e: Throwable) { Log.e(TAG, "createSpeechRecognizer failed", e); return@thread }
                if (sr == null) { Thread.sleep(2000); continue }

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
                        Log.d(TAG, "wake SR ready")
                    }
                    override fun onBeginningOfSpeech() { Log.d(TAG, "wake SR beg") }
                    override fun onPartialResults(partial: Bundle?) {
                        if (!running.get()) return
                        val list = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
                        val text = list.firstOrNull() ?: return
                        Log.d(TAG, "wake partial: $text")
                        if (!sessionTriggered && looksLikeWake(text)) {
                            sessionTriggered = true
                            val cmd = extractCommand(text)
                            Log.i(TAG, "wake by partial: $text -> '$cmd'")
                            notify("Чую: $text")
                            onWake(cmd)
                            try { mainHandler.post { sr.stopListening() } } catch (_: Throwable) {}
                        }
                    }
                    override fun onResults(r: Bundle?) {
                        if (!running.get()) return
                        val list = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
                        val text = list.firstOrNull() ?: ""
                        Log.i(TAG, "wake final: '$text' (triggered=$sessionTriggered)")
                        if (!sessionTriggered && looksLikeWake(text)) {
                            val cmd = extractCommand(text)
                            Log.i(TAG, "wake by final: $text -> '$cmd'")
                            notify("Чую: $text")
                            onWake(cmd)
                        }
                        synchronized(started) { started.notifyAll() }
                    }
                    override fun onError(e: Int) {
                        Log.w(TAG, "wake SR onError=$e (${errorName(e)})")
                        synchronized(started) { started.notifyAll() }
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                // startListening з main-потоку
                var startFailed: Throwable? = null
                val startedFlag = Object()
                mainHandler.post {
                    try { sr.startListening(intent) }
                    catch (t: Throwable) { startFailed = t; Log.e(TAG, "wake startListening exception", t) }
                    synchronized(startedFlag) { startedFlag.notifyAll() }
                }
                synchronized(startedFlag) { try { startedFlag.wait(2000) } catch (_: InterruptedException) {} }
                if (startFailed != null) { try { sr.destroy() } catch (_: Throwable) {}; Thread.sleep(2000); continue }

                synchronized(started) { try { started.wait(8_000) } catch (_: InterruptedException) { return@thread } }
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
                    sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(frameLength * 2)
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize
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
                if (running.get() && SpeechRecognizer.isRecognitionAvailable(context)) startSpeechRecognizer()
            }
        }
    }
}
