# OF-01 passive phone reply reader — 18 November 2026

Unreleased work on `feat/offline-chat-actions`. OF-01 is still in progress:
physical model/UI/accessibility acceptance and final release gates remain.
The published beta.9 APK is unchanged.

## Behavior

Completed local replies render a bounded selectable Markdown preview. A full
reader preserves all text; larger replies use independently selectable chunks
instead of one large layout. Streaming shows a bounded newest portion; stored
reply bytes remain complete. Code blocks have explicit literal-copy controls and
a separate reader. No model text executes, opens a link or fetches an image.
English, Chinese and pinyin labels follow the existing Words layer.

Markdown uses the already pinned Markwon/core dependency. The local renderer
removes clickable link spans, supplies a no-op link resolver/image loader and
disables Android text classification. It does not enable HTML or network-image
plugins. Explicit copies preserve exact text and carry Android's sensitive-clip
flag. That flag suppresses previews on supporting systems; it does not assert
that every third-party keyboard honors it or cannot read a clipboard.

Bounds: replies remain capped at 128 KiB; previews are 1,024 UTF-16 units,
stream tails 512, and full plain-reader chunks at most 512 with surrogate pairs
preserved. Markdown full rendering is limited to 16 KiB, 4,096 UTF-16 units and
128 newlines. The code browser counts all blocks and retains the first 64;
the full original text remains readable/copyable. No parser or rendering claim
replaces model/context/storage limits.

## Verification and retained counterexamples

The exact source-bound required build passes 1,053 distinct JVM cases in 101
XML reports, both lint variants with zero errors (51 debug/50 release warnings),
and app/instrumentation APK assembly. Eight new JVM cases verify text boundaries,
literal code extraction/count limits and exact chunk reconstruction. Five native
reader methods cover styled/passive links, actual native text-classifier settings,
literal/private clipboard copy, long bilingual text and copy-to-keyboard behavior.

The final same APK pair passes 36/36 selected methods on each API26 and API37,
with no skips. The selection also includes existing local chat/review/storage
and seven real draft-routing methods. This is a focused synthetic-content UI /
actual private-filesystem scope, not a full standard matrix or physical-model
acceptance. The source receipt, APK hashes, raw inventory/method envelopes and
independent XML/native verification are retained in private owner evidence.

Earlier runs are retained: the first had 34/35 API26 and 33/35 API37 positives;
after adding the fifth keyboard regression another API26 run had 33/36 positives.
Both emulators then exposed System UI ANR windows, with boot-time system-app ANR
reports predating that run. Ordinary owned-emulator process restarts, one at a
time without data wiping, preceded the final checks. These facts qualify the
new verification; they do not establish a root cause for every earlier failure.
No Cancel/input retry, longer original readiness budget or dropped assertion
turns those failures into passes. Clipboard cleanup and a private-copy flag were
also added; their individual causal effect is not established.

No phone APK, user data, Pi service, gateway, credential, model/default or worker
grant changes in this chunk. TalkBack, real-phone edit/regenerate/branch and
native model behavior remain required before OF-01 completion.

## UI integration and upstream boundary

PR #1 merged into main at `a992cf4a8d534d4015c4ec820142854116c8859b`.
The reviewed head `f01f5d0`, actual PR checkout `1248873` and main merge have
identical Git trees. Independently parsed four-shard PR evidence verifies
1,149 API26 / 1,150 API37 standard methods with no skips, 1,011 JVM cases and
zero-error hosted lint (52 debug/51 release warnings). The extra hosted warning
reports an available Gradle version; it remains disclosed. External-model gates
are separate. Main's Android and Windows worker runs pass.

Main's upstream compatibility job 44384185499 fails closed while obtaining its
tracked source tree; it is not a compatibility pass. Independent official Git
tree inspection reproduces missing unreviewed modules at stable v0.23.2
`ae2a81c` (`tools/cronjob_job_args.py`) and main `4ae451d` (that file and eight
`hermes_state_*` modules). Review their replacement contracts before changing
the checker/baseline; retain the prior semantic/transitive/shutdown findings.

Sources: [Markwon core configuration](https://noties.io/Markwon/docs/v4/core/configuration.html),
[Android sensitive clipboard previews](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste#sensitive-content),
[TextView text classification](https://developer.android.com/reference/android/widget/TextView#setTextClassifier(android.view.textclassifier.TextClassifier)),
[PR Android run](https://github.com/DionCroft/Signal_Grove/actions/runs/44368972662),
[main upstream run](https://github.com/DionCroft/Signal_Grove/actions/runs/44384185499).

Exact build/source bindings:

| Item | SHA-256 / receipt |
| --- | --- |
| Immutable source snapshot | `b406c9e65730b534d913bbc5bf049470f43c0800c41adcb2fcc925a7b65db9e5` |
| Build receipt | `20261118-205335-699af79d476e44abbebc3148b90219a2` |
| Debug app APK | `8f885e00eec0f494bf44867a471501c8eba04d3c4395609ec3eb465d6ddcbcd9` |
| Debug test APK | `0a765d4edc917f49aab52fbd731640590bb2bd39b0d86cd5d98e8e47beb343a0` |
| Native source policy fingerprint | `0223a5df63104eb358eac6b6ebaf4a47af4f87bd3f8d3220627be3e730fa7063` |
