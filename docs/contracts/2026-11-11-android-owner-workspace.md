# Android real-owner workspace and host notifications

## Behavior corrected

The canonical channel previously treated an empty-session `cron.changed`,
`projects.changed`, `skills.changed` and other registered host notifications as
unreviewed transcript activity. A valid host notification could therefore close
a newly authenticated channel before a reviewed action was sent. The channel now
recognizes nine exact registered host types with an empty session ID, only the
`type`/`session_id`/optional object `payload` envelope and no sequence/authority
stamps. They remain bounded ignored observations. They enter neither transcript
activity nor an approval/work path. Session-scoped, sequenced, authority-stamped,
unknown empty-scope and malformed payload frames still refuse; the existing
notification flood, private-event and profile/instance/home guards remain.

Primary sources are the pinned Hermes
[event registry](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/tui_gateway/contracts/events.py),
[global event producer](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/tui_gateway/server.py) and
[session-less replay rule](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/tui_gateway/event_replay.py).
The registry/replay modules already have full source guards. No baseline,
handler replacement, compatibility waiver, endpoint or permission is added.

A controlled real-runtime reproduction fails on the old source207 APK with
GatewayProtocolException after the actual upstream broadcaster emits valid
cron/project/skill notifications. It sends no workspace mutation; read-only
reconciliation finds the old directory and zero mutation receipts. The fixed
source217 APK passes the same producer stimulus on both supported execution
pins and both Android APIs. This is separate from the earlier uninstrumented
pre-claim socket closure, whose exact cause was not captured and remains an
interoperability finding rather than being retrospectively assigned this cause.

## Real Android client through an actual owner

The tracked `compatibility_fixture_owner_workspace_tcp.py` starts the actual
GatewayRunner, reserved multiplexed profile authorities, native control pipe,
API/dashboard lifespan and supported ready publication under a fresh isolated
E: home. No trusted Actor, identity, dispatcher, snapshot or ready reply is
substituted. The only IdP is the Git-bound upstream StubAuthProvider. Windows
DACL/SID/session checks and native one-use tickets remain real. One disposable
GUI session uses a synthetic unserved inference route and no enabled toolsets;
a real native viewer keeps it in the live inventory. No model or tool runs.

Android uses its actual ManagementClient and NativeLogin loopback callback,
S256/state/cookie browser driver, token exchange/refresh, identity/profile-home
checks and HTTP-minted one-use WebSocket tickets. A bounded `/dashboard/` prefix
forwarder calls the actual GatewayRuntimeAPI graph. It permits only the finite
auth/profile HTTP routes and read RPC shapes plus one exact fixture-session,
directory, UUIDv4, revision/generation-fenced workspace mutation. It refuses
submission, another resource/path/profile, changed payloads and extra writes.
No production credential/home, Pi/Spark service, worker or server default changes.

The test explicitly reviews/adopts the live session, checks its empty history
and old directory, approves one workspace change, checks the exact ACK and
redacted persisted receipt, rejects reuse of the consumed review, closes/reopens
an actual Room database file, and obtains a separately reviewed fresh attachment
whose directory/revision/generation agree. The mutation receipt remains honestly
acknowledged: this test adds no automatic app confirmation behavior. Saved
credential preferences remain unchanged. Each failed/unknown outcome is retained;
no automatic replay, process kill or output-root reuse occurs.

Hermes's Stored picker feed requires a native-registered transport. Both tested
HTTP authorities return no eligible stored row even while the native viewer is
open. The test asserts that refusal and uses the supported Live scope. It does
not manufacture legacy eligibility. An empty canonical Stored feed does not
prove the host has no saved chats; regular Chat history uses another reader.
Closed canonical/legacy HTTP history access remains required audit work.

## Measured final-source checks

Build source217 has digest
`190bfe65287e67cd369ebe9b3fc542de8f997a1aaa26942ee1cb3f87a08ce5ae`.
Required checks pass **956 unique JVM cases / 93 reports**, both APK assemblies,
zero-error debug/release lint (50 / 49 warnings), the original debug signer,
all four native ABIs, load alignment and absence of bundled model weights.
Six new wire methods cover all nine exact host types and five rejection shapes.
The script suite passes **244 cases**, including twelve fixture write/metadata
boundary tests; checksum policy verifies 495 components / 1,071 artifacts.

- Application SHA-256: `07f484fbfc9c9f1b7f47dae4f99c344e1ccdcc8b88c09f3649c86c6e3378403c`.
- Test APK SHA-256: `3503aa6fb79b2184d90c0b2c54fad0a47a12a849527bd4fb371d2b451eeb4f9c`.
- Final tracked fixture SHA-256: `e7950b6631fbd94c86b7e161ecc057fa981b2165b6f4eaabac5b1942a214766c`.

All compiled inputs still match the frozen build. The one subsequent ancillary
fixture edit adds the explicit host-signal flag and is independently hash-bound;
documentation is added after measurement. No application/test/Gradle/native
source is changed after its build proof. This is not a production APK release.

Fresh selected emulator runs pass **31 methods on each API 26/API 37**:
15 viewer UI, 11 authentication/Room/workspace and 5 storage. Native keyboard
settings are restored to their exact original values. These are selected methods,
not the complete standard Android suite or physical-device acceptance.

The final tracked `--emit-host-signals` matrix uses the same verified APK pair:

| Immutable Hermes execution pin | Android API | Git-bound source modules | Observed public host signals |
| --- | --- | --- | --- |
| `3b421578588243f920886e6fe79a6faa87ea026e` | 37 | 1,431 | 21 |
| `3b421578588243f920886e6fe79a6faa87ea026e` | 26 | 1,431 | 21 |
| `2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` | 37 | 1,397 | 21 |
| `2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` | 26 | 1,397 | 21 |

Each is one distinct passing Android method with eight scoped assertions/checks,
seven authenticated sockets, one workspace mutation and two fresh attachments/
replay reads. The three explicit public notifications come through the real
upstream broadcaster on each describe; they are controlled stimulus, not claimed
natural changes to cron/projects/skills. No readiness, identity, prompt, transcript
or ACK is fabricated. Metadata-only captures prove all three signals reached
each socket; exact method/APK identities and owned reverse-map cleanup are checked.
Two earlier normal latest-source runs also pass on API 26/API 37 without this
stimulus, using the preceding tracked fixture bytes retained in the frozen build.

After actual child exit, all four final supervisors independently verify healthy
read-only SQLite stores, the one exact session CWD/revision/generation and one
durable receipt whose full result equals the observed ACK. Both profiles have
zero messages, admissions and rooms; the sibling has no session. Every imported
source module matches its immutable Git blob and all final boundary denials are
zero. All four return **2 / CORE_PASS_SHUTDOWN_FINDING_OPEN** because the genuine
upstream reaper still writes a heartbeat after cached DB closure/reopen. That
warning is neither suppressed nor relabelled as complete lifecycle acceptance.

## Reproduction and retained failures

Use an existing native-Windows Hermes interpreter/dependency site and a clean E:
checkout at one of the two allowlisted commits:

```text
python -I -S -B scripts/compatibility_fixture_owner_workspace_tcp.py --source-commit <exact-pin> --source-root <matching-E-checkout> --dependency-site <existing-site-packages> --work-root <fresh-E-output-root> --android-api <26-or-37> --emit-host-signals
```

The private ready manifest binds its PID, origin, profile, session, directories,
API and harness hash. Target only the explicitly verified owned emulator; create
one owned adb reverse mapping and pass its values as the `signalGroveOwnerWorkspace*`
instrumentation arguments to `CanonicalSessionAttachmentContractTest#ownerWorkspaceRoundTripUsesAuthRoomAndIndependentFreshReadback`.
Verify installed APK identities, exact one-method terminal output and its QA
receipt before writing the fixture's android-result.json. Its final process/store
checks must also be reviewed. Never reuse an uncertain fixture root. Without an
external fixture the same Android method runs a labelled bounded mock, with no
assumed/skipped success. The upstream test fixture retains the
[Hermes MIT attribution](../licenses/hermes-workspace-fixture-MIT.txt).

Private checkpoints retain source201's incorrect Stored eligibility assertion;
the old Android-17 UID-label preflight failure before installation/requests;
source207's pre-claim closure with zero mutations; strict Windows `ver` metadata
refusals; and the source207 controlled-host-signal red case. None is overwritten
or called a pass. The normal prototype success and two baseline runs remain
separate from the four final tracked executions. Public catalog fetching is
outside this isolated test: its supported manifest setting is disabled and only
five exact public, credential-free GETs from named catalog call sites are
intentionally refused before DNS. Unknown URLs/callers, credential headers and
other network/process activity remain fatal. Exact platform-version subprocess
probes remain blocked and recorded; no subprocess permission was added. No
catalog health, model readiness or general integration success is inferred.

## Still required

Exercise real Android lost-ACK, cancellation, owner loss, busy/permission/
unknown-worker and profile/remote-client recovery without replay. Complete closed
stored-session access through supported authenticated HTTP surfaces, production
IdP/TLS/forwarding and deployed-version acceptance. Retain the unattributed earlier
pre-claim closure and upstream shutdown finding until their own evidence closes
them. Then finish actual worker/project identity, authoritative Windows/NAS
working-copy binding, repository/file access and reviewed dispatch.

Phone-to-Hermes handoff, current-source physical phone/helper/download/background/
normal guest reboot, integrations/automation, final-source signed release and
every original [audit requirement](../AUDIT_EXECUTION_PLAN.md) remain open. The
one empty disposable conversation proves no inference, nonempty user history,
worker execution, deployed Pi behavior or full fleet lifecycle.
