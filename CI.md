# GitHub Actions verification

## Migration status

[.github/workflows/verify.yml](.github/workflows/verify.yml) is the new verification workflow.
The repository migration and first GitHub-hosted run must be completed before cloud CI is
considered verified. Follow [MIGRATION_TO_GITHUB.md](MIGRATION_TO_GITHUB.md).

Bitbucket Pipelines remains unavailable during the reported billing migration. Its YAML is
retained as a fallback; GitHub does not execute it. Git history is copied separately from CI
configuration. Signing and Play publishing remain manual.

## Checks and triggers

| Mode | Trigger | Work |
| --- | --- | --- |
| `quick` | Pull requests targeting `develop` or `master`; also manual | Core and app JVM tests, phone/watch debug lint |
| `full` | Manual | Quick checks plus both debug apps, isolated `uitest` apps and instrumentation APKs |
| `release` | Manual | JVM tests, release lint, both release bundles and optimized `releasecheck` APKs |

There is no automatic full/release build on push or tag and no deployment job. A newer run
for the same PR/ref cancels an older run in that group. Avoid starting duplicate long builds.
Quick checks can still take time on a cold cache; no fixed runtime is promised.

The workflow must exist on GitHub's default branch, `master`, before the **Run workflow**
button can start it manually. Select the intended branch and mode in **Actions → Verify Android**.
Confirm the run's commit before treating it as verification for a release.

## Build environment

- GitHub-hosted `ubuntu-24.04`, selected explicitly rather than the moving `ubuntu-latest` label.
- Temurin JDK 21 for startup; checked-in criteria select the JetBrains JDK 21 Gradle daemon.
- The Gradle wrapper, currently 9.9.1; Java/Kotlin bytecode remains target 17.
- The runner's Android SDK, with `platforms;android-37.0`, `build-tools;37.0.0` and Platform-Tools
  ensured before the build. No custom SDK ZIP bootstrap is needed.
- Gradle build cache, wrapper validation and cached Gradle/JDK downloads.
- One Gradle worker, no task parallelism, 4 GiB Gradle heap and 1.5 GiB Kotlin daemon heap.

Actions are pinned to reviewed commit SHAs. Gradle setup uses the open-source v5 action.
PR runs read caches; trusted manual runs on `master` or `develop` can update them. The workflow
does not publish Build Scans or submit dependency snapshots.

## Results and artifacts

The job summary shows per-module JVM test totals, failures, errors and skips, plus lint error
and warning counts. Missing reports are shown as missing, not passing. The overall job status
and Gradle log determine whether verification succeeded; `--continue` does not hide failures.

- XML and HTML reports are uploaded even after failure when files exist; retained for 14 days.
- Successful manual `full` runs upload debug/test APKs.
- Successful manual `release` runs upload AABs, optimized test APKs and mapping files.
- Build artifacts are retained for three days to limit storage. Save the approved production
  artifacts and matching mappings separately for long-term release/crash investigation.

Instrumentation APKs are built but not executed on devices. A physical phone/watch and the
checks in [TESTING.md](TESTING.md) are still needed. Cloud APKs use the runner's debug key;
use both companion apps from one run. A `releasecheck` app is isolated under `.uitest`.
Plain release bundles have no production signing configuration and are not ready for Play upload.

The workflow uses a read-only repository token. No keystore, signing password or Play service
account is needed in GitHub secrets. Keep production signing files and `keystore.properties`
outside Git; ignore rules do not remove files already committed.

## Cost and day-to-day use

GitHub private repositories also have plan-dependent Actions and storage allowances. Review the
account's usage/budget settings before enabling paid usage. The workflow needs no paid action
service; hosted runner usage is still governed by GitHub's billing rules.

Use quick PR checks for ordinary work. Start `full` for APK/test-wiring changes, and `release`
for an approved release candidate or R8/dependency change. Keep local checks focused and reuse
passing results for unchanged source. See [TESTING.md](TESTING.md) for commands and coverage.

If cloud CI is unavailable, run the relevant local checks, record the tested commit and results
in the PR, and label CI as not run. A local result is not a GitHub status check.

## Initial enablement

1. Finish the Git migration and select `master` as the default branch.
2. Open the migration PR into `develop`; check its quick run when Actions is enabled.
3. Merge reviewed changes into `develop`, then into `master`, keeping both branches open.
4. Start a manual `full` run from the intended branch. Inspect its summary, logs and artifacts.
5. Record the exact passing commit. Add required checks or branch rules where the account's
   plan supports them; choose the checks produced by this workflow.

## References

- [GitHub workflow triggers](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows)
- [Gradle action setup and caching](https://github.com/gradle/actions/blob/v5/docs/setup-gradle.md)
- [Ubuntu runner software](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md)
- [GitHub Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
- [Manual Git workflow](GIT_WORKFLOW.md)
