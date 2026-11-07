# Changelog

## Unreleased

- Bound compact Android31+ history dialogs by the hosting activity viewport so multilingual More/Close controls remain above the keyboard.
- Synchronize the Demo-send regression against the actual native keyboard and stable control, retaining the original single tap/completion assertions and bounded failure evidence.

- Refresh Kanban/project review identity even when visible management state is unchanged, keeping stale confirmations closed and fresh read reviews available without changing request/receipt safeguards.

- Keep multilingual history filters and rows in one scrollable body. Use a compact read-only chat bar with a scrollable full explanation so large text leaves room for messages and controls. Keyboard-aware layout, intentional initial focus and a real Android-input paging/Close regression now pass on API26 and API37; full release gates remain open.

- Include existing Desktop, Discord and other Hermes session origins in paginated conversation history. Add loaded-row source filtering and a reachable multilingual Load more footer.
- Keep non-API and unknown histories read-only across the UI, request builder and operation repository; retain origin metadata without a Room migration. Preserve legacy API caches and existing pending work.
- Add 12 JVM and 14 device regressions for origin parsing, partition isolation, storage, paging, actual history reads and blocked foreign writes. Three related chat regressions also pass locally. The original-key production candidate was installed on the owner phone without a data reset; existing Desktop/Discord histories, a post-upgrade reply in the same API chat, retained sign-in and project metadata reads were verified. Hosted device and release gates remain separate.

## 1.1.0-beta.6 — 2026-11-06

- Avoid native session title collisions when several conversations start with the same text. Generate a sanitized, Unicode-safe label with a stable session-derived suffix while preserving the complete user message and existing conversations.
- Add actual send-path regressions for repeated opening text, multiline Unicode and explicit per-turn model selection while the saved server default differs. Do not automatically switch/load models or clear session locks.
- Preserve original packages, signing identities and data; see [hotfix scope](docs/releases/1.1.0-beta.6.md). Broader audit and task-override form work continue separately.

## 1.1.0-beta.5 — 2026-11-06

- Distinguish the verified serving model, saved Hermes default, next-request selection and last chat-reported model. Add Home/Chat access, foreground refresh and explicit cached/error states through the optional authenticated runtime observer.
- Separate Dictation and Read aloud, explain unavailable offline engines and offer Android speech/voice setup. Keep on-device recognition and offline read-aloud defaults.
- Add an offline multilingual Hermes setup guide and reviewed host project/Kanban administration, task creation and dependency controls with durable uncertainty handling.
- Expand upstream authentication, plugin recall/isolation and profile-status guards without replacing the original compatibility baseline. Preserve packages, signing identities and existing app data.
- This is a preview; live/provider/hardware coverage and the remaining automation/configuration audit work are tracked separately. See docs/releases/1.1.0-beta.5.md.

## 1.1.0-beta.3 — Signal Grove

- Rebrand the Android client as Signal Grove across the launcher, app header, notifications, widgets, sign-in callback, updates and documentation. The longer header wraps on narrow/large-text screens.
- Preserve Android package/data identities, signing keys, task-backup format and the existing GitHub update feed so installed versions can upgrade normally. See docs/BRANDING.md.

## 1.1.0-beta.2 — 2026-11-04

Second preview: physical-pixel motion/progress refinements and the reviewed management follow-up below. The broader Hermes parity audit remains active.

- Keep uncertain listener-scoped endpoint probes blocked across profiles at the same dashboard, including claims arriving during preflight. Reuse private exact-request local review, with clearer shared-work wording and a shorter fresh approval label. Ordinary profile-scoped MCP probes remain separate.

- Keep uncertain Team Gateway requests blocked across profiles at the same dashboard address. Hide another profile’s original receipt details and require a fresh explicit grant for local uncertainty review, including when the original profile or connection was removed. Sending requests remain protected.

- Sharper physical-pixel scenery and a draw-only eight-frame ambient clock, paused in the background or with reduced motion. Accessible stepped progress bars now match the pixel interface, including verified update downloads.

- Approved Team Gateway link-code creation and original-code recovery: encrypted local copies, explicit status checks, honest unchecked/used-or-expired states, private reveal, sensitive clipboard copying and separately reviewed bot handoff with fresh scope/status checks. Unknown requests never auto-mint replacements.

- Reviewed connector-managed Team Gateway account unlinking: fresh private target approval, one durable send, acknowledgement separated from account-list observations, encrypted original-target recovery and separately approved local cache clearing. Interrupted requests retain uncertainty and are never automatically repeated.

- Private Team Gateway account review, verified against the instance launch profile, with hidden account details, read-only unknown authority, and a separately approved Portal handoff that rechecks its destination before opening. Code creation, status and connector-managed account removal each have separate reviewed controls.

- Reviewed, profile-scoped channel setup: dynamic platform catalogs, private allowlist/routing inspection, separate enable/keep/replace/clear drafts, exact save approval, and host-reported connection/access posture. Durable uncertainty and ACK/readback separation preserve privacy across connection changes. Team Gateway linking and onboarding remain separate work.

- Stage discovered endpoint addresses, API modes, model catalogs and selected model aliases into a local draft. Saving requires a separate review; changing a returned address clears a newly entered key. Alias saves disclose canonical routing and any shared reasoning overwrite, preserve private details and distinguish accepted saves from configuration observations.

- Fix management pending-operation guards across inference connections pointing to the same dashboard/profile. Schema 10 retains and re-keys historical receipts, keeps other connections’ details private, and allows explicit local review after an original credential is removed. Sending requests cannot be cleared; no request is replayed. OAuth ownership and chat/inference scopes remain separate.

## 1.1.0-beta.1 — pixel interface and approved updates

- Original orbital-garden pixel scenery, Starhaven Grove palette, stepped panels and navigation icons, tactile buttons and gentle screen transitions. Readable native typography, saved-theme preservation, large-text support and app/system reduced motion.
- Public GitHub release checks on opening/resuming the app, stable/preview selection and explicit release review before download. Private downloads verify SHA-256, size, package, increasing version and the installed signing certificate before Android’s approved installer opens. Active Hermes work delays installation; cancel and restart never silently install.
- The audit follow-up below remains in progress. This preview is not a claim of complete Hermes feature parity.

### Audit follow-up included so far

- Reviewed gateway schedule pause/resume/removal, known selector-collision refusal, durable single-send receipts, exact acknowledgements and separate configuration/run outcomes.

- Explicitly reviewed gateway schedule reads through single-use ticket authentication, exact profile proof and non-reconnecting bounded JSON-RPC.

- Reviewed installed-skill availability and curator lock controls, plain-text SKILL.md inspection, essential-skill protection and scoped durable receipts.

- Reviewed advanced schedule creation with an explicit paused-schema gate, typed runtime/automation fields, preserved empty toolset allowlists and cron-specific durable receipt observation.

- Explicit management-profile advanced schedule inventory and exact-ID details, with bounded metadata, unknown-state handling and separate inference/management caches.

- Strict dependency SHA-256 verification, metadata/bypass policy checks and a checked multi-platform aapt2 baseline.

- Reviewed MCP OAuth start, exact persisted flow identity, transient browser destinations, explicit retry-aware status refresh and separately acknowledged cancellation.

- Reviewed MCP catalog installation with masked declared inputs, stale-manifest refusal, accepted/background distinctions and explicit host-setup limits.

- Typed MCP HTTP/stdio server creation with reviewed provisioning, protected existing names and receipts that omit secrets.

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
