# Current validation — 0.4.0

**39 unit tests and 26 Android 16 connected cases passed**. `assembleDebug testDebugUnitTest lint assembleDebugAndroidTest connectedDebugAndroidTest` passed, followed by APK signature verification.

Added exact job mutation contracts, no automatic mutation replay, durable ambiguity blocking, atomic encrypted connection pairing under concurrent reads/writes, schema 3→4 preservation, actual private notification delivery and deduplication, widget provider/rendering and individually confirmed Demo job actions. Earlier voice, controls, saved-cursor recovery, migrations, sessions, task/cache, language/theme/navigation coverage remains passing.

APK SHA-256: `4de3d668e378883d6dc08299533638548de7e4102e4be83bd6656f9ef51d174f`. Deliverables are in ignored `artifacts/0.4.0/`. Compile/target API 37; minimum API 26. Android 16/API 36 Google APIs x86_64 emulator, 1080×2400/420 dpi. Gradle 9.8.2, AGP 9.5.0, JDK 24. Repository stays in the chosen project directory; temp/cache/tool/emulator data stay on E:.

Notification testing uses harmless local fixtures and grants the test app's Android notification permission. No microphone, speaker, real agent or production provider was used. Device battery scheduling, live Hermes and physical hardware remain separately reportable checks. See [release evidence](releases/0.4.0.md). CI compiles instrumentation; connected tests run locally.
