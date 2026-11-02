# Current validation — 0.3.0

**33 unit tests and 20 Android 16 connected cases passed**. `assembleDebug testDebugUnitTest lint assembleDebugAndroidTest connectedDebugAndroidTest` passed. APK signature verification passed before publication.

New coverage includes strict approval/clarification identity and payloads, ambiguous acknowledgements, no decision replay, persisted prompt/cursor/tool records, schemas 1→2→3, offline speech policy and Unicode-safe TTS chunks. Real HTTP fixture integration exercises saved-cursor resume without another run admission or duplicated text, and replay-gap fallback to read-only status recovery. Earlier session/task/cache/theme/language/navigation checks remain passing.

APK SHA-256: `b0349a49dc64176cd32694ca3f2ba0303dbbb99cbaeec72b05beeb68d2e6a761`. Prepared deliverables are in ignored `artifacts/0.3.0/`.

Toolchain: Gradle 9.8.2, AGP 9.5.0, JDK 24, compile/target API 37, minimum API 26. Device: Android 16 / API 36 Google APIs x86_64 emulator, 1080×2400 / 420 dpi. Repository uses the chosen project directory; tools/cache/temp/emulator data use E:.

No live Hermes credentials, physical microphone or speaker validation was supplied. Audio tests verify policy, state and text review without recording or playback. See [release evidence](releases/0.3.0.md); [screenshots](screenshots.md) show the earlier visual baseline. CI compiles instrumentation but connected tests are run locally.
