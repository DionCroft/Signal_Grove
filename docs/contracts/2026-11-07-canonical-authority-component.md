# Real canonical authority component regression

Reviewed 7 November 2026. `scripts/compatibility_probe_session_authority.py`
executes the real SessionDB, SessionAuthority, LiveSession and AuthorityConnection
from official Hermes v0.23.0, commit
`2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19`. It checks the clean checkout identity
and six reviewed Git blobs before importing it, then records imported module
hashes. It preserves the existing F0/source watcher and Android protocol.

## Verified behavior

A fresh SQLite database contains two synthetic transcript messages. Twenty-four
unique assertions exercise the real authority and runtime store:

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

The private report includes all 24 names and module hashes. Local execution on
Python3.14.9 imported426 project modules, passed24 cases and recorded no denied
operation. Reusing that environment does not certify every supported Python or
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
