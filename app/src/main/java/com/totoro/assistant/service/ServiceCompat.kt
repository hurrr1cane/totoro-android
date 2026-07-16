package com.totoro.assistant.service

import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build

/**
 * Сумісна обгортка навколо startForeground(id, notif, type),
 * яка з'явилась у Android Q (API 29). Для нижчих версій — fallback.
 *
 * androidx.core.app.ServiceCompat існує, але вимагає версії core >= 1.12,
 * яка може бути нижчою у проєкті. Тримаємо локальну реалізацію.
 */
object ServiceCompat {
    fun startForeground(
        service: Service,
        id: Int,
        notification: Notification,
        foregroundServiceType: Int
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+ — передаємо тип служби явно, інакше SecurityException
            service.startForeground(id, notification, foregroundServiceType)
        } else {
            service.startForeground(id, notification)
        }
    }
}
