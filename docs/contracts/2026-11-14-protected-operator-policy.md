# Protected operator policy - 14 November 2026

The missing operator-binding source is implemented but not deployed. A trusted
operator supplies fixed routes and the enrolled authority; phone fields cannot
select policy, identity or commands. Closed bounded policy rows grant only exact
subject/profile/device/project IDs. Each admission and post-I/O check rereads a
protected observation. Content and native file generation enter the revision;
revocation, replacement or revoke/restore discard in-flight replies while
preserving submitted state. Empty/missing/invalid policy grants no access.

All 493 portable cases pass on 919 frozen inputs, including eight new behavior
cases. The native authentication gate and all inputs of the earlier 72-case
Windows gate remain unchanged. Windows explicitly refuses POSIX protection.
The Linux runner declares eight real filesystem cases and refuses skips, missing
positives or source drift. Its hosted native result is pending at this source
checkpoint; no Linux/Pi file-protection acceptance is claimed yet.

## Trust and configuration contract

The [native integration review](2026-11-14-plugin-refusal-and-native-plan.md)
found both extension and operator module absent on Pi. This source supplies the
missing reusable binding; it creates no live operator module, policy file or
grant. The [native adapter](../../server_extensions/coordinator_workspaces/native_adapter.py)
retains its verified owner/operator/profile/current-authority/live-transport gate.
This policy layer does not authenticate a caller or convert an API subject to
an owner. The worker's protected enrollment remains an independent gate.

The explicit operator bootstrap uses
[policy_file_service](../../server_extensions/coordinator_workspaces/policy_file.py)
with an absolute protected directory, fixed filename, enrolled authority and a
server-owned map of reviewed transports. Those transports are copied by the
existing CoordinatorReadService; no policy field or phone frame chooses an SSH
host, executable, arguments or a project root. The optional source installer
packages both new modules before switching its existing guarded source anchors.
It still installs no operator or policy and starts/restarts no service.

The only configuration schema is:

```json
{"schema":1,"authority":"reviewed-enrolled-authority","grants":[]}
```

An explicit grant row has exactly subject, profile_id, device and project_ids.
A subject and profile are exact values from trusted native enrollment; profile_id
must be an absolute canonical spelling for that backend OS. Device must exist in
the fixed operator route map. Project IDs use the existing bounded registry-ID
syntax, with at most 32 per row and 64 rows total. Duplicate rows/IDs, unknown keys,
duplicate JSON keys, invalid types/Unicode, nonfinite numbers and more than 32 KiB
refuse without worker I/O. Empty grants are valid and authorize nothing. No
wildcard, default-profile inference, credentials or automatic project discovery
is added. Expanding live grants is a separate explicit operator configuration
change, not a result of this source publication.

The
[platform-independent binding](../../server_extensions/coordinator_workspaces/operator_policy.py)
requires a trusted PolicyDocument reader. Every lookup reparses a fresh bounded
observation, matches the exact context and returns a hash of both data and its
file generation as the grant revision. Protected administrative edits affect new
reads; any changed/revoked observation after I/O drops the reply. An error after
a submitted read retains request_submitted=true. There is no retry, cached reply
or fallback to coordinator project storage. PolicyDocument repr and exceptions
omit identity, paths, data and reader error text; diagnostic/model context must
not receive the private policy.

## Native file boundary

The POSIX reader anchors no-follow directory descriptors from the filesystem
root. Every traversed directory must be owned by root/the gateway user and have
no group/other write bits. The leaf must be a private regular single-linked file
owned by root/the gateway user, with a positive inode and bounded nonzero size.
A normal provisioned file uses mode 0600 or 0400 in a protected directory. It must
be on the administrator's trusted local filesystem; this is not a review of NAS
project-file identity or remote filesystem permission emulation.

The reader compares pre/post fstat and the name's no-follow stat, including native
identity, ownership, mode, link count, size and nanosecond modification/change
times. The version binds that metadata and the content. A same-byte replacement
or in-place revoke/restore can therefore invalidate an earlier observation.
Symlinks, hardlinks, special files, mutable/insecure metadata and unsupported
native primitives refuse. Nonblocking opens keep FIFOs from parking the loop.
Every descriptor closes on success/refusal. No chmod, policy creation, process,
network call or permissive fallback occurs in the reader.

Windows returns UNSUPPORTED before filesystem access; Windows mode bits are not
used as ACL evidence. A Windows coordinator would need a separately reviewed
native protected reader passed to build_operator_service. The new concrete
file reader targets the Pi's POSIX host; no host OS is faked for acceptance.

## Validation and current limits

Eight portable behavior cases exercise actual CoordinatorReadService with
synthetic trusted policy observations/worker replies: exact grants and refusal
before routing; malformed/ambiguous policy; empty grants; fresh protected edits
without route/authority changes; in-flight revocation; same-byte new generation;
private observer failures; and client identity/command/ungranted-project claims.
All 493 portable cases pass on 919 frozen tracked/new inputs. The approved native
auth adapter remains byte-identical; every input of the previous 72-case Windows
native provider gate remains identical, and that suite was not rerun locally.

Eight separate Linux-native methods cover stable private observations, file/
ancestor permissions, leaf/ancestor symlinks, hardlinks/FIFO/directories, bounded
and changing reads, revocation, replacement/in-place ABA and descriptor cleanup.
The [strict runner](../../scripts/run_coordinator_posix_tests.py) requires actual
Linux, complete distinct positive identities, zero failures/errors/skips and
unchanged source hashes. Fixtures are new private directories outside source;
no production policy, SSH, inference, project content or live grant is involved.
The new Linux lane runs alongside the existing Windows gate after the portable
protocol job. **The native result is pending at this source checkpoint.**

The workflow parses with the existing ruamel validator; no dependency environment
was installed or changed. Initial verifier import-path/missing-PyYAML attempts
are retained privately; only the verifier changed, and no source mutation was
replayed. Windows' unsupported observation is genuine native behavior, not POSIX
permission evidence.

| Evidence | SHA-256 / status |
| --- | --- |
| Full 493-case portable log | `f51adc976e85eb1f273c785a8c0ed2431170af065d806b192ee5f89555fced97` |
| Native auth adapter retained | `e2ea0efdf8294fa8cce36cd68ba06cbefe9d48c0caf53d4acc9bf81bac85fa52` |
| Parsed Linux workflow | `7e4dfb7d6e4bd7b4a325686461148359fbbbaaacb520b8643166f9e0d4ee5cf2` |
| Native Linux policy gate | Pending; no acceptance claim |

## Exact next action

Run the new Linux policy lane on this exact feature source and reconcile the
raw report/log and every source hash before promoting it to main. After it passes,
prepare the exact maintained-runtime bundle and private policy for the already
reviewed worker enrollment, plus current encrypted recovery and idle checks.
The previous authentication approval covers source only: do not install the Pi
operator, create/expand live grants or restart services under that approval.
Real native HTTPS/Android/worker content, independent chat/execution adoption,
NAS, original automation/integration and final phone/release acceptance remain.


The complete [goal](../GOAL.md) and every original audit item stay active. Forty
stable upstream findings/main review, pristine SDK shutdown, the separate POSIX
plugin-RPC case, original API26 causes, actual integrations/automation, doctor/
graph and final source/physical-production acceptance remain open. App, Gradle,
models, signing, owner data and beta.9 assets are unchanged; this is no new APK.
