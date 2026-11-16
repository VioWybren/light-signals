# Versioning

## Current release configuration

The upcoming release is **v5.0.0**, still **Unreleased**. Root
[gradle.properties](gradle.properties) defines:

```properties
appVersionName=v5.0.0
phoneVersionCode=375001
watchVersionCode=375000
```

| Module | `versionName` property | `versionCode` property | Current code |
| --- | --- | --- | ---: |
| `phone` | `appVersionName` | `phoneVersionCode` | 375001 |
| `watch` | `appVersionName` | `watchVersionCode` | 375000 |

Both modules read these properties in `defaultConfig`. The values also apply to `debug`,
`uitest` and `releasecheck`; test variants change the application ID, not the version values.
`core` is a library and has no Android upload code.

## Name and upload codes

- `versionName` is the user-facing product version. Use the same `vmajor.minor.patch` name
  in both modules. UI labels and startup logs read generated `BuildConfig` values.
- `versionCode` is Android's integer update identifier. It is independent of the display name,
  target SDK and communication protocol.
- Phone and watch are separate artifacts. Either can be built and uploaded independently;
  changing one does not require uploading the other.

Google Play requires unique codes across form factors in the shared listing. For every
artifact being uploaded, use an unused code greater than the previous code for that artifact.
Check every testing and production track, not only the current production release. The maximum
allowed code is 2,100,000,000. See [Android versioning](https://developer.android.com/studio/publish/versioning)
and [Wear OS packaging](https://developer.android.com/training/wearables/packaging).

The current codes are greater than the earlier v4.0.0 candidate codes, 374001 and 374000.
Only Play Console can confirm that 375001 and 375000 are still unused; the repository cannot
establish the complete upload history.

## Preparing an upload

1. Set the shared product name once. Keep both module configurations on that name even if
   only one artifact is being uploaded.
2. Increase the code for each artifact that needs a new upload. An unchanged, unuploaded
   artifact does not need a new code.
3. Keep [CHANGELOG.md](CHANGELOG.md) under **Unreleased** until publication.
4. Build and verify the actual artifacts, rather than relying only on source properties:

   ```powershell
   .\gradlew.bat :phone:bundleRelease :watch:bundleRelease
   ```

   For a phone-only or watch-only upload, request only that module's task. Plain Gradle
   release builds have no production signing configuration; use the existing Android Studio
   signed-bundle process before uploading.
5. Check the final package ID, version name/code, non-debuggable manifest and signing identity.
   Test the signed candidate through the appropriate Play track or a compatible signed APK.
6. Record the approved commit, artifact hashes and mapping files with the release. Follow
   [GIT_WORKFLOW.md](GIT_WORKFLOW.md#release-workflow).

Version changes do not rename preference keys, migrate stored data or change RPC endpoints.
The production application ID remains `nodomain.greensnowapps.lightsignals`; `.uitest`
installations cannot prove a production-app upgrade. See [TESTING.md](TESTING.md#remaining-coverage).
