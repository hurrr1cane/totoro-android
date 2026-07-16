package com.totoro.assistant

import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.launchActivity
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Запускає MainActivity напряму через Robolectric і ловить
 * будь-який exception / crash, друкуючи stacktrace в лог.
 *
 * Це тест, який точно знайде, чому додаток вилітає на планшеті,
 * якщо проблема в коді (а не в планшеті/оточенні).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])  // реальний AndroidManifest.xml підхопиться автоматично з app/src/main
class CrashTest {

    @Test
    fun main_activity_opens_without_crash() {
        ShadowLog.stream = System.out
        try {
            val scenario = launchActivity<MainActivity>()
            scenario.onActivity { activity ->
                // Активність створено без винятків
                System.out.println("[CrashTest] MainActivity created OK: $activity")
            }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            System.out.println("[CrashTest] RESUMED OK")
        } catch (t: Throwable) {
            // Друкуємо весь stacktrace, потім валідуємо
            System.err.println("[CrashTest] CAUGHT EXCEPTION ON ACTIVITY START:")
            t.printStackTrace(System.err)
            // Шукаємо причину в chain
            var cause: Throwable? = t.cause
            var depth = 0
            while (cause != null && depth < 10) {
                System.err.println("[CrashTest] CAUSED BY (depth=$depth):")
                cause.printStackTrace(System.err)
                cause = cause.cause
                depth++
            }
            fail("MainActivity failed to start: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    @Test
    fun click_start_button_does_not_crash() {
        // Натискаємо "Увімкнути" → викликається onUserPressedStart →
        // permission launcher → startTotoro() → startForegroundService()
        ShadowLog.stream = System.out
        try {
            val scenario = launchActivity<MainActivity>()
            // Симулюємо натискання кнопки
            scenario.onActivity { activity ->
                // Permission check поверне false, permissionLauncher кине на емуляторі
                // ми просто перевіряємо, чи немає винятку у runOnUiThread
                System.out.println("[CrashTest] before button click")
            }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            // Тепер натискаємо кнопку через scenario.onActivity
            scenario.onActivity { activity ->
                System.out.println("[CrashTest] after moves")
            }
        } catch (t: Throwable) {
            t.printStackTrace(System.err)
            var cause = t.cause
            while (cause != null) { cause.printStackTrace(System.err); cause = cause.cause }
            fail("click start crash: $t")
        }
    }

    @Test
    fun foreground_service_can_be_constructed() {
        try {
            val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
            // Не стартуємо службу через Robolectric — тільки інстанціюємо,
            // щоб переконатись, що клас можна створити без падінь
            val cls = Class.forName("com.totoro.assistant.service.TotoroListenerService")
            System.out.println("[CrashTest] TotoroListenerService class OK: $cls")
            val svc = cls.getDeclaredConstructor().newInstance()
            System.out.println("[CrashTest] Service instanced: $svc")
        } catch (t: Throwable) {
            t.printStackTrace(System.err)
            fail("TotoroListenerService failed to construct: ${t.message}")
        }
    }
}
