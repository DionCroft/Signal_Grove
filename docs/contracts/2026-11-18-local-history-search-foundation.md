# OF-02 read-only history search foundation — 18 November 2026

Internal feature-branch increment; not yet exposed in the application UI or
released. OF-01 physical acceptance still waits on the staged debug APK install.
That waiting step does not establish completion; no gate is waived by OF-02 work.

The helper supports literal case-insensitive title/question/reply matching,
Chinese/emoji, a model fingerprint filter and inclusive-start/exclusive-end
timestamp bounds. Empty/whitespace queries include all eligible local chats.
Results contain existing small summaries, ordered by updated time then ID.
It uses the strict bounded existing store, keeps one transcript at a time and
propagates cancellation/read failures without a partial result. It writes no
history, loads no weights and calls no inference/network/server interface.
The controller must schedule and serialize it away from the UI thread when wired.

Existing 50-chat, 30-turn and per-file/message limits remain. Search text is
bounded to 1,024 UTF-8 bytes with no NUL, models to valid SHA-256 identities and
time windows to ordered nonnegative values. No regex, fuzzy/semantic search,
new indexing database or extracted text collection is introduced.

Eleven new JVM cases cover title/body and Unicode/literal matching, visible
title normalization, stable order, exact date/model boundaries, a long reply's
last text and later exchanges, weights-absent reads, cancellation, malformed
history, retained interrupted/generating states and invalid filters. Assertions
compare original file bytes; a read never relabels an interrupted turn.

Required app/instrumentation builds, **1,064 distinct JVM cases / 102 reports**
and both lint variants pass without errors or JVM skips/failures (51 debug /
50 release lint warnings retained). This increment adds no connected UI/model
acceptance. Earlier 36-method reader/native cohorts retain their original APK/
source bindings; full final-head standard/phone gates remain separate.

Next: controller/UI search and filters, then reviewed rename/pin/archive,
selected Markdown export and validated/encrypted backup/import. Those features,
actual offline search UX, keyboard/accessibility and OF-02 acceptance remain open.
The phone still receives the specifically staged OF-01 APK pair; no newer build
is silently substituted. Beta.9, normal phone data and live Pi settings remain.

Immutable source snapshot: `18c8ff632da568ed54365663821cf3dba22e7e1e7e05483a68ec72d73d4c3a1a`.

Build receipt: `20261118-213344-7ce6b19813f84452bf6ff51e5342df8a`.
