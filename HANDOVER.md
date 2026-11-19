# Signal Grove handover

19 November 2026. The full goal is **active**. Fix UI blockers first, then OF-01
through OF-10; commit/push tested chunks. The [goal](docs/GOAL.md),
[full audit](docs/AUDIT_EXECUTION_PLAN.md) and [ordered backlog](docs/ON_DEVICE_AI_BACKLOG.md)
retain every original requirement.

## Current implementation and evidence

Main `a992cf4` has the UI readiness/viewport fixes; identical-tree PR evidence
independently verifies 1,149 API26 / 1,150 API37 standard methods, no skips.
Main Android/Windows pass. Upstream scanning fails closed on moved/missing
tracked modules at recorded v0.23.2/main revisions; replacement/transitive
contracts and prior semantic/shutdown findings remain to review.

Unreleased `feat/offline-chat-actions` implements reviewed edit/regenerate/fork,
scoped drafts, request provenance, passive reply/code readers, text/model/date
search and now reviewed optional titles/pin/archive with Active/Archived/All
views. Labels live outside transcripts, stay out of prompts and preserve model/
parent identity. Cancellation after publication reconciles files; exact explicit
deletion cleans labels and corrupt indexes refuse deletion before data changes.
Latest required builds pass 1,093 JVM cases/103 reports and zero-error lint
(51/50 warnings). The same APK pair passes 50 selected native methods on each
API26/API37 without skips, including eight organization UI and two filesystem cases.
Rows show recorded activity time and copied/edited/regenerated branch labels.
[Exact source/APK bindings](docs/contracts/2026-11-19-phone-history-identification.md).

Earlier phone pairings verify one actual offline native branch/Stop method and
real UI fork/edit/regenerate. The matching search build upgrades in place with
all UIDs, 16 metadata files and both models retained. Actual offline/unloaded
Search/Clear/garden read preserves all four chat files and both models. The normal
code112 app is untouched. The newer organization app is not installed yet.

One four-case synthetic phone run stalls before first render and is stopped;
no method pass/retry is claimed. Startup needs diagnosis. SQLite main/WAL and
other data bytes match; transient SHM is separately reconciled with read-only
integrity ok. Radio/wake settings restore; reopening configured Tailscale once
restores a connected VPN and private Pi ping. No API/model readiness is inferred.
[Physical evidence and limits](docs/contracts/2026-11-19-phone-history-search-physical.md).

## Exact next action

Stage the consolidated original-key app/test pair for owner-phone upgrade and
organization checks. Preserve all data; diagnose the stalled instrumentation
startup before another native phone run. Verify label/Cancel/pin/archive/unloaded
reads, then continue OF-01 accessibility/lifecycle and OF-02 selected Markdown
export/versioned protected backup/import. New row identification is implemented
and covered by the current 50-method source-bound cohort.

Original remaining scope: upstream/shutdown, SDK/gateway/unbound drain/concurrency/
PM/fleet/recovery, real workspace/worker/files/NAS, automation/rooms/integrations/
configuration, resource/security and final original-key production upgrade/release.
Published beta.9 is unchanged. No Pi service/gateway/credential/model/default/
grant changes. Isolated staging does not authorize live cutover or canonical PM/
policy/launcher/private-backup/restart changes.

## Device and continuation safeguards

Owner phone retains the earlier search app d9a0fc372eba and test3594d52ad096;
new consolidated app 8bdaf53ebc3f/test2dfc9fc16f89 are tested only on emulators.
State/summary/callback ABI changed: install a matching app and companion together.
Both model copies, four actual UI chat JSONs and old failed-run QA histories stay
protected. Tailscale is the current phone foreground app after its saved VPN was
restored; no phone test is active. Restore only observed original settings. No
production app update, private-data reset or hosted CI-signed install is authorized
by these test fixtures. Use owner navigation after verified USB taps fail.

Keep SQLite payload retention separate from volatile SHM. Record original VPN
state and verify it/private route after offline tests. For binary model staging
use raw ADB exec-in or verified sync; shell stdin can truncate at Ctrl+Z. Reconcile
partials and verify full size/hash before publication. Keep tools/evidence on E:.
The prior temporary Python harness path disappeared; a fresh E-only stdlib
harness is verified. That tool replacement starts no test by itself and changes
no APK. Read AGENTS.md before continuing.
