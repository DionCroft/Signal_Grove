# Canonical Linux lane, Android reports and native side environment

14 November 2026. The full goal and all four priorities remain active.

## Independently verified Android cohort

[Run 43547210524](https://github.com/DionCroft/Signal_Grove/actions/runs/43547210524)
at source `ed2c996df479ca106494a53ca3883355ae1a6342` completes its verify,
four device partitions and standard-suite summary. The six original artifact
ZIPs are checked against their API-reported sizes/SHA-256 values before safe
extraction. Independent aggregation exactly reproduces the hosted summary.

| Platform | Positive cases | Disjoint shard sizes |
| --- | --- | --- |
| API26 | 1,141 of 1,141 | 540 + 601 |
| API37 | 1,142 of 1,142 | 541 + 601 |

Raw positives include the original clarification and scrolling checks and
`homePickerKeyboardDismissalUsesTheUniqueChatRoute` on both APIs. Actual debug
APK bytes match every partition and the summary, rather than accepting manifest
claims alone:

| Artifact | Bytes | SHA-256 |
| --- | --- | --- |
| Debug app | 92,606,054 | `e9181e87c388b53fbf323ffcdc2ad0be2abb023d87b87b48d0722c10338e77fd` |
| Test companion | 8,028,751 | `0cc4f3eac4128180b2d50963aade99a8e8912e4ef7af4e86c38b49321c60e669` |
| Independent complete report | - | `59db700c92b51b291d39c8d9613b627f078da521ab0c7dd56d768db128f9849a` |

Only Markdown changed between this cohort and the preceding main checkpoint.
This is acceptance of that exact cohort, not a claim of a final current-source
or signed production upgrade gate. The 12 external-model methods remain
explicitly excluded. Original API26 failure causes are not proved by a later
positive; the older failures remain recorded. Beta.9 and its public APK remain
unchanged.

## Actual Pi side test environment

Within the already approved isolated staging/PM scope, supported
`pm.testenv.ensure_testenv` builds a separate environment with the declared
dev/test groups and all/discord/agentic-contextgraph extras. Actual ARM64
imports include pytest, FastAPI, websockets, ContextGraph and PyYAML.
No raw pip/uv, copied environment, app-facts selection or live activation occurs.

The pre/post gates retain the entire 23,628-file candidate snapshot, selected
app facts, old live stamp/facts, all 79 checked live source paths, both service
identities and all existing ancestor modes. The parent's write fence reports
zero violations. The selected app facts still have SHA-256
`1e3797bd3438272b0cab9e21bdfa21d814410574d89aacb50253d3c3aa4d2151`.
The local complete native stdout receipt has SHA-256
`59ff71c7d635964f9da08acd6c1d14fe95830e5caa01b6b5cf7dc0aa68657f89`;
private paths and operation receipts stay outside Git.

This prepares imports for actual HTTP/ticket acceptance. It is not that
acceptance, a pristine SDK run, a protected-policy positive, live worker access
or an Android release check.

## New pristine Linux canonical lane

The source-only `Pinned Hermes native SDK` workflow checks out the complete
unmodified SDK at `3b421578588243f920886e6fe79a6faa87ea026e`, independently from
this app. Its fresh disposable checkout uses a local Git info attribute to
materialize PS1 as exact Git blob bytes, overriding only the SDK's default PS1
checkout conversion. No tracked attribute or source content changes.
It verifies all 23,586 source-file bytes against Git blobs and native
executable bits before setup and after the run. Official pinned SDK PM setup
builds its separate dev/test environment. No deployment credentials are supplied.

The lane invokes the unchanged `scripts/run_tests.sh` on the complete
`tests/gateway/test_session_plugin_rpc.py`, with zero file retries. Its actual
Linux runner owns the SDK's normal ephemeral test scratch. This avoids moving
canonical test material outside the separately approved Pi generation.
No platform is faked, SDK source patched, assertion removed or warning hidden.
The SDK's loopback fixture supplies its chart responses, not a live model.

Acceptance requires runner exit 0, the exact six positive JUnit case identities
including both in_process and host parameters, no skip/failure/error, an actual
Linux test environment and an identical whole-source snapshot. Full canonical
logs, JUnit, exit status, environment identity and source map are retained as one
artifact. Six evidence-parser tests refuse misleading totals, duplicate/missing
cases, wrong classes/suites, skipped POSIX coverage and XML entity declarations.
The local portable compatibility suite passes 499 tests (the prior 493 plus these
six); this count does not add native cases or the overlapping protocol subset.

At this source-preparation checkpoint the hosted canonical lane is **pending**.
Do not close the older canonical POSIX gap until the actual run and original
artifact bytes are independently verified. Even a six-case positive will not
close the separate owner-runtime shutdown warning or the full upstream review.

## Next acceptance boundaries

Verify the new hosted canonical lane, then finish the isolated actual Pi
HTTP/PKCE/refresh/ticket/WebSocket exchange with the existing 16 coordinator
behavioral outcomes retained. Respect real Linux IPC and scratch boundaries.
Prepare the exact activation/permission/unit/launcher/recovery transaction only
after those gates; live activation still requires its own reviewed approval.
Keep the full integration, project/execution adoption, worker grants, automation,
phone lifecycle, doctor/graph and signed-release requirements in the audit ledger.

## Initial hosted preparation failure retained

[Run 43557182054](https://github.com/DionCroft/Signal_Grove/actions/runs/43557182054),
source e9f9f010, stops at the strict activate.ps1 Git-blob check, before PM setup
or any canonical test. Its SDK default PS1 checkout conversion was not removed
by the first checkout-index preparation. No test positive is claimed. The
corrected fresh-checkout preparation uses Git info attributes to disable only
PS1 text conversion and materializes those paths directly from their exact
pinned Git blobs. The whole-source guard and six canonical assertions remain
unchanged. A new source run, rather than replay of that failed run, is required.

## Second pre-test refusal and stronger source proof

[Run 43557440768](https://github.com/DionCroft/Signal_Grove/actions/runs/43557440768),
source bf8798b7, refuses at the initial Git-status gate, before PM/test execution.
It is retained without a test-positive claim. That status-only result does not
establish which SDK byte changed. The revised guard records raw Git status and
requires the complete pinned HEAD tree to equal the exact index path/mode/blob
map, then every working file to equal that pinned blob and native executable
mode. This explicit three-way proof is independent of text-filter/stat summaries.
Wrong index blobs/modes, duplicate/unmerged paths and every raw working-byte
change remain refusals. Two additional evidence tests cover these cases, bringing
the portable suite to 501 positives. All SDK tests/assertions and the pinned SHA
remain unchanged; hosted acceptance is still pending.

## Accepted pristine canonical Linux result

[Run 43557755078](https://github.com/DionCroft/Signal_Grove/actions/runs/43557755078),
client source `94b3ff5c5778f27833e1cfc9193983c979dcd86c`, is independently accepted.
Actual Linux/x86_64 Python 3.14.9 and pytest 9.1.1 use the supported PM side
environment. The unchanged canonical runner exits 0 with six positives, zero
skips/failures/errors and zero retries. Both in_process and POSIX host plugin
frontend cases run, alongside session verbs, events, namespace and profile checks.
The full original SDK file and its assertions are unchanged.

The downloaded original artifact is 1,119,115 bytes, matching API-reported
SHA-256 `625c3750595cb115230359048c55c98dc01bfeed5d86f3aba28bb82ccb25025c`.
Independent verification reads its raw JUnit, canonical exit/log and native
environment identity. It then derives all 23,586 expected SHA-256 values from
the immutable local pinned SDK's Git blobs: the entire hosted before/after
source map equals that independently derived map. The HEAD/index/working-byte
guard is unchanged by any hosted observation. The original Git-status data
reports 39 PS1 paths before and after; all their bytes/modes/index entries match
the pinned tree. This observation is preserved rather than treated as source
drift or silently removed.

| Verified evidence | SHA-256 |
| --- | --- |
| Canonical full source map | `52a2dddf97a3fa39251a666fa8819f05b3185d249f9e14005f66247ba217fbf2` |
| Raw six-case JUnit | `768d3ba94a526ba000b8c0635d65407e606b5e04003c16a53a7b263f52a838ed` |
| Full canonical log | `e2773b43e546240919611988362f547b7dd6038cb21b693a4887b25eed2c6a6f` |
| Native accepted report | `8e4b8f6ed27680690222a4c20818389d0c9e063aca6c7a4a77ee333124dffe3a` |

This closes only the previously incomplete canonical plugin-RPC POSIX file at
stable3b. It does not turn earlier Windows skips/failures into successes and does
not close the different owner-runtime process-exit shutdown finding, broad
upstream compatibility review, Pi activation or any original integration gate.
The canonical file ran on its real Linux CI host, not on Pi ARM64. Pi's current
18 constructor cases and separate PM/testenv acceptance retain their own scope.

A fresh independent Pi read-only postcheck after the side build still verifies
both live services, old stamp/facts, 79 selected live source paths, candidate
facts, absent live policy/extension and existing ancestor modes. Its result
matches the earlier conservation digest exactly; no activation occurs.

Next: complete isolated actual Pi native/HTTP ticket, PKCE/refresh/WebSocket and
the existing 16 coordinator outcomes with a reviewed real-Linux IPC fence.
Keep source/runtime/profile/UID checks and all assertions. Resolve the short
OS-owned IPC path documentation before creating that fixture; do not place
ordinary maintenance scratch outside the approved generation. Then prepare the
exact separate activation/permission/unit/launcher/idle/recovery review, followed
by actual HTTPS/Android/worker/execution and the full original release gates.
