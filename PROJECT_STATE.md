# Signal Grove project state

OF-02 has a tested internal read-only search foundation: 1,064 JVM cases/102
reports, required builds and zero-error lint (51/50 warnings). Controller/UI,
organization, transfers/encryption and real offline UX remain. This independent
increment proceeds while OF-01 phone installation/testing waits; it does not
close or replace that gate. [Evidence](docs/contracts/2026-11-18-local-history-search-foundation.md).

18 November 2026. The full goal is **active**, explicitly resumed by the owner.
Fix UI blockers first, then OF-01 through OF-10; commit and push tested chunks.
The [goal](docs/GOAL.md), [full audit](docs/AUDIT_EXECUTION_PLAN.md) and
[offline backlog](docs/ON_DEVICE_AI_BACKLOG.md) retain every original requirement.

## Current source and validation

UI readiness/native-viewport fixes are on main at `a992cf4`. The reviewed head,
actual PR checkout and main merge have identical trees. Independent raw PR
evidence verifies 1,149 API26 / 1,150 API37 standard methods, no skips, 1,011 JVM
cases and zero-error hosted lint (52 debug/51 release warnings). Main's Android
and Windows runs pass. Original failures and cause uncertainty remain preserved.

Unreleased `feat/offline-chat-actions` now adds reviewed edit/send, regeneration
and branches, per-chat unsent drafts, parent/model/request provenance and a
passive Markdown/code/full-text reader. Original histories remain accessible;
legacy settings stay unknown. The same source-bound APK pair passes 36 focused
native methods on each API26/API37, 1,053 JVM cases/101 reports, required builds
and zero-error lint (51 debug/50 release warnings). Earlier reader/keyboard and
emulator System UI failures remain documented. These are synthetic UI and real
filesystem checks; physical model/UI/accessibility acceptance is still open.
[Exact behavior and evidence](docs/contracts/2026-11-18-local-reply-reader.md).

Main upstream run 44384185499 fails closed on missing tracked source modules.
Fresh official trees reproduce one missing unreviewed file at Hermes v0.23.2
and nine at current main. Review replacements before adjusting the checker;
prior semantic/transitive and shutdown findings remain. No compatibility pass
or live runtime cutover is claimed.

## Next action

Finish OF-01 actual-phone/native-model edit/regenerate/fork, cancellation,
retention and accessibility checks, preserving the normal app and existing
model/history/connection data. Then implement OF-02 in the backlog order.

Remaining original scope: upstream/shutdown review; corrected SDK gateway,
unbound drain/concurrency/PM/fleet/recovery; real project/workspace/worker/files/
NAS, automation/rooms/integrations/configuration; model/lifecycle/security and
final original-key upgrade/release acceptance. Each still needs its own evidence.

Published beta.9 is unchanged. The connected owner phone's normal app is code112;
no phone APK or data changes occur in the reader chunk. No Pi service, gateway,
credential, model/default or grant changes. Isolated Pi staging does not authorize
live cutover, canonical PM/policy/launcher/private-backup or restart changes.
