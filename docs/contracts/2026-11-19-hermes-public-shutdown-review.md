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
the register-once case and the retained original warning check. The prior
two-file candidate targets the earlier SDK revision. Applying it to
current stable or main requires a new reviewed port.
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
shutdown, contention and the reviewed lifecycle correction remain open.
