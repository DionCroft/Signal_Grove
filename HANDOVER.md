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
Latest required builds pass 1,107 JVM cases/104 reports and zero-error lint
(51/50 warnings). The fresh same-build APK pair passes 58 selected native methods on each
API26/API37 without skips: the earlier 56 plus two real Android export-storage cases.
Six real toggle inputs per API each have one accepted DOWN/UP, IME retained and
no retry. The exact Save title/pin/archive/model assertion remains unchanged.
Rows show recorded activity time and copied/edited/regenerated branch labels.
[Current source/APK bindings](docs/contracts/2026-11-19-selected-markdown-foundation.md) and
[retained UI failures](docs/contracts/2026-11-19-history-native-readiness.md).
The internal frozen Markdown codec is implemented; user review/provider flow
and versioned/encrypted backup/import remain incomplete.

Earlier phone pairings verify one actual offline native branch/Stop method and
real UI fork/edit/regenerate. The matching search build upgrades in place with
all UIDs, 16 metadata files and both models retained. Actual offline/unloaded
Search/Clear/garden read preserves all four chat files and both models. The normal
code112 app is untouched. The organization app8bda/test2df upgrade now verifies
all UIDs, 15 cold metadata files, both models and all four actual UI chats.

One four-case synthetic phone run stalls before first render and is stopped.
Two later bounded diagnostics confirm MIUI rejects the test-host start with
result102; one exact matching foreground intent unblocks a real identification
case, which fails on an adjacent label not scrolled into view. No case pass is
claimed. The current companion fixes that scroll and waits for stable actual
IME geometry before native toggles. Full c46 CI also fails one API26 Save
assertion (pin false); the other three jobs succeed. Fresh full CI stays open. SQLite main/WAL and
other data bytes match; transient SHM is separately reconciled with read-only
integrity ok. Radio/wake settings restore; reopening configured Tailscale once
restores a connected VPN and private Pi ping. No API/model readiness is inferred.
[Physical evidence and limits](docs/contracts/2026-11-19-phone-history-search-physical.md).

## Exact next action

Verify the owner-installed companion960 against retained app8bda, then run the
single corrected identification case and finish reviewed organization/Cancel/
unloaded archive reads. Preserve the live full88a8d05 run before pushing the
tested Markdown component. Connect its selected-chat review, one-shot Android
file picker/writer and partial/recovery outcomes next, then versioned/encrypted
backup/import. OF-01 accessibility/lifecycle and the full original audit remain.

Original remaining scope: upstream/shutdown, SDK/gateway/unbound drain/concurrency/
PM/fleet/recovery, real workspace/worker/files/NAS, automation/rooms/integrations/
configuration, resource/security and final original-key production upgrade/release.
Published beta.9 is unchanged. No Pi service/gateway/credential/model/default/
grant changes. Isolated staging does not authorize live cutover or canonical PM/
policy/launcher/private-backup/restart changes.

## Device and continuation safeguards

Owner phone retains the consolidated organization app8bdaf53ebc3f and
companion2dfc9fc16f89. The new companion960fac43d1de changes only Android-test
sources; all application/build inputs match the installed subject. Verify that
subject and same original debug key before any companion-only update. Both
models, four actual UI chat JSONs and failed-run histories stay protected. The
owner's earlier label/pin/archive Save is now observed absent. USB is connected;
no debug process/model/test runs. Companion960 is staged but not verified
installed yet. Thirteen non-database metadata files and both models match;
database hashes remain separate. Never stage the newer foundation companion
against old app8bda: that pair has a new export API and must be installed together.
Normal networking was never changed by the new diagnostics.
Restore only observed original settings. Keep production untouched and never
install the hosted CI signer. Use owner navigation after verified USB taps fail.

Keep SQLite payload retention separate from volatile SHM. Record original VPN
state and verify it/private route after offline tests. For binary model staging
use raw ADB exec-in or verified sync; shell stdin can truncate at Ctrl+Z. Reconcile
partials and verify full size/hash before publication. Keep tools/evidence on E:.
The prior temporary Python harness path disappeared; a fresh E-only stdlib
harness is verified. That tool replacement starts no test by itself and changes
no APK. Read AGENTS.md before continuing.
