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

Publish completed application releases from the verified `main` commit. The
owner has authorized the public APK release once its checks are complete; do not
leave delivery at a source push or temporary CI artifact. Preserve preview/stable
semantics below. A signed candidate that still fails a required gate is not ready.

Before publication refresh the README's release table, implemented features,
validation date/counts, APK/model sizes and current screenshots. Reconcile
`docs/FEATURES.md`, ROADMAP, changelog, compatibility and release notes with the
same final artifacts. Keep historical results labelled with their exact scope;
current-main source, fixture/hardware evidence and published inclusion differ.
After upload verify assets/digests and the download URL, then update availability
and provide the direct production APK link. Preserve failed checks in validation.

Create the release at the exact passing source commit. Versions below 1.0 and versions with a preview suffix (for example `1.1.0-beta.1`) are prereleases; 1.0.0 remains the stable community release until a newer stable version is published. Include concise change notes, compatibility limits, tested matrix, signed production APK, optional debug-upgrade APK, public signing certificate and checksums. Verify the uploaded asset digests against local files. Use exact asset names `hermes-agent-mobile-VERSION.apk` and `hermes-agent-mobile-VERSION-debug.apk` (no `v` in VERSION). The updater requires GitHub’s `sha256:` asset digest and rejects missing/ambiguous assets. Increment Android versionCode for every published upgrade and retain the same signing key for each package. Defaults are the public version; `-PhermesVersionCode=... -PhermesVersionName=...` is reserved for owned local upgrade fixtures and deliberate release versioning. Never upload future-version QA fixtures as release assets. Preserve historical release records. No Play Store deployment is part of this repository workflow.

Signing background: [Android app signing](https://developer.android.com/studio/publish/app-signing). Task migration uses the [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files) with explicit user-selected files and no broad storage permission.
