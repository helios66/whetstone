Releasing
=========

This fork publishes to **GitHub Packages** under group `io.github.helios66`
(`https://maven.pkg.github.com/helios66/whetstone-private`, plugin id
`io.github.helios66.whetstone`). Maven Central + Sonatype signing are disabled.

Whetstone uses an automated release process triggered by GitHub Releases. The workflow automatically handles testing, publishing to GitHub Packages, tagging, and preparing the next development version.

## Quick Release Guide

1. **Update Version in gradle.properties**
   ```bash
   # Change VERSION_NAME from X.Y.Z-SNAPSHOT to X.Y.Z
   # Example: 1.1.5-SNAPSHOT → 1.1.5
   vim gradle.properties
   ```

2. **Open a PR for the Release Version**
   `main` is protected — you cannot push directly. Open a PR titled
   `Prepare for release X.Y.Z` containing only the `gradle.properties`
   change above, get review, and merge.
   ```bash
   git checkout -b release/X.Y.Z
   git commit -am "Prepare for release X.Y.Z"
   git push -u origin release/X.Y.Z
   gh pr create --base main --fill
   ```

3. **Create a GitHub Release** (only after the prep PR is merged to `main`)
   - Go to [GitHub Releases](https://github.com/helios66/whetstone/releases/new)
   - Click "Choose a tag" and create a new tag `X.Y.Z` (e.g., `1.2.3`).
   - Write release notes describing changes
   - Click "Publish release"

4. **Automated Publishing**
   - GitHub Actions automatically:
     - Validates version format (must be X.Y.Z, no SNAPSHOT)
     - Runs full test suite
     - Publishes to GitHub Packages (`helios66/whetstone-private`)
     - Commits next SNAPSHOT version to main (X.Y.Z+1-SNAPSHOT)

5. **Done!**
   - Verify the release in [GitHub Packages](https://github.com/helios66/whetstone-private/packages)
   - Check that main branch was automatically updated to next SNAPSHOT version

## What the Automation Does

When you create a GitHub release:

1. **Version Validation**: Reads `VERSION_NAME` from gradle.properties and validates format (X.Y.Z, no SNAPSHOT)
2. **Testing**: Runs `./gradlew build` to ensure all tests pass
3. **Publishing**: Publishes artifacts to GitHub Packages (`helios66/whetstone-private`)
4. **Next Version**: Automatically commits `X.Y.(Z+1)-SNAPSHOT` to main branch

## Required repo configuration

The workflow publishes to GitHub Packages and pushes the post-release SNAPSHOT bump
directly to the protected `main` branch. Both use a single classic PAT secret,
`RELEASE_TOKEN`, configured on `.github/workflows/publish-release.yml` via
`actions/checkout`'s `token:` input (so the persisted git credential carries the
identity for the bump push) and as `GITHUB_TOKEN` on the build/publish steps.

`RELEASE_TOKEN` must be a classic PAT with:

- **`repo`** — push the version-bump commit to `main`. If `main` is branch-protected,
  add the token's account to the bypass list ("Allow specified actors to bypass
  required pull requests"), or the push is rejected with `GH006: Protected branch update failed`.
- **`write:packages`** — publish to `helios66/whetstone-private` (read is implied).
- **`read:packages`** — resolve Mundus from `helios66/mundus` during the build.

Locally (manual publish), the build reads the same credentials from `gpr.user`/`gpr.key`
in `~/.gradle/gradle.properties`.

## Version Scheme

- **Release versions**: `1.2.3` (no SNAPSHOT suffix) — landed on `main` via the prep PR before you create the GitHub Release
- **Development versions**: `1.2.4-SNAPSHOT` — always on `main` between releases; auto-committed by the bot after each release
- **Tags**: `X.Y.Z` — created by you when you publish the GitHub Release. The workflow never inspects the tag name; only `gradle.properties:VERSION_NAME` is validated.

## Manual Publishing (Fallback)

If the automated workflow is broken and you must publish manually:

```bash
# Credentials: gpr.user/gpr.key in ~/.gradle/gradle.properties (or export
# GITHUB_ACTOR / GITHUB_TOKEN). The PAT needs write:packages on whetstone-private
# (publish) + read:packages on mundus (the sample resolves Mundus during the build).

# 1. From a release prep PR branch (gradle.properties already at X.Y.Z), run tests
./gradlew clean build

# 2. Publish the library modules + the Gradle plugin to GitHub Packages
#    (the includeBuild plugin reads GROUP/VERSION_NAME from the root gradle.properties
#    via loadParentProperties(), so no property-propagation hack is needed).
./gradlew clean -x test -x lint \
  publishAllPublicationsToGitHubPackagesRepository \
  :whetstone-gradle-plugin:publishAllPublicationsToGitHubPackagesRepository

# 3. Tag and push the tag
git tag -a X.Y.Z -m "Version X.Y.Z"
git push origin X.Y.Z

# 4. Open a follow-up PR bumping gradle.properties to X.Y.(Z+1)-SNAPSHOT
#    (main is protected — you cannot push the bump directly).
```

Note: Using GitHub Releases is strongly recommended as it provides full automation and audit trail.

## Troubleshooting

**`Error: Release version cannot contain SNAPSHOT: X.Y.Z-SNAPSHOT`**
- The tag points at a commit whose `gradle.properties` still has `-SNAPSHOT`. Cause: you created the tag before (or without) merging the prep PR. Fix: merge the prep PR (so `gradle.properties` on `main` reads `X.Y.Z`), delete the tag, and re-create the GitHub Release pointing at the merged prep commit.

**`Error: Invalid version format 'X.Y.Z'. Expected format: X.Y.Z`**
- The workflow validates `VERSION_NAME` from `gradle.properties` (it never inspects the tag). The value must match `^[0-9]+\.[0-9]+\.[0-9]+$` — three dot-separated integers, no suffixes. Fix `gradle.properties` and open a new prep PR.

**Release workflow failed on tests**
- The workflow runs the full test suite before publishing.
- Fix failing tests on `main`, then create a new GitHub Release.

**Published version not showing in GitHub Packages**
- Check the [packages page](https://github.com/helios66/whetstone-private/packages) and the "Publish to GitHub Packages" step logs.
- A 401/403 on publish means `RELEASE_TOKEN` lacks `write:packages` on `whetstone-private` (or expired).

**Build fails resolving Mundus**
- The sample depends on Mundus from `helios66/mundus`. `RELEASE_TOKEN` needs `read:packages`; a 401 on `com.unpopulardev.mundus:*` is the tell.

**Next SNAPSHOT version not committed to `main`**
- Most common cause: `RELEASE_TOKEN` is missing/lacks `repo`, or its identity is not on the branch-protection bypass list — see [Required repo configuration](#required-repo-configuration). The default `GITHUB_TOKEN` cannot push to a protected `main`, and the failure surfaces only at the bump step (after the artifacts are already published).
- Other causes: check the GitHub Actions logs for the "Bump to next SNAPSHOT version" step.
- Workaround: open a PR manually bumping `gradle.properties` to `X.Y.(Z+1)-SNAPSHOT`.
