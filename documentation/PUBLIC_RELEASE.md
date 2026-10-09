# Public Release Process

Status: describes the jlalcazar/MOTO-HUB fork (MotoVisor)
Last updated: 9 October 2026

Release APKs are built and published by hand. They are signed with this fork's own key and carry
**no Android Auto head-unit identity**.

## What A Release Contains

The release asset is named:

```text
MOTO-HUB-<versionName>-<versionCode>-public.apk
```

with a matching `.sha256` checksum file. It is the obfuscated `release` variant, arm64 only.

A release APK pairs with the dashboard, mirrors the screen, drives the USB external display and
reads the handlebar buttons. Android Auto does not start in it: the app reports that its identity
is not included in this build.

The in-app updater reads this repository's GitHub releases and pre-releases, selects the latest
APK newer than the installed build, and shows the release notes before installing.

## Why The Identity Is Left Out

Android Auto only projects to a head unit that presents a certificate it accepts, with the matching
private key. Anything packaged in an APK can be extracted from it, so a public APK that contains
the identity publishes that private key. This fork has no identity of its own to publish and does
not redistribute anyone else's.

Building an APK with an identity for personal use is described in the README, under *Building with
Android Auto*. That APK must not be attached to a release.

## What Stays Out Of Git

- `aa_cert` and `aa_identity_data`;
- the APK-signing keystore and `release-signing.properties`;
- the whole `tooling/private/` directory;
- locally generated APKs under `artifacts/`.

`.gitignore` ignores these by name anywhere in the tree. The hooks in `.githooks/` refuse a commit
or a push that carries them, including a copy saved under another name. Enable them once per clone:

```bash
git config core.hooksPath .githooks
```

## Signing Key

The release build reads `tooling/private/android-auto/release-signing.properties`:

```properties
storeFile=tooling/private/android-auto/motohub-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Keep a backup of the keystore and its password outside this machine. Android rejects an update
signed with a different key, so losing it means every rider has to uninstall before upgrading.

## Publishing A Release

1. Update `versionName` and increment `versionCode` in `apps/android/app/build.gradle.kts`.
2. Run the quality gates from `apps/android/`:

   ```bash
   ./gradlew lintDebug testDebugUnitTest assembleDebug
   ```

3. Build the public APK, with no identity flag:

   ```bash
   ./gradlew exportPublicApk
   ```

   The task writes the APK to `artifacts/`. It refuses to run if `-PincludeAndroidAutoIdentity`
   is set, and checks the exported file for the identity resources.
4. Test that exact APK on a phone and on the target motorcycle.
5. Commit and push the source, then create the GitHub release with a tag matching `versionName`,
   for example `v1.1.121`, and hand-written notes.
6. Attach the APK with the helper, which verifies once more that it carries no identity and
   uploads it with its checksum:

   ```bash
   tooling/publish-release.sh v1.1.121 artifacts/MOTO-HUB-1.1.121-215-public.apk
   ```

Do not upload an APK with `gh release upload` directly: that command attaches whatever file it is
given.

## Release Workflow Gate

`.github/workflows/release-android.yml` runs for tags matching `v*`. It does not create the
release; it proves that the tagged commit builds, signs and verifies away from a laptop, and
leaves the APK as a workflow artifact for comparison. It:

1. Validates that the signing secrets exist.
2. Reconstructs the signing keystore on the ephemeral runner.
3. Refuses to build if an identity directory is present in the checkout.
4. Installs Android SDK platform 36 and Build Tools 36.0.0.
5. Runs unit tests, release lint, and a clean release build without the identity flag.
6. Fails if either Android Auto identity resource is packaged in the APK.
7. Requires the Git tag version to match the Android `versionName`.
8. Aligns and signs the APK, then verifies alignment, signature, version code and version name.
9. Removes the reconstructed keystore even when an earlier step fails.

It needs these repository-level Actions secrets:

| Secret | Content |
|---|---|
| `MOTOHUB_KEYSTORE_B64` | Base64 encoding of the APK-signing JKS file |
| `MOTOHUB_KEYSTORE_PASSWORD` | Signing keystore password, also used for the key entry |
| `MOTOHUB_KEY_ALIAS` | Signing key alias |

Without them the workflow fails at its first step; local releases do not depend on it.
