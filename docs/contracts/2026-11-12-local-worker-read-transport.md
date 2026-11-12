# Private local worker read transport - 12 November 2026

## Scope and authority

`local_pipe.LocalReadPipe(adapter)` is an opt-in local transport for the already
checked broker byte adapter. Importing starts nothing. It creates no public
port, HTTP endpoint, agent, model, credential transfer, execution queue or
project-content cache. The original read provider is still the only filesystem
implementation. This transport has not been enrolled in a live host daemon or
exposed through Hermes/Android.

The actual interactive broker must supply fresh authenticated actor/profile,
policy, registered project/root and OS-owner checks. Endpoint metadata must come
from its trusted private control path. A caller cannot supply an arbitrary pipe,
remote host, PID, scope, path or trusted context through a phone request. Windows
user authentication does not replace HTTP/profile/project authorization. The
current fixture's session metadata is not deployed interactive acceptance.

## Windows authentication and lifecycle

A generated local pipe name uses a protected DACL with one ACE for the current
user SID. The server handle is not inheritable; first-instance/single-instance
and PIPE_REJECT_REMOTE_CLIENTS are configured. There is no null/default DACL,
shared secret or bearer secret in the pipe name. Local SSH and interactive logons
of the same deliberately authorized Windows account can communicate; another
OS token without its required access cannot obtain a pipe handle.

Before writing a request, the client queries the actual pipe server PID and
matches its creation timestamp, token user and session to trusted metadata. PID
alone is not authority; creation timestamps retain full integer precision.
After reading a bounded message the server queries the actual client token,
then always reverts impersonation before filesystem dispatch. Client SQOS is
identification-only. Failed impersonation closes/retires the endpoint. A reported
revert failure raises IdentityRevertFailure and permanently retires the pipe;
the dedicated caller thread must stop and never be reused for unrelated work.
The fault test performs successful real reversion before reporting a synthetic
failure, so it does not leave the test thread impersonating anyone.

One foreground request/reply occupies the pipe. Message limits remain 16 KiB
request / 128 KiB reply. The client validates reply schema, correlation, worker
and profile/project identity before acknowledging its exact hash. Deadlines
range from 0.05 to 10 seconds. Pending overlapped I/O is cancelled and drained;
there is no FlushFileBuffers wait, implicit reconnect, listener restart or
read replay. A failed write/read carries a conservative request_submitted flag;
an observation timeout cannot establish that the request never reached the host.

Close cancels pending local pipe I/O, retires the provider and drops in-flight
file contents. It does not force-kill native filesystem work already underway;
that operation must return before its thread can finish. Broker policy must
retire contexts on revocation and discard any retired delivery. These are finite
transport-I/O waits, not a hard upper bound for an unresponsive OS filesystem.

The API/security review uses Microsoft's primary
[pipe security](https://learn.microsoft.com/en-us/windows/win32/ipc/named-pipe-security-and-access-rights),
[CreateNamedPipeW](https://learn.microsoft.com/en-us/windows/win32/api/namedpipeapi/nf-namedpipeapi-createnamedpipew),
[server PID](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-getnamedpipeserverprocessid)
and [impersonation](https://learn.microsoft.com/en-us/windows/win32/api/namedpipeapi/nf-namedpipeapi-impersonatenamedpipeclient)
references, plus the installed CPython pipe/overlapped implementation. Its private
_winapi interface is tested on the explicit Python3.13 and 3.14 versions below;
new interpreter versions need their own acceptance.

## Exact source and verification

Source 630, parent 8d343924b46e958dec6c0574bc0883ecc90abdd0, freezes
784 files with digest `c5bf8c4b7831557589bc9b9880206425ca60add56d30848d6ee98e1e64623511`. Required build tasks,
release lint and instrumentation compilation pass 1004 JVM tests / 96 reports,
359 portable policies and both lint variants with zero errors (51/50 warnings).
Original debug key/certificate and all 32 packaged native ABI/alignment checks
pass. No Android source/assertion changes occur; frozen executable inputs are
checked unchanged after documentation is appended and before committing.

App APK SHA-256: `6c23fcfd8fd5865cfd33f295c14df76219b4eb81c16d1bd31b0ad57a2e7f00e9`.
Test APK SHA-256: `56309d5b81768249ef7ffbab97edad5f828e4d6155a8b766cd6b5c49b695b352`.
Both unchanged project-gateway methods pass on the verified installed API26 pair,
retaining all 9 protected hashes and both package UIDs. These do not certify a
production signed upgrade, phone models or worker-network app integration.

The strict runner records 48 distinct positive Windows cases on Python3.13 and
on the installed worker Python3.14.9, without failures, skips or source drift.
All 8 package/test/runner source hashes agree across those runs. The cases comprise
24 filesystem, 10 checked-adapter and 14 transport cases. The transport cohort
performs actual native DACL/handle inspection, restricted-token access denial,
separate-process prepare, Unicode prepare/list/preview, forged PID/start/session
refusal, oversized native input, deadlines and drain/reuse, missing ACK/no replay,
close during accept/read and safe identity-failure injection. Repeated pending-
accept cancellation checks native handle counts. Six new portable locator,
framing, deadline and refusal tests join the 359-policy suite.

The retirement regression initially records 47 positives / 48 and one failure:
a retire between the fresh binding check and grant insertion could allow a new
grant into a retired provider. Grant commit now checks retirement while holding
the same lock. All 48 pass with the original regression assertion unchanged.
Before-fix619 and passing630/631 reports remain retained; they are not relabelled.

Parent 8d34392's Windows CI artifact is independently verified: 33 distinct actual
positive methods, matching native log/report and six committed source hashes
with Windows checkout line endings. [Run 43144310744](https://github.com/DionCroft/Signal_Grove/actions/runs/43144310744)
artifact 13910114051 has ZIP SHA-256
`700b57dd1b9f7a8b8c33bf06d0cd58ba175e21ae27c112a46cd2f169e0364d21`.
It covers that parent only; the new 48-case hosted gate still requires its result.
The prior complete standard Android union at 2a763fe remains separately verified;
the eleven external-model methods and current final-source release gates stay open.

## Existing broker observation and remaining integration

A read-only observation verifies fresh ready/registry health against the live
process's actual owner, creation time and nonzero session. Its declared maintained
runtime exists. Queue/receipt state-only counts contain eight completed entries,
without a running/held row at that observation. Only state counts are exported.
Private receipt JSON is parsed locally; its answer/path/private fields are not
exported, and no live rows, files or services are changed. The first diagnostic's codec-label error
is retained separately; the corrected read completes without changing live state.
This does not authorize a later restart or replace fresh pre-deployment checks.

Private evidence: source/build/proof/native/policy630, Python3.14 native631,
protected upgrade635, connected636, existing broker observations637/638 and
parent hosted proof613. All temporary fixtures/evidence remain in approved
non-C scratch and only disposable verified fixture roots are cleaned up.

Next: add the reviewed adapter and pipe to the existing interactive broker and
private helper with actual actor/profile/root authorization. Verify recovery,
live references and an idle window before any production changes. Then add the
versioned optional Hermes capability and translated Android project/root/file
review. NAS full-ID incompatibility, Surface, production auth/recovery, integrations,
model/physical/lifecycle, advisories and signed community APK/download acceptance
remain in the original audit ledger. Published beta.8 and the active full goal
are unchanged. No private/generated evidence or APK is committed by this chunk.

### Windows CI SID identity gate correction

The first c37483a Windows run retained 47 positives / 48 and one failed test:
Windows Server formatted the Administrator account SID as SDDL `LA`, while the
test expected its numeric spelling. The pipe DACL/runtime code is unchanged.
The corrected gate reads the binary ACL and requires protected/non-default DACL,
one non-inheriting allow ACE, exact full file rights, `EqualSid` equality to the
owner token and a non-inheritable pipe handle. Native alias equivalence and
Administrators-group rejection cases prevent string formatting from determining
authorization. All 50 native cases pass on Python3.13 and 3.14.9 with no skips or
source drift. Hosted acceptance of that corrected commit remains required.

Only the native test file changes executable bytes after source 630; all 653 other
frozen executable inputs, including every runtime/Android input, are verified
unchanged. The source 630 build/1004 JVM/359 policy/lint/signing/alignment and
retained-data connected results remain evidence for those unchanged inputs;
this test-only follow-up does not claim a new APK or a new full Android run.
Failed [run 43148625763](https://github.com/DionCroft/Signal_Grove/actions/runs/43148625763)
and artifact 13912787747 are retained with ZIP SHA-256
`06876233beb7addfa318a185b8deecb7e2da6767fd593f791aec9636d9b9fdc4`.
The correction follows Microsoft's [SID string aliases](https://learn.microsoft.com/en-us/windows/win32/secauthz/sid-strings)
and [EqualSid](https://learn.microsoft.com/en-us/windows/win32/api/securitybaseapi/nf-securitybaseapi-equalsid).
All deployment, Hermes/app wiring, NAS and full original release requirements
remain open; the goal stays active and published beta.8 is unchanged.
