# Latest stable owner-runtime and canary checkpoint

## Scope

The owner-runtime probe now accepts two explicit immutable execution targets:
`2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` (the original default) and
`3b421578588243f920886e6fe79a6faa87ea026e` (the latest stable source inspected
here). This allowlist authorizes a bounded conformance execution; it does not
accept all changed mobile contracts. A mutable branch, another commit, a dirty
checkout or a reused output root is refused. The chosen commit is forwarded to
the one supervised child and recorded before launch, including failed attempts.
No runtime/authentication/dispatcher assertions or filesystem/network boundaries
were relaxed. No dependency installation, production-home access or Pi/Spark
configuration change occurs.

## Actual executions

Fresh isolated native-Windows executions of the tracked probe return **2 /
CORE_PASS_SHUTDOWN_FINDING_OPEN** for both commits. The probe SHA-256 is
`bdd1114d6a1e6321c5231e6e2a7aa4a1b65b0f2edaaeb9215292d89671ddd6a5`.
The original pin imports 1,167 Git-verified source modules; latest stable imports
1,206. Both use actual GatewayRunner/bootstrap/ready publication, the current
Windows owner's authenticated control pipe, real profile-scoped tickets and
canonical WebSockets. Both pass default/field descriptions and empty room reads,
foreign-profile refusal, actual owner-SID ticket binding, one-use ticket refusal
and loss of ready ownership after teardown.

Each process-exit observer still records one upstream warning:
`tui_gateway.session_reaper._atexit_clear` writes a backend heartbeat after
canonical shutdown closes the cached database, reopening it. The supervisor
keeps this a nonzero finding. Final read-only SQLite quick_check is healthy in
both profiles, with zero sessions, rooms and admissions, and the PID file is
removed. No warning/reaper/assertion was suppressed. Private attempts 179/180
retain exact logs, source/import hashes, PID and terminal receipts. The preceding
latest-only prototype 178 independently produced the same scoped finding; its
result is not counted as a separate supported implementation.

A direct unreviewed `--source-commit main` invocation is refused by argument
validation before creating its fixture or supervisor. The complete script-policy
suite passes 232 tests and checksum policy passes 495 components / 1,071
artifacts. Product Kotlin/native sources and Android fixtures are unchanged
from the reviewed workspace commit `cf209598bd81bca9f926225b56da270049bbb93a`;
no new Android or physical-device acceptance is attributed to these probe runs.

For reproduction, use the command in the [owner bootstrap contract](2026-11-08-owner-runtime-bootstrap.md)
with the optional `--source-commit` set to either exact commit above, a matching
clean checkout, existing dependencies and a fresh E: output root. All original
supervision, timeout/no-replay and post-exit checks still apply.

## Hosted canary finding and reporting correction

[Compatibility run 43016471551](https://github.com/DionCroft/Signal_Grove/actions/runs/43016471551)
for workspace commit `cf209598bd81bca9f926225b56da270049bbb93a` fails at latest
stable inspection as intended: `mobile_review_required=true`. Parser regressions
and both exact pinned registry/sidecar jobs pass. The downloaded latest-source
artifact is hash-verified against GitHub's ZIP digest
`78d08b2703fda412a6c082f308f686e13f4b6561ac2bc34e67627ab0709abc11`.
It resolves stable `v0.23.1` to the exact `3b421578588243f920886e6fe79a6faa87ea026e`.
There are 47 changed used-handler entries and no changed watched request fields;
these counts describe source-review work, not 47 proven broken features.
The report adds one API route (`POST /v1/room-members/runs/close`) and eleven
management routes for gateway OAuth/SSH and messaging relay pairing. Authentication,
canonical authority/event handling, workspace dependencies, room context and
integration configuration still require targeted review/runtime acceptance.
No baseline, reviewed variant, source fingerprint or contract waiver is added.

That failed stable step previously skipped the independent upstream-main source
inspection because of GitHub's implicit success condition. The main inspection
now uses `if: ${{ !cancelled() }}` so a stable failure does not hide its report;
user cancellation is still honored. The stable failure remains a failed job.
Read-only workflow permissions, pinned actions, parser dependency, timeouts,
source-cache validation and result retention remain unchanged. The next hosted
execution must verify both actual reports; editing the condition alone does not
prove that remote outcome.

## Still required

Authenticated real-source Android workspace mutation and independent fresh CWD
readback, lost-result/permission/busy/profile recovery and the supported/deployed
version matrix remain open. An empty native-owner probe is not a seeded-session,
HTTP login, Android, Pi, worker/project/file, model/tool or complete lifecycle
acceptance. The shutdown finding, physical current-source tests, phone-to-Hermes
handoff and every original [audit requirement](../AUDIT_EXECUTION_PLAN.md) remain
required. No production APK is released by this checkpoint.
