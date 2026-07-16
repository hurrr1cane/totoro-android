package com.totoro.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class TotoroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Підключаємо crash handler ДО будь-яких інших ініціалізацій
        CrashHandler.install(this)
        ensureNotificationChannel()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(TotoroNotificationChannel.ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        TotoroNotificationChannel.ID,
                        "Тоторо слухає",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        setShowBadge(false)
                        description = "Постійна нотифікація ForegroundService голосового асистента."
                    }
                )
            }
        }
    }
}

object TotoroNotificationChannel {
    const val ID = "totoro_listen"
}
