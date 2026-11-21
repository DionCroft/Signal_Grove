# Scoped scheduled-job history

The optional native owner ledger method is a separate source contract for
durable scheduler attempts and one exact receipt. Its `expected_profile`
must match the authenticated connection. The Android dashboard path
described below remains current. See the
[native ledger method](../../server_extensions/coordinator_workspaces/README.md#optional-native-scheduled-execution-receipts).

The Android management view reads GET /api/cron/jobs/{job_id}/runs with the
selected explicit dashboard profile and limit20 after rechecking the exact job.
The dashboard bearer remains independent from the inference credential; existing
TLS, configured path prefix, local-network guard and no-redirect policy apply.
The app sends no trigger, edit, cancellation, chat adoption or worker request.

Reviewed official source: NousResearch/hermes-agent at
bf0203a794a07aac3ff764972ab9087baa4e4f4e (the Pi generation source vector) and
2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19 (reviewed stable). The route and sync
helpers build {runs:[...],limit:n}; there is no root job/profile proof. Agent
rows carry an owning profile. Script-output/ledger-derived rows currently omit
it. The owner resolver can fall back to another profile when a hinted job is
missing. A preceding job read alone cannot rule out a deletion race.

Therefore every row must explicitly report the exact requested profile. A
missing, foreign or mixed owner fails the entire read and clears old history.
This deliberately leaves script-only/mixed history on the dashboard for those
host revisions. A host extension that stamps the owner may be shown; synthetic
scoped script fixtures test the projection, not deployed-host compatibility.

At most20 immutable display rows are accepted. Text, timestamps, booleans,
counters, response size and duplicate identifiers are bounded/validated. Opaque
run IDs remain display data and never become URI selectors or execution handles.
Default diagnostics redact titles, previews, IDs and models. Those private
fields require a per-row reveal which resets on refresh, close or scope change.
A late reply cannot reopen a closed detail or restore an old connection.

The UI says recorded model and reported activity: activity is a host heuristic,
and script artifact timestamps need not be execution start/finish. Neither a row,
an assistant result, is_active, scheduler_owned nor a timestamp establishes
worker/tool completion. This does not yet add full log files or outcome controls.

The original F0 contract pin remains unchanged. Seven newly consumed cron
handlers have explicit BF/stable AST reviews bound to complete cron,
cron_helpers and web_deps modules. Other transitive storage/auth/runtime behavior
still requires live acceptance. New module bytes require review even if a route
AST remains unchanged. Validation is recorded separately in docs/VALIDATION.md.

## Stable v0.23.3 and exact main acceptance

The two-profile native dashboard probe reproduced profile-hint fallback
and a mixed history response carrying an unmarked output row on stable
`caafe53` and main `242315c`. A profile query and a successful job
preflight alone cannot prove ownership of every later output row. Keep the
explicit row proof requirement for existing hosts. A supported scoped
output/log response needs to bind the authenticated profile, exact
canonical job and output to the same server-side read. A mismatched or
unknown profile or a missing job must fail before resolving output. Test
same IDs in separate profiles and concurrent removal or reassignment.
[Exact HTTP and SQLite evidence](../VALIDATION.md#hermes-scheduler-and-scoped-run-history-on-stable-and-main).

Hermes distinguishes elapsed intervals from cron wall-clock schedules.
For a recurring calendar schedule, a pinned IANA zone takes precedence;
an unset zone uses the job owner's profile. The next occurrence must
move strictly forward in absolute time in the repeated autumn hour.
The foreground preview should use an explicit verified zone for calendar
estimates and distinguish an estimate from the host's stored next run.

A zero-minute grace setting is subject to Hermes' two-minute tick
tolerance. Even outside grace, the effective catch-up policy can still
run one missed occurrence. Disabled catch-up skips beyond grace when
the scheduler can compute the next occurrence. Manual runs are handled
separately and inherit no permission from a forecast or a history row.
[Hermes scheduling rules](https://github.com/NousResearch/hermes-agent/blob/242315ce571cde721e717a544cf8ae6c343315cf/cron/jobs.py)
· [Missed-run tests](https://github.com/NousResearch/hermes-agent/blob/242315ce571cde721e717a544cf8ae6c343315cf/tests/cron/test_catchup_policy.py).
