# Actual-owner project conversation conformance

## Runtime boundary

The Android production repositories run against unmodified, immutable upstream
Hermes sources at `2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` and
`3b421578588243f920886e6fe79a6faa87ea026e`. The actual multiplex owner,
profile authorities, dashboard lifespan, native PKCE/refresh, bearer identity,
ticket minting, profile routing, project handlers, session store, project tree,
Git probes and canonical stored-session inventory execute. A synthetic IdP and
renderer placeholder are the declared fixtures. No production account, Pi,
Spark model, worker service or server default is changed.

Eight finite cases cover each source × Android API26/37 × gateway/Webapp.
Each disposable profile has one project with its own root, a retained discovery
cache entry and seven closed sessions. Two match. The other five are outside the
project, archived, scheduled, a child or empty. The same `shared-project-fixture`
ID in both profiles has distinct titles, contents, reasoning sentinels and cwd.
Existing content is seeded before the client; it is never supplied as a fake
upstream response.

The closed outer boundary forwards only `runtime.describe`, exact profile-bound
`projects.list/get/project_sessions` and `session.list(scope=stored,limit=200)`.
Grouping uses the production limit 500. Only seeded project IDs can be looked up;
a foreign ID reaches the actual typed refusal. Auto-discovery/cache-reconciliation,
creation/update, adoption/resume, workspace mutation, tools, model selection,
approvals, worker execution and other methods are rejected before real dispatch.
Responses pass through unchanged. Auth routes use the real implementation;
URLs retain the configured dashboard prefix. Scoped socket tickets are allowed
only for the exact field profile. Missing named-profile transport cannot fall
back to launch-profile projects.

The Android case reads inventory/detail/grouping, checks exact identities and
eligible IDs/cwd/message counts, prepares pure stored-session reviews and refuses
foreign projects and frozen wrong-home contexts. It never consumes a review or
resumes a session. A gateway-only address supports default and explicitly refuses
field; Webapp supports both. The standard-CI invocation of the new method uses
the declared local HTTP/PKCE/legacy project fixture, without a skip or a claim
that a mock establishes actual-owner acceptance.

## Independent effects proof

After each child exits, read-only SQLite checks cover both profile state stores
and project stores. Independent host code reselects every column of every seeded
session/message and all project/folder/meta/discovered-repository rows, preserving
SQLite BLOB types. Digests match the pre-client snapshots. Totals across the
matrix are 112 sessions and 192 messages in 16 profile stores. Each discovery-cache
entry remains present; both workspace markers retain exact contents.

All admissions, hosted rooms, worker executions, turn leases, compression locks
and mutation receipts remain zero, and all sessions remain closed. The RPC log
contains exactly 116 descriptors, 12 project inventories, 48 project details,
12 grouping reads and 12 stored inventories: 200 frames, no resume/mutation/work.
Native credentials are unchanged. Each owned ADB reverse is removed without
changing other links. Debug app/test UIDs and original signing are retained.
This proves bounded read-only navigation and prepared reviews; it does not
establish a fleet worker's identity, project execution root or file authority.

The real reaper still reports a heartbeat write/reopen after database close
at atexit. Each of the eight supervisors returns CORE_PASS_SHUTDOWN_FINDING_OPEN
(exit 2). This warning is retained, not suppressed and not converted into clean
lifecycle acceptance. A host-only reconciler used the wrong source-commit JSON
field on its first verification; complete existing cases were reverified from
the supervisor field without replaying their Android/native calls.

## Build and retained evidence

Source474 freezes 760 files, digest
`7ef0a3941f3bd60a610f06109bb1075dc20fa7af57207f9cac2b88df5a65202a`.
Required unit/lint/debug application/test tasks pass: 1004 JVM methods/96 reports,
311 policies, lint 0 errors with 51 debug / 50 release warnings. Original debug
certificate and all 32 packaged native-library ABI/alignment checks pass; no
GGUF is packaged. Both normal project contract methods pass on each Android API.
The new native case runs once for each of the eight independent owner fixtures.

Private artifacts stay outside Git: `local-helper-source474.json`,
`local-helper-build474.json`, `local-helper-proof474.json`,
`owner-project-policy477.log`, both `project-owner-matrix479-api*.json`, the
first `project-android475-*` and remaining `project-android479-*` roots,
`android-project-<caseId>/`, `project-normal481-api*/` and
`project-owner-proof482.json`. Raw instrumentation is strictly parsed, then
independently matched to the exact selected method and APK/source identities.

## Hosted UI finding and remaining scope

Diagnostic main2581 hosted run 43104683515/API26 job 130814733331 completes 1125
starts, 1124 unique positive methods and one failure, with no timeout. The failed
case is `largeBilingualWorkspaceFormKeepsKeyboardActionsReachableAndCancelHasNoEffect`.
Its unchanged geometry assertion reports Review bottom 1011 versus native safe
bottom 827. The screenshot shows the Gboard first-use Google Search help bubble
covering the lower caption; reported keyboard inset top is 1053. The test correctly
refuses complete visibility. This is not a proven general app-layout cause.
Default-keyboard first-use readiness must be reproduced and controlled before
new acceptance. No assertion, caption, consent or zero-effect oracle is weakened.

Artifact 13898223588 SHA-256:
`33bf1684cf0cac512bfae358ffe656b95af8b81839ca8dc5fee608212eeead55`.
The ZIP, extracted failed screenshot and parsed diagnostics are retained as
`diagnostic-main2581-api26-478.*`; this remains a failed whole-suite run.
Earlier main2fde's full pass stays bound to that source. Latest upstream source
review still fails and its findings are unwaived. Actual production IdP/fleet,
worker/root/files/dispatch, physical/model/lifecycle, broader integrations,
advisories and signed release remain required. The full goal stays active;
beta.8 remains the published preview.
