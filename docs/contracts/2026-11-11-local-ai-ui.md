# Separate phone chat and model controls

The non-exported LocalAiActivity is an explicit destination from Chat and Settings.
Opening it does not copy a remote draft, submit a remote request, switch a Hermes
profile or replay a failed/unknown request. Its MVVM controller has no Hermes
client or credential store. One application-scoped engine avoids overlapping
model loads across rotation. Leaving cancels work and holds the update guard
until weights are released. Drafts and picker results survive rotation in memory,
not Android saved-state bundles. Local JSON history is separate from Hermes Room
history and is excluded from backup.

Models are optional. The recommended download shows the complete separate LFM
licence before explicit consent; no model weights are bundled. Android document
import requires a separate source/licence review and copies into private storage.
Cancellation signals the document provider and closes the stream. The library
shows SHA-256, architecture, size and publisher-match status; imported identity
does not establish publisher trust or native compatibility. Contexts are presets,
models rehash before loading, and switching releases old weights before checking
available memory. Delete reviews explain retained history and unload first.

Local conversations remain bound to their model hash. Missing/different models,
the 30-turn limit and the 8-KiB question limit have explanations. Cancellation,
interruption, failure and output limits have distinct labels. A cancellation just
after atomic save records CANCELLED before releasing the guard. Only completed
and token-limited exchanges enter a later explicitly submitted prompt. Neither
stopped questions nor helper/remote requests are replayed. A context/byte overflow
requires an explicit new chat or larger supported context.

Handoff is a reviewed, editable clipboard excerpt with a phone-advice label.
The user chooses the Hermes connection/chat, pastes, reviews and sends separately.
No approval, action or automatic remote submission accompanies the copy.

## Scoped evidence

The full UI measurement snapshot passes 905 JVM cases across 89 reports,
229 policy cases, required debug/test APK assembly and both lint variants:
zero errors, 50 debug / 49 release warnings. Its debug app SHA-256 is
`5976a3de122b75373e9a6c070bd96799c4601d3af810615ec9be62a2cf2714e0`;
test SHA-256 is `b92f0bd0400b8ecf5d432c3d91674edca3245922c1d6ffe168dec7b3127a4234`.
The existing debug signer is retained and all four ABIs/alignment checks pass.
Five synthetic Compose checks pass independently on API 26 and API 37 for consent,
import cancellation, absent/wrong model send blocking, stop, deterministic guide
availability and explicit handoff review. They do not download or load weights.

A subsequent controller-only snapshot adds the after-save cancellation regression:
906 JVM cases, 89 reports, eleven focused controller cases. Its two controller
files differ from the packaged UI measurement; this is not an exact-head release
or physical app-level offline UI acceptance. Private source manifests, immutable
APKs and terminal logs are retained on the authorized build volume.

Open: real document-provider and HTTPS transfer acceptance, physical app UI with
radios off, actual model-library lifecycle/process/upgrade retention, grounded
helper quality, broader devices and the full original Hermes audit. Production
beta.8, its signing identity/data and the Pi were not modified by this chunk.
