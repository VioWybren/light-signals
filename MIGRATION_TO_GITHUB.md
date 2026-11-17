# Migrate from Bitbucket to GitHub

## Scope

Preserve commit history, `master`, `develop` and existing tags. The new repository is
`VioWybren/light-signals` on GitHub; the original Bitbucket name remains `light-signals-kotlin`.
Use GitHub for future pushes, pull requests and CI. Keep Bitbucket available as a fallback until the
copy is verified and GitHub Actions has completed its first run.

Git copying preserves branches, tags and commits. It does not transfer Bitbucket PR discussions,
issues, pipeline history, permissions or account settings. Keep Bitbucket available for those
records. Signing keys, passwords and local app data are not part of this migration.

Run commands in a normal PowerShell, one line at a time. Stop on an error. The examples use
an empty private GitHub repository; do not initialize it with README, license or .gitignore.

## 1. Prepare the task branch

From the Android project folder, with source changes committed:

```powershell
git status --short
git switch develop
git pull --ff-only origin develop
git switch -c chore/github-migration
```

If a migration branch already exists, keep using it; its spelling need not match the example.
The current preparation branch is `chore/github-migratio`, as created. Later commands detect its name.
An untracked `phone/release/` directory
contains generated bundle output; leave it on disk and do not stage it. The migration adds
ignore rules for that directory and local signing files.

Review, stage and commit the migration files:

```powershell
git add -- .gitignore .github/workflows/verify.yml MIGRATION_TO_GITHUB.md
git add -- CI.md GIT_WORKFLOW.md README.md CHANGELOG.md TESTING.md ARCHITECTURE.md
git diff --cached --stat
git diff --cached --check
git diff --cached
git status --short
git commit -m "ci: prepare GitHub migration and tiered verification"
git push --set-upstream origin HEAD
```

At this stage `origin` still means Bitbucket. This push preserves the preparation there before
copying its branches. It does not enable GitHub CI or change app versions.

## 2. Inspect what will be copied

```powershell
git fetch origin --prune --tags
git branch -vv
git tag --list
git log --oneline --branches --not --remotes=origin
```

The last command identifies local commits not reachable from Bitbucket tracking branches.
Push each intended unpublished branch before copying; inspect rather than blindly publishing
unrelated local work. Local-only tags also need to be pushed explicitly if they should migrate.

Check signing-file names in reachable history without displaying their contents:

```powershell
git log --all --name-only --format= -- '*.jks' '*.keystore' '*.p12' '*.pfx' 'keystore.properties' '**/keystore.properties'
```

If it lists signing files, inspect the exposure before uploading the repository elsewhere.
Adding an ignore rule does not erase tracked files or earlier commits. The preparation-time
local filename check found no matching paths; this is not a complete scan for embedded secrets.

## 3. Create an empty private GitHub repository

Open [New repository](https://github.com/new) under the intended account or organization:

- Name: `light-signals` under `VioWybren`, as chosen for this migration.
- Visibility: **Private**, unless public access has been explicitly chosen.
- Leave README, .gitignore and license initialization unchecked.

Copy its HTTPS clone URL without credentials. Authenticate through Git Credential Manager or
Android Studio when needed; do not put a token into the URL or a document.

## 4. Copy all Bitbucket branches and tags

Use a separate bare clone so the Android working folder and untracked local files stay intact:

```powershell
$projectRoot = (Get-Location).Path
$migrationBranch = (git branch --show-current).Trim()
$bitbucketUrl = 'https://bitbucket.org/VioWybren/light-signals-kotlin.git'
$githubUrl = 'https://github.com/VioWybren/light-signals.git'
if ($githubUrl -notmatch '^https://github\.com/[A-Za-z0-9-]+/[A-Za-z0-9_.-]+$') { throw 'Use a GitHub HTTPS URL without credentials or a trailing slash.' }
$migrationRoot = Join-Path $env:USERPROFILE 'Documents\GitMigrations'
New-Item -ItemType Directory -Path $migrationRoot -Force | Out-Null
$bareRepo = Join-Path $migrationRoot 'light-signals-kotlin.git'
git clone --bare $bitbucketUrl $bareRepo
git -C $bareRepo push --all $githubUrl
git -C $bareRepo push --tags $githubUrl
```

The URL may end in `.git`. The destination must be empty. These pushes copy all source branch
heads and tags without force-pushing or deleting destination references. If the bare clone folder
already exists, choose a different new folder name rather than overwriting it.

The current repository has no detected Git LFS attributes or submodules. If either is introduced
before migration, handle its external objects/repositories separately before declaring the copy complete.

## 5. Compare the remote references

```powershell
$bitbucketRefs = @(git ls-remote --heads --tags $bitbucketUrl | Sort-Object)
if ($LASTEXITCODE -ne 0 -or $bitbucketRefs.Count -eq 0) { throw 'Could not read the Bitbucket refs.' }
$githubRefs = @(git ls-remote --heads --tags $githubUrl | Sort-Object)
if ($LASTEXITCODE -ne 0 -or $githubRefs.Count -eq 0) { throw 'Could not read the GitHub refs.' }
$refDifference = Compare-Object $bitbucketRefs $githubRefs
if ($refDifference) { $refDifference; throw 'The remote refs do not match yet.' }
Write-Output 'All branch and tag refs match.'
```

Continue only if both remote commands succeed and the comparison is empty. That confirms matching
branch/tag names and object IDs at this point. Keep the bare clone until migration is verified.

Set GitHub's default branch to **master** in repository settings. Check that `develop`, the
migration branch and the expected tags are visible.

## 6. Switch the existing working copy to GitHub

Back in the original Android project folder, reuse the URL variables from the same PowerShell:

```powershell
Set-Location $projectRoot
git remote rename origin bitbucket
git remote add origin $githubUrl
git fetch origin --prune --tags
git branch --set-upstream-to=origin/master master
git branch --set-upstream-to=origin/develop develop
git branch --set-upstream-to="origin/$migrationBranch" $migrationBranch
git branch -vv
```

Use `origin` for GitHub from this point onward. `bitbucket` retains the old remote as a fallback.
Renaming a remote also updates existing upstream names, so check any other active local branches
and point them to GitHub before using plain `git push` or `git pull` there.

## 7. Review and start GitHub CI

1. Open a GitHub PR from your migration branch (`$migrationBranch`) into `develop`.
2. Enable Actions if the repository requires it; inspect the quick JVM/lint check.
3. Review and merge using **Merge commit**, then update local `develop`.
4. Open a PR from `develop` into `master` for the CI/tooling changes. Keep both branches open.
5. After the workflow exists on default `master`, open **Actions → Verify Android → Run workflow**.
   Choose the intended branch and `full` for the first complete APK/test-build check.
6. Inspect the run's commit, test/lint summary, logs and downloadable artifacts.

`quick` runs automatically on PRs. `full` and `release` are manual. No production keystore or
Play credential is needed, and the workflow does not publish the app. See [CI.md](CI.md).

Migration is complete only after branch/tag comparison, working-copy remote setup and a successful
GitHub verification run. Keep Bitbucket available until then. The prepared YAML alone is not
proof that GitHub runner downloads, account allowances and the Linux build all work.

## After migration

- Follow [GIT_WORKFLOW.md](GIT_WORKFLOW.md) using GitHub pull requests and `origin`.
- Retain Bitbucket for historical PR records; archive or restrict writes only after the migration
  is accepted. Repository deletion is a separate decision.
- Use [TESTING.md](TESTING.md) for device checks. Keep the ongoing Play signing/upgrade work separate.
- Add branch rules and required checks if supported by the repository's plan, after checks exist.
- Review GitHub Actions usage/storage settings; private CI is not assumed to be unlimited.

## References

- [GitHub repository duplication](https://docs.github.com/en/repositories/creating-and-managing-repositories/duplicating-a-repository)
- [Importing an external repository](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/importing-an-external-git-repository-using-the-command-line)
