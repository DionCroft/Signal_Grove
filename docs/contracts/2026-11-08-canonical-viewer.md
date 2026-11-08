# Foreground canonical activity viewer

Development scope: metadata/activity observation from an explicitly selected
management profile. This UI is excluded from the d0 beta.8 release candidate.
The local acceptance below does not establish full backend/worker parity.

## Identity and effect boundary

Inventory uses the separate authenticated canonical channel. Selection freezes
actor, API scope, dashboard URL/prefix, profile/home, runtime/authority epoch and
the exact row. Read/cancel does not adopt a session. Confirming requires a fresh
one-use acknowledgement: Hermes may restore/adopt the chat, refresh settings and
hooks, and apply its configured policy when the last viewer closes. The viewer
offers no send, interrupt, decision answer, workspace change or automatic reopen.

The UI closes on lifecycle STOP, disposal and management context changes. Every
async result and live batch is generation-fenced. An identity-bound installation
guard lease lasts through loading/opening/following and releases on completion.
The socket heartbeat requires an advertised capability and exactly `{"ok":true}`
from the actual outer `gateway.ping` route, not the inner authority's pong shape.
Heartbeat timeouts/foreign scope/replay gaps hold observations without replaying
an opening. Periodic authenticated identity/profile reads recheck the owner/home.

## Applied cursors and private projection

Only confirmed canonical receipts can hold an applied-view cursor. Exact
connection/actor/profile/home/requested/resolved/runtime/authority/replay identity
and monotonic sequence are verified before an atomic Room compare-and-set.
A corrupt newer cursor fails closed. Lookup is capped at 50 matching receipts;
without a valid cursor at that bound, opening needs an explicitly fresh review.
Starting fresh skips older activity and does not delete server messages.

Activity stays in memory, capped at 40 entries and 512 Unicode code points per
preview; truncation is labelled and cannot split a surrogate pair. Receipts store
metadata/cursors, not transcript text, credentials or reasoning. Socket closure,
assistant text and running=false never establish execution completion. Unknown
opening receipts route to a dedicated local inspection acknowledgement, never
through MCP-name observation or an automatic resend.

## Local validation and remaining gates

Required tasks pass 768 JVM cases (12 checkpoint, 20 controller, 9 strict heartbeat
cases added), both lint variants0, 201 policy and 15 runtime-observer cases.
Nine Compose UI and five real Room methods pass on three preserved emulator
profiles: 42 executions. Original keys/preferences/geometry and 634 frozen source
inputs match. Retained failed import/lazy-node harness attempts are documented in
[validation](../VALIDATION.md); the final harness changes no production behavior.

Actual pinned backend viewer/heartbeat/teardown and applied-cursor conformance,
synthetic screenshot review and full exact-head hosted acceptance remain open.
Existing stable/prior/current-main negotiation/adoption results apply only to
their recorded earlier source/scope. No owner-phone canonical adoption, worker
admission, project filesystem operation, Pi deployment or broad integration
certification follows from these local checks.

## Pinned native heartbeat acceptance

The next conformance chunk retains the application APK exactly and adds one
native contract method plus a default-off, eight-RPC bounded fixture heartbeat
flag and four refusal tests. A fresh complete build passes 768 JVM, both lint 0,
205 policy and 15 runtime-observer cases. Fifteen explicit-emulator controllers
pass 69 method executions: 45 mock/UI/Room, 15 preserved adoption/recovery
regressions and nine actual-source pairings.
Original keys, preferences, AVD geometry, 28 legacy cache files and all 636
frozen source/context inputs match. Native screenshots are retained privately.

Stable `2bd378e` and main `21ae0e1` each show an independently verified 29-record
native PKCE/identity/home/ticket/socket/inventory/resume/replay/heartbeat trace.
There is exactly one opening, two sockets and three outer pings; authenticated
owner/home checks and Room initial applied-cursor readback pass. Prior `bf0203a`
shows the expected 12-record empty-eligibility refusal. All nine fixtures stop,
with exact transcripts, only the allowed synthetic ownership metadata delta,
zero admissions/worker executions and restored reverse bindings. All imported
source files match immutable Git blobs: stable 1301, main 1318, prior union 838.

This covers an idle initial cursor and periodic heartbeat. Advancing real-source
activity/checkpoint progression and full exact-head hosted gates remain open.
It does not certify an active provider, worker, filesystem, external TLS/browser,
real identity provider, owner-phone canonical session or current moving main.
The parent d40 canary now observes `8d7061`; changed pool/auxiliary internals need
separate triage even though selected mobile watches report no review required.

## Advancing replay and privacy acceptance

The next development chunk corrects a generic preview fallback that could expose
private reasoning text. Only schema-reviewed public fields are displayed:
message text, tool names and subagent summaries/tool names. Reasoning/thinking
events and explicitly private status kinds get a translated hidden notice;
unknown bodies have no preview. Their sequences still apply normally. Activity
remains in memory; durable receipts contain ownership metadata and cursors only.

The fresh full build passes 775 JVM cases, both lint 0, 213 policy and 15 observer
checks. Six new privacy/public-field cases demonstrably failed against the old
production projection before the fix; all earlier 768 cases remain passing.
Twenty-two mock methods on each emulator retain UI/Room and all five earlier
adoption/recovery cases. Nine real-source pairings produce 75 native executions.
Stable/main actual SessionEvents publish status/reasoning/message sequences
1/2/3 after the resume acknowledgement; overlapping replay/live delivery is
deduplicated. A later live status 4 precedes the periodic pong. The Android
controller observes exactly four entries, hides private text and records confirmed
Room cursors exactly 3 then 4. Prior remote eligibility remains empty.

Default-off fixture activity requires explicit heartbeat opt-in, fixed synthetic
payloads, exact canonical-session scope and the existing finite RPC allowlist.
It admits no tools/work and never reads the owner's projects. All nine actual
fixtures stop with exact transcript preservation and unchanged 29/12-record wires.
Every imported source file is independently bound to immutable Git objects;
stable/main/prior contain 1301/1318/838 imported files respectively.

The source inventory now includes `gateway/session_events.py`,
`tui_gateway/event_replay.py`, `tui_gateway/transport.py` and
`tui_gateway/contracts/events.py`. All original F0 and mobile-scope values and
existing review entries remain exact. The two changed consumed-module modes
require complete seven-module reviewed vectors; malformed or missing source
identities still require review. Both immutable stable/main comparisons cover
189 watched modules. This does not certify the newer moving main, local provider
inference, workers, external TLS or owner-phone adoption. Exact-head hosted gates
and every other open audit row remain required. The d0 beta.8 assets are unchanged.
