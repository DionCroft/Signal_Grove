# Published launcher and separate runtime bytecode inventory

14 November 2026. The actual SDK-published POSIX launcher is staged and exercised
on the Pi. Native owner-home startup succeeds; an unprepared home is refused.
These are finite isolated launcher gates, not live service/HTTP/worker/phone
acceptance. The full goal and all four original priorities stay active.

## Supported publication and real invocation

The unchanged pinned SDK `stage_launcher` publishes into a fresh isolated command
directory. Its exact shell tokens and generated Python program match the SDK
writer. It binds the permanent source's own managed Python 3.14.9 tool, ignoring
an inherited transient runtime override during publication. No transient store
is created. Both owner/borrowed-home descriptor invocations exit zero and select
the same reviewed source/tool. Descriptor success alone is not startup success.

The saved executable is then invoked normally, without changing its script,
interpreter flags, SDK bootstrap or callbacks: `--run-module site` exits zero in
the isolated owner home and shows its exact source-keyed application environment,
with user-site packages disabled. In a separate unprepared fixture home, the
same command exits one with the SDK's dependency-environment refusal. No repair
command is issued to that home and no production home is provisioned.

The fixture wrapper stops because it also expects an internal exception class
name in the friendly refusal text. Its exit-one receipt and false expectation
are retained. Independent read-only recovery checks the original complete logs,
actual child exit statuses and absent child processes; startup is not replayed.
This does not turn the failed wrapper into a successful run.

## Source purity and generated outputs

Normal launch creates 12 native CPython bytecode files in this cohort. They are
retained separately from all 23,628 reviewed source inputs and their native modes.
The source-only copy preparer remains unchanged and still rejects extra files.
The new `scripts/hermes_runtime_inventory.py` is a distinct read-only check for
an executed runtime, bound to the exact immutable reviewed sibling, one-field
tools stamp rebind and actual Linux CPython 3.14.9.

Every extra file must be a non-executable, ordinary native timestamp cache for
an existing reviewed Python source. The verifier checks magic/flags/timestamp/
size, bounded bytes/count, exact source hashes and every code attribute including
nested code filenames and line/exception tables against a fresh trusted compile.
It unmarshals for comparison without executing cached code. Other modules,
optimized/foreign/pytest caches, links, executable caches, empty shadow directories
and unknown outputs are rejected. It deletes nothing and imports no SDK code.
The existing source-only preparer and protected operator-policy reader are not
relaxed. A cache inventory is not a runtime authorization or race-safety claim.

Twenty counterexample tests include forged matching-header code, nested filename
and line-table drift, source changes/removal/mode drift, malformed/trailing
payloads, foreign/optimized/orphan caches, bounds, symlinks and executable output.
All 20 pass on the actual Pi with zero skips. The full Windows portable suite has
564 tests: 561 pass and the three actual-POSIX mode/link checks are skipped there.
Those same three pass in the native Pi run. No new Android build is claimed.

Independent native postcheck verifies every input/native mode and all 12 cache
compilations. The frozen original/lifecycle sources, original and new PM facts,
79 selected live source paths, service identities, vendor inputs and ancestor
modes remain. Live services, launchers, credentials, models, grants, policies,
production application and beta.9 APK remain unchanged.

## Actual live home boundary and next gate

Read-only inventory finds an ordinary old install-state directory without facts
and no new source-keyed install state in the canonical live home. Staged owner
facts must not be silently treated as canonical-home facts. No install-state
alias or fact copy is introduced. Next provision one fresh borrowed fixture home
using supported PM and exercise the saved launcher against that exact selection.
Then finish the full start/stop/post-stop/serve wrapper, home/profile, protected
policy/ancestor permissions, idle-state and encrypted recovery/rollback packet.
Live activation and any permission changes require their separate approval.

## Exact evidence

| Artifact | SHA-256 |
| --- | --- |
| Portable 564-case log | 4e2ef399d5fe98b17f2bd23b816cd0c2758da6142eee2326cb4fc3c31b847187 |
| Native 20-case log | c0a03851cece85e2122d5f2a0de5995dfa2f881100aaf275e5fbe3a2d5bb8261 |
| Actual owner-home launcher log | b835399479883c6e2fc1019524bfcaf0083fab2deb300e46fe3789617cf13758 |
| Actual unprepared-home refusal log | f87c2dfbbf9d91efdbd9f7e4cef5c666315f4f1c728e8fb9d76c1acf7ee379a6 |
| Independent raw/input/bytecode/live review | f273c5130c17e7835806759e156c9827fb7c077969ea34822f1a8bf20e17eceb |
| Published executable | 6e4292d196bb17a0487d63877d78b8acac4d70f6d9687d7b81a4f69b9b0d3700 |


## Prepared borrowed home: supported PM and actual launcher accepted

Supported `pm.sync_venv` provisions one fresh isolated fixture home for this exact
source using its declared all/Discord/ContextGraph extras. The SDK sync receipt
has outcome ok, exit zero and successful dependency rebuild; `venv_is_current`
confirms the new home-specific selection. Existing owner/live facts stay exact.

The first wrapper stops after successful sync/readiness and before creating its
published-launcher child: Python opens `/dev/null` read/write for DEVNULL, which
the wrapper's approved-staging-only write fence refuses. The complete traceback,
failed operation and empty child log are retained. No child PID/start exists.
The PM sync is not repeated and the fence is not broadened. A new observation
uses an empty stdin file inside approved staging for the first actual child.

The unchanged saved SDK executable runs its real normal branch with explicit
borrowed HERMES_HOME and `--run-module pm.environments --format json`. It exits
zero and reports that home's exact source-keyed facts and application dependency
path. A separate selected-interpreter observation verifies Linux/aarch64 Python
3.14.9, ContextGraph 0.8.1, PyYAML 6.0.3, exact dependency origin and no pytest.
All child/supervisor processes terminate. The isolated home has no real identity,
model configuration, credentials, project content or production sign-in.

Independent recovery checks the original PM receipt/facts, both failed and
successful operations, exact child log, dependency observation and complete
runtime inventory. All 23,628 source input hashes/native modes and the now 29
generated native bytecode files verify. The initial unprepared refusal home's
new-source facts remain absent. Old/base sources, original/new owner facts,
selected live sources, services, vendor inputs and ancestor modes are unchanged.
This accepts the finite prepared-home launcher case. It does not accept real
gateway/serve, host identity/TLS/login, protected policy, workers or Android.

| Prepared-home artifact | SHA-256 |
| --- | --- |
| Exact published-launcher environment log | 77b6d3530a672b7209c8352021f9e33194577bc17fbdc2bec252a9fafb21b3f7 |
| Home-specific PM facts | 9ce01f97a9de7e9572efe655d633c00f06fabb95a4a6c3cb6993d0dc4b62e018 |
| Original SDK sync receipt | 02ab4167cdf540a0c180ab924fe00b5d86cfdd80e5e3f0ace739611c1c1cd55c |
| Selected dependency observation | 26b78601088b56dfc365098b01ad1b3a9bbf612191f1855b616e061411b1994b |
| Retained pre-child wrapper failure | 273526e15be2fcfbfe03a4e786012a522f9c1eb035f96b360ebbc816ae7d1f82 |
| Independent raw/source/cache/live review | 3a5d910f15a0545a49fcca4471335c0f18240b2e57093be9299a63e0f93d4820 |

## Hosted source checks and fresh upstream findings

For source cc894824, [compatibility run 43581487163](https://github.com/DionCroft/Signal_Grove/actions/runs/43581487163)
passes all 564 portable cases and 15 runtime-model cases on actual hosted Linux,
with no skips. Pinned registry-module and response-sidecar jobs also succeed.
The Windows worker workflow succeeds. The separate Android workflow is pending
at this observation; no new full Android or exact-production acceptance is made.

The upstream job remains red. Its original size/hash-verified report archives
retain 40 changed used handlers for stable 3b421578 and 144 for main 0f05ab7f,
against the existing f0d94882 baseline. These are changes requiring semantic
review, not 184 proven app defects. The other tracked used-contract change
categories are empty. The 115/151 changed-source-module counts are not additional
accepted behavior tests. No baseline, source guard, test assertion or exception
allowance changes to obtain success.

| Upstream report | Bytes | SHA-256 |
| --- | --- | --- |
| latest | 1759087 | 19426c9353c74fa3945a736c350a79accc0cdb4d3524b5e621e4c300f7642443 |
| main | 1798906 | 88502509810179b47331384f672f7eaf7618783a2d9908a0ceeb650a4e61ce47 |

## Next deployment boundary

The published launcher now has owner, refused-unprepared and PM-prepared-home
evidence. Next build the concrete reversible production transaction covering
both effective unit chains, gateway start/stop/post-stop, serve wrapper, explicit
canonical home and supported home-specific PM ownership. Review profile/auth,
protected-policy ancestors, narrowly scoped grants, current idle state and
verified encrypted backup/recovery before requesting live activation. No live
service, canonical-home PM selection, ancestor mode or policy is changed here.
Continue the full upstream used/transitive review and all other original gates.
