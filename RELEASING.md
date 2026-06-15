Releasing
=========

This fork publishes to **Maven Central** (Sonatype Central Portal) under group
`com.unpopulardev.whetstone` (plugin id `com.unpopulardev.whetstone`). Publishing is wired with the
[vanniktech maven-publish plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/) and is
**`USER_MANAGED`** — the build uploads a staged bundle to the Central Portal and you click *Publish*
in the Portal UI to finalize. Releases are GPG-signed; `-SNAPSHOT` builds go unsigned to the Central
snapshot repository.

The `com.unpopulardev` namespace is already verified on the Central Portal (DNS TXT on
`unpopulardev.com`); `com.unpopulardev.whetstone` inherits that — no namespace/DNS setup is needed.

## Credentials & signing (reused account secrets — nothing new to provision)

vanniktech reads Central credentials and the in-memory GPG key from `ORG_GRADLE_PROJECT_*`
properties. The account already has both; bridge them at invocation:

| What | Source (already on this machine) | Bridge to |
|---|---|---|
| Central Portal token | `maven.username` / `maven.password` in `~/.gradle/gradle.properties` | `ORG_GRADLE_PROJECT_mavenCentralUsername` / `...Password` |
| GPG signing key | `SIGNING_KEY_ID` / `SIGNING_KEY` / `SIGNING_PASSWORD` (sourced by `~/.zshrc` from `~/.config/mundus/signing.env`) | `ORG_GRADLE_PROJECT_signingInMemoryKey{,Id,Password}` |

A single Central Portal token covers every namespace under the account, and the same GPG key is
reused across sibling libraries — so the new library needs **no new token and no new key**.

## Local publish (the primary path)

```bash
# Load the reused signing key into the shell (Central creds live in ~/.gradle/gradle.properties).
source ~/.config/mundus/signing.env       # exports SIGNING_KEY{,_ID} / SIGNING_PASSWORD
export ORG_GRADLE_PROJECT_signingInMemoryKey="$SIGNING_KEY"
export ORG_GRADLE_PROJECT_signingInMemoryKeyId="$SIGNING_KEY_ID"
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="$SIGNING_PASSWORD"
export ORG_GRADLE_PROJECT_mavenCentralUsername="$(grep '^maven.username=' ~/.gradle/gradle.properties | cut -d= -f2-)"
export ORG_GRADLE_PROJECT_mavenCentralPassword="$(grep '^maven.password=' ~/.gradle/gradle.properties | cut -d= -f2-)"

# 0. Set a release version in gradle.properties (X.Y.Z, no -SNAPSHOT).

# 1. DRY RUN — stage everything locally, no upload. Confirm signed artifacts land in
#    ~/.m2/repository/com/unpopulardev/whetstone/ (.pom, -sources.jar, -javadoc.jar, .asc).
./gradlew publishToMavenLocal -x test -x lint
./gradlew -p whetstone-gradle-plugin publishToMavenLocal -x test     # included build

# 2. REAL UPLOAD to the Central Portal (staged bundle; still needs the manual "Publish" click).
./gradlew publishAllPublicationsToMavenCentralRepository
./gradlew -p whetstone-gradle-plugin publishAllPublicationsToMavenCentralRepository

# 3. Finalize: open https://central.sonatype.com -> Deployments -> review the staged bundle -> Publish.
```

The build refuses to run `publishAllPublicationsToMavenCentralRepository` if no signing key is
present (Central rejects unsigned release bundles) — see the guard in the root `build.gradle.kts`.

> The sample modules resolve **Mundus** from the `helios66/mundus` GitHub Packages registry, so the
> build (not the publish) still needs `gpr.user`/`gpr.key` (or `GITHUB_ACTOR`/`GITHUB_TOKEN`) with
> `read:packages`. Build with `-Pmundus.present=false` to skip Mundus entirely.

## CI release (GitHub Actions)

`.github/workflows/publish-release.yml` publishes to Central on a GitHub Release (and snapshots on
push to `main`). It requires these repo **secrets** (provision before the next release):

- `CENTRAL_PORTAL_USERNAME` / `CENTRAL_PORTAL_PASSWORD` — the Central Portal token (same value as the
  local `maven.username`/`maven.password`).
- `SIGNING_KEY` (ASCII-armored private key) / `SIGNING_KEY_ID` / `SIGNING_PASSWORD` — the GPG key.
- `RELEASE_TOKEN` — classic PAT, still needed for `read:packages` (Mundus resolution) and to push
  the post-release SNAPSHOT bump to the protected `main` (`repo` scope; add to the branch-protection
  bypass list).

## Version scheme

- **Release**: `X.Y.Z` (no suffix) — landed on `main` via a prep PR before the GitHub Release.
- **Development**: `X.Y.(Z+1)-SNAPSHOT` — on `main` between releases; auto-bumped after each release.
- The workflow validates `gradle.properties:VERSION_NAME` (it never inspects the tag name).
