# CI and local verification

## Current status

**Bitbucket Pipelines is unavailable for this workspace during the reported billing migration.**
The owner has contacted Atlassian support. No successful cloud verification is recorded for the
current release candidate. Git hosting and pull requests remain the manual collaboration process.

Continue with local checks, record their results in the pull request, and identify CI as
**not run — unavailable**. Local success is not a Bitbucket CI status. GitHub migration is
deferred; this repository has no configured GitHub Actions workflow or automated deployment.

## Local process while CI is unavailable

1. Work on a task branch and run the smallest relevant checks from [TESTING.md](TESTING.md).
2. Before merging code changes, check the affected modules and any shared contracts. Review
   the complete staged diff. Text-only documentation changes do not need APK/device rebuilds.
3. Record the tested commit (`git rev-parse HEAD`), commands, pass/fail/skip results and device
   details in the pull request. Include local changes in a commit before claiming that a
   particular commit was tested.
4. Review and merge manually. Check the integrated result where a merge or conflict resolution
   changes the tested source.
5. Use optimized artifacts and the release device checks before publishing. Signing and Play
   uploads remain manual.

For a combined local equivalent of the retained CI tasks:

```powershell
.\gradlew.bat :core:test :phone:testDebugUnitTest :watch:testDebugUnitTest :phone:lintDebug :watch:lintDebug :phone:assembleDebug :watch:assembleDebug :phone:assembleUitest :phone:assembleUitestAndroidTest :watch:assembleUitest :watch:assembleUitestAndroidTest --continue
```

This is a broad checkpoint, not the command to run after every small edit. Keep the local daemon
and incremental outputs. Release lint, R8 builds and device execution are separate steps in
[TESTING.md](TESTING.md).

## Retained Bitbucket pipeline

[bitbucket-pipelines.yml](bitbucket-pipelines.yml) defines one custom pipeline, `verify`.
There are no automatic push, pull-request or tag triggers. It can be started explicitly and
could be scheduled separately in Bitbucket.

It checks:

- `:core:test`, `:phone:testDebugUnitTest` and `:watch:testDebugUnitTest`.
- Debug lint for both apps.
- Both debug APKs and both isolated `uitest` apps/instrumentation APKs.

`--continue` collects independent failures; a failed task still fails the step. Instrumentation
APKs are built, but the pipeline does not start an emulator or run paired-device tests. It also
does not build release bundles, verify production signing or publish to Play.

### Build environment

The YAML currently specifies:

- Temurin JDK 21 on Ubuntu 24.04, pinned by image digest.
- The checked-in Gradle 9.8.2 wrapper and JetBrains JDK 21 daemon criteria.
- Android command-line tools build `15859902`, with a pinned SHA-256 check.
- SDK packages `platform-tools`, `platforms;android-37.0` and `build-tools;37.0.0`.
- A `2x` step, 60-minute timeout, one Gradle worker and no task parallelism.
- A 4 GiB Gradle/D8 heap and a separate 1.5 GiB Kotlin daemon heap.
- Caches for Gradle dependencies, wrapper distributions, JDK downloads and the Android SDK.

The pipeline accepts SDK licenses during setup. Gradle provisions the required daemon JDK;
application bytecode still targets Java 17. Keep SDK packages, tool versions, checksums and
image digests aligned when changing the build environment.

### Reports and artifacts

- JUnit XML: `core/build/test-results/test/` and each app's
  `build/test-results/testDebugUnitTest/`, captured on success or failure.
- HTML/lint/build reports: root and module `build/reports/`, captured on success or failure.
- Debug/test APKs: each app's `build/outputs/apk/`, captured after successful verification.

Test APKs use the container's debug signing key. Use the phone and watch from the same run;
they are not production-signed updates. No production keystore, password or Play credentials
are required by this pipeline.

## When Bitbucket becomes available

1. Keep the existing YAML when opening Bitbucket's pipeline setup screen.
2. Open **Pipelines → Run pipeline**, select the intended branch and custom `verify` pipeline.
3. Confirm the displayed commit and run it. Review the log, test results, skips and artifacts.
4. Record that result against that commit. A later code change needs relevant verification
   again; an earlier green run does not cover it.

Availability and Linux execution must be checked at that time. Static YAML review and local
Windows builds do not verify Bitbucket billing, quotas, network downloads or the runner image.

## References

- [Manual Git workflow](GIT_WORKFLOW.md)
- [Testing and release validation](TESTING.md)
- [Bitbucket manual/custom pipelines](https://support.atlassian.com/bitbucket-cloud/docs/pipeline-start-conditions/)
- [Bitbucket artifacts](https://support.atlassian.com/bitbucket-cloud/docs/use-bitbucket-artifacts-in-step/)
- [Bitbucket test reporting](https://support.atlassian.com/bitbucket-cloud/docs/test-reporting-in-pipelines/)
