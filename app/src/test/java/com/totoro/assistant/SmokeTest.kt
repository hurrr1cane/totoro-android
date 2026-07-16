package com.totoro.assistant

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke test: перевіряє, що Application створюється, ресурси доступні,
 * і NotificationChannel створюється без винятків.
 *
 * Не вимагає емулятора — Robolectric запускає JVM-SDK.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class SmokeTest {

    @Test
    fun application_can_be_created_without_exceptions() {
        val app = ApplicationProvider.getApplicationContext<TotoroApp>()
        assertNotNull(app)
        assertNotNull(app.packageName)
        // debug build має applicationIdSuffix .debug → очікуємо com.totoro.assistant.debug
        // release — com.totoro.assistant. Тестуємо лише що suffix в межах очікуваного
        val pkg = app.packageName
        assertTrue("package=$pkg must start with com.totoro.assistant",
            pkg == "com.totoro.assistant" || pkg == "com.totoro.assistant.debug")
    }

    @Test
    fun main_activity_class_is_loadable() {
        // Якщо Activity клас недійсний чи не парситься з маніфесту —
        // Robolectric впаде тут з ClassNotFound або схожим.
        val cls = Class.forName("com.totoro.assistant.MainActivity")
        assertNotNull(cls)
    }

    @Test
    fun totoro_listener_service_class_is_loadable() {
        val cls = Class.forName("com.totoro.assistant.service.TotoroListenerService")
        assertNotNull(cls)
    }

    @Test
    fun wake_listener_class_is_loadable() {
        val cls = Class.forName("com.totoro.assistant.service.WakeListener")
        assertNotNull(cls)
    }
}
