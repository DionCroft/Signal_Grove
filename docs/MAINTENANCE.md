# Keeping the companion current with Hermes

The active [contract baseline](contracts/hermes-baseline.json) describes the implemented mobile client, reviewed against Hermes v0.22.6 (`f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab`). It tracks 32 inference API routes, sixteen dashboard management routes, 80 handler AST fingerprints and eighteen management request fields, plus used static flags and cron limits/fields. The historical [2026-11-02 audit](audits/2026-11-02/REPORT.md) remains unchanged; its four confirmed mobile defects have been fixed and are not represented as open in the active baseline.

The **Hermes compatibility** GitHub workflow runs weekly on Monday at 07:27 UTC, on relevant source/baseline changes, and through manual dispatch. It checks latest stable and main separately. It reads public GitHub source at one resolved commit, parses it as data and never imports or executes Hermes. It sends no deployment credentials and performs no server mutation. PRs run the offline parser tests without the live-source job. Results and additions are retained in workflow artifacts; used-contract changes fail their check for review. Module hashes also expose changes outside the explicitly tracked handlers. New routes and features require triage even when existing used contracts remain unchanged. No baseline or dependency is updated automatically.

Local commands (Python 3.10+ and authenticated GitHub CLI):

```text
python -B -m unittest discover -s scripts/tests -v
python -B scripts/check_hermes_contract.py --ref latest
python -B scripts/check_hermes_contract.py --ref main
```

On the owner’s Windows machine, set TEMP/TMP to E: before invoking tools and keep output files there. `--output` saves JSON; `--baseline` selects historical evidence; `--snapshot-only` emits a source snapshot. Exit 0 means no change to this tracked surface, 2 means used-contract review is required, and 3 means inspection was incomplete. A passing check is not live interoperability or security certification. Transitive helpers, provider behavior, deployed configuration, runtime gates, auth policies and hardware/OEM behavior still need review and their actual environments.

Before accepting a changed baseline, review the source/payload and pre-mutation guards, adjust the client and meaningful regressions as needed, and rerun local checks. Replace the baseline’s `upstream` section only with the reviewed snapshot, preserve the used lists and extend them for newly implemented routes/fields/handlers. Record the reviewed commit/date and rerun stable/main. Do not copy an entire new baseline merely to clear a failing check. AST fingerprints ignore comments, top-level handler docstrings, locations and optional empty fields; changed executable bodies and field types remain visible.

The normal Android workflow currently builds, runs JVM tests/lint and compiles instrumentation. Device CI execution and dependency verification are additional work; local device coverage is recorded separately in [VALIDATION.md](VALIDATION.md). Source checks complement those tests and cannot replace them.

Workflow scheduling follows the [GitHub Actions schedule contract](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule); repository Actions must remain enabled.
