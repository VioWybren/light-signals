# Manual Bitbucket verification

The root [bitbucket-pipelines.yml](bitbucket-pipelines.yml) defines one custom pipeline,
`verify`, for Bitbucket Cloud. It runs only when started manually, or if someone explicitly
adds a schedule in Bitbucket. Pushes, pull requests, and tags have no automatic triggers.

## First run

For the complete commit/push/review sequence and the existing v4.0.0 checkpoint, follow
[GIT_WORKFLOW.md](GIT_WORKFLOW.md).

1. Review and commit the pipeline, this guide, and the application/test changes you want to verify.
2. Push that commit to the intended Bitbucket branch, normally `develop`.
3. Enable Pipelines under the repository's **Repository settings > Pipelines > Settings**.
   Keep the checked-in YAML when using the setup screen.
4. Open **Pipelines > Run pipeline**, select the branch and the custom `verify` pipeline,
   then start it. Check that the displayed commit is the one you intended to verify.
5. Inspect the build log, test results, and downloadable artifacts before merging the changes.

No additional repository variables or release credentials are required. The pipeline installs
the Android SDK and accepts its licenses non-interactively as part of the build setup.
The initial run downloads SDK packages, Gradle, the daemon JDK, and dependencies, so it takes
longer than runs with populated caches.

## What it checks

- Shared `core` JVM tests and both Android app modules' JVM unit tests.
- Debug lint for the phone and watch.
- Both debug APKs.
- Both isolated `uitest` apps and their Android instrumentation APKs, including Hilt/KSP generation.

The root `android.onlyEnableUnitTestForTheTestedBuildType=false` setting keeps the debug JVM
test tasks available alongside the `uitest` instrumentation build. Without it, AGP 9 generates
unit tests only for `uitest`, and the pipeline's `testDebugUnitTest` targets do not exist.
The Kotlin/JVM `core` module uses the standard `:core:test` task and has no Android build type.

The Gradle invocation uses `--continue` to collect results from independent tasks when a task
fails. A failed task still fails the pipeline. Test XML and available HTML reports are captured
on success or failure; APK artifacts are captured after successful verification.

Instrumentation APKs are built here. Run the actual device tests separately using
[TESTING.md](TESTING.md). These checks need a connected phone/watch or a suitable emulator;
this pipeline does not start an emulator or connect to your personal devices.

## Build environment

- Official Temurin JDK 21 / Ubuntu 24.04 image, pinned by its registry digest.
- The checked-in Gradle wrapper and JetBrains JDK 21 daemon criteria.
- Android command-line tools build `15859902`, verified against Google's published SHA-256.
- Android Platform 37 (`platforms;android-37.0`), Build Tools 37.0.0, and Platform-Tools.
- A `2x` step (8 GB of memory), a 60-minute timeout, and one Gradle worker with task parallelism disabled.
- CI heap limits of 4 GiB for Gradle/D8 and 1.5 GiB for the Kotlin compiler daemon.
- Caches for Gradle dependencies, wrapper distributions, downloaded JDKs, and the Android SDK.

The bootstrap Temurin JDK starts Gradle; Gradle provisions the JetBrains daemon JDK required by
`gradle/gradle-daemon-jvm.properties`. Application bytecode continues to target Java 17.
The SDK cache is invalidated when the YAML or either module's build script changes.

The pipeline overrides JVM settings on the Gradle command line. D8 runs inside the Gradle
build JVM and can exceed the project's 2 GiB default during dependency dex merging. The
larger CI heap and single worker reduce memory pressure when building both applications and
their test APKs. Kotlin gets a separate limit because its daemon otherwise inherits Gradle's
heap setting. The runner also needs memory for metaspace, native tools, test JVMs, and the OS.

When updating the build image, refresh its digest. When updating the Android command-line
tools, change the build number and checksum together. Keep the SDK packages aligned with the modules'
`compileSdk` and the Android Gradle Plugin's Build Tools requirements.

## Release boundary

The generated APKs use the build container's debug signing key. Use phone/watch APKs from the
same run when testing companion communication. These artifacts are for development/testing.
Release signing and Google Play uploads remain manual, using the existing production signing
setup. This pipeline has no deployment step and needs no production keystore or Play credentials.

Cloud execution is confirmed only after the first successful Bitbucket run. Local YAML/schema
and shell syntax checks cannot validate Bitbucket quotas, network access, SDK downloads, or
the complete Linux build.

## References

- [Bitbucket custom manual pipelines](https://support.atlassian.com/bitbucket-cloud/docs/pipeline-start-conditions/)
- [Bitbucket artifact configuration](https://support.atlassian.com/bitbucket-cloud/docs/use-bitbucket-artifacts-in-step/)
- [Bitbucket test reporting](https://support.atlassian.com/bitbucket-cloud/docs/test-reporting-in-pipelines/)
- [Official Temurin build images](https://github.com/adoptium/containers)
- [Android command-line tools and published checksums](https://developer.android.com/studio)
- [Google SDK repository package metadata](https://dl.google.com/android/repository/repository2-3.xml)
- [Gradle build JVM memory](https://docs.gradle.org/current/userguide/config_gradle.html#sec:configuring_jvm_memory)
- [Kotlin daemon JVM settings](https://kotlinlang.org/docs/gradle-compilation-and-caches.html#setting-kotlin-daemon-s-jvm-arguments)
- [Bitbucket step resources](https://support.atlassian.com/bitbucket-cloud/docs/step-options/#Size)
