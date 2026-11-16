package nodomain.greensnowapps.lightsignals.testing

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals

/**
 * Provides isolated-app device, lifecycle, permission and asynchronous assertion helpers.
 *
 * Blocking polling runs on the instrumentation test thread; onMain marshals UI work to Main.
 */
object DeviceTestSupport {
    /**
     * Instrumentation instance that owns the current Android test.
     */
    val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    /**
     * Target test application context.
     */
    val context: Context get() = instrumentation.targetContext
    /**
     * UiAutomator handle for the device running this test.
     */
    val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    /**
     * Grants a permission to the isolated target app and waits for Android to report it
     * granted.
     *
     * Run on the instrumentation test thread, not Main.
     *
     * @param permission Android permission name to grant or verify.
     */
    fun grantPermission(permission: String) {
        device.executeShellCommand("pm grant ${context.packageName} $permission")
        eventually("Permission $permission was not granted") {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Asserts that an isolated test starts without the requested runtime permission.
     *
     * @param permission Android permission name to grant or verify.
     */
    fun assertPermissionDenied(permission: String) {
        assertEquals("Use Orchestrator with clearPackageData=true to isolate permission tests: $permission",
            PackageManager.PERMISSION_DENIED, ContextCompat.checkSelfPermission(context, permission))
    }

    /**
     * Brings an existing target-app task forward using APIs available on the minimum Android
     * version.
     *
     * Run on the instrumentation test thread.
     *
     * @param taskId ID of an existing task owned by the target test app.
     * @throws IllegalArgumentException If taskId is negative.
     * @throws IllegalStateException If the task does not belong to the target application.
     */
    @Suppress("DEPRECATION")
    fun focusTask(taskId: Int) {
        require(taskId >= 0)
        // The shell's "am task focus" command is unavailable on Android 7.
        // Move this app's existing task instead of creating another Activity.
        val manager = context.getSystemService(ActivityManager::class.java)
        val task = manager.appTasks.firstOrNull {
            val info = it.taskInfo ?: return@firstOrNull false
            val existingId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.taskId else info.id
            existingId == taskId
        }
        checkNotNull(task) { "Task $taskId does not belong to ${context.packageName}" }.moveToFront()
    }

    /**
     * Runs a test action on Main, waits for completion and propagates its result or failure.
     *
     * @param T Result type returned by the main-thread action.
     * @param action Operation to execute on Android's main thread.
     * @return The value returned by action.
     */
    fun <T> onMain(action: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return action()
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(action)) }
        return result.get().getOrThrow()
    }

    /**
     * Polls asynchronous Android state on the instrumentation thread until the condition
     * succeeds.
     *
     * Never call this blocking helper on Main.
     *
     * @param message Assertion failure description reported if the condition never becomes true.
     * @param timeoutMillis Maximum polling time in milliseconds.
     * @param condition Predicate polled on the instrumentation thread until it succeeds or times
     *     out.
     * @throws IllegalStateException If called on the main thread.
     * @throws AssertionError If the condition remains false at the deadline.
     */
    fun eventually(message: String, timeoutMillis: Long = 15_000, condition: () -> Boolean) {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Never wait on Android's main thread" }
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        do {
            if (condition()) return
            SystemClock.sleep(25)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError(message)
    }

    /**
     * Queries Android's running-service records for the target component.
     *
     * @param className Fully qualified component or application class name.
     * @param foregroundOnly Whether only foreground service records should match.
     * @return True when the service appears and satisfies the optional foreground filter.
     */
    @Suppress("DEPRECATION")
    fun serviceRunning(className: String, foregroundOnly: Boolean = false): Boolean =
        context.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == className && (!foregroundOnly || it.foreground) }
}
