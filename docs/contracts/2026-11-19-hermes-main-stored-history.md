# Hermes main stored history and recovery

19 November 2026. This review compares Hermes v0.23.2
`ae2a81ccc00e9b7c06301523e5bb09608397149c` with main
`364aded7ee2a2888421fc7aec96adbe6c07748a5`. The inspected main archive
SHA-256 is `d8684f78839a3ad43a6a080f49ebe8922d92517c47e0dfc7737c9bfa090a388e`.
The staged cohort includes 185 main files, each checked against its public
Git tree blob ID. The review reads source and compares syntax and call paths.

## Stored transcripts

- The transcript handler still validates the request and cursor before using
  it. Cursor binding includes session, subagent and requested order.
  The top-level handler requests the `session:read` check before reading rows.
  The authority still checks serving state, the profile on the actor and
  target, capability, read-only history mount access, and local ownership or
  operator capability. Main obtains local policy and legacy marks through
  SQLite `binding_marks`, which checks the same two stored key prefixes.
  [Transcript and cursor](https://github.com/NousResearch/hermes-agent/blob/364aded7ee2a2888421fc7aec96adbe6c07748a5/gateway/session_stored_reads.py)
  · [Authority](https://github.com/NousResearch/hermes-agent/blob/364aded7ee2a2888421fc7aec96adbe6c07748a5/gateway/session_authority.py)
  · [SQLite binding marks](https://github.com/NousResearch/hermes-agent/blob/364aded7ee2a2888421fc7aec96adbe6c07748a5/persistence/sqlite/authority/_local.py).
- The legacy fallback handles only `not_found`. It resolves the
  compression tip, then runs the native-reader, profile and stored-history
  checks. SQLite still uses the supplied session ID when it finds no tip.
  Other refusals propagate. The regular local path restores and checks the
  receipt route, origin and stored row before choosing the receipt's
  current physical target. Reads may restore a route in memory, backfill
  a display index or trigger storage catch-up. A runtime check should
  distinguish these operations from submitting a model turn.
  [Legacy picker guard](https://github.com/NousResearch/hermes-agent/blob/364aded7ee2a2888421fc7aec96adbe6c07748a5/gateway/session_listing.py)
  · [Local binding and recovery](https://github.com/NousResearch/hermes-agent/blob/364aded7ee2a2888421fc7aec96adbe6c07748a5/gateway/session_local_recovery.py)
  · [SQLite lineage](https://github.com/NousResearch/hermes-agent/blob/364aded7ee2a2888421fc7aec96adbe6c07748a5/persistence/sqlite/conversation/lineage.py).
- The gateway's snapshot read and resume-size guard now call the
  conversation message repository. The latter receives the resolved
  message limit explicitly, and the gateway maps its typed exception
  to the existing oversized branch. The two pins' canonical list
  result and snapshot schema have identical syntax. The list input
  schema changes only a comment referencing the relocated workspace rule.
  Keep the oversized, compressed, moving cursor, cross-profile and
  restored legacy cases in the next runtime comparison.
  [SQLite message repository](https://github.com/NousResearch/hermes-agent/blob/364aded7ee2a2888421fc7aec96adbe6c07748a5/persistence/sqlite/conversation/messages.py)
  · [Canonical wire models](https://github.com/NousResearch/hermes-agent/blob/364aded7ee2a2888421fc7aec96adbe6c07748a5/tui_gateway/contracts/canonical_sessions.py).

## Pending recovery

Both pins query pending admissions by target, in sequence order, with
`queued`, `started` and `unknown` in the SQLite filter. Main exposes the
rows through typed admission records. The regular recovery loop still
holds a session with any `unknown` admission before scheduling execution.
The separate persisted-viewer-close path needs its own acceptance check.
Verify durable receipt identity, storage contention, acknowledged close,
and profile retirement before wiring these new boundaries into the watcher
baseline. The reviewed source paths warrant isolated runtime trials.
Shutdown, worker dispatch and live Pi acceptance remain open.
[Stable SQLite query](https://github.com/NousResearch/hermes-agent/blob/ae2a81ccc00e9b7c06301523e5bb09608397149c/hermes_state_runtime.py)
· [Main SQLite query](https://github.com/NousResearch/hermes-agent/blob/364aded7ee2a2888421fc7aec96adbe6c07748a5/persistence/sqlite/authority/_admission_store.py)
· [Main recovery caller](https://github.com/NousResearch/hermes-agent/blob/364aded7ee2a2888421fc7aec96adbe6c07748a5/gateway/session_local_recovery.py).

## Disposable SQLite admission checks

Separate fixtures exercised the reviewed v0.23.2 and main admission bodies
against real SQLite transactions. Five source files matched their pinned
Git blob identities. The fixture supplies a transaction holder, one
physical segment per conversation and a controlled transcript-close
callback. The exercised scope covers stored admission transitions.

Both pins rejected recovery under the stale epoch. The current owner
changed one stale started admission to unknown. Its own started
admission stayed started. Recovery marked a stale worker unknown and
ended the old steering receipt as interrupted. Both unknown branches
refused the next claim and left its queue untouched.

A mismatched generation refused acknowledgement. A forced boundary
failure rolled back the marker and kept the admission unknown. A
reviewed acknowledgement moved it to terminal interrupted. Re-reading
that acknowledgement left the ledger and transcript marker unchanged.
A mismatched FIFO head and a drain gate refused a fresh claim. An
explicit claim with the correct head then started the separately queued
item at generation 8. SQLite integrity passed for both fixtures.

Evidence SHA-256 is
`948c3a616f215df00e580bf483bc83287be5b2248a7b3208e5829210aa1aaf9f`.
Native authority bootstrap, actual transcript boundary, user and
profile authorization, worker recovery, contention and live Pi
acceptance remain open.
[Stable storage code](https://github.com/NousResearch/hermes-agent/blob/ae2a81ccc00e9b7c06301523e5bb09608397149c/hermes_state_runtime.py)
· [Main SQLite admission store](https://github.com/NousResearch/hermes-agent/blob/364aded7ee2a2888421fc7aec96adbe6c07748a5/persistence/sqlite/authority/_admission_store.py).

## Paired gateway and SQLite tests

The hash-verified public archives ran the upstream gateway tests for
stored transcripts, cursor polling, snapshot reads, handoff ownership,
stored history mounts and canonical inventory on isolated Windows
profiles. The suite also ran the two SQLite snapshot tests on each pin.
Main includes its relocated, expanded transcript repository suite.

Main passed all 163 selected tests. Stable v0.23.2 passed 106 of 107.
The single failure is the canonical inventory assertion for a lazy
session with the profile constrained to file and terminal toolsets. The
response advertised at least one unexpected group from the assertion's
web, browser and image set. The fixture's stored-history reads themselves
passed. The assertion concerns the reported inventory.

On stable the inventory test passes in isolation. In a fresh process,
putting the legacy read-only API history case immediately before it
reproduces the failure. The API history case itself passes. Putting the
API mount's session-info case first also reproduces it. The same legacy
API sequence passes both cases on pinned main. A 22-case mount prefix and
an 80-case transcript prefix each reproduce the stable failure.

| Report | SHA-256 |
| --- | --- |
| Stable full selected suite | 4525e7a1d6dbc3f8e58a61fe32c75c0b3d62e2725a4b7c21cd4d08891ea413f6 |
| Main full selected suite | 219eb16acc3f2b8ee607232d6a6af84e09e6ff4b37a60f7d775333786db404ab |
| Stable isolated inventory | bf97a7bc6ccc658701af5617fac3a59b5a2902f8f50faae13e2e9309ef4d98d6 |
| Stable legacy API then inventory | 305fe1931878d2ccef4df799149b10240899bf3b338e5fad75fbd40dd2cfc2f5 |
| Main legacy API then inventory | e7a1ae21375fe81ffaba96f7cbcfff68207fca65ceb8a79512979bfd4d791735 |

Keep the exact order in the remaining stable diagnosis. These trials
exercise authority dispatch and SQLite through generated gateway
fixtures. The original shutdown warning, deployed Pi, compatibility
watcher baseline and real worker or integration acceptance remain open.
[Stable legacy fixture](https://github.com/NousResearch/hermes-agent/blob/ae2a81ccc00e9b7c06301523e5bb09608397149c/tests/gateway/test_stored_history_mount.py)
· [Stable inventory fixture](https://github.com/NousResearch/hermes-agent/blob/ae2a81ccc00e9b7c06301523e5bb09608397149c/tests/gateway/test_canonical_session_inventory.py)
· [Main inventory fixture](https://github.com/NousResearch/hermes-agent/blob/364aded7ee2a2888421fc7aec96adbe6c07748a5/tests/gateway/test_canonical_session_inventory.py).

## Profile refresh directory and fresh fixture comparison

The two-case diagnostic confirms that initial config resolution reads the
generated profile on both pins. Stable then infers the profile directory
from the database file's parent during policy refresh. That lookup targets
a missing config in the failing fixture. The next inventory resolves 18
enabled toolsets. Main uses the explicit persistence profile directory for
refresh and resolves three enabled toolsets in the corresponding snapshot.

Removing the wrapper's custom home override let upstream create its
normal isolated test homes on E:. Stable still failed the original
inventory assertion, with 106 passes. Main passed all 163 again.
Live profile and dispatch acceptance remains open.

| Report | SHA-256 |
| --- | --- |
| Stable path-match trace | 9c280d21014407788036b82a0d41ffa3a55fc1fe18cabdaf4a78ccaad3ddb96f |
| Main path-match trace | 22936871262939fd84366c85b0606a7c9d4f08b6b21713dbe0ba1ea38133fb8c |
| Stable automatic home transcript tests | a762660bca4f1fb5a49589f5a39380bcf3c4d3e7c5d5540752f9f15fc33ee7da |
| Main automatic home transcript tests | 9feaa8ec0036e17c3c8e7a6863afe6b55daf481885393e69947b50668c30490c |

[Stable refresh directory](https://github.com/NousResearch/hermes-agent/blob/ae2a81ccc00e9b7c06301523e5bb09608397149c/gateway/session_policy_refresh.py)
· [Main refresh directory](https://github.com/NousResearch/hermes-agent/blob/364aded7ee2a2888421fc7aec96adbe6c07748a5/gateway/session_policy_refresh.py).

## Watcher evidence

[Hosted run 44575549490](https://github.com/DionCroft/Signal_Grove/actions/runs/44575549490)
passed parser, pinned sidecar and pinned registry jobs. Stable produced the
status 2 review artifact, with 165 verified cache hits and 63 verified
downloads. The hosted report matched the local stable JSON after
excluding the requested-ref and cache-counter metadata. Its SHA-256 is
`08fc141c43e17b02c05e9186dead51178a369f5456f29919ccfbc4d7438c1227`.
Main remained incomplete with status 3.

The new incomplete diagnostic names the pinned commit and each missing
required path from the local watch list. The actual main canary printed
all eight retired storage module paths and exited 3 before producing a
comparison report. A regression test proves that incomplete discovery
performs no source downloads or extraction and emits no JSON report.
The targeted cache, transport, authority and auth-boundary suites ran
45 tests. Portable contract discovery ran 121 tests with overlap.


## Stored history on Hermes v0.23.3 and the current main pin

The exact v0.23.3 checkout and reviewed main checkout each passed the
163 unmodified upstream cases in eight files. The test identities match
each other and the earlier accepted main cohort. The v0.23.2 inventory
failure remains in its original record. The current trials used isolated
Windows homes with the compatible CPython 3.14.9 test environment.

These tests cover stored-history access, picker inventory, cursor polling,
transcript snapshots and handoff ownership through the upstream fixtures.
They also cover the SQLite transcript repository. They keep the separate
workspace mutations, receipt recovery, persistent contention and deployed
Pi requirements open.

| Revision | JUnit SHA-256 |
| --- | --- |
| v0.23.3 caafe5389ae585ef77387a962aee05b1bcf17029 | 8d324a191da113438ca8f166c6c86b71856d979b16d914bc8464e9466eaa1f63 |
| Main 2de9f51b8008a7d49a49221c184f2f11cbfc4af1 | 23454daa8b29afc8ce6f6cc57e078837cd51dd29d389cd6578f2d93285e6d96c |

The paired identity and eight-file hash receipt has SHA-256 5bd1eaa6b5389540bc8590eca4fcadeecb28690fce1557720627df2a9e3d09a6.
