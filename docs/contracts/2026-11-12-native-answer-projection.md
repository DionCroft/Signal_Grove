# Native answer projection correction

12 November 2026. This source correction prepares beta.9/code113, increasing the
code above the installed code112. It has not yet passed a fresh APK/physical
acceptance gate and is not a public release.

## Cause and implementation

The production garden smoke test exposed a closing reasoning marker and repeated
sentence. A30-case vocabulary-only diagnostic with the exact bundled pinned
runtime confirms that disabling thinking produces an already-closed reasoning
prefill; the observed extra reasoning close remains literal answer text even
with the normal parser. Using the model template's normal reasoning prefill and
automatic reasoning separation projects the observed response into one answer.

The app now constructs one upstream common chat session, using that same session
for prompt parts, sampling configuration and token-aware streaming/final parsing.
Only parsed answer content crosses JNI. Reasoning is not displayed or saved.
Prompt tokenization still parses template syntax separately from plain user text;
automatic BOS/EOS handling, bounded fresh decode contexts, metadata repetition
controls, explicit cancellation and no tools/network access remain.

The answer projection requires an append-only bounded prefix and refuses tool
calls or rewrites. Raw generation is also byte bounded. Both EOG and token-limit
paths finish parsing; an empty EOG answer is a failure, while an exhausted budget
remains token-limited rather than complete. Existing stored chats are unchanged.

Reasoning shares the bounded generation budget. The helper total increases from
96 to384 tokens, matching ordinary local chat. Its accepted result remains only
one to three unique reviewed IDs; no generated instructions become actions or
guide text. Existing physical exact-answer and six-topic helper assertions remain,
with total-token caps adjusted for hidden reasoning. A garden streaming regression
requires append-only nonempty answer text without reasoning markers.

## Executed evidence

- The native vocabulary-only regression passes29 checks on the physical ARM64
  Android17 phone with the exact bundled llama.cpp libraries and reviewed LFM3
  vocabulary. Eight response shapes run as whole, per-token and per-byte chunks;
  they cover repeated reasoning/final text, split/unfinished markers, withheld
  reasoning, multiline/Chinese/emoji/accented text and a literal marker inside
  answer content. Five checks cover stable prefixes and refusal of rewrites,
  oversized content and tool calls. No inference or app data access occurs in
  this regression. The initial test compile's mixed string initializer error is
  preserved; the corrected explicit string vector compiles and passes.
- An independent bounded native CLI trial uses the exact production metadata
  sampling functions, segmented prompt tokenization and corrected session path.
  It loads the retained LFM3 file, reaches EOG at147 generated tokens and returns
  one clean sentence. The decode-loop observation is3,774ms; total host wall
  observation is8,068ms. This one connected diagnostic is not an offline APK,
  latency guarantee, sustained benchmark or six-topic helper acceptance. Private
  diagnostic output stays outside Git. It changes no model or app history.
- The original production code112 offline answer and its formatting failure remain
  preserved. Earlier complete b599 standard Android coverage is historical;
  changed native/build inputs require fresh checks.

## Reproduction and remaining gates

Build the explicit optional native regression target against the pinned source
with `SIGNAL_GROVE_BUILD_NATIVE_TESTS=ON` and target
`signal_grove_chat_output_tests`. It is never included in the APK. On a reviewed
64-bit test device, supply the verified LFM3 GGUF path and the matching extracted
native-library directory as its two arguments, with that library directory on
`LD_LIBRARY_PATH`. The model SHA-256 remains
`43a7d09a0e9cba91b63eb7052488b9564569bddfd65754d2e426465e572a7452`.
Expected successful terminal output is `NATIVE_CHAT_OUTPUT_PASS checks=29 vocab_only=true`.

Fresh JVM/lint/all-ABI builds, exact-source standard CI, physical JNI garden,
six-topic helper, Unicode/cancellation/recovery and relevant family tests remain.
Then verify the increasing-version original-key upgrade and public artifacts.
Do not substitute the CLI trial for these gates or rewrite the old saved answer.
All original compatibility/worker/automation/integration audit work stays active.

[Observed production defect](2026-11-12-production-phone-offline-output.md) ·
[Full audit](../AUDIT_EXECUTION_PLAN.md) · [State](../../PROJECT_STATE.md).
