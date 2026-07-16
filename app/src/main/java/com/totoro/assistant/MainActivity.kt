package com.totoro.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.totoro.assistant.diagnostics.HermesReporter
import com.totoro.assistant.service.TotoroListenerService
import com.totoro.assistant.service.WakeListener
import com.totoro.assistant.ui.HomeScreen

class MainActivity : ComponentActivity() {

    private val TAG = "TotoroMain"
    private val audioPermission = Manifest.permission.RECORD_AUDIO
    private val postNotifications = Manifest.permission.POST_NOTIFICATIONS

    private var listening by mutableStateOf(false)
    private var lastCommand by mutableStateOf<String?>(null)
    private var statusText by mutableStateOf("Готовий.")
    private var lastError by mutableStateOf<String?>(null)
    private var usingServiceOnly by mutableStateOf(false)
    private var activityWakeListener: WakeListener? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        HermesReporter.report(this, "INFO", TAG, "perms_result",
            "granted=${results.filterValues { it }.keys}, denied=${results.filterValues { !it }.keys}")
        if (allGranted) {
            // Запускаємо одразу
            startTotoroWithBothBackends()
        } else {
            statusText = "Дозволи не надано — слухатиму якщо увімкнеш у налаштуваннях"
            lastError = results.filterValues { !it }.keys.firstOrNull()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        HermesReporter.report(this, "INFO", TAG, "activity_onCreate",
            "sdk=${Build.VERSION.SDK_INT}")

        // Запам'ятати: чи можна запустити службу, чи треба тільки Activity-режим
        try {
            setContent {
                HomeScreen(
                    listening = listening,
                    lastCommand = lastCommand,
                    statusText = statusText,
                    lastError = lastError,
                    onStartListening = ::onUserPressedStart,
                    onStopListening = ::stopAll,
                    onOpenSettings = ::openAppSettings
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "setContent failed", e)
            HermesReporter.report(this, "ERROR", TAG, "setContent_fail", e.message)
        }
    }

    override fun onResume() {
        super.onResume()
        // Якщо служба все ще жива — синхронізуємо UI
        if (TotoroListenerService.isRunning) {
            listening = true
            usingServiceOnly = true
            statusText = "Слухаю 'Гей, Тоторо' (служба активна)"
        }
    }

    override fun onDestroy() {
        // Activity-only wake listener — зупинити, якщо додаток закривають
        try { activityWakeListener?.stop() } catch (_: Throwable) {}
        activityWakeListener = null
        super.onDestroy()
    }

    private fun onUserPressedStart() {
        val perms = mutableListOf(audioPermission)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(postNotifications)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startTotoroWithBothBackends()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    /**
     * Запуск у двох режимах одночасно:
     * 1) ForegroundService (живе навіть коли додаток закритий)
     * 2) WakeListener в межах Activity (працює навіть якщо служба падає)
     *
     * Якщо служба впаде — Activity-fallback продовжує слухати,
     * і тоді HermesReporter повідомляє про проблему.
     */
    private fun startTotoroWithBothBackends() {
        startForegroundServiceSafe()
        startActivityFallback()
        listening = true
        statusText = "Слухаю 'Гей, Тоторо'… (служба + Activity)"
        HermesReporter.report(this, "INFO", TAG, "both_started")
    }

    private fun startForegroundServiceSafe() {
        try {
            val intent = Intent(this, TotoroListenerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            usingServiceOnly = true
        } catch (e: Throwable) {
            Log.e(TAG, "startForegroundService failed", e)
            HermesReporter.report(this, "ERROR", TAG, "start_service_fail", e.message)
            lastError = "Служба: ${e.javaClass.simpleName}"
            usingServiceOnly = false
        }
    }

    private fun startActivityFallback() {
        if (activityWakeListener != null) return
        try {
            activityWakeListener = WakeListener(
                context = applicationContext,
                language = "uk-UA",
                usePorcupine = false,
                picovoiceKey = "",
                onWake = { phrase ->
                    HermesReporter.report(this, "INFO", TAG, "wake_in_activity", phrase.take(80))
                    // Зараз ми не виконуємо команди з Activity-mode — це робить служба.
                    // Цей listener — лише «keep alive» для Hermes-телеметрії.
                }
            )
            activityWakeListener?.start()
            HermesReporter.report(this, "INFO", TAG, "activity_wake_started")
        } catch (e: Throwable) {
            Log.e(TAG, "activity fallback failed", e)
            HermesReporter.report(this, "ERROR", TAG, "activity_fallback_fail", e.message)
        }
    }

    private fun stopAll() {
        try { stopService(Intent(this, TotoroListenerService::class.java)) } catch (_: Throwable) {}
        try { activityWakeListener?.stop() } catch (_: Throwable) {}
        activityWakeListener = null
        listening = false
        usingServiceOnly = false
        statusText = "Зупинено."
        HermesReporter.report(this, "INFO", TAG, "stop_all")
    }

    private fun openAppSettings() {
        // Перейти на сторінку нашого додатку в Android Settings
        try {
            val intent = android.content.Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        } catch (e: Throwable) {
            // Fallback — стандартні Android settings
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }
}
