# Once-only private backup publication and Windows identity correction

Follow-up: the [complete one-use backup stage](2026-11-15-backup-transaction.md)
now composes this component with actual SDK/age evidence under isolated fixture
scope. This contract retains its original publication-only scope and hashes.
Real private GitHub/live-home/external-provider acceptance remains open.

14 November 2026. The private Release publication component and archive verifier
pass a combined **58 Pi methods without skips**: 32 publication/transport/schema
cases and 26 archive cases. Windows: **963 cases, 774 pass/189 platform skips**.
Of those platform methods, 186 have scoped Pi evidence; three older cases retain
their original separate scope. Cohorts overlap older evidence.

The publication component is implemented; the complete production backup stage
and actual private GitHub interoperability remain unaccepted. No live home is
archived, production key read, actual GitHub request sent or service changed by
these fixtures. Android inputs and the published beta.9 APK remain unchanged.

## Exact component boundary

`scripts/private_backup_release.py` accepts an already claimed `backup_pre`,
`backup_post`, `recovery_backup` or `recovery_backup_post` stage. It binds the
original journal owner, exact pending prefix, approval, packet/authority,
forward/recovery scope, finite native controller/configuration and shared
operational path witness. A reopened journal cannot take ownership. Production
paths require the real-root guard; owned fixtures are explicitly scoped below
their generated private root.

The caller supplies the reviewed immutable publication specification and an
exact encrypted archive/checksum pair. Operation/stage-specific literal names,
private directory/file identities, regular file type, owner, single link,
size/hash, age header and exact checksum line are checked. Every asset must be
under 2 GiB; larger inputs refuse before file hashing or network activity.
This limit follows [GitHub's release asset documentation](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases).
ZIP/content/snapshot selection, encryption and key provenance remain the complete
backup transaction's responsibility. An age header alone is not encryption proof.

An exclusively created, fsynced publication receipt precedes HTTP activity.
Original pending-stage ownership is still required, and an existing receipt
prevents replacement instances from replaying publication. The component checks
the pinned repository name/numeric ID/private flag, existing release ID/tag and
upload endpoint before effects. It inventories existing names and refuses any
collision. Each of the two assets gets one POST, preceded by a durable intent.
There is no release creation, asset deletion, clobber, retry, resume or retention.

A successful response must identify the exact uploaded asset, size and digest.
Fresh metadata is read, the asset is downloaded to an exclusive private file,
then streamed size/hash, native identity and disk bytes are verified. Final
inventory must contain the exact unique pair. Failed/starter/ambiguous uploads,
partial downloads or changed inputs remain held with their original artifacts
and receipt. The component does not compensate, regenerate or infer absence
from a failed response. These rules follow [GitHub's asset API](https://docs.github.com/en/rest/releases/assets?apiVersion=2022-11-28),
with repository identity read through the [repository API](https://docs.github.com/en/rest/repos/repos?apiVersion=2022-11-28)
and release identity through the [release API](https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28).

The transport uses normal certificate/hostname validation, the pinned REST
version, exact API/upload hosts and bounded socket/metadata/body reads. It has
no proxy/base-URL/environment fallback. One HTTPS redirect to either reviewed
GitHub asset CDN is allowed; credentials and API-version headers are stripped
on that hop. Signed URLs and response bodies do not enter receipts or errors.
The credential is caller-supplied and redacted; this module discovers no secret.
The reviewed specification retains only its binding digest in private evidence.

Success reports `effects_observed`, downloaded-byte/private-target verification,
and explicitly false snapshot/encryption/fresh-backup assurances. It does not
mark the journal's backup stage verified. Only the complete independently
verified backup transaction may provide `fresh_backup_verified`.

## Actual acceptance and preserved failures

The 32 publication cases comprise 13 portable cases and 19 Linux cases using
real protected journals, descriptor ancestry and an actual finite user-manager
unit. Native cases cover the four backup stages, exact ownership/controller,
ancestry/deadline drift, private identity, collisions, starter acknowledgments,
unknown first/second uploads, corrupted downloads, redirect credential scope
and refusal of actor/replacement replay. A local kernel TCP server supplies
synthetic GitHub metadata. The connection factory is explicitly replaced with
loopback HTTP in the fixture; actual GitHub TLS, authentication, permissions and
remote error behavior remain a separate acceptance gate. The production TLS
context is checked independently, not represented as a real remote handshake.

One native case copies only the ciphertext from the previously independently
verified unmodified SDK/age fixture. Both uploaded/downloaded files match the
original ciphertext and checksum; the original is unchanged. It reads no fixture
key and creates no new snapshot. All non-publication journal prerequisites are
explicitly synthetic. Earlier actual SDK/age/content evidence retains its exact
source scope in the [archive contract](2026-11-14-backup-archive-verification.md).

The first isolated cohort stopped because the staging harness omitted a policy
dependency. The next stopped because a new test expected `Refused` where the
existing journal correctly reports ancestor drift as `Unknown`. Both receipts
and failed units remain; corrected cohorts use distinct private roots. The first
Windows wrapper omitted the repository import root. Two later independent reviews
finished native readback but failed local log parsing when an intentional client
abort emitted background socket tracebacks between skip lines, including a
Windows-specific aborted connection. The local size-bound test now uses a
deterministic byte sink; real ambiguous-upload cases still use loopback TCP.
The server handles only explicit disconnect exceptions. None of these failed runs is
silently changed to a pass or replayed as a production action.

## Reproduced Windows archive defect

The final Windows run exposed an intermittent refusal in the existing private
SQLite image check. Direct native observation reproduces it: the open handle
and named path have the same device/file index, type, owner, link count, size,
mtime and explicit birth time, but disagree on deprecated `st_ctime_ns`.
One reports change time while the other reports creation time. Python documents
that Windows creation time is [explicitly available as `st_birthtime_ns`](https://docs.python.org/3.14/library/os.html#os.stat_result.st_birthtime_ns)
and [deprecates Windows `st_ctime_ns`](https://docs.python.org/3.14/library/os.html#os.stat_result.st_ctime_ns).

Windows image comparison now uses the explicit birth timestamp; its absence
refuses. Device/file identity, type/ownership/link count, size and mtime remain
bound, and all CRC/content/SQLite checks remain. POSIX still binds metadata
change time. Four regressions cover this exact disagreement, missing birth time,
continued native/content binding and unchanged POSIX behavior. The old source
refuses the observed fixture; the new source verifies 250 fresh generated
SQLite images without a timestamp refusal. The full 26 archive methods also
pass on Pi with no skips. The failed run, observation and retained image remain.

Independent final readback verifies all 58 exact positive method names, final
source hashes, native receipts, both downloaded files and the original fixture
ciphertext. Every involved owned unit is stopped with an empty cgroup, including
the failed units. It verifies all 23,628 immutable source inputs, 41 SDK caches,
support environment, existing facts, seven real modes and live service
incarnations. No raw paths, manifests, keys, ciphertext or credentials enter Git.

| Evidence | SHA-256 |
| --- | --- |
| Private publication component | 1a3b9c884cdbe4076fcef23b2a9aa3241a6961b99a636d2ea81644a2b46d4864 |
| Publication regressions | f0a77faa35a3c17e5b7533742b6a26d89d1db5b5d2a60cc179ac65f8c7a86df1 |
| Archive verifier | 525bcd5a99e977c37967db57e4a1a3a3cdfdf6b8dba780b584151eb9e24bf695 |
| Archive regressions | 62f6fdf1facf02d337a95b9eb32f81f5c166cf9fc50daee7ccfac198f3ab5459 |
| Independent final native/raw readback | be1ac7ef53fd262f5d62fb3e88247420559bc4d4427ed9a7823e30a1c72fc0c9 |
| Windows mismatch observation | 62cc26d0e3c699e28a8c0a12d3ecb34bf7f60479e9db5535abf444408fa6b4df |
| 250 corrected fresh-image observations | 4698d7b8a290d18e47326656f093630124ef648892fb9f80b3907f4800df8451 |
| Final Windows suite | 56f2aef2e28422d10a17be2c24814f4c36fdf46e32174a4c9ca6ca7d88fee30c |

## Required next stage

Compose the complete one-use backup stage around these components: bind actual
SDK/interpreter/age/key inputs and output preimages; perform supported snapshot
selection/SQLite backup; explicitly handle external memory-provider selection
errors; verify exact archive contents and encryption/decryption round-trip; then
invoke this publication segment and reconcile its evidence. Do not regenerate
unknown backups or promote publication-only success to a fresh-backup claim.
Actual private GitHub acceptance and operational recovery remain open.

Then finish admission/retirement, manager reload, authenticated health/history/
graph and full runtime-only forward/recovery. Both canonical PM contexts remain
unprepared, seven real modes remain 0775 and live activation still needs the
complete concrete packet and separate approval. Every original API26, upstream
used/transitive/shutdown, project/workspace/worker/NAS/automation/integration,
phone/local-AI/security and original-key release requirement remains active.

[Full runtime packet](2026-11-14-runtime-cutover-packet.md) Â·
[Shared operational guard](2026-11-14-operational-ancestry.md).
