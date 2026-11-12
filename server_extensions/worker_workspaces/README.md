# Windows registered-workspace read provider

This is the first worker-side component for Signal Grove's project-file access.
It runs on the Windows execution owner inside its trusted interactive broker.
It is **not yet wired to the broker, Hermes HTTP service or Android UI**. It
does not create a listener, agent, SSH client, model, registry entry or execution
job. Existing phone/Pi production behavior is unchanged by adding this source.

The native Hermes profile filesystem can point to the coordinator or an SSH /
container backend. Its default-cwd and list responses do not certify a Project
Agentic Windows worker. Keep that interface distinct from registered execution
ownership; do not use coordinator storage as a substitute for a worker project.

## Trusted binding and fixed operations

The integrator supplies a fresh `RegisteredProject(project_id, device, root,
registry_revision, scope_id)` from the interactive worker's authoritative registry and
host/root policy. A phone-provided absolute path, a remembered project name or
an SSH-side cached routing projection is insufficient. The provider independently
checks actual hostname, root/entry disk identities and the binding again before
returning a result. The registry revision must change when its relevant binding
or approval scope changes.

The broker derives a 64-hex scope ID from its authenticated actor/profile policy.
It creates a provider for that authorized scope and calls the trusted lookup with
(scope_id, project_id). Scope IDs are context fences, not credentials. A caller
cannot select a different scope or reuse its binding across profiles.

Only three closed-schema operations exist:

- `prepare`: schema1, operation, scope_id, registered project_id. Returns observed worker
  instance, canonical root, a process-local binding token and a 300-second lease.
- `list`: adds binding_token, relative_path and entry_token. Root uses an empty
  path/null entry token; entering a listed directory requires its exact token.
- `preview`: the same fields, with an exact listed regular-file token.

No operation accepts a command, host, PID, URL, absolute child path, model,
credential, registration or write action. The integrator must authenticate the
transport, bind it to the selected worker, validate response framing and call
`dispatch` (four concurrent requests maximum). Tokens stay inside request bodies,
not URLs/logs. Process restart, registration/root/entry change and expiry require
an explicit fresh review; this module performs no retries.

## Native filesystem behavior

Every existing disk handle opens with OPEN_EXISTING and
FILE_FLAG_OPEN_REPARSE_POINT. Held ancestor directories exclude FILE_SHARE_DELETE.
Final handle paths and volume/file IDs are checked before content is read.
Reparse points, junctions, hard-linked files, Windows device/ADS/traversal aliases
and credential/control subtrees are refused. An existing writer yields BUSY.
File metadata is compared after the bounded read, and changed authority prevents
returning its result.

Lists are bounded to 200 returned entries, 1,000 scanned entries and 96 KiB of
encoded entry metadata, with an explicit incomplete marker. Previews read at
most 32 KiB from a regular file no larger than 64 MiB. UTF-8 and original line
endings are preserved; a partial final code point is withheld. Binary/invalid
UTF-8 receives an explicit binary state. The preview hash covers only returned
preview bytes, not an unobserved full file.

Content remains in worker memory for the explicit reply. This code writes no
project content or receipt cache. Preview contents must never be logged, inserted
automatically into helper/model context or persisted on the coordinator. File
display is untrusted content; it cannot authorize a tool or execution action.

The handle rules follow Microsoft's primary references:
[CreateFileW](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-createfilew),
[GetFinalPathNameByHandleW](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-getfinalpathnamebyhandlew),
[GetFileInformationByHandle](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-getfileinformationbyhandle)
and [ReadFile](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-readfile).
Filesystem/SMB identity support and authenticated deployed-broker behavior still
need their own acceptance; a local NTFS test is not NAS or Surface certification.

## Verification and next integration

Portable policy cases run with the ordinary script tests. Native acceptance must
run on Windows with TEMP/TMP and evidence output set to the approved non-C
scratch root:

```text
python -B scripts/run_workspace_provider_tests.py --output <scratch>/worker-native.json
```

The strict runner records actual distinct successes and source hashes and rejects
skips, failures, empty/partial suites and source changes. The Windows CI job uses
its disposable runner scratch; no owner files, credentials or registry are used.

Remaining stages: install a reviewed fixed-action adapter in the owning broker,
preserve authenticated worker/root identity over the existing private control
channel, publish a versioned optional Hermes capability and add translated
Android project/root/file review controls. Validate real registered roots on each
owner and preserve current data before deployment. Large/binary downloads,
dispatch/reassignment, owner-phone acceptance and the full audit stay open.

File identity requires the full `FILE_ID_INFO` volume/128-bit identifier from
`GetFileInformationByHandleEx(FileIdInfo)`. The legacy 64-bit identity is not
a fallback: [Microsoft documents possible ReFS collisions](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/ns-fileapi-by_handle_file_information). Unsupported filesystems
refuse explicitly; local NTFS testing does not certify ReFS or SMB deployment.

## Checked broker byte adapter

`broker_protocol.BrokerReadAdapter` binds one authenticated interactive context
and one provider to strict JSON byte frames. The trusted broker supplies fresh
`CheckedReadContext(scope_id, device, policy_revision, interactive)` observations;
these are internal authority inputs, never fields accepted from request JSON.
Authentication and actual interactive-owner verification remain the transport's
responsibility. An ordinary SSH process or its cached registry projection must
not assert that context merely by setting a Boolean.

Frames contain schema1, a 32-hex request_id, the exact worker_instance and the
closed provider payload. Duplicate JSON fields, invalid UTF-8, nonfinite constants,
unknown fields and requests above 16 KiB refuse. Replies are bounded to 128 KiB.
Four live requests and 256 retained request IDs are maximums. Duplicate IDs do not
replay file reads or cached contents; exhaustion explicitly requires a newly
reviewed context, without evicting live identities. Errors return fixed codes,
not exception text, supplied contents or credentials.

Context is checked before and after dispatch. Revocation, owner loss or closure
drops observed file contents. `close()` retires the provider and its leases;
close and successful serialization share a lock. The transport must discard
responses for a retired context and must not retry unknown submissions itself.
Native acceptance includes the close-from-context-callback race with a real read.

No listener, HTTP route, SSH action, durable job queue or scheduled service is
added by this adapter. In particular, the existing execution queue is not a
preview-content transport. The next stage is an authenticated private foreground
bridge in the actual interactive broker, followed by the optional Hermes
capability and Android review. The NAS full-ID refusal remains unresolved.
