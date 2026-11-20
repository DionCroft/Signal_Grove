# Using on-device AI (beta.9 preview)

## Set up offline chat in the development build

1. Open **Settings → On-device AI → Phone models → Set up offline
   chat**. The same guide is available in local **Chat** when no model
   is loaded.
2. Choose **Compare downloadable models** to browse the offline
   catalogue. Download a listed file when connected, or use **Import
   GGUF file** to select a compatible file you already have. Review and
   confirm its licence.
3. Open **Phone models** and tap **Load model**. The model card shows
   the minimum available RAM needed.
4. Use **Open phone chat** in the guide, or open the **Chat** tab. Type
   a message and tap Send. Use **New chat** first if the conversation
   on screen is bound to a different model.

The guide shows installed and saved chat counts and the loaded model.
It works without a Hermes connection. Each download, load and message
requires a separate action.
[Guide validation](VALIDATION.md#guided-offline-chat-setup).

### Choose which screen opens from the app icon

In **Set up offline chat**, scroll to **App startup** and enable
**Open On-device AI from the app icon**. A fresh launch opens
On-device AI without loading a model. The **Home** icon at the top
of that screen opens Hermes. Turn the same switch off to restore
Hermes as the app-icon destination on a fresh launch.

Returning to an open task resumes it. Notifications, the Tasks
widget and shared images still open their Hermes destinations.
Any separately enabled background reminders follow their own
settings.
[Startup validation](VALIDATION.md#optional-on-device-ai-startup).

## Compare downloadable models in the development build

Open **On-device AI → Phone models → Compare downloadable models**. Both
model descriptions and their licences are available offline. The load
check uses free RAM at the time you select the installed model.

| Model | Exact file size | Free RAM required by the load check |
| --- | ---: | ---: |
| LiquidAI LFM3-1.7B QAD Q4_0 | 1,049,615,680 bytes | 2,258 MiB |
| Qwen2.5-0.5B Instruct Q4_K_M | 491,400,032 bytes | 1,194 MiB |

Select **Review licence and download** to see the publisher, file revision,
hash and licence for that entry. Scroll to read the licence. Consent and
confirmation stay visible below the text. Confirm the download when online.
Each model keeps its own unfinished download. After installation, return
to **Phone models → Load model**. The downloaded weights can then run
offline. Use **Import GGUF file** for a file obtained elsewhere.

With multiple installed models, search by model name, architecture or
SHA-256. **Phone models** also shows the installed model total, sampled
free storage and retained download data. Tap **Refresh** to recheck
the figures. [Library and licence validation](VALIDATION.md#installed-model-library-and-fixed-licence-controls).
[Catalogue validation](VALIDATION.md#offline-catalogue-and-separate-model-downloads).

## Manage model downloads in the development build

Open **Phone models → Compare downloadable models**, select a file,
and review its source and licence. After selecting the consent box,
tap **Add to downloads** to save it for later or **Confirm** to start
now. Saving the item requires no internet connection.

Open **Phone models → Model downloads** to choose which saved file
to start or resume. The list shows exact bytes and whether the app
is transferring, verifying, checking compatibility, or installing.
**Pause download** keeps the written bytes. Leaving On-device AI
also pauses the current transfer. To abandon a selection, use
**Remove queued download** or review **Discard partial download**.
A partial from an older version opens licence review first.

Each transfer starts on an explicit action and each installed model
needs its own load action.
[Download validation](VALIDATION.md#reviewed-download-queue-and-explicit-resume).

### Choose a network for model downloads

In **Phone models → Model downloads → Download network**, choose
**Any network** or **Unmetered only**. Any network is the default.
For the second option, Android must report the current network as
unmetered. If the network changes while downloading, Signal Grove
keeps the saved bytes and waits for you to resume. The setting
applies to model transfers. You can verify a complete saved file
offline.
[Network preference validation](VALIDATION.md#model-download-network-preference).

### Keep a model download running while you leave

In **Model downloads**, select **Download in background** on a
reviewed item. Signal Grove shows a notification with progress and
Pause. Tap the notification to open the downloads list. This option
unloads any loaded phone model. If the app asks for notifications,
open Android notification settings, enable the model download
channel, then choose the download again.

Choose **Start download** or **Resume download** to keep it tied
to the On-device AI screen. If Android stops a background transfer,
return to the list to resume its saved bytes yourself.
[Background transfer validation](VALIDATION.md#opt-in-model-downloads-while-away-from-the-app).

### Review another version of an installed phone model

1. Open **Phone models** and choose **Review a replacement file** on the
   model you already have.
2. Pick a compatible GGUF and confirm that you reviewed its licence.
   Signal Grove stages the file. The phone needs space for both files.
3. Compare the hashes and see the new file size and remaining space.
   If you have a SHA-256 from the publisher, compare it with the
   selected file's displayed hash.
4. Choose **Install alongside**. Select **Load** on the new model when
   you want to use it in a new chat. Existing chats continue with their
   original model. Use Cancel to discard a review and release the space.

[Replacement validation](VALIDATION.md#reviewed-replacement-from-a-phone-model-file).

## Choose settings for a phone model

In a development build, open **On-device AI → Phone models → Model settings**
on the model you want to use. Select a preset or open **Advanced settings**
to choose the context size, CPU thread cap and reply token cap. Tap **Save**.
The model card shows the settings for its next load and the settings in use
if the model is already loaded. Tap **Load model** to apply the saved choice.

| Preset | Context tokens | CPU thread cap | Reply token cap |
| --- | ---: | ---: | ---: |
| Balanced | 2,048 | 4 | 384 |
| Fast | 1,024 | 4 | 384 |
| Battery saver | 1,024 | 1 | 384 |

The app caps preset context at the model's trained limit and CPU threads at
the phone's available processor count. The reply cap covers reasoning and
control tokens as well as visible text. Presets retain the 384-token
cap. Lower advanced caps can end a turn before any reply text appears.
Actual speed and energy use depend on the phone, model and prompt. The RAM
figure on the model card is the minimum available-memory check before load.
Generation stores the settings it actually requested with the reply. Open
**Request settings** on a saved reply to read them later.

[Scope and validation](contracts/2026-11-19-phone-model-performance.md).

## Measure performance on your phone

Load a model and open **Phone models → Performance details**. App memory
shows one sample of Android's proportional set size (PSS) after load, in
MiB. It includes Signal Grove's process and its proportional share of
shared pages. [Android Debug.MemoryInfo](https://developer.android.com/reference/android/os/Debug.MemoryInfo#getTotalPss()).

After an ordinary chat reply, the panel shows how long it took to receive
the first visible text and the rate of sampled tokens, where available.
The first-text timer includes prompt processing. The sampling rate counts
reasoning and control tokens and excludes prompt processing. A model can
reach its reply cap before producing any visible text.

Choose **Run benchmark** to measure a fixed garden prompt offline with the
loaded settings. It uses up to 384 output tokens or the loaded cap if lower.
The app requests cancellation after 60 seconds. You can also tap **Stop**.
The benchmark creates no chat and shares no prompt or result with Hermes.
**Copy performance report** puts the active settings, measurements and
benchmark status on the clipboard. Unload the model to clear in-memory
measurements.

## Continue a phone chat with a reviewed summary

In the development build, open a saved phone chat and choose **Earlier
turns**. Select the turns to summarize and tap **Review a summary**. Write the
notes yourself or choose **Draft with phone model** when that chat's model
is loaded. Check the source turn numbers and edit the draft before approving
it. The summary can contain up to 4 KiB of UTF-8 text.

Choose **Create phone chat** to save the summary as context for a separate
phone chat. If you selected **Copy the unsent draft too**, the new composer
shows your draft. Review it and choose **Send** when you are ready to run a
new turn. The token meter includes the saved summary. **Read saved summary**
opens the approved text. Phone history can search, export or back it up
before the first new question.

[Saved format and checks](contracts/2026-11-19-phone-reviewed-summaries.md).

## Continue a phone chat with selected earlier turns

In the development build, open a saved phone chat and choose the **Earlier
turns** branch icon beside New Chat. Select the turns to keep. You can open a full turn in the review
and choose whether to copy your unsent draft. The preview measures the selected
turns and draft when the matching model is loaded. The count excludes omitted
turns.

Tap **Create phone chat** to open a separate chat with the selected turns in
their original order. The original conversation and its draft stay available.
The new chat shows the source turn numbers and links to the parent when it is
in Phone history. You can create the copy with no model loaded. Load the same
model to send a new question. Creating the copy sends nothing.

[Saved format and verification](contracts/2026-11-19-phone-selected-turns.md).

## Check how much context your phone model has

On the development branch, load a model and enter a draft. The chat composer
shows the number of prompt tokens used and the loaded context capacity. Tap
the count to see how many output tokens remain and how many the current request
reserves. The count includes the model template, special tokens and eligible
earlier turns. The output reservation covers both reasoning and the reply.

If the prompt leaves too little room, shorten the question or open a larger
supported context. **Use draft in new chat** copies the text into a new unsent
chat and keeps the original chat and its draft. Edit and regenerate reviews
measure the exact earlier turns for that branch. A request that exceeds context
keeps the original history and any unsent question.

[Validation and limits](contracts/2026-11-19-phone-context-meter.md).

## Back up and restore phone chats on the unreleased branch

1. Open **On-device AI → Phone history → Back up chats**. Select the saved
   chats you want, including archived chats, then tap **Set backup protection**.
2. Keep **Protect with a passphrase** on, enter the passphrase twice and keep
   a copy of it somewhere safe. Turn protection off only when you want an
   unencrypted file. **Change selected chats** returns to the list with
   your selection intact and clears any passphrase you entered.
3. Tap **Review backup → Choose where to save**. A local folder keeps the
   backup on this phone. A cloud document provider can sync the chosen file.
4. To restore, open **Phone history → Restore a backup** and select the file
   in Android's picker. For protected files, enter the original passphrase.
5. Review the chats, labels and any new IDs before tapping **Restore reviewed
   chats**. Chats already on the phone keep their original IDs and contents.
   A restored branch with a missing parent opens on its own.

The backup contains selected phone chat transcripts, model IDs and saved
labels. Model weights and Hermes conversations are stored separately. Phone
history can restore without loading a model. If Android reports an
interruption or cannot confirm a write, check the selected location and
Phone history before trying again.

[Exact build and validation](contracts/2026-11-19-phone-history-backup-ui.md).

## Export a phone chat to Markdown on the unreleased branch

In **On-device AI → Phone history**, choose **Export Markdown** on a saved chat.
The review shows the selected title, saved activity, exchange count, file size
and suggested filename. Choose **Choose where to save** and select a location
in Android's file picker. Choose Downloads or another local folder when you
want the document to stay on your phone.

The UTF-8 file includes the selected chat's saved questions, replies,
completion states, model fingerprint, recorded generation settings and
any reviewed summary with its source references.
Text inside messages is exported literally. The filename uses a short
conversation ID. Keep sensitive chat exports in a location you trust, because
Markdown is plaintext. A cloud document provider may sync files it receives.

Cancelling the review or Android picker leaves history untouched. Leaving the
picker after choosing a destination may leave an empty document if the activity
was interrupted. A stopped or unconfirmed write can leave a partial file.
Check the destination before starting a fresh export.

[Validation and provider behavior](contracts/2026-11-19-phone-markdown-export.md).

## 13 November 2026 - goal active; four acceptance priorities explicit

The owner resumed the existing full audit goal. Its explicit priorities are
API26 Demo failure investigation, upstream compatibility/shutdown review,
real project/workspace/worker/automation/integration acceptance, and final
production upgrade/release checks. Beta.9 preview availability is complete;
incomplete acceptance stays open. All original audit requirements remain.

Five unchanged source1180 Milestone03 class trials pass15 original native
methods on a fresh owned API26 emulator under the original CI settings.
They do not reproduce or fix the retained hosted failure. One original598-case
shard reproduction is running with all outcomes retained. No app/test assertion,
SDK source, live Pi/model/credential or user-phone data is changed.

[Resumed criteria and exact baseline scope](contracts/2026-11-13-resumed-acceptance.md).

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
separate. The development catalogue now offers the exact Qwen file as a pinned download.
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
