# Route diagnostics investigation checkpoint - 15 November 2026

The owner requested pausing the full goal and releasing only if stable. These
seven source changes are saved on `feat/on-device-ai` as an investigation;
they are **not qualified for main or a new production APK**. The goal is paused.

## What changed

- A debug-only route observer records fixed event/route identifiers and coarse
  frame/inset metadata. It excludes chat text, sessions, credentials and model
  input. Observer failures cannot alter the underlying navigation.
- A bounded test observer captures the actual Home new-chat callback and
  NavController changes. It retains/restores its observer and never injects input.
- The original Demo tests retain their gestures, deadlines, review/submission
  assertions and action sequence while collecting that observation.
- Phone-draft navigation uses the existing bounded unique-route helper, with one
  click. Two native diagnostic contract cases exercise the actual callback and
  prove an observer exception cannot change navigation.
- The release Kotlin bytecode for `record` contains no observer lookup or call.
  This bytecode inspection does not qualify a release APK.

The earlier Window callback prototype was superseded before installation:
Compose's pinned test input directly dispatches to the View and bypasses that
callback. Its source/build remain private evidence, outside this checkpoint.

## Exact build and terminal results

Parent: `62c8bbe0cad0a5c212225a30251269f698900ad0`.
E-drive immutable build snapshot:
`1621114d106ecb40a4fab31c54ef5385e444b0b992d45d4004ca6fffbb3853d3`.
The required debug JVM, lint, debug assembly and test-APK assembly tasks pass.
There are 1,011 JVM cases /97 XML reports, no failures/errors/skips, and zero
lint errors/fatals with 51 debug /50 release warnings retained.

| Focused run | Started | Positive | Failed | Scope |
| --- | ---: | ---: | ---: | --- |
| API26 | 16 | 16 | 0 | Four exact selected classes, no skips |
| API37 | 16 | 15 | 1 | Same classes/APKs; failed result retained |

The API37 failure is
`PhoneDraftRoutingTest.cancelReviewKeepsTheRemoteDraftAndPhoneFilesUnchanged`:
`Entire control above native IME: phone-draft-cancel`, focused visible rect
`(0,63)-(1080,959)`, native keyboard visible and IME bottom 961 at display height
1920. A process exit code of zero is not a passing JUnit result. The strict case
parser refuses; no successful final operation receipt is manufactured.
The original API37 operation remains `NATIVE_COMMAND_RETURNED`; a separate
read-only pause review reconciles the actual 16 starts, 15 positives and one
negative status. No failed assertion is relaxed and no test is rerun on pause.

Both selected original Demo cases pass in these runs. Their retained route
traces show the real Home callback, clear, navigation request/change/return and
one controller identity, without observer failures. These passing observations
do **not** establish the cause of the older API26 Demo/navigation/history failures
or prove those failures fixed. Exact-source full standard coverage is still open.

| Private evidence | SHA-256 |
| --- | --- |
| Build review | `8709acad0f588ae7d41491bd8efb5ba242034e97e2dbea542820514871600817` |
| API26 original raw log | `b0b78c2002ee14770f903493457d704b96570c14498c4fac07929869703f0952` |
| API37 original raw log | `fc2754c7ea4d4266d2957a82ebb7849bd6e46ccdc6d62ddff29811b2330356c2` |
| Read-only pause review | `e3088911a796da1faebaca58fd24c85a178f5d9b514552688d308c5af1b4be40` |
| Debug application APK | `5e343c9d4597340200c59ef83a5f7dd991b3f6c489cb3439803b461923855102` |
| Debug test APK | `a4bead33f9fad2baab6960750a549c08c4245fa4b16fdfd8f22edc64a6e9a6ee` |

APK code is still 114; these are private debug fixtures, not updated public
release assets. The owner's phone, production app, live Hermes and beta.9 are
unchanged. Prior source-bound 27-method per-API workspace checks remain separate.

A stale owned emulator installer session was destroyed exactly once through
the supported package command and reconciled with unchanged installed test
bytes. Android retains its destroyed session entry. The old install's original
completion remains unproven. Slow APK hash reads received a finite I/O budget;
UI deadlines and assertions were not changed. Failed preparations remain saved.

## Resume boundary

Resume only when requested. First inspect these exact receipts and the retained
IME assertion; do not merge this investigation or retry uncertain writes blindly.
Complete the original API26 cause investigation, full upstream/shutdown and real
worker/integration acceptance, and final original-key production upgrade/release
checks under the [unchanged goal](../GOAL.md).
