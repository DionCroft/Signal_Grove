# Real canonical authority component regression

Reviewed 7 November 2026. `scripts/compatibility_probe_session_authority.py`
executes the real SessionDB, SessionAuthority, LiveSession, AuthorityConnection
and canonical JSON-RPC dispatcher
from official Hermes v0.23.0, commit
`2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19`. It checks the clean checkout identity
and six reviewed Git blobs before importing it, then records imported module
hashes. It preserves the existing F0/source watcher and Android protocol.

## Verified behavior

A fresh SQLite database contains two synthetic transcript messages. Twenty-four
original assertions exercise the real authority and runtime store:

- Reading binds profile/session, instance, epoch, revision and execution generation.
  Foreign actor/reference profiles, missing read capability, unknown sessions,
  foreign LOCAL owners and retired authority are refused. A matching LOCAL owner
  can read without creating another session.
- Identity dictionary fields cannot grant operator access. Only the explicit
  trusted internal `operator=True` constructor can cross the fixture owner gate.
  This tests constructor behavior; it does not verify a transport's authentication.
- Attach preserves history, cancels pending/orphan closes and reuses its own
  subscription. Another transport receives a distinct subscription. Foreign
  detach is refused; own detach preserves the other viewer and final detach
  removes the appropriate fanout sink. Closing connections releases registration.
- An identical queued request reuses its receipt. A changed payload under the
  same request key is refused. After actual claim and authority-epoch replacement,
  recovery marks the work unknown, retains its generation, rejects stale admission
  and refuses another claim. The original transcript remains intact; no producer,
  tool or inference call executes.

Contracts: [authority](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/gateway/session_authority.py),
[connection](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/gateway/session_controls.py),
[runtime store](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/hermes_state_runtime.py).

## Canonical dispatcher and replay extension

The probe now runs46 distinct cases: the original24 plus22 actual dispatcher
cases. It sets the upstream documented HERMES_TEST_ISOLATION flag before imports
and asserts the official registry's STRICT mode. Actual declared parameter and
result schemas validate every dispatched success. No handler or registry stub
replaces these modules.

The added cases exercise request-ID echo, nonmapping parameters, unknown methods,
foreign profiles, closed/missing mutation fields, missing authenticated-user
identity, runtime feature metadata without actor grants, read-only mutation
refusal and foreign LOCAL history refusal. Opening a preloaded synthetic session
preserves both messages and binds session/stored ID, authority epoch, revision,
generation, replay cursor and subscription. Repeat resume reuses that viewer's
subscription; another viewer cannot detach it. A foreign replay epoch requires
a snapshot and boolean cursors are refused.

The replay gap uses two declared status.update events and the actual bounded
SessionEvents ring. The reconnecting viewer first reads event1, event2 arrives
before resume, and resume catches up with exactly event2. No answered event is
duplicated. A fresh transport receives its own subscription; teardown releases
it. The original interrupted admission stays unknown, with one admission and the
original transcript throughout. No inference, tool execution or rename occurs.

The repository run passed46 cases with497 imported module hashes, strict schema
checks and no denied operation. Required Android tasks passed incrementally;
all70 prior reports and both original-key APKs remain byte-identical.179 policy
tests and two preflight refusals pass. These reused Android outputs retain their
original scope and do not become a fresh native or deployment run. The report identifies wire cases separately and
uses PASS_BOUNDED_REAL_AUTHORITY_WIRE_BEHAVIOR. This remains an in-process
component test with direct trusted actors and in-memory sinks. It is not HTTP or
WebSocket authentication, network frame ordering, actual native-login/profile
isolation, LOCAL restore/adoption or deployed acceptance. The fixture runner has
no active agent or adapters. Those required stages remain open.

## Running the probe

This implementation currently supports Windows. Use an existing compatible
Hermes interpreter and its dependency site. It installs nothing and does not
run upstream pytest. Both source and the fresh, nonexistent output directory
must be on E: on the owner's workstation. Pass paths as arguments; do not put
credentials or a production Hermes home in them.

```powershell
& $HermesPython -I -S -B scripts/compatibility_probe_session_authority.py `
  --source-root $PinnedHermesCheckout `
  --work-root $FreshFixtureRoot `
  --dependency-site $ExistingDependencySite
```

The probe clears inherited application/credential environment variables,
assigns all home/runtime/temp paths inside the fixture, disables lazy installs,
and uses isolated imports without site hooks or bytecode output. Two initial
Git commands inspect local source identity with optional locks disabled; after
that Python audit hooks reject process execution, nonlocal network operations,
unowned local ports and file writes outside the fixture. Asyncio can create its
own ephemeral loopback wakeup socket. Read access covers the fixture, pinned
source, explicit dependency site, interpreter base and probe itself.

Exact absent Windows `/proc` probes and the absent manifest beside the source
checkout may naturally raise FileNotFoundError only while still missing. This
exception does not allow reading a manifest that appears later. Python audit
hooks are a diagnostic boundary, not an OS security sandbox.

The private report includes all46 names,22 wire cases and imported module hashes.
Local execution on Python3.14.9 imported497 project modules, passed46 cases and
recorded no denied operation. The earlier24-case report remains separately retained. Reusing that environment does not certify every supported Python or
OS version. Preflight refusal checks also preserve an existing result and refuse
-O before upstream execution. Mandatory clean Android tasks, both lint variants
and179 existing policy tests passed for this source chunk;599 unique JVM cases,
no failures/skips/errors and lint0. App/test bytes match the previously verified
development pair. This component check is not yet part of hosted CI.

## Remaining acceptance

The runner has no active agent, adapters or producer. Existing live-session
fixtures do not exercise restore/mount/adoption, compression lineage, deferred
prompts or full resume effects. Direct trusted actors and an in-memory sink do
not verify HTTP/WebSocket login, actor derivation, ready grants, event ordering,
reconnect or replay cursors. A SessionHandle or runtime feature advertisement
still does not grant workspace or worker rights.

Actual server transport, deployed Pi authority, scoped canonical conversation
attachment, worker/file ownership and native approval/unknown-outcome behavior
remain separate required stages. The current operator connection keeps its
no-transcript/no-server-request boundary. No app RPC, backend, credential,
workspace write, model/default change or worker dispatch is added here.

The initial diagnostic fixture passed19 cases before a keyword-only getter call
in the probe failed. Read-only SQLite review confirmed that claim/recovery had
already committed an unknown receipt. That fixture and its code are retained;
the corrected probe uses a new database. A blocked discovery read was identified
as the absent E: manifest beside the test checkout, with no protected manifest
read. Neither diagnostic failure is relabelled as a passing run.
