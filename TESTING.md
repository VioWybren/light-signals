# Testing Light Signals

This branch tests permission-free torch operation. Keep compatibility results separate from the
camera-permission-based `baseline/v4.0.0-camera` checkpoint; see
[PERMISSION_EXPERIMENT.md](PERMISSION_EXPERIMENT.md).

Run the local JVM suites from the project directory in PowerShell:

```powershell
.\gradlew.bat :phone:testDebugUnitTest :watch:testDebugUnitTest --stacktrace
```

The first run may download the coroutine testing dependency. HTML reports are written to
`phone/build/reports/tests/testDebugUnitTest/index.html` and
`watch/build/reports/tests/testDebugUnitTest/index.html`.

## Coverage in this batch

- Phone state: defaults, legacy preferences, range validation, non-finite numbers, and safe restoration without restarting a previous torch session.
- Phone repository: initialization, remote state application, mode precedence, service-start failure, cancellation, queued writes, and recoverable storage errors.
- Watch repository: foreground ownership, stop/restart and delayed initialization, mode changes, remote disconnect, ordered writes, and storage failures.
- SOS: exact dot/dash durations, letter and word gaps, repetition, cancellation during a pulse or gap, and failed hardware enable.
- Synchronization: exact DataMap field names and value types, defaults for older payloads, unknown future fields, delivery timestamps, recoverable transport failures, cancellation, and readiness/reconnect/resume gates.
- Preference helpers: cancellation and programming errors propagate instead of becoming default values or successful writes.
- Existing torch ownership/concurrency and gradient regression tests remain included.
- Timed LED sessions: acquire a CPU wake lock before signaling, keep it through SOS gaps and
  cancellation-safe torch cleanup, and release it on mode changes or failures. Steady torch
  operation does not hold this lock.

## Test design

Both modules select `uitest` for instrumented tests. AGP 9 normally generates JVM test tasks
only for that selected build type. Root `gradle.properties` sets
`android.onlyEnableUnitTestForTheTestedBuildType=false` so `testDebugUnitTest` remains available
in both modules. This enables unit-test components for the other build types as well; the
commands above run the debug JVM suites.

JUnit 4 and `kotlinx-coroutines-test` run these suites on the JVM. Coroutine tests use a shared virtual scheduler and advance time explicitly. Repository scopes and platform callbacks can be replaced through internal constructors; Hilt continues to use the Android constructors. Test-owned scopes are cancelled when each test finishes.

`test-support/src/main/kotlin` contains an atomic in-memory preferences fake and fixture readers. It is compiled only into the test source sets of the two modules. It does not simulate Android filesystem persistence, permission enforcement, service startup, or physical hardware.

`test-support/src/main/resources/contracts` contains independent reference fixtures used by both modules. The phone tests check the production decoder against them; the watch tests check the production sender/encoder. This catches an accidental key, value-type, path, or default change in either module. Keep these fixtures independent of the encoder; do not regenerate them automatically from production code.

The SOS fixture records absolute edges for a 200 ms unit and includes the beginning of the next cycle. The signal pattern delegates cancellation cleanup to its owning repository or torch session. Repository tests verify that obsolete signal jobs cannot continue after stop or replacement; torch-controller tests cover release and ownership.

The synchronization gate tests exercise the function used by `WearMainViewModel`. Actual Google Play services listener registration, DataEventBuffer handling, radio delivery, and paired-device compatibility still require integration tests.

## Android device tests

The `uitest` build type installs as **Light Signals Tests**, with application ID
`nodomain.greensnowapps.lightsignals.uitest`. It has separate data, permissions, and notifications
from the production/debug app. Instrumentation targets this build type, so its tasks use `Uitest`
in their names. The default `debug` and `release` application IDs are unchanged.

Build the test apps and their instrumentation APKs:

```powershell
.\gradlew.bat :phone:assembleUitest :phone:assembleUitestAndroidTest :watch:assembleUitest :watch:assembleUitestAndroidTest --stacktrace
```

List connected devices with `adb devices -l`, then run the phone and watch suites separately.
Substitute each device's actual serial in these examples:

```powershell
.\scripts\Test-Android.ps1 -Module phone -Serial 'emulator-5554'
.\scripts\Test-Android.ps1 -Module watch -Serial 'emulator-5556'
```

The script also accepts real-device serials. It checks the device type, builds the APKs,
checks the isolated application ID, resets only an existing `.uitest` installation, and selects
the requested device through `ANDROID_SERIAL`. Keep devices unlocked and available while UI
automation runs; a watch using wireless ADB must remain connected. The script reads the SDK
location from `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or `local.properties`; `-AdbPath` can override it.
During watch runs, a temporary job sends a wake-up key every five seconds to keep the display
awake while building, installing, and testing. The job stops when the script exits; it does not
change persistent screen-timeout settings.

Android Test Orchestrator runs each case in a fresh instrumentation invocation and clears test-app
data between cases. This isolates application settings and permission decisions; it does not
reset the device's secure lock state. The test runner also checks the isolated target package.

On a personal phone with a secure lock, `wakeUp()` turns the display on but does not unlock it.
A screen-off case can therefore leave the keyguard covering subsequent UI cases. Keep the
phone unlocked for UI cases, run screen-off cases last, and unlock between filtered runs when
needed. Do not change PIN, fingerprint, or lock settings for these tests. Preserve initial
failures as well as rerun results; successful filtered reruns are not a single unattended
full-suite pass.

The phone suite covers:

- Absence of camera permission from the installed app's merged manifest and runtime grants.
- Starting/stopping the flashlight without a camera permission prompt.
- Granting or denying the independent notification permission, continued LED operation after
  denial, and respecting that denial after relaunch (API 33+).
- Foreground notification flags, channel, title, launch intent, and service shutdown.
- Home/resume while the torch service is active, and cleanup when the Activity finishes.
- LED blinking/SOS phases with the display off, actual partial wake lock ownership, and lock
  release after switching to steady light, finishing the Activity, stopping, or hardware failure.
- Screen selection/blink mode across Activity recreation.
- Recovery from unavailable torch hardware.
- A separate hardware suite verifies actual CameraManager torch callbacks without camera
  permission for steady LED, blinking, SOS, screen-off operation, and cleanup.

The watch suite covers:

- Actual Activity start/stop ownership, Home/resume, and keep-screen-on flags.
- An offscreen cached signal page leaving window brightness unchanged.
- Brightness application/restoration when selecting/leaving the signal page or pressing Home.
- Mode retention and owner replacement during Activity recreation.
- Signal/session/window cleanup when finishing the Activity.

`PhoneAndroidBehaviorTest` starts the real Android service and uses the real notification and
permission frameworks. Hilt replaces its torch driver with a recording backend and supplies
matching flash availability, so the UI cases can also run on emulators without an LED.
`PhoneTorchHardwareTest` keeps the production Hilt bindings and checks real CameraManager torch
callbacks. Its cases require a physical device with a rear LED; emulators and devices without
that hardware are skipped, not counted as hardware compatibility proof.
Watch tests restore the previous media volume.
These tests recreate or stop Activities; they do not claim process-death or OS service-restart coverage.

### Physical LED screen-off regression

The behavior tests check Android's actual power manager/service with a recording torch backend.
The hardware tests also check on/off callbacks from the real camera service. Both check the
service's partial wake lock in `dumpsys power` while the display is non-interactive. A test runner,
USB charging, or other device activity can also keep the CPU awake, so these tests do not replace
an unplugged physical LED check.

On the affected phone:

1. Start LED blinking and disconnect the charger/USB cable.
2. Turn the display off and observe the LED for at least three minutes. Repeat at the fastest
   and slowest blink settings, and repeat in SOS mode.
3. Turn the display back on, switch to steady LED, then turn the LED off. Confirm that the
   requested modes work and the foreground notification disappears after stopping.
4. Record the phone model, Android version, and Battery Saver setting with the result.

The lock is scoped to an active blinking/SOS session and released after its final LED-off write.
The user controls the session duration, so it has no fixed wake lock timeout that could interrupt
a longer signal. It does not keep the display on. Forced deep Doze and manufacturer battery
restrictions are separate scenarios: Android can ignore ordinary wake locks in Doze; see
[Android's Doze restrictions](https://developer.android.com/training/monitoring-device-state/doze-standby#restrictions).

The baseline's first device run caught a service-start deadline crash on the missing-camera path.
This experiment removes that permission path; the service still supplies its foreground
notification before observing LED requests. The Home/resume tests explicitly refocus the existing Android task instead
of assuming that a lifecycle state request can bring a background task to the foreground.

Reports from Gradle device runs are under each module's `build/reports/androidTests/connected`
and `build/outputs/androidTest-results/connected` directories. Tests conditioned on a newer API
are reported as skipped on older devices.

## Before merging or releasing

Run the full Gradle checks:

```powershell
.\gradlew.bat :phone:testDebugUnitTest :watch:testDebugUnitTest :phone:lintDebug :watch:lintDebug :phone:assembleDebug :watch:assembleDebug --stacktrace
```

The next batches should add real DataStore upgrade and corruption tests, process-death/service-restart
tests, paired phone/watch tests with mixed released versions, and physical-hardware checks for LED,
audio, memory, and performance.
