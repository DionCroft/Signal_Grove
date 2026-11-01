# Validation — 2026-11-01

## Verified locally

- Native project compiled with Gradle **9.8.2**, AGP **9.5.0**, JDK **24**, compile/target API **37**, Java bytecode target **17**.
- `assembleDebug`, `assembleRelease`, `assembleDebugAndroidTest` and `lint` passed on the final source. Release APK is intentionally unsigned.
- **18 unit tests passed**, 0 failures/errors: 10 HTTP/security/isolation/idempotency tests, 6 SSE/event tests, 2 theme/language tests. These cover HTTP 401, unsupported discovery, redirects, prefix routing, fragmented Chinese UTF-8, absent terminal status, explicit approvals, compatible responses and palette contrast.
- **4 connected phone tests passed**, repeated after the final test-selector/display fix: every destination and streamed Demo chat; real Room task create/edit/complete/delete; all four persisted themes; all three interface language modes.
- **1 additional tablet smoke test passed** for Demo chat and every navigation destination at 1800×1200 pixels / 240 dpi (1200 dp width). The phone viewport was 1080×2400 / 420 dpi (about 411 dp width). Device: Android **16 / API 36**, Google APIs x86_64 emulator with WHPX acceleration.
- Actual captured screenshots were visually reviewed for phone themes, Markdown chat and the tablet navigation rail. They contain synthetic Demo text only. See [screenshots](screenshots.md).
- `apksigner verify --verbose` passed for the installable debug APK (APK Signature Scheme v2). Package: `org.hermesmobile.app.debug`, version `0.1.0-alpha`, code `1`.
- Lint: **0 errors, 3 warnings**. One is the deliberately isolated debug-only HTTP configuration; two suggest SharedPreferences KTX style changes. Credential writes intentionally check synchronous commit results. No lint baseline or broad suppression was added.
- Tracked-file sensitive-artifact/credential pattern review and Git whitespace review passed before publication. Signing material, local configuration, caches, logs and APK output are excluded from Git. Temporary tools, downloads, caches, emulator data and QA intermediates were placed on E:; source and deliverables remain in the chosen repository.

## APK checksum

`e7563bf6917b48f220eb1db6cbb4251f1f4ba599c536b6af32b824c045e6876b`

File: `hermes-agent-mobile-0.1.0-alpha-debug.apk`. A copy and `SHA256SUMS.txt` are in the ignored `artifacts/` directory. The normal build output is `app/build/outputs/apk/debug/app-debug.apk`. The debug key is for development installation, not a production release-signing identity.

## Not verified

No live Hermes server URL/token was supplied. Real-instance session interoperability, real tool execution, server approval delivery and job availability are **not claimed as live-tested**; transport/protocol behavior was tested with MockWebServer fixtures and the official documented schemas. Android microphone recognition, online-service behavior and permission UX require physical-device testing. The suite opened Voice without activating audio.

Physical foldable hinge/posture behavior, real tablets, Android 8 runtime execution, TalkBack and large-font stress testing remain future device-matrix work. Layouts adapt by available width; this is not a claim of complete foldable certification. No signed production release or Play Store publication has been performed.

## Recommended next milestone

Test against a private, trusted HTTPS Hermes instance and a physical Android phone. Validate approvals with harmless operations, auth expiry, process-death recovery and job schemas. Then add incremental history synchronization and broader accessibility/device coverage. Advanced voice, server job mutations, attachments, notifications and widgets remain on the [roadmap](../ROADMAP.md).
