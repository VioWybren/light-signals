package nodomain.greensnowapps.lightsignals.testing

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class LightSignalsTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        check(targetContext.packageName == "nodomain.greensnowapps.lightsignals.uitest") {
            "Device tests must target the isolated uitest application"
        }
        super.onCreate(arguments)
    }

    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
