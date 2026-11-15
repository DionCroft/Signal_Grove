# Full-inventory retirement receipts and actual SDK socket timing

Later source-preparation implementation now passes an independently verified
actual normal-SDK/native-role exchange with all 23,628 SDK inputs, one commit and
normal shutdown. The original failures below remain. Full executor/live acceptance
stays open. [Current exact proof and next stage](2026-11-15-prelogin-source-witness.md).

15 November 2026. The receipt-size defect is fixed and verified in the native
cohort. The new actual normal-SDK/native-service trial exposes a separate socket
lifetime problem before dispatch. The full SDK actor and executor remain
unaccepted; neither successful synthetic tests nor partial SDK login/shutdown
evidence closes those gates.

## Full verification with bounded receipts

The original actor put each full native file-identity map in one receipt row.
A new 1,006-file native fixture produces **160,736 identity-map bytes**, exceeding
the fixed 65,536-byte row limit. The old actor reproduces one error in 83 native
methods; its original begin/held receipt and zero HTTP requests are preserved.

The actor still reads and verifies every bound file and all native identity
fields. It now records each inventory's reviewed SHA-256, file count and digest
of the complete observed identity map. Before/after comparisons still cover all
identities, including ctime; this is not a content-only comparison. The large
fixture now completes with a **3,682-byte receipt**. A second new native case
rewrites identical source bytes after commit and correctly leaves the result
unknown without replay. The current exchange/source/owner/journal checks and
exclusive receipt rules remain.

Final controlled native result: **83 methods, no skips**, including 19 retirement
methods. Independent raw method/receipt/configuration/owner readback verifies
five effect receipts, two held receipts, five unknown receipts and one preserved
collision. Final Windows: **1,039 cases, 806 positive / 233 platform skips**.
Forty native-cohort units are independently stopped; the original failed
controller remains failed. These protocol-server fixtures are synthetic.

## Actual SDK/native-role trial and retained failures

`sdk_serve_retirement_fixture.py` starts the unchanged normal SDK server through
the existing isolated provider/lifespan fixture, in a fresh uniquely named native
serve role. The generated home uses a disabled local runtime, no fallback model
and an unreachable fixture-only inference endpoint. The process environment is
closed; loopback is ephemeral; a finite separate controller owns the journal and
shared real-root ancestry boundary. Other transaction prerequisites are explicitly
synthetic. No live home, real user credential, model or gateway is used.

The first attempt resolves the Python path to its base executable, losing the
committed venv identity. Normal SDK startup refuses with “no dependency
environment is committed.” It reaches no listener or login. That original failed
directory, controller/serve units and raw error remain. The driver now uses the
already-committed `packages.venv.environment/bin/python` entry from verified PM
facts. The fixture requires a venv entry and preserves failed cleanup state
without masking the original error. No repair/install or PM selection change
is performed.

The corrected attempt reaches the actual SDK listener and completes all eight
expected exchanges on one kernel-owner-verified socket: public health, an
unauthenticated identity rejection, supported provider/PKCE login, token exchange,
one-time-code rejection, token refresh and authenticated identity. The retirement
spec binds **all 23,628 reviewed SDK source files**, plus the executor inventory.

After full input verification, the actor refuses the pinned socket in `_socket`
**before its dispatch claim**. The original journal remains pending at
`serve_retirement`, with no native effect receipt or SDK commit call. No
retirement request is sent. The connection is closed and no successor connection,
automatic retry or replay is attempted.

The fixture's own cleanup stops only its exact generated serve role. Its native
result is success, zero main/control PID, zero restarts and empty cgroup. Ten
normal SDK lifecycle events include marker cleanup and local-runtime shutdown
returning before lifespan exit. No fence refusal, PM readiness warning, credential
value in logs or prior SQLite shutdown warning is observed. This is partial
normal-provider/shutdown evidence without an SDK retirement commit; the full
retirement/lifecycle and original warning-cause review remain open.

Independent readback across the two cohorts and two actual trial attempts checks
**44 temporary units**, all stopped with zero PIDs/restarts and empty cgroups.
Four original failed units retain that state. SDK inputs/caches, app/tool/support
facts, seven real directory modes and live service incarnations remain conserved.

## Timing evidence and next implementation

A separate read-only measurement verifies all 23,628 exact SDK inputs in
**14.663 seconds**. The selected runtime is Uvicorn **0.54.0**, whose existing HTTP
keep-alive default is **5 seconds**. The exact candidate's sole `uvicorn.Config`
call supplies no keep-alive override or keyword expansion. The server source SHA
is `46ecf0f02b194dd68de83ba0d7288f537b4465a164582b10c3c43cda8e5fd381`.

This measured mismatch is consistent with the preclaim socket-owner refusal and
shows that the current verification/connection ordering is unsuitable. The
original failing call's elapsed time was not instrumented; this measurement is
not an exact duration for that call. No SDK timeout is changed to make the trial
pass, and no source inventory is reduced to the six minimum protocol modules.

Next implement a typed, original-owner input-preparation boundary before login,
followed by fresh native descriptor/identity checks before the one claimed
exchange and after commit. Preserve full content verification, exact source/home/
executor/journal/approval bindings and source-drift refusal. Prove stale/raced
identity rejection and a positive actual SDK native-role exchange without
reconnect/retry or a widened SDK timeout. Then complete gateway drain, the unified
executor/reload/authenticated health and bounded runtime-only recovery.

## Source-specific CI and complete objective

The last independently verified hosted policy source876 has 1,037 Linux positives
without skips; these two new native cases need the new source's hosted result.
The completed source7e standard gate remains 1,142 API26 / 1,143 API37 methods.
Source8d's build artifact has 1,011 JVM cases and both lint variants verified;
its two API26 jobs now report success and the two API37 jobs remain running.
The complete new raw shard union is not accepted early. Twelve external-model
methods, original failure causes and final phone/original-key production gates
remain. Stable/main used-handler review retains 37 / 171 findings at the exact
previously inspected sources; no baseline/variant is changed here.

## Exact evidence identities

| Artifact | SHA-256 |
| --- | --- |
| scripts/native_serve_retirement.py | c3b13a4cd2ec446fdae5c9949a8fc953eb2bd8496a50ea88a277209ec778e9ba |
| scripts/tests/test_native_serve_retirement.py | a7bfaf371a91c65b793004cad3d89ddecc2feebb2219c403080104a22ac7252f |
| scripts/native/sdk_serve_retirement_fixture.py | e8e430501466f1d291b3c710fd02cf3a3f4fd0d9137e59eab5bf24d19f49bd8f |
| Independent native/SDK/timing reconciliation | c5a45d850eee09363fd6959c55f5a677c7aa28f42c2a818116f754b078aba47e |
| Independent 83-method raw/native inventory review | 257c1141f5a20bda0a3c3d4f0f0b4c04cf71f2dec5af77a3f272497bf262e216 |
| First SDK startup failure readback | 96dd16cc8d9ab8c97463d3581afa6d43de20da172fee3854998abed13e121fae |
| Corrected-entry SDK/socket refusal readback | 1900537f1f5501f9ed05383708e0292a71b3dfa84bf95221ce0658424144ec63 |
| Read-only exact-input/runtime timing measurement | 1d879f345f0e2453967a79e21e4fdaa2b6d15b565e3e47452f7b3e1a8b00b1c1 |
| Final Windows policy log | be8bd76513304117a9d0a2c865ff10030c219779a0e82788a72e5229a8233d13 |

Private paths, generated provider data, SDK copies and logs stay outside Git.
Android app/build inputs and published beta.9 are unchanged. The same full
[goal](../GOAL.md) and [audit ledger](../AUDIT_EXECUTION_PLAN.md) remain active:
all original upstream/shutdown, API26 cause, real project/workspace/worker/NAS,
automation/integration, phone/local-AI/security and signed-release requirements
remain. These isolated results authorize no live deployment.
