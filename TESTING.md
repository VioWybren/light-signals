# Testing

Run commands from the repository root in PowerShell. Use Android Studio's normal **Run**
and test actions when preferred. Choose the smallest check that covers a change; a full R8
build and both device suites are not needed after every edit.

## Everyday checks

| Change | Start with | Add when needed |
| --- | --- | --- |
| KDoc or Markdown only | Review the diff, links and examples | No APK or device rebuild for text-only changes |
| Shared signal or codec logic | `:core:test` | Both app JVM suites for integration changes |
| Phone state or repository | `:phone:testDebugUnitTest` | Phone lint and affected device tests |
| Watch state or repository | `:watch:testDebugUnitTest` | Watch lint and affected device tests |
| Compose UI | Affected app's `assembleUitest`, then Run | Relevant lifecycle/UI tests |
| Manifest, permissions, service or hardware | Affected app's lint and Android tests | Physical device check |
| Companion communication | Core/app contract tests | Matching phone/watch device check |
| R8, keep rules or release dependencies | Optimized builds | Paired optimized smoke check |

Run all local JVM suites:

```powershell
.\gradlew.bat :core:test :phone:testDebugUnitTest :watch:testDebugUnitTest
```

Or run one suite or a focused class:

```powershell
.\gradlew.bat :phone:testDebugUnitTest
.\gradlew.bat :phone:testDebugUnitTest --tests "*.TorchSignalSessionTest"
.\gradlew.bat :phone:lintDebug
```

Use `:watch:testDebugUnitTest` and `:watch:lintDebug` for watch-only work. Leave the local Gradle
daemon and incremental outputs available. Avoid routine `clean`, `--rerun-tasks` and repeated
release builds. Use `--stacktrace` after a failure or `--profile` when investigating build time.

JVM HTML reports are written to:

- `core/build/reports/tests/test/index.html`
- `phone/build/reports/tests/testDebugUnitTest/index.html`
- `watch/build/reports/tests/testDebugUnitTest/index.html`

## What local tests cover

- SOS dot/dash timing, gaps, repetition, cancellation and failed output writes.
- Shared palette anchors, slider conversion and compatibility with older saved colors.
- Preference defaults, invalid/non-finite values, safe torch restoration and queued writes.
- Repository initialization, foreground ownership, replacement jobs and recoverable failures.
- Torch ownership, serialized hardware access and CPU-lock cleanup through cancellation/failure.
- Legacy DataMap fields/types/defaults and modern connection, resume and command layouts.
- Ordered action snapshots, mode replacement, readiness, session validation and legacy fallback.

JUnit 4 and `kotlinx-coroutines-test` use virtual time for deterministic logic checks. Internal
constructors allow fake callbacks, scopes and stores; Hilt uses the production constructors.
Root `android.onlyEnableUnitTestForTheTestedBuildType=false` keeps debug JVM tasks available
while instrumented tests target `uitest`.

`test-support` supplies an in-memory Preferences fake and independent wire fixtures. The SOS
reference is `core/src/test/resources/contracts/sos-v1.csv`. Keep fixtures independent of the
production encoders. JVM fakes do not prove Android persistence, permissions, radio delivery
or hardware behavior.

## Android device tests

### Choose the correct apps

Select **uitest** for both app modules in Android Studio's **Build Variants** window. Both
install as **Light Signals Tests**, using `nodomain.greensnowapps.lightsignals.uitest` and
separate data from the regular app. Run the phone module on the phone and watch module on
the watch. Keep regular Light Signals apps closed during paired checks.

Instrumentation targets `uitest`. Build the apps and test APKs when needed:

```powershell
.\gradlew.bat :phone:assembleUitest :phone:assembleUitestAndroidTest :watch:assembleUitest :watch:assembleUitestAndroidTest
```

For a simple manual UI check, build/run only the affected `uitest` app; no instrumentation
APK is needed. In Android Studio, run JVM or Android test classes from their gutter actions
with the appropriate variant/device selected.

### Select devices from PowerShell

```powershell
adb devices -l
$phoneSerial = Read-Host 'Enter the phone serial shown by adb devices'
.\scripts\Test-Android.ps1 -Module phone -Serial $phoneSerial
$watchSerial = Read-Host 'Enter the watch serial shown by adb devices'
.\scripts\Test-Android.ps1 -Module watch -Serial $watchSerial
```

Here, a serial is the device identifier in the first output column, not its model name.
`adb` must be on PATH; Android Studio also supplies it in the SDK's `platform-tools` directory.

The script checks the device type, builds the APKs, verifies the isolated app ID, clears only
the existing test app's data and runs `connectedUitestAndroidTest` for the selected device.
It finds ADB through `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `local.properties` or `-AdbPath`.
For watch runs, a temporary job wakes the display every five seconds and stops on script exit.

Android Test Orchestrator clears test-app data between cases. It does not unlock a secure
keyguard. Keep devices unlocked for UI cases, run screen-off/locked cases last and unlock
between filtered runs when necessary. Preserve failures and rerun results separately; filtered
passes are not one unattended full-suite pass. Wireless ADB can disconnect independently of
the app's Wearable connection.

### Suite responsibilities

| Suite | Covers | Prerequisites / limits |
| --- | --- | --- |
| `PhoneAndroidBehaviorTest` | Permission absence, optional notifications, real foreground service, recorded LED phases, wake locks, recreation and cleanup | Uses a recording torch and matching availability binding; suitable for emulators |
| `PhoneTorchHardwareTest` | Actual CameraManager torch callbacks, steady/blink/SOS, screen-off operation and cleanup | Physical rear LED required; unsupported devices skip |
| `PhoneWatchBackgroundBehaviorTest` | Real phone handoff handlers while stopped, state-read reconnect and cold initialization behind keyguard | Some cases require a matching watch app or secure keyguard; inspect skipped assumptions |
| `WearAndroidLifecycleTest` | Foreground ownership, Home/resume, keep-screen-on, cached pages, brightness restoration and recreation | Wear device/emulator; tests restore prior media volume |

The background-handler tests invoke real handlers in the unoptimized app; they do not replace
watch-to-phone radio and remote-launch checks. API 33+ notification cases skip on older Android.
Emulator runs do not prove physical LED compatibility.

Connected reports are under each app's `build/reports/androidTests/connected/` and
`build/outputs/androidTest-results/connected/` directories. These suites do not claim complete
process-death or OS service-restart coverage.

## Optimized release validation

Before a release, run the combined check once from the approved source:

```powershell
.\gradlew.bat :core:test :phone:testDebugUnitTest :watch:testDebugUnitTest :phone:lintRelease :watch:lintRelease :phone:bundleRelease :watch:bundleRelease :phone:assembleReleasecheck :watch:assembleReleasecheck
```

If the JVM suites already passed and only keep rules changed, repeat the affected optimized
builds and device checks rather than every earlier test.

`releasecheck` uses R8, debug signing and the `.uitest` app ID. Select it in Android Studio
and use **Run** on both devices, or install the built APKs using the serials collected above:

```powershell
adb -s $phoneSerial install -r -t .\phone\build\outputs\apk\releasecheck\phone-releasecheck.apk
adb -s $watchSerial install -r -t .\watch\build\outputs\apk\releasecheck\watch-releasecheck.apk
```

These replace the corresponding test apps. Use matching IDs and certificates on the pair.
Reinstall the unoptimized `uitest` apps before running instrumentation.

For manual release checks:

1. Check screens, icons, version labels, sound selection, watch volume and brightness restoration.
2. Connect from the phone and verify that the watch imports its settings. With Blink selected
   on the watch, select SOS on the phone. Check that each watch color, flashlight and sound
   action restores the watch's complete configuration, selecting phone SOS again before each.
3. Close/reopen each app and interrupt/restore connectivity. A remembered relationship can resume;
   explicit disconnect must stay disconnected until the user reconnects.
4. Open the phone app once, dismiss it, then lock it. Connect from the watch, wait about 30 seconds
   and exercise LED/Blink/SOS/stop without unlocking. Android force-stop is a separate scenario.
5. While the locked phone is blinking, disconnect/reconnect from the watch, first keeping its
   app open and then dismissing/reopening it. The blink must continue and the watch must import
   current phone settings.
6. Unplug the phone and observe screen-off blinking for at least three minutes, then SOS for
   at least three minutes. Include fast/slow blinking and record Battery Saver, model and OS.
7. Stop LED and sound; check notification removal. Record the exact commit, variant, versions,
   devices, results and any skips.

USB and instrumentation activity can keep the CPU awake. Automated wake-lock checks do not
replace unplugged observation or establish forced-Doze behavior.

Release artifacts are under each app's `build/outputs/bundle/release/`. Retain the corresponding
`build/outputs/mapping/release/mapping.txt` for crash deobfuscation. Measure uncompressed DEX
separately from AAB/download size. A locally debug-signed `releasecheck` app does not verify
production signing or an in-place Play upgrade.

## Recorded results

Last documentation review: **16 November 2026**. The following are retained results, not new
tests run during this documentation edit. The latest runtime/release checks used the preceding
permission-free **v4.0.0 candidate**, before the v5.0.0 version-only change.

| Check | Recorded result | Scope |
| --- | --- | --- |
| JVM suites, 14 November | 147 passed: core 21, phone 64, watch 62; no failures/errors/skips | Normal local Gradle run; these counts also match current test declarations |
| Debug and release lint | Zero errors; phone 29 warnings, watch 37 warnings | Candidate reports; not warning-free |
| Phone SM-A536E, API 36 | Platform, hardware and stopped/locked-phone regressions passed | Filtered runs with manual unlocking; not one unattended suite |
| Watch SM-L310, API 36 | Five lifecycle cases passed | Physical watch |
| API 24 emulator | 11 applicable phone cases passed; optimized startup/tile check passed | Minimum-API software behavior, no physical LED proof |
| API 33 / 34 emulators | 13 selected cases passed on each | Earlier permission-free checkpoint; not a fresh final-candidate run |
| SM-A520, Android 8.0.0 / API 26 | Manual steady/Blink/SOS/stop and unplugged screen-off checks passed with Battery Saver enabled | Earlier permission-free debug APK, confirmed by user; no ADB automation |
| Optimized paired apps, 14 November | Locked launch/control, both explicit reconnect paths, SOS and stop passed | User report on refreshed matching test APKs |
| Unplugged screen-off LED | Blink and SOS each passed at least three minutes | User report; usual Battery Saver setting, exact on/off value not separately recorded |
| Release packaging | Required Protobuf fields preserved; optimized APK ZIP and inspected 64-bit ELF alignment passed 16 KB checks | Static inspection, not runtime proof on a 16 KB-page device |
| v5.0.0 source metadata | Shared `v5.0.0`, phone 375001, watch 375000 checked | Does not verify newly signed artifacts or Play delivery |

Earlier permission checks are detailed in [PERMISSION_EXPERIMENT.md](PERMISSION_EXPERIMENT.md).
The validation session retained Gradle XML, lint reports, artifact hashes and manual results
outside the repository. Keep future release evidence with the tested commit and artifact hashes.
Bitbucket has no successful CI result recorded for the current candidate; see [CI.md](CI.md).

## Remaining coverage

- Final signed v5.0.0 artifacts: package, version/code, signature, optimized behavior and exact
  source-to-artifact association. The retained v5 record stops at source metadata checks.
- A real 3.03-to-v5.0.0 update preserving production app data. Keep the old app installed and
  update through Play internal testing or an APK with an accepted app-signing identity.
  An AAB is for Play upload; an APK is for local installation. An upload key may differ from
  the installed app-signing key.
- Mixed released-version phone/watch pairs and broader manufacturer/OS coverage, including
  physical Android 7 LED operation.
- Real DataStore migration/corruption, process death, OS service recreation and force-stop behavior.
- Extended Doze/manufacturer restrictions, long-duration battery/audio/memory testing and
  runtime verification on a 16 KB-page-size device.
- Restored cloud CI coverage when the Bitbucket billing issue is resolved or migration is completed.

Do not convert an assumed or planned check into a passed result. Publication and upgrade
approval remain separate from the local checks above.
