# Guarded standalone retirement client and old-stop PM prerequisite

14 November 2026. This is a tested deployment component, not a live deployment.
The complete audit, original four priorities and signed-production acceptance
remain active. No live service, policy, grant, permission, model or APK changes.

## Exact process and protocol safety

`scripts/retirement_guard.py` consumes the unchanged SDK standalone-serve
identity, idle and retirement routes. It requires an explicit authenticated
request adapter, positive native PID/start-tick/boot identity observations and
a durable exclusive transaction-journal adapter. It has no CLI, installer,
service-stop command, authentication fallback or retry loop. Production adapters
and the complete cutover executor/rollback are still required.

The client checks the native process before/after every exchange and again after
the request-intent journal. The request adapter must pin its connection to that
exact process, disable reconnect/redirect/retry, bound time/response size and
retain TLS validation. A file, URL or HTTP PID alone is not that adapter proof.
Each action records intent before dispatch. Journal failure, unknown request,
operator interruption or identity change holds the operation without replay.

Authenticated identity and a positive idle diagnostic allow only a prepare
request. Busy/indeterminate diagnostics or a prepare refusal do not create a
permit. A successful prepare requires the actual process-owned token; only that
guard's unspent opaque in-memory permit can be committed or cancelled. The local
25-second bound is shorter than the pinned SDK's 30-second lifetime, is checked
after the intent write, and is not extended by a slow response. A committed or
uncertain request cannot be cancelled/reissued automatically. Permit credentials
are absent from object representations, exceptions and journal events.

All 29 client safety tests pass inside the complete Windows portable suite:
605 cases, 602 passed and three actual-POSIX skips. The skips are existing platform
cases; no new guard test skips. Covered failures include HTTP 401/no fallback,
wrong/boolean owner fields, busy/unknown work, stale/native-reboot identity,
timeout/reset/interrupt, malformed responses, expired/backward/nonfinite clock,
slow intent journal, unconfirmed commit/cancel and journal failure. These are
client safety cases, not proof of a signed-in live dashboard or a pinned socket.

## A prerequisite applies to stopping the old loaded unit

The current gateway's 90 override changes its start command to an explicit old
application interpreter. Its loaded ExecStop and ExecStopPost still call the
old SDK-published launcher. The observed canonical home has that old source's
bootstrap/lock directory but no committed PM facts; the old generation has its
own selected application facts. PM's dependency facts are scoped to the active
data home; borrowing a tool store is not a committed environment for that home.
Lazy installation is disabled in the loaded unit.

The review packet now explicitly requires supported old-stop-launcher PM readiness
under the canonical home as well as new-source startup PM readiness. Provisioning
either remains a separately approved live action; do not copy/alias facts or use
an absent-facts shortcut. Confirm both exact home/source/environment bindings
before draining/stopping anything. Old loaded units must still stop before
reload or reference changes. The read-only renderer's new required gate changes
no wrapper/unit bytes; the updated packet must be rendered/verified before use.

## Isolated route check and accepted native outcome

At source 22dcd76, `scripts/native/retirement_route_fixture.py` now passes on the
actual Pi using the exact candidate application's unchanged ASGI routes,
a fresh fenced home and in-memory fixture authentication, actual kernel process
identity and fsynced journal. Real SDK admission acquire/release calls cover
work arriving after a diagnostic, prepare/cancel and prepare/commit. No SDK callback is patched, listener/lifespan started or live host state changed.

This check is in-process ASGI with the loopback token seam. It does not
prove native TCP connection ownership, live provider sign-in or full server
startup/shutdown. HTTPX explicitly leaves lifespan events outside its ASGI
transport; that is why this route-only scope is stated separately.
[HTTPX transport reference](https://www.python-httpx.org/advanced/transports/).

Independent raw recovery verifies all 41 native unit cases (29 guard and 12
renderer cases, no skips), six separate ASGI outcomes, 12 exchanges and a 28-row
fsynced credential-free journal. Actual SDK admission is held after work arrives,
closed by prepare, reopened by cancel and closed permanently by commit. A spent
permit causes no additional HTTP exchange. The child exits 0 without pytest;
742 loaded SDK module identities match the complete unchanged 23,628-input/native
mode map. All 41 native bytecode outputs, selected PM facts, live references and
permissions verify. All owned check processes have ended.

The first controller fails with Linux E2BIG after the unit suite passes and
before an ASGI child starts. That failed receipt and empty child logs remain.
Only the controller metadata handoff changes from a too-large argv value to a
private file. The original unit suite is not repeated, and no SDK or committed
fixture assertion changes. The ASGI child runs once. This positive component
result does not prove a live provider session or a pinned native TCP connection.

A separate verified-listener GET reads the live public provider list: it offers
username/password sign-in. It reads no credential and requests no login, token,
retirement permit or mutation. A normal supported sign-in remains necessary.

The live dashboard's earlier read-only owner-token request still returns 401
under its active provider gate. No native ticket/browser credential is substituted.
Supported provider-session authentication, the complete bounded transaction and
rollback, fresh encrypted backups, canonical PM, named permission/policy review
and separate idle-only live approval remain before production activation.

The original API26 failure cause, upstream used/transitive compatibility,
real project/workspace/worker/NAS/adoption, automation/integration and final
phone/model/release acceptance remain in `docs/AUDIT_EXECUTION_PLAN.md`.

## Evidence identities

| Evidence | SHA-256 |
| --- | --- |
| Complete Windows portable log | 0c04e74b7bb4f356782dc717ed522447e104ba5ba3bf026c9d6e5fda622567b2 |
| Guard source | fc30f1e7013dd10f1904d1554b86e85f4326b3814cc7e7edbf98624f65082f71 |
| 29-case client suite | 7f00b5fd56d1af06d8c3b4d3c168f6a83fac39946104884c321a964eefbbc96d |
| Planned ASGI fixture source | 148b93626f3d2f130624740d160c83386408e5b8263c19c8277b3ec7eac3af25 |
| Independent native raw/source review | 1524f6cc0d605a7145448e93fefb568a6b205978671bd55ea4e36be3533819b2 |
| Native 41-case unit log | 7eec315174cbea95676f77d70a1ad4173030e5eaadde740dfc3a95395fac705d |
| Actual SDK ASGI result | 67c56833ba6831d0d43e0bfb37a3e8e3e3f12bd699228019a810c148556dd043 |
| ASGI source/module report | 20a3c936d90d8081d11257f088df0c8837a0c0eeff81a6a81cfb75a2afeae887 |
| Native credential-free journal | fd1e8f9c776356f0a514dea3d6e329f67dae0fb1f4f9f78a5c47e2fb5128826f |
| Live public provider read | 5dec7e99881eecc849b594a6b64b9bb136eacd71888db6f5d810a2e25d5107b2 |

The [subsequent single-socket HTTP component](2026-11-14-pinned-retirement-http.md)
adds a tested existing-connection adapter with no reconnect/retry/login/refresh.
Its 18 real TCP cases and 623-case portable suite use synthetic native-owner and
provider seams. Actual kernel socket ownership, supported provider sign-in and
the complete bounded executor/rollback remain prerequisites. No live action is
authorized or performed by that source/test addition.
