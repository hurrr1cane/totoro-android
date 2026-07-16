package com.totoro.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.totoro.assistant.service.TotoroListenerService
import com.totoro.assistant.ui.HomeScreen

class MainActivity : ComponentActivity() {

    /** Список runtime-дозволів, які нам реально потрібні. */
    private val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // На Android 12+ потрібен явний запит на запуск ForegroundService з мікрофоном
            // (інакше служба падає одразу — це наш випадок).
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Після того як юзер відповів, можна запускати службу.
        if (results.values.all { it }) {
            startTotoro()
        } else {
            // Якщо RECORD_AUDIO відхилено — запропонувати перейти в налаштування
        }
    }

    private var listening by mutableStateOf(false)
    private var lastCommand by mutableStateOf<String?>(null)
    private var statusText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Встановити глобальний crash-handler (щоб не втрачати причину падіння)
        // CrashHandler.install()

        setContent {
            HomeScreen(
                listening = listening,
                lastCommand = lastCommand,
                statusText = statusText,
                onStartListening = ::onUserPressedStart,
                onStopListening = ::stopTotoro,
                onOpenSettings = ::openAppSettings
            )
        }
    }

    private fun onUserPressedStart() {
        if (hasAllPermissions()) {
            startTotoro()
            return
        }
        permissionLauncher.launch(permissions)
    }

    private fun hasAllPermissions(): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startTotoro() {
        val intent = Intent(this, TotoroListenerService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            listening = true
            statusText = "Запущено. Слухаю 'Гей, Тоторо'…"
        } catch (e: Exception) {
            statusText = "Не вдалося запустити службу: ${e.message}"
            listening = false
        }
    }

    private fun stopTotoro() {
        stopService(Intent(this, TotoroListenerService::class.java))
        listening = false
        statusText = "Зупинено."
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // На деяких пристроях служба вбивається — синхронізуємо стан
        listening = TotoroListenerService.isRunning
        if (listening) statusText = "Слухаю 'Гей, Тоторо'…"
    }
}
