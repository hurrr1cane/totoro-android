package com.totoro.assistant

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Глибший тест: створює MainActivity через Robolectric і слідкує за
 * життєвим циклом. Якщо onCreate впаде — Robolectric кине exception тут,
 * у stacktrace буде видно корінь.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeepTest {

    @Test
    fun main_activity_can_be_launched_and_paused() {
        try {
            val controller = Robolectric.buildActivity(MainActivity::class.java).create().start()
            val activity = controller.get()
            assertNotNull(activity)
            assertFalse(activity.isFinishing)
            assertFalse(activity.isDestroyed)
            controller.pause()
            controller.stop()
            controller.destroy()
        } catch (t: Throwable) {
            System.err.println("### MainActivity failed to launch ###")
            t.printStackTrace()
            throw t
        }
    }

    @Test
    fun main_activity_resume_does_not_crash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        assertNotNull(activity)
        controller.pause().stop().destroy()
    }

    @Test
    fun crash_handler_does_not_throw_on_install_twice() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        CrashHandler.install(ctx)
        // Другий install не повинен кинути виняток
        CrashHandler.install(ctx)
        // Ok
    }

    @Test
    fun prefs_can_be_read_without_exception() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = com.totoro.assistant.prefs.TotoroPrefs(ctx)
        // Має повернути default-values без exception
        val token = prefs.haToken
        val url = prefs.haUrl
        assertNotNull(url)
        assertEquals("http://localhost:8123", url)
        assertEquals("", token)
    }
}
