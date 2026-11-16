# Signal Grove handover

## Planning update - 16 November 2026

The remaining scope now includes [OF-01 through OF-10](docs/ON_DEVICE_AI_BACKLOG.md)
for richer offline chat/history/context, performance, models/transfers, selected
documents, writing, opt-in personal context and supported offline voice/help.
All are planned. Preserve the pause; do not begin implementation without resume.
Existing UI failures and all earlier acceptance requirements stay open.

15 November 2026: **paused at the owner's request**, confirmed by the goal API.
Resume only when asked. Read [PROJECT_STATE](PROJECT_STATE.md),
[GOAL](docs/GOAL.md), the [full audit](docs/AUDIT_EXECUTION_PLAN.md) and
[completed/incomplete inventory](docs/audits/2026-11-15/PAUSED_REVIEW.md).

Reviewed SDK source62c8bbe is already on main; this handover changes docs only.
The unfinished route investigation is saved/pushed only on feat/on-device-ai
at a2a67b2ec55b04a9e1ede71e19b4d033487fd00e. Required JVM/lint/build pass,
API26 passes 16/16 and API37 fails one of 16: Cancel is not entirely above the
native keyboard. No proven fix, full release qualification or main merge is
claimed. [Feature checkpoint](https://github.com/DionCroft/Signal_Grove/blob/a2a67b2ec55b04a9e1ede71e19b4d033487fd00e/docs/contracts/2026-11-15-route-diagnostics-investigation-checkpoint.md).

Beta.9/code114 remains publicly downloadable as an experimental preview.
Fresh anonymous production APK/checksum downloads verify. Install over the
matching normal app without uninstalling. No newer stable APK is qualified.
The [paused review](docs/audits/2026-11-15/PAUSED_REVIEW.md) has the direct link.

After explicit resume, inspect the exact failed API37 assertion, original API26
receipts and feature changes before modifying code. Passing selected Demo cases
do not explain or fix the original failure. Do not blindly retry unknown writes.
Retain all four original priorities: UI diagnosis/full final-source gates;
supported Hermes semantic/transitive and original shutdown-warning review;
real project/workspace/worker/automation/integration acceptance; final
original-key production upgrade/security/device/release checks.

The corrected SDK still needs actual gateway-wire/unbound original-journal
drain, concurrent snapshots, unprofiled behavior and final PM/fleet/recovery
qualification. Live cutover, canonical PM/policy/launcher/grant changes,
private GitHub/live-home backup and restart are not authorized by isolated
staging approval. No such changes occur in this wrap-up.

Source and scratch stay on their verified owner/storage. Phone/models/credentials/
live Hermes and assets are unchanged. No final local build/instrumentation
remains active. Automatically triggered GitHub jobs may continue independently;
pending results must not be counted as acceptance.
