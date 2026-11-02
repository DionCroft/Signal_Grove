# Current validation — 0.2.0

Debug APK, unit tests, lint and instrumentation compilation passed. **23 unit tests and 10 Android 16 connected cases passed**. The device suite covers all four palettes, English/Chinese/pinyin settings, navigation/chat, task CRUD/notes/search, conversation pin/rename/delete, cached job details, schema-1 migration preservation and active-turn history synchronization.

The schema migration test reads the checked-in alpha schema from instrumentation assets; it preserves task/message content and unresolved run IDs/keys. Room cache tests verify credential/profile isolation and preservation after an outage.

APK checksum: `bfc0f867a0d659936138b71e9fab04040b0a4d2930023df3e99de43959945342`. Prepared APK and checksum are in ignored `artifacts/0.2.0/`; normal Gradle outputs remain under `app/build/`.

Toolchain: Gradle 9.8.2, AGP 9.5.0, JDK 24, compile/target API 37, min API 26. Test device: Android 16 / API 36 Google APIs x86_64 emulator, phone viewport 1080×2400 / 420 dpi. Tools, caches and emulator data use E:; repository files use the requested project directory.

No real Hermes credentials, physical microphone, foldable hardware or production provider were supplied. Contract tests and emulator results do not assert live-provider interoperability or physical speech-recognition accuracy. These limits remain explicit in release notes. See [the version evidence](releases/0.2.0.md) and [the earlier screenshots](screenshots.md).
