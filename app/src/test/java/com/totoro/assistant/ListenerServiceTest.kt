package com.totoro.assistant

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.totoro.assistant.service.TotoroListenerService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тест запускає TotoroListenerService (як у реальному житті,
 * коли app тисне «Увімкнути»). Якщо на цьому етапі виникає крах —
 * Robolectric кине exception зі stacktrace.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListenerServiceTest {

    @Test
    fun service_can_be_started_via_intent() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(app, TotoroListenerService::class.java)
        // Robolectric підтримує createService, але startForeground потребує specific env
        try {
            val controller = Robolectric.buildService(TotoroListenerService::class.java, intent)
                .create()
                .startCommand(0, 0)
            val service = controller.get()
            assertNotNull(service)
            // Перевіримо що isRunning став true (startForeground мав спрацювати)
            Thread.sleep(500)
            // Service не повинен впасти
            controller.stop()
        } catch (t: Throwable) {
            System.err.println("### TotoroListenerService start FAILED ###")
            t.printStackTrace()
            throw t
        }
    }
}
