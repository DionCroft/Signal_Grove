# Signal Grove project state

18 November 2026. The full goal is **active**, explicitly resumed by the owner.
Fix UI blockers first, then OF-01 through OF-10, with tested commits/pushes.
The [full goal](docs/GOAL.md), [audit](docs/AUDIT_EXECUTION_PLAN.md) and
[offline backlog](docs/ON_DEVICE_AI_BACKLOG.md) retain every original requirement.

## OF-01 request provenance — 18 November 2026

New turns record request settings and native source identity; legacy replies
explicitly show unknown. Regeneration and recovery retain each turn's metadata.
1,045 JVM cases and zero-error lint pass, with 24 selected native UI/filesystem
methods on each API26/API37. Failed test-navigation/label attempts remain bound
to their original source. Formatting, accessibility and real-model/phone gates
remain before OF-01 is complete. [Evidence](docs/contracts/2026-11-18-local-request-provenance.md).

## OF-01 reviewed controls — 18 November 2026

Edit/send, regenerate and branch now require review and preserve the original.
Unsent drafts follow each phone chat and clear after durable publication.
1,036 JVM cases and zero-error lint pass; the same APK pair passes 21 selected
methods on each API26/API37 (synthetic UI and real private-file storage).
This is unreleased feature-branch work. Generation settings, passive formatting,
long replies and final native/phone/accessibility gates remain for OF-01.

[Exact evidence](docs/contracts/2026-11-18-local-chat-actions.md).

## OF-01 storage increment — 18 November 2026

Branch storage now preserves the exact parent and records child provenance,
with legacy read-only compatibility and no automatic replay. All 1,023 JVM
cases pass; lint has zero errors (51 debug/50 release warnings), and required
APKs build. This internal increment does not expose the actions or complete
OF-01. Next implement reviewed controls, one generation per action, scoped
drafts, reply provenance/formatting and device gates.

[Scope and evidence](docs/contracts/2026-11-18-local-branch-storage.md).

## Resumed UI checkpoint - 18 November 2026

The full goal is active at the owner's request. UI navigation now observes the
actual enabled Home action and requires Chat after one click. Phone-draft Cancel
uses fresh complete-control/native-caption geometry within the original bounded
readiness budget. Four new regressions include an actual late keyboard; no input
retry or submission/retention assertion is removed.

The same final APK pair passes 27/27 selected UI/history methods on each API26/
API37, plus 1,011 JVM cases/97 reports and zero-error lint (51 debug/50 release
warnings). Earlier failures, emulator framework/overlay faults and historical
cause uncertainty remain preserved; full standard/physical/release gates are
separate. OF-01 is next, followed by the remaining offline backlog. No new offline
feature or APK release is claimed by this UI checkpoint.

[Exact evidence and limits](docs/contracts/2026-11-18-ui-readiness-and-phone-draft-viewport.md).

## Next action

Consolidate the tested UI chunk through exact-head hosted standard CI, preserving
old failed/unknown evidence. Implement OF-01: edit-and-send, explicit regeneration,
branches, model/settings provenance and readable local replies without rewriting
the original chat or replaying unknown work.

Remaining: historical UI cause review/full final-source gates; upstream Hermes
semantic/transitive and original shutdown-warning review; corrected SDK gateway/
unbound drain/concurrency/PM/fleet/recovery; real workspace/worker/files/NAS,
automation/rooms/integrations/configuration; external-model/phone/lifecycle/
security and final original-key upgrade/release acceptance.

No new release or live Pi/model/credential/grant change occurs. The original
isolated Pi staging scope does not authorize live cutover or canonical PM/
policy/launcher/private-backup/restart changes. Current release evidence retains
its own original source and the owner's phone data is outside this UI stage.
