# Community release process

Versions are completed in order, one active version goal at a time. Keep the source, versionCode/versionName, changelog, roadmap, compatibility notes and validation evidence consistent. Android schema changes need exported schemas and non-destructive migration tests from supported historical versions.

## Validate before publication

Run unit tests, lint, debug build, instrumentation compilation and connected tests. Exercise real-client fixture contracts for network mutations and recovery. Inspect current phone/wide/large-text screens. Verify the release manifest, install the signed production APK and verify debug upgrade signatures. Keep hardware/live-Hermes checks distinct from fixtures. Scan tracked source using `python -B scripts/secret_review.py` and inspect the Git diff. Public CI must pass for the exact release commit.

## Signing

Unsigned release builds remain available to contributors and CI without secrets. To sign, provide `HERMES_RELEASE_KEYSTORE`, `HERMES_RELEASE_STORE_PASSWORD`, optional `HERMES_RELEASE_KEY_PASSWORD` (defaults to store password), and optional `HERMES_RELEASE_KEY_ALIAS` (defaults to `hermesmobile`) to Gradle's environment. Never put these values in tracked files or command arguments.

On the owner's Windows setup, `scripts/sign-release-windows.ps1 -WorkRoot E:\Temp\hermes-mobile -AndroidSdk E:\Android\Sdk` creates/reuses a 4096-bit RSA JKS in ignored `.local/release-signing/`, with a random password protected by Windows CurrentUser DPAPI in `password.dpapi`. It refuses an incomplete pair rather than replacing an existing key. The script exports a public certificate and builds/lints release. Temporary/tool/cache paths remain on E:. Review the script before use on a different machine.

Back up the keystore and its password through an owner-controlled secure backup. The DPAPI file is tied to this Windows user/machine and is not a portable recovery password. Establish a protected portable recovery method before replacing the machine/account. Losing the original signing key/password prevents normal updates to already installed production APKs. Never regenerate a replacement key to fix a build error. Private signing files and credentials must never be uploaded to GitHub.

`apksigner verify --verbose --print-certs` verifies APK signature schemes and signer SHA-256. Publish the public certificate, signer fingerprint and SHA256SUMS with the APK. Production uses `org.hermesmobile.app`; debug uses `.debug` and a different key. Supply a debug upgrade APK when supporting users of earlier development builds. CI-generated debug keys are ephemeral and cannot replace the owner's development upgrade key.

## Publish and verify

Create the release at the exact passing source commit. Versions below 1.0 are prereleases; 1.0.0 is the stable community release. Include concise change notes, compatibility limits, tested matrix, signed production APK, optional debug-upgrade APK, public signing certificate and checksums. Verify the uploaded asset digests against local files. Preserve historical release records. No Play Store deployment is part of this repository workflow.

Signing background: [Android app signing](https://developer.android.com/studio/publish/app-signing). Task migration uses the [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files) with explicit user-selected files and no broad storage permission.
