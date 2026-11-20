# Signal Grove project state

20 November 2026. The full goal is **active**. Fix UI blockers first, then OF-01
through OF-10; commit/push tested chunks. The [goal](docs/GOAL.md),
[full audit](docs/AUDIT_EXECUTION_PLAN.md) and [ordered backlog](docs/ON_DEVICE_AI_BACKLOG.md)
retain every original requirement.

## Latest offline writing checkpoint

Writing tools provide editable English and Chinese presets, custom
templates, reviewed Android text intake, exact model and prompt review,
and local result notes. LFM3 generated offline and the note reopened
after relaunch on Android 17. Physical-phone acceptance remains open.
[Writing evidence](docs/VALIDATION.md#reviewed-offline-writing-tools).

## Latest selected documents checkpoint

Signal Grove can ask the phone model about reviewed TXT and Markdown
excerpts and about selected text PDFs on Android 15 and newer. Android 17
verified an offline LFM3 answer with an original page citation. The strict
build passed 1,179 JVM tests and 106 focused device tests per emulator API.
Physical-phone and signed-release acceptance remain open.
[Document evidence](docs/VALIDATION.md#offline-questions-about-pdf-pages).

## Latest publisher model catalogue checkpoint

Signal Grove can review a newer bundled publisher revision while
keeping earlier model files, chat bindings and exact queue receipts.
The exact APK pair passed 1,173 JVM tests and 103 focused tests on
each emulator API. The current publisher revisions already match the
packaged pins. A live Qwen download recovered its exact prefix after
process stop and verified the full publisher hash after explicit
resume. A real successor, physical device and release gates remain
open.
[Catalogue evidence](docs/VALIDATION.md#publisher-catalogue-revision-updates).

## Latest phone model replacement checkpoint

The development app can stage a user-selected GGUF and review both
hashes and required space. Installation adds the candidate beside the
previous model. An Android 17 offline normal-app picker trial tested
cancel and exact publication alongside a verified LFM3 file. All
1,170 JVM and 102 focused native tests per API passed with zero lint
errors. Catalogue replacement and physical lifecycle checks remain
open. The original Hermes and release gates remain active.
[Replacement evidence](docs/VALIDATION.md#reviewed-replacement-from-a-phone-model-file).

## Latest background model transfer checkpoint

A reviewed model can download with a foreground notification after
an explicit tap. The service keeps the existing network preference
and saved partials. The debug pair passed 1,167 JVM tests and 100
focused tests per API with zero lint errors. The Android 17 system
timeout closed the socket and retained the exact queue item without a
retry. Continue OF-06 with reviewed updates, a notification-channel
trial and physical and publisher-transfer checks.
Hermes integration and final-source release gates remain active.
[Service evidence](docs/VALIDATION.md#opt-in-model-downloads-while-away-from-the-app).

## Latest model download network checkpoint

The download queue offers a saved unmetered-only option. The bound
request stops when the selected network changes and keeps its saved
bytes for an explicit resume. The debug pair passed 1,165 JVM and
99 focused methods per emulator API with zero lint errors. Continue
OF-06 with supported foreground transfers and reviewed model
updates while retaining physical, Hermes and final-source release gates.
[Network evidence](docs/VALIDATION.md#model-download-network-preference).

## Latest model download checkpoint

The development app has a private ordered queue for reviewed catalogue
files, exact saved bytes and phase, and explicit pause, resume and
discard. The debug pair passed 1,162 JVM and 96 focused methods per
emulator API with zero lint errors. Continue OF-06 with network
preferences, supported foreground transfers and reviewed model
updates. Physical and final-source release gates remain open.
[Queue evidence](docs/VALIDATION.md#reviewed-download-queue-and-explicit-resume).

## Latest offline startup checkpoint

The development app can open On-device AI from the app icon using
the switch in Set up offline chat. The debug pair passed 1,158 JVM
tests and 94 focused methods per emulator API with zero lint errors,
including launcher, draft retention, Hermes return and explicit
destination coverage. Continue with physical acceptance, OF-06
model transfers and the original Hermes and signed-release gates.
[Startup evidence](docs/VALIDATION.md#optional-on-device-ai-startup).

## Latest guided setup checkpoint

The development app now guides users from phone model download or
import through explicit load and first chat. It shows existing model
and chat state. The signed debug pair passed 1,158 JVM tests and 89
focused methods per emulator API with zero lint errors. Continue
with the saved local startup preference, hosted and physical checks,
and all remote integration and signed-release gates.
[Guide evidence](docs/VALIDATION.md#guided-offline-chat-setup).

## Latest model library checkpoint

The development app filters installed models by name, architecture or
SHA-256, shows stored weights, saved download data and sampled free
storage, and surfaces Load beside the model name. The licence scrolls
independently of the consent footer. The debug pair passed 1,158 JVM
and 87 focused checks per emulator API with zero lint errors. Continue
with guided local setup and the startup preference. Physical,
integration and signed-release gates remain active.
[Library evidence](docs/VALIDATION.md#installed-model-library-and-fixed-licence-controls).

## Latest model catalogue checkpoint

The development branch offers an offline comparison of the exact LFM3
and smaller Qwen files. Each download names its source, licence, hash and
revision and requires separate confirmation. Partials and discard are
scoped by model hash. The debug pair passes 1,158 JVM and 85 focused
methods per Android emulator API with zero lint errors. Finish installed
search and storage visibility, guided local setup and the startup
preference. Physical resource and host/release gates remain active.
[Catalogue evidence](docs/VALIDATION.md#offline-catalogue-and-separate-model-downloads).

## Latest performance checkpoint

The development branch `feat/offline-history-backup` now includes model
performance presets and measured app memory, first visible text and
sampled throughput. The explicit LFM3 benchmark passed offline through
the Android 17 app. All four fixture chats matched their original hashes.
The exact debug pair passes 1,157 JVM and 83 focused methods on each
emulator API with zero lint errors. The physical Android 17 phone ran
the fixed LFM3 prompt with an independently verified disconnected state,
then recovered the private route to Hermes. All six chats, models and
labels retained their hashes. The copyable performance report passes
all 84 focused checks per emulator API, including a real clipboard test.
An offline 2 GiB Android 8 emulator refused LFM3, then loaded the smaller
Qwen model on request and saved its reply. The earlier chat and both
model hashes were preserved.
[Low-memory evidence](docs/VALIDATION.md#offline-low-memory-refusal-and-smaller-model-recovery).
[Report checks](docs/VALIDATION.md#copy-on-device-performance-details).
[Physical measurements](docs/VALIDATION.md#physical-lfm3-benchmark-with-networking-off).
[Measurements and build evidence](docs/VALIDATION.md#on-device-performance-measurements-and-offline-benchmark).

## Current implementation and evidence

The export component is on `feat/offline-markdown-export`, layered on
`feat/offline-chat-actions` at 88a8d05. The earlier history branch passed full Android run 44496075372 with
1,182 API 26 and 1,183 API 37 tests. Main stays at a992cf4.

Main `a992cf4` has the UI readiness/viewport fixes; identical-tree PR evidence
independently verifies 1,149 API26 / 1,150 API37 standard methods, no skips.
Main Android and Windows pass. The feature branch watcher follows the
reviewed cron move and reports 177 stable v0.23.2 fingerprint changes.
Main upstream still requires a review of eight replaced storage modules.
Paired upstream tests pass all 163 selected main cases and 106 of 107
stable cases. The stable inventory assertion fails after a read-only API
history fixture and passes by itself. Continue mapping the persistence
authority and mutation interfaces before updating watcher paths. A reviewed
patch cleared both isolated native Windows heartbeat modes on v0.23.2,
and all 50 selected upstream cases passed. Main requires a separate port.
Stable Linux, contention and owner runtime checks remain open.
[Shutdown evidence](docs/contracts/2026-11-19-hermes-public-shutdown-review.md#verified-v0232-shutdown-candidate).
[Paired test evidence](docs/contracts/2026-11-19-hermes-main-stored-history.md#paired-gateway-and-sqlite-tests). [Source evidence](docs/contracts/2026-11-19-hermes-cron-source-relocation.md).

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
matrix passed. Physical Markdown export, protected save and offline restore
with ID remapping are verified on the phone.

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
69 device checks per API. The latest backup screens and picker wiring pass
1,146 JVM tests, both lint variants and 72 focused Android checks per API.
[Exact app and companion evidence](docs/contracts/2026-11-19-phone-history-backup-ui.md).
[Wire format and checks](docs/contracts/2026-11-19-phone-history-backup-archive.md).

The context meter and overflow admission check pass 1,148 JVM tests, both
lint variants and 73 focused checks per emulator API. The pinned LFM3 boundary
passes on the owned Android 17 emulator.
[Context meter and limits](docs/contracts/2026-11-19-phone-context-meter.md).

Selected-turn continuation passes 1,150 JVM cases, both lint variants and
76 focused checks per Android emulator API, including a backup restore with
chat ID collisions. Source bytes and unsent drafts are preserved.
[Selected-turn evidence](docs/contracts/2026-11-19-phone-selected-turns.md).

Reviewed phone summaries pass 1,152 JVM tests, both lint variants and 81
focused checks on each supported emulator API. Approval saves the edited
summary and source references. The original chat stays readable, and the
new chat is searchable and exportable before its first new question.
[Summary evidence](docs/contracts/2026-11-19-phone-reviewed-summaries.md).

## Exact next action

The hosted Android 17 shard at f46452b ran 572 tests with one search
fixture failure before its keyboard bounds assertions. The corrected
fixture waits for a coherent native keyboard viewport and retains the
original assertions. The frozen app and test pair passed all 84
focused checks on each local Android 8 and Android 17 emulator.
The original bilingual search keyboard bounds passed in both hosted
device logs. The full matrix exposed a separate history model-picker
failure on Android 8 and a Kanban keyboard timeout on Android 17.
Resolve both cases and verify the subsequent hosted matrix.
[Exact hosted run](https://github.com/DionCroft/Signal_Grove/actions/runs/44617490182).
The prior run at 1590452 stopped before device jobs when one native
backup child-identity fixture errored. Retain the failed run and resolve
that case independently of the keyboard fixture.
[Policy failure evidence](docs/VALIDATION.md#hosted-native-backup-child-observation-failure).
[Search fixture evidence](docs/VALIDATION.md#native-keyboard-viewport-in-phone-history-search).

The complete hosted Android run 44560196101 passed at 3d2b24a. Verified
raw partitions and APKs cover 1,203 Android 8 and 1,204 Android 17
standard methods, including both corrected fixtures. The earlier policy
failure remains recorded. Verify the independently queued
[reviewed-summary run](https://github.com/DionCroft/Signal_Grove/actions/runs/44573654477). The physical protected restore and Markdown export are verified. The physical
label Save, Cancel and unloaded archive read are verified. The full hosted
Markdown export matrix passed 1,187 API 26 and 1,188 API 37
cases. On the later backup branch, one hosted Android 8 shard failed before
Home was ready. The other found Review backup disabled after fixture input.
The current Home and protected-backup fixtures pass 14 focused checks on
both APIs. Both historical hosted failures remain in the evidence.
Physical manual reviewed-summary creation and selected-turn provenance are
verified, with no model loaded. Confirm the separate copy-only selected-turn
action and model-generated draft on the physical phone.
[Evidence](docs/contracts/2026-11-19-phone-reviewed-summaries.md#physical-reviewed-summary-with-the-model-unloaded).
The short physical offline benchmark and model mapping release passed.
Complete physical Stop, sustained thermal/battery and low-memory
checks. Keep OF-01 accessibility,
lifecycle and the original audit gates open.

Original remaining scope: upstream/shutdown, SDK/gateway/unbound drain/concurrency/
PM/fleet/recovery, real workspace/worker/files/NAS, automation/rooms/integrations/
configuration, resource/security and final original-key production upgrade/release.
Published beta.9 is unchanged. No Pi service/gateway/credential/model/default/
grant changes. Isolated staging does not authorize live cutover or canonical PM/
policy/launcher/private-backup/restart changes.
