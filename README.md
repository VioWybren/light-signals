# Light Signals

Light Signals displays steady light, blinking patterns and SOS on an Android phone or
Wear OS watch. The watch can work on its own or control the companion phone's screen,
flashlight and sound.

## Release status

**v5.0.0 is unreleased.** Both apps use the same display version and separate upload codes:

| Module | Version name | Version code |
| --- | --- | ---: |
| `phone` | `v5.0.0` | 375001 |
| `watch` | `v5.0.0` | 375000 |

These values come from root [gradle.properties](gradle.properties). They describe the source
configuration, not a published Play release. See [VERSIONING.md](VERSIONING.md) and
[CHANGELOG.md](CHANGELOG.md).

## Features

- Steady screen light, adjustable blinking and a repeating SOS pattern.
- A shared phone/watch color palette with white at the end of the slider.
- Phone flashlight control without requesting camera permission.
- Optional signal sounds and watch media-volume controls.
- Companion launch, connection and reconnection through the Wearable Data Layer.
- Watch control while the phone is locked, subject to Android and device restrictions.
- A phone Quick Settings tile that opens Light Signals controls.
- Version labels in both apps, refreshed launcher/splash artwork and localized controls.

Phone controls remain available while linked. Their local changes are not continuously sent
to the watch. A later watch action applies the watch's complete signal configuration to the
phone; reconnecting imports the phone's current configuration. See
[ARCHITECTURE.md](ARCHITECTURE.md#phone-and-watch-communication) for the full contract.

## Modules and requirements

| Module | Purpose | Minimum Android API | Compile / target API |
| --- | --- | ---: | ---: |
| `core` | Shared Kotlin/JVM signal logic and communication codecs | Not an Android app | Not applicable |
| `phone` | Android phone application | 24 | 37 |
| `watch` | Wear OS application | 30 | 37 |

`phone` and `watch` each depend on `core`. They produce separate apps and can be built and
uploaded independently. `test-support` and `instrumentation-support` are shared source
directories, not additional Gradle modules.

The checked-in toolchain is:

| Tool | Version |
| --- | --- |
| Gradle wrapper | 9.9.1 |
| Android Gradle Plugin | 9.5.1 |
| Kotlin | 2.4.21 |
| Gradle daemon JDK | JetBrains JDK 21, selected by daemon JVM criteria |
| Java / Kotlin bytecode target | 17 |

Use Android Studio compatible with the configured Android Gradle Plugin and a JDK 21 build
environment. Install Android SDK Platform 37, Build Tools 37.0.0 and Platform-Tools. The
command-line SDK package ID used by CI is `platforms;android-37.0`.

Companion control needs Google Play services, an available Wearable connection and matching
phone/watch app IDs and signing certificates. Screen signals do not require flashlight hardware.

## Build and run

1. Open the repository root in Android Studio and sync Gradle.
2. Set the local SDK location through Android Studio or an ignored `local.properties` file,
   for example `sdk.dir=C:/path/to/Android/Sdk`.
3. Let Gradle download the configured toolchain and any uncached dependencies.

Run commands from the repository root in PowerShell:

```powershell
.\gradlew.bat :phone:assembleDebug :watch:assembleDebug
```

Debug APKs are written to `phone/build/outputs/apk/debug/` and
`watch/build/outputs/apk/debug/`. To build one app, request only its task; Gradle builds its
`core` dependency as needed.

For isolated device testing:

```powershell
.\gradlew.bat :phone:assembleUitest :watch:assembleUitest
```

In Android Studio, select `uitest` for both modules in **Build Variants**, select the module
and device, then use **Run**. Both apps appear as **Light Signals Tests** and use
`nodomain.greensnowapps.lightsignals.uitest`, with separate data from the regular app.
Use the test apps together; mixing a regular app with a test app does not form a companion pair.

The `debug` and `release` variants use `nodomain.greensnowapps.lightsignals`. Use `uitest`
when keeping an existing Play installation intact. See [TESTING.md](TESTING.md) for automated
tests and optimized `releasecheck` builds.

## Permissions and lifecycle

- Flashlight control uses `CameraManager.setTorchMode`; the app does not declare or request
  camera permission, open a camera, take photos or record video.
- Android 13+ notification permission is optional. Denial does not block flashlight controls.
- Active phone LED work uses a foreground service, with the special-use type on Android 14+.
  Notification visibility still follows the user's notification settings.
- Blinking and SOS hold a partial CPU wake lock during the LED session and final cleanup.
  They can continue with the display off; this does not bypass every Doze or manufacturer limit.
- Watch screen and sound work follow foreground ownership. Leaving the signal page restores
  its brightness; leaving the app stops local signaling.

## Testing and release builds

Run all local JVM tests:

```powershell
.\gradlew.bat :core:test :phone:testDebugUnitTest :watch:testDebugUnitTest
```

For daily work, run the affected module or test class first. Reserve R8 builds and the full
device matrix for changes that need them. [TESTING.md](TESTING.md) lists commands, recorded
results and remaining coverage gaps, including final v5 artifact and real upgrade verification.

Release and `releasecheck` variants enable R8 code/resource optimization. Targeted Protobuf
keep rules are in each app's `src/main/keepRules/` directory. UI control icons use XML vectors;
see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for attribution.

Release signing and Play uploads are manual. A plain `bundleRelease` build does not apply a
production signing configuration. See [GIT_WORKFLOW.md](GIT_WORKFLOW.md#release-workflow).

## Development and CI

Develop on short branches from `develop`, review through a pull request, and merge an approved
release into `master`. Android Studio can handle editing, testing, commits and pushes.
See [GIT_WORKFLOW.md](GIT_WORKFLOW.md).

GitHub migration is prepared in [MIGRATION_TO_GITHUB.md](MIGRATION_TO_GITHUB.md). Keep
`master` and `develop`, copy all intended branches/tags, and keep Bitbucket as a fallback until
remote references and the first GitHub CI run are verified.

[GitHub Actions](.github/workflows/verify.yml) runs JVM/lint checks on pull requests. Longer
APK and optimized release builds are manual. Production signing and Play uploads remain manual;
CI needs no signing keys. See [CI.md](CI.md) for modes, reports and migration status.

## Documentation

- [Architecture](ARCHITECTURE.md): module boundaries, state, lifecycle and communication.
- [Testing](TESTING.md): fast checks, device tests, release checks and evidence limits.
- [Versioning](VERSIONING.md): the shared name and separate upload codes.
- [Changelog](CHANGELOG.md): consolidated unreleased v5.0.0 changes.
- [CI](CI.md) and [Git workflow](GIT_WORKFLOW.md): the current manual process.

KDoc covers named declarations and constructor properties in production and test sources.
Keep documentation aligned with code when changing behavior.
