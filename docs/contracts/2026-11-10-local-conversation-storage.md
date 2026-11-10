# Independent on-device conversation storage

Local conversations use private UUID-named JSON files and a model SHA-256
identity. They do not use the Hermes database, server session IDs, profile
credentials or remote execution receipts. Saving validates bounds, syncs a unique
partial and atomically replaces the selected conversation. A malformed update
cannot replace the previous valid record. Partials are excluded from history.

Each reply records generating, complete, cancelled, token-limit, failed or
interrupted state. Ordinary reads preserve an active generation. The application's
single cold-start recovery must run before native work starts; it converts an
old generating receipt to interrupted and never executes that question again.
This is local text-generation state, not proof of any Hermes/tool outcome.

History lists retain small summaries rather than all transcripts in memory.
Files are bounded to 4 MiB, the library to 50 conversations, and each conversation
to 30 exchanges with bounded UTF-8 question/reply sizes. Cancellation checkpoints
are available between reads. A corrupt record is an error and remains available
for explicit deletion; it is not silently replaced with an empty transcript.

Eight JVM cases pass for Unicode/model identity, durable process interruption,
preserving live state during reads, every terminal state, invalid/oversized
updates preserving earlier history, corrupt-file retention, path-safe deletion,
partial exclusion and ordering. The source-identical component appears in two
development builds passing 895 JVM cases / 88 reports and zero-error lint
(50 debug / 49 release warnings). Those builds contain additional work and are
not this component's exact-commit release gates.

This accepts storage only. The controller, chat UI, explicit local routing,
reviewed Hermes handoff, grounded helper, Android process/upgrade checks and
the complete original audit remain required. No production history was migrated,
cleared or claimed to be available through this new local store.
