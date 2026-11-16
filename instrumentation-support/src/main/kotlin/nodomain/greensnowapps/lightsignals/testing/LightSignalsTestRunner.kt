package nodomain.greensnowapps.lightsignals.testing

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Starts instrumentation with the Hilt test application and isolated test environment.
 */
class LightSignalsTestRunner : AndroidJUnitRunner() {
    /**
     * Checks that instrumentation targets the isolated test application before starting the
     * runner.
     *
     * Rejects production application IDs so device tests cannot alter the regular app's data.
     *
     * @param arguments Instrumentation arguments supplied by the test launcher.
     * @throws IllegalStateException If instrumentation targets an application ID other than
     *     the isolated uitest app.
     */
    override fun onCreate(arguments: Bundle) {
        check(targetContext.packageName == "nodomain.greensnowapps.lightsignals.uitest") {
            "Device tests must target the isolated uitest application"
        }
        super.onCreate(arguments)
    }

    /**
     * Creates the Hilt test application instead of the production Application class.
     *
     * @param cl Class loader supplied by Android.
     * @param className Requested application class, replaced by HiltTestApplication.
     * @param context Target context supplied by Android to create the test application.
     * @return The application instance used by this instrumented test process.
     */
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
