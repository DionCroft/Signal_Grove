# Owning Windows workspace read provider - 12 November 2026

## Scope and source decision

This internal component executes filesystem operations on the owning Windows
host. It creates no agent/backend, listener, SSH connection, scheduled task,
registry entry, model operation or production deployment. Android networking and
UI are unchanged. It must be integrated with the trusted interactive broker
before it can serve a phone.

Immutable source 2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19 and
3b421578588243f920886e6fe79a6faa87ea026e is checked against actual Git blob IDs.
At these revisions, native /api/fs/default-cwd returns cwd/branch and /api/fs/list
returns entries/error or the profile's SSH adapter. Those projections do not
certify a separate registered Windows execution owner. Native SSH filesystem
paths follow that profile's backend policy; a Pi path cannot stand in for a
Windows/NAS project. No upstream baseline, handler waiver or private service is
changed by this decision. The maintained architecture/registry/broker source is
inspected read-only; no private paths or project data are copied into this repo.

## Binding and filesystem contract

A fresh authorized RegisteredProject contains project ID, actual device,
authoritative root, registry revision and authenticated actor/profile scope.
The broker supplies a scope-specific lookup. Caller-selected roots, endpoints,
commands, PIDs and credentials are not protocol fields. A scope hash is a
context fence, not transport authentication.

Closed prepare/list/preview operations use process-local 300-second binding
leases and exact entry tokens. Scope, hostname, process instance, registry
binding, root/entry identity and metadata changes refuse a result. Registry
authority is checked before/after observation; roots are re-opened and verified
each time. The caller must authenticate its private transport and handle
unavailable/expired/changed results explicitly; there is no retry loop.

Existing disk handles use OPEN_EXISTING, FILE_FLAG_OPEN_REPARSE_POINT and
held directory handles without FILE_SHARE_DELETE. Final handle paths and
volume/full 128-bit file IDs are checked before reading content. Unsupported
full-ID filesystems refuse rather than falling back to the legacy 64-bit ID. Junctions, reparse points,
hard-linked files, traversal, Windows device/ADS aliases and credential/control
trees refuse. An existing writer yields BUSY. Metadata is rechecked after read.
Raw file data is returned only for the explicit preview and is not written to a
coordinator/cache, logged or automatically included in model/helper context.

Listings stop at 200 entries, 1000 scanned entries or 96 KiB of encoded entry
metadata, reporting truncation. Previews read at most 32 KiB from a regular file
no larger than 64 MiB. UTF-8/CRLF are preserved, an incomplete trailing codepoint
is withheld, binary/invalid UTF-8 is explicit and the hash covers preview bytes
only. Percent-looking filenames stay literal. Four concurrent requests are
bounded without one global filesystem I/O lock.

The native mechanisms are reviewed against Microsoft's primary
[CreateFileW](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-createfilew),
[final-path](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-getfinalpathnamebyhandlew),
[file-information](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-getfileinformationbyhandle),
[full file identity](https://learn.microsoft.com/en-us/windows/win32/api/winbase/ns-winbase-file_id_info)
and [ReadFile](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-readfile)
references. The detailed provider contract is in
[its README](../../server_extensions/worker_workspaces/README.md).

## Exact validation

Frozen source 566 is based on 2a763fe4c42a4cabde6fd7707e377e7664d5ec3b and has
digest `a4aad19b72b256c2f504ff24a8464bf85b8fa80e0eed9efb4d0bbfd638d7e1df`. Required build tasks pass 1004 JVM tests
in 96 reports, 347 policy tests and both lint variants with zero errors
(51 debug/50 release warnings). Original debug signing and all 32 packaged
native-library ABI/alignment checks pass. No Android source/assertion changes
are made. Documentation is appended after freeze; all frozen non-document
inputs are checked unchanged before committing.

App APK SHA-256: `4fa0444364cf13fef11e7931e3ef00904927d8e0e626e9edcebe0da81b4dc4e4`.
Test APK SHA-256: `56309d5b81768249ef7ffbab97edad5f828e4d6155a8b766cd6b5c49b695b352`.
Both project-gateway methods pass on API26 after verified installation, retained
package UIDs and all 9 protected file hashes. This is a two-case connected check,
not complete source 566 Android or worker-network acceptance.

Strict native report 566 records 23 distinct positive Windows cases, no skips,
failure/errors or source drift. These perform real disposable filesystem reads:
Unicode/nesting/CRLF, entry/root/registry/profile/worker/restart/expiry fences,
junction/hard-link escape refusal before content, writer BUSY, rename prevention
during a read, exact byte/list/metadata caps, binary/empty/invalid UTF-8 behavior,
literal percent names, four concurrent reads and repeated handle cleanup. The
full native file ID matches Windows stat across repeated opens and rename; a
new object differs. A refused FileIdInfo call fails before content access with
no legacy identity fallback.
Fixtures live solely in approved non-C scratch and are removed only after
resolved-root containment checks. There is no production registry, NAS, Surface,
Pi or owner-phone test in that suite.

Seven initial portable cases plus the new cross-scope case and four real
unittest-result gate checks join the existing script suite: 347 total. The strict
Windows runner rejects empty/duplicate/foreign/partial, skipped, expected-failure,
unexpected-success and changed-source results. A separate Windows2025/Python3.13
workflow records native evidence on each main source. The setup-python action is
pinned to the verified official v6 commit
ece7cb06caefa5fff74198d8649806c4678c61a1. Hosted acceptance of the new job remains
required; a local result is not its success.

## Preserved failures and parent evidence

Initial native 550/551 has 17 positives/ 18 and one fixture mismatch: the expected
value normalized CRLF while the provider preserved the original file bytes.
The fixture now compares decoded original bytes; no provider assertion is
weakened. A portable fixture's trailing-backslash syntax preflight and the first
strict reporter's unexpectedSuccess attribute error are retained. The corrected
reporter uses unexpectedSuccesses and is checked against real unittest result
objects. Those failed reports do not become native passes.

Parent daa3ec8's hosted artifacts are independently hash/grammar verified:
1132 API26 cases in 3924.38 seconds and 1133 API37 in 5052.987 seconds, with every
declared case started/positive once and no skip/failure/partial result. ZIP
digests are respectively
40b421d9e8b758c190cd305b328d115ee19cbbd112ca263c99146f0258dcabb6 and
daa378bb965ab166a27027025f5b05cf20bf6c4b57e6637b57cc2920d813317a.
The unchanged five-class/eleven-method external-model inventory stays not-run.
This success belongs only to source daa3ec8. Original 2581 timeout/occlusion
findings remain retained; the parent success does not explain their causes.
The newer 2a763fe four-shard jobs were live, not complete, at this checkpoint.

Private evidence retains source/build/proof 566, native/policy 566, protected
upgrade 568, connected 569, source review 562 and parent artifact 558. The earlier
source 555 / 21-case proof remains retained separately. Final review found that
the legacy 64-bit ID is not guaranteed unique on ReFS, as documented by
[Microsoft](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/ns-fileapi-by_handle_file_information).
Source 566 requires the full ID; the final-source evidence is not relabelled from
555. Local NTFS passes do not claim real ReFS/SMB acceptance. A separate read-only
metadata observation on the current mapped network repository returns native
ERROR_INVALID_PARAMETER (87) for FileIdInfo with its 24-byte buffer. The provider
correctly returns UNSUPPORTED; no content or project/registry/transport binding
is read or established. This mapping cannot yet use the provider. The separate
network observations 571/572 are retained; do not weaken the identity rule or
claim NAS acceptance from local fixture passes.
No private/generated evidence is committed. The goal remains active.

## Next integration and full remaining scope

Add a reviewed fixed-action adapter in the existing trusted interactive worker
broker. Its lookup must freshly authorize the exact actor/profile/project/owner
and root; a cached SSH routing row alone is insufficient. Carry identity and
bounded content over the existing private authenticated control path without
staging project files on Pi. Then advertise a versioned optional capability and
add translated Android project/root/file review and lifecycle clearing.

Resolve the observed SMB full-ID incompatibility with a separately reviewed,
verified identity strategy before enabling this provider for that storage.
Verify actual registered roots and SMB identity behavior on each owner. Complete
native transport, production authentication/recovery, binary/download/file
hydration, reviewed dispatch/reassignment, automation/rooms/integrations,
physical/model/voice/background, source/advisory/provenance and exact-source
signed release acceptance. The full original audit ledger remains authoritative.
Published APK stays beta.8; this provider is not deployed or exposed to the phone.
