# Release versioning

Release metadata is defined once in the root `gradle.properties` file:

```properties
appVersionName=v4.0.0
phoneVersionCode=374001
watchVersionCode=374000
```

Both Android modules read `appVersionName`. Each reads its own upload code. The default
configuration supplies these values to `debug`, `release`, and the isolated `uitest` build type.

`versionName` is the product version shown to users. Use the same `vmajor.minor.patch` name for
the phone and watch. `versionCode` is an independent integer upload/update identifier; it is
not calculated from `targetSdk` or the semantic version.

For each release:

1. Update `appVersionName` once for the product release.
2. Give every module being uploaded an unused code greater than its previously published code.
3. Keep phone and watch codes distinct across the shared Play application, including codes
   already uploaded to internal, closed, open, or production tracks.
4. Keep codes within Google Play's positive-integer limit of 2,100,000,000.
5. Check the generated APK/AAB manifest before uploading. Uploading/publishing is a separate step.

The v4.0.0 codes exceed the previous repository values: phone 373041 and watch 373040.
The repository cannot establish the full Play Console upload history; verify the proposed codes
have not already been used there before publishing.

Changing this metadata retains application IDs, signing configuration, saved-preference keys,
and the phone/watch wire protocol. Version metadata alone does not perform a data migration.

Google's references:

- [Version your app](https://developer.android.com/studio/publish/versioning)
- [Package and distribute Wear OS apps](https://developer.android.com/training/wearables/packaging)
