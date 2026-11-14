# Actual native service errors, late completion and replay refusal

14 November 2026. The current native user-manager adapter preserves immutable
last-observed attempt evidence after close/uncertainty. The new owned failure
fixture verifies five actual Pi scenarios in addition to the normal lifecycle.
This closes those specific component checks, not full deployment/recovery or the
original audit. Live services, files, modes, policy, credentials and models stay
unchanged. There is no new APK or live CLI/default approval.

## Attempt evidence is not completion or authority

`NativeAttempt` binds operation, authorization, exact role/stage/unit and native
preimage hash. Its phases are `claimed`, `accepted`, `job_done` and `verified`.
A claimed attempt has no job path; later phases require the exact native ACK.
Only actual correlated completion advances to job_done, and only verified
native process/configuration/helper outcomes advance to verified. The frozen
observation survives connection close and is available to the caller for its
durable receipt. It cannot issue approval, resume a journal or authorize retry.
It is not a durable substitute for the pending intent across abrupt process
loss; that original ledger still forbids replay and requires reconciliation.

The two new portable cases check closed roles, job/phase identity and refusal to
manufacture completion without an ACK. All **124 related methods pass on the
actual Pi with no skips**: 99 coordinator/journal and 25 service-boundary methods.
The complete Windows suite runs **766 cases: 699 pass, 67 POSIX skips**. All 64
coordinator/journal/watchdog platform methods run on the Pi; three older skips
retain their separate scope.

## Actual owned-unit scenarios

The fixture establishes pinned actor/independent observer connections before the
controller creates one exact owned transient gateway service per scenario. The
second role binding is owned and absent; live Hermes names are refused. Payloads
have finite native lifetimes. The observer retains the exact owned unit's loaded
evidence across actor close. Non-service prerequisite journal rows are explicitly
synthetic; no real backup, PM, work/admission or cutover claim follows them.

| Scenario | Actual observed result | Original intent |
| --- | --- | --- |
| Replacement adapter after claim, before effect | New adapter refuses; native process/configuration unchanged, no helper or dispatch attempt | Pending, unchanged |
| Reopened pending journal | New journal/adapter refuses even though no previous service action was sent; native process unchanged | Pending, unchanged |
| ExecStop exits 7 | Exact JobRemoved says done, but service Result is exit-code and helper status is 7; adapter holds unknown at job_done | Pending, never verified |
| ExecStopPost exits 9 | Exact JobRemoved says done, but service Result is exit-code and post-helper status is 9; adapter holds unknown at job_done | Pending, never verified |
| Job completes after the caller's deadline | Actor closes/holds at accepted; independent native snapshot still shows deactivation/control PID; helper later finishes and exact job completes successfully | Pending, never retroactively verified |

The late helper takes at least twelve seconds. Its actual finished timestamp is
after the actor's recorded uncertainty; independent completion/terminal native
evidence is retained without changing the actor's frozen attempt or journal.
Neither the deadline nor failed helpers cause another StopUnit, cancellation,
reset or automatic compensation. Refused cases expire naturally; no cleanup
service command is issued to force their result. Five unfinished ledgers and
bounded fsynced intermediate observations remain for exact readback.

This verifies why a completed stop job alone cannot prove successful cleanup.
The [systemd D-Bus contract](https://www.freedesktop.org/software/systemd/man/latest/org.freedesktop.systemd1.html)
defines asynchronous jobs and their result signal; the separate
[service contract](https://www.freedesktop.org/software/systemd/man/latest/systemd.service.html)
defines stop/post-stop helpers and cleanup after natural process exit.

## Independent review and retained failed fixture

The fresh normal lifecycle also completes six distinct jobs and refuses six
spent actions. Together with the three error/late jobs, nine exact job identities
verify. Across the normal lifecycle and five scenarios, all eighteen main/child
incarnations end, eighteen helper executions verify (including the two injected
nonzero exits), original cgroups are empty/absent and every owned unit unloads.
No live names are sent service commands.

Independent read-only recovery checks all 124 named positives, exact source and
raw hashes, ACK/signal/snapshot/helper correlations, pending/complete ledger
bytes and fsynced intermediate observations. It reconserves 23,628 SDK inputs,
41 SDK caches, 30 package sources/29 caches, app/tool facts, original seven
ancestor modes and live references. No source baseline is reset and no native
operation is replayed during recovery.

The first failure cohort passes its 124 unit methods, normal lifecycle and
replacement refusal. Reopening also refuses, but the fixture polls a full
snapshot across expected natural process exit; the conservative adapter returns
Unknown for that changing state. The entire cohort remains failed. Read-only
recovery proves all twelve created payload incarnations ended and all four
created units unloaded before a fresh operation is prepared. Logs and pending
history are preserved.

Only the fixture observer changes: bounded native MainPID/ControlPID plus the
exact completed-helper files establish a stable boundary before its final full
snapshot. It does not reconnect/retry a failed observation connection or relax
the production snapshot/success guards. Intermediate native evidence is fsynced.
The observer explicitly distinguishes done/failed JobRemoved outcomes under the
documented schema; the three actual injected/late jobs here all report done.
The corrected cohort uses fresh root/operation/unit identities and passes.

## Remaining full scope and next action

Next implement the reviewed native artifact/reference publisher and named mode
changes with exact preimages, preserved displaced data and owned-directory
failure tests. Canonical old-stop/new-start PM, fresh verified encrypted backups,
gateway/serve admission, reload, supported live-user health/history/graph and a
complete separately approved runtime-only forward/recovery run remain open.
The five scenarios do not accept all possible start, conflict, connection-loss,
manager-replacement or production recovery paths. Keep those boundaries explicit.
All original upstream used/transitive/shutdown, real workspace/worker/NAS/
adoption/integration/automation and physical-phone/model/security/release gates
remain active. Android/build inputs and published beta.9 remain unchanged.
[Runtime packet](2026-11-14-runtime-cutover-packet.md) ·
[Prior claim checkpoint](2026-11-14-one-use-native-dispatch.md).

| Evidence | SHA-256 |
| --- | --- |
| Current native adapter | `5e3ef1572138c6c9e47c6d07feafe7c409bd03a24d21025a0da73b9dd5a25de4` |
| Boundary tests | `39f1141071ba4ce8ff7a5793f7aed358b4a2af6bc161b529e16803bb337c2ec8` |
| Owned error fixture | `00493e547f16a4a2b3a282b314cd87752283137e012804d52462829770cb23e6` |
| Native terminal receipt | `47c1c6bfc0331377436c949cb072c2ebf7f1bfc59cad3f59a715420713b9327a` |
| Independent source/raw/native review | `8e6962ce6757b17a77040fb62668d5c6b6ae196be79cd4ffd915a190f258a6f0` |
| Preserved first failed cohort | `7a65283d612f1d9c1c6bdf4f2ae07858ea55e2e579ad5b7daa3172f029920e63` |
| Failed cohort terminal readback | `e339e9c5bab58754de11351bc56d832630a8c87d90a1713d76b218bb037a6408` |
| Full Windows suite | `08dc42ed1cb138069f07a7becd8ac7c523105293168ac7410b998e5cc294e546` |
