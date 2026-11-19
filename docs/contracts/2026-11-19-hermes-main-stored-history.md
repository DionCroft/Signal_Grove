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
