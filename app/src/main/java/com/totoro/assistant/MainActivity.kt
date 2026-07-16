package com.totoro.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.ContextCompat
import com.totoro.assistant.diagnostics.HermesReporter
import com.totoro.assistant.service.CommandRouter
import com.totoro.assistant.service.TotoroListenerService
import com.totoro.assistant.service.WakeListener
import com.totoro.assistant.ui.HomeScreen
import com.totoro.assistant.ui.LiveLog
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeListener: WakeListener? = null

    private var listening by mutableStateOf(false)
    private var lastCommand by mutableStateOf<String?>(null)
    private var statusText by mutableStateOf("Готовий.")
    private var lastError by mutableStateOf<String?>(null)
    private var speechAvailable by mutableStateOf(false)
    private var isTestListening by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.filterValues { it }.keys
        val denied = results.filterValues { !it }.keys
        LiveLog.info("perms", "granted=$granted denied=$denied")
        HermesReporter.report(this, "INFO", "perms", "result",
            "granted=$granted denied=$denied")
        if (denied.isEmpty()) {
            startTotoro()
        } else {
            statusText = "Дозволи не надані: ${denied.joinToString(", ")}"
            lastError = "Бракує: ${denied.joinToString(", ")}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechAvailable = SpeechRecognizer.isRecognitionAvailable(this)
        LiveLog.event("app", "MainActivity.onCreate SR=$speechAvailable")
        HermesReporter.report(this, "INFO", "MainActivity", "onCreate",
            "sdk=${Build.VERSION.SDK_INT} speechAvail=$speechAvailable")

        setContent {
            HomeScreen(
                listening = listening,
                lastCommand = lastCommand,
                statusText = statusText,
                lastError = lastError,
                liveEvents = LiveLog.events,
                speechAvailable = speechAvailable,
                onStartListening = ::onUserPressedStart,
                onStopListening = ::stopTotoro,
                onOpenSettings = ::openSettings,
                onTestCommand = ::testCommandManually,
                onTestVoiceListen = ::testVoiceListen,
                onRefresh = ::refreshState
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    override fun onDestroy() {
        try { wakeListener?.stop() } catch (_: Throwable) {}
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshState() {
        val avail = SpeechRecognizer.isRecognitionAvailable(this)
        speechAvailable = avail
        // Активна служба?
        val serviceRunning = TotoroListenerService.isRunning
        listening = serviceRunning || wakeListener != null
        LiveLog.info("refresh", "SR=$avail service=$serviceRunning wake=${wakeListener != null}")
        statusText = when {
            !avail && !serviceRunning -> "⚠ SpeechRecognizer недоступний. Встанови Google Search (Google Now) або GMS."
            serviceRunning -> "Слухаю 'Гей, Тоторо' (служба активна)"
            wakeListener != null -> "Слухаю 'Гей, Тоторо' (активність)"
            else -> "Готовий. Натисни ▶ щоб увімкнути."
        }
    }

    private fun onUserPressedStart() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startTotoro()
        } else {
            LiveLog.info("start", "Запитую дозволи: ${missing.joinToString(", ")}")
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startTotoro() {
        refreshState()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            lastError = "SpeechRecognizer недоступний. Встанови/онови Google App."
            LiveLog.error("start", "SpeechRecognizer недоступний")
        }
        // 1) Запустити ForegroundService (стара логіка)
        try {
            val intent = Intent(this, TotoroListenerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            LiveLog.event("service", "ForegroundService запущено")
        } catch (e: Throwable) {
            LiveLog.error("service", "Не вдалося стартувати: ${e.message}")
            HermesReporter.report(this, "ERROR", "MainActivity", "start_service_fail", e.message)
        }

        // 2) Запустити wake listener в межах Activity
        try {
            wakeListener = WakeListener(
                context = applicationContext,
                language = "uk-UA",
                usePorcupine = false,
                picovoiceKey = "",
                onWake = { phrase ->
                    LiveLog.event("wake", "🟢 Wake: '$phrase'")
                    HermesReporter.report(this, "INFO", "MainActivity", "wake", phrase)
                    scope.launch { executeCommand(phrase) }
                }
            )
            wakeListener?.start()
            LiveLog.event("activity", "WakeListener стартований в Activity")
            lastError = null
            listening = true
            statusText = "Слухаю 'Гей, Тоторо'…"
        } catch (e: Throwable) {
            LiveLog.error("activity", "WakeListener.start fail: ${e.message}")
            lastError = "Wake: ${e.javaClass.simpleName}"
        }
    }

    private fun stopTotoro() {
        try {
            stopService(Intent(this, TotoroListenerService::class.java))
        } catch (_: Throwable) {}
        try { wakeListener?.stop() } catch (_: Throwable) {}
        wakeListener = null
        listening = false
        statusText = "Зупинено."
        LiveLog.event("stop", "Все зупинено")
    }

    /** Запуск короткого тестового прослуховування — показати, чи SR взагалі чує. */
    private fun testVoiceListen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            LiveLog.error("test", "SpeechRecognizer недоступний")
            return
        }
        LiveLog.event("test", "Запускаю одноразовий SR listen (3 сек)…")
        isTestListening = true
        scope.launch {
            val result = wakeListener?.captureCommand(timeoutMs = 4000)
            isTestListening = false
            if (result == null) {
                LiveLog.warn("test", "SR нічого не почув (timeout 4с)")
                statusText = "Тест: SR мовчить. Можливі причини: мікрофон зайнятий, DND, інший застосунок слухає."
            } else {
                LiveLog.event("test", "🎤 SR почув: '$result'")
                lastCommand = result
                executeCommand(result)
            }
        }
    }

    /** Виконати команду вручну (набір у полі вводу) — bypass SR. */
    private fun testCommandManually(cmd: String) {
        LiveLog.event("manual", "Виконую вручну: '$cmd'")
        scope.launch { executeCommand(cmd) }
    }

    private suspend fun executeCommand(command: String) {
        lastCommand = command
        statusText = "Виконую: $command"
        withContext(Dispatchers.Main) {
            try {
                val lower = command.lowercase()
                when {
                    lower.contains("youtube music") || (lower.contains("муз") && lower.contains("youtube")) ->
                        CommandRouter.playYTMusic(this@MainActivity, command)
                    lower.contains("youtube") ->
                        CommandRouter.playYT(this@MainActivity, command)
                    lower.contains("spotify") || lower.contains("спотіф") ->
                        CommandRouter.playSpotify(this@MainActivity, command)
                    lower.startsWith("таймер") || lower.contains("постав таймер") ->
                        CommandRouter.setTimer(this@MainActivity, command)
                    else -> {
                        LiveLog.warn("exec", "Невідома команда: '$command'")
                        statusText = "Невідома команда: $command"
                    }
                }
                LiveLog.event("exec", "OK: $command")
                statusText = "Готово: $command"
            } catch (e: Throwable) {
                LiveLog.error("exec", "Виняток: ${e.javaClass.simpleName} ${e.message}")
                lastError = "${e.javaClass.simpleName}"
            }
        }
    }

    private fun openSettings() {
        try {
            startActivity(Intent(this, SettingsActivity::class.java))
        } catch (e: Throwable) {
            LiveLog.error("settings", e.message ?: "")
        }
    }
}
