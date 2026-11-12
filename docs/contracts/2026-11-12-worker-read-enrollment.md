# Explicit owner-scoped worker read enrollment

12 November 2026. This extends the existing local read transport; it does not
start a service, admit a phone actor or enable a production workspace capability.

## Trust and lifecycle

An operator-reviewed policy grants exact scope hashes to exact project IDs.
The closed schema permits one device, one Windows owner SID and at most eight
scopes with 32 distinct IDs each. It accepts no root, command, URL, model or PID
from a requester. Scope identity is a domain-separated hash of an enrolled
coordinator authority and its verified native actor/profile identity. A phone
cannot turn a claimed identity or possession of that hash into authorization.
The authenticated coordinator integration still needs deployment acceptance.

The broker supplies fresh protected policy bytes, a fresh canonical registry
snapshot and its existing project validator. The validator preserves ownership,
paused-project, infrastructure-root and foreign-worker guards. The registry
revision hashes the exact locked bytes parsed, not a cached routing projection.
The default observer reads the actual process token, creation time, session and
current WTS connection state. Session zero, another user/process/device or a
non-active session cannot authorize reads.

Any observed policy/registry/owner change retires this session and its file
leases permanently. Authorization is checked again after the existing worker's
root validation and after file observation. Read grants cannot revive when an
old policy reappears. The existing bounded frame/no-replay contract remains.

`operator_file.read_operator_file` is internal configured-source I/O. Held
ancestor handles prevent rename/reparse substitution; the source handle denies
concurrent writes/deletion. Only bounded, ordinary, single-link disk bytes with
an exact final path are returned. This metadata loader does not issue project
leases and never substitutes legacy identity for the workspace provider's
mandatory full 128-bit file IDs. UNC share-root separator handling is covered.

## Verified scope

Source 677 freezes 796 inputs with digest
`2abddc3214886c8d854f2717c5097a6ceec3c748966adffdf649b0e6856210ec`.
Required build tasks pass: 1004 JVM cases across 96 reports, both lint variants
with zero errors (51 debug/50 release warnings), debug APK and instrumentation
compilation. All 373 portable policies and all 58 distinct native Windows cases
pass; native acceptance has no skips, expected failures or source drift. This
local native run uses the actual worker Python 3.14.9; fixture tests explicitly
inject their owner observation and do not certify service-session admission.
The exact API26 debug upgrade retains all nine protected file hashes and both
package UIDs. Both unchanged project gateway contract cases pass on that installed
APK. Its SHA-256 is
`f967d01266e024a25f955fd36090cf86f814e456a29cc4927878f6a6910bc272`;
the companion is `56309d5b81768249ef7ffbab97edad5f828e4d6155a8b766cd6b5c49b695b352`.
The first upgrade preflight omitted the no_backup directory and stopped before
installation; the corrected inventory includes its three WorkManager database
files and retains the failed preflight separately.
Original debug signing and all 32 native ABI/alignment checks pass. No model
weights are bundled. This is a debug validation build, not a released upgrade.

A separate foreground proof uses the default actual-owner observer, locked
canonical registry bytes and the installed worker's unmodified project
validator. It performs prepare/list/preview through the actual owner-only pipe
against an already registered local validation root: seven entries and a
302-byte existing text preview, 22 fresh registry reads and six worker-validator
calls. The paused and foreign-worker registrations remain rejected. Private
IDs, root paths, source contents and raw evidence are not committed. No project
writes, persistent daemon enrollment, Pi, credentials or model changes occur.

The first new native run found a fixture newline mismatch; that failed result
is retained. An attempted edit missed its Unicode match, so the same failure
is retained separately. The corrected fixture writes explicit UTF-8 bytes.
The first real registry probe refused the UNC share-root spelling difference
before project access; metadata observation informed the normalization fix and
its regression. Later frozen-source native and real-worker proofs pass.

The corrected preceding SID gate also passes independently on hosted Windows:
[run 43150240082](https://github.com/DionCroft/Signal_Grove/actions/runs/43150240082),
50 native cases, artifact 13913820784, verified ZIP SHA-256
`424c5b4e65fab28386d64ad7f318eafe91b862c7844ef1b3d80ac1f8bd71fcaf`.
That historical hosted pass does not certify this new enrollment code.

## Still required

Review the concrete private host-daemon/helper deployment and its endpoint
metadata ACLs; verify recovery and fresh idle receipts before enabling it.
Wire authenticated native coordinator actor/profile selection and optional
capability, then Android review and actual phone acceptance. NAS workspace
full-ID support remains unavailable; this operator-metadata read does not fix
NAS workspace previews. Surface, reconnect/revocation races across the complete
chain, deliberate session renewal, all retained audit and signed APK release
gates remain open. Published beta.8 is unchanged.

The owner MCP initialized missing lifecycle documents without replacing existing
instructions/state or creating Git. Local graph/configuration is ignored.
Indexing timed out; the first new partial derived index had zero nodes and only
schema metadata, was hash-preserved privately, and was reconciled before a
fresh attempt. The bounded context-pack call still refuses the incomplete graph
identity. No accepted graph freshness/context-pack claim is made. Continue using
bounded authoritative files while repairing this owner-local derived index;
never build a substitute on the coordinator.

## Primary API references

- [WTS session information](https://learn.microsoft.com/en-us/windows/win32/api/wtsapi32/nf-wtsapi32-wtsquerysessioninformationw).
- [WTS connection states](https://learn.microsoft.com/en-us/windows/win32/api/wtsapi32/ne-wtsapi32-wts_connectstate_class).
- [CreateFile sharing and non-inherited handles](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-createfilew).
