# Current development validation — audit follow-up

2026-11-02, connection-isolation chunk: 43 JVM tests passed; full API 36 suite passed 33 tests (30 existing + 3 regressions). A final targeted 10-test rerun passed after the history/recovery guards were tightened. `testDebugUnitTest lint assembleDebug assembleDebugAndroidTest` passed. New coverage blocks switching during unknown acknowledgement, discards delayed job details, and rejects old run/stream writes against a newer durable submission. No schema change. API 26 has not yet been rerun for this chunk. See [implementation progress](AUDIT_FOLLOW_UP.md).

Response-history chunk: 44 JVM tests passed; 6 targeted API 36 regressions passed (compatibility history, run recovery, connection isolation). Required build/lint/APK checks passed. Compatibility finish reasons now describe response outcomes separately; `[DONE]` cannot overwrite an earlier failure/cancellation outcome or assert native run completion. Completed and usable partial answers remain eligible for subsequent compatibility history. No schema change.

Approval-storage chunk: 44 JVM tests passed; 13 targeted API 36 tests passed (8 control-storage, 2 recovery, 3 isolation). Required build/lint/APK checks passed. New regressions cover SSE state preservation, generation ordering, tombstones after empty snapshots, concurrent response claims and late acknowledgements. No schema change; existing generation/request IDs remain the decision identity.

All-four-defects checkpoint: 46 JVM tests and all 38 connected tests passed on both API 26 and API 36. `testDebugUnitTest lint assembleDebug assembleDebugAndroidTest` passed. The first full reruns exposed existing asynchronous notification/package-selection fixture assumptions; bounded delivery waits and exact package matching corrected those checks, then both complete device suites passed. Cron coverage verifies local oversize rejection, known pre-mutation refusal vs ambiguous HTTP 400, durable receipts, no mutation replay, bounded structured errors and Retry-After. No schema change. Physical hardware and live Hermes checks remain open.

Server-discovery chunk (`1.1.0-dev`): 48 JVM tests and the full 43-test API 36 suite passed. All 8 discovery/isolation cases also passed on API 26 at 130% font scale. English and bilingual/pinyin screenshots were inspected. Tests cover prefixed bearer GETs, explicit full-catalog probes, degraded readiness over HTTP 200, unavailable provider metadata, a delayed old-connection response that is awaited before asserting the new inventory, offline cache activation and startup restoration/configuration races. Build/lint/debug/instrumentation checks passed after correcting a lint indentation finding. Runtime providers, skills and readiness are synthetic fixtures; live-server integration remains unverified.

Durable-request chunk: 55 JVM tests and all 45 connected cases passed on both API 26 and API 36. Required unit/lint/debug/instrumentation build checks passed. Added coverage includes full JSON/header retention, reopen and concurrent-writer immutability, wrong-scope/missing/corrupt request refusal, size limits, per-conversation deletion/clear, v4→v5 migration, and HTTP loss after admission followed by exact body/key/channel recovery. Earlier v1/v2/v3 migrations still pass. No live agent executed; the lost-ack fixture confirms client traffic, not a production server's deduplication implementation.

Requested-runtime chunk: 59 JVM tests and the full 49-case device suite passed on API 36 and API 26; the complete API 26 run used 130% font scale. Required build/lint/APK checks passed. Added coverage includes all three request transports, scope-separated persisted choices, stale preference updates, catalog account restrictions, reviewed manual selection, model-options-only requests, original model/provider/options retained after lost admission, visible reply content, and a real drag followed by appended output that preserves reader position. English/Chinese/pinyin screenshots were inspected. Existing task UI checks now scroll to recycled/offscreen fields and await asynchronous Room saves; language checks also assert runtime choices are retained. These are fixtures, not live provider executions.

Session-control chunk: 63 JVM tests and all 57 connected cases passed on API 36 and API 26 (130% font). Required unit/lint/debug/instrumentation build checks passed. New coverage verifies prefixed/authenticated exact lock/fork/steer requests and acknowledgements, bounded metadata, pre-mutation refusal classification, durable unknown receipts, atomic concurrent claims, wrong-scope review refusal, v5→v6 preservation, reviewed Demo lock/fork lineage and copied history, active-run steering without readmission or terminal inference, stale appearance/privacy/connection isolation, and compatibility capability-upgrade history/transport preservation. Demo branch screenshots were inspected. These are simulated backend contracts; live-server branching and model routing remain unverified.

Android 17 LAN-access chunk: 65 JVM tests passed. Complete device suites passed 57 cases each on API 26/36 and 58 on API 37 (no ignored API 37 cases). The API 37-only case is excluded on older systems. Required unit/lint/debug/instrumentation builds passed. The real Android permission UI was denied, then granted; the client sent no request while denied and successfully reached a private-address fixture after grant. Public/loopback access, private DNS answers, IPv6 private routes and revoked permission are covered by policy regressions. The multilingual Android 17 Settings screenshot was inspected. API 37 exposed the transitive Espresso 3.5 input-reflection failure; stable test-only 3.7 fixes it. Two navigation fixtures now explicitly activate Demo after startup and wait for asynchronous task/UI persistence; all four updated navigation cases passed on each API before the final full API 37 run. No live LAN Hermes or physical VPN was tested.

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
