# Hermes public heartbeat shutdown review

19 November 2026. The existing owner-runtime cleanup warning was observed
on the earlier pristine SDK pin. This review checks the corresponding
shutdown path in v0.23.2 `ae2a81ccc00e9b7c06301523e5bb09608397149c` and
main `364aded7ee2a2888421fc7aec96adbe6c07748a5`. Both shutdown modules
and all six files from the canonical shutdown test list were verified
against the public Git tree for each pin.
[Original native evidence and candidate](2026-11-13-heartbeat-lifecycle-candidate.md)
· [Canonical test list](2026-11-14-paired-canonical-shutdown.md).

Both pins register the heartbeat synchronously. A nonpositive refresh
interval returns before registering exit cleanup. With a positive
interval, the exit callback signals the daemon and acquires the current
DB handle for cleanup without joining the refresher. The gateway close
method waits for executor and tracked storage writers. Its close phase
has no corresponding reaper drain or cleanup call. Main changes the
backend calls to `persistence.maintenance` and the shared close to
`persistence_assembly.close_all_stores`. The cleanup ordering remains
open at both public pins.
[Stable reaper](https://github.com/NousResearch/hermes-agent/blob/ae2a81ccc00e9b7c06301523e5bb09608397149c/tui_gateway/session_reaper.py)
· [Stable close method](https://github.com/NousResearch/hermes-agent/blob/ae2a81ccc00e9b7c06301523e5bb09608397149c/gateway/run_shutdown.py#L1939)
· [Main reaper](https://github.com/NousResearch/hermes-agent/blob/364aded7ee2a2888421fc7aec96adbe6c07748a5/tui_gateway/session_reaper.py)
· [Main close method](https://github.com/NousResearch/hermes-agent/blob/364aded7ee2a2888421fc7aec96adbe6c07748a5/gateway/run_shutdown.py#L1939).

## Controlled callback checks

Four cases used only the three verified function bodies for each
revision. A fixture supplied thread registration, inactive writer counts,
a captured exit callback and a real disposable SQLite connection. The
fixture called the gateway close method before the exit callback. It
recorded calls at the storage boundary and checked the DB through a
fresh read-only handle afterward.

| Refresh setting | v0.23.2 | Main |
| --- | --- | --- |
| Register once | DB closed and no cleanup callback was registered. One heartbeat row remained. | Same |
| Periodic | After DB close and registry sweep, the callback looked up the handle and attempted cleanup. The held handle was closed. One heartbeat row remained. | Same |

All four integrity checks passed. The serialized outcomes have SHA-256
`d3e4d0790ee83af51e6d2752055fe1ab003c61cf19ed11cc53c69855aa98e91c`.
This fixture verifies the callback behavior under the selected ordering.
The actual owner bootstrap, daemon/writer contention, live DB acquisition
and final process exit remain separate acceptance checks.

The five upstream heartbeat tests cover initial registration, repeated
starts, write-error handling, and startup from the entry point or
WebSocket. None of those five exercises stopping the gateway and
then executing captured cleanup. A native follow-up needs that full
lifecycle in a generated private home, once for each public pin, including
the register-once case and the retained original warning check. The earlier
SDK revision has a reviewed two-file candidate. Application and test results
for the newer pins appear below.
[Stable heartbeat tests](https://github.com/NousResearch/hermes-agent/blob/ae2a81ccc00e9b7c06301523e5bb09608397149c/tests/tui_gateway/test_backend_heartbeat.py)
· [Main heartbeat tests](https://github.com/NousResearch/hermes-agent/blob/364aded7ee2a2888421fc7aec96adbe6c07748a5/tests/tui_gateway/test_backend_heartbeat.py).

## Current public pins, six-file Windows suite

All 50 selected upstream cases passed on each exact archive using the
upstream automatic isolated-home fixture and its original protection.
Both pins exercised the six listed canonical files. A first run pointed
the parent environment at a disposable custom home, which the upstream
guard treated as protected. Each first run recorded two failures before
the fixture's restart-count write. A fresh fixture removed that parent
override and kept the guard and assertions in place.

| Report | SHA-256 |
| --- | --- |
| Stable Windows shutdown JUnit | f791082727d6d4e98df238f5416afb251fbc709a38ccb56de1a526a5f8f737ef |
| Main Windows shutdown JUnit | 7c467dd242fee57bf8359c3fc9a8f34db7de6e1703235c69f5834ee3067b0427 |

The gateway shutdown suite and the controlled late-heartbeat behavior
remain distinct checks. The earlier native owner cleanup warning and
full public-pin owner lifecycle are still open.

## Real gateway stop with native process exit

Four disposable Windows processes used the exact public archives, a
constructed GatewayRunner, the native TUI heartbeat entry point and the
normal gateway stop method. Each process registered its native heartbeat
against its own state.db, then exited with code zero. A fresh read-only
connection checked the table after exit. Each run observed one heartbeat
row after the stop method returned and passed SQLite integrity checking
after exit.

| Pinned archive | Heartbeat | Rows after process exit | Reopen warning after stop |
| --- | --- | ---: | --- |
| v0.23.2 | Register once | 1 | Absent |
| v0.23.2 | 0.5-second refresh | 0 | Present |
| Main | Register once | 1 | Absent |
| Main | 0.5-second refresh | 0 | Present |

The periodic trace shows cleanup after the stop marker reopening the
state database and clearing the heartbeat row. The register-once trace
retains its row. These native findings preserve the ordering defect
under the observed Windows conditions. In the controlled callback
fixture above the closed handle instead leaves the row in place.

Initial guarded native runs allowed the gateway to finish but recorded
six refused open calls. A separate trace found an absent bundle manifest
and an absent per-process Linux stat file. The final Windows boundary
allowed only those exact absent reads and observed no refusals through
the stop marker. All child writes used their private data directories.

| Process log | SHA-256 |
| --- | --- |
| Stable register-once | d7df9a3fbc2e1edbe5a81ad67bd129366066cdbb09cb77738c5d955afdae1b19 |
| Stable periodic | d84a0d6930fd15b9729ba40cd5acfd3112d54e6775269fd92343f7359292811f |
| Main register-once | d7df9a3fbc2e1edbe5a81ad67bd129366066cdbb09cb77738c5d955afdae1b19 |
| Main periodic | 239e1b10499176ccd75c5e29bb5951b01c04d61c8ed6e65fba92e8e2119c1833 |

The test invokes heartbeat setup directly. Full owner bootstrap, live Pi
shutdown and contention remain open.

## Verified v0.23.2 shutdown candidate

The archived v0.23.2 source is pinned to
`ae2a81ccc00e9b7c06301523e5bb09608397149c` (archive SHA-256
`fe3481973fd7b4f78a4f716110e793b1474ec5eec9d44fc0332c6be40fa9b776`).
The checked-in `hermes-3b42157-heartbeat-shutdown.patch` applied exactly
to a fresh v0.23.2 copy. A full before/after manifest of 26,190 files
found changes only in `gateway/run_shutdown.py` and
`tui_gateway/session_reaper.py`. Both candidate file hashes matched
again after the test runs. The existing patch and pristine sources
retain their original bytes.

Two disposable Windows processes used the same native gateway test as
the pristine source above. Each registered one row. A normal gateway
stop cleared it before returning, for both register-once and 0.5-second
periodic refresh. Each process exited zero, and a new read-only SQLite
connection found zero rows and returned integrity `ok`. Both logs were
free of late database reopen warnings and recorded zero guard denials
through the stop marker. The six-file Windows suite passed the identical
set of 50 upstream cases on the pristine and patched trees with
zero skips.

| Verified artifact | SHA-256 |
| --- | --- |
| Checked-in patch | `21e42e06ebf1f1c7c2c6eda122a64894b76485d7b3a95c8640e164f057091190` |
| Candidate file comparison | `cafd972cecff14d6de43fa859e6efcb84c0fba9584e987dbd830a90fe1eab0ce` |
| Register-once receipt | `bca731348cd3582762a44913646b39aeec74a87f28452d2f3016304c329e4905` |
| Periodic receipt | `fc5116ccfbb24a6b56c9c275076d77fe3891f2b066a5d6750d472e538df9b161` |
| Candidate six-file Windows JUnit | `fe36f22fbd5b85c0f4898c849ea5fd11d48e844f0675c4e730ec3a56f2d1784a` |

The same patch's apply check failed on both files at the paired
public main pin `364aded7ee2a2888421fc7aec96adbe6c07748a5`. That pin
requires a separate storage and shutdown review before a port.
The stable Windows trial ran in disposable homes. Native Linux,
held-writer contention, owner bootstrap and live Pi shutdown remain
open for v0.23.2.


## Hermes v0.23.3 and current main shutdown candidate

On 20 November, the GitHub API resolved the stable release to
`caafe5389ae585ef77387a962aee05b1bcf17029` and main to `2de9f51b8008a7d49a49221c184f2f11cbfc4af1`.
Their reaper, shutdown phase, storage binding, assembly and heartbeat test
files have identical blob IDs. The two archive inventories match all
27,139 and 27,171 regular Git blobs. On each pin,
42 PowerShell files used archive CRLF conversion. Their normalized bytes
matched the Git blob hashes before the disposable source was run.
[Stable release](https://github.com/NousResearch/hermes-agent/releases/tag/v0.23.3)
· [Main at the observed pin](https://github.com/NousResearch/hermes-agent/commit/2de9f51b8008a7d49a49221c184f2f11cbfc4af1).

Each pristine cohort registered one row. Normal stop returned with the row
present. Register-once left the row after process exit. Periodic mode
cleared it at exit after logging a database reopen. All four SQLite
integrity checks passed. The native harness used CPython 3.14.9 with an
existing compatible Hermes PM side test environment. The first smoke
attempt used Python 3.13 with 3.14 extension modules and stopped at import.
Its diagnostics were retained.

The [two-file candidate](../../upstream_patches/hermes-caafe538-heartbeat-shutdown.patch)
uses the existing persistence handle and backend ID for cleanup. It arms
cleanup before registration, so standalone exit can drain without starting
a thread during interpreter shutdown. Beginning gateway teardown fences
future heartbeat writes. The database close phase waits at most one second
within its remaining budget. An active or failed cleanup leaves handles
open. The candidate applied with Git's whitespace check to both pins.
The complete post-run file inventories matched their pristine or exact
candidate hashes.

| Disposable Windows process case | v0.23.3 and pinned main result |
| --- | --- |
| Normal gateway stop, register-once and periodic | Stop returned with zero heartbeat rows. |
| Standalone process exit, register-once and periodic | Clean process exit removed the row in both modes. |
| Close phase with a live heartbeat SQLite transaction | Returned in the wait budget and left the connection and transaction open. After explicit release, the original cleanup cleared the row. |
| Full stop with a live heartbeat transaction released once a teardown writer waited | Both writes settled, no new heartbeat transaction started, and stop closed the connection. |

All 12 candidate process runs exited zero. All 12 fresh read-only SQLite
checks found zero heartbeat rows and integrity `ok`. The native logs
had no late close/reopen warning and the guards recorded no denials.
The four pristine and candidate pytest combinations each passed the same
50 unmodified upstream cases with zero skips. The candidate pytest logs
include exit diagnostics for handles already closed by the upstream
autouse fixture. The native owner-handle scenarios above exited without
those diagnostics.
[Fixture close order](https://github.com/NousResearch/hermes-agent/blob/caafe5389ae585ef77387a962aee05b1bcf17029/tests/gateway/fixtures/storage_handles.py).

An earlier full-stop trial kept a real heartbeat transaction open for
10 seconds. The teardown subagent ledger write waited on that transaction
before the database close phase. A subsequent prototype released the
transaction and completed stop, but admitted more heartbeat writes during
teardown. The final two-file patch fences admission at teardown entry.
The full gateway path while a transaction stays blocked throughout
teardown remains open for further review.

| Evidence | SHA-256 |
| --- | --- |
| v0.23.3 archive | `3dffc702249874db2e6f60d93370a8d3c11e57e3ab5ee321447969e170a51aef` |
| Main archive | `dc7db14c94a8a02530e27deaed7bfa16673893b356e82883d31615417aa5b65f` |
| Two-file candidate patch | `43ca3e046be09981f85ce43209ef7d4556f4e95a7a995153788d1ee658964dd2` |
| v0.23.3 pristine / candidate JUnit | `a3d27ea5a0ef22d8d996fc7ff1f45a409d98598c08aa3c238b1675f72bb06c29` / `c6753b3cdebe9d5b0226cab9b74a7ced7e43c182c3abaf2febb570828585974d` |
| Main pristine / candidate JUnit | `fb1101111998be0bca3367ea50777a581f17712a06cb9ee119e4ddac8f82c199` / `cbf706df20e5d0f75943c713143ab85a71a3d779283df5bca620f233afe0078e` |
| Native receipt and complete verification summary | `c0f1b5a5559b98b2b3d8b3e37acd917e455f8af69d7c3f9296b08d1cbb5e3c13` |
| Retained first held-transaction trace | `68fba238e9943de261ed98c2eba4d6ae70252f5a65b753ad2642eb312b916972` |

Hosted Linux with the current pins, the full owner bootstrap and Pi
lifecycle, and the separate compatibility and Android release gates
remain open. The candidate is retained for review and local testing.

The [Linux matrix](../../.github/workflows/hermes-shutdown.yml) now runs the
same six untouched canonical files for both variants at all three reviewed
pins. Each job verifies its full source and executable maps before PM setup
and after execution. Separate jobs compare artifact pairs for the exact
requested pin. The 21 portable verifier tests include refusals for mixed
revisions and mismatched source inventories. The Windows public archive
maps reproduce the two new pin fingerprints. The hosted Linux results and
native lifecycle testing remain separate gates.
