package com.totoro.assistant.service

import android.Manifest
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
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Слухач wake-word «торо».
 *
 * Повністю відмовостійкий: якщо SR недоступний — повертає control без exception.
 * Якщо немає RECORD_AUDIO — теж мовчки виходить.
 *
 * Шляхи:
 *   1) Porcupine (offline) — коли prefs.usePorcupine && accessKey не blank
 *   2) Android SpeechRecognizer — fallback
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

    fun start() {
        if (running.getAndSet(true)) return
        Log.i(TAG, "start() usePorcupine=$usePorcupine")

        val audioGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!audioGranted) {
            Log.w(TAG, "No RECORD_AUDIO permission, wake listener disabled")
            running.set(false)
            return
        }

        if (usePorcupine && picovoiceKey.isNotBlank()) {
            startPorcupine()
        } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
            startSpeechRecognizer()
        } else {
            Log.w(TAG, "SpeechRecognizer unavailable and no Porcupine key — nothing to listen to")
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

    // ────────────── SpeechRecognizer: постійне слухання ──────────────
    private fun startSpeechRecognizer() {
        thread = thread(name = "totoro-wake-sr", isDaemon = true) {
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
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                val started = Object()
                var triggered = false
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onPartialResults(partial: Bundle?) {
                        if (!running.get()) return
                        val list = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
                        list.firstOrNull()?.let { text ->
                            val low = text.lowercase()
                            if (low.contains("торо") && low.length > 4) {
                                triggered = true
                                val cmd = text.substringAfter("торо", "").trim().ifBlank { text }
                                onWake(cmd)
                                try { sr.stopListening() } catch (_: Throwable) {}
                            }
                        }
                    }
                    override fun onResults(r: Bundle?) {
                        if (!running.get()) return
                        val list = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
                        if (!triggered) {
                            list.firstOrNull()?.let { text ->
                                if (text.lowercase().contains("торо")) {
                                    onWake(text)
                                }
                            }
                        }
                        synchronized(started) { started.notifyAll() }
                    }
                    override fun onError(e: Int) {
                        Log.w(TAG, "SR onError=$e")
                        synchronized(started) { started.notifyAll() }
                    }
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
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
                Thread.sleep(200)
            }
        }
    }

    // ────────────── Porcupine (offline wake-word) ──────────────
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

                val buf = ShortArray(frameLength)
                while (running.get()) {
                    val read = record.read(buf, 0, frameLength)
                    if (read == frameLength) {
                        val kw = porcupine.process(buf)
                        if (kw >= 0) onWake("Торо")
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Porcupine failed — falling back to SR", e)
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
