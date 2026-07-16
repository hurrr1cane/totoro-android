package com.totoro.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.totoro.assistant.prefs.TotoroPrefs
import com.totoro.assistant.service.TotoroListenerService

/** Запускає ForegroundService автоматично після перезавантаження. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = TotoroPrefs(ctx)
        if (!prefs.hermesUrl.isBlank() || true) {
            val svc = Intent(ctx, TotoroListenerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(ctx, svc)
            } else {
                ctx.startService(svc)
            }
        }
    }
}
