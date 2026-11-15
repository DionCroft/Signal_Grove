# Bounded workspace-review pre-input synchronization

15 November 2026. Test-fixture correction only. The full goal stays active.

## Result and scope

The API37 workspace-review pre-input race is corrected in the test fixture.
Actual keyboard/inset transitions are re-observed within the original five-second
deadline before input. Native single-tap protection, full caption/viewport checks,
fresh consent and the one workspace change remain required. A real late-keyboard
regression and the originally failing case pass among **27 focused methods on
each of API26 and API37**, without skips. Original diagnostics verify nine Review
actions and twelve single DOWN/UP pairs per API, with no input retry.

The exact E-drive build passes **1,011 JVM tests**, both lint variants with zero
errors/fatals, and both debug APK assembly tasks. Lint retains 51 debug / 50 release
warnings. This is focused test-fixture acceptance; final full device and phone
acceptance remain open. The source snapshot, installed APK hashes and native
method inventories independently agree; no production APK is published here.

The prior isolated Pi checkpoint still verifies offline-configured actual SDK
freeze, exclusive original-journal drain and normal native shutdown in one
generated home. All 39 focused Pi methods pass; Windows coverage remains
885 positive / 273 platform skips in 1,158 cases. Parent `4e66626` independently
passes 1,158 hosted Linux methods/no skips. Full unprofiled/unbound/fleet behavior
and the original owner shutdown warning's cause remain open.

The retained `4e66626` CI artifact separately identifies an API26 phone-draft
navigation failure: 541 methods started, 540 positive, one missing `nav-chat`
node before input. Its cause and the original Demo/history failures remain
unresolved. Current `6e1325b` hosted upstream inspection still fails stable/main
semantic review; its Android run was in progress at this checkpoint. Failed CI
artifacts and timed-out QA installations are preserved. The latter were reconciled
after owned-emulator cold recovery before any test dispatch or further install.
Live Hermes, the owner's phone and published beta.9 are unchanged. The full goal
remains active with every original acceptance priority retained.

## Failure, correction and regression

The exact failed parent `967638d` artifact remains retained: Android run
[43709066154](https://github.com/DionCroft/Signal_Grove/actions/runs/43709066154),
API37 shard 1 job 132800358972 / artifact 14161532895, archive SHA-256
`e5cc88481b12980964330a151b11f8f588c6ce8c07639e352ff5f39774957696`.
Its 601 started methods finish with 600 positive / one failure in
`CanonicalViewerUiTest.checkedWorkspaceReviewSendsOnceAndRequiresAFreshAttachmentAfterAcknowledgement`.
No Review input was attempted. After a stable keyboard-closed observation, the
fresh sample records native IME absent / delivered inset visible with 282 ms
between those non-atomic reads; the helper returned an incoherent null viewport.

The fixture now uses one original five-second deadline for readiness, full caption
checks and the fresh pre-input geometry observation. A contained, keyboard-matching
viewport/control tuple must remain stable for 250 ms. A changed/null fresh sample
resets readiness before any input; the same observation loop continues only until
the original deadline. Exact geometry equality and complete native caption
containment remain required. This change never retries a dispatched input.

`lateKeyboardBeforeWorkspaceReviewIsObservedBeforeItsOnlyNativeTap` closes the
real keyboard, then opens it through the actual directory text field after the
first settled observation and before the fresh pre-tap sample. It requires one
keyboard request, a disabled Confirm and unchecked fresh consent, zero workspace
changes before consent, then one acknowledged change and one link close. Both
this regression and the original failing case pass. Production Kotlin, consent
policy, the native single-tap implementation and its retry refusal are unchanged.

Strict dry-inventory/native-positive method unions agree on all 27 selected
methods per API, with zero failures/errors/skips. Nine schema-3 Review diagnostics
per API each match their own successful native diagnostic: exactly one accepted
DOWN and one UP, no blocked/second dispatch, no error, and fresh consent visible.
All twelve native records per API (including Cancel) satisfy the same single-pair
check. Original samples prove keyboard-closed to keyboard-open transitions with
at least one pre-input resample: one observed transition on API26 and two on
API37. Largest recorded Review observation totals are 2,166 / 2,176 ms. These
include post-input checks and remain below the unchanged five-second limit.

## Build and emulator recovery

The E-only guarded build completes `testDebugUnitTest lint lintRelease
assembleDebug assembleDebugAndroidTest`: 1,011 distinct JVM cases / 97 XML reports,
zero failures/errors/skips; lint zero errors/fatals and 51 debug / 50 release
warnings. Receipt ID `20261115-131449-6d74fd6b8b3441cfbcad146947413a98` binds
snapshot `b117ab22dba47ca6fdec662aaf8db18b40da8f0b34f183fa5ec1c46ad34aaf4f`
to parent `6e1325b` plus the single uncommitted test change. Documentation is
updated afterward; this is not a claim that those APKs were built at the later
documentation commit. Both verified debug packages share the existing debug
certificate; no original production key or phone package is used here.

Two pre-test ADB installs timed out. The API37 Android framework had lost its
package service; API26 also showed intermittent service unavailability. Native
test instrumentation had not started. Original logs, install intents and data
remain preserved. Only the two exact owned QA emulators received a normal cold
stop/start with no data reset or snapshot replacement. No root/permission change,
ADB-server restart or phone action occurred. After recovery, installed APK hashes
showed both expected packages already present on API37, so neither install was
repeated. API26's app was already correct; its observed older companion hash
justified one companion update. Both expected hashes were checked again before
one actual focused instrumentation dispatch per API. Cold recovery does not
establish the emulator framework failure's root cause.

The earlier incomplete SDK/build-tool metadata and shared Gradle cache failure
are separately retained. A fresh E-only SDK and Gradle work root produced this
successful build; checks were not weakened or failed caches silently reused.

## Independently retained failures and remaining acceptance

Parent `4e66626` Android run
[43713713507](https://github.com/DionCroft/Signal_Grove/actions/runs/43713713507),
API26 shard 0 job 132813932501 / artifact 14163002063 completes 541 methods:
540 positive and one `PhoneDraftRoutingTest.cancelReviewKeepsTheRemoteDraftAndPhoneFilesUnchanged`
failure. Its verified archive SHA-256 is
`53a4305bf460fcebb7d5c27cfe6184f73e50c844c96e3428515b6a3cef2d58a2`.
The original log shows no `nav-chat` node at the attempted input; a later
non-atomic snapshot finds Home selected, all six navigation nodes, and a visible
keyboard. This identifies the failing transition, not its proven cause. No CI
rerun is dispatched and neither this nor the original Demo/history causes is
waived by passing focused workspace tests.

Source `6e1325b`'s hosted parser, pinned sidecar and registry jobs pass; its upstream
job still fails stable/main semantic checks. The Android verify job was running
at the recorded snapshot. That snapshot is not final-source standard-device
coverage. The earlier recorded full `09306e6` device unions retain their own
exact-source scope. All supported used/transitive compatibility, actual unbound
work/status, original owner shutdown cause, fleet/worker/workspace/automation/
integration and original-key physical-upgrade/release requirements stay open.
The prior isolated SDK lifecycle record remains separate and is not broadened
by this Android fixture correction.

## Exact identities

| Item | SHA-256 |
| --- | --- |
| `independent combined review` | `65343dda9503d57774b8e66be633a010a1fbd3f9e2eae0843d489d8eb0d62819` |
| `independent build review` | `520f0883e64a9025d875bdb21fe4ab9f712783f0f57c19266d6acb8f957675c9` |
| `app/src/androidTest/java/org/hermesmobile/app/CanonicalViewerUiTest.kt` | `38f7d41362e8be753c372b37c512b7867b836f7efe2115cd64bad565ddc34a77` |
| `debug app APK` | `6f94529cb373c756d45c9d8be288177371b584b91984dbacdc9122b98b8daf98` |
| `debug companion APK` | `230393878f294a5dfbf8be8d90aebd3c5a58f7b5716a374aad93787b52f9121d` |
| `review-emulator2460-26/operation.json` | `e6aac8a6e93337bf4b8581140748c0f93db02e90b4f6c1821deba5fe2a2e60d1` |
| `review-emulator2460-26/instrumentation.log` | `bd05d1dfe78ebacb62475f21a234c35844adf320a53c923f2bc05ae853642168` |
| `review-emulator2460-26/inventory.stdout` | `6fb2485bfc984eed279ea8c78e8ce9c5475d91fbc68ba536928d1e23f3293495` |
| `review-emulator2460-37/operation.json` | `ae9e04f5c28dbf7eaa19f82941f3231b6ec726c3fe5cbd7b9a42b0737a0b0fb3` |
| `review-emulator2460-37/instrumentation.log` | `e3e081e4a28ae9ae09b14f02a98eb5032d920534f24b4e5499ac70b825b52d37` |
| `review-emulator2460-37/inventory.stdout` | `f928fb060882168f5cf1843fe202b3fb764d56e0c52a91cf75779d735a347565` |

Private raw outputs and every retained diagnostic are bound by the combined
review; machine paths, credentials, owner content and signed artifact download
URLs are excluded from this public record.

[Next action](../../PROJECT_STATE.md) · [Full goal](../GOAL.md) · [Full audit](../AUDIT_EXECUTION_PLAN.md).
