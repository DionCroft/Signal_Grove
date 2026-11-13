# Signal Grove project state

13 November 2026. The existing full audit/on-device AI goal is **active**.
Preserve all four [explicit priorities](docs/GOAL.md) and every original
[audit requirement](docs/AUDIT_EXECUTION_PLAN.md).

## Latest tested chunk

- Original source 1180 API26 shard1/2 passes 598 unchanged cases after15 original
  class-trial passes. The hosted Demo failure is still unreproduced/unfixed.
- Failure-only safe Demo snapshots preserve interactions, assertions and waits.
  Fresh original-key debug/test APKs pass5 focused predecessor/Demo cases.
- Required build checks pass 1,011 JVM/97 reports,422 policy and15 runtime-model
  cases; both lints have 0 errors and 51/50 warnings. All4 ABIs/original signer,
  DEX and 64-bit 16KiB native packaging verify. No product behavior changed.
- Default/optional native owner probes preserve the real shutdown warning and
  exit 2. Actual trace: shared DB closes before heartbeat cleanup reopens it.
  Traced seeded mode retains 20 room checks. No SDK/live correction applied.
- Fresh stable 3b42157/main 0e72e8a static scans retain 40/104 findings. The3 newly
  flagged model/profile handlers were inspected without accepting new variants.
- [Exact input hashes, native logs and limits](docs/contracts/2026-11-13-demo-shutdown-diagnostics.md).

## Delivery

Beta.9/code114 remains the published experimental preview from source 0ee08e3,
with original production signer and all 6 public download hashes verified.
Stable 1.0.0 and the released APK are unchanged. The prior a2a3dd2 hosted Android
run is green; raw complete-gate verification was not repeated for that source.
This diagnostic chunk still requires final clean-head standard CI.

## Exact next action

Reconcile the diagnostic commit's hosted Android artifacts and safe failure
snapshots, if any. Determine the Demo failure's actual cause before changing
product readiness. Separately prepare/test an idempotent heartbeat lifecycle
correction; preserve the failing cleanup evidence until complete acceptance.

No physical phone is visible over USB; the production beta.9 upgrade state is
unknown. A pending owner question requests its current installation/connection.
Resume the in-place production retention/live/offline gates when available;
never repeat the earlier approved normal reboot or clear owner data.

Real project/workspace/worker, automation/integration, approvals/images/recovery,
notifications/background/audio/accessibility/performance and final release
checks remain required. Preserve unknown worker receipts; never replay or
restart that daemon blindly. No new live Pi/gateway/model/credential change is
part of this diagnostic stage. All scratch/builds/private evidence stay on E:;
source remains on the canonical requested share. Historical checkpoints remain
in validation/audit records and Git history.
