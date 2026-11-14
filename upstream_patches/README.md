# Experimental Hermes lifecycle candidate

This directory contains a reviewed two-file candidate patch for exact Hermes
`3b421578588243f920886e6fe79a6faa87ea026e`. It addresses a heartbeat cleanup write
after gateway shutdown closes shared SessionDB handles. It is experimental test
material, **not installed on a live gateway** and not bundled into the Android
APK. Unmodified upstream acceptance and the full Signal Grove audit remain open.

The candidate closes heartbeat admission, tracks synchronous/periodic writes,
drains its one cleanup attempt and clears captured registrations before the
gateway closes any session DB. A live or failed cleanup keeps the close gate
closed. Exit callbacks observe the same completed attempt without reopening a
handle or replaying a failed write. Register-once mode remains nonperiodic.

```mermaid
flowchart TD
    Shutdown[Gateway shutdown] --> Admission[Stop heartbeat admission]
    Admission --> Drain[Drain admitted writes and one cleanup]
    Drain -->|Finished successfully|Close[Close session databases]
    Drain -->|Live or failed|Keep[Keep databases open]
    Exit[Later exit callback] --> Receipt[Observe the same cleanup result]
```

`scripts/hermes_heartbeat_candidate.py` reads the exact base's Git blobs into a
fresh scratch snapshot, applies only the checksum-pinned patch, then verifies
every source file. It refuses a dirty/wrong base, extra/missing/changed files,
ambiguous paths, reparse points and a reused output. Base checkout and live
services are never modified. Git blob materialization avoids archive/checkout
EOL filters. All Windows SDK/scratch paths must use E:.

## Reproduce the helper regressions

Use an existing dependency environment and a clean immutable checkout of the
exact base. Set TEMP/TMP to your approved scratch directory. Substitute your
own absolute paths; keep the output fresh and outside the SDK checkout.

```text
python -B scripts/hermes_heartbeat_candidate.py prepare --base-root <SDK_BASE> --output <FRESH_CANDIDATE>
python -I -S -B scripts/compatibility_probe_heartbeat_lifecycle.py --base-root <SDK_BASE> --source-root <FRESH_CANDIDATE>/source --dependency-site <EXISTING_SITE_PACKAGES> --work-root <FRESH_FIXTURE_OUTPUT>
```

The second command verifies the entire candidate before loading SDK code. Its
eight tests use the real patched helpers, official binding mechanism and SQLite
with an explicit synthetic server/callback registry and controlled blocked/
failed fixture writes. It refuses network, subprocesses and outside writes.
Its positive result is **not full native owner-runtime or deployed acceptance**.
Failed roots/receipts are retained; inspect them before making a new trial.

## Reproduce the finite native owner trial

On Windows, use the existing isolated dependency environment and a fresh output
for each invocation. After preparing the exact candidate above:

```text
python -I -S -B scripts/compatibility_probe_owner_runtime.py --source-commit 3b421578588243f920886e6fe79a6faa87ea026e --source-root <FRESH_CANDIDATE>/source --heartbeat-candidate-base <SDK_BASE> --dependency-site <EXISTING_SITE_PACKAGES> --work-root <FRESH_OWNER_OUTPUT> --trace-close-order
```

For the seeded room trial, use another fresh output and add
`--seeded-room-reads`. For the actual SQLite contention trial, use a third fresh
output and add both `--seeded-room-reads --held-heartbeat-writer`. The held mode
requires explicit candidate opt-in. Unsupported pins and unreviewed snapshots
refuse before SDK execution. Reports identify experimental derived source;
successful candidate exit is never unmodified-upstream acceptance.

These three finite native trials pass actual bootstrap/tickets/profile/socket
checks and process exit; both seeded trials retain 20 room checks. The held
writer leaves both DBs open at its bounded close gate, then drains/closes after
lock release. They replace no SDK function and run no model/tool/user turn.
The default pristine mode retains its original warning/exit 2. No source guard,
warning or callback is waived. The full semantic, deployed and Android/Pi
acceptance remains separate. See [exact trial evidence](../docs/contracts/2026-11-13-native-owner-candidate-and-ui-replay.md).

The patch is derived from Nous Research's MIT-licensed Hermes Agent source.
See [the upstream notice](NOUS_RESEARCH_LICENSE) and the repository's
[original-code licence](../LICENSE). No upstream issue, PR or deployment is
performed by these tools.


The [whole-snapshot preparer](../docs/contracts/2026-11-14-snapshot-heartbeat-preparer.md)
can preserve an explicitly reviewed maintained tree when its two target inputs
match this patch. It retains all other bytes and native modes, requires a fresh
sibling source/evidence root, and rejects any unreviewed map or extra delta.
It performs no SDK execution, PM change or deployment. The original pristine
preparer and original failure/compatibility gates remain independent.
