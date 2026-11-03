# Manual Git workflow

Use this guide for the phone and watch together: they are one Git repository with two Android
modules. Run commands from the project root in PowerShell, one line at a time. If a command
fails, stop and resolve the error before running the next command.

```powershell
Set-Location 'C:\Android\Light Signal Project\Light Signals Kotlin'
```

## Branches and everyday rules

| Branch or tag | Purpose |
| --- | --- |
| `master` | Stable code approved for a release. Merge reviewed changes here. |
| `develop` | Integration branch for the next release. New work starts from this branch. |
| `feature/...`, `fix/...`, `chore/...` | Short branches where you edit code, tests, build settings, or documentation. Merge these into `develop`. |
| `hotfix/...` | An urgent production fix, started from `master`, merged into `master`, then carried back into `develop`. |
| `v4.0.0` and later release tags | Permanent references to the exact commits used for approved release builds. |

Work in the same project directory after switching to the relevant branch. You do not need
separate phone/watch branches or separate project copies.

| Action | When to do it | Effect |
| --- | --- | --- |
| Edit | On a short task branch created from updated `develop` | Changes files locally. |
| Stage (`git add`) | When selecting one coherent change for a commit | Selects the exact changes the next commit will record. |
| Commit | After reviewing the staged changes and running relevant checks | Records a local checkpoint. It does not upload anything. |
| Push | After a useful commit, before CI/review, or at the end of a work session | Uploads commits to Bitbucket. Uncommitted files are not uploaded. |
| Merge | After the source branch is reviewed and its latest commit passes verification | Integrates committed work into the destination branch. |
| Tag | After final release verification, on the exact release-build commit | Gives that commit a permanent release name. |

Keep commits focused, but include the code, tests, resources, and build changes needed for
that checkpoint to work. Push unfinished work only to a short task branch and mark its pull
request as a draft. Merge complete work into `develop`; merge a verified release into `master`.

The normal path is:

```text
feature/fix/chore branch -> develop -> master -> release tag -> manual Play publishing
hotfix branch from master -> master -> develop
```

This guide uses Bitbucket pull requests for feature and release merges, with **Merge commit**
as the merge strategy. The commands after a Bitbucket merge update your local copy; they do
not repeat that merge. Keep the long-lived `master` and `develop` branches open. Avoid squash
merges between those two branches so their ancestry remains connected.

## First run: commit the existing v4.0.0 work on develop

The existing app fixes, tests, versioning, and CI changes were developed together and are
already in the `develop` working tree. Make the first reviewed checkpoint there. Use short
task branches for subsequent work as described below.

### 1. Check the branch and refresh your view of Bitbucket

```powershell
git branch --show-current
git status --short
git fetch origin --prune
git branch -vv
git ls-remote --heads origin develop
```

The current branch should be `develop`. `fetch` downloads remote history and updates tracking
references without merging into your branch or changing your source files. An empty result
from `ls-remote` means the remote does not yet have `develop`.

Do not switch away from the current batch of uncommitted source changes to start this first
checkpoint. If the branch or remote state differs from what you expect, inspect it before
continuing. A local branch list alone does not prove that Bitbucket has no newer commits.

### 2. Review the changes and run local checks

```powershell
git diff --stat
git diff
```

`git diff` shows changes to tracked files. Open new files listed by `git status` in your editor
too; they will appear in the staged diff after the next step. Press `q` to exit Git's pager.

Run the existing checks from [TESTING.md](TESTING.md):

```powershell
.\gradlew.bat :phone:testDebugUnitTest :watch:testDebugUnitTest :phone:lintDebug :watch:lintDebug :phone:assembleDebug :watch:assembleDebug --stacktrace
.\gradlew.bat :phone:assembleUitest :phone:assembleUitestAndroidTest :watch:assembleUitest :watch:assembleUitestAndroidTest --stacktrace
```

Review the existing SDK, dependency, and Gradle wrapper updates as part of this checkpoint.
The checked-in YAML repeats these checks against the committed source on Bitbucket.

### 3. Stage the intended files

The following list covers the existing v4.0.0 work, including new support files. It leaves
local IDE device selection and the generated `watch/release/` bundle outside the commit.
Review any additional files you create before adding them.

```powershell
git add -- build.gradle.kts gradle.properties gradle gradlew gradlew.bat
git add -- phone/build.gradle.kts phone/src
git add -- watch/build.gradle.kts watch/src
git add -- test-support instrumentation-support scripts
git add -- README.md CHANGELOG.md TESTING.md VERSIONING.md CI.md GIT_WORKFLOW.md bitbucket-pipelines.yml
git diff --cached --stat
git diff --cached --check
git diff --cached
git status --short
```

`--cached` shows the exact snapshot you are about to commit. Check new files as well as
modifications. Keep machine-specific SDK paths, signing credentials, IDE device selections,
and generated APK/AAB files outside that snapshot.

If you accidentally staged the local IDE selection, unstage it without changing its contents:

```powershell
git restore --staged -- .idea/deploymentTargetSelector.xml
```

### 4. Create the local checkpoint

```powershell
git commit -m "Prepare v4.0.0 with stability fixes, tests, and manual CI"
git show --stat --oneline HEAD
git status --short
```

This creates a development checkpoint, not a published release. Remaining local IDE edits
or generated release files can still appear in status because they were deliberately left
unstaged. Confirm that no intended source files were missed.

If Git reports a missing author identity, set your real name/email for this repository, then
retry the commit. These example values must be replaced:

```powershell
git config user.name "Your Name"
git config user.email "your-email@example.com"
```

### 5. Publish develop to Bitbucket

If the first step found an existing `origin/develop`, first check for remote commits:

```powershell
git rev-list --left-right --count develop...origin/develop
```

The first number counts local-only commits; the second counts remote-only commits. If the
second number is greater than zero, integrate the remote work before pushing. Commit intended
work first, preserve remaining local tracked edits, then run:

```powershell
git merge --no-edit origin/develop
```

Resolve conflicts using the section below and rerun the relevant checks. Skip this comparison
and merge when Bitbucket has no `develop` branch yet.

Publish the branch and set its upstream:

```powershell
git push --set-upstream origin develop
git branch -vv
git rev-parse HEAD
```

The first push creates `develop` on Bitbucket if it does not exist. Later pushes from that
branch can use `git push`. Compare the commit hash with the one shown in Bitbucket.

A rejected push means the remote needs attention; fetch and inspect the difference rather
than forcing the push.

### 6. Run the first manual pipeline

Enable Pipelines if needed, then open **Pipelines > Run pipeline**, select `develop` and
`verify`, and check the displayed commit. Follow [CI.md](CI.md) for setup and artifacts.

Wait for a successful result. Every later code push needs a new manual run: the YAML does
not automatically rerun checks after a push. A successful earlier run covers only its commit.
If verification fails, fix it, commit and push the correction, and run verification again.

At this point the first checkpoint is stored on Bitbucket and verified. Keep developing on
short branches; a release merge into `master` is a separate decision.

## Daily development after the first checkpoint

### 1. Start a task from updated develop

Check status and finish or preserve pending work before switching branches. These commands
assume `develop` has been published and the intended source changes are committed:

```powershell
git status --short
git switch develop
git fetch origin --prune
git pull --ff-only origin develop
git switch -c feature/improve-settings
```

Replace `feature/improve-settings` with a short name for the task. Use `fix/...` for a bug fix
or `chore/...` for build/documentation work. `--ff-only` updates the branch only when it can
move forward without a merge; if it fails, inspect the histories before proceeding.

### 2. Edit, test, stage, and commit

Edit the files on the task branch and run checks appropriate to the change. Stage the relevant
paths explicitly. For example, for a change confined to the phone source:

```powershell
git status --short
git diff
git add -- phone/src
git diff --cached --stat
git diff --cached --check
git diff --cached
git commit -m "Improve signal settings"
```

Use narrower file paths when the directory contains unrelated edits. Repeat this cycle for
each coherent checkpoint; do not wait until release day to commit all work.

### 3. Push the task branch and verify it

```powershell
git push --set-upstream origin feature/improve-settings
```

Run the manual `verify` pipeline for that branch. For subsequent commits on the same branch,
use `git push` and start a new verification run. Push at useful checkpoints and before leaving
a work session so committed work is also stored remotely.

### 4. Merge the task into develop

Create a Bitbucket pull request with the task branch as source and `develop` as destination.
Review the diff and the latest verification result. When ready, merge with **Merge commit**.
Closing/deleting the short feature branch after the merge is optional.

Update your local integration branch and verify the integrated commit:

```powershell
git switch develop
git pull --ff-only origin develop
git log -1 --oneline
```

Run `verify` manually on the updated `develop`. Once the branch is merged and no longer needed:

```powershell
git branch -d feature/improve-settings
git fetch origin --prune
```

`-d` deletes the local branch only when Git considers it merged. Remote branch deletion is a
separate action, such as the close-source-branch option on the pull request.

## Release workflow

### 1. Prepare and verify develop

Complete the intended tasks. Check the shared version name and the two upload codes using
[VERSIONING.md](VERSIONING.md), including their availability in Play Console. Update the
changelog with notable changes. Use the task-branch process for any further release edits.

Make sure `develop` includes any production hotfixes from `master` before the release review.
Use the synchronization commands below if necessary. Run `verify` on the final `develop`
commit and run the physical-device checks from [TESTING.md](TESTING.md).

### 2. Merge develop into master

Open a Bitbucket pull request from `develop` to `master`. Review the release diff, then merge
using **Merge commit**. Keep `develop` open by clearing any close/delete-source-branch option.

Fetch the resulting release commit locally:

```powershell
git switch master
git fetch origin --prune
git pull --ff-only origin master
git log -1 --oneline
git rev-parse HEAD
```

Run the manual `verify` pipeline on that exact `master` commit. Repeat the device checks from
that checkout before signing. Any source correction requires another committed change and
verification; do not tag or publish a failing candidate.

### 3. Build, tag, and publish the approved release

Build signed phone and watch bundles from this checkout using Android Studio's signed-bundle
wizard and the existing production upload-key setup. The current Gradle release build types
do not define a release signing configuration, so a plain `bundleRelease` task alone does not
establish that the output is signed for Play. Follow the existing manual signing procedure.

Confirm that the release source is committed and that the bundles match the approved version
and codes. For the first release, create an annotated tag on that build commit:

```powershell
git tag -a v4.0.0 -m "Light Signals v4.0.0"
git show --no-patch v4.0.0
git push origin v4.0.0
```

Tagging and pushing do not publish the app. Upload the signed bundles manually to the intended
Play testing track, validate them, and promote manually when approved. Keep the tagged commit
and signed artifacts together in the release record. If code changes, prepare a new candidate;
keep published release tags fixed.

After actual publication, record the date in `CHANGELOG.md` through a follow-up documentation
change and carry release/hotfix changes back into `develop` as described below. Use the same
release procedure with the new version/tag for later releases.

## Carry master changes back into develop

After a release or hotfix, preserve any pending work, then synchronize the integration branch:

```powershell
git switch develop
git fetch origin --prune
git pull --ff-only origin develop
git merge --no-edit origin/master
git push origin develop
```

This fast-forwards when possible and otherwise creates a normal merge commit. Resolve conflicts
and rerun relevant checks before pushing a merge that changes the source. Then run `verify`
on the resulting `develop` commit. Preserve the intended next development version when resolving
version metadata conflicts with a production hotfix.

These commands assume you can push synchronization commits to `develop`. If branch restrictions
require a pull request, prepare content changes on a `chore/sync-master` branch and use the same
review/merge procedure instead.

## Urgent production hotfix

Start from updated `master`, so unrelated unreleased development is excluded:

```powershell
git switch master
git pull --ff-only origin master
git switch -c hotfix/v4.0.1
```

Make the fix and tests, update shared release metadata with unused upload codes, and update the
changelog. Stage the relevant files as above, review the staged diff, and commit:

```powershell
git commit -m "Fix the production issue for v4.0.1"
git push --set-upstream origin hotfix/v4.0.1
```

Run `verify` and the relevant device checks. Merge a Bitbucket pull request from the hotfix
branch into `master`, then follow the release steps using the `v4.0.1` tag and its approved
commit. Carry `master` back into `develop` so the next normal release retains the fix.

## Local merge alternative

The main workflow merges through Bitbucket pull requests. If your branch permissions allow
local merges and you choose that method for a feature, use this in place of the Bitbucket
merge step:

```powershell
git switch develop
git pull --ff-only origin develop
git merge --no-ff feature/improve-settings -m "Merge feature/improve-settings into develop"
```

Review/test the merged result, then publish it and run `verify`:

```powershell
git push origin develop
```

For a locally merged release, the corresponding commands are:

```powershell
git switch master
git pull --ff-only origin master
git merge --no-ff develop -m "Merge develop for v4.0.0"
```

Review and check the merge before `git push origin master`, then continue the final `master`
verification/tagging procedure. Use this alternative only when server policy permits direct
pushes to the destination. Choose one merge method for a given change.

## Conflicts and local edits

If a merge stops with conflicts, run `git status`, edit the listed files, and remove the conflict
markers while keeping the intended behavior from both branches. Stage each resolved file using
its actual path, then finish the merge:

```powershell
git merge --continue
```

Save and close Git's commit-message editor if prompted, then rerun checks before pushing.
To abandon an in-progress merge that was started with committed source changes:

```powershell
git merge --abort
```

For the existing local Android Studio device selection, you can preserve just that tracked
edit before switching/merging:

```powershell
git stash push -m "Local Android Studio device selection" -- .idea/deploymentTargetSelector.xml
git stash list
```

This does not include the generated watch release bundle. Restore the saved IDE edit later
with `git stash apply` and the appropriate quoted stash reference from the list, after checking
its message. Keep it outside application commits.

For an unexpected push rejection, failed fast-forward, or unfamiliar conflict, stop and inspect
the state before proceeding. Routine work does not require force pushes or destructive resets.

## References

- [Git push and upstream branches](https://git-scm.com/docs/git-push)
- [Git merge and conflict handling](https://git-scm.com/docs/git-merge)
- [Bitbucket pull request merging](https://support.atlassian.com/bitbucket-cloud/docs/merge-a-pull-request/)
- [Manual CI setup](CI.md)
- [Testing](TESTING.md)
- [Versioning](VERSIONING.md)
