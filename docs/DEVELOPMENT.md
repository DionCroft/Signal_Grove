# Development

Pinned toolchain: AGP 9.5.0, Gradle 9.8.2, Kotlin Compose plugin 2.4.21, KSP 2.3.13. AGP uses built-in Kotlin. Compile/target API 37, minimum API 26. Java bytecode target 17; build verified with JDK 24. Dependency versions are explicit in `app/build.gradle.kts`; no dynamic `+` versions.

## Windows owner setup

Use **E:** for tools, SDK, Gradle user home, Android user data, emulator data and temporary files. Keep the source and generated app output in the requested N: repository. Never fall back to C: temporary directories. See `scripts/build-windows.ps1`. Set `JAVA_HOME` to an installed supported JDK. The script leaves no machine paths in tracked configuration. A debug signing key is generated in `HERMES_BUILD_HOME` on E:, not the default user-profile `.android` folder.

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
