# Paired canonical Linux shutdown gate

14 November 2026. The full audit goal stays active. The exact paired Linux
shutdown gate is now independently accepted as recorded below. The original
rejected run, actual Pi finite/held results and all wider requirements remain.

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

## First hosted execution and output correction

Source 7af0d7e runs both complete canonical cohorts on real Linux. Original
artifact archives are independently downloaded through the repository API;
their API sizes/digests match actual bytes. Both raw inventories have the same
50 positive classname/name pairs, zero skips/errors/failures and six successful
unretried canonical exits. Before-source maps and native modes match review.
The complete source-after check did not finish, so both jobs stay rejected.
[Original failed run](https://github.com/DionCroft/Signal_Grove/actions/runs/43569001301).

The unmodified tests/gateway/conftest.py writes an adapter anti-pattern scan
cache named from gateway test mtimes/sizes. The output verifier originally
omitted that cache. The correction independently computes the exact unchanged
fixture fingerprint, accepts only its literal clean result and optional empty
lock, and records hashes/modes. No cache is preseeded; pytest must perform its
own scan. Stale fingerprints, incomplete/dirty results and arbitrary files
remain refusals. Full source/output observations are now saved before inventory
verdicts. The SDK runner, all 50 case identities/assertions and lifecycle patch
bytes remain unchanged. The strict source-only preparer is unchanged.

| Original archive | Bytes | SHA-256 |
| --- | ---: | --- |
| Pristine artifact 14104125596 | 2,426,565 | ee5c0b7f310c0f8b27bc1a42b6204b0efe5e91f50118899f9eaae853337f84f0 |
| Candidate artifact 14102584723 | 2,429,390 | 3ac03f882a541be09cb467397dd2c2eb224ccc26e6e3076362276c9987636a5c |

The corrected local portable suite passes **532 checks**, adding three cache
and refusal-observation counterexamples. The first hosted run also passed the
original 16 verifier checks on actual Linux, including the real symlink branch.
No old result is erased. Fresh whole-source-after and aggregate acceptance are
still required before closing the paired regression gate.

## Independently accepted paired Linux result

[Source c6077892](https://github.com/DionCroft/Signal_Grove/commit/c6077892dcbd8bcf9b5e1ccc5556bc72da9283ec)
[run 43569839773](https://github.com/DionCroft/Signal_Grove/actions/runs/43569839773)
passes pristine, candidate and aggregate jobs on actual Linux/x86_64 with
supported-PM Python 3.14.9/pytest 9.1.1. Each complete canonical cohort has the
same 50 raw positive identities across six files, zero failures/errors/skips
and no retries. These are 50 shared identities, not 100 unique tests.

The original API archive sizes/digests are checked against actual downloaded
bytes. Both complete before/after source maps and all native file modes match
the exact 23,586 pinned Git objects/index entries; all 124 executable identities
are unchanged. The candidate has only the two hash-pinned lifecycle changes.
Generated outputs are separately inventoried; the adapter cache has the exact
current fingerprint and literal clean result. The SDK runner/assertions and
source-only preparer remain unchanged. The aggregate is independently rebuilt
from both raw JUnit/log cohorts and compared to the published aggregate artifact.

The independent reviewer initially refused Windows working-tree PS1 newline
conversion; the successful review reads raw pinned Git objects without changing
that working copy. Raw tuple/JSON-list identities are normalized only for
structural comparison. Both reviewer refusals and the original failed CI run
remain; no SDK assertion or failed result is removed.

| Accepted original archive | Bytes | SHA-256 |
| --- | ---: | --- |
| Pristine artifact 14104471252 | 6,233,575 | 65e45a4848ef2afdd21c6ea9aeca9e44c36984f51bbbf96f331161f9e689dd6d |
| Candidate artifact 14103686516 | 6,236,407 | ddc1c9b747c9ebce869bf06522a8958c44d91a36c36b7203b621b1cf646df933 |
| Aggregate artifact 14103691590 | 3,340 | b817f5e0a343f434a0a12cdaf0506b1ada7e62ea6d7bfc2186d350d8072ba534 |

Independent raw/source review SHA-256: `2e6fa8090ba8ad7a0e1ff09d38ac63672f461e6cfa8b076d0c8cd37e6f125ab0`.

This closes the finite six-file paired Linux regression gate. Source c6077892
also passes all 532 portable and 15 runtime-model checks on hosted Linux.
The [application environment gate](2026-11-14-native-app-environment.md) has its
own native scope. Neither result closes broad upstream/transitive review or
authorizes live activation, project/worker dispatch or a new APK release.
