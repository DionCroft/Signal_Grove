# OF-01 branch storage foundation — 18 November 2026

This is an internal storage increment, not completed OF-01 or a new APK release.
The full audit goal remains active. The new actions are not exposed by this chunk.

An explicitly created fork copies through a completed or token-limited exchange.
Edit/regenerate creates a separate child with one pending question and omits the
replaced reply and later exchanges. The parent is never saved or modified.
Each child records parent UUID, parent timestamp, selected exchange, action and
SHA-256 of the exact original file; it retains the model SHA-256. Generation
settings/provenance and the chat controls still require the next increment.

A changed source, live generation, invalid selection/text, full 50-chat history
or ID collision refuses publication. Existing turn/text/size limits remain.
Atomic publication precedes any future caller's inference. Cold recovery marks
a pending child interrupted without replay. Children are self-contained after
parent deletion. The one process controller serializes local writes; this is
not a multiprocess database or a server session/worker feature.

Legacy schema-1 chats remain readable without migration writes or invented
parentage. Branches use schema 2. Unknown schema/fields or malformed origin
records are preserved and reported, rather than silently dropping metadata.
Strict UTF-8 and bounded, cancellable reads keep the existing 4 MiB cap.

Validation: 1,023 distinct JVM cases across 98 reports, including 12 new storage
regressions, all positive with no skips/errors/failures. Debug/release lint has
zero errors/fatal findings and retains 51/50 warnings. Required debug app and
instrumentation APKs compile/package successfully; their ZIP CRCs and published
hashes were independently checked. The tested source snapshot is
`11e5f23ddff0fb24ac7aa186cf1289441fbbb8b14bf65d13fca2320e8444078a`, build receipt
`20261118-184942-655ed5a3bd5e42bc904eb15ce1b29b53` (SHA-256
`5ace8fa24c67c6edd18a7c78ab1174c80c409c567103530caedd56193d4c9f15`).

No emulator/physical inference or UI branch acceptance is claimed for this
storage-only increment. The prior UI guard source has separate exact-head hosted
CI in PR #1. Finish reviewed controls, exactly-once generation/cancellation,
conversation-scoped draft retention, actual generation provenance, passive
Markdown/code display and the remaining OF-01 device/accessibility gates before
marking OF-01 complete. Preserve all original Hermes/worker/release gates.
