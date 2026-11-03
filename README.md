# Light Signals

Light Signals is an Android phone and Wear OS app for showing light signals with a screen
or the phone's flashlight. It supports steady light, adjustable blinking, SOS patterns,
selectable screen colors, and optional signal sounds. The watch can show signals on its own
screen and control the companion phone app through the Wearable Data Layer.

## Modules and requirements

| Module | Device | Minimum API | Compile / target API |
| --- | --- | --- | --- |
| `phone` | Android phone | 24 | 37 |
| `watch` | Wear OS watch | 30 | 37 |

The project uses Kotlin, Jetpack Compose, Material 3, Hilt, coroutines/StateFlow, Preferences
DataStore, and Google Play services Wearable APIs. The watch UI uses Wear Compose components.
Companion control requires the app on both paired devices and an available Wearable connection.

The checked-in build configuration uses:

| Component | Version |
| --- | --- |
| Gradle wrapper | 9.8.2 |
| Android Gradle Plugin | 9.5.0 |
| Kotlin | 2.4.21 |
| Gradle daemon JDK | JetBrains JDK 21, selected by daemon JVM criteria |
| Java / Kotlin bytecode target | 17 |

See `gradle/libs.versions.toml` and `gradle/gradle-daemon-jvm.properties` for the build's
dependency versions and daemon requirements.

## Getting started

1. Open the repository root in Android Studio and let Gradle sync.
2. Install Android SDK Platform 37 and SDK Platform-Tools through the SDK Manager.
3. Configure the SDK location in your local `local.properties`, for example
   `sdk.dir=C:/path/to/Android/Sdk`. This machine-specific file is ignored by Git.

Use the checked-in Gradle wrapper. The initial sync/build needs access to download the
configured toolchain and dependencies that are not already cached.

Run these commands from the repository root in PowerShell:

```powershell
.\gradlew.bat :phone:assembleDebug :watch:assembleDebug
```

The APKs are written to `phone/build/outputs/apk/debug/` and
`watch/build/outputs/apk/debug/`.

Both modules use `nodomain.greensnowapps.lightsignals` as the application ID for `debug` and
`release`. For isolated device testing, use the `uitest` build type described in
[TESTING.md](TESTING.md). It installs as **Light Signals Tests** with the `.uitest` suffix
and separate app data.

## Permissions and operation

- The phone requests camera permission to operate the flashlight; it does not take photos
  or record video. Screen signaling does not require flashlight hardware.
- Notification permission is optional. Denying it does not disable the flashlight.
- Active phone flashlight work uses a foreground service and can continue while the phone
  screen is off.
- Watch screen signaling follows the Activity lifecycle. It stops when the app leaves the
  foreground and restores window brightness when leaving the signal screen.

## Project structure

| Path | Purpose |
| --- | --- |
| `phone/` | Phone UI, state, preferences, flashlight service, and synchronization receiver |
| `watch/` | Watch UI, state, preferences, sound, and companion control |
| `test-support/` | Shared JVM test fakes and independent SOS/synchronization contract fixtures |
| `instrumentation-support/` | Shared Android test runner and device helpers |
| `scripts/Test-Android.ps1` | Runs an isolated Android suite on a selected phone or watch |

Each application has Compose screens and ViewModels, repositories for state and persistence,
and wrappers around platform resources. Shared support directories are compiled into test
source sets only.

## Testing

Run both local JVM suites:

```powershell
.\gradlew.bat :phone:testDebugUnitTest :watch:testDebugUnitTest --stacktrace
```

Run debug lint:

```powershell
.\gradlew.bat :phone:lintDebug :watch:lintDebug --stacktrace
```

See [TESTING.md](TESTING.md) for Android test APK builds, device selection, report locations,
covered behavior, and remaining coverage. Device tests use an isolated app and Android Test
Orchestrator. The phone suite substitutes a recording torch driver, so physical LED behavior
needs a separate hardware check.

## Continuous integration

[bitbucket-pipelines.yml](bitbucket-pipelines.yml) defines a manually started `verify` pipeline
for Bitbucket Cloud. It runs JVM tests and lint, builds both debug apps and their isolated
Android test APKs, and saves reports and APK artifacts. See [CI.md](CI.md) for setup and
run instructions. Release signing and publishing remain manual.

## Versioning and release notes

The shared display version and independent phone/watch upload codes are defined in root
`gradle.properties`. Follow [VERSIONING.md](VERSIONING.md) when preparing a release and
record notable changes in [CHANGELOG.md](CHANGELOG.md).

Follow [GIT_WORKFLOW.md](GIT_WORKFLOW.md) for the complete manual Git process: the first
`develop` commit/push, daily task branches, pull requests, release tags, and production hotfixes.
