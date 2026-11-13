# HTTP operator binding review and complete Android gate - 13 November 2026

## Reproduced behavior and proposed correction

The actual current-stable HTTP probe now reproduces an optional coordinator
gap: successful PKCE/refresh and a consumed socket ticket produce a verified
host-owner identity without native profile/instance fields. The repository
adapter refuses describe before any worker call. All 20 inspected authentication/
authority source modules on the running Pi match the exact pristine 3b target;
this is a code-binding observation, not a production sign-in test.

A disposable candidate binds an instance-less host-scoped login to the identical
current registry authority, retaining owner/operator/profile/transport checks.
Explicit instance claims, malformed native bootstrap, API-principal and foreign
profile cases remain strict. It passes 16 actual credential/socket checks with a
synthetic worker, plus 18 native authority/contract checks retaining all 12 original
outcomes. The original and candidate HTTP probes both retain one real SDK exit
warning; the supervisor's lifecycle verdict stays nonzero. No warning is hidden.

Automatic approval review rejected applying the persistent authentication policy
change because its exact scope was not explicitly authorized. The reviewed
source-and-test patch is proposed only; the repository adapter, deployed Pi,
operator policy and grants are unchanged. Explicit approval is required before
applying that fix. Authorized probe improvements pass 481 portable cases; all
inputs of the prior 72-case native Windows gate remain byte-identical.

The newer full Android source 4444 gate is independently reconciled: all four raw
partitions reproduce the hosted summary and actual same-run APK hashes, with
1,141 API26  / 1,142 API37 distinct positives. Original clarification/scrolling and
the unique Chat-route counterexample are positive on both. The 12 external-model
methods, final-current-head/phone gates and original failure causes stay separate.

The [reviewable patch](../proposals/2026-11-13-http-operator-binding.patch) is
**not applied**. The review artifact uses zero-context hunks; apply only
against the exact bound base (`--unidiff-zero`) after approval. This avoids
whitespace-only context records without changing any tested source or outcome. It changes the optional native adapter and adds six regression
cases to the existing constructor probe. Actual HTTP/ticket acceptance is tested
by the new explicit `--coordinator-http-reads` mode on
[the owner workspace probe](../../scripts/compatibility_probe_owner_workspace.py),
with [a bounded fixture](../../scripts/coordinator_auth_fixture.py). The ordinary
mode retains its original behavior. With the unchanged repository adapter, the
new mode intentionally reproduces the valid-login refusal; it is an investigation
mode, not a declared passing production capability.

The candidate accepts a missing instance field only when the identity is not a
native bootstrap, has upstream verified host scope, still resolves to the actual
OS owner and its exact profile resolves to the identical current authority object.
It does not infer an owner from an API key or phone JSON. Explicit null/wrong
instance values never fall back. The existing verified operator capability,
actor/profile equality, registered live transport, serving/retirement and fresh
grant checks remain. Context is checked again after I/O; policy change drops
contents and retains request_submitted. This permits signed-in owners to reach
only explicitly enrolled worker reads; the protected grant remains decisive.

## Actual fixture evidence and limits

The new path uses the official runner, native control channel, synthetic-IdP
authorize/code exchange/refresh, real issued one-use tickets, real TCP WebSocket,
AuthorityConnection, strict contract registry and dispatcher. No Actor or
operator Boolean is injected. The optional handler extension uses its declared
two-anchor source adapter in memory; SDK files stay unchanged. The worker route
is synthetic and bounded, with no external connection, file read, inference,
tool or execution admission. The fixture creates only its own disposable workspace
session and reviewed cwd change, preserving the original owner-workspace checks.

The 16 credential/socket checks comprise13 HTTP/socket cases and3 genuinely
issued native-ticket cases. They cover describe/prepare/list/preview; completed-ID refusal;
profile-bound sibling, structured API-subject and worker-adoption nonoperator
credentials; host-scoped sibling without a grant; foreign project; client scope/
identity claims; missing operator; in-flight grant change; revocation and ticket
replay. The separate 18 constructor checks retain all 12 prior outcomes and add
current-authority acceptance without the native field, explicit-null/native-
bootstrap/profile-bound refusals, unregistered authority and closed transport.
Those constructor identities remain explicitly synthetic; they do not replace
the actual HTTP path.

Both original and candidate real-runner processes finish their core protocol
checks but emit the same one SDK heartbeat/DB-reopening warning, differing only
in the fixture DB path. Raw diagnostics remain unchanged and the lifecycle
supervisor reports CORE_PASS_SHUTDOWN_FINDING_OPEN (exit2). These are functional
protocol observations, not a clean SDK lifecycle pass. No heartbeat patch,
callback suppression or warning waiver is used here.

Earlier attempts are retained: two pre-login long-DLL-path failures; a denied
helper bytecode-cache lookup; a real refusal to issue a bare API label; and the
valid HTTP rejection. The probe now preserves the already-established short
dependency alias while keeping the same canonical read fence, and loads the
exact helper source without permitting a bytecode-cache directory. The valid
API-principal case uses Hermes' own structured-subject encoder and a genuinely
issued ticket. No failed or uncertain production operation is replayed.

| Binding | SHA-256 / result |
| --- | --- |
| Unchanged repository adapter | `0eaea73ca96593731f395e583950a1e1378dfd320b3bae26eefe778b2f1ca97f` |
| Tested disposable adapter | `e2ea0efdf8294fa8cce36cd68ba06cbefe9d48c0caf53d4acc9bf81bac85fa52` |
| Proposed source-and-tests patch | `c3721b91c6bb6fd2f4b2cfbf5a621bb9b0aaf0a76ac48d807bb754660cebcb79` |
| Actual candidate HTTP/core report | `80aa6a41e036510d125a8303c47ad57c3a4c28248f2493cef55a8ce63b32d2de` |
| Candidate18-case constructor report | `7ae34005d6dc78520aae0aef1e4e3ebab0acc76ce5bb5f960fe105c01f3a257d` |
| Failed actual HTTP report | `af9cee02e249f6200c725db704163109f667936a1d6962c4cd4db180de878cbd` |
| Current 481-case policy log | `88168b823ebb6d877627835750423d3088833d4ab8d6976a51ae1c3b91905534` |

All 20 directly inspected Pi auth/authority source hashes match immutable upstream
`3b421578588243f920886e6fe79a6faa87ea026e`; the active generation, stamp and services
are verified without reading config, credentials or jobs. This does not certify
every custom/transitive runtime module or execute the production IdP. Native
HTTPS/Android, actual Pi operator policy and authenticated live-worker replies
still need their own end-to-end acceptance. The earlier installed-helper Pi
[read-client trial](2026-11-13-live-read-client-update.md) remains a separate
administrative-context proof.

## Independently reconciled source 4444 Android cohort

[Run43506396178](https://github.com/DionCroft/Signal_Grove/actions/runs/43506396178)
at `4444c308258fc8c7524535388003bc26ea02123c` passes the strict unmodified verifier.
All six archive hashes/sizes verify. Four complete raw inventory/execution pairs
are disjoint within each API and union to 1,141 API26 (540+601) and 1,142 API37
(541+601) positives; no failures, skips or missing native finishes are admitted.
The hosted summary is reproduced exactly. The actual build artifacts are:

| CI debug APK | Bytes | SHA-256 |
| --- | ---: | --- |
| Application | 92,606,158 | `0ace3640aa7fb155706804121212deb8ad8f39c8d81b08942f4b2bd11eb1f0f8` |
| Test companion | 8,028,751 | `826b2cca7a057b92235b9512fdc6133b0b40bad30144bf95f7ff9aae9acf7b1f` |

The reproduced complete report SHA-256 is
`1bd129237d7c6b6ca4ae35021d46879f6db2f7a3b02685237f05c087c357f0a4`.
The original clarification and scrolling methods and the current unique Chat
route control have positive starts/finishes on both APIs. An extra review first
looked for the pre-c814 controlled-test name; exact raw/source identities proved
the intentional unique-route rename. That review was corrected without rerunning
or waiving tests. Earlier failures and unproven root causes remain retained.
These CI debug APKs are not public production APKs. The 12 external-model methods
are excluded explicitly, not accepted; final-head and physical-device acceptance
remain separate. Beta.9/code114 and its published six assets are unchanged.

## Next action and approval scope

Obtain explicit approval for the exact proposed authentication-source patch
before applying it, then validate/commit the identical tested source and negative
cases. No Pi deployment, operator enrollment or service restart is included in
that source approval. Continue the separately reviewed native coordinator plan
and actual Android workspace/worker/file/NAS acceptance. Independently finish
stable/prior/main semantics, pristine SDK shutdown and the original integration/
automation and final production-phone/release ledger. Preserve unknown work.

The full [goal](../GOAL.md) remains active, including every original audit and all
four priorities. Doctor/key and graph identity/index findings, upstream semantic/
dependency review, pristine shutdown, actual integrations and final device gates
remain open. Automatic approval review holds only the proposed persistent auth
change; it does not close or cancel the remaining authorized work.
