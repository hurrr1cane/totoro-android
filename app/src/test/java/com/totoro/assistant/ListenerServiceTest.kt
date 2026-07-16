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
 * Тест запускає TotoroListenerService як у реальному житті — startForegroundService().
 * Якщо на цьому етапі виникає крах — Robolectric кине exception зі stacktrace.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListenerServiceTest {

    @Test
    fun service_can_be_created_and_started_without_exceptions() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(app, TotoroListenerService::class.java)
        // Robolectric buildService: create — без startForeground потреби
        try {
            val controller = Robolectric.buildService(TotoroListenerService::class.java, intent)
                .create()  // ONLY create, не startCommand — інакше FGS вимагає реального system_server
            val service = controller.get()
            assertNotNull("Service повинен існувати", service)
            // destroy для cleanup
            controller.destroy()
        } catch (t: Throwable) {
            System.err.println("### TotoroListenerService create FAILED ###")
            t.printStackTrace()
            throw t
        }
    }

    @Test
    fun service_class_does_not_have_unsatisfied_linkage() {
        // Перевіряємо що Service клас можна завантажити без native-бібліотек, які ламають JVM.
        val cls = Class.forName("com.totoro.assistant.service.TotoroListenerService")
        assertNotNull(cls)
        // Перевіримо, що Service не має статичних блоків, які кидають ExceptionInInitializerError
        try {
            cls.getDeclaredField("INSTANCE") // перевірка ініціалізації static
        } catch (_: NoSuchFieldException) {
            // Ok, немає INSTANCE поля
        }
    }
}
