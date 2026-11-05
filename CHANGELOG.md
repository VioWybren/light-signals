# Changelog

Notable changes are recorded here, beginning with v4.0.0. The entry below describes the
upcoming release; a release date should be added when it is published.

## [v4.0.0] - Unreleased

### Added

- Repository/state, SOS timing and cancellation, and phone/watch synchronization contract tests.
- Android device tests for permissions, notifications, foreground-service behavior, Activity
  recreation, Home/resume, watch brightness, and resource cleanup.
- An isolated `uitest` build type, shared test support, and a device-selection test script.
- A manual Bitbucket verification pipeline for tests, lint, debug/test APK builds, and report artifacts.
- A manual Git workflow guide covering the first development checkpoint, task branches, releases, and hotfixes.
- README, testing instructions, versioning guidance, and this changelog.

### Changed

- Unified both modules on `versionName = "v4.0.0"`, configured once in root `gradle.properties`.
- Set independent upload codes to `374001` for the phone and `374000` for the watch.
- Scoped UI state collection and watch signaling to the appropriate lifecycle.
- Made preference initialization asynchronous and guarded synchronization until state is ready.
- Send fresh control state on reconnect/resume without transmitting local blink phases.
- Use Wear Material 3 theming for the watch and resource-backed accessibility labels for controls.

### Fixed

- Keep the CPU awake during LED blinking/SOS with the display off, and release the partial
  wake lock after torch cleanup on mode changes, service shutdown, cancellation, or failure.
- Increased CI Gradle/D8 heap space and limited build concurrency to address heap exhaustion
  during watch dependency dex merging; bounded the Kotlin daemon heap separately.
- Restored debug JVM test tasks alongside the isolated `uitest` build by configuring AGP 9
  to generate unit-test components for all build types.
- Corrected the Bitbucket SDK platform package ID to `platforms;android-37.0` so a clean CI
  environment can install the required Android platform.
- Foreground-service startup crash when camera permission is missing, and flashlight operation
  being blocked by notification permission denial.
- Competing torch jobs and stale session cleanup interfering with a newer signal session.
- Unhandled flashlight discovery/hardware failures and companion-operation failures.
- Invalid saved frequencies, including NaN and infinity, restoring an unintended blink interval.
- Pending preference writes and cancellation handling during state changes and shutdown.
- SoundPool playback stream tracking and cleanup of queued playback.
- Watch brightness changes from inactive pages and incomplete brightness restoration on stop.
- Color-gradient discontinuities and invalid input handling.

### Security

- Restricted the phone's internal permission receiver to non-exported registration and scoped
  permission broadcasts to the application package.
