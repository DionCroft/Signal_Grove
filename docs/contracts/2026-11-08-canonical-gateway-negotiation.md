# Authenticated canonical gateway negotiation

This separate connection is an internal production building block, without a UI
caller. It negotiates runtime identity only. No session read, resume, restore,
adoption, subscription, grant, approval or work request is sent. Canonical
attachment/replay and full handle/grant binding remain required next stages.

The authenticated management client verifies the actor and selected profile
home, obtains a one-use dashboard WebSocket ticket, opens a separate canonical
socket, and verifies the profile home again before returning it. The existing
management operator socket keeps its behavior.

The first ready frame must explicitly advertise canonical authority. Its skin,
change-events flag and replay epoch must have valid types. The peer must select
only hermes-gateway-v1; credentials are sent in the ticket subprotocol, never
in a URL query, bearer header, cookie or Origin. Runtime negotiation sends exactly
one runtime.describe with an empty params object. Source-declared protocol
features and creation descriptors are validated without treating them as grants.

The returned profile_id must hash to the selected authenticated profile home.
Dashboard tickets have no profile selector, and runtime.describe rejects extra
params. Therefore a host socket bound to the primary runtime cannot supply an
instance identity for a sibling selection. That mismatch is refused before any
session request. A verified profile-bound acquisition path remains necessary for
that sibling; this connection never invents an instance or changes the host.

Instance ID, integral authority epoch and exact target remain bound together.
Closing or failing the socket removes access to that binding. Debug strings
redact private identity. Frames have bounded UTF-8 bytes, nesting and aggregate
size; escaped duplicate object keys are refused before JSON projection. Server
requests, foreign replies, unsolicited transcript events and notification floods
fail closed. There is no automatic reconnect or retry.

Primary reviewed source is official Hermes:
[runtime wire](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/tui_gateway/contracts/canonical.py),
[authority dispatch](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/gateway/session_controls.py),
[dashboard upgrade](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/hermes_cli/web_routers/chat_ws.py)
and [ticket route](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/hermes_cli/dashboard_auth/routes.py).
The independently pinned prior bf0203a revision remains a separate fixture.

Two added source aliases extend the original F0 baseline using verified F0 Git
blob bytes; existing entries are preserved. Twelve selected handler/schema/
registration projections and eight runtime descriptor fields are watched.
Changed canonical variants require exact declaring/wire/socket module hashes.
This source check does not certify unreviewed main revisions or deployment.

## Accepted local validation

The revised frozen build passes all 665 unique JVM methods freshly, including
27 new handshake regressions, plus 191 policy tests and both lint variants with
zero errors. The original duplicate-ready regression first failed because the
protocol cause was lost between readiness and describe. The channel now retains
the first failure across that race; the assertion is unchanged. The first failed
build and reports remain retained, and it did not publish new APKs.

One native contract method passes nine invocations: mock, actual stable and
actual prior Hermes, each on the preserved API26/large37/compact37 profiles.
Each invocation checks ten named assertions. The actual fixtures use upstream
dashboard authentication, a synthetic IdP, actual SessionAuthority and the real
WebSocket dispatcher. Each permits exactly one empty runtime.describe per
socket. Independent traces show two sockets and exactly 19 expected HTTP/WS/RPC
records per invocation, with no session read/adoption, admission or mutation.

All 903 stable and 828 prior imported source-module inventories are rehashed.
Thirteen expected metadata subprocess probes are blocked per actual invocation;
there are zero unexpected denials. Seeded history, saved credentials, original
signing keys, emulator geometry and reverse bindings remain intact. All 610
source/context inputs and 28 legacy cache files are unchanged; 159 published
files match the verified E: outputs. No owner-phone fixture or reset occurs.

The complete static stable comparison passes all 168 aliases with Git blob
binding; all original F0 entries are preserved. The older Windows-chunk canary
at main72d4433 remains failed: its middleware AST is unchanged from the reviewed
main071, but the guarded saved-gateway callback dependency changed. That code
requires separate review; no source-guard waiver or runtime acceptance follows.

Upstream socket initialization can activate already configured deferred
discovery. These fixtures contain no configured external MCPs and certify no
TLS, real external browser/IdP, Pi deployment, inference, worker or UI behavior.
Explicit attachment/adoption, replay integration and supported operation-specific
owner/grant contracts remain required. See [validation](../VALIDATION.md).
