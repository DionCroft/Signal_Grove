# Hermes cron source relocation

19 November 2026. The watcher reads `cron/job_args.py` as the existing
`cron_format` module at the two reviewed revisions. The older source path
still applies to other revisions. The cached and direct reads select the
same path. A missing replacement fails inspection.

All 21 function definitions match their v0.23.1 syntax trees. The module
also adds `timezone` to `_FORMAT_JOB_OPTIONAL_KEYS`, which controls which
job metadata the formatter echoes. Android already accepts this optional
field for host schedule creation and displays it in the host job details.
The watcher continues to report the changed fingerprint for review.

## Stable watcher result

The official v0.23.2 archive matched the fresh Git tree identities for all
224 unique watched files. The checker read 228 logical sources from those
verified public blobs and returned status 2. Against the baseline
`f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab`, it reported 177 watched handler or constant
fingerprint changes, 169 changed source modules, one added API route and
17 added management routes. It reported zero used-route or used request
field removals and zero changed tracked job limits. Scope includes static
contract review. Live interoperability and shutdown acceptance remain open.

- Hermes stable revision `ae2a81ccc00e9b7c06301523e5bb09608397149c`
- Static report SHA-256 `e9725e7dd8d00aefd95b0934884577f9e4ccc7d70f092bbf07c242bc94646560`
- Archive SHA-256 `fe3481973fd7b4f78a4f716110e793b1474ec5eec9d44fc0332c6be40fa9b776`
- Cache and contract regressions passed 134 selected tests. The broader
  portable contract discovery passed 121 tests with overlap.

[Stable cron source](https://github.com/NousResearch/hermes-agent/blob/ae2a81ccc00e9b7c06301523e5bb09608397149c/cron/job_args.py)
· [Previous path](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/tools/cronjob_job_args.py).

## Main storage boundary

At `364aded7ee2a2888421fc7aec96adbe6c07748a5` the real watcher still returns status 3 because eight
previously watched storage modules are absent. The frozen main archive
verified 170 persistence files and eight gateway callers against the
public tree. Of 148 previous functions and methods, 38 have an exact
syntax match in those persistence files. This establishes candidates for
review. Gateway callers now use storage admissions, authority bindings
and conversation read interfaces for operations formerly supplied by
the state modules. Continue with admission, profile and binding checks,
then mutation receipts, retirement and the no-replay recovery contract
before adding new watched source paths.

[Main storage facade](https://github.com/NousResearch/hermes-agent/blob/364aded7ee2a2888421fc7aec96adbe6c07748a5/hermes_state.py)
· [Main gateway authority caller](https://github.com/NousResearch/hermes-agent/blob/364aded7ee2a2888421fc7aec96adbe6c07748a5/gateway/session_authority.py).
