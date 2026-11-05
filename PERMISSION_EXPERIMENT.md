# Permission-free torch compatibility experiment

This branch investigates operating the phone LED without camera permission. It is separate from
the tested v4.0.0 checkpoint at Git tag `baseline/v4.0.0-camera` (commit `7b6f2fc`).
The shared version name and phone/watch version codes remain unchanged during the experiment.

## Implementation scope

- Use the existing `CameraManager.setTorchMode` implementation without opening a camera device.
- Remove CAMERA from the phone manifest, startup checks, runtime requests, and explanatory UI.
- Keep the foreground service, cancellation-safe torch ownership, partial CPU wake lock, and
  optional notification permission. Notification denial does not prevent LED operation.
- Keep the phone/watch Data Layer message format, SOS pattern, frequencies, and saved control
  state unchanged. The missing-permission broadcasts were internal phone UI plumbing, not
  phone/watch messages.
- Leave the baseline available for release preparation and independent refactoring. Do not
  treat the experiment as a verified replacement before its compatibility checks are complete.

The torch API is available from API 23, below this app's API 24 minimum. API availability is
not itself proof of behavior on every supported device. References:
[CameraManager](https://developer.android.com/reference/android/hardware/camera2/CameraManager#setTorchMode(java.lang.String,%20boolean))
and [notification permissions](https://developer.android.com/develop/ui/views/notifications/notification-permission).

## Automated verification

Run the full JVM/lint/build checks from [CI.md](CI.md) and the selected-device commands from
[TESTING.md](TESTING.md). The phone suite contains:

- A merged-manifest check and runtime check that CAMERA is not requested or granted.
- UI/service tests with a recording LED backend and matching fake flash availability, including optional notification permission,
  lifecycle changes, errors, and screen-off wake lock ownership/cleanup.
- `PhoneTorchHardwareTest`, using production bindings and actual camera-service callbacks for
  steady LED, blinking, and SOS. These tests do not adopt a shell camera permission. They are
  skipped on emulators and devices without a rear LED.

Read skipped-test counts. A passing emulator suite with skipped LED cases does not establish
physical flash compatibility. Keep the device model, API level, APK hashes, and test results
with each compatibility result.

## Compatibility targets

| Phone Android API | Purpose | Emulator/platform checks | Physical LED checks |
| --- | --- | --- | --- |
| 24 | Minimum supported Android | Passed: 11/11 selected cases in a complete emulator run | Physical Android 7 check pending |
| 28 or 29 | Older camera-service implementation | Unverified | Unverified |
| 30 to 32 | Background service/lifecycle behavior | Unverified | Unverified |
| 33 | Independent notification permission | Passed: 13/13 selected cases in a complete emulator run | Unverified |
| 34 | Foreground-service type requirements | Passed: 13/13 selected cases in a complete emulator run | Unverified |
| 35 | Additional platform coverage | Unverified | Unverified |
| 36, Samsung SM-A536E | Current screen-off regression device | Phone platform cases passed on real device; no emulator run | Three hardware cases and unplugged blinking/SOS checks passed |

Both phone and watch also need their existing Android suites. Pair the isolated `.uitest` apps
from the same build/signing environment, then verify that watch controls can start/stop the
phone LED and change blinking/SOS. Repeat mode changes with the phone display off while the
service is active. Confirm that the LED, notification, and CPU wake lock are cleaned up on stop.

## Results recorded on 2026-11-05

- Full Gradle JVM/lint/APK verification: `BUILD SUCCESSFUL`, reported from normal PowerShell.
- JVM tests: 74 passed (46 phone, 28 watch).
- Phone, Samsung SM-A536E, Android 16 / API 36: all 16 distinct Android cases passed across
  the initial run and two filtered reruns. All three real camera-service LED cases passed
  without CAMERA requested or granted.
- The initial phone run had five UI timeouts with the secure keyguard covering the app.
  After manual unlock, four affected UI cases passed together, followed by the remaining
  screen-off SOS case. This was not a single unattended green full-suite run. Retain the
  initial failure log with the successful rerun logs. See [TESTING.md](TESTING.md) for handling
  secure-lock phones without changing their security settings.
- Watch, Samsung SM-L310, API 36: five Android cases passed, with no skips.
- The isolated phone and watch app APKs use the same signing certificate. The user confirmed
  watch control of steady LED, blinking, SOS, and stopping, including mode changes/stopping
  with the phone display off while its LED service was already active.
- Outside instrumentation, the user confirmed unplugged screen-off blinking and SOS each
  continued correctly for at least three minutes using the usual Battery Saver setting,
  without a camera-permission request. Blink-interval extremes were not reported as checked.

The initial physical-device batch covered only the current phone and watch. The API 24,
API 33, and API 34 emulator results are recorded below; physical Android 7 LED behavior remains unverified.
Keep the permission-based baseline while the compatibility and release scope remain open.

## API 24 emulator follow-up

The Google APIs x86_64 emulator `New_Device`, running Android 7.0 / API 24, installed the
same isolated app and test APKs used for the current-phone checks. Its initial run passed
10 of 11 selected cases. Home/resume failed because the test helper's `am task focus` command
is not supported on Android 7; the app's LED request and foreground service remained active
before the test attempted to resume it.

The shared test helper now uses the public `ActivityManager.AppTask.moveToFront()` API with
an older-task-ID fallback. Both instrumentation APKs were rebuilt successfully. The second
API 24 run again passed 10 of 11 cases, with Home/resume now failing because
`ActivityScenario.state` threw during the transient `STARTED` stage after moving the task.

Phone and watch Home/resume tests now poll the original Activity's `Lifecycle.currentState`
on the main thread instead. They retain the same required stopped/resumed states and service,
brightness, and signal assertions. Both instrumentation APKs were rebuilt successfully again.
The API 24 Home/resume case then passed independently, followed by a complete run passing all
11 selected cases in 68.377 seconds. Both earlier failure logs were retained. Production app
code and its APK are unchanged.

The updated watch suite subsequently passed all five cases on the Samsung SM-L310 / API 36,
including both Home/resume checks. Its follow-up result and the API 33/API 34 emulator results
are recorded below. Physical Android 7 steady LED, blinking, SOS, and unplugged screen-off
checks remain pending access to the real device.

The two notification-permission cases are filtered out below API 33, and the three physical
LED cases are filtered out on emulators. Neither exclusion is a physical compatibility result.

## API 33 emulator follow-up

The Google APIs x86_64 emulator `New_Device_API_33`, running Android 13 / API 33, passed all
13 selected cases in one complete run (317.390 seconds). Both phone APK hashes match the
successful API 24 run; no source changes or rebuild were needed.

Both notification-permission cases executed successfully: granting permission left the LED
running, and denying it did not stop the LED or repeat the prompt after relaunch. Home/resume,
screen-off blinking/SOS, Activity recreation, foreground notification, hardware-failure
recovery, and cleanup also passed with the recording LED backend. The tests assert CAMERA
is absent from the installed manifest and is not granted.

The three physical LED cases were filtered out on the emulator. This result establishes
platform test coverage on this API/image, not physical flash compatibility.

## API 34 emulator follow-up

The `Pixel_8` phone emulator, running Android 14 / API 34, passed all 13 selected cases in one
complete run (434.537 seconds). Both phone APK hashes match the API 24 and API 33 runs; no
source changes or rebuild were needed.

This run exercises the Android 14 foreground-service startup path, including its declared
special-use type, along with notification grant/denial, Home/resume, screen-off blinking/SOS,
recreation, failure recovery, and cleanup. CAMERA absence and its denied runtime grant are
asserted by the tests. The LED backend is replaced for these platform cases; the three real
hardware cases were filtered out.

## Watch lifecycle follow-up

After the task-refocusing and lifecycle-polling corrections, the rebuilt watch instrumentation
suite passed all five cases in one complete run on the Samsung SM-L310 / API 36 (40.016 seconds).
Both Home/resume cases, brightness restoration, recreation, foreground ownership, and signal/window
cleanup passed. The watch app APK is unchanged. The temporary wake-up job was stopped and
removed when the run finished; the tests restore the prior media volume.

The targeted emulator batch (API 24, API 33, API 34) and updated watch suite are complete.
These results do not establish physical LED compatibility on Android 7 or coverage of the
other unverified rows in the matrix.

## Unplugged check

Run the ordinary `Light Signals Tests` app outside instrumentation, where it uses the real LED.
Confirm that Android's app permission page has no Camera permission for this test app. Start
blinking, unplug USB/charging, turn the display off, and observe for at least three minutes.
Repeat with SOS and at the blink interval extremes. Keep the user's normal Battery Saver
configuration and record it. Check steady light and stopping after waking the display.

Automated results cover only the recorded devices and APIs. Availability of suitable older
hardware may limit the compatibility claim; retain the baseline rather than infer physical
LED behavior from emulator or current-phone results.
