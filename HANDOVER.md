# Signal Grove handover

13 November 2026. Continue the existing **active** goal with its four explicit
[acceptance priorities](docs/GOAL.md); retain every original audit row.

## Current checkpoint

Read [Demo and shutdown diagnostics](docs/contracts/2026-11-13-demo-shutdown-diagnostics.md). The old
source 1180 failure is not fixed:15 original class cases and its full 598-case
shard now pass locally. A fresh failure-only snapshot records safe fixture state
without click retries or changed waits/assertions. The exact diagnostic pair
passes 5 native predecessor/Demo cases. Full clean-head standard CI remains.

The required build passes 1,011 JVM/97 reports,422 policy and15 runtime-model
cases, both lint variants with 0 errors (51/50 warnings), original debug signing,
all 4 ABIs and DEX/64-bit16KiB packaging. Frozen build input is b5433027 plus
the two reviewed diagnostic changes; product source/behavior is unchanged.

Actual upstream shutdown still returns exit 2: shared DB close precedes heartbeat
atexit cleanup and connection reopen. Optional bounded profiling reproduces
this with 20 seeded owner-room checks and unchanged SDK/auth/source guards.
Stop/drain-before-close and idempotent cleanup need a real regression/fix.
Keep warnings/callbacks and original failing evidence intact.

Current stable 3b42157/main 0e72e8a scans retain 40/104 changed-handler findings.
The3 newly flagged model/profile changes are statically triaged, not waived.
No new pin is trusted for runtime execution and no live service was changed.

## Continue

Reconcile the new diagnostic commit's hosted raw partitions/summary and safe
failure snapshots before claiming complete coverage or a Demo cause. Do not
combine APK pairs/sources or relabel a positive rerun as the old failure's fix.
Preserve all earlier failures and unknown worker receipts; no blind replay or
daemon restart. Real worker/project/automation/integration acceptance remains.

The phone is not currently visible over USB; a pending question asks whether
public beta.9 is installed. Verify current package UID/key/version and data
before any in-place production check. Never clear phone data, run destructive
fixture resets there, or repeat the already completed authorized normal reboot.

## Release state

Public beta.9/code114 remains an experimental preview from source 0ee08e3, with
original signer and 6 public asset hashes verified. Stable 1.0.0 and published
APKs are unchanged. Production/live/offline upgrade acceptance and wider release
gates remain disclosed and open. The prior a2a3dd2 hosted Android run is green;
its raw partition union was not independently validated in this checkpoint.

Use the requested authoritative share for source and E: for all tools/caches/
builds/models/AVDs/private evidence. Source Git history, concise project state
and validation/audit documents preserve all prior scope and evidence.
