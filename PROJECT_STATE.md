# Signal Grove project state

21 November 2026. The full goal is **active**. The owner approved the order:
phone context and request review, measured larger windows and Hermes-reported
context, then verified workspace attachment, project approvals and scheduling.
The [goal](docs/GOAL.md), [full audit](docs/AUDIT_EXECUTION_PLAN.md) and
[offline backlog](docs/ON_DEVICE_AI_BACKLOG.md) retain the original acceptance
requirements. Commit and push tested chunks.

## Android schedule preview checkpoint

The paused create review shows up to three local estimates, timezone
and UTC offset, and the missed-run policy. The review still requires an
explicit confirmation for one scoped paused create. The test passed at
both viewports on Android 8 and 17, together with 1,222 JVM cases and
zero-error lint. The preview requires a known zone for calendar
estimates. Existing schedule edit, run-once, authenticated output and
live acceptance remain open.
[Validation](docs/VALIDATION.md#calendar-and-missed-run-preview-in-the-schedule-review).

## Hermes scheduler profile and timezone checkpoint

Both exact upstream pins passed 112 scheduler cases with explicit UTC
for two Windows fixtures that assume UTC. Before that control each pin
had 110 passes and two filename reconciliation failures. The native
dashboard confirmed exact agent-job history and reproduced profile-hint
fallback and ownerless output rows. Two disposable profile stores per
pin passed integrity checks. Continue the local schedule forecast with
the verified timezone and missed-run policy, and require authenticated
server owner proof before displaying mixed output or enabling mutations.
[Validation](docs/VALIDATION.md#hermes-scheduler-and-scoped-run-history-on-stable-and-main).

## Hermes dashboard listener authentication checkpoint

The exact stable and main pins passed 129 common HTTP and WebSocket
authentication checks each on Windows. Main passed ten additional
companion-listener cases through the real in-process dashboard mount.
The hosted source scan published both reports, with review still
required. Live Pi TLS, browser/profile acceptance and unreviewed source
drift remain open.
[Validation](docs/VALIDATION.md#hermes-dashboard-listener-authentication-on-stable-and-main).

## Hermes main source inspection checkpoint

The exact main canary `242315c` can now read all ten relocated sources.
Their Git blobs match the earlier reviewed layout. The checker completes
with 212 changed used handler fingerprints and 29 changed persistence
sources requiring semantic review. It reports zero removals of used routes
or request field changes. The existing stable report and the prior
incomplete main run remain in the validation record.
[Validation](docs/VALIDATION.md#hermes-main-source-relocation-at-242315c).

## Integrated release candidate checkpoint

Source `6da9cd1` combines the phone context and history, scoped workspace
access, shutdown and unbound drain changes. The combined APKs passed 1,213
JVM checks and zero-error lint. Android 8 and 17 both read a registered
fixture through native Hermes authentication and a real Windows pipe;
the known heartbeat cleanup warning remains. The worker CI matrix passed.
The candidate Android matrix, upstream contract review, live Pi verification
and signed release gates remain open.
[Validation](docs/VALIDATION.md#integrated-audit-release-candidate).

## Phone history date picker checkpoint

The hosted Android 17 history test reported an undisplayed date option.
The four date presets now stay in the scrollable form with full-width
single-choice controls. Four history UI cases passed at standard and
compact sizes on Android 8 and 17, including the visible keyboard with
bilingual large text. The complete hosted matrix remains open.
[Validation](docs/VALIDATION.md#phone-history-inline-date-choices).

## Unbound gateway drain observation checkpoint

The drain actor checks the exact unbound count in a separate read-only
snapshot before accepting idle. Two isolated Windows trials with real
maintained SDK receipts reproduced the old status gap and proved both
settlement and expiry while the original fence stayed frozen. The full
portable policy suite completed with 923 passes and 273 platform skips.
The original-journal native socket scenario and all-profile drain remain
open. [Validation](docs/VALIDATION.md#unbound-work-during-the-original-gateway-drain-window).

## Native subagent recovery checkpoint

On Windows, both current Hermes candidate pins passed the original-owner
exit and next-process recovery, with their SQLite writer released during
stop or held until it returned. Hermes retained the original child
checkpoint, classified the held case as unknown and made no new model
calls. The previous mark was durable only in the released case. Both
Linux pins reproduced all four outcomes after the fixture preserved the
reaped owner's missing-file result. The all-profile drain and live Pi
gates remain open.
[Validation](docs/VALIDATION.md#native-recovery-of-an-in-flight-hermes-delegation).

## Phone helper consent checkpoint

The Android 8 helper case retained a hosted opt-in failure that did not
reproduce in the focused run. The repaired consent row accepts taps on its
label and sends only the previewed observation snapshot. Both focused cases
passed on Android 8 and 17, with zero build or lint errors. The complete
device matrix remains open.
[Validation](docs/VALIDATION.md#phone-helper-observation-consent).

## Authenticated Android owner and Windows worker checkpoint

Android 8 and 17 passed through the actual Hermes v0.23.1 owner dashboard,
prefixed native PKCE, refresh and a single followed socket. The same socket
read and previewed the disposable project through the real Windows pipe and
answered a subsequent heartbeat. The confirmed Room receipt and saved
credentials matched their pre-read values. Both Hermes stores passed SQLite
quick checks with zero admissions. The known heartbeat cleanup warning
remains open.

A read-only Pi check verified the active original maintained runtime and
the earlier v1 staging generation. A four-module source delta is verified
against that stage. Next, obtain scoped approval for a fresh Pi stage and
verify its ARM64 package and owner route before considering live activation.
[Evidence](docs/VALIDATION.md#authenticated-android-owner-and-native-windows-workspace).

## Android attached project browser checkpoint

The followed viewer now reviews the worker and registered root before listing
files. The client sends only the current session revision and generation and
the selected entry token. The frozen Android build passed 1,213 JVM tests,
both lint variants and both APK builds. The new reviewed-preview Compose
check passed on Android 8 and 17. The wire fixture verified that an older
gateway or a mismatched worker response leaves the original chat subscribed.
The Android owner-login interoperability and the live Pi-to-worker read are
still required before project-run approval and scheduling acceptance.
[Validation](docs/VALIDATION.md#android-attached-project-browser).

## Session-bound worker read checkpoint

The coordinator can derive one registered Windows workspace from a protected
association with a currently followed Hermes chat. The isolated v0.23.1
authority and native Windows pipe passed 10 new attachment checks in addition
to the prior 13. The two fixture paths were distinct. A concurrent workspace
move discarded the preview, and the obsolete lease was refused before the
next worker call. The source preserves the v1 worker read API.
Next, wire the followed Android viewer to the two attachment methods and
verify reviewed read-only browsing through the authenticated owner path.
[Validation](docs/VALIDATION.md#session-bound-worker-file-reads).

## Attached workspace and Windows read checkpoint

A disposable Hermes v0.23.1 session applied and read back a workspace move.
The native Windows broker prepared the same root, listed it and returned a
verified 26-byte preview. All 13 combined checks passed, along with the 18
original coordinator cases, 119 portable tests and 72 Windows-native tests.
The test preserved the chat row, subscription and project bytes and created
no turn or worker execution.

Hermes also refused a path missing on its host before any worker call.
Next, bind reads server-side to the verified session's current attachment and
an explicit worker registration. The worker's root can live on another host. Then verify the authenticated Android-to-Pi-to-worker
path before adding project approvals. Production enrollment and Pi activation
still require their own acceptance.
[Isolated evidence](docs/VALIDATION.md#attached-workspace-and-windows-worker-read-checkpoint).

## Hermes context read checkpoint

The followed viewer can read and refresh Hermes-reported context and
compression over a separate authenticated socket. Both new native Android
cases passed on API 26 and 37. Four broader workspace tap failures remain
retained. The same cases passed after isolation. Eight upstream authority
checks passed across v0.23.1, v0.23.2, v0.23.3 and the reviewed main pin.
Continue with verified read-only workspace attachment and bound worker
files before the project approval inbox.
[Context read and workspace cohort evidence](docs/VALIDATION.md#hermes-reported-context-and-compression).

## Phone context review

The request review reports exact formatted tokens against the active input
budget and gives the pre-format byte sizes for each included part. Earlier turns
opens the existing continuation review with the unsent draft. Larger windows
remain a separate experiment with pinned LFM3 on the Xiaomi.
[Context guide](docs/ON_DEVICE_AI.md#check-how-much-context-your-phone-model-has).

## Physical LFM3 context capacity checkpoint

The debug-only 4K, 8K and 16K trials captured short reply latency,
memory, overflow refusal and Stop on the Xiaomi. The near-window 4K
reply completed. The near-window 8K and 16K runs hit the 180-second
deadline. The phone passed the exact 512-token boundary that timed out at
generation on the x86_64 emulator. The production 4K cap remains the
qualified limit. Continue the separate Hermes-reported context read using
the existing authenticated session scope and the pinned session.stats
contract; workspace approval and scheduling acceptance remain open.
[Physical and emulator results](docs/VALIDATION.md#lfm3-context-capacity-probe-on-the-xiaomi).

## Beta.10 release checkpoint

Exact candidate d651a33 passed 1,203 JVM tests, both lint variants and
13 standard and seven compact checks on each of Android 8 and 17.
The original-key APK upgraded in place on the owner's phone. Its existing
Hermes conversations and project metadata loaded, and a real offline
LFM3 reply persisted. An Android 8 upgrade path from the published
beta.9 APK retained a local task. The hosted suite passed 1,241 Android 8
and 1,242 Android 17 tests. Beta.10 is published and the Hermes audit
stays active. Quick Capture is queued separately.

[Signed upgrade and test evidence](docs/VALIDATION.md#beta10-offline-routing-and-signed-upgrade-checkpoint) ·
[Hosted Android run](https://github.com/DionCroft/Signal_Grove/actions/runs/44836131279).

## Compact phone chat menu checkpoint

The previous build reproduced the Android 8 Temporary chat visibility
failure on a compact viewport after drafting. Chat options puts both
Temporary chat and Personal profiles in the existing toolbar. The
same viewport passed with the candidate, along with its focused
Android 17 retest. The initial Android 17 save and Voice transcript
failures remain in the evidence. The full hosted matrix and physical
production gates are still open.
[Menu checks](docs/VALIDATION.md#compact-phone-chat-action-menu).

## Exact rebuild and hosted UI checkpoint

The app rebuild at `a97c462` verified the updated notice, packaged
native alignment and exact-APK offline generation and model switching
on Android 8 and 17. The earlier hosted Android 8 shard failed two
visibility assertions. The local focused and 13-case runs passed, so
the hosted discrepancy remains open. Continue with the hosted matrix,
physical ARM64 and signed release gates.
[Build and Android evidence](docs/VALIDATION.md#exact-runtime-packaging-and-hosted-android-follow-up).

## Native dependency release checkpoint

Development pins the verified b12556 llama.cpp fix for CVE-2026-133710.
The runtime-pin APK passed the Android 8 and 17 offline model checks.
The published beta.9 still uses b12255. Physical ARM64 and final
signed upgrade remain open.
[Runtime-pin evidence](docs/VALIDATION.md#verified-llamacpp-b12556-development-runtime).

## Cold-start model stage cleanup

The app reclaims incomplete manual import and replacement stages when
the private model library opens after a process restart. The normal
startup test passed on Android 8 and 17 and verified retained download
prefixes and existing model bytes. The exact build passed 1,203 JVM
tests and both lint variants with zero errors. Physical and production
checks remain open.
[Cleanup evidence](docs/VALIDATION.md#cold-start-cleanup-for-interrupted-manual-model-copies).

## Phone model library durability checkpoint

Model install, settings and deletion sync directory entries before returning.
The build passed 1,202 JVM tests and both lint variants with zero errors.
Verified LFM3 and Qwen inputs passed five offline Android cases across
both owned emulators. The older hosted Android 8 shard holds a timeout in
the phone startup test. The exact build then passed a normal Android 8 reboot
with the Qwen bytes and both chat turn states retained. Physical-phone,
the older Android 17 guest observation, full hosted and production checks
remain open.
[Model evidence](docs/VALIDATION.md#phone-model-library-durability) ·
[Reboot evidence](docs/VALIDATION.md#offline-qwen-history-after-a-normal-android-8-reboot).

## Phone dialogs and project cancellation checkpoint - 20 November 2026

The project cancellation fixture now targets the reachable dialog footer.
The Voice microphone stays in the phone chat toolbar and waits for speech
preferences. The chat keyboard releases focus before the three private
dialogs open. The exact build
passed 1,200 JVM tests, both lint variants with zero errors and 40 focused
methods on each of Android 8 and 17. Full hosted, live Hermes and
production-release acceptance remain open.
[Validation evidence](docs/VALIDATION.md#project-cancellation-and-phone-dialogs-with-the-keyboard).

## Current Hermes heartbeat shutdown checkpoint

The exact v0.23.3 and main pins reproduced the late database reopen.
A shared two-file candidate stops heartbeat admission at teardown and
cleans up register-once and periodic rows on exit. Windows native stop,
standalone exit, live SQLite close-gate and released-contention checks
passed. Hosted Linux passed the earlier canonical and lifecycle cases.
The candidate now also returns from a full Windows stop with the
heartbeat transaction still held and leaves SQLite open. Hosted Linux reproduced that outcome at both reviewed pins. The subagent
shutdown ledger times out under the same contention. Real subagent recovery
and live Pi checks remain open.
[Candidate and evidence](docs/contracts/2026-11-19-hermes-public-shutdown-review.md#full-hermes-stop-with-the-heartbeat-transaction-still-open).

## Latest offline setup checkpoint

The offline checklist shows model, storage and history state. Typed topic
search and deterministic recovery check cards work with the model unloaded
and networking off. Exact JVM and focused API 26/37 checks passed. Physical
ranking of the new recovery topic, full hosted and release gates remain open.
[Setup evidence](docs/VALIDATION.md#offline-checklist-and-setup-issue-search).

## Latest offline phone voice checkpoint

Phone chat has an offline speech panel with reviewed draft append and
read-aloud for completed replies. Device coverage includes the phone
voice path and shared Hermes Voice screen controls. Physical speech
and Bluetooth remain open, along with release checks.
[Voice evidence](docs/VALIDATION.md#offline-voice-in-phone-chat).

## Latest personal profile backup checkpoint

Each profile backup includes explicitly selected profiles and uses a
passphrase. The actual Android 17 document picker saved and restored a
profile offline. Fixture tests verified additive conflict handling
and preserved existing history. Physical, hosted and release checks
remain open.
[Profile backup evidence](docs/VALIDATION.md#separate-protected-personal-profile-backups).

## Latest personal profile checkpoint

On-device Chat supports local profiles, writing preferences and
explicit selection of individual notes for a single reviewed request.
An offline LFM3 trial used one chosen note and preserved the prior
history. Profile backup selection, physical testing, and the original
Hermes and release gates remain open.
[Personal profile evidence](docs/VALIDATION.md#personal-profiles-with-a-reviewed-phone-model-request).

## Latest project approval and search checkpoint

Project review keeps the confirmation controls together, and phone history
shows the selected date before Apply. Both Android versions passed 138
focused checks, including summary drafting and image-share review.
Full hosted, real Hermes and signed-release acceptance remain open.
[Android UI evidence](docs/VALIDATION.md#project-approval-footer-and-phone-history-filter-menus).

## Latest temporary phone chat checkpoint

Temporary chat keeps an independent foreground transcript. Ending or
backgrounding the chat clears it. The native request releases its
context while keeping the model loaded. Android 17 verified two LFM3
turns offline and an empty reopen. Personal profiles, reviewed memory,
backup controls and physical-phone acceptance remain open.
[Temporary chat evidence](docs/VALIDATION.md#foreground-only-temporary-phone-chats).

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
