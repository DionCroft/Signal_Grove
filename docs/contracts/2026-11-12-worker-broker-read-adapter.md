# Checked worker read adapter - 12 November 2026

## Boundary

The adapter is an internal byte boundary between a trusted interactive broker
and its registered-root provider. It starts no listener, HTTP route, SSH helper,
agent, job queue, service or model. It is not deployed or connected to Android.
The caller must authenticate its private foreground transport and verify actual
interactive owner, actor/profile, project policy and fresh root lookup. A caller
cannot gain that authority by supplying CheckedReadContext as JSON, an SSH-side
cached registry row or a Boolean claiming interactive execution.

Existing host execution queues carry acknowledgements/leases and persistent
receipts; they must not become preview-content storage. File bytes stay in the
owning worker's memory and explicit response, outside coordinator caches, logs
and automatic helper/model context. A future pipe transport needs explicit OS
access control: [Microsoft documents default named-pipe read access for Everyone
and anonymous](https://learn.microsoft.com/en-us/windows/win32/ipc/named-pipe-security-and-access-rights).
This source does not install such a pipe or authenticate a production caller.

## Fixed protocol and lifetime

One adapter owns one provider/context lifetime. Closed schema1 frames contain a
32-hex request_id, exact worker_instance and closed provider payload. Requests
above 16 KiB, invalid UTF-8, duplicate JSON fields, nonfinite constants, unknown
fields, foreign instances/scopes and commands refuse. The trusted broker supplies
fresh CheckedReadContext observations internally; policy revision, actual device
and interactive readiness are rechecked before and after dispatch.

Replies are bounded to 128 KiB and carry exact request correlation and instance.
Only fixed errors leave failures; exception messages and supplied private values
are not echoed. Four active reads and 256 retained IDs are the maximums. Duplicate
IDs never repeat a read or return cached file contents. Exhaustion refuses rather
than evicting old identities; a newly reviewed context is explicit. No retry loop
or cross-process exactly-once guarantee is claimed.

Close retires the provider and invalidates its leases. Context validation checks
closure after the callback; successful serialization and close share a lock.
Revocation, owner loss or close drops a completed read before serialization.
The transport still must discard delivery for a retired context. The root/entry,
full-ID, path, size, link, secret-tree and no-follow rules remain unchanged.

## Exact tests and build

Source 600, parent 57d04fa2499e9d454ab19f32678509f650f6c345, freezes
780 files with digest `9e9ac267472a787ec5fd16ddaa154eca68dc2800183f0f16f971d467a56e43d7`. Required build tasks,
release lint and instrumentation compilation pass: 1004 JVM cases / 96 reports,
353 script policies, zero debug/release lint errors (51/50 warnings), original
debug key/certificate and all 32 packaged native-library ABI/alignment checks.
No Android source or assertion changes occur. Post-freeze documentation is
separate; all frozen executable inputs are checked unchanged before commit.

App APK SHA-256: `b59a82c516bd409206bc00588c0d36d59fb9f99ca2b4645791cdf878e1de0767`.
Test APK SHA-256: `56309d5b81768249ef7ffbab97edad5f828e4d6155a8b766cd6b5c49b695b352`.
Both unchanged Android project-gateway methods pass on the verified installed
API26 pair after all 9 protected file hashes and both package UIDs are retained.
This does not certify worker networking or a production signed upgrade.

The strict Windows runner records 33 distinct positive cases: the 23 existing
filesystem cases plus ten real broker-adapter cases. These exercise actual
prepare/list/Unicode preview, correlation, wrong profile, revocation before/after
read, duplicate refusal, context errors, close, four concurrent reads/fifth BUSY
and bounded request memory. All package/test Python sources and the runner are
hashed before/after, including newly added inputs; no skipped, partial or changed
source result is accepted. Six portable frame/error/context tests join the full
353-policy suite. Dedicated Windows CI discovers both native classes; its own
new-commit result remains to be verified.

The added callback-close case initially records 32 positives / 33 and one real
failure: a context callback could close the adapter while returning its former
context, allowing observed content into a successful reply. The corrected code
rechecks closure after that callback and synchronizes serialization with close.
All 33 now pass without changing the regression assertion. Failed native595 and
passing native600 remain private evidence, not relabelled results.

## Parent CI proof and storage observation

[Parent Android run 43131612110](https://github.com/DionCroft/Signal_Grove/actions/runs/43131612110)
now has successful verify, all four device shards and the complete union job.
Independent parsing of all inventory/native logs, source/APK bindings and the
separate summary establishes API26 1132=535+597 and API37 1133=536+597, with disjoint
complete unions and no failed/skipped/missing/duplicated positives. Source is
2a763fe4c42a4cabde6fd7707e377e7664d5ec3b; eleven external-model methods stay not-run.
This is exact parent acceptance, not a newer APK or physical/model acceptance.

| Artifact | ZIP SHA-256 |
| --- | --- |
| android-device-37-shard-0 | `0e6dd3bb6164771165acd513b4aa12f6840ea509f77ad785eda6302894add3cf` |
| android-device-37-shard-1 | `495e922bc369aa5296e75fc83e7047f1563ba9e034f4241dcf0431dca49c87bd` |
| android-device-26-shard-1 | `f0fc536404baf3128326d0e35ce6ba30bad5770e87b93f52a651875a39fdade8` |
| android-device-26-shard-0 | `7378bc405265bc2330d27cd0ef87211590bd25b34509928539af38432f1871ac` |
| android-standard-suite-summary | `0ca4ff05be1ab21eb1b5c5314336a7ff0f579826802ca4f9569888c82eed1103` |

Read-only volume observation592 confirms the current mapped network storage
labels itself NTFS, exposes flags0x5006f (without open-by-file-ID), and refuses
FileIdInfo with native87; local NTFS supports the full query. A label/capability
projection is not proof that legacy IDs satisfy the needed uniqueness semantics.
No fallback or production NAS acceptance is introduced. Microsoft's
[file-ID rules](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/ns-fileapi-by_handle_file_information)
and [volume API limits](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-getvolumeinformationbyhandlew)
remain relevant to a separately verified compatible strategy.

Private evidence: source/build/proof/native/policy600, protected upgrade605,
connected606, parent artifact downloads601 and independent union602. No logs,
credentials, registries, owner paths or generated binaries are committed.

## Remaining end state

Complete authenticated private foreground transport and actual interactive owner
integration, advertise a versioned optional Hermes capability, then implement
Android project/root/file review and clearing. Resolve NAS full-ID compatibility
with a separately reviewed and verified strategy; verify each actual registered
owner/root and Surface. Complete production auth/recovery/approvals, integration,
model/physical/lifecycle, advisory/provenance and signed-release gates in the
original audit ledger. Publish the original-key production APK and verified
public download after applicable release checks, as required by docs/GOAL.md.
Published beta.8 and the full active goal remain unchanged.
