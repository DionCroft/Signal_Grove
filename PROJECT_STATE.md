# Signal Grove project state

19 November 2026. The full goal is **active**. Fix UI blockers first, then OF-01
through OF-10; commit/push tested chunks. The [goal](docs/GOAL.md),
[full audit](docs/AUDIT_EXECUTION_PLAN.md) and [ordered backlog](docs/ON_DEVICE_AI_BACKLOG.md)
retain every original requirement.

## Current implementation and evidence

The export component is on `feat/offline-markdown-export`, layered on
`feat/offline-chat-actions` at 88a8d05. The earlier history branch passed full Android run 44496075372 with
1,182 API 26 and 1,183 API 37 tests. Main stays at a992cf4.

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
(51/50 warnings). The fresh same-build APK pair passes 61 selected native methods on each
API 26 and API 37 without skips, including three new export flow checks.
Six real toggle inputs per API each have one accepted DOWN/UP, IME retained and
no retry. The exact Save title/pin/archive/model assertion remains unchanged.
Rows show recorded activity time and copied/edited/regenerated branch labels.
[Current source/APK bindings](docs/contracts/2026-11-19-phone-markdown-export.md) and
[retained UI failures](docs/contracts/2026-11-19-history-native-readiness.md).
Markdown review and file export work through the Android picker. The real API 26
Downloads transfer matches the source. The full hosted Markdown export
matrix passed. The physical picker and Android backup flows remain open.

Earlier phone pairings verify one actual offline native branch/Stop method and
real UI fork/edit/regenerate. The matching search build upgrades in place with
all UIDs, 16 metadata files and both models retained. Actual offline/unloaded
Search/Clear/garden read preserves all four chat files and both models. The normal
code112 app is untouched. The organization app8bda/test2df upgrade now verifies
all UIDs, 15 cold metadata files, both models and all four actual UI chats.

One four-case synthetic phone run stalls before first render and is stopped.
Two later bounded diagnostics confirm MIUI rejects the test-host start with
result102; one exact matching foreground intent unblocks a real identification
case, which previously failed its adjacent branch label check. The corrected
companion passes that identification case on the owner phone. Full Android CI
for the history branch passes, including the corrected pin/archive assertion.
SQLite main/WAL and
other data bytes match; transient SHM is separately reconciled with read-only
integrity ok. Radio/wake settings restore; reopening configured Tailscale once
restores a connected VPN and private Pi ping. No API/model readiness is inferred.
[Physical evidence and limits](docs/contracts/2026-11-19-phone-history-search-physical.md).


The branch feat/offline-history-backup layers the version 1 archive reader and
writer on the Markdown export branch. Its build passed 1,117 JVM tests, both
lint variants and 63 selected device checks on each API 26 and API 37.
The restore review passed 1,123 JVM tests and both lint variants. It checks
chat/label capacity, remaps collisions and resolves only included parents.
Recoverable publication passed 1,132 JVM tests, both lint variants and 65
focused checks on each API 26 and API 37. The protected backup build passed
1,139 JVM tests, both lint variants and 67 device checks per API. The private
staging and Tink 1.24.0 build passed 1,146 JVM tests, both lint variants and
69 device checks per API. Android picker wiring and physical backup/restore
remain open.
[Wire format and checks](docs/contracts/2026-11-19-phone-history-backup-archive.md).

## Exact next action

Verify the export branch through the physical Android document picker.
The physical label Save, Cancel and unloaded archive read are verified. Connect the Android backup and import
screens and picker callbacks to the tested private staging and restore workflow. The
full hosted Markdown export matrix passed 1,187 API 26 and 1,188 API 37
cases. Keep OF-01 accessibility and lifecycle, plus the original audit gates open.

Original remaining scope: upstream/shutdown, SDK/gateway/unbound drain/concurrency/
PM/fleet/recovery, real workspace/worker/files/NAS, automation/rooms/integrations/
configuration, resource/security and final original-key production upgrade/release.
Published beta.9 is unchanged. No Pi service/gateway/credential/model/default/
grant changes. Isolated staging does not authorize live cutover or canonical PM/
policy/launcher/private-backup/restart changes.
