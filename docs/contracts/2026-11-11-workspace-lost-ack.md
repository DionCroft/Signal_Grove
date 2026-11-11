# A lost workspace acknowledgement remains uncertain

## User-visible behavior

An unknown workspace receipt now explains the exact recovery boundary in English,
Chinese and pinyin, including in a read-only receipt card: do not resend; inspect
its request ID and exact mutation receipt on the Hermes host. A matching current
directory alone does not establish the outcome of that request. A known ACK still
has its distinct label. No new approval, endpoint, retry, reconciliation or
remote-confirmation behavior is introduced.

The existing approved local uncertainty review is an operator acknowledgement
following independent inspection. It changes the local barrier only; it neither
retries nor undoes nor confirms the host mutation. The tests deliberately leave
it unapplied. Uncertainty is retained even when the private test supervisor can
independently prove that the host committed.

Hermes `session.receipt` accepts a turn `admission_id` and queries the turn
admission authority. It is not a workspace-mutation receipt lookup. This fixture
explicitly refuses it. No verified in-app mutation lookup is claimed or invented,
and replaying `session.mutate` is not a read operation. The reviewed primary source
is the pinned [AuthorityConnection receipt implementation](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/gateway/session_controls.py).

## Actual owner and Android proof

The existing finite actual-owner fixture now has an optional
`--drop-workspace-ack` mode. It starts the real immutable native-Windows
GatewayRunner/bootstrap, profile authorities, API/dashboard lifecycle and native
viewer. The upstream StubAuthProvider is the explicitly synthetic IdP. Android
still uses its real HTTP PKCE/refresh/identity/profile-home/ticket/canonical client
and disk-backed Room repository. Authority, dispatcher, stored session, readiness,
mutation processing and ACK are not replaced. Its single disposable live session
has no enabled tools or served model. No inference, worker, production credential,
Pi/Spark deployment, server default or application project operation occurs.

Only after the actual dispatcher returns the exact expected committed workspace
ACK does the outer proxy close that WebSocket instead of forwarding the ACK. The
other bounded routes remain available. The same directory/revision/generation,
profile, session, UUIDv4 and one-write limits still apply. Controlled registered
cron/project/skill notifications use the real upstream broadcaster; they are
explicit metadata stimulus, not natural configuration changes.

Each distinct Android execution proves these nine scoped checks:

1. Native sign-in/refresh, identity and reported-home verification.
2. Explicit Live attachment review and the old directory/empty session snapshot.
3. One reviewed mutation loses its result; the local receipt is unknown without
   invented result details, and raw old/new directories are absent from summaries.
4. Actual Room close/reopen plus startup recovery retains the exact unknown row.
5. A new same-connection review is blocked before another sending claim/request.
6. A saved-connection alias at the same address/profile/home shares that barrier.
7. New adoption is blocked without another resume or replay read.
8. Read-only Live inventory remains available and cannot resolve uncertainty.
9. Credential preferences remain unchanged; no local review is applied.

Normal standard instrumentation runs the same method against a labelled bounded
mock rather than skipping it. Actual-owner acceptance is separate from that mock.
The alias uses a distinct connection identity with the same verified home; it is
not a second real sign-in or independently rotated profile/actor test.

| Immutable Hermes execution pin | Android API | Git-bound modules | Public metadata signals |
| --- | --- | --- | --- |
| `3b421578588243f920886e6fe79a6faa87ea026e` | 37 | 1,428 | 12 |
| `3b421578588243f920886e6fe79a6faa87ea026e` | 26 | 1,428 | 12 |
| `2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` | 37 | 1,394 | 12 |
| `2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` | 26 | 1,394 | 12 |

Every case has one mutation, one initial resume/replay read, four authenticated
sockets and exactly one withheld real ACK. Installed APK hashes, exact terminal
one-method/no-skip output, private QA evidence and owned reverse-map cleanup are
checked. No failed/uncertain run is replayed or reused for another mutation.

The supervisor separately reads final host SQLite: healthy profile stores; one
session with the exact new CWD/revision/generation; one exact-request mutation
receipt whose result equals the observed genuine ACK; no messages, admissions or
rooms in either profile; no sibling session. Every imported source module matches
its immutable Git blob and final boundary denials are zero. A further independent
read-only copy of each exact Android fixture database, including WAL where
present, passes quick_check and contains exactly the confirmed initial adoption
and the unknown workspace operation with an empty result summary. Host proof
does not clear or rewrite that phone row. No private fixture DB, identity, token,
path, transcript or model weight is committed.

All four actual-owner supervisors return **2 / CORE_PASS_SHUTDOWN_FINDING_OPEN**.
The retained genuine `session_reaper._atexit_clear` heartbeat attempts database
access after cached closure/reopen. It is not suppressed or called full lifecycle
acceptance. The previous unrelated pre-claim closure also remains unattributed.

## Measured source and artifacts

Source240 has digest
`0848344b3cc4ea103efd501b417fc4e70c8fb1e031ed193b75d0c6f6c1bacfbd`.
Required local checks pass **956 unique JVM cases / 93 reports**, both APK
assemblies and zero-error debug/release lint (50 / 49 warnings). The policy suite
passes **244 cases**. The original debug signer, all four native ABIs, load
alignment and absence of bundled model weights are verified.

- Application SHA-256: `3191a44ee8c5ffaa7ea8b5d83ddba98ff540ba37b0ff0e06dd1da7c8751933fc`.
- Instrumentation SHA-256: `85dd28bc8d9d8df17629d5c066f270b47825b699fee1542a963d4660549f8c48`.
- Final tracked fixture SHA-256: `8343573e18a77a5c579d84bac5186f679db0dc32b57a8d017e3009e0e9db57fd`.

All compiled app/test/Gradle/native inputs still match the frozen build. Only the
ancillary fixture header/phase label changed after the build verifier; its final
hash above binds all four actual-owner runs. Documentation is added after
measurement. No current-source signed release or production APK is published.

Fresh selected native suites pass **33 methods per API 26/API 37**: 16 viewer UI,
12 auth/Room/workspace and 5 storage methods. They include the new unknown-receipt
explanation and mock lost-ACK method. Native keyboard settings return to their
exact previous values. These are selected methods, not complete Android suite
or physical acceptance. The four actual-owner executions are counted separately.

## Reproduction and remaining acceptance

Use a clean E: checkout at one allowlisted pin and existing native-Windows
interpreter/dependencies. Retain the finite network/process guard:

```text
python -I -S -B scripts/compatibility_fixture_owner_workspace_tcp.py --source-commit <exact-pin> --source-root <matching-E-checkout> --dependency-site <existing-site-packages> --work-root <fresh-E-output-root> --android-api <26-or-37> --emit-host-signals --drop-workspace-ack
```

Bind its private ready manifest to the explicitly verified owned emulator with
one owned reverse mapping. Supply its exact `signalGroveOwnerWorkspace*` arguments
to `CanonicalSessionAttachmentContractTest#ownerWorkspaceLostAckSurvivesRoomReopenAndBlocksFurtherWrites`.
Verify installed identities, exact one-method result and QA unknown receipt before
writing android-result.json. Review the separate child-exit, server SQLite and
phone SQLite evidence. Do not reuse an uncertain root or resend a mutation to
learn its outcome. Normal ACK/fresh-readback mode remains separately supported
and its prior measured acceptance is retained in the
[actual-owner workspace contract](2026-11-11-android-owner-workspace.md).

Room reopen is not Android process death, reboot or app-upgrade acceptance. Real
busy/permission/owner loss, independently rotated actor/profile/remote client,
cancellation, unknown worker, closed/stored HTTP history, deployed production
IdP/TLS and current-source physical behavior remain open. The supported Stored
inventory's native-transport refusal is not removed or disguised. A single empty
session is not nonempty user-history, inference or project/engineering evidence.

Actual Windows worker/project identity, authoritative working-copy/repository/file
access and reviewed binding/dispatch remain required. Keep all original
[audit requirements](../AUDIT_EXECUTION_PLAN.md), phone-to-Hermes/local-AI handoff,
integrations/automation, helper/download/background/reboot and final-source signed
release gates. No contract baseline, source canary, assertion or permission is
weakened to obtain a pass.
