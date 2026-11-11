# Workspace refusal guidance and actual transcript guards

## Behavior

An exact workspace-mutation refusal can now show closed translated guidance for
`session_busy`, its `field: compression` variant, or `permission_denied`. The
canonical channel binds it to the currently outstanding workspace RPC ID;
code 4001 and matching message/data reason are required. Unknown, contradictory,
wrong-code/field and unrelated inventory/handshake errors remain generic. No raw
reason, detail, path or credential is retained by the advisory exception.

This is advice only. Repository receipts, one-use approvals and one-send behavior
are unchanged. Every attempted unsuccessful workspace change still retains its
unknown receipt and shared home/alias barrier. A busy report does not prove no
write happened; a released host lock or inventory refresh does not settle the
request. Independent exact host inspection and separate approved local review
remain necessary. That review neither retries nor confirms remote execution.

Primary source review covers the official [API reference](https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server/),
pinned [RPC refusal serialization](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/gateway/session_controls.py),
[mutation coordination](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/gateway/session_mutations.py)
and [transcript guards](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/hermes_state_mutation_guards.py).
The two reviewed execution pins have the same relevant busy/lock mapping. No
endpoint, source-watch waiver, contract rebaseline or additional grant is added.

## Eight actual-owner Android cases

The finite actual-owner fixture adds mutually exclusive `--workspace-refusal
turn-lease` and `--workspace-refusal compression-lock` modes alongside the prior
lost-ACK option. It retains the real GatewayRunner/bootstrap, profile authorities,
native owner/tickets, dashboard API lifecycle and upstream synthetic IdP. Android
uses its actual HTTP PKCE/refresh/identity/home/ticket/canonical client and Room.
No Actor, dispatch, ready frame, refusal or database result is substituted.

Immediately before forwarding the one exact reviewed mutation, the fixture
acquires its own real database turn lease or compression lock through the upstream
API. The genuine dispatcher evaluates the real guard and returns `session_busy`.
Only after observing that exact bound refusal does the fixture release its owned
lock, before forwarding the refusal to Android. It does not admit a model/tool
turn or modify a running user's work. All paths/stores are disposable test homes.
The same finite method/payload/session/directory/profile/revision/generation bounds
apply. Controlled registered host notifications use the genuine broadcaster.

| Execution pin | Guard | API 26 / API 37 Git-bound modules |
| --- | --- | --- |
| `3b421578588243f920886e6fe79a6faa87ea026e` | Turn lease | 1,422 / 1,422 |
| `3b421578588243f920886e6fe79a6faa87ea026e` | Compression lock | 1,422 / 1,422 |
| `2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` | Turn lease | 1,387 / 1,387 |
| `2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` | Compression lock | 1,388 / 1,388 |

Each case is one passing native method with ten scoped checks: real sign-in and
home verification; reviewed Live attachment; exact typed advisory refusal; unknown
receipt with empty result and redacted directories; actual Room close/reopen and
startup recovery; no new same-connection claim; shared alias/home barrier; blocked
new adoption; available read-only inventory without uncertainty resolution; and
unchanged credential preferences with no local review. Standard instrumentation
runs the method against a labelled bounded mock rather than skipping it.

Every case has one mutation attempt, one initial resume/replay read, four sockets
and twelve controlled public metadata signals. Installed identities, exact
one-method positive/no-skip output, QA fields and owned reverse-map restoration
are checked. After actual child exit, read-only host SQLite proves the original
CWD/revision/generation, zero mutation receipts, no remaining turn/compression
locks, and zero messages/admissions/workers/rooms. The sibling has no session, admission, room, message or remaining lock.
An independent read-only copy of each exact Android fixture DB, including its WAL
when present, passes quick_check and contains only confirmed initial adoption plus
the unknown workspace receipt. No host evidence is used to clear the phone row.

Every actual owner retains **CORE_PASS_SHUTDOWN_FINDING_OPEN** and a nonzero
supervisor/shell outcome. The genuine reaper heartbeat after cached DB closure
remains recorded. Six commands explicitly propagate exit 2; the first two native
PowerShell wrappers report 1 while retaining the same core/shutdown finding and
child/store proof. These are scoped transport/guard passes, not full lifecycle
acceptance. No production home/credential, Pi/Spark default, worker or model changes.

## Source, artifact and selected-suite checks

The actual-owner matrix used source253, digest
`8a5306b86aa3c785962e29c3147067ee1e7b01c123da37d2bf30f0846fb6f3d5`:

- App SHA-256: `1c21a061525eaa6d3596c7d148c9628eba4f313a1e6e843b2e9065f5184cbea6`.
- Test SHA-256: `d82ff9a21e2d0e06130d0bfa73e7530235e69038b917eb5a61d4e986b69acc01`.
- Fixture SHA-256: `a788a662076044abac9cabe2693825ec84aae3c8fd4b1606ed8b89ae5bc75e1f`.

Its first UI run exposed an invalid new assertion: an attached viewer must retain
its installation guard. Both APIs failed that assertion. An existing API 26
consent test also observed disabled Confirm after its tap; the precise cause was
not captured and remains unattributed. These failed complete runs are retained.
The corrected test asserts an active guard until Close, then its release. Consent
now requires an unchecked box, no native keyboard, a stable checkbox position and
one tap that must visibly check the box before Confirm is inspected. There is no
retry or weaker consent/write assertion. New UI captures cover all three labels.

Only this UI test file changed between source253 and final source267, digest
`737214156b316fd410bca9e7f6e58e0727ad77467f2574be9aa11f6b0eecb2c2`.
All product/native/Gradle inputs and the owner method/fixture remain identical.
The final required build passes **959 JVM cases / 93 XML reports**, both APKs and
zero-error debug/release lint (50 / 49 warnings). Policy checks pass **244 cases**;
original signing identity, all four native ABIs, alignment and no bundled weights
are verified. All frozen files match before documentation is added.

- Final app SHA-256: `b4e20b3024b3de2e368bdd03b94b7d8cf72948fde018453256d3386420d8066e`.
- Final test SHA-256: `d3859783e6ccd621287f7b988a3ec9df4ed6374ec5f714b2817ffd402472e877`.

Fresh final selected suites pass **35 methods per API**: 17 viewer UI, 13
auth/Room/workspace and 5 storage. Native keyboard settings are restored exactly.
All twelve application DEX entries, resources and manifest are byte-identical to
the actual-owner app. Twenty native library entries rebuilt under a new context;
the complete APK hashes differ. The eight real-owner executions therefore remain
associated with their original APKs, not relabelled as final raw-APK acceptance.
No inference ran in them. These are selected suites, not full Android/phone gates.

Six English screenshots were inspected. The messages wrap and the Close action
is visible. Two API 26 captures include native window-transition dimming/ghosting;
those are not settled-rendering acceptance. Translation, animation, accessibility
and current-source physical UI acceptance remain broader release work.

A private independent verifier initially used the wrong report key and failed
before phone copying. Correcting it to the actual schema reran only read-only
verification, never a mutation. Unknown/failed evidence is not overwritten.

## Reproduction and open work

Run the tracked fixture with an existing native-Windows interpreter/dependency
site, clean allowlisted checkout, fresh E: output and one guard:

```text
python -I -S -B scripts/compatibility_fixture_owner_workspace_tcp.py --source-commit <exact-pin> --source-root <matching-E-checkout> --dependency-site <existing-site-packages> --work-root <fresh-E-output-root> --android-api <26-or-37> --emit-host-signals --workspace-refusal <turn-lease-or-compression-lock>
```

Bind its private ready manifest and one owned adb reverse to the verified emulator.
Pass the exact `signalGroveOwnerWorkspace*` arguments plus
`signalGroveOwnerWorkspaceRefusal` to
`CanonicalSessionAttachmentContractTest#ownerWorkspaceBusyRefusalRetainsUnknownAndBlocksAfterHostGuardReleased`.
Verify the exact terminal method, QA unknown state, process/source/store boundaries
and independent phone DB before counting acceptance. Never recycle an uncertain
root or resend to learn an outcome.

Permission guidance has wire/fake-repository/native UI coverage, not a real HTTP
permission-denied actor proof. Active/queued/unknown admissions and workers,
independently rotated actor/profile/owner loss, process death/reboot, cancellation,
closed/stored HTTP history, production IdP/TLS and deployed/physical acceptance
remain open. A real transcript lock is not a running-turn or engineering proof.
Retain the prior unattributed pre-claim closure and upstream shutdown finding.

Next test supported closed-session HTTP reads with nonempty fixture history and
real Android authentication, then finish permissions/recovery and actual Windows
worker/project identity, authoritative files/working copies and reviewed binding/
dispatch. Every original [audit requirement](../AUDIT_EXECUTION_PLAN.md), local-AI/
phone handoff/helper/download/background gate, compatibility review and exact-source
signed release remains required. No APK is released by this chunk.
