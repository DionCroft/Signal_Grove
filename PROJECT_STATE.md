# Signal Grove project state

18 November 2026. The full goal is **active**, explicitly resumed by the owner.
Fix UI blockers first, then OF-01 through OF-10; commit and push tested chunks.
The [goal](docs/GOAL.md), [full audit](docs/AUDIT_EXECUTION_PLAN.md) and
[offline backlog](docs/ON_DEVICE_AI_BACKLOG.md) retain every original requirement.

## Current source and validation

UI readiness/native-viewport fixes are on main at `a992cf4`. Independently
verified identical-tree PR coverage has 1,149 API26 / 1,150 API37 standard device
methods, no skips. Main Android and Windows pass. Upstream scan fails closed on
missing tracked modules at v0.23.2/current main; moved/transitive contracts need
review before changing its guard. Prior semantic/shutdown findings stay open.

Unreleased `feat/offline-chat-actions` adds reviewed edit/send, regeneration,
branches, scoped drafts, request provenance, passive reply/code readers and now
local history text/model/date search. Required builds, 1,071 JVM cases/102 reports
and zero-error lint (51/50 warnings) pass. Exactly 40 selected native methods pass
on each API26/API37 with the same source-bound APKs and no skips. Search preserves
the complete inventory/current chat and never loads weights or changes files.
[Search behavior, bindings and limits](docs/contracts/2026-11-18-phone-history-search-controls.md).

One actual offline native phone method verifies real edit/regenerate/fork/Stop,
five QA histories and no replay on unloaded reads/reopened-store recovery. The
first stale-parent fixture refusal stays preserved. Separately, real app UI
fork/edit/regenerate each creates one child and preserves all earlier JSON bytes.
Edit/regenerate records the owner's 4,096-context/four-thread/384-output choice.
These phone checks use the earlier explicitly paired app/companion, not the new
search APKs. [Physical proof](docs/contracts/2026-11-18-offline-phone-branches.md).

## Next action

Finish unloaded real UI request-settings retention; install the matching tested
search app/companion pair with exact data/model/package preflight and verify
physical offline search. Preserve original history, normal app and connection.
Continue remaining OF-01 accessibility/lifecycle gates and OF-02 organization,
Markdown export and protected backup/import in bounded tested chunks.

Remaining original scope: upstream/shutdown; corrected SDK gateway/unbound drain/
concurrency/PM/fleet/recovery; real project/workspace/worker/files/NAS, automation/
rooms/integrations/configuration; model/resource/security and final original-key
upgrade/release. Each requires its own evidence; no aggregate completion claim.

Published beta.9 is unchanged. The normal owner app remains code112, untouched;
the earlier debug package was initially absent, so its first installation is not
an upgrade-retention proof. No Pi service/gateway/credential/model/default/grant
changes. Isolated staging does not authorize live cutover, canonical PM/policy/
launcher/private backup or restart changes.
