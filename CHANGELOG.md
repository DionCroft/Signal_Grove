# Changelog

## 1.1.0-dev — audit follow-up

- Reviewed, profile-scoped MCP connection tests and enable/remove controls, with independent durable receipts and explicit uncertainty review (schema 9).

- Independent dashboard sign-in using browser PKCE, encrypted management sessions, explicit authenticated profile selection and scoped MCP inventory.

- Encrypted named API connections, guarded switching, explicit token-rotation boundaries and read-only cached history after credential removal.

Connection/submission isolation for delayed controls and run replies; preserved compatibility assistant history with distinct response outcomes; monotonic approval state and atomic decision claims; verified 5000-character cron prompt limit, known refusal handling and safe structured HTTP error metadata. 46 JVM tests and 38 connected tests each on API 26/36 passed. See docs/AUDIT_FOLLOW_UP.md.

Read-only server details now includes advertised capabilities, payload-based readiness, provider/model inventory, installed skills and API-server toolsets. Connection-scoped loading, offline startup preservation, explicit provider probing, lazy model lists and bilingual/pinyin scrolling are covered by 48 JVM / 43 API 36 cases and targeted large-text API 26 checks.

Hash-checked durable full requests, stable advertised conversation keys for new chats, atomic submission receipts and schema 5 migration are implemented. Per-conversation request cleanup accompanies cache deletion. 55 JVM and 45 connected cases each on API 26/36 passed.

Per-connection requested model/provider/reasoning/service-tier choices now feed frozen requests across Runs, session and compatibility transports. Catalog restrictions, explicit manual IDs, preference isolation, compact chat controls, keyboard adaptation and reader-respecting streaming are verified by 59 JVM / 49 connected cases each on API 36 and large-text API 26.

Reviewed session model locks, forks, steering and safe metadata are implemented with schema 6 durable mutation receipts. Legacy compatibility conversations retain local history and transport through capability upgrades. Targeted settings patches prevent stale appearance saves from restoring older connection or speech/privacy flags.

Android 17 local-network permission and explicit Settings controls now support private-address Hermes connections, with DNS/literal-address guards and revocation checks. API 37 emulator coverage adds a real deny/grant/connection flow. Test-only Espresso is updated to stable 3.7 for current platform input support.

Parallel tool-call pairing, public subagent lifecycle/lineage, server-reported runtime and usage, explicit incomplete outcomes, and SSE run-identity checks are implemented. Schema 7 preserves legacy activity and keeps accounting available independently of refreshed transcript IDs. Recent views are bounded, with no inferred zero usage or provider invoice.

Structured image history now preserves typed image parts in scoped private files, with bounded metadata-only Room references and schema 8 preservation. Inline images are oriented, resized and stripped of source metadata; previews can be enlarged, and remote links open explicitly in the browser. Exact Runs/session/compatibility image envelopes and smaller-page history GET recovery are implemented. Picker/share-target input follows as a separate chunk.

Native image picking now stages up to three private, cancellable, connection/conversation-bound previews. Sending remains separate, including image-only messages. Lost-admission recovery retains the original pixels/body/key/channel after a different draft image/model is chosen; explicit retry restores original JPEG bytes into a reviewable draft. Temporary drafts clear on context change/process restart, and Demo forks copy their image cache. Reviewed image sharing into the current draft is implemented, with caption append, bounded content-URI parsing, selected-target review, cancellation and pending/consumed state across recreation.

## 1.0.0 — 2026-11-02

Stable signed community release. Task JSON backup/reviewed migration, strict consent choices and visibility, bounded import parsing, credential/header hardening, current screenshots and user/compatibility/security/signing documentation. 43 unit tests, 30 device cases each on Android 8 and 16, plus large-text/tablet reruns passed. Production install and real file-picker task round-trip verified. See docs/releases/1.0.0.md.

## 0.4.0

Opt-in private best-effort notifications, local-task widget, confirmed job create/edit/pause/resume/run/delete, durable uncertain mutation receipts, atomic encrypted connection snapshots and schema 4 preservation. 39 unit / 26 connected cases passed. See docs/releases/0.4.0.md.

## 0.3.0

On-device speech policy, optional foreground read-aloud, persisted tool events, durable explicit approvals/clarification, exact prompt verification, ambiguous-delivery handling, cursor-based run recovery and replay-gap fallback. Schema 3 preservation and 33 unit / 20 device cases passed. See docs/releases/0.3.0.md.

## 0.2.0

Session search/pagination, local pin/hide, guarded rename/delete, atomic history synchronization, task notes and calendar-aware filters, offline read-only job snapshots, and schema 1→2 preservation tests. See docs/releases/0.2.0.md for validation.

## 0.1.0-alpha — 2026-11-01

Initial native Kotlin/Compose implementation: four themes, English/Chinese/pinyin, authenticated HTTPS capability discovery, persistent API-owned conversations, Runs/session/compatibility streaming, explicit approvals and status recovery, Room tasks, read-only jobs, Demo mode, speech transcription, documentation and CI. See validation for build/runtime evidence and README for alpha limits.
