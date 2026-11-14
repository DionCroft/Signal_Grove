# Single-socket retirement HTTP component

14 November 2026. Tested source a36e75b adds a bounded request adapter to the
reviewed retirement guard. This is a deployment component with real portable TCP
tests, not a live provider sign-in, native process-pin acceptance or deployment.
The complete audit, original four priorities and release requirements remain.

## Connection, authentication and request boundaries

`scripts/pinned_retirement_http.py` takes one already connected IPv4 loopback
socket, an exact native Owner and an in-memory provider access token. It creates
no connection and performs no discovery, login, refresh, redirect or retry.
Its connection class refuses every reconnect attempt. A closed, replaced,
uncertain or expired connection is permanently unusable; a second invocation
cannot replay the first request through a replacement socket.

The supplied observer must positively resolve the actual reverse TCP tuple and
socket inode to the expected process incarnation from the kernel. A same-port
listener, file or HTTP PID is insufficient. The component calls that observer
at construction and before/after each exchange. Implementing and verifying a
finite native observer is still required; portable tests use a synthetic owner
seam and do not prove kernel ownership. The retirement guard also retains its
independent process observations and durable journal requirements.

The adapter sends only `Authorization: Bearer` for a supported provider's native
session. It reads no stored credential, browser cookie, dashboard owner key or
environment secret and performs no credential transfer. The pinned SDK's
[provider middleware](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/hermes_cli/dashboard_auth/middleware.py)
verifies native bearer sessions through its provider stack. A 401 is returned
once and closes the adapter; there is no owner-token/cookie/refresh fallback.
Actual supported sign-in remains unaccepted. No authentication check is bypassed.

Only the guard's two fixed GET routes and prepare/commit/cancel POST route/body
shapes are accepted. External URLs, query strings, arbitrary paths/methods,
header injection and unbounded or extra action fields refuse before dispatch.
Permit and access credentials stay out of representations and exception messages.
The existing guard keeps them out of the journal; this adapter writes no journal.

## Response and lifetime bounds

The socket has an operation timeout and an absolute watchdog that shuts down
that exact socket, interrupting a trickling response as well as a blocked read.
The default is five seconds, with a maximum of ten. The observer itself must also
be bounded by its native implementation; constructor/caller work is not certified
by the request watchdog. Only HTTP/1.1 persistent, length-delimited JSON is
accepted, with a cumulative 16-KiB header bound and 64-KiB body bound. Ambiguous
lengths, chunking, compression, close framing, invalid UTF-8, duplicate fields,
nonfinite JSON and non-object bodies hold the result as unknown. Errors close
the connection and remove its retained access token.

This adapter is owner-local administration over a kernel-verified loopback
socket. It has no remote URL/TLS mode and rejects SSL socket subclasses; it does
not change the app's verified HTTPS/Tailscale connections or expose a listener.
The caller closes it after use and supplies authentication, native ownership
proof and the exclusive fsynced transaction journal. It does not stop services,
claim a retirement permit, install PM facts or activate the candidate by itself.

## Observed tests and preserved failed invocation

All 18 new methods use actual TCP sockets/HTTP traffic on the Windows owner,
with synthetic provider tokens and owner observations. They verify reuse of one
exact socket, prepare/commit with the unchanged guard and no second commit;
changed owner before/after dispatch; connection loss after POST; no reconnect,
refresh or redirect; 401; framing/header/body/JSON limits; an absolute deadline
against a byte-trickling server; concurrent requests; invalid input; and private
exception messages. Owned fixture threads/sockets finish. These are transport
checks, not a native Linux kernel observer or an authenticated deployed SDK.

The full portable suite runs 623 cases: 620 pass and the same three actual-POSIX
cases skip. A preceding invocation from the temporary working directory fails
with ten project-import errors after reporting 529 cases. Its raw log remains
failed; no source/assertion changes fix it. Running from the required repository
root resolves imports and passes the complete cohort. No repeated Android build
is needed for these Python-only source additions: the reviewed Android inputs,
1,011 JVM result, lint and installed debug/test APKs remain unchanged.

The first Android cohort is independently verified at 541 positive methods.
The complementary 601-case operation, including the original Demo clarification
case, remains live at this checkpoint. Observe that exact existing operation;
do not restart from a stale receipt. Historical causes, complete API37/current
source gates and final production-key phone acceptance remain open.

No Pi process, service, launcher, canonical PM facts, policy, permission, grant,
model, credential, phone or published APK changes in this chunk. Next deployment
work remains native socket ownership/sign-in and the full bounded transaction/
rollback, followed by a concrete separately approved live packet. The isolated
staging approval does not authorize activation. See the
[cutover packet](2026-11-14-runtime-cutover-packet.md) and
[guard/native-ASGI scopes](2026-11-14-retirement-client.md).

## Evidence identities

| Evidence | SHA-256 |
| --- | --- |
| HTTP component source | 3301bc06244754601677083b8ed669c6ba7ead5810148986995e1197086b453b |
| Real TCP safety suite | f8362468f5656ecf0512d90b8b033a8a7e7945a11a2f3e8b05b64e6844185b78 |
| 18-method focused log | 784aa28b0cef88606fcd7fcdd8c9014d33ee0108310e1288b50afce796c52a0c |
| Complete 623-case portable log | b82bf9978cf07a84cf17a1558779d017e2a1b4bf0da436ae3bec82a12980196f |
| Preserved wrong-working-directory failure | 3953d65250fa5117295dca1124232abcc583f8225b3bc9ddf1c7213ba592456a |
| Independent Android terminal/source/APK review | b63aa7102776794abe4879fdbaf51a665f131083b668abd4a4ecc1671371feca |

The subsequent [native socket-owner checkpoint](2026-11-14-native-socket-owner-and-api26.md)
adds a real procfs observer and verifies it on the isolated Pi: 59 component unit
methods/no skips and four guarded TCP exchanges on one fixture-owned connection.
Provider/protocol are synthetic; normal provider/full SDK listener/lifespan and
the complete transaction still require acceptance. All owned checks are terminal.
Its separate Android review also verifies the completed 1,142-method standard
API26 union. Historical causes, full matrix and signed-production gates remain.
