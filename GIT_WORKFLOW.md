# Manual Git workflow

The phone, watch and shared core use one repository. Run commands from its root in PowerShell,
one line at a time. Android Studio's branch, commit, push and test actions are equally suitable.
If a command fails, inspect the result before continuing.

Follow [MIGRATION_TO_GITHUB.md](MIGRATION_TO_GITHUB.md) once to copy the repository and
make `origin` point to GitHub. The daily commands below assume that remote change is complete.
Until then, `origin` still means Bitbucket; do not confuse the two destinations.

Quick JVM/lint checks run on GitHub pull requests. Full APK/test builds and optimized release
checks are manual; see [CI.md](CI.md). The first GitHub run is pending until migration setup.

## Branches and checkpoints

| Branch / tag | Purpose |
| --- | --- |
| `develop` | Integration branch for the next release; start normal tasks here |
| `feature/...`, `fix/...`, `chore/...`, `refactor/...`, `perf/...` | Short task branches, normally merged into `develop` |
| `master` | Reviewed release code; not the branch for routine development |
| `hotfix/...` | Urgent production correction started from `master` |
| `v5.0.0` and later release tags | Fixed references to the exact approved release-build commits |

The current target is **v5.0.0**, still unreleased. The initial develop checkpoint is already
complete; do not repeat the old v4.0.0 first-commit procedure.

```text
task branch → develop → master → release tag → manual Play publishing
hotfix from master → master → back into develop
```

- **Commit** a coherent, reviewed local checkpoint, including the tests/resources it needs.
- **Push** after useful commits, before review and at the end of a work session. Only committed
  content is uploaded.
- **Merge** when the task is complete, reviewed and appropriately verified.
- **Tag** the exact approved release-build commit. A tag or push does not publish the app.

Use GitHub pull requests with **Merge commit** as the normal merge method. Keep `develop`
and `master` open. Do not squash release merges between those long-lived branches.

## 1. Start a task

Finish or preserve pending work before switching branches. Check `git status`; uncommitted
changes can follow a branch switch, so switching is not a way to isolate unfinished work.

```powershell
git status --short
git switch develop
git pull --ff-only origin develop
git switch -c chore/update-docs
```

Replace `chore/update-docs` with the task name. Examples are `feature/new-control`,
`fix/reconnect` and `refactor/state-ownership`. If the task branch already exists, switch to it
instead of recreating it. Keep using the same project directory; separate phone/watch branches
or duplicate project copies are not required.

`pull --ff-only` fetches and updates only if no merge is needed. Plain `git pull` also works,
but its merge/rebase behavior depends on configuration; the explicit command is predictable.
If fast-forward fails, inspect the histories instead of force-pushing or resetting work.

## 2. Edit and run focused checks

Develop on the task branch. Use [TESTING.md](TESTING.md) to choose relevant checks. For example:

```powershell
.\gradlew.bat :phone:testDebugUnitTest
```

A shared codec change needs core and app contract tests; a companion behavior change also needs
a matching phone/watch check. A Markdown-only edit needs a diff/link review, not a full APK build.

## 3. Review, stage and commit

For this documentation task:

```powershell
git status --short
git diff --stat
git diff
git add -- README.md CHANGELOG.md ARCHITECTURE.md TESTING.md VERSIONING.md CI.md GIT_WORKFLOW.md
git diff --cached --stat
git diff --cached --check
git diff --cached
git commit -m "docs: refresh v5.0.0 guides and release status"
git show --stat --oneline HEAD
```

Stage the actual paths for other tasks. `git diff` shows unstaged tracked edits;
`git diff --cached` shows the exact next commit, including staged new files. Press `q` to exit
Git's pager. Review untracked files listed by status before adding them.

`git add .` is acceptable when every non-ignored change in the current directory belongs in
the commit. Review status first and the staged diff afterward. Use explicit files when changes
are unrelated. To unstage a file without discarding its contents:

```powershell
git restore --staged -- README.md
```

Keep local SDK paths, credentials and generated APK/AAB/build outputs out of commits.
`.idea/deploymentTargetSelector.xml`, `watch/release/` and `core/build/` are ignored. Ignore rules
do not untrack files already committed, and are not access controls for secrets.

In Android Studio, select the intended files/hunks in **Commit**, review the diff and message,
then commit. Include significant behavior and test changes in the message; avoid describing
only the last small edit in a larger checkpoint.

## 4. Push and open a pull request

For the first push of the task branch:

```powershell
git push --set-upstream origin chore/update-docs
git rev-parse HEAD
```

Later pushes on that branch can use `git push`. Create a GitHub pull request from the task
branch into `develop`. Describe what changed, why, how it was checked and any remaining gaps.
Record local checks against the intended commit and inspect its quick GitHub check. Start
manual full/release verification when needed. If CI is unavailable, record that fact and the local
results. An older commit's successful run does not verify new source.

Push unfinished checkpoints only to the task branch and make their unfinished status clear in
the pull request. After further source changes, repeat the relevant checks before merging.

## 5. Merge and update develop

Review the final diff and verification record, then merge the pull request using **Merge commit**.
After GitHub reports the merge:

```powershell
git switch develop
git pull --ff-only origin develop
git log -1 --oneline
git status --short
```

These commands download the server merge; do not repeat it locally. Check the integrated
result if conflict resolution or other merged work changes the tested source.

When the task branch is fully merged and no longer needed:

```powershell
git branch -d chore/update-docs
git fetch origin --prune
```

`-d` deletes the local branch only if Git considers it merged. Remote branch deletion is a
separate GitHub action. Keep long-lived branches and release tags.

## Release workflow

### Prepare the release

1. Complete the intended work on `develop`, including any production hotfixes from `master`.
2. Check `appVersionName=v5.0.0`, phone code `375001` and watch code `375000` against Play
   upload history. Follow [VERSIONING.md](VERSIONING.md).
3. Keep the consolidated changelog entry under **Unreleased** until actual publication.
4. Run the release checks and device/upgrade checks in [TESTING.md](TESTING.md). Record actual
   results and remaining gaps; planned or assumed checks are not passed tests.
5. Open a pull request from `develop` to `master`. Review it and merge using **Merge commit**,
   leaving `develop` open.

### Check the approved master commit

```powershell
git status --short
git switch master
git pull --ff-only origin master
git log -1 --oneline
git rev-parse HEAD
```

Confirm that this is the source used for final validation. If a merge changes source, test the
affected result before signing. Run the manual `release` mode from the approved commit when the workflow is enabled.
While CI is unavailable, retain the local verification record.

### Sign, tag and publish manually

Use Android Studio's **Generate Signed Bundle / APK** flow with the existing registered upload
key. Build an Android App Bundle for each module being uploaded. A plain `bundleRelease` task
does not configure production signing. Keep keys/passwords local and outside Git.

Check the final artifacts' package, version/code, signature and release manifest. Retain their
hashes and matching R8 mapping files. Play internal testing can verify the actual delivered
update before production rollout; `.uitest` apps cannot prove a production upgrade.

After approving the release-build commit, check that the tag does not already exist, then
create it on that commit:

```powershell
git tag --list v5.0.0
git ls-remote --tags origin refs/tags/v5.0.0
```

Continue only if neither command finds an existing `v5.0.0` tag and HEAD is the approved build commit:

```powershell
git tag -a v5.0.0 -m "Light Signals v5.0.0"
git show --no-patch v5.0.0
git push origin v5.0.0
```

Upload the signed artifacts to the intended Play track, validate delivery and promote manually.
Phone and watch can be uploaded independently. Keep published tags fixed; a changed candidate
needs appropriate new metadata and verification. After publication, add the real release date
to the changelog through a documentation change and carry relevant master changes into develop.

## Carry release or hotfix changes into develop

Use a short synchronization branch so the merge remains reviewable:

```powershell
git switch develop
git pull --ff-only origin develop
git switch -c chore/sync-master
git fetch origin --prune
git merge --no-edit origin/master
```

If Git reports no new work, no synchronization pull request is needed. Otherwise review the
merge, resolve conflicts, run relevant checks, then push:

```powershell
git push --set-upstream origin chore/sync-master
```

Create a pull request into `develop` and follow the normal merge/update steps. Preserve the
intended next development version when resolving metadata conflicts.

## Production hotfix

Start from the currently released `master`, not unreleased develop work:

If `master` already contains an unpublished candidate, use the actual production release tag
as the branch's starting point instead.

```powershell
git switch master
git pull --ff-only origin master
git switch -c hotfix/production-issue
```

Make the focused fix and tests. Choose a version based on the actual published release, assign
unused higher upload codes and update the changelog. Commit/push, review a pull request into
`master`, follow release validation/signing, then carry the fix into `develop`.

## Conflicts and amended commits

For a conflicted merge, run `git status`, edit the affected files, remove conflict markers and
stage each resolved path. Finish with:

```powershell
git merge --continue
```

To abandon an in-progress merge started from a clean working tree, use `git merge --abort`.
Save unfinished work before starting merges; do not use destructive resets to solve routine
conflicts.

Use `git commit --amend` only for the latest unpublished local commit. Once pushed, prefer a
new commit so review history remains stable. After a rejected push or failed fast-forward,
fetch and inspect before choosing how to integrate the remote work.

## References

- [Testing](TESTING.md), [CI status](CI.md) and [Versioning](VERSIONING.md)
- [Git pull](https://git-scm.com/docs/git-pull), [merge](https://git-scm.com/docs/git-merge)
  and [branch cleanup](https://git-scm.com/docs/git-branch)
