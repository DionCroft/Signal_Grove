# Offline LLM feature backlog

Planning extension requested 16 November 2026 following the owner's report that
users enjoy running a local LLM on their phones. **Features below are planned or in progress; none is completed or newly released by this document.** The owner resumed the full audit goal on 18 November 2026. This extends the remaining work without replacing any original Hermes,
offline-model, device, security or release requirement.

[Existing implementation and limits](ON_DEVICE_AI.md) ·
[Original on-device acceptance](ON_DEVICE_AI_PLAN.md) ·
[Full audit](AUDIT_EXECUTION_PLAN.md) ·
[Paused inventory](audits/2026-11-15/PAUSED_REVIEW.md)

## Baseline and delivery order

Beta.9 already provides separate local text chat/history, text selection, Stop,
model loading/unloading, context selection, compatible GGUF import, a verified
recommended-model download/resume, a static guide and a constrained setup helper.
History currently has open/delete controls; local chat binds to the model hash.
These existing controls are not counted as newly delivered backlog features.

After the resumed UI checkpoint, finish the retained UI gates and preserve the
existing native/no-replay/upgrade gates. Then deliver one bounded feature at a
time in the order below. Each feature needs implementation, meaningful tests,
actual offline device evidence and documentation before its status changes.
This order is not a new release promise, version assignment or separate goal.

| Stage | Planned items | User outcome |
| --- | --- | --- |
| A: Everyday chat | OF-01, OF-02, OF-03, OF-04 | More useful daily chat, manageable history and understandable resource limits |
| B: Models and transfers | OF-05, OF-06 | Easier model choice, setup and reliable large-file management |
| C: Personal offline tools | OF-07, OF-08, OF-09, OF-10 | Selected-document questions, writing tools, optional personal context and accessible voice |

## OF-01: Edit, regenerate and branch conversations

**Status: in progress (reviewed actions, drafts, provenance and passive readers).**
[The current source-bound cohort](contracts/2026-11-18-phone-history-search-controls.md)
passes 1,071 JVM cases and 40 selected native methods on each API26/API37.
[One actual offline native method and separate real app UI branching](contracts/2026-11-18-offline-phone-branches.md)
verify the earlier phone pairing; final-source/accessibility/lifecycle gates stay open. Add edit-and-send, explicitly requested regeneration and
branch-from-message. Keep original exchanges accessible and record parentage,
model fingerprint and generation settings. Add readable Markdown/code blocks,
code copying, clear progress/completion-limit messages and usable long replies.
Text selection and Stop already exist; extend rather than duplicate them.

Acceptance: edits/regeneration produce a new branch without rewriting the
original; generation occurs once per explicit action; interrupted work is not
automatically retried. Streaming, Stop, process recovery, keyboard layout,
TalkBack and large fonts work. Model/template tokens remain outside rendered
answers and code/text cannot execute actions.

## OF-02: Searchable, organized and portable phone history

**Status: in progress (search and reviewed title/pin/archive controls implemented).**
[Current controls and native readiness](contracts/2026-11-19-history-native-readiness.md)
pass 1,093 JVM cases and 56 selected native methods on each API26/API37, including
the earlier 50 and six scripted input guards. Actual toggle inputs retain the
IME and use one DOWN/UP without retry; exact Save values remain asserted. [Earlier actual phone offline search](contracts/2026-11-19-phone-history-search-physical.md)
retains its own pairing. The updated companion passed physical branch
identification. Full hosted history coverage passed 1,182 API 26 and 1,183
API 37 cases. The [reviewed Markdown export](contracts/2026-11-19-phone-markdown-export.md)
saved exact content through the Android picker on API 26. The physical phone
saved labels and opened the archived reply with its model unloaded. Physical
Cancel preserved the saved labels. The [version 1 archive and restore review](contracts/2026-11-19-phone-history-backup-archive.md)
preserve selected transcript bytes and labels, map IDs and check capacity.
Recoverable restore publishes reviewed chats and labels together and resolves
interrupted publication before opening the catalog. The [protected backup format](contracts/2026-11-19-protected-phone-history-backups.md)
passed portable Unicode, wrong-passphrase, tampering and restore checks.
Private staging now freezes export documents, checks a single imported
copy and reclaims abandoned files when phone history opens. Android backup
and import screens now connect this staging to Android's document pickers.
The [verified backup UI](contracts/2026-11-19-phone-history-backup-ui.md) passes
72 focused Android methods on each API 26 and 37 and 1,146 JVM cases.
Physical Markdown transfer, protected backup save and offline collision
restore pass. The full branch matrix and release checks remain open.
Add local title/content search, rename, pin, archive and
filters by model/date. Add reviewed selected-chat Markdown export and versioned
backup/import with a protected encrypted-backup option. Explain export contents,
encryption and where Android will save/share them. Models remain separate.

Acceptance: history remains readable with weights unloaded and in Airplane mode.
Import validates schema, size, identifiers and model bindings; collisions are
reviewed/remapped and publication is atomic. Invalid input, cancellation,
wrong passwords and interrupted transfers preserve existing history. Exports
exclude credentials, server state and unrelated chats. Existing delete/open
behavior and older history migrate without data loss.

## OF-03: Context meter and controlled long conversations

**Status: in progress.** The meter and preflight count exact prompt tokens
using the loaded model's tokenizer and template. The composer shows capacity,
remaining output room and the reasoning/reply reservation. Overflow leaves the
draft and history intact. Users can copy an unsent draft into a new chat or
measure a branch with its reviewed earlier turns. **Earlier turns** can now
copy a selected subset and optional unsent draft into a separate chat. Backup
restore preserves its original turn references and links to the restored parent.
Reviewed summaries now support manual editing and optional on-device drafting
from the selected turns. The exact approved text is searchable, exportable
and backup-ready. Physical continuation and the full release gates remain open.
[Summary implementation and tests](contracts/2026-11-19-phone-reviewed-summaries.md).
[Selected-turn implementation and tests](contracts/2026-11-19-phone-selected-turns.md).
[Measured LFM3 boundary and test evidence](contracts/2026-11-19-phone-context-meter.md).

Acceptance: counts include applicable templates, special tokens and generation/
reasoning reservations; estimates are labeled where exact counts are unavailable.
No silent truncation, summary replacement or memory insertion. Summaries remain
editable derived text with source-turn references. Existing native/model context,
byte and turn limits stay enforced until separately qualified changes justify
them; overflow preserves the draft/history and offers a usable next step.

## OF-04: Performance presets and useful on-device diagnostics

**Status: in progress.** Balanced, Fast and Battery saver presets and advanced
context, thread and reply caps are available on the development branch.
Saved settings follow the installed model fingerprint and apply on the next
explicit load. The app shows active and next-load settings separately, the
load phase and its minimum free-memory check. Performance details show
the app's after-load RAM sample, generation timings and a fixed-prompt
offline benchmark. Native sampling follows the reviewed greedy policy.
Copy performance report copies only the active numerical settings, timing,
memory and result status. The clipboard action passed native checks on
Android 8 and 17. The physical phone passed one offline LFM3 benchmark
and released the model mappings in the background. An offline 2 GiB Android 8
emulator refused LFM3 before mapping it, then completed and saved a chat after
an explicit load of the smaller Qwen model. Both model hashes and the earlier
chat were preserved. Physical low-memory and sustained thermal/battery checks
remain open.
[Physical evidence](VALIDATION.md#physical-lfm3-benchmark-with-networking-off) ·
[Low-memory evidence](VALIDATION.md#offline-low-memory-refusal-and-smaller-model-recovery).

Acceptance: every preset maps to documented supported settings, respects device/
model limits and preserves responsiveness/Stop. Persist the settings used for
each reply. Avoid promising that a preset is faster or uses less energy until
measured on representative phones. Benchmarking is explicitly started, bounded,
offline and content-free in its shareable results; sustained thermal/battery and
low-memory tests remain release gates. GPU/NPU acceleration is not promised.

The debug context probe measured 4K, 8K and 16K on the Xiaomi with the
same LFM3 model. Near-window 8K and 16K generation did not finish within
180 seconds. The production context ceiling is still 4K. Sustained
thermal, battery and representative long-chat acceptance remain open.
[Context capacity evidence](VALIDATION.md#lfm3-context-capacity-probe-on-the-xiaomi).

## OF-05: A guided model library and first-run local setup

**Status: in progress.** The development branch packages an offline comparison
for the exact LFM3 QAD Q4_0 and Qwen2.5-0.5B Q4_K_M files. It shows the publisher,
licence, file size, load-memory threshold and physical evidence. Review shows the
pinned revision and hash before any download. Partials and discard are model-specific.
Installed models can be filtered by name, architecture or SHA-256. Phone
models shows installed bytes, current free storage and saved partial download
data. Load is explicit and appears near the model name. Licence consent and
actions stay visible beside the scrollable source text. A first-run guide
reads the library and routes explicitly through download or import, load and
phone chat. A saved, opt-in launch preference opens On-device AI from the
app icon. Explicit Hermes destinations keep their existing routes. Physical
acceptance remains open.
[Startup checks](VALIDATION.md#optional-on-device-ai-startup).
[Guide checks](VALIDATION.md#guided-offline-chat-setup).
[Catalogue checks](VALIDATION.md#offline-catalogue-and-separate-model-downloads) ·
[Library and licence checks](VALIDATION.md#installed-model-library-and-fixed-licence-controls).

Acceptance: catalogue browsing can use a packaged/cached version offline;
refreshes/downloads need deliberate connectivity and cannot load weights.
Recommendations label estimates and actual device evidence. Unsupported models
have an actionable reason; no assumption that every GGUF works. User imports
retain explicit compatibility/license checks. Existing chats retain their model
hash; choosing a different model starts a new chat or reviewed branch and never
silently changes the identity of old replies. Local-only setup needs no server
account, token or automatic cloud connection.

## OF-06: Better downloads, updates and lifecycle controls

**Status: in progress.** The foreground queue saves reviewed model pins in
order and shows exact bytes and phase. Start, pause, resume, and discard
are explicit. Earlier partials retain their data and require a current review.
[Queue checks](VALIDATION.md#reviewed-download-queue-and-explicit-resume).

The model download preference supports any network or the current
unmetered Android network. A restricted request stops if its network
changes or loses the capability. Saved bytes require an explicit resume.
[Network checks](VALIDATION.md#model-download-network-preference).

Reviewed items can opt into a foreground service with notification
progress and Pause. The transfer waits for Android to promote the
service and keeps the existing verification and network policy.
[Service checks](VALIDATION.md#opt-in-model-downloads-while-away-from-the-app).

The Android 17 system timeout closed the transfer and kept the same
reviewed item after relaunch. Revoking runtime notification permission
also closed the attempt. The real Qwen publisher transfer recovered
after process interruption and published its pinned SHA-256 after an
explicit resume. Additional publisher/network combinations, same-process
channel changes, physical interruption, handoff, low-space and reboot
verification remain open. User-selected GGUF replacements have an exact side-by-side
review with a storage measurement and a separate install action.
[Replacement checks](VALIDATION.md#reviewed-replacement-from-a-phone-model-file).
Fresh library startup reclaims abandoned manual import and replacement
copies. Android 8 and 17 startup tests verified the exact cleanup and
preserved the resume prefix and existing model bytes.
[Cold-start cleanup checks](VALIDATION.md#cold-start-cleanup-for-interrupted-manual-model-copies).
The publisher update review shows both pinned revisions and the
required extra storage. It preserves the earlier file, chats,
and exact reviewed download receipts. Bundled pins and their licence
assets stay immutable across future catalogue transitions. When
adding a reviewed replacement from the same publisher repository,
retain earlier pins in the revision history and use a distinct
content hash for the replacement.
[Publisher update checks](VALIDATION.md#publisher-catalogue-revision-updates).
A real upstream replacement is still pending. Both manifests
matched the packaged files by revision, size and SHA-256 on
20 November 2026.
[LiquidAI manifest](https://huggingface.co/api/models/LiquidAI/LFM3-1.7B-GGUF/tree/main) ·
[Qwen manifest](https://huggingface.co/api/models/Qwen/Qwen2.5-0.5B-Instruct-GGUF/tree/main).

Acceptance: downloading/backgrounding never starts inference. Cancel, sleep,
network loss, low space and process/reboot interruption reconcile exact receipts
and partial bytes before an explicit resume. Full hashes precede atomic install;
keep an older verified revision until the replacement is accepted. Bound resume
behavior and respect network/data preferences; do not silently redownload.
Existing foreground cancellation, unload and no-auto-replay behavior remains
until a separately tested lifecycle change supersedes it. No reboot auto-load
or background generation is introduced by this item.

## OF-07: Ask questions about selected local documents

**Status: in progress.** TXT and Markdown support includes an explicit
picker and index action, bounded scope and excerpt review, native local
answers with source labels and line ranges, and exact remove and re-index
controls. Android 15 and newer support selected PDFs with extractable text
and original page citations. Offline Android 17 checks verified the picker,
a password-protected refusal, image-only refusal, and an LFM3 answer from
page 3. Physical-phone acceptance remains open.
[Document checks](VALIDATION.md#offline-questions-about-pdf-pages).

Acceptance: extraction, retrieval and inference work with networking disabled.
Unsupported encrypted/scanned PDFs, oversize documents and malformed text produce
clear limits rather than fabricated results. Citation targets must resolve to
actual stored excerpts; model answers still carry uncertainty. No broad filesystem,
project/NAS access or hidden upload follows from choosing one file. Documents
cannot override app policy or grant tools/approvals. Remove private extracted
copies/indexes on explicit deletion; preserve the original selected document.

## OF-08: Reusable writing tools and selected-text actions

**Status: in progress.** The writing workspace provides editable English
and Chinese summary, rewrite, translation, brainstorming, and code presets,
custom templates, reviewed Android share/selected-text intake, explicit
phone-model generation, and private local result notes. Emulator acceptance
passed. Physical-phone and production acceptance remain open.
[Writing checks](VALIDATION.md#reviewed-offline-writing-tools).

Acceptance: users can inspect/edit exactly what text and template will be sent.
No background clipboard reading, automatic generation or automatic sending to
Hermes. Bound incoming text and handle unsupported attachments explicitly.
Templates work offline with English/Chinese coverage and preserve unsent drafts
on cancellation. Code is explained as text; this does not add shell execution.

## OF-09: Optional personal profiles, memory and temporary chats

**Status: in progress.** Foreground-only temporary chats have explicit
model choice and end confirmation. Their drafts and transcripts clear on
end or backgrounding. The temporary-chat emulator cases and real LFM3 trial passed.
Local profiles now support manual preferences and notes, per-request
selection, exact review and budget checks. An offline LFM3 trial verified
one selected note and one unselected preference. Profiles can now be
selected for a separate protected backup and reviewed before additive
restore. Physical-phone acceptance remains open.
[Profile backup checks](VALIDATION.md#separate-protected-personal-profile-backups).
[Personal profile checks](VALIDATION.md#personal-profiles-with-a-reviewed-phone-model-request).
[Temporary chat checks](VALIDATION.md#foreground-only-temporary-phone-chats).

Acceptance: memory starts off and never harvests other chats, credentials, files
or server profiles. Switching profiles does not leak another profile's context.
User instructions cannot grant remote authority. Memory inclusion is visible and
bounded by OF-03; missing/deleted entries cannot silently reappear. Temporary
chats do not enter persistent chat/search/backup/debug logs and are cleared on
exit/recovery; document normal Android/OS memory limits without claiming forensic
erasure. Existing persistent chats are unaffected.

## OF-10: Offline voice and a more helpful setup experience

**Status: in progress.** Phone chat offers offline-only dictation, editable
draft append and read-aloud for the current chat. Verify physical microphone,
Bluetooth, and interruptions. The local-first checklist shows stored model,
load and history state. It opens reviewed acquisition, storage, memory and
context recovery. Typed questions find reviewed topics without loading a
model or sending the question to inference. Verify the new topic's
grammar-constrained ranking and checklist UI on a physical phone.
[Offline voice checks](VALIDATION.md#offline-voice-in-phone-chat) ·
[Setup and search checks](VALIDATION.md#offline-checklist-and-setup-issue-search).

Acceptance: speech works offline only when a compatible offline recognizer/voice
is installed and positively verified; show unsupported reasons otherwise. Never
enable an online speech fallback without explicit user choice. Read-aloud stops
on leaving/Stop and releases audio resources; test interruptions and Bluetooth.
Bundled ASR models or a new speech runtime require a separate feasibility/security/
license decision and are not promised by this item. Helper suggestions retain
reviewed IDs, source/version/freshness and explicit redacted-observation consent;
freeform model commands never become actions or bypass the deterministic guide.

### Natural on-device read-aloud

**Status: Kokoro phone feasibility passed.** The offline Xiaomi trial held
LFM3 at 4,096 tokens while Kokoro generated English and Mandarin. See the
[trial results](VALIDATION.md#offline-kokoro-and-lfm3-audio-on-xiaomi).
**Kokoro v1.1 is the selected integration candidate.** Use the phone's
installed offline Android voice as the listening baseline. Verify the
licence and notices for the exact Kokoro voice pack, model, phonemizer
and runtime before distribution.
[Kokoro samples and Android build](https://k2-fsa.github.io/sherpa/onnx/tts/all/Chinese-English/kokoro-multi-lang-v1_1.html).

1. Audition identical short replies, longer explanations, dates, names and
   mixed English/Mandarin on the Xiaomi. Record cold and warm time to first
   audio, generation time relative to audio duration, peak memory, pack size
   and battery/thermal behaviour. Repeat during LFM3 generation at its 4,096-token
   limit. Playback should keep up with speech, with acceptable pauses and no
   stalled local chat. Check compatibility on the Android 8 and 17 targets.
2. Add an optional downloadable voice pack to Read aloud with voice preview,
   selection, speed and removal. Verify the download and reuse Signal Grove's
   existing read-aloud controls. Play completed sentences from the visible reply
   as they arrive. Keep automatic spoken replies off until the user opts in.
3. Verify playback in Airplane mode with Wi-Fi off and no active network after
   the pack is installed. Keep speech generation on-device for both Phone Chat
   and Hermes replies. Speak only the displayed answer text. Stop on request,
   screen exit or audio interruptions. Check Bluetooth, calls, screen-reader
   audio focus, language fallback and low-memory behaviour. Retain the installed
   offline Android voice as a fallback and preserve the user's online-voice
   preference. Dictation and Quick Capture transcription keep their own tests.

## Cross-cutting definition of done

- Preserve local/server identity and history separation, original signing/package
  identity, profile isolation, private storage and unknown remote receipts.
  New-chat routing is explicit; reconnection never replays remote/local work.
- Each change uses the structured English/Chinese/pinyin Words layer, explains
  disabled controls, preserves pixel themes/reduced motion, and supports keyboard,
  screen readers and large text without obscuring actionable controls.
- New local inference/documents/memory/diagnostics work without Hermes or internet
  once required files are installed. Network activity for transfers/catalogue/
  sharing is deliberate and distinguished from inference. No analytics or content
  uploads are implied.
- Review any new dependency/runtime/model license and advisory/compatibility
  impact. Record immutable model/template/runtime fingerprints, migrations and
  exact source/APK bindings. Keep weights, credentials and private content out of Git.
- Complete relevant JVM/native/emulator and representative physical-phone checks,
  including cancellation, low memory/storage, process recovery and no data loss.
  Exercise the actual feature. Retain evidence for failed gates and verify
  every case in the current 15-method external-model inventory and final
  production acceptance.
- Commit and push coherent tested chunks when resumed. Publish only through the
  existing final-source, original-key upgrade and signed-release process.
