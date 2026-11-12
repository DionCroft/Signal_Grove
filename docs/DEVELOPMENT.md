# Development

Pinned toolchain: AGP 9.5.0, Gradle 9.8.2, Kotlin Compose plugin 2.4.21, KSP 2.3.13. AGP uses built-in Kotlin. Compile/target API 37, minimum API 26. Java bytecode target 17; build verified with JDK 24. Dependency versions are explicit in `app/build.gradle.kts`; no dynamic `+` versions.

## Windows owner setup

Use **E:** for tools, SDK, Gradle user home, Android user data, emulator data and temporary files. Keep source and published APKs/reports in the requested N: repository. The wrapper builds a hash-verified temporary source snapshot, intermediates and project/Kotlin caches on E: (KSP requires source and generated files on one Windows root), then verifies and mirrors final artifacts/reports back to their usual checkout paths. Never fall back to C: temporary directories. See `scripts/build-windows.ps1`. Set `JAVA_HOME` to an installed supported JDK. The script leaves no machine paths in tracked configuration. Its E: layout and per-invocation receipt identify actual temporary outputs and verified published files; see [the Windows build layout](contracts/2026-11-07-windows-build-layout.md). A debug signing key is generated in `HERMES_BUILD_HOME` on E:, not the default user-profile `.android` folder.

```powershell
.\scripts\build-windows.ps1 -WorkRoot E:\Temp\hermes-mobile -AndroidSdk E:\Android\Sdk
```

On other systems:

```sh
./gradlew testDebugUnitTest lint assembleDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest  # requires an unlocked emulator/device
```

CI builds all variants, tests and lint without signing secrets. Unit tests cover fragmented/multiline SSE, Chinese UTF-8, missing terminal events, approval IDs, capability fallback, HTTP/auth errors, redirect refusal, persisted idempotency headers, server-path prefixes, history partitioning, multilingual display and palette contrast. Compose instrumentation tests use Demo mode; no live Hermes credentials are required.

## Manual QA

Exercise every navigation destination on a narrow phone and a wide tablet; use Android back. Send a Demo message, switch conversations, copy code, rotate and restart. Add/edit/complete/delete a task with a due date. Check each theme and each of the three language layouts. Increase system font size and enable reduced motion. Voice requires a supported recognition service and explicit permission; test deny/grant/background cleanup on hardware. Use a trusted live test server for auth expiry, job availability and sensitive approval review. Never use destructive operations for integration testing.

Build reports: `app/build/reports/tests`, `app/build/reports/lint-results-*.html`. APK: `app/build/outputs/apk/debug/app-debug.apk`. Generated reports and debug signing material are excluded from Git. Run `python -B scripts/secret_review.py` before publishing.

## Current validation and historical baseline

The beta.9 candidate has 1,004 JVM cases / 96 reports and ten scoped API26 local-AI
UI passes. The earlier fully reconciled standard gate at source 7ee5c59 executes
1,132 API26 / 1,133 API37 cases; fresh candidate full-suite acceptance is separate.
Six physical offline native cases use an earlier original-key debug candidate,
while two current HTTPS transfer cases retain timeout failures. Consult
[the feature/figure matrix](FEATURES.md) and dated [validation](VALIDATION.md) before
copying counts into a release. Report inventory, actual execution, hardware and
live-server evidence separately; do not total them into one completion figure.

The historical 1.0 suite had 43 unit and 30 device cases. Use unique fixture notification/task identities when testing durable receipts across repeated installs. UI tests must scroll controls into view on API-26-sized screens. ActivityScenario route tests restore their original launch Intent after exercising onNewIntent. Exported task files are bounded and fully validated before atomic import; never add server credentials or execution definitions to that format.

Use [the release process](RELEASING.md) for signing, private key backup and public asset verification. Contributors can build unsigned release variants; the owner's production key/password are never required for ordinary tests or CI. Update [compatibility](COMPATIBILITY.md) and [user instructions](USER_GUIDE.md) when behavior changes.


## Owned-emulator self-update QA

The normal debug instrumentation APK contains the standard `androidx.test.runner.AndroidJUnitRunner` and a separate `org.hermesmobile.app.SelfUpdateInstrumentation`. The standard runner still discovers every normal case. The standalone runner has no JUnit tests or skips and runs only when explicitly invoked by component name. No special Gradle runner switch or production instrumentation is needed.

Use only disposable owned English-language QA emulators for an actual package replacement. Keep the existing debug signing key. Build and copy a higher-code, higher-version full debug APK using explicit local-fixture overrides (for example `-PhermesVersionCode=105 -PhermesVersionName=1.1.0-beta.4` when testing the code-104 beta.3 base). Always choose values higher than the base under test; these examples are not published releases. Preserve the fixture before rebuilding the normal current version. Also prepare a copy signed by an unrelated throwaway fixture key. Never replace the owner’s upgrade key, publish future-version QA APKs, or use the production signing key for negative fixtures.

Place those APKs under the target app’s external `files/qa-updates/` directory, then invoke `org.hermesmobile.app.debug.test/org.hermesmobile.app.SelfUpdateInstrumentation` with `candidate`, `wrong_signer`, `version`, `expected_code` and a unique `marker` argument. It uses public-metadata fixtures through the real updater transport, rejects the wrong signer, opens the actual review, approves the download, checks the active-work guard and drives Android’s source permission and install confirmation. It does not contact a Hermes host or silently grant a source permission. Launch/control waits are bounded, and failure screenshots stay in the emulator’s QA directory.

Replacing the target package normally terminates its instrumentation process. That crash output is **not** a passing test summary. An external controller must independently observe the expected installed version with PackageManager, then invoke the same standalone runner with `mode=verify`, `expected_code` and the original `marker`. Require its successful result, installed code and retained marker before recording an actual-update pass. Keep initial and post-replacement logs separate from ordinary JUnit results. Restore the current APK only on the owned emulator; remove synthetic release metadata so it cannot appear to be a real published update. Normal device suites must still finish with a positive complete test count and no failures.
