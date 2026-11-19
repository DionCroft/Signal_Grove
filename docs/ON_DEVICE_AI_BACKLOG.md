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
[Current controls and branch/time identification](contracts/2026-11-19-phone-history-identification.md)
pass 1,093 JVM cases and 50 selected native methods on each API26/API37, including
eight organization UI/two Android filesystem cases. [Earlier actual phone offline search](contracts/2026-11-19-phone-history-search-physical.md)
retains its own pairing; current physical organization and the stalled native
startup diagnosis remain open. Branch/date identification is implemented; the consolidated phone check,
Markdown export and protected backup/import remain. Add local title/content search, rename, pin, archive and
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

**Status: planned.** Show actual tokenizer/template-aware context use, remaining
answer budget and an understandable full-context warning. Offer a new branch,
explicit selection of earlier turns, or a reviewed local summary to continue.
Retain the original full conversation and identify exactly what context is used.

Acceptance: counts include applicable templates, special tokens and generation/
reasoning reservations; estimates are labeled where exact counts are unavailable.
No silent truncation, summary replacement or memory insertion. Summaries remain
editable derived text with source-turn references. Existing native/model context,
byte and turn limits stay enforced until separately qualified changes justify
them; overflow preserves the draft/history and offers a usable next step.

## OF-04: Performance presets and useful on-device diagnostics

**Status: planned.** Add Balanced, Fast and Battery saver presets plus optional
advanced controls for supported sampling, output limits, context and threads.
Remember preferences per model fingerprint; show loaded model, load/progress
state, approximate memory and optional time-to-first-answer/token-rate statistics.

Acceptance: every preset maps to documented supported settings, respects device/
model limits and preserves responsiveness/Stop. Persist the settings used for
each reply. Avoid promising that a preset is faster or uses less energy until
measured on representative phones. Benchmarking is explicitly started, bounded,
offline and content-free in its shareable results; sustained thermal/battery and
low-memory tests remain release gates. GPU/NPU acceleration is not promised.

## OF-05: A guided model library and first-run local setup

**Status: planned.** Extend the single recommended download into a curated
compatible-model catalogue with publisher/source, license, exact file revision,
hash/size, quantization, estimated resource needs and clear tested-versus-untested
status. Add model comparison, searchable installed models, storage visibility,
quick explicit selection and a first-run download/import/load/first-chat checklist.
Offer a saved preference to open directly into local AI without configuring Hermes.

Acceptance: catalogue browsing can use a packaged/cached version offline;
refreshes/downloads need deliberate connectivity and cannot load weights.
Recommendations label estimates and actual device evidence. Unsupported models
have an actionable reason; no assumption that every GGUF works. User imports
retain explicit compatibility/license checks. Existing chats retain their model
hash; choosing a different model starts a new chat or reviewed branch and never
silently changes the identity of old replies. Local-only setup needs no server
account, token or automatic cloud connection.

## OF-06: Better downloads, updates and lifecycle controls

**Status: planned.** Add a transfer queue, clear byte/progress/storage reporting,
pause/resume/discard, network preferences and an explicit opt-in for supported
foreground-service downloads with visible progress/Cancel. Offer reviewed model
updates that identify the new revision and extra storage needed.

Acceptance: downloading/backgrounding never starts inference. Cancel, sleep,
network loss, low space and process/reboot interruption reconcile exact receipts
and partial bytes before an explicit resume. Full hashes precede atomic install;
keep an older verified revision until the replacement is accepted. Bound resume
behavior and respect network/data preferences; do not silently redownload.
Existing foreground cancellation, unload and no-auto-replay behavior remains
until a separately tested lifecycle change supersedes it. No reboot auto-load
or background generation is introduced by this item.

## OF-07: Ask questions about selected local documents

**Status: planned.** Add user-selected TXT/Markdown and supported text PDFs through
Android's picker. Extract/index a bounded private local collection; answer using
selected excerpts with filename/page/section citations and visible source text.
Provide remove/re-index controls and show the active document scope.

Acceptance: extraction, retrieval and inference work with networking disabled.
Unsupported encrypted/scanned PDFs, oversize documents and malformed text produce
clear limits rather than fabricated results. Citation targets must resolve to
actual stored excerpts; model answers still carry uncertainty. No broad filesystem,
project/NAS access or hidden upload follows from choosing one file. Documents
cannot override app policy or grant tools/approvals. Remove private extracted
copies/indexes on explicit deletion; preserve the original selected document.

## OF-08: Reusable writing tools and selected-text actions

**Status: planned.** Add editable prompt templates for summarizing, rewriting,
translation, brainstorming and explaining code, plus user-created templates.
Allow Android Share/selected-text entry into a reviewed local draft, with a clear
model choice and an explicit Send. Offer save-to-local-note or reviewed export.

Acceptance: users can inspect/edit exactly what text and template will be sent.
No background clipboard reading, automatic generation or automatic sending to
Hermes. Bound incoming text and handle unsupported attachments explicitly.
Templates work offline with English/Chinese coverage and preserve unsent drafts
on cancellation. Code is explained as text; this does not add shell execution.

## OF-09: Optional personal profiles, memory and temporary chats

**Status: planned.** Add local profiles for writing style/preferences and
user-approved notes/facts. Show, edit, remove and choose what enters each chat.
Add a temporary-chat option and clear local retention/privacy controls, including
which collections enter an explicitly selected backup.

Acceptance: memory starts off and never harvests other chats, credentials, files
or server profiles. Switching profiles does not leak another profile's context.
User instructions cannot grant remote authority. Memory inclusion is visible and
bounded by OF-03; missing/deleted entries cannot silently reappear. Temporary
chats do not enter persistent chat/search/backup/debug logs and are cleared on
exit/recovery; document normal Android/OS memory limits without claiming forensic
erasure. Existing persistent chats are unaffected.

## OF-10: Offline voice and a more helpful setup experience

**Status: planned.** Bring compatible offline dictation and read-aloud into local
chat, with an editable transcript before Send. Extend the existing guide/helper
with a guided local-first checklist, better missing-model/storage/context recovery
and natural-language topic selection constrained to reviewed helper checks.

Acceptance: speech works offline only when a compatible offline recognizer/voice
is installed and positively verified; show unsupported reasons otherwise. Never
enable an online speech fallback without explicit user choice. Read-aloud stops
on leaving/Stop and releases audio resources; test interruptions and Bluetooth.
Bundled ASR models or a new speech runtime require a separate feasibility/security/
license decision and are not promised by this item. Helper suggestions retain
reviewed IDs, source/version/freshness and explicit redacted-observation consent;
freeform model commands never become actions or bypass the deterministic guide.

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
  Test the actual feature rather than only schema/UI fixtures. Existing failed
  gates are not waived and all original twelve-method external-model/final
  production acceptance remains required.
- Commit and push coherent tested chunks when resumed. Publish only through the
  existing final-source, original-key upgrade and signed-release process.
