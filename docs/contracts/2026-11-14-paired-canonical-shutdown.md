# Paired canonical Linux shutdown gate

14 November 2026. The full audit goal stays active. This checkpoint implements
the next regression gate; hosted results have not yet been accepted. The
previous actual Pi finite/held results and original failures remain intact.

The new [workflow](../../.github/workflows/hermes-shutdown.yml) uses two fresh
Ubuntu 24.04 jobs, each checking out exact SDK commit
3b421578588243f920886e6fe79a6faa87ea026e. Both verify all 23,586 Git blob bytes,
the index and native executable identities before provisioning. The disposable
candidate checkout receives only the existing hash-pinned two-file lifecycle
patch. Its SDK test files, runner, package inputs and assertions are unchanged.
The pristine checkout is a separate job; the frozen Pi bases remain untouched.

Both jobs build the supported PM side test environment with pinned tools,
declared dependencies, archive credentials disabled and dependency/tool caches
disabled. No raw pip/uv mutation, OS emulation or assertion selection is added.

| Complete canonical test file | Exact positive case identities |
| --- | ---: |
| tests/tui_gateway/test_backend_heartbeat.py | 5 |
| tests/tui_gateway/test_session_db_ownership_teardown.py | 17 |
| tests/gateway/test_shutdown_executor_quiesce.py | 10 |
| tests/gateway/test_shutdown_teardown_profile_scope.py | 2 |
| tests/gateway/test_mcp_shutdown_profile_passes.py | 2 |
| tests/gateway/test_gateway_shutdown.py | 14 |

Each file runs through scripts/run_tests.sh, one worker, zero retries,
fail-on-skip, a 240-second per-file limit and its own raw JUnit/log. Durable
launch/terminal receipts distinguish failed and unknown outcomes. An outer
timeout preserves the uncertain process receipt and stops further execution;
it never restarts that work. Hosted job cancellation/VM teardown remains the
external terminal bound. No Pi test scratch exception is invented.

The [gate](../../scripts/canonical_shutdown_gate.py) checks every source byte
and original mode after PM and test execution. The canonical runner writes
bytecode and test_durations.json; these generated outputs have a separate
path/hash/size/mode inventory. Only bytecode paths corresponding to reviewed
Python source and the actual pinned/bootstrap cache tags, known pytest cache
paths and the selected six duration entries are allowed. New source files,
symlinks, executable outputs, changed modes and empty shadow directories fail.
The strict source-only snapshot preparer is unchanged and still refuses caches.

Acceptance requires all six raw JUnit files, all 50 exact classname/name pairs,
zero skips/failures/errors and successful unretried canonical exits in each job.
A separate job reparses both downloaded raw cohorts and checks identical case
sets, expected complete source maps, patch scope and native modes. Original
artifact archive digests and the final source/job provenance must still be
independently reconciled after execution; a workflow badge alone is insufficient.

The portable suite passes **529 tests**, including 16 new counterexample checks
for hidden JUnit failures, missing/duplicate/wrong-class cases, filtered/retried
or unknown process receipts, source mutation and generated-output escape. The
earlier 526-test draft pass is retained. The POSIX symlink branch awaits the
actual hosted Linux run; no Windows result is relabelled as Linux acceptance.

The prior Windows paired SDK cohort and actual Pi helper/normal/held owner gates
retain their separate scopes. This gate is not full upstream/transitive review,
final app-environment acceptance, live deployment, worker/project acceptance or
a new Android release. Those requirements and all original audit rows remain.
After raw paired Linux acceptance, validate the exact application environment
without test-only packages, then prepare the separate source/PM/permission/unit
and encrypted-recovery activation review. No live activation is approved here.
