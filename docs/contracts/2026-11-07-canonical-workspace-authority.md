# Canonical session and workspace authority review

Reviewed 7 November 2026. This is a contract/design checkpoint for the remaining
workspace audit. It adds no app protocol, backend, workspace or worker change.
Existing project administration remains metadata; foreign REST histories remain
read-only. The full audit goal and its outstanding acceptance rows stay active.

## Pinned evidence

Official stable source is v0.23.0 at
`2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19`. The complete inventory, local-info
and contract modules were also checked at main
`122fc40fb06b722b4debedac429c57336cff61ae`. Earlier transport/authority review
used stable and main `cf7003f3fa650e2cebc06a0af0c09dabaf5ee367`; this is not
a claim that every transport module was rechecked at the newer main pin.
Fetched bytes matched their Git blob identities before AST parsing. These
modules were inspected without execution; this is no live authority acceptance.
The original F0 compatibility baseline remains unchanged.

| Contract | Verified observation |
| --- | --- |
| [WebSocket ready](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/tui_gateway/ws.py) | The same transport can serve legacy methods. Only an attached AuthorityConnection adds the boolean `gateway.ready.payload.session_authority=true`. |
| [Authority connection](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/gateway/session_controls.py) | Server-authenticated actor identity and transport gates govern rights. `runtime.describe.capabilities` lists protocol features, not actual writer grants. |
| [Session contract](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/gateway/session_contract.py) | A handle binds profile/session, instance, authority epoch, revision, execution generation and state. A subscription snapshot carries the handle and replay cursor. |
| [Local session info](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/gateway/session_local.py) | Display info contains source/profile/protocol/model/cwd/policy fields; it does not itself supply a SessionHandle or writer permission. |
| [Inventory](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/gateway/session_local_inventory.py) | Owner-scoped builders run off the owner loop. A lazy session's policy/model/tool inventory is distinct from authenticated inference readiness or loaded weights. |
| [Workspace preparation](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/hermes_state_mutation_prepared.py) | Canonical workspace mutation checks directory/Git identity on the authority host, then updates frozen cwd/environment and lineage metadata under revision/generation guards. |
| [Worker contract](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/tui_gateway/contracts/canonical.py) | Worker registration/adoption/persistence prove a producer claim, including secret/PID/birth/generation. They are not general phone operator controls. |

The inventory and session-contract blobs match at the two newer pins. Main's
local-info diff adds named client metadata and a strict boolean `archived` field.
Treat optional/versioned projections explicitly. Do not invent missing fields
or equate SQLite integer flags with a verified boolean wire contract.

## Boundaries the implementation must preserve

`session.info` first resolves the authorized session and returns merged info.
It does not provide revision/generation by itself. `session.resume` may resolve
titles/latest sessions, adopt legacy histories, follow compression lineage and
route messaging owners before attachment. It cannot be silently used as a
side-effect-free read on the current operator connection.

That operator connection currently has no transcript subscription, server-request
opt-in, reconnect or mutation replay. Future canonical conversation attachment
needs an independently scoped design and explicit adoption effects. Successful
login, readiness or feature discovery cannot authorize a workspace write.

Info may include system prompt, launch request and a masked credential hint.
Keep responses private, bounded and redacted. Avoid logging or persisting full
info responses. Preserve the serving/default/request/last-response distinction;
the next-turn policy model is not proof of loaded inference weights.

Project ownership derived from cwd uses the authority host's projects database.
Neither an active-project pointer nor a server-local directory establishes
Windows, NAS or portable-worker execution routing. File hydration, byte grants
and worker dispatch each need their own authenticated ownership contracts.

## Next bounded implementation and acceptance

1. Preserve typed, per-connection ready negotiation with explicit advertised,
   unadvertised and malformed/unknown states. Parse a strict boolean; never
   turn a feature advertisement into a writer grant. Retain legacy metadata
   reads and all current credential/profile/prefix boundaries.
2. Review canonical attachment effects and exact handle/replay identities on
   isolated actual server modules. Preserve the management socket's existing
   no-subscription/no-server-request boundary and foreign REST write guards.
3. Bind workspace review to the server-resolved profile/session/instance/epoch,
   revision/generation and exact cwd. Use private exact-value approval and a
   durable one-send receipt; inspect unknown outcomes instead of replaying.
4. Extend consumed source guards alongside F0. Verify ownership/profile/epoch/
   generation conflicts, malformed discovery, cancellation/reconnect/privacy
   and unsupported-server behavior through actual-server and native checks.
5. Verify deployed authority, worker ownership and file/tree hydration separately
   before offering live project operations. Close complete exact-head gates and
   retain physical/OEM and synthetic fixture scope as separate evidence.

No canonical authority, workspace write, worker execution or project-file access
is certified by this source review.


## Implemented transport advertisement foundation

The current feature adds GatewayAuthorityAdvertisement and a read-only getter on
GatewayRpcSession. Missing payload/flag or explicit false is NotAdvertised;
only a non-string JSON boolean true is Advertised. A malformed payload/flag is
Unknown while existing scoped metadata remains usable. Only the first ready
frame sets the socket snapshot; close or failure makes it Unknown. No raw ready
payload, identity, prompt, credential hint or skin is retained by this type.

599 unique JVM cases, both fresh lint variants,179 existing policy tests and
seven affected native project cases per API26/API37 pass locally. Six new
WebSocket fixture methods verify strict/malformed values, misleading grant and
identity fields, exact ticket/profile/prefix traffic, no extra calls, connection
isolation, duplicate frames and protocol-failure invalidation. The current
gateway_ws:handle_ws AST watch covers the consumed wire field; changing its real
boolean to a string requests review without rebaselining F0.

This exposes protocol metadata to future setup code. It adds no workspace UI or
canonical session RPC. Deployed authority/actor grants, canonical attachment and
handle ownership, worker/filesystem routing, actual-server acceptance and full
exact-head hosted gates remain required.
