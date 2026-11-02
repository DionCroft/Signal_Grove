# Current development validation — audit follow-up

2026-11-02, connection-isolation chunk: 43 JVM tests passed; full API 36 suite passed 33 tests (30 existing + 3 regressions). A final targeted 10-test rerun passed after the history/recovery guards were tightened. `testDebugUnitTest lint assembleDebug assembleDebugAndroidTest` passed. New coverage blocks switching during unknown acknowledgement, discards delayed job details, and rejects old run/stream writes against a newer durable submission. No schema change. API 26 has not yet been rerun for this chunk. See [implementation progress](AUDIT_FOLLOW_UP.md).

The 1.0.0 results and APK hashes below describe that released build, not the current development APK.

# Released validation — 1.0.0

| Check | Verified result |
| --- | --- |
| JVM unit tests | 43 passed |
| Android 16/API 36 phone device suite | 30 passed |
| Android 8.0/API 26 minimum device suite | 30 passed |
| 130% system-font UI rerun | 5 passed, including multilingual flows |
| Tablet viewport UI rerun | 5 passed, including navigation rail |
| Debug/release APK and instrumentation compilation | Passed |
| Android debug/release lint | Passed; no baseline hiding failures |
| Production install/launch | Passed on API 26 and 36 |
| Real production document-picker task round-trip | Passed with synthetic task/notes; confirmed merge kept one ID |
| Production APK signature and manifest | APK v2 / 4096-bit RSA verified; non-debuggable; cleartext disabled; min API 26 |
| Debug upgrade identity | Signing certificate matches published 0.4.0 debug APK |

Phone emulator: Android 16/API 36 Google APIs x86_64, 1080×2400 / 420 dpi. Minimum emulator: Android 8.0/API 26 Google APIs x86_64, 720×1280 / 320 dpi. Tablet viewport: API 36 resized to 1600×2560 / 240 dpi; this is an emulator viewport, not physical tablet hardware. Large-text rerun uses the phone viewport with system font scale 1.3. [Current screenshots](screenshots.md) were inspected.

Coverage includes fragmented/UTF-8 SSE, exact HTTP contracts, route prefixes/auth/redirect refusal, terminal-state accuracy, replay-gap recovery without resubmission, durable prompt identity/choices/ambiguity, job mutations without replay, concurrent encrypted credential pairs and v1 migration, schemas 1→4, task import validation/atomic merge, offline job caches, notifications/privacy/deduplication, widget rendering, themes/languages/navigation. Direct Android 8 reruns use unique notification fixtures so durable receipt deduplication remains effective between runs.

Production APK SHA-256: `08d3376c161a57e18428e004c7ae200d74e3b2c41dcc719b49737942e89efcd5`.
Debug APK SHA-256: `db13e66bff79904bc6c8b476e03bc9def5e5c7367c32c0250e767feb3e2ec0c7`.
Production signer SHA-256: `bbffacc53cc2dfecd78c598a41a44caf89623827b315bb873f62129da771e40f`.

Prepared deliverables are in ignored `artifacts/1.0.0/`; original generated reports remain in `app/build/`. Tools, caches, scratch files and emulator data are on E:; project source/build outputs use the requested project directory. Gradle 9.8.2, AGP 9.5.0, JDK 24, compile/target API 37, minimum API 26. CI compiles instrumentation and runs unit/build/lint checks; connected tests are local.

No live Hermes credentials or physical microphone/speaker/foldable hardware were supplied. Recognition/playback testing covers policy, lifecycle and text review without recording or speaker output. No production agent or destructive server operation was executed. Hardware audio, device-specific battery/launcher behavior and live-Hermes interoperability remain distinct from the verified fixture/emulator matrix. See [compatibility](COMPATIBILITY.md), [user guide](USER_GUIDE.md) and [release evidence](releases/1.0.0.md).
