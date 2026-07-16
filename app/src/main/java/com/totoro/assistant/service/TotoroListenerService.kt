package com.totoro.assistant.service

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.totoro.assistant.BuildConfig
import com.totoro.assistant.R
import com.totoro.assistant.hermes.HermesClient
import com.totoro.assistant.prefs.TotoroPrefs
import kotlinx.coroutines.*

class TotoroListenerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var prefs: TotoroPrefs
    private var wakeListener: WakeListener? = null
    private var commandSession: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = TotoroPrefs(applicationContext)
        startForeground(NOTI_ID, buildNotification("Слухаю 'Тоторо'…"))
        // На сучасному Android потрібен дозвіл на мікрофон
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        }
    }

    override fun onDestroy() {
        wakeListener?.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startListening() {
        wakeListener = WakeListener(
            context = applicationContext,
            language = prefs.language,
            usePorcupine = prefs.usePorcupine && BuildConfig.PICOVOICE_KEY.isNotBlank(),
            picovoiceKey = BuildConfig.PICOVOICE_KEY,
            onWake = { phrase ->
                // Wake-word розпізнано → переходимо в режим прийому команди
                commandSession = true
                scope.launch {
                    val cmd = wakeListener?.captureCommand(timeoutMs = 6500) ?: ""
                    if (cmd.isNotBlank()) execute(cmd)
                    commandSession = false
                    updateNotification("Слухаю 'Тоторо'…")
                }
            }
        )
        wakeListener?.start()
    }

    private suspend fun execute(command: String) {
        // Прості локальні обробники
        val lower = command.lowercase()
        updateNotification("Виконую: $command")
        withContext(Dispatchers.Main) {
            when {
                lower.contains("youtube") && lower.contains("увімкни") -> CommandRouter.playYT(this@TotoroListenerService, command)
                lower.contains("music") && (lower.contains("youtube") || lower.contains("муз")) ->
                    CommandRouter.playYTMusic(this@TotoroListenerService, command)
                lower.contains("spotify") -> CommandRouter.playSpotify(this@TotoroListenerService, command)
                lower.contains("світл") && (lower.contains("увімкн") || lower.contains("вкл")) ->
                    CommandRouter.haSwitch(prefs, listOf("light.living_room"), true)
                lower.contains("світл") && (lower.contains("вимкн") || lower.contains("викл")) ->
                    CommandRouter.haSwitch(prefs, listOf("light.living_room"), false)
                lower.startsWith("таймер") || lower.contains("постав таймер") ->
                    CommandRouter.setTimer(this@TotoroListenerService, command)
                lower.contains("надішли в телеграм") || lower.contains("в телеграм") ->
                    CommandRouter.sendTelegram(prefs, command)
                lower.contains("запиши") || lower.contains("нотатк") ->
                    CommandRouter.noteToObsidian(prefs, command)
                else -> {
                    scope.launch {
                        val reply = HermesClient(prefs.hermesUrl).ask(command)
                        if (reply.reply.isNotBlank()) speak(reply.reply)
                        if (reply.url.isNotBlank()) CommandRouter.openUrl(this@TotoroListenerService, reply.url)
                    }
                }
            }
        }
    }

    private fun speak(text: String) {
        val tts = android.speech.tts.TextToSpeech(this) { /* init */ }
        tts?.setLanguage(java.util.Locale("uk", "UA"))
        tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "totoro_${System.currentTimeMillis()}")
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTI_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, TotoroListenerService::class.java).setAction("STOP")
        val stopPi = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, "totoro_listen")
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Тоторо")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_stop, "Зупинити", stopPi)
            .build()
    }

    companion object {
        const val NOTI_ID = 1
    }
}
