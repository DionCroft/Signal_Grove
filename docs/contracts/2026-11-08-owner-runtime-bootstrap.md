# Actual owner-runtime bootstrap contract

The [11 November matrix checkpoint](2026-11-11-owner-runtime-matrix.md) adds an
explicit second immutable execution target. The original default and all scope,
shutdown, supervision and no-replay requirements below remain in force.

This is finite Windows conformance tooling, not an agent backend or deployment.
It adds no application RPC, approval, credential, model selection or scheduler.
It uses the existing interpreter/dependency site and a fresh ordinary E: root.
All fixtures, logs, account/runtime/lock namespaces and databases stay there.
Normal source and dependency locations are read-only.

## Primary source and actual runtime

The immutable reviewed runtime is
[`2bd378e`](https://github.com/NousResearch/hermes-agent/tree/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19).
The probe refuses a different/dirty checkout. It reserves both real profile homes,
claims the ordinary runtime and host-role locks under the supported isolated
`HERMES_GATEWAY_LOCK_DIR`, constructs `GatewayRunner`, calls
`initialize_gateway_runtime`, starts the official control socket/API and real
dashboard lifespan, then runs `runner.start` before official ready publication.
It neither substitutes descriptors nor patches peer verification or dispatch.
The fixture omits scheduler/housekeeping orchestration, messaging adapters and
turn-machinery warmup through its documented disable setting. This is deliberately
narrower than complete `start_gateway`, inference/tools or a deployed fleet.

Native control uses the production current-user DACL and SID/session verification.
Tickets come through the actual control pipe. An observed grant has the actual
host-owner SID and profile scope. Real canonical WebSockets verify each returned
profile, instance and epoch; empty room reads succeed for both authorities and
foreign profile selectors fail. A redeemed ticket's second upgrade returns 403.
After official runner/control teardown and lock release, discovery finds no ready
owner or fallback. No ensure/start-on-demand helper is called by those reads.

Hermes's `gateway/owner_identity.py` explicitly treats authenticated sign-ins as
the machine owner. A dashboard login is not a separate tenant. Seeded actor tests
must use the actual owner comparison and truly foreign non-owner subjects;
profile-bound sockets still cannot read sibling authorities.

The audit guard allows reads only within the isolated root, immutable source,
existing dependencies/interpreter and the probe file. Writes and SQLite are
confined to the fixture; sockets are ephemeral loopback and connect only to owned
ports. Process/registry writes, arbitrary subprocesses, external connections,
process kills and extension loading are refused. Exact harmless version/source
metadata subprocess probes are intentionally blocked and recorded separately.
Absent Linux metadata probes are allowed only at verified absent E: paths.
All 1,167 imported Python modules independently match their immutable Git blobs.

## Terminal results and reproduction

Use an existing native Windows interpreter with matching installed dependencies:

```text
python -I -S -B scripts/compatibility_probe_owner_runtime.py   --source-root <immutable-Hermes-source>   --dependency-site <existing-site-packages>   --work-root <fresh-E-output-root>
```

The supervisor starts exactly one owned child and retains its PID, hash-bound
script, log and terminal code. It never reuses a root or retries an uncertain
attempt. A timeout is unknown and requires inspection of that exact PID/root.
After child exit it checks read-only SQLite integrity, zero chats/rooms/admissions,
removed PID identity and the final exit observer's diagnostics. Exit 0 requires
all those gates; exit 2 means core transport passed with a shutdown finding.

The exact repository probe returns **2 / CORE_PASS_SHUTDOWN_FINDING_OPEN**.
Both profile stores pass quick_check with zero sessions, hosted rooms and
admissions. The last exit observer captures a genuine upstream sequence:
`tui_gateway.session_reaper._atexit_clear` calls `clear_backend_heartbeat` after
canonical runner shutdown closes its cached DB, triggering the post-close write
reopen warning. Its exact stack is retained privately. No handler, reaper flag,
assertion or diagnostic was suppressed to make lifecycle green.

Earlier experiments remain evidence: the first failed an invalid fixture-only
status-publisher call and strict metadata boundaries; the second completed empty
reads but exited nonzero on an unrecognized absent PID-1 probe. The third passed
the core with a post-exit warning. The fourth added negative transport checks and
captured the exit stack. They are not relabelled as full passes. The repository
supervisor now makes the final shutdown finding explicit and nonzero.

Required fresh Android checks are 836 JVM cases / 82 XML, both lint variants 0,
and 229 policy cases. Both original-key APKs are byte-identical to the previous
caption proof. No new Android native executions or deployed acceptance is counted.

Next add actual seeded same-ID/profile-private rooms, actor-filtered opaque paging,
installation-shared/handed-root merges and the outer authenticated forwarding
chain. Complete full orchestration/exit and all other audit rows separately.
