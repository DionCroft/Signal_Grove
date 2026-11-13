# Resumed acceptance goal and original Demo baseline - 13 November 2026

The owner resumed the same existing goal, explicitly retaining four priorities:
the API26 Demo failure; upstream Hermes compatibility/shutdown review; real
project/workspace/worker, automation and integration acceptance; and final
production upgrade/release acceptance. [The expanded goal](../GOAL.md) records
their concrete finish conditions. Every original audit row remains required.
No replacement goal is created and no completed earlier check is reset.

Beta.9/code114 is public as an experimental preview. Its APK/source/signers and
all six public downloads remain as [recorded at publication](2026-11-13-beta9-public-preview.md).
Availability is complete; unresolved production/interoperability acceptance is
not closed by publication. The original failed/unknown outcomes remain evidence.

## API26 investigation: accepted baseline, cause still open

The retained source1180 hosted shard reports one failure in
`Milestone03Test.demoClarificationChoiceIsReviewedBeforeSubmission`: the expected
multilingual Demo question node is absent at `performScrollTo`. Source 31f05c7
later passes Android CI without changing that test. Neither fact establishes
the cause or justifies discarding the failed run.

A fresh disposable x86_64 API26 emulator uses the exact original source1180
local app/test pair, confirmed by installed APK SHA-256. It matches the CI font
scale 1.3, disabled animations and one reviewed default-keyboard preparation.
The original three-method Milestone03 class is then exercised in five preplanned
independent trials. All 15 method executions pass with unchanged source/assertions.
These positive isolated trials do not reproduce or fix the original failure.

| Trial | Host seconds | Raw native log SHA-256 |
| --- | ---: | --- |
| 1 | 10.591 | `30d3af2f58f00c38ae44532951b92307a314b4a1917bf784224e337800dfe023` |
| 2 | 7.517 | `accba1cd9539a7cb40db20106feb904e0b1aba79c3148f4646d4445e87f6bd6d` |
| 3 | 7.324 | `c1072e1dc00a1f72740030ee72c099344a5ccd1582cfb6fcd335d5c422a0f340` |
| 4 | 7.324 | `aa08af354a0690f56d7810e9b1659cd3c57b4db86a612e40eaf868d74a28779d` |
| 5 | 7.243 | `f873f6e5ed464281472ac89b2e21adb409e21481fba9cd6997a6df94598f156b` |

App SHA-256: `d2574160504e3c1d124f873358ffacf3a7fa03657c37c16b19e36a94cb9e196b`.
Test SHA-256: `54daea44c162e64d49a3aecff54d4fa636a32e8c5fc79c9b537adb793736d745`.
Exact source: `1180d481a99f031ed40a1c0e95b16702dfce3d3a`.

One original 598-case shard 1/2 reproduction is running after clearing only this
owned fixture's synthetic debug data. Its full inventory is 1,135 API26 cases;
the same twelve prerequisite-dependent model methods remain explicitly excluded.
This is a deliberate fresh reproduction, not a replay of unknown work or an
automatic retry until green. All outcomes/raw logs are retained. A running or
single completed shard is not complete standard-suite acceptance. The next step
is to reconcile that exact result and inspect rendered/lifecycle/preferences
readiness before selecting a cause-specific correction.

No test timeout, review/submission assertion, app privacy guard or user-phone
data is changed. No physical device is currently visible over USB; emulator
observations cannot close final production-phone gates. Tools/AVD/evidence stay
on the verified Windows owner and approved temporary storage.

## Shutdown and compatibility follow-up

The preserved native-owner exit trace shows the registered heartbeat cleanup
calling `clear_backend_heartbeat` after the database handle is closed, causing
the loud bounded reopen path. The owner probe already awaits the native runner
and control stop paths. Exact ownership/caller ordering still needs review and
supported lifecycle validation. This checkpoint does not suppress warnings,
unregister callbacks, patch immutable SDK sources or claim clean shutdown.

Retain the last fully inspected 40 stable/101 main changed-handler findings,
separate auth/authority/room component scopes, uncertain worker receipts and
all real transport/profile/actor, projects/files/workers, automation/rooms,
integration/configuration and phone/release requirements. The remaining work is
tracked by the [full original audit](../AUDIT_EXECUTION_PLAN.md).
