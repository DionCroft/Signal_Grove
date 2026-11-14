# Native file publication and named mode component

14 November 2026. `scripts/native_file_publication.py` implements exact native
file/link publication, retained-entry recovery and the seven named mode roles.
All 37 methods pass on the actual Pi with no skips. The component requires
existing parent directories and an externally reviewed exact catalog. It has
no CLI, renderer-to-live catalog loader or directory-creation implementation.
No live path, permission, service, policy, credential, model or APK changes.

## Native preimages and scope

Only ten closed roles/names can bind: the four rendered source artifacts, two
runtime wrappers, policy, two 100 drop-in links and the stable client reference.
Each fixed stage requires its complete role set, exact directory device/inode/
owner/group/mode and a bounded file/link preimage. No arbitrary filename,
recursive operation, ownership change or deletion API exists. File bytes are
bounded to 1 MiB and are hidden from repr/evidence; receipts contain identities,
hashes and literal link metadata rather than payloads. Policy schema/grant
validation remains the real caller's separate responsibility.

Selected parents are opened with native directory/no-follow/close-on-exec flags
and checked against both descriptor and actual name. File reads reject special
nodes, foreign owners, hardlinks, changed identity, incomplete/oversized reads
and unsafe modes. Symlink observations do not follow their targets. Regular
file observations bind owner/group/mode, size, mtime/ctime, inode and full hash.
Publish parents must be 0700/0755. Full protected ancestry/work/source/backup
proof is a separate production gate; this does not weaken the policy reader.

The exact catalog digest must be supplied from the externally reviewed packet.
A freshly computed hash alone is not approval. The original live journal must
have the exact authorized pending stage and full fixed verified prefix before
one dispatch claim is consumed. Replacement, fork and reopen cannot reclaim
it. The component never marks a journal stage verified; the production caller
must verify its complete native effects and all other real gates independently.

## Atomic publication with retained data

Prepared files use exclusive creation, exact bytes/mode and fsync. Literal
links are prepared without following either entry. Kernel no-replace rename
publishes a previously absent name; exchange replaces an existing entry while
retaining the displaced inode under the recorded incoming name. Directory
fsync and exact native postimages follow. Only rename's legitimate ctime change
is excluded from displaced-entry equivalence; other identity/content metadata
must match. There is no overwrite fallback if the kernel/filesystem lacks the
required operation.

A concurrent creation is preserved by no-replace refusal. An exchange race
retains the actual displaced concurrent content and holds with both names
available; it never automatically restores over a newer edit. Existing correct
entries keep their original inode. Byte data, receipts, prepared/displaced
entries and partial outcomes are not automatically deleted after an error.
Reviewed backups remain necessary; the primitive cannot protect an earlier
version already replaced by another writer before its atomic exchange.

The implementation follows the documented
[Linux renameat2 semantics](https://man7.org/linux/man-pages/man2/rename.2.html),
[descriptor/open flags](https://man7.org/linux/man-pages/man2/open.2.html) and
[file plus directory fsync requirements](https://man7.org/linux/man-pages/man2/fsync.2.html).
Regular native I/O/fsync can block at the kernel/storage layer. Byte bounds and
pre/post monotonic gates do not promise hard syscall preemption; a production
supervisor must retain the exact owned process/unknown outcome on an overrun.

## Named modes, receipts and explicit recovery

Only the seven literal role bindings from the reviewed packet are accepted,
with current 0775 and target 0755. All seven native preimages are checked before
the first fchmod. Each existing owned directory descriptor is checked again at
the effect and afterwards, with UID/GID/inode unchanged. Children are untouched.
Any partial chmod failure holds without recursive repair or automatic reversal.
The packet still must define permission disposition after policy retirement.

Private single-link 0600 effect receipts are exclusively created under the
exclusive journal's native directory. Intent/preparation/actual observations
are appended and fsynced with name/inode/content checks and byte bounds. Existing
receipts are never truncated or followed. A failed receipt write closes only
owned descriptors, preserves original bytes and leaves dispatch spent.

Separately approved recovery can retire an exact policy/reference by moving its
entry into a retained slot, or restore reviewed old targets. Original regular
executable CLI wrappers can also be restored at 0755; forward reference
installation still requires a literal link. No database/history/model names
are mutation roles. Recovery uses its own approved operation, exact prior
history and fixed recovery prefix; it does not resume a forward action.

## Actual verification

The **37 Pi methods have zero skips**: eight portable schemas and 29 actual
Linux cases using real files, renameat2, inodes, fsync, symlinks/hardlinks and
seven-directory fchmod. Injected race/storage/deadline errors use real native
effects in owned scratch. Tests check no-replace/exchange preservation, stable
no-ops, parent/name drift, short reads, receipt collisions/descriptor cleanup,
spent claims, complete catalogs, pre/post failures, no recursive chmod, policy
retirement and both old link/regular-CLI recovery forms. Non-file prerequisites
are explicitly synthetic; no actual service/PM/backup/admission cutover occurs.
Temporary test files are asserted then removed by the owned test fixture; this
is a native unit cohort, not retained production or end-to-end packet evidence.

The first 36-method source trial also passes. Review then adds the old regular-
CLI recovery form and a strict forward-link guard, verified by a fresh 37-method
trial with new operation/root identities. No failed operation is replayed.

Independent read-only recovery verifies every named positive result, exact four
source hashes, raw log/receipt hashes, terminal owned process incarnations and
empty scratch. It reconserves 23,628 SDK inputs, 41 SDK caches, 30 package sources/
29 caches, app/tool facts, live references and all seven existing ancestor modes.
The full Windows suite runs **803 cases: 707 pass, 96 platform skips**. Ninety-three
coordinator/journal/service-watchdog/file platform methods run in their actual
separate Pi cohorts; three older skips retain their original scope. The earlier
124-method service/journal cohort and five native error scenarios stay separate.

Next bind the renderer's exact ten paths and reviewed payloads to this component,
implement explicit missing-parent preparation and retain a composed native
publication/recovery fixture for independent readback. Neither generic catalog
validation nor these owned parents establish the final live path layout. Actual
canonical old-stop/new-start PM, encrypted backups, admission, reload, live-user
health/history/graph, other fault paths and complete runtime-only forward/
recovery acceptance remain before separate live approval. All original audit,
phone/on-device, upstream/project/worker/NAS/integration and signed-release
requirements remain active. Published beta.9 and Android/build inputs are unchanged.
[Runtime packet](2026-11-14-runtime-cutover-packet.md) ·
[Separate service error evidence](2026-11-14-native-service-error-paths.md).

| Evidence | SHA-256 |
| --- | --- |
| Native publication source | `6e2255caca484210e6aafe568f963865013bb304adf5241bda46d8fa89323812` |
| Schema/native tests | `4986ab0d67888a0a277bd51a4bd59c0a9416880cec3de9e39269a2ba7a785e49` |
| Final native terminal receipt | `c824472cf7553df96c3b43cb08fe7b77b6805518f5ee082fb86026b3dc9c249f` |
| Independent native review | `1d4f3162564b679e41707da62512432533434d566916cdde3981092297bbd761` |
| Earlier 36-method trial | `a953eeea3d0a43f64a891a97d60315175d712e455f31d77ba953eaa4b5ceeff3` |
| Full Windows suite | `edf214ec80f151eaee2efe735bc6ff13284be0fb8a672a8bcf13778d2ab310e4` |
