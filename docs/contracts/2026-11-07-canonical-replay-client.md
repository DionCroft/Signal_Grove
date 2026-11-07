# Canonical replay reducer

This production reducer is a building block for a separate live-session
connection. It currently sends no RPC and has no application UI caller. It does
not attach/adopt a conversation, restore a workspace, grant writes or execute
worker/tool work. Those remain required stages of the full audit.

The primary contracts are pinned official Hermes:
[canonical events](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/tui_gateway/contracts/canonical_sessions.py),
[event stream](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/gateway/session_events.py),
[attachment](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/gateway/session_controls.py)
and the independently pinned priorbf0203a794a07aac3ff764972ab9087baa4e4f4e.
Do not use the stable replay-first catch-up guarantee for that older revision.

## Application behavior

CanonicalReplayWindow starts buffering after a caller establishes an attachment
and supplies its last accepted cursor. It accepts live JSON-RPC event frames,
then reconciles session.events.since from that cursor. Replay must be strictly
contiguous through its declared latest sequence; replay/live overlaps must have
the same complete event content. Object key order does not make a conflict.
No output or cursor update occurs until the complete merged batch validates.
Following live events advance one sequence at a time, once.

The owner binds API scope, authenticated actor, server URL, selected profile/home,
server profile identity, instance, authority epoch and resolved session ID.
The reducer requires that exact owner on every call. A future connection must
obtain and verify those values from authentication, profiles and runtime.describe.
The owner type does not verify authentication itself. Ready advertisement,
capabilities, project active pointers and stored display metadata cannot supply
a grant or an execution owner.

Sequence/cursor fields use nonnegative integral JSON numbers; event sequences
must be positive. Booleans, strings, fractional and overflowing integers are
refused. Replay aliases, flags and counts must agree. Session/replay identity
and optional authority stamps are checked; execution-generation stamps stay
observations. Assistant final messages and finish reasons do not prove terminal
execution. Unknown event types remain bounded observations.

Conflicting duplicates, gaps, foreign owners/epochs, truncation, malformed wire
data and buffer exhaustion move the window to SnapshotRequired, clear private
pending payloads and emit nothing. The last safe cursor remains available.
A caller must establish a fresh snapshot/owner explicitly; the reducer cannot
replay admissions, tools or session mutations. Close clears retained state.

Default bounds are256 pending/merged events,2MiB per buffered/merged batch,
256KiB per event,32 JSON nesting levels and1024 recent event fingerprints.
A structural budget validates UTF-8/escape costs and depth before serialization;
unpaired surrogates and non-JSON primitives are refused. Object keys are
canonicalized for content fingerprints. Caller-owned JSON is
frozen before buffering. A duplicate older than the retained fingerprint window
requires a snapshot because its content can no longer be verified. Debug
strings redact owner identity, cursor epochs and payloads.

## Actual source vectors and validation

The existing isolated actual-authority probe now exports a bounded synthetic
client-replay-vector.json during attach-first flow. It contains only that
fixture's owner/cursor, replay events2/3 and live frames3/4. The exact exported
stable/prior vectors are committed as unit-test resources with their full source
commit identities. They contain no owner transcript, credentials or machine path.
The probe preserves its original assertions and does not patch upstream.

Repository probe runs pass stable46 authority/dispatcher checks and13 attach-first
checks on each stable/prior pin.497/486/436 imported module inventories were
independently rehashed, with0 unexpected denials. Expected version probes remain
blocked. These component fixtures use trusted synthetic actors and in-memory
transports; they do not certify HTTP/WebSocket authentication or deployment.

The unit suite exercises actual vectors, attachment/replay races, reordered and
concurrent duplicates, immutable payloads, exact owner isolation, malformed wire
values, missing sequences, conflicts, byte/count/depth bounds, fingerprint
eviction, cursor overflow and close/privacy behavior. Fresh638 JVM cases (all608 existing plus30 reducer methods),186 policy and both
lint0 pass after the revised604-input freeze. Existing auth/project native clients
pass two methods on each of three preserved emulator profiles with local mocks.
Those six native method invocations do not certify canonical attachment. Both
local freezes are closed; the initial successful build before the budget review
fix remains retained. See VALIDATION for exact outputs and scopes.

## Next required connection stage

Keep the management operator socket unchanged. A separate authenticated channel
must verify strict authority negotiation, actor/profile/home/runtime identity,
explicit resume/adoption effects and server-resolved lineage. Buffer live frames
before requesting replay, bind the actual snapshot/cursor and pass only verified
same-owner events to this reducer. Do not fabricate a full SessionHandle from
the resume snapshot: it omits instance identity and complete execution state.
Obtain actual handle/grant contracts separately before workspace mutations.
Preserve private review, durable one-send receipts and unknown-outcome handling.

The reviewed runtime.describe accepts an empty params object; dashboard ticket
creation has no selected-profile field. Verify a profile-bound acquisition path
before assigning a default connection instance to a selected sibling. Extend
consumed source guards alongside F0 when introducing the wire connection.
