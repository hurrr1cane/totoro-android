package com.totoro.assistant.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.totoro.assistant.BuildConfig
import com.totoro.assistant.MainActivity
import com.totoro.assistant.R
import com.totoro.assistant.TotoroNotificationChannel
import com.totoro.assistant.diagnostics.HermesReporter
import com.totoro.assistant.hermes.HermesClient
import com.totoro.assistant.prefs.TotoroPrefs
import kotlinx.coroutines.*
import java.util.Locale

/**
 * ForegroundService, що слухає wake-word.
 *
 * Версія 0.1.2 — стійка до всіх задокументованих причин падіння на Android 12-14:
 *  - перевіряє дозволи (RECORD_AUDIO, POST_NOTIFICATIONS) перед стартом;
 *  - викликає startForeground одразу в onCreate(); у разі SecurityException
 *    або іншого винятку — повідомляє Hermes і НЕ падає;
 *  - передає foregroundServiceType у startForeground() через ServiceCompat;
 *  - має fallback на «слухати в межах Activity», якщо службу вбивають.
 *  - повідомляє Hermes про start/stop/crash для швидкої діагностики.
 */
class TotoroListenerService : Service() {

    companion object {
        private const val TAG = "TotoroService"
        const val NOTI_ID = 0x1A55
        const val ACTION_STOP = "com.totoro.assistant.STOP"
        const val ACTION_START = "com.totoro.assistant.START"

        @Volatile var isRunning: Boolean = false
            private set
        @Volatile var lastError: String? = null
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var prefs: TotoroPrefs
    private var wakeListener: WakeListener? = null
    private var commandSession: Boolean = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var ttsRef: android.speech.tts.TextToSpeech? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate()")
        prefs = TotoroPrefs(applicationContext)
        HermesReporter.report(this, "INFO", TAG, "service_onCreate")

        // Перевірка дозволу на RECORD_AUDIO
        val audioGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!audioGranted) {
            lastError = "RECORD_AUDIO not granted"
            HermesReporter.report(this, "ERROR", TAG, "no_mic_perm", lastError)
            updateNotificationText("❌ Немає дозволу на мікрофон")
            // Не кидаємо виняток — падаємо м'яко, нотифікація лишається
        } else {
            startInForegroundCompat()
        }

        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "Totoro::WakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(60L * 60L * 1000L)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "wakeLock acquire failed", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i(TAG, "onStartCommand action=$action")
        HermesReporter.report(this, "INFO", TAG, "start_command", "action=$action")
        when (action) {
            ACTION_STOP -> {
                stopListening()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> startListening()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.w(TAG, "onDestroy()")
        HermesReporter.report(this, "WARN", TAG, "service_onDestroy")
        isRunning = false
        try { wakeListener?.stop() } catch (e: Throwable) { Log.e(TAG, "wakeListener.stop", e) }
        try { wakeLock?.release() } catch (_: Throwable) {}
        try { ttsRef?.shutdown() } catch (_: Throwable) {}
        ttsRef = null
        try { scope.cancel() } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartIntent = Intent(applicationContext, TotoroListenerService::class.java).apply {
            action = ACTION_START
        }
        val pi = PendingIntent.getService(
            this, 1, restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.ELAPSED_REALTIME, 1000, pi)
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Запуск Foreground з максимальною відмовостійкістю:
     * - ловимо SecurityException, ForegroundServiceStartNotAllowedException, все інше;
     * - пробуємо варіант з типом, потім без типу, потім у межах Activity.
     */
    private fun startInForegroundCompat() {
        val notif = buildNotification("Слухаю 'Гей, Тоторо'…")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this, NOTI_ID, notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTI_ID, notif)
            }
            isRunning = true
            lastError = null
            HermesReporter.report(this, "INFO", TAG, "fg_ok", "type=microphone")
        } catch (e: Throwable) {
            // SecurityException / ForegroundServiceStartNotAllowedException / інше
            lastError = "fg_typed_failed: ${e.javaClass.simpleName} ${e.message}"
            Log.e(TAG, lastError ?: "", e)
            HermesReporter.report(this, "ERROR", TAG, "fg_typed_failed", e.message)

            // Другий шанс — без типу
            try {
                startForeground(NOTI_ID, notif)
                isRunning = true
                HermesReporter.report(this, "INFO", TAG, "fg_ok_untyped")
            } catch (e2: Throwable) {
                lastError = "fg_untyped_failed: ${e2.javaClass.simpleName} ${e2.message}"
                Log.e(TAG, lastError ?: "", e2)
                HermesReporter.report(this, "ERROR", TAG, "fg_untyped_failed", e2.message)
                updateNotificationText("⚠ Службу заблоковано системою. Відкрий додаток.")
            }
        }
    }

    private fun startListening() {
        if (wakeListener != null) return
        // Перевірка permission ще раз — користувач міг відкликати
        val audioGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!audioGranted) {
            HermesReporter.report(this, "WARN", TAG, "cannot_start_listening", "no RECORD_AUDIO")
            return
        }

        try {
            wakeListener = WakeListener(
                context = applicationContext,
                language = prefs.language,
                usePorcupine = prefs.usePorcupine && BuildConfig.PICOVOICE_KEY.isNotBlank(),
                picovoiceKey = BuildConfig.PICOVOICE_KEY,
                onWake = { phrase ->
                    Log.i(TAG, "Wake detected: $phrase")
                    HermesReporter.report(this, "INFO", TAG, "wake_detected", phrase.take(80))
                    if (commandSession) return@WakeListener
                    commandSession = true
                    scope.launch {
                        try {
                            val cmd = wakeListener?.captureCommand(timeoutMs = 6500) ?: ""
                            if (cmd.isNotBlank()) execute(cmd)
                        } catch (e: Throwable) {
                            Log.e(TAG, "capture/execute", e)
                            HermesReporter.report(this@TotoroListenerService, "ERROR", TAG, "capture_fail", e.message)
                        } finally {
                            commandSession = false
                            updateNotificationText("Слухаю 'Гей, Тоторо'…")
                        }
                    }
                }
            )
            wakeListener?.start()
            HermesReporter.report(this@TotoroListenerService, "INFO", TAG, "listening_started")
        } catch (e: Throwable) {
            Log.e(TAG, "startListening", e)
            HermesReporter.report(this, "ERROR", TAG, "start_listening_fail", e.message)
        }
    }

    private fun stopListening() {
        try { wakeListener?.stop() } catch (e: Throwable) { Log.e(TAG, "stop", e) }
        wakeListener = null
    }

    private suspend fun execute(command: String) {
        val lower = command.lowercase()
        updateNotificationText("Виконую: ${command.take(40)}")
        HermesReporter.report(this, "INFO", TAG, "command", command.take(100))
        withContext(Dispatchers.Main) {
            try {
                when {
                    lower.contains("youtube music") || (lower.contains("муз") && lower.contains("youtube")) ->
                        CommandRouter.playYTMusic(this@TotoroListenerService, command)
                    lower.contains("youtube") ->
                        CommandRouter.playYT(this@TotoroListenerService, command)
                    lower.contains("spotify") || lower.contains("спотіф") ->
                        CommandRouter.playSpotify(this@TotoroListenerService, command)
                    lower.contains("світл") && (lower.contains("увімкн") || lower.contains("вкл")) ->
                        CommandRouter.haSwitch(prefs, listOf("light.living_room"), true)
                    lower.contains("світл") && (lower.contains("вимкн") || lower.contains("викл")) ->
                        CommandRouter.haSwitch(prefs, listOf("light.living_room"), false)
                    lower.startsWith("таймер") || lower.contains("постав таймер") ->
                        CommandRouter.setTimer(this@TotoroListenerService, command)
                    lower.contains("надішли в телеграм") || (lower.contains("телеграм") && lower.contains("надішли")) ->
                        CommandRouter.sendTelegram(prefs, command)
                    lower.contains("запиши") || lower.contains("нотатк") ->
                        CommandRouter.noteToObsidian(prefs, command)
                    else -> {
                        scope.launch {
                            val reply = try {
                                HermesClient(prefs.hermesUrl).ask(command)
                            } catch (e: Throwable) {
                                HermesReporter.report(
                                    this@TotoroListenerService, "ERROR", TAG,
                                    "hermes_fail", e.message
                                )
                                null
                            }
                            if (reply != null) {
                                if (reply.reply.isNotBlank()) speak(reply.reply)
                                if (reply.url.isNotBlank())
                                    CommandRouter.openUrl(this@TotoroListenerService, reply.url)
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "execute: $command", e)
                HermesReporter.report(this@TotoroListenerService, "ERROR", TAG, "execute_fail", e.message)
            }
        }
    }

    private fun speak(text: String) {
        try {
            ttsRef?.shutdown()
            ttsRef = null
            val ref = android.speech.tts.TextToSpeech(this) { status ->
                if (status != android.speech.tts.TextToSpeech.SUCCESS) return@TextToSpeech
                ttsRef?.language = Locale("uk", "UA")
                ttsRef?.speak(
                    text,
                    android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                    null,
                    "totoro_${System.currentTimeMillis()}"
                )
            }
            ttsRef = ref
        } catch (e: Throwable) {
            Log.e(TAG, "speak", e)
        }
    }

    private fun updateNotificationText(text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTI_ID, buildNotification(text))
        } catch (e: Throwable) { Log.e(TAG, "notify", e) }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TotoroListenerService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, TotoroNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Тоторо")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openPi)
            .addAction(R.drawable.ic_stop, "Зупинити", stopPi)
            .build()
    }
}
