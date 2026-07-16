package com.totoro.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class TotoroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // ForegroundService channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Тоторо слухає",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "totoro_listen"
    }
}
