# Using on-device AI (beta.9 preview)

## 13 November 2026 - beta.9 preview published; full audit remains paused

At the owner's request, beta.9/code114 is public as an experimental prerelease
from exact APK source0ee08e3 in main history. Both original-key APKs and all six
assets verify through anonymous public downloads and GitHub SHA-256 metadata.
Production APK:71,052,938 bytes; debug:92,608,818. Checksums, certificate,
183-component runtime SBOM and honest source/validation provenance are attached.
Stable1.0.0 is unchanged. Use the production APK to update the normal app without
uninstalling; Include preview releases enables in-app discovery.

The source's complete standard Android gate and seven selected debug-phone
native positives retain their exact scopes. Upstream compatibility inspection,
final exact-production-phone upgrade/live/offline acceptance and the full
original audit remain incomplete. No failed result/baseline/source guard is
waived. This release-only request does not resume the wider paused goal.
Earlier entries below describe their historical publication/candidate states.

[Download, exact source and remaining checks](contracts/2026-11-13-beta9-public-preview.md).

## 13 November 2026 - current check complete; owner-requested pause

The final actually offline cancellation/recovery method passes in27.984 s:
Stop during thinking and visible streaming, exact recovery, coroutine stop,
overflow refusal, unload/reload and retained original short-limit probes.
Seven selected native methods have positive evidence on the unchanged source0ee
debug app across identified companions; this is not the full twelve-method
external gate. The latest companion source6034/cdb0570164ff compiles and signs
with the original key. App code/defaults/caps are unchanged. Both app APKs,
models, UIDs and core rows are retained; only WorkManager's force-stop timestamp
changes. Exact private SQLite copies are removed and original networking is
restored. No phone test/model CLI/local build remains active.

Source0ee's complete1,135 API26/1,136 API37 standard gate remains accepted.
Source1180 has a retained one-case API26 Demo clarification failure, so its
complete gate fails. Source6034 Android CI is in progress at observation,
compatibility fails and worker CI passes; pending runs are not acceptance.
Beta.8 remains published and beta.9/code114 remains unpublished. Full upstream,
live project/worker/integration, phone and final-release audit rows stay open.
Work stops after this checkpoint at the owner's request; resume only on request.

[Paused evaluation and remaining work](audits/2026-11-13/PAUSED_REVIEW.md).

Earlier sections retain their original source/time scope.


## 12 November 2026 - bounded reasoning and reviewed helper schema

Code113 fully builds/signs (1,006 JVM cases, zero-error lint, all ABIs) but its
helper diagnostic fails0/6 without a reasoning limit and5/6 with the budget alone.
Code114 now implements the supported budget plus constrained unique reviewed-ID
selections. Integrated native vocabulary coverage passes49 checks; all six public
helper diagnostic topics pass at the original96-token total. Original48/96-token
physical assertions are restored. These diagnostics are not final APK/JNI proof.
Fresh build/CI/phone/upgrade/publication gates and all original audit work remain.
[Exact failed stages, correction and scope](contracts/2026-11-12-bounded-helper-selections.md).

## 12 November 2026 - native answer projection, fresh gates pending

The pinned runtime diagnostic identifies the pre-closed reasoning prefill as the
source of the observed marker/repetition. Code113 prepares one upstream session
for template/sampling/parser and streams only its append-only answer channel.
The physical vocabulary regression passes29 checks and one bounded native CLI
turn produces a clean sentence. These do not certify the APK or helper. Fresh
JVM/lint/all-ABI/standard-CI and physical JNI/helper/family/recovery gates remain.
The helper shares a384-token reasoning/answer budget; strict reviewed-ID validation
and approval boundaries remain. Old history and its observed defect are retained.
[Correction and exact validation scope](contracts/2026-11-12-native-answer-projection.md).

## 12 November 2026 - production upgrade/read checks and native output blocker

The installed original-key beta.9/code112 retains its identity and saved
connection. Refreshed current-server history and stored project details load.
An older cached missing chat remains preserved and is not counted as a live pass.
Production LFM3 completes a genuinely offline turn and its reply reloads with
weights unloaded, but exposes a reasoning-close marker and repeats a sentence:
native output acceptance and publication remain blocked. No history is rewritten.
The owner restores normal networking. Bounded resource points are not sustained
benchmarks. Fresh b599 complete standard CI independently verifies1,135 API26 /
1,136 API37 unique cases; external models remain separate. All original audit
rows, stable40/main101 compatibility findings and live worker/runtime limits stay.
[Exact observations, failures and scope](contracts/2026-11-12-production-phone-offline-output.md).

Current lifecycle checkpoint: the original cold/post-reboot methods pass on the
owner phone after one approved normal Android restart. Original models/history/
settings remain and no automatic model load/replay occurs. The intentional
preparation interruption is not a positive test. [Exact scope](contracts/2026-11-12-physical-restart-retention.md).
Earlier measurements and failed fixtures below retain their original scope.

These controls are available in the beta.9 preview; beta.8 does not include them. The
Hermes connection still uses your agent host. On-device AI is an optional,
separate text assistant that runs on the phone without Hermes connectivity.

## Install and load a model

1. Open **Chat → On-device AI**, or **Settings → On-device AI**.
2. Open **Phone models**. Inference needs a 64-bit application process, sufficient
   free storage and available memory. The Hermes client and static setup guide
   remain available on supported 32-bit devices.
3. For the recommended **LFM3-1.7B QAD Q4_0**, review the complete separate model
   licence and explicitly confirm the download. It is 1,049,615,680 bytes (about
   1,001 MiB). The application does not bundle model weights. Downloading needs
   connectivity; inference after installation does not. Keep On-device AI visible
   and the phone awake during the transfer. Leaving its foreground activity stops
   work; retained partial data requires an explicit resume.
4. Alternatively, choose **Import GGUF file** and select a file through Android's
   document picker. Review its publisher's licence before confirming the copy.
   The file is copied into private application storage; its original remains.
5. Choose a context preset and **Load model**. Start with 2,048 tokens where
   supported. A smaller context reduces memory use. Loading rechecks the full
   file hash and metadata. Failure does not choose a remote model for you.
6. Return to Chat. Its heading identifies on-device AI and the loaded model.

The recommended file must match SHA-256:

```text
43a7d09a0e9cba91b63eb7052488b9564569bddfd65754d2e426465e572a7452
```

The publisher-match label identifies those exact bytes. An imported model's own
hash identifies the file and does not establish its publisher or licence.

The metadata reader admits GGUF v3 files up to 4 GiB with supported tensor
encodings, a chat template and one of these architectures: lfm3, lfm2, llama,
qwen2, qwen3, gemma, gemma2 or gemma3. Metadata admission is not a promise that
every quantization, template, model or device will load successfully. The actual
native load is a separate check. Broader physical model compatibility remains
under validation; see [current evidence](ON_DEVICE_AI_VALIDATION.md).

The official Qwen2.5-0.5B Q4_K_M file also passes real embedded import,
generation, unload/history, deletion and two-family switching on the owner's
physical ARM64 phone. LFM3 and Qwen histories stay bound to their separate file
hashes. These are native integration cases using explicitly staged inputs;
normal Qwen document-picker and final beta.9 physical-upgrade acceptance remain
separate. Qwen is not another built-in recommended download.
See [physical evidence and figures](contracts/2026-11-12-physical-offline-model-acceptance.md).

## Chat offline and manage memory

Type a new message in the on-device composer and send it explicitly. To copy a
new unsent question from Hermes Chat:

1. Type a new text-only question, then choose **Use unsent draft on phone**.
2. Review/edit the exact text and choose **Open as new phone draft**.
3. Choose/load a phone model and send separately. Your Hermes draft is retained.

Images, history, credentials, settings and approvals are excluded. Staged retries
and active/uncertain remote work cannot be copied. Expired delivery reports an
issue and keeps the original draft. See [routing scope](contracts/2026-11-11-reviewed-phone-draft.md).
No failed Hermes request is replayed. Phone conversations
have independent history; each stays bound to its model hash. To use another
model, load it and start a new conversation. Existing phone messages remain
readable even when their model is not loaded.

Use **Stop** during generation. Cancelled, interrupted and failed questions are
labelled and excluded from the context of a later send. After process exit,
unfinished local exchanges are marked interrupted and never automatically rerun.
An Android 17 emulator component test verifies real-native force-stop recovery
and read-only history after restarting the preserved AVD. A timed-out guest
reboot remains unaccepted; phone and transfer interruption checks are separate.
See [the recovery scope](contracts/2026-11-11-native-process-recovery.md).

Each chat has a 30-turn limit and each question an 8-KiB UTF-8 limit. Start a new
chat if its context is full; history is retained.

Leaving on-device AI stops active work and unloads weights. Returning requires
an explicit load. **Unload model** releases memory while keeping its file;
reviewed **Delete** removes the model file after unloading. Deleting a model
does not delete its conversations. The matching file is needed to continue them.
Android app-data removal/uninstallation deletes private models and local history;
they are not included in Android backup.

Stop ends unfinished work. If a verified model was already atomically installed,
it stays in Phone models; loading still requires your choice. Stopping an import
before publication removes its temporary copy and keeps the original selected
file. A reconciliation error preserves stored files and displays its reason.

Interrupted recommended downloads retain their partial file. Resume or discard
it explicitly from Phone models. A server that ignores a resume request leaves
that partial intact; it does not trigger an automatic second download.
An earlier component build passed real HTTPS cancellation/discard and a full
publisher-hash-verified API37 resume ([historical scope](contracts/2026-11-11-live-model-transfer.md)).
The current beta.9 emulator batch hits both original bounds: cancellation 10s
and resume 300s. The latter retains a 384 MB partial after actual progress. These
failures are preserved; current transfer acceptance is open. No network speed,
completion time or automatic resume is promised. Physical download, UI
interruption and process/reboot recovery remain separate gates; see
[the candidate record](contracts/2026-11-12-uncompressed-dex-candidate.md).

## Setup and troubleshooting

The **Setup guide** works before any model download. It includes connecting an
existing Hermes host, authenticated API access, private HTTPS/Tailscale, separate
dashboard sign-in, model choices, conversation history and stored projects.

With a model loaded, **Setup helper** suggests one to three reviewed checks for
six preset topics. Each check identifies where it applies: the phone app, Hermes
host or execution worker. The model chooses eligible IDs; the app displays exact
reviewed English/Chinese/pinyin text and source references. Generated commands,
URLs and freeform model instructions never enter this helper view. A rejected or
incomplete selection leaves the full guide and examples available.

Optional phone observations require a checkbox and show their capture time.
Only Demo mode, address-scheme category, 64-bit process and local-model-loaded
state are shared; hostnames, paths, profile names, tokens, chat history and logs
are excluded. These observations do not establish server readiness or
authentication. The helper cannot change settings, execute commands or approve
work. Suggestions still require your judgment about relevance. Ordinary phone
chat is freeform; review its answers carefully before using them.

## Share reviewed advice with Hermes

For a local chat reply, choose **Review for handoff**, edit the excerpt and choose
**Copy reviewed excerpt**. It receives a phone-advice label. Open the intended
Hermes connection and conversation, paste, review the destination and send it
yourself. No action, approval or automatic remote submission accompanies copying.

The earlier original-key debug candidate passes six physical offline native
cases, including all six helper topics and model/history isolation. The earlier
beta.9 candidate passes ten API26 local-AI UI cases. The current candidate passes
1,006 JVM cases and zero-error lint; its debug phone upgrade preserves owner data.
Real HTTPS cancellation/discard and full foreground resume/hash/atomic install
have positive physical evidence; earlier timeout/freezer failures are retained.
The current native cold-recovery case also passes on an API37 emulator, with no
automatic model load or replay. Guest reboot is unverified: the same VM remains
ADB offline after one request. Production-key upgrade and remaining lifecycle/
release acceptance stay open. [Current figures](FEATURES.md),
[delivery plan](ON_DEVICE_AI_PLAN.md) and [validation](VALIDATION.md).
