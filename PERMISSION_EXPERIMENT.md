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
| 24 | Minimum supported Android | Unverified | Unverified |
| 28 or 29 | Older camera-service implementation | Unverified | Unverified |
| 30 to 32 | Background service/lifecycle behavior | Unverified | Unverified |
| 33 | Independent notification permission | Unverified on API 33 | Unverified |
| 34 to 35 | Current foreground-service restrictions | Unverified on these APIs | Unverified |
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

Only the current phone and watch were available. No older-Android compatibility result is
claimed. Keep the permission-based baseline while those matrix entries remain unverified.

## Unplugged check

Run the ordinary `Light Signals Tests` app outside instrumentation, where it uses the real LED.
Confirm that Android's app permission page has no Camera permission for this test app. Start
blinking, unplug USB/charging, turn the display off, and observe for at least three minutes.
Repeat with SOS and at the blink interval extremes. Keep the user's normal Battery Saver
configuration and record it. Check steady light and stopping after waking the display.

The current-phone automated results cover only the device and API recorded above. Availability
of suitable older hardware may limit the compatibility claim; retain the baseline rather than
infer older-device behavior from a current-phone result.
