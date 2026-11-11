# Using on-device AI (development branch)

These controls are in development source, not the published beta.8 APK. The
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
   connectivity; inference after installation does not.
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

The official Qwen2.5-0.5B Q4_K_M candidate also passes real embedded import,
generation, unload/history and delete tests on Android emulators. Switching
between it and LFM3 keeps histories separate. Normal Qwen picker/physical-phone
acceptance remains open; this does not create another recommended download.
See the [tested scope](contracts/2026-11-11-two-family-android-integration.md).

## Chat offline and manage memory

Type a new message in the on-device composer and send it explicitly. No failed
Hermes request or existing remote draft is copied or replayed. Phone conversations
have independent history; each stays bound to its model hash. To use another
model, load it and start a new conversation. Existing phone messages remain
readable even when their model is not loaded.

Use **Stop** during generation. Cancelled, interrupted and failed questions are
labelled and excluded from the context of a later send. After process exit,
unfinished local exchanges are marked interrupted and never automatically rerun.
Each chat has a 30-turn limit and each question an 8-KiB UTF-8 limit. Start a new
chat if its context is full; history is retained.

Leaving on-device AI stops active work and unloads weights. Returning requires
an explicit load. **Unload model** releases memory while keeping its file;
reviewed **Delete** removes the model file after unloading. Deleting a model
does not delete its conversations. The matching file is needed to continue them.
Android app-data removal/uninstallation deletes private models and local history;
they are not included in Android backup.

Interrupted recommended downloads retain their partial file. Resume or discard
it explicitly from Phone models. A server that ignores a resume request leaves
that partial intact; it does not trigger an automatic second download.

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

Physical offline CLI/native-engine evidence is recorded separately from full app
UI, document-provider, transfer, lifecycle and release acceptance. See
[the delivery plan](ON_DEVICE_AI_PLAN.md) and [validation](VALIDATION.md).
