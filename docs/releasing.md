# Releasing LanInbox

The release workflow builds a signed Android Release APK and a Windows x64 MSI from an existing
version tag, runs the shared and desktop tests, and creates an unpublished GitHub Release draft.
Publishing remains a manual step after testing the exact draft assets on real devices.

## Version numbers

`VERSION` is the source for the public version, the Android `versionName`, and the release asset
names. Use `MAJOR.MINOR.PATCH`, optionally followed by `-alpha.N`, `-beta.N`, or `-rc.N`, where `N`
is a positive integer. Tags must be exactly `v<VERSION>`. A branch named `v1.0` is not a release tag.

Before every distributed build, update both upgrade counters in `gradle.properties`:

| Setting | Purpose | Rule |
| --- | --- | --- |
| `laninbox.androidVersionCode` | Android `versionCode` | Increase for every release, including prereleases; never reset. Maximum: 2,100,000,000. |
| `laninbox.windowsBuild` | Third field of the MSI version | Increase for every release, including prereleases, within the same major/minor. Range: 1–65,535. |

Windows uses `<MAJOR>.<MINOR>.<windowsBuild>` because MSI versions cannot contain a prerelease
suffix. Major and minor must each be at most 255. The public patch number is deliberately
independent of the MSI build number. Once the Windows build limit is reached, advance the public
major/minor before resetting that counter. Keep Android's counter increasing across that change.

For example, these are consecutive builds, not values to apply all at once:

| Public version | Android code | Windows build | MSI version |
| --- | --- | --- | --- |
| `1.0.0-beta.1` | 2 | 1 | `1.0.1` |
| `1.0.0-beta.2` | 3 | 2 | `1.0.2` |
| `1.0.0-rc.1` | 4 | 3 | `1.0.3` |
| `1.0.0` | 5 | 4 | `1.0.4` |
| `1.0.1` | 6 | 5 | `1.0.5` |

The root Gradle build checks syntax and numeric bounds. Maintainers must compare the counters
with previously distributed builds; the workflow does not infer upgrade order from Git tags.
Keep the Windows `upgradeUuid` in `desktopApp/build.gradle.kts` constant across versions. Earlier
verification installers predate that explicit identity and may require a one-time uninstall.
The MSI is not currently code-signed. Continue using `packageMsi`; enabling desktop ProGuard is
a separate change that needs its own acceptance checks.

## Android signing

Create a release keystore once using Android Studio or `keytool`. Keep a secure backup of the
keystore, alias, and passwords outside the repository, and reuse the same key for future APKs.
Keystores under `.signing/`, and files ending in `.jks` or `.keystore`, are ignored by Git.

Configure these repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `LANINBOX_KEYSTORE_BASE64` | Base64 encoding of the complete keystore file, not its path. |
| `LANINBOX_KEYSTORE_PASSWORD` | Keystore password. |
| `LANINBOX_KEY_ALIAS` | Alias of the release signing key. |
| `LANINBOX_KEY_PASSWORD` | Private-key password; for a PKCS12 keystore, normally the keystore password. |

For example, copy the Base64 value to the clipboard in PowerShell, then paste it directly into
the secret value:

```powershell
$keystorePath = (Resolve-Path '.signing/laninbox-release.jks').Path
[Convert]::ToBase64String([IO.File]::ReadAllBytes($keystorePath)) | Set-Clipboard
```

The workflow restores the keystore only in the runner's temporary directory and removes it
after packaging, including when a previous step fails. It disables Gradle's configuration cache
for the release build so signing credentials are not saved in that cache. Signing values are
not included in build information or uploaded artifacts.

For a local signed build, set `LANINBOX_KEYSTORE_PATH` to the absolute keystore path and set
`LANINBOX_KEYSTORE_PASSWORD`, `LANINBOX_KEY_ALIAS`, and `LANINBOX_KEY_PASSWORD` in the environment.
Then run:

```powershell
.\gradlew.bat --no-configuration-cache :androidApp:assembleRelease
```

Debug builds do not require these variables. A Release APK signed with a different key cannot
replace an installed Debug APK. Save needed data before uninstalling; uninstalling removes
Android app data and pairing. Future Release APKs must preserve the release signing key and
increase `versionCode` to support normal upgrades.

## Prepare and trigger a release

1. Update `VERSION` and both upgrade counters. Add English release notes at
   `docs/releases/<VERSION>.md`, covering highlights, requirements, installation and upgrades,
   and known issues. Commit and push those files together with the workflow changes.
2. Create an annotated tag on that commit and push it. For the first beta:

   ```powershell
   git tag -a v1.0.0-beta.1 -m "LanInbox 1.0.0-beta.1"
   git push origin v1.0.0-beta.1
   ```

3. Open the **Draft LanInbox release** Actions run. It builds and tests the tagged commit,
   verifies the APK signature, and prepares these assets:

   - `LanInbox-<version>-android.apk`
   - `LanInbox-<version>-windows-x64.msi`
   - `ANDROID-SIGNING-CERTIFICATE.txt`
   - `BUILD-INFO.txt` with the version, tag, source commit, and platform upgrade versions
   - `SHA256SUMS.txt` covering the other four assets

4. After the build succeeds, a separate job with `contents: write` downloads only that run's
   assets, checks their hashes and the tag's commit, and creates the draft. Build jobs retain
   `contents: read`. Prerelease suffixes automatically set **Pre-release** and prevent the
   prerelease from being selected as **Latest** by this workflow.
5. Download the assets from the draft as a repository maintainer. Follow the
   [manual acceptance checklist](release-verification.md), then publish the same draft and
   assets. For a stable release, confirm **Pre-release** is off and choose **Latest** if appropriate.

The workflow appends GitHub's generated PR/contributor changelog to the authored release notes.
`.github/release.yml` groups PRs labeled `enhancement`, `bug`, and `documentation`; other PRs go
under other changes, and `skip-changelog` excludes a PR. Direct commits may not appear as PR
entries, so the authored notes are required.

To run manually, choose **Draft LanInbox release → Run workflow** and provide an existing
version tag. The workflow must exist on the repository's default branch before GitHub exposes
manual dispatch. Choose a workflow revision containing this release setup; the build still
checks out `refs/tags/<tag>` rather than the selected branch. Tag pushes work when the tagged
commit contains the workflow. Daily verification runs on branch pushes and PRs, while release
tags use the release workflow to avoid a duplicate verification build.

## Failures and reruns

- A missing secret, invalid version, tag/version mismatch, missing notes, failed test, failed
  signature check, or failed checksum check stops release creation. Check the failed step;
  test reports are uploaded even when the build fails after tests have run.
- Runs for the same tag are serialized. An existing draft or published release is never
  replaced. A successful rerun will deliberately stop if a release already exists.
- A failed asset upload may leave a partial draft. Inspect it and delete only that unpublished
  draft before rerunning; keep the tag. Do not publish a draft from a failed workflow.
- Once a release is public, ship corrections under a new version and tag. Do not move an
  existing release tag or replace public installers. Prefer protecting version tags with a
  repository ruleset and enabling immutable releases once this flow has been accepted.
- Build assets are also retained in the release run's Actions artifacts for 30 days. A draft
  is visible only to maintainers; public downloads become available after publishing.
