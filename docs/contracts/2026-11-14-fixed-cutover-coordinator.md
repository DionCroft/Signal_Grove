# Fixed cutover coordinator and separately reviewed recovery

14 November 2026. `scripts/cutover_coordinator.py` wires the native exclusive
journal into fixed forward and recovery stage orders. There is no live CLI,
default approval or default Pi operations backend. Concrete PM/backup/drain/
service/publication/health adapters and their end-to-end acceptance remain.
This component does not make the live packet ready for approval or activation.

The externally approved execution binds the exact packet, authority, scope,
operation, receipt hash and bounded native monotonic deadline. Its digest is
durable in the operation's begin row before any action. A hash is a binding,
not an approval issuer or proof that an observation is true. The production
caller must enforce the actual approval and authenticate native observations.

Each fixed stage has fresh closed observations before intent, again after the
durable intent, and after its effect. Native adapters must persist and verify
actual source/process-or-absence/files/receipts and authenticated SDK fences.
Every required flag must be a real boolean true; context/authority/scope/stage
and observation time must match. A slow intent write, changed journal inode,
busy/unknown work, model/data conservation failure or expired/backward/nonfinite
clock holds before further dispatch. An action's return/ACK is ignored; actual
effect proof is required. Native adapters must bound I/O and recheck exact native
preimages at mutation; the coordinator cannot preempt an unbounded callback.

The stage orders below are literal, with no caller-selected shell/command path.
Preflight and final verification stages only observe. Errors, lost replies and
interruptions preserve the existing journal and do not retry, resume, or invoke
automatic rollback. Reusing/reentering a coordinator is refused.

| Position | Forward | Separately approved recovery |
| --- | --- | --- |
| 1 | `preflight` | `recovery_preflight` |
| 2 | `backup_pre` | `recovery_backup` |
| 3 | `pm_old` | `rollback_gateway_drain` |
| 4 | `pm_new` | `rollback_serve_retirement` |
| 5 | `gateway_drain` | `rollback_stop_gateway` |
| 6 | `serve_retirement` | `rollback_stop_serve` |
| 7 | `stop_gateway` | `rollback_policy` |
| 8 | `stop_serve` | `rollback_runtime` |
| 9 | `secure_modes` | `rollback_reload` |
| 10 | `publish_reviewed` | `rollback_start_gateway` |
| 11 | `install_policy` | `rollback_start_serve` |
| 12 | `bind_gateway` | `verify_rollback` |
| 13 | `bind_serve` | `recovery_backup_post` |
| 14 | `bind_cli` |  |
| 15 | `reload_units` |  |
| 16 | `start_gateway` |  |
| 17 | `start_serve` |  |
| 18 | `verify_new` |  |
| 19 | `backup_post` |  |

The fixed forward order requires backed-up and verified old/new canonical PM
before admission fences; both old processes stop before permission/policy/
reference installation and reload. Final health/auth/history/graph proof and
the post-change backup are separate gates, not inferred from a process start.

## Explicit recovery, never continuation of unknown forward work

The journal now accepts an explicit `recovery_begin` record only after an
unfinished/unknown operation. It binds a new operation and recovery packet,
exact original operation/plan and SHA-256 of the actual prior history bytes.
CRLF bytes are hashed as stored. Root plan and original authorization identity
are retained through later recovery attempts. A recovery row cannot discard or
replace old rows, and recovery operations can dispatch only recovery step names.
An interrupted recovery needs another fresh review; it never resumes itself.
Malformed/partial journals remain refused without repair or truncation.

Before this new record, the coordinator requires a matching separate recovery
approval, the reviewed rollback packet, fresh exact native effect inspection
and a matching approved current snapshot. Runtime-only and no-work-replay proof
remain mandatory throughout recovery. Databases, newer conversations, model
state, credentials and user work are preserved. Permission rollback is not
automatically inferred; the concrete packet must review its named disposition.
The generic ledger still supports old unauthenticated fixture rows for readback;
the coordinator cannot use them as an original authorization for recovery.

## Verification scope

All **95 focused methods pass on the actual Pi with no skips**. They use the
real exclusive journal, fsync, inodes, native files and competing-process lock.
SDK/manager/work/approval observations are explicit synthetic unit seams, with
real private file writes to check the orchestration/effect boundary. They are
not an authenticated two-service cutover or accepted production host adapters.
Earlier normal SDK provider/retirement/lifespan evidence remains separate.

Coverage includes full independent literal stage orders, durable approval
binding, every forward action failing after its effect, pre/post failure,
work arriving after intent, stale/wrong-context observations, action returns
without effects, source/model/data gates, deadline/clock failures, native
name replacement, interruption, reuse/reentry, exact-history recovery and a
failed recovery. Native unit data is retained through compensation fixtures;
no real user database or model is modified by these tests.

The full Windows suite runs **737 cases: 676 pass, 61 POSIX skips**. Fifty-eight
are coordinator/journal POSIX methods actually exercised in the Pi cohort;
three pre-existing platform skips retain their original scope. Independent
recovery checks all 95 named positive results, exact four source hashes and
terminal owned-process identities. Runtime source/native modes (23,628 inputs),
41 generated caches, repaired tools, application facts, seven parent modes
and live references conserve. A local reviewer-transform count assertion fails
before SSH and is corrected against the actual source; no native test reruns.

Next implement the concrete native operations for the reviewed Pi packet,
including supported canonical old-stop/new-start PM, fresh encrypted backups,
native gateway/serve admission fences, exact service/file/reference outcomes
and supported live-user health/history/graph checks. Test actual adapters in
owned isolated fixtures and review the complete packet before separate live
approval. Preserve all original upstream/project/worker/NAS/integration/phone/
release requirements. No Android/build input or published APK changes.
[Packet](2026-11-14-runtime-cutover-packet.md) ·
[Earlier journal checkpoint](2026-11-14-exclusive-cutover-journal.md).

| Evidence | SHA-256 |
| --- | --- |
| Coordinator source | `da7f6855b636f9853834c3713b273225b45c244f42dad847cb5a25e35ed43560` |
| Coordinator tests | `510993b73ecd254df5004f681ad234a9c0a000ab532c495eded6f6edfe486ef4` |
| Journal source | `761e39affb8099443b17a5900470e7dca96bedb65bd5839d8d4ff7adef558b4c` |
| Journal tests | `7c51a22bb829db7c6b681068754619da3b0b9d703df5482af7332792d8864708` |
| Native terminal receipt | `ca73d0b9df6a514a16cd602e6ef02dddcc475afe5c4812f4a66494e723a180f3` |
| Independent source/raw review | `db85b172befa79073c9bc786b4f2639a3a110bcea5fcc98df581e650b07a9fa6` |
| Full Windows suite | `fd6edc3360a8add9b1abbb8bd9523fdf5a3241ccfd6702ae75a26fb16e01459c` |
