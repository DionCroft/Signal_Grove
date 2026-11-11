# Authenticated native-owner workspace transport

This checkpoint closes a narrow part of the real-source workspace gap. It is a
native-Windows conformance probe, not an Android networking or server feature.
The separate [empty-owner bootstrap probe](2026-11-11-owner-runtime-matrix.md)
remains available with its original no-session scope.

## Actual authority and separate-channel readback

`scripts/compatibility_probe_owner_workspace.py` accepts only the two exact clean
execution pins `2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` and
`3b421578588243f920886e6fe79a6faa87ea026e`. It supervises exactly one fresh
isolated native-Windows child, using existing dependencies and E: homes, locks,
logs, workspaces and databases. It starts the actual GatewayRunner, authorities,
control socket, API/dashboard lifespan and normal ready publication. It never
supplies a synthetic trusted Actor, grant, runner or dispatcher. Windows DACL,
SID/session verification, native control ticket minting and real canonical
WebSockets remain the production implementations.

After the original two-profile authentication/one-use-ticket checks, an actual
owner socket creates one disposable GUI session through `session.create`. Its
explicit synthetic custom-provider route is an unserved loopback endpoint; no
inference or model readiness is attempted or claimed. It has no enabled toolsets,
no real credential and no session admissions or transcript messages. No production
profile, model default, Pi, Spark or worker configuration is touched.

A separately authenticated native-ticket channel sends one exact revision/
generation-fenced `session.mutate` workspace request without attaching/resuming
the session on that channel. Its ACK must name the exact session, operation,
directory and next revision/generation. After that channel closes, another fresh
native-ticket connection explicitly attaches the session and must independently
observe the new directory and the same fences, with empty history and no running
or pending work. This is actual readback, separate from accepting the ACK.

Nine named workspace checks pass: authenticated creation; fresh-channel ACK;
separate fresh-attachment directory observation; an explicit finite server receipt
replay with no second mutation; changed-payload/same-key refusal; stale revision;
stale generation; foreign-profile mutation; and missing-directory refusal.
The explicit replay verifies server idempotency only. It does not authorize an
application to retry unknown remote work automatically.

## Terminal evidence and remaining shutdown finding

The exact tracked probe SHA-256 is
`0167a1fd1c00dc4479da46bf48c2e8faf41e2c1b483c12877084f2c48207e02a`.
Fresh tracked attempts 188/189 import 1,361 / 1,395 independently Git-bound source
modules and have zero denied boundary events. Only the exact four known Git
metadata commands against the two disposable fixture directories are additionally
permitted, with a real `tui_gateway.git_probe` caller; unrelated subprocesses stay
refused. This is a Python audit boundary, not an OS sandbox.

After actual child exit the supervisor opens SQLite read-only. Both profile
stores pass quick_check. The default store has exactly the expected one session
at the expected directory/revision/generation and one durable mutation receipt
whose full result equals the observed ACK. The sibling store is empty. Both
stores have zero messages, admissions and rooms; the PID file is removed. No
fixture is reused and an observation timeout preserves its PID/root as unknown
without killing/replaying. Prototype attempts 185/186 independently reached the
same scoped core finding; they do not replace the tracked-source executions.

Both tracked executions return **2 / CORE_PASS_SHUTDOWN_FINDING_OPEN**. The final
exit observer retains the upstream reaper's backend-heartbeat write after cached
DB closure/reopen warning. The warning, reaper, teardown and acceptance assertions
are not suppressed. A successful workspace exchange does not close lifecycle
acceptance. The complete script-policy suite still passes 232 tests. Android
Kotlin/native/fixture sources remain unchanged from `cf209598bd81bca9f926225b56da270049bbb93a`;
no fresh Android or physical-device pass is inferred from this probe.

Reproduce with existing native-Windows Hermes Python and dependencies:

```text
python -I -S -B scripts/compatibility_probe_owner_workspace.py --source-commit <one-exact-pin-above> --source-root <matching-clean-E-checkout> --dependency-site <existing-site-packages> --work-root <fresh-E-output-root>
```

## Hosted reporting fix verified

[Feature-branch canary run 43020604275](https://github.com/DionCroft/Signal_Grove/actions/runs/43020604275)
executes the exact `b377d94dd1bfbd030ad04b2e1b49a5656c131abf` workflow. Both stable
and main inspections now run and produce retained reports even though each exits
2 requiring review. Parser, pinned-registry and pinned-sidecar jobs pass. No
baseline/variant waiver or failure suppression was added.

Both downloaded ZIPs match their GitHub digests. Stable resolves to `3b421578588243f920886e6fe79a6faa87ea026e`
with 47 changed used-handler entries, no changed watched request fields and
1 / 11 added API/management routes; ZIP SHA-256 is
`a11dd389317a9406c46bb8e41cef7f094ebe2be1de6d53bf1582df3aef764b06`.
Upstream main resolves to `79fb37f8a9e74ad09f63227ebc069c98e84855ea` with 86 changed
used-handler entries, no changed watched request fields and 1 / 12 added routes;
ZIP SHA-256 is `0189161d31bb35447c7eddad6791146c34ba1b523c9fdb61d08a68da312a3cf6`.
There are no removed used routes/fields in either report. These are static review
findings, not counts of proven mobile defects or complete compatibility passes.

## Remaining acceptance

Android's actual HTTP/browser sign-in, profile ticket and repository/Room path
must be exercised against this real owner. Lost ACK, cancellation/owner loss,
real busy/unknown-worker state, permission boundaries and deployed-version
acceptance remain open. This native-owner probe must not be combined with the
separate mock-Android proof to claim that end-to-end acceptance. The app's receipt
still says acknowledged until its own independent readback exists.

Then finish verified worker/project identity, authoritative working-copy binding,
file access and reviewed dispatch. A Hermes-host directory does not establish a
Windows/NAS execution owner. All original [audit rows](../AUDIT_EXECUTION_PLAN.md),
phone-to-Hermes handoff, physical current-source checks and signed-release gates
remain required. No production APK is published here.
