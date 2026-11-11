# Native dialog input diagnostics and controlled UI acceptance

## Problem and bounded behavior

The retained API26 workspace Cancel result previously said only that Espresso
returned a non-success status. It did not identify WARNING versus FAILURE, event
attempts, timings or whether Cancel had acted. The exact pinned Espresso 3.7.0
AAR is inspected locally (SHA-256
`5dd90e366838bf044cb52eae06474debd285df18a7a77c40441ac8e8951bb00f`). Its
MotionEvents down path can attempt DOWN up to three times, while a sufficiently delayed DOWN
call can make Tap return WARNING. See the [official implementation](https://github.com/android/android-test/blob/main/espresso/core/java/androidx/test/espresso/action/MotionEvents.java);
the retained local bytecode proof binds this finding to the actual dependency,
rather than assuming that moving source is the installed version.

The test helper still calls Tap.SINGLE once and requires exactly SUCCESS. A
bounded UiController wrapper refuses a second DOWN before forwarding it. A
failed UP permits only the library's CANCEL cleanup; sequences, unrelated input
and attempt reuse cannot inject additional work. No warning becomes success and
no failed control is tapped again. Six scripted-controller cases verify success,
internal retry refusal, failed-UP cleanup, WARNING rejection, redacted errors and
reuse refusal. These scripted cases make no real-input claim.

Real calls record one bounded atomic JSON report: a fixed action enum, geometry,
window focus/attachment/IME facts, per-event timestamps, Boolean dispatch results
and a closed error category. No view text, captions, paths, chat, credentials or
exception messages are included. Events are counted at the instrumented
UiController boundary; kernel delivery is not separately traced. A linked
read-only workspace observation records control presence, viewer phase and the
repository write count even if dispatch fails. Observation failure cannot replace
the original input failure, and neither observation accepts a test or retries it.

All four affected UI callers retain their exact geometry, caption, keyboard,
consent and effect assertions. Independent source comparison verifies three
callers change only fixed labels, and the workspace caller retains its original
preconditions and postconditions around one call. Production/build inputs,
networking, approvals and dependencies are unchanged. Earlier one-tap wording
meant a single helper invocation; old runs did not record internal event attempts.
Their event multiplicity and the original Cancel failure cause stay unproven.

## Retained run421 failures

Source420 compiles all new instrumentation. The first ten-case invocation fails:
API26 has six positive scripted cases and four failed UI cases; API37 has eight
positives and two failures. Both are terminal failed batches with original font
and IME settings restored, not accepted ten-case runs.

Workspace fails its keyboard-visible precondition on both APIs before native
Cancel dispatch. API26 History also fails an initial keyboard condition; Kanban
and phone-draft encounter focused-root failures. An actual SystemUI-not-responding
dialog is observed on API26 during this period, with app windows lacking focus.
This does not establish the cause of every earlier precondition failure.

API37 History records WARNING for its More control: one DOWN and one UP both
return true at the observed boundary, with no retry. The DOWN dispatch call takes
859 ms against the pinned 400 ms long-press threshold. The failure stays failed;
the later Close is not executed. Kanban and phone-draft pass. Total gesture time
also includes UP acknowledgement and Espresso settling, so it is not used as a
substitute for the DOWN measurement. The historical workspace Cancel non-success
is not retrospectively classified as the same warning.

## Preserved recovery and new measurement

Both debug packages are quiesced only after instrumentation is terminal. Their
protected app files are fingerprinted, stores synchronized, and each verified
owned emulator receives one console stop. Both console stops complete without
forced QEMU termination. Stopped userdata, backing images, encryption keys and
configuration are copied to fresh private backup directories and hash-checked.
The exact existing AVDs relaunch without data wipe or geometry changes. New boot
IDs, identical protected-file fingerprints, APK hashes, UIDs, configuration and
settings are verified before another UI invocation. No physical phone is targeted.

The API37 initial per-file hash scan times out before any stop request; a batched
read-only continuation completes the same preflight. An API26 UID assertion is
corrected to match the exact package rather than both prefix matches. Its private
fingerprint files also require the original normalized text-hash representation
on Windows; both raw before/after files and normalized fingerprints are equal.
These host-harness interruptions are retained and do not trigger another restart.

Run426 is a distinct post-recovery four-UI-case batch per API, on the same exact
APKs, one emulator at a time. It uses CI's zero animation scales during testing,
font 1.3 on API26 and 1.0 on API37, with native keyboard enabled. All prior font,
keyboard and animation values are restored exactly afterward. All four methods
pass per API, with six SUCCESS tap reports, each forwarding one DOWN and one UP.
Workspace Cancel is absent afterward, the viewer remains Following and workspace
writes stay zero. This controlled measurement does not prove behavior under every
normal animation, long-running emulator or physical-device condition.

| API | Passing scripted cases in retained run421 | Fresh passing UI cases | Native taps | DOWN call duration (ms) | Retained protected files |
| --- | ---: | ---: | ---: | ---: | ---: |
| 26 | 6 | 4 | 6 | 8–18 | 75 |
| 37 | 6 | 4 | 6 | 11–33 | 1547 |

The measured workspace-form screenshots are visually inspected: English,
Chinese and pinyin action captions fit above the native IME on both APIs at their
recorded scales, with clear foreground colors and no clipped action captions.
This is controlled form/keyboard inspection, not general animation acceptance.
The recovered pass is not attributed solely to the restart: recovery, scheduling
and API26 animation settings changed, and original failures remain recorded.

## Build, source and hosted evidence

Source420 freezes 754 files at digest
`a45c50cfd7ad3709e973e25d89ac80546d2b0bddc1334fb2fd1294fef87b2c9f`. All required build tasks pass: 987 JVM cases /
95 reports, zero-error lint (51 debug / 50 release warnings), debug
app and test assembly. All 293 policy tests pass. The original debug signer and
all 32 packaged libraries' ABI/load alignment checks
pass, including 16-KiB alignment where required. No model is bundled.

- App SHA-256: `1a2307b5f37ac0cba8e0a1b10446bba19f0c50647c0f8bdab8af19a62ba14f23`.
- Companion SHA-256: `8db60fbe5d02713efc98524c038aa694a1620214f43294dfb33917a7ea9085c9`.

Separately, hosted main `2fde17d` API26 completes all 1118 standard methods in
3336.349 seconds with exact distinct starts/positive terminals, zero skips and no
partial results. Independent parsing matches the retained collector result and
verifies the five external-model classes / 11 methods remain explicitly not_run.
The large workspace/native-IME case passes there. This acceptance remains bound
to that earlier head and its artifact, not the new diagnostic build or API37.
Artifact 13889469842 has SHA-256
`fac1ad7163458af0bfb7428726c98a744478cd5d79d5c944dffc2445a93925c0`.

Private evidence includes source/build/proof420, raw run421 failures and fixed-
schema tap reports, pinned Espresso bytecode418, preserved recovery425 receipts
and checked backups, fresh run426 logs/reports/screenshots, final proof427,
unchanged-oracle proof428 and hosted-main independent proof423. No device files,
keys, model weights, screenshots or generated output enter Git.

The full audit stays active. Original Cancel/ADB-loss causes, normal animation/
physical/current-source acceptance, remaining model/helper/lifecycle gates,
production authentication, worker/project/file ownership, reviewed dispatch,
automations/integrations, upstream/advisories and signed release remain required.
Next continue the worker/project/session/file gap with source-backed protocols;
this diagnostics chunk is not a new APK release.

## Subsequent complete API37 hosted result

After the diagnostic chunk was published, main2fde API37 also completes
successfully: 1119 exact distinct starts/positive terminals in 5665.758 seconds,
zero skips, failures or incomplete cases. Artifact 13892943951 is independently
hash-checked (`987f8154cf51a41acef9231fc544076c6914fb3299251f427f92fee3e754042b`)
and reparsed; its manifest exactly retains the same five external-model classes /
11 not-run methods. Together with the earlier API26 proof, this closes that
specific head's complete standard Android matrix. It does not retrospectively
change any failed local batch, accept external gates, accept upstream source
drift or establish a full-suite result for later commits. The newer compatibility
run passes its parser/registry/sidecar checks and fails only the latest stable/
main source-inspection job. The full audit and next worker/project stage remain.
