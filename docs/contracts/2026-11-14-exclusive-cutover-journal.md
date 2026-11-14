# Exclusive durable cutover journal component

This preserves the earlier 39-method source checkpoint. The
[fixed coordinator/current journal](2026-11-14-fixed-cutover-coordinator.md)
adds explicit separate recovery records and durable authorization bindings; it
never resumes unknown forward steps. The earlier source/evidence is unchanged.

14 November 2026. `scripts/cutover_journal.py` implements the first persistent
transaction component. It executes no cutover or recovery action. There is no
service, network, policy, permission-change, database restore or replay API.
The full coordinator/native adapters/rollback and final live approval remain.

The caller must supply a previously observed native directory device/inode pair.
Opening compares that identity before creating any lock/journal files. The owner-
private directory must be mode 0700. Native no-follow opens, single-link regular
0600 files and a nonblocking exclusive kernel flock protect the evidence. Every
append rechecks directory/lock/journal identities and detects content replacement.
Full parent ancestry, approval and immutable plan binding remain driver gates;
private-directory validation alone is not a deployed protected-policy proof.

Rows use a closed schema with operation/plan/evidence hashes and fixed step/kind
names. Credentials, commands, free-form notes and unexpected keys cannot enter
the journal. Sequence, operation and plan continuity are checked. Input is bounded
to 1 MiB, 512 rows and 1 KiB per row. Each accepted append completes short writes,
fsyncs and rechecks identities before returning. The directory is fsynced when
names are opened. Hashes identify evidence; they do not establish its truth or
authorize an effect. Workflow ordering and actual effect verification belong to
the future coordinator. Completing this journal is not project/release completion.

An error or interruption poisons the instance, preserving partial bytes. Unknown,
pending or unfinished records block a new operation. Reopening grants inspection
only; it cannot append a step, verification, completion or unknown outcome for
the old operation. A completed history permits an explicit fresh operation with
a new identity, retaining prior bytes. There is no repair, reset, resume, retry,
automatic reconciliation or automatic rollback path.

## Actual validation

All **39 focused methods pass on the actual Pi with no skips**: fifteen parser
methods and twenty-four POSIX methods. The latter use real native locks, inodes,
directory/lock/journal replacement, hardlinks/symlinks and reopening. A separate
owned process verifies actual kernel lock contention. Explicit fault injection
checks short/zero/partial writes, fsync failure and interruption; those injected
failures do not claim a real disk failure. Completed operations retain earlier
bytes, and invalid ownership/modes are refused without permission repair.

Review identified and tightened two boundaries before final acceptance: reopening
must not grant old-operation mutation authority, and directory opening must bind
the caller's prior native identity. The earlier 37-method snapshot remains as
separate evidence; two new methods and the changed constructor are validated in
a fresh 39-method cohort after its predecessor ended.

The full Windows suite runs **681 cases: 654 pass, 27 POSIX skips**. Twenty-four
skips are these native-only journal methods, all actually exercised on the Pi;
three pre-existing POSIX skips retain their original scope. The original focused
import failure and later wrong-working-directory run (ten missing package imports)
remain failed logs. Explicit repository module loading and running the full
command from the repository root correct the harness without removing assertions.

Independent recovery matches all 39 named positive native results, exact source
hashes, terminal process identities and untouched SDK/live state. The reviewed
runtime retains 23,628 immutable inputs/modes, 41 generated caches, the repaired
tool facts and unchanged application facts. No original parent permission,
credential, user project, phone or published APK changes. Android/build inputs
are unchanged, so this Python component does not repeat or replace Android gates.

Next wire this journal into the fixed cutover coordinator with actual native
effect verifiers, explicit unknown-outcome reconciliation and runtime-only
rollback. Preserve all canonical old-stop/new-start PM, permission/policy,
backup, work-fence, live-user/phone and full audit/release requirements.
[Cutover packet](2026-11-14-runtime-cutover-packet.md) ·
[Prior readiness result](2026-11-14-staged-pm-readiness-and-sdk-pass.md).

| Evidence | SHA-256 |
| --- | --- |
| Journal source | `70f5d6005fd4d0052ff8c8ea615f912c7de4f4642570647a12e82fafa159e92d` |
| Journal tests | `989579d28fd4ea4137c2c1311902fdb71626572a36e3513de83c6c6804de5008` |
| Native terminal receipt | `2eb38152f69eedec053150ab0eb158c12f2bd270448f240b36a2fa2ea67a4461` |
| Independent native/source review | `ecd2502520ae4f4bfc39eab757b9a0f9673ff5605a36230f12c932f200674773` |
| Full Windows test log | `55d881709cd3d4647dc02b7d4f46289639d1fb57546da335803743d63a412ba9` |
