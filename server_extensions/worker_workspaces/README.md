# Windows registered-workspace read provider

This read provider runs on the Windows execution owner inside its trusted
interactive broker. The separately reviewed existing-daemon integration is now
deployed. Earlier local/Pi private SSH reads passed at the documented revisions;
see the [deployment scope](../../docs/contracts/2026-11-12-persistent-owner-read-bridge.md).
The current RAM-only coordinator trial refuses directory listing before
submission. A separately tested client availability fix is undeployed; see the
[current evidence and limits](../../docs/contracts/2026-11-13-pipe-availability.md).
The optional [native coordinator extension](../coordinator_workspaces/README.md)
is source-tested separately. Production native-auth/Android wiring and NAS file
acceptance remain open. Importing these modules starts no listener, agent,
SSH client, model, registry entry or execution job.

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

The deployed broker adapter preserves worker/root identity over the existing
private control channel. Remaining stages: accept the optional native Hermes
coordinator capability against production authentication and add translated
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
preview-content transport. The separately commissioned private foreground bridge
is deployed; optional native Hermes capability acceptance and Android review
remain. The NAS full-ID refusal remains unresolved.

## Opt-in local Windows transport

`local_pipe.LocalReadPipe(adapter)` explicitly creates one ephemeral local named
pipe for an existing checked broker context. Importing starts nothing. The pipe
has a protected DACL granting only the current Windows user SID; its handle is
not inheritable, its name is generated, only the first single instance exists,
and PIPE_REJECT_REMOTE_CLIENTS refuses network clients. No TCP port, shared
secret, credential copy, persistent queue or project-content file is added.

The server queries the client's actual Windows token after reading a bounded
message and always reverts impersonation before the interactive worker reads a
project. Identification-only client SQOS prevents using client credentials for
those operations. A failure to impersonate retires the endpoint. A reported
failure to revert raises `IdentityRevertFailure`, permanently closes the context
and must stop the dedicated pipe thread; callers must never catch it and reuse
that thread for other work. The native fault test performs real successful
reversion before simulating its reported failure, so it leaves no impersonation.

`call_once(endpoint, frame)` verifies the actual pipe server PID, process creation
time, Windows user and session before submission. The locator accepts only the
fixed local pipe namespace and exact scope/instance. Process timestamps must
retain their complete integer precision. Endpoint metadata comes from the trusted
broker/control path, never directly from a phone request. Windows account
identity is separate from actor/profile/project authorization; the existing
broker must supply fresh policy and actual interactive-owner checks. A service
session in a disposable transport test is not production interactive acceptance.

The candidate client makes one finite WaitNamedPipeW availability wait before
its single CreateFileW attempt, sharing the original deadline. Readiness does
not reserve the instance: a racing open can still refuse without retry.
Caller/server identity checks and conservative submission state are preserved.
This source change is not yet installed in the live worker component.

Each call has a finite 0.05–10 second deadline, bounded message I/O and an ACK over
the exact reply hash. Pending local I/O is cancelled and drained; unread contents
are not kept for retry. `ReadTransportError.request_submitted` is conservative:
a failed write or lost reply may have reached the broker. There is no implicit
retry or listener restart. Close retires leases, cancels pending pipe I/O and
drops in-flight file contents; OS filesystem work already in progress must still
return before its thread can finish. Transport deadlines do not force-kill that
work or certify that an unresponsive filesystem stopped.

This transport is enrolled in the existing live host daemon through its reviewed
fresh-registry/operator policy and private SSH helper, preserving current jobs.
The coordinator native-auth source extension has its own isolated acceptance;
production auth, Android/phone files, NAS full-ID incompatibility, Surface and
the rest of the audit remain open.
