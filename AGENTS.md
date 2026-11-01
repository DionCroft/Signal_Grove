# Project instructions

Build a native Android client for the documented Hermes API. Use Kotlin, Compose, MVVM and the existing repositories. Keep the theme engine independent of connectivity. Use `FakeHermesApiClient` for fixtures. Do not create an agent backend.

Keep all project files in the chosen repository. On the owner's Windows machine use **E:** for tools, caches, temporary files and emulator data; never C: for temporary work. `scripts/build-windows.ps1` enforces this. Do not commit machine paths or local credentials.

Before changing networking, consult the official API reference and verify payloads. Preserve configured profile path prefixes, TLS validation, token isolation and explicit approvals. Never infer a terminal run from an assistant-finalisation event, a closed socket, a stop request or a chat-completion finish reason. Do not auto-replay unknown tool work with a new key.

Keep English/Chinese/pinyin in the structured `Words` layer. Every control must act or be disabled with an explanation. Demo content must remain clearly labelled.

Run `testDebugUnitTest lint assembleDebug assembleDebugAndroidTest`; use connected tests if a device exists. Update docs/VALIDATION.md honestly. Keep generated outputs, secrets and personal data out of Git. Original code is MIT. Never overwrite an existing public repository.
