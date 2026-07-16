package com.totoro.assistant

import androidx.test.core.app.ApplicationProvider
import com.totoro.assistant.diagnostics.HermesReporter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HermesReporterTest {

    @Test
    fun report_does_not_throw_synchronously() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Без endpoint report() має просто записати в logcat
        try {
            HermesReporter.report(ctx, "INFO", "test", "hello", null)
            HermesReporter.report(ctx, "ERROR", "test", "boom", null)
        } catch (t: Throwable) {
            t.printStackTrace()
            throw t
        }
        // Зачекати трохи щоб Thread всередині не впав у фоні
        Thread.sleep(300)
    }

    @Test
    fun report_with_endpoint_does_not_throw() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Симулюємо preferences з HermeS endpoint
        val prefs = com.totoro.assistant.prefs.TotoroPrefs(ctx)
        prefs.hermesUrl = "http://localhost:9999/this-will-fail"
        try {
            HermesReporter.report(ctx, "INFO", "test", "with endpoint", null)
        } catch (t: Throwable) {
            t.printStackTrace()
            throw t
        }
        Thread.sleep(500)
    }
}
