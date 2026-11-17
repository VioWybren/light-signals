# Changelog

Notable changes since the production 3.03 version are consolidated below. v5.0.0 remains
**Unreleased**; add a publication date only after it is released.

## [v5.0.0] - Unreleased

### Added

- A phone Quick Settings tile that opens the controls page, with setup guidance in the menu.
- Version labels in the phone menu and watch sound screen, plus version details in Activity logs.
- A shared color slider with more colors, including orange and violet, and white at the end.
- Phone-led watch connection that launches the companion and transfers the phone configuration.
- Remembered companion relationships, availability checks and automatic resume after temporary
  interruptions. Explicit disconnect disables automatic resume.
- Repository/state, palette, SOS timing/cancellation and communication contract tests.
- Isolated Android test apps, shared test helpers, physical LED tests and optimized device builds.
- A manual Bitbucket verification pipeline and guides for testing, versioning, architecture
  and the Git workflow.
- KDoc for production and test declarations, including lifecycle and resource ownership.
- GitHub Actions verification with quick PR checks, manual full/release modes, cached builds
  and downloadable test/lint reports; a guide for preserving branches, tags and history during migration.

### Changed

- Set the shared display version to `v5.0.0`, phone upload code to `375001` and watch upload
  code to `375000`, all configured in root `gradle.properties`.
- Removed the phone's camera permission, permission requests, explanation dialogs, obsolete
  permission broadcasts and related localized text. Flashlight control still uses CameraManager.
- Made notification permission independent of flashlight operation on Android 13 and later.
- Kept phone controls usable during watch control. Each explicit watch action transfers a
  complete configuration; local phone edits are not continuously mirrored to the watch.
- Import the phone's current settings on resume and reconnection to the last connected phone.
  Preserve the running phone signal instead of replacing it with standalone watch settings.
- Route sound to the phone during companion control. Show sound off when remote playback is
  unavailable, while allowing an explicit offline choice to restore watch sound.
- Use the watch's media-volume range for its volume controls.
- Share the palette, SOS engine, signal snapshots and communication codecs through `core`.
- Make preference restoration asynchronous and wait for readiness before applying controls.
- Use lifecycle-aware state collection and explicit foreground ownership for watch resources.
- Refresh launcher, adaptive, monochrome and splash assets while preserving the brand design.
- Use localized accessibility labels and Wear Material 3 theming where appropriate.
- Replace the Material Icons library dependencies with the required XML vectors and retain
  their license attribution.
- Upgrade the Gradle wrapper to 9.9.1.
- Enable R8 code/resource optimization for `release` and isolated `releasecheck` builds.
  Preserve reflected Horologist Protobuf fields through targeted keep rules.

### Fixed

- LED blinking/SOS becoming steady or pausing when the phone display turns off. Timed sessions
  hold a partial CPU wake lock through cancellation-safe cleanup.
- Old torch jobs or service cleanup interfering with a newer signal. Serialized writes and
  ownership tokens protect replacement sessions.
- Missing foreground notification after the first notification-permission grant. Refresh the
  active notification immediately and remove it when the service stops.
- Watch discovery incorrectly treating an installed phone companion as missing after reopening.
- Stale connection indicators and incomplete recovery when either app returns or the physical
  connection becomes available again.
- Watch actions leaving the phone in a locally selected SOS/blink mode. Apply each action's
  captured configuration in order, including its mode and output selection.
- Locked-phone handoff being rejected solely because the Activity was not resumed. Initialize
  controls before the first draw and retain peer, readiness and session checks.
- Explicit reconnect stopping a running phone blink when the watch app remained open.
- Unhandled torch discovery, hardware and companion-operation failures.
- Unsafe restoration of saved torch sessions, invalid frequencies and out-of-range settings.
- Cancellation and queued preference writes being lost or treated as successful work.
- SoundPool stream tracking, queued playback restarting after stop and duplicate local audio.
- Watch cleanup depending on uninitialized playback resources during Activity stop.
- Cached watch pages changing brightness and incomplete restoration when leaving the signal UI.
- Palette discontinuities and compatibility with saved color positions from older versions.
- Splash clipping, monochrome proportions and the extra startup loading indicator.
- Minimum-API Quick Settings lint findings and Android 7 test-helper incompatibilities.
- Bitbucket SDK package installation and build-memory settings; keep debug JVM test tasks
  available when instrumented tests target `uitest`.
- Generated build output and local Android Studio device selection entering source control.

### Security and compatibility

- Validate modern companion requests against sender identity, capability, session, timestamp
  and increasing action sequence; reject malformed or inconsistent snapshots.
- Separate remembered relationship identity from the last-phone routing hint. The hint alone
  does not authorize control or automatic reconnection.
- Retain legacy DataItem fields, preference keys and the earlier command endpoint for compatibility.

### Release status

- Local Gradle 9.9.1 verification and optimized paired checks passed for v5.0.0. Earlier
  Android device/emulator results remain attributed to their tested candidates; see [TESTING.md](TESTING.md).
- Production signing acceptance, Play delivery, the real 3.03 upgrade and mixed-version pairing
  remain separate checks. v5.0.0 remains Unreleased.
- GitHub migration is being prepared because Bitbucket CI is unavailable. Repository transfer
  and the first GitHub Actions run must be verified before the migration is considered complete.
- Signing and publishing remain manual; no automated Play deployment is configured.
