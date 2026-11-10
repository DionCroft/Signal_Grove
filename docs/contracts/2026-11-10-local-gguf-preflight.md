# Bounded local-model preflight

This component validates a local GGUF v3 file before an optional native loader
sees it. It does not load weights or establish that a model can generate correctly.
The file stays on its owning device; this API has no network, credentials,
Hermes session identifiers or tool execution.

The reader limits the file to 4 GiB and the header to 32 MiB. It bounds metadata
counts, string/array sizes, dimensions and tensor counts; rejects duplicate keys
and tensor names, unsupported architectures/encodings, missing chat templates,
invalid alignment, overflowing/overlapping tensor ranges and truncated bodies.
Supported metadata describes LFM3/LFM2, Llama, Qwen2/Qwen3 and Gemma families;
native architecture/template/device compatibility remains a separate gate.
Model display names lose control/format characters. Templates are bounded data
for later validation and are not executed by this reader.

Sequential buffered input and a cached offset avoid per-byte filesystem calls
across a large tokenizer vocabulary. A caller-supplied checkpoint makes long
scans cooperatively cancellable. Cancellation is propagated as cancellation,
not reclassified as malformed model data. No weights are allocated or retained.

Validation on 10 November 2026:

- All 22 reader JVM cases pass, covering valid metadata/tensor blocks, unknown
  architectures/encodings, bad/truncated/oversized metadata, tensor bounds,
  malformed UTF-8, display controls and cancellation inside a large vocabulary.
- The official hash-pinned LFM3-1.7B QAD Q4_0 passes a real-phone header scan in
  **45 ms**, and the same full-model scan stops at its requested checkpoint.
- The measurement build passes 865 total JVM cases in 85 reports, 229 policy
  cases and both lint variants with zero errors (48 debug / 47 release warnings).
  It contains other development work; these totals are not an exact-commit
  release claim for this independently committed component.

The physical measurement uses development app SHA-256
`af9b93e0753341c51a833f83acb80dd68113f4499f2f51d7ffeb6ecba76975a5`
and test APK SHA-256
`04761fb1a991897da3f1dcbf6305579bf56c345522c918320a31eb288443a5fa`.
The [model/runtime provenance](../on-device-ai-provenance.json) pins the weights.
Private device identity, complete source manifest and raw status stream remain
in E: evidence, outside Git.

The earlier unbuffered full-model run exceeded its 420-second supervisor bound;
its owned debug process was stopped without clearing data. That failed attempt
is retained. The corrected development APK generates its first exact reply and
cancels generation, but a later cancellation-recovery assertion fails at the
token limit. Full embedded-engine acceptance remains failed; it is not waived
by the successful parser test. Native lifecycle, offline chat, model management,
grounded helper, old remote-audit requirements and release gates remain open.

Focused JVM reproduction:

```sh
./gradlew :app:testDebugUnitTest --tests org.hermesmobile.app.local.GgufModelReaderTest
```

On the owner's Windows workstation, use the E: build wrapper. Runtime memory and
native loading need their own checks; a valid container is not a healthy model.
