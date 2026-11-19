# Reviewed summaries for phone chats

19 November 2026. Development branch.

Open Earlier turns, select the source turns, then choose Review a summary.
Write the summary or explicitly draft one with the matching loaded phone
model. The optional draft reads only the chosen turns and reserves 384 output
tokens. If the draft reaches that limit, the review displays that status.
The editable field accepts at most 4 KiB of valid UTF-8. You can create the
separate summary chat with the model unloaded.

Confirmation saves the approved text and source chat ID, timestamp, snapshot
hash and turn numbers. It may also copy the reviewed unsent draft. An explicit
Send uses the summary as background in the first user message and counts it
with the same native tokenizer as generation. Regular edit and regenerate
branches retain that context. The original chat remains available in history.

The feature uses transcript schema 6 within the existing backup container.
Versions 1 through 5 still read. Older builds need schema 6 support to read
these new chats or restore a backup containing them. Search, Markdown and
backup include the saved summary even before the first exchange. Restore
preserves the source references and remaps only the link to an included
parent. Drafting and review cancellation create no transcript.

## Validation

The exact build passes 1,152 JVM methods across 109
reports, both lint variants with zero errors and 81 focused Android methods
on each API 26 and 37. Regressions cover exact selected input, no publication
during drafting, cancellation, overflow, editing before confirmation, prompt
budget parity, unloaded review, import collision and Markdown or backup
selection before the first question.

The initial API 26 run reached 78 of 79 positive results. Its multilingual
backup fixture found the review disabled after password input. The test now
verifies selection, focuses the two fields and replaces the exact fixture
text. It still requires the real keyboard and checks both action bounds.
The initial API 37 run passed all 79 methods. The original logs are preserved. A real LFM3 trial of the initial draft
prompt returned an empty editor and preserved the original library. The
current build uses a direct request with a 384-token reservation. The next
API 26 run passed 80 of 81 cases. Its synthetic draft test missed the
button interaction. A later attempt timed out reading native-window
geometry. The final synthetic test asserts visible enabled Compose
controls and dispatches their semantic actions. The real-app trial used
actual touches on the same production source.

The actual Android 17 app loaded the pinned LFM3 model and requested a draft
from turns 1 and 3. The model reached its cap with a short draft that omitted
the lavender plan. The editor displayed the output-limit warning, and the
tester supplied the missing fact. One explicit confirmation saved the exact
edited text and source references. The original bytes matched and the new
chat had zero exchanges. Closing the app view released the model.

- Build receipt `20261119-165313-f981f3abf1c643878d2d6d207aea863c`
- Source snapshot SHA-256 `29c085a472751e732d4b9a5c6e81fc1869c40cfa0243717c552e36d4b5a80682`
- Debug APK SHA-256 `8aae5f9b1e412c3644a3dd03868f5b5d70b9327db6320fbed32527d4e504b30b`
- Test companion SHA-256 `067b9956a5131a9f4c4cd8f55e03c8076589c05e2363c103d96f108fe4bc1a3c`
- Real LFM3 trial source snapshot SHA-256 `e26054b8b24d2a622afb9f6405ef250d54806cf57f0138940dd5aabb308c058b`
- Real LFM3 trial debug APK SHA-256 `3f078dc4a486a7177f99130be52f5cb1f9be3495ca8b815b225912a888aafafc`
- API 26 instrumentation SHA-256 `5bf4736991fe055242820ccb0d265616c5cc6cc3550597ec1531f9e9c4d2f988`
- API 37 instrumentation SHA-256 `af230d198a6ff0d1b322a2ea31fcd87e09555962c3fe04ab1a566a642bb7f307`

## Physical reviewed summary with the model unloaded

The owner tested the exact debug and companion APKs above on Android 17.
The normal production APK hash and all three package UIDs matched their
pre-upgrade values. After the debug update, the original four transcripts,
one restored copy, both model files and the labels matched their saved
hashes.

The owner selected turn 1 in Forest phone check, entered the summary by
hand and confirmed Create phone chat once. The app added one schema-6 chat
with the selected turn, its exact parent hash, a parent link and the same
model identity. The stored wording had the sample sentence's final period
omitted. No new exchange was present and the model was unloaded. The app
showed Read saved summary. The five preceding chats, labels and both model
files still matched. Network settings remained in their normal state.

The summary transcript SHA-256 is f521ea0f4606c1afe28d2d2bac2d4b42140325f999ade77bf212f90a181877ba. Model-generated drafts and the
copy-only selected-turn action still need physical acceptance. Earlier
Android 17 emulator proof covers their normal app paths.
