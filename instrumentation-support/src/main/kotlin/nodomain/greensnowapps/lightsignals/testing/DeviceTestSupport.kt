package nodomain.greensnowapps.lightsignals.testing

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals

object DeviceTestSupport {
    val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val context: Context get() = instrumentation.targetContext
    val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    fun grantPermission(permission: String) {
        device.executeShellCommand("pm grant ${context.packageName} $permission")
        eventually("Permission $permission was not granted") {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun assertPermissionDenied(permission: String) {
        assertEquals("Use Orchestrator with clearPackageData=true to isolate permission tests: $permission",
            PackageManager.PERMISSION_DENIED, ContextCompat.checkSelfPermission(context, permission))
    }

    fun focusTask(taskId: Int) {
        check(taskId >= 0)
        device.executeShellCommand("am task focus $taskId")
    }

    fun <T> onMain(action: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return action()
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(action)) }
        return result.get().getOrThrow()
    }

    /** Polls asynchronous Android/IPC state with a deadline, rather than fixed startup sleeps. */
    fun eventually(message: String, timeoutMillis: Long = 15_000, condition: () -> Boolean) {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Never wait on Android's main thread" }
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        do {
            if (condition()) return
            SystemClock.sleep(25)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError(message)
    }

    @Suppress("DEPRECATION")
    fun serviceRunning(className: String, foregroundOnly: Boolean = false): Boolean =
        context.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == className && (!foregroundOnly || it.foreground) }
}
