# Signal Grove project state

## Current priority - on-device AI, authorized 10 November 2026

The owner requested a physical LFM3-1.7B test first, then native offline chat,
user-added models and a grounded onboarding/troubleshooting helper, with tested
regular commits/pushes. The complete original audit remains required. See
[the expanded objective](docs/GOAL.md) and [acceptance plan](docs/ON_DEVICE_AI_PLAN.md).
The owner edited and resumed the expanded goal through the app controls; the API
now confirms it is active with this scope. Do not clear or falsely complete the
unfinished original audit.

The retained setup guide is now a separately tested delivery chunk. Its five UI
cases pass on API 26 (32-bit process) and API 37, including language/connection
retention, recreation and all nine pages. Four address-formatting JVM cases pass.
The original six draft files were preserved byte-identically through validation.
Its measurement build passes 887 JVM cases and zero-error lint.
This accepts the deterministic guide, not the model-based helper or a release.

Physical offline CLI feasibility now passes: official hash-pinned LFM3-1.7B
QAD Q4_0, CPU generation about 51.44 tokens/s over three benchmark repetitions,
largest recorded process high-water mark about 2.04 GiB, bounded cancellation.
One monitor exit race was reconciled without replay. The ungrounded setup answer
was inaccurate; helper quality is still open. See docs/ON_DEVICE_AI_VALIDATION.md.
No production APK/data, Pi/model-default or credential changes occurred.

The independent GGUF reader passes 22 JVM cases plus a 45 ms full-model phone
scan and scan cancellation. The private model-library component now passes 11
additional JVM cases. See docs/contracts/2026-11-10-private-model-library.md.
Its earlier measurement build passes 887 JVM cases, 229 policy cases and
zero-error lint (50 debug / 49 release warnings). Earlier TOKEN_LIMIT failures
and blocked activity fixtures are retained. Only exact owned debug processes
were stopped during recovery, with no data clearing. Later controls and the
passing native measurement below supersede those incomplete checkpoints.

The direct physical fixture now runs without the blocked ActivityScenario
bootstrap. Same-library controls isolated the model-declared repetition penalty
(1.1), omitted by the bare sampler. The corrected app passes both complete phone
methods: consecutive replies, explicit/coroutine cancellation, recovery, overflow
refusal, unload/reload and metadata scan/cancellation. Phone/API 26/API 37 native
boundaries and a forced 32-bit core-app launch pass. No old request was replayed
or assertion relaxed. See docs/contracts/2026-11-10-native-local-engine.md.

The native foundation is committed/pushed. Chat and Settings now open a separate
MVVM phone chat/model activity with explicit licence consent, import/download,
context/load/unload/delete, local history and reviewed clipboard handoff. Five
synthetic UI cases pass on each of API 26 and API 37. Its full measurement passes
905 JVM / 89 reports, 229 policy tests and zero-error lint (50 / 49 warnings).
Eleven controller cases pass in a newer 906-case JVM snapshot, including the
after-save cancellation race. See docs/contracts/2026-11-11-local-ai-ui.md. The
physical phone still has the earlier native-engine APK; no new release is claimed.

Exact next action: implement the versioned, grounded helper, then validate the
combined app on the physical phone before a release. Live Android transfer/picker,
lifecycle/upgrade, representative compatibility and the complete remote audit
remain required.

## Earlier audit checkpoint (historical)
The full audit goal remains active and incomplete. Continue verified commit
chunks with regular pushes. The complete remaining scope is in
[the audit ledger](docs/AUDIT_EXECUTION_PLAN.md); retain earlier failures and
checkpoints in [validation](docs/VALIDATION.md) and Git history.

## Published preview and physical checks

[Beta.8 is public](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.1.0-beta.8),
code 111, exact application source `d0c01b818604b3dee057d544cff432d27c8bba63`.
All five anonymous asset downloads, APK signers and tag target match reviewed
bytes. Stable 1.0.0 remains latest. Its exact-source hosted gates remain 727 JVM,
1026 API 26, 1027 API 37, 201 policy and both lint 0.

The production phone retained its original-key APK, UID, first-install time and
saved connection. After choosing Automatic, the owner sent one new no-tool check;
its successful reply was independently observed. An existing Desktop transcript
loaded read-only. Project details after upgrade are owner-confirmed; no current
independent project-screen capture is claimed. Remote inventory was82 rows with
direct Desktop/Discord reads passing; some cached rows are unavailable remotely.

The published APK still has the disclosed stale-history error banner. The fix
below is unreleased. No Pi deployment, model/default change, online speech or
worker execution occurred during these chunks.

## Current development checkpoint - actual owner bootstrap

The reusable finite Windows probe now executes immutable `2bd378e` through real
profile/host reservations, official runtime initialization and runner/dashboard
startup. Both authorities pass native SID/session, ticketed WebSocket identity,
foreign-profile refusal, one-use replay rejection and no-ready owner-loss checks.
All 1,167 imported source modules are Git-bound. Independent post-process SQLite
checks pass with zero sessions, rooms and admissions in both stores.

Its supervisor returns 2 (`CORE_PASS_SHUTDOWN_FINDING_OPEN`), retaining the real
legacy reaper heartbeat cleanup's post-close DB reopen stack. Core transport is
accepted; full lifecycle is not. No seeded owner-room, merged paging, outer
forwarding, scheduler or deployed-fleet acceptance is claimed. See
[scope and reproduction](docs/contracts/2026-11-08-owner-runtime-bootstrap.md).

Fresh required checks pass 836 JVM / 82 XML, both lint 0 and 229 policy cases.
Both original-key APKs are byte-identical to the caption proof; no fresh Android
native executions are counted. All 659 frozen inputs and original key/cache/
staged beta.8 digests match. The probe and prior fixes remain unreleased.

Exact `c9479ca` raw full hosted acceptance is now 836 JVM, 1,064 API 26 and 1,065
API 37, both lint 0. The archives, complete source method sets and successful
job/input-artifact bindings pass. Hosted installed APK hashes are not independently
attested. This does not close later commits' full release gates or main drift.

## Previous development checkpoint - receipt-return caption


The separate receipt-return caption now fits the small pixel button; actual
before/after images show intact English/Chinese/pinyin at the original API 26
font scale 1.3. Only one shape property changes. Required builds pass 836 JVM /
82 XML and both lint variants without errors. The test APK is byte-identical to
`b583162`; three fresh pending-scroll/cancel executions and six inspected PNGs
cover the current application, with the parent's 25-case proof kept separate.
All 658 frozen inputs and original keys/preferences/display/configuration/reverse
bindings, 28 legacy caches and staged beta.8 assets retain their proofs. Full
exact-head hosted acceptance remains open; this correction is unreleased.

The earlier five-file source review informed the real owner-bootstrap probe above.
Its empty read scope and retained shutdown finding leave seeded-room forwarding
and full gateway lifecycle open.

## Previous development checkpoint - fixed Kanban cancel footer

Cancel stays outside the scrolling Kanban body while busy, with fully reachable
48 dp Cancel/Close controls at both scroll ends. All approval/controller/transport
and unknown-write behavior, including the original fixture deadline, is unchanged.
The new trilingual regression failed on the old UI and now passes. Required local
gates are 836 JVM / 82 XML, both lint variants without errors, and 25 selected
native executions (12 API 26 / 12 API 37 / one compact). Six busy-footer images
were inspected. The separate receipt-caption finding from that checkpoint is
corrected above. All source, preference, display/configuration/reverse, key/cache
and staged beta.8 preservation checks pass. Earlier baseline/observer failures
remain recorded, and the precise older hosted failure cause is not claimed.
See [the cancellation contract](docs/contracts/2026-11-08-kanban-cancellation.md).

Full exact-head hosted acceptance remains required. Prior `c9479ca` hosted build
metadata is successful; its full API 26/37 jobs remain separate checks. The current
stable contract remains unchanged at `2bd378e`. Main `3e82260` has three unreviewed
handler changes and adds `POST /v1/room-members/runs/close`; review the exact source
and scoped contract before enabling room controls. No canary waiver or Pi update.

## Previous development checkpoint - history feedback

Missing-history feedback is exact to HTTP 404/session_not_found and preserves
saved messages. Selection/profile/client/read-generation fences prevent late
feedback/loading completion from affecting another read. The global error banner
is compact, with full scrollable details and reachable 48 dp controls. Successful
concurrent cache-write ordering remains separate work. See
[the history contract](docs/contracts/2026-11-08-history-feedback.md).

Required builds pass 836 JVM/82 XML and both lint 0. Fresh native checks pass 20
API 26, 20 API 37 and 8 compact API 37, 48 executions;12 screenshots were inspected.
All 656 frozen inputs matched at acceptance; documentation preserves tested code.
Original keys/preferences/display configuration/reverse bindings, 28 legacy cache
files and five public beta.8 assets retain their verified hashes. Earlier failed
fixtures, compile attempts and the real API 26 tall-banner failure remain recorded;
the run needing a diagnostic dismiss is explicitly excluded from acceptance.
No owner phone or Pi was targeted for this development fix.

The prior room guard retains 229 policy passes on Python 3.11/3.13/3.14 and 229+15
hosted Python 3.12 raw acceptance. Exact browser `1a889d6` now has full raw native
1056 API 26/1057 API 37 acceptance. Portability `72444fd` API 37 passes 1057, but API 26
fails one Kanban cancel-control visibility check (1055 positives); investigate it.
Current exact-head hosted gates remain pending until their raw evidence is checked.

The earlier main `8422` observation also found three handler changes. Review fallback
extra-body scoping and structured plugin refusals before accepting variants.
Stable2bd remains unchanged. Do not waive canaries or rerun solely for green.
Privacy `d57` retains full raw 1043 API 26/1044 API 37 acceptance.

The unreleased read-only room browser has exact actor/profile/home checks,
ticketed reads, opaque paging, stale/unknown state and lifecycle fences. Nine
actual immutable standalone pairings prove unchanged chats/room rows and zero
admissions/workers. Standalone room storage can be installation-shared. The finite
fixture omitted background ASGI startup; full gateway lifecycle, owner-private
forwarding and merged paging remain open. See
[the room contract](docs/contracts/2026-11-08-hosted-rooms.md).

## Protected refs and scope

Main `6b809e6`, held release branch `207c6b4` and privacy branch `277c89b` stay fixed.
Do not merge/reset/force-push or advance them during unrelated audit work.
Published beta.8 excludes the unreleased canonical viewer and rooms. Original
keys/data and the on-device-only speech preference remain protected.

## One exact next action

Extend the exact-source owner probe with official atomic ownership seeding for
same-ID rooms in separate profile stores, foreign non-owner rows and opaque
actor-filtered paging. Hermes treats authenticated sign-ins as the machine owner;
do not invent separate dashboard-user islands. Drive the real authenticated
standalone-to-owner forwarding chain, shared/handed-root merge and owner-loss
paths with zero new admissions/workers and unchanged seeded chats/rooms. Preserve
the real post-close heartbeat cleanup finding and require complete exit evidence;
do not suppress it or disable its background service to claim full lifecycle.
Review exact main `3e82260` fallback/plugin changes and room-member close route,
inspect the existing `b583162` full CI handles without reruns, and continue every
remaining audit ledger row. No Pi deployment or model/default change is authorized
by this diagnostic checkpoint alone.
