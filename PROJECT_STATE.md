# Signal Grove project state

15 November 2026. The full goal is **active**. The [goal](docs/GOAL.md),
[full audit](docs/AUDIT_EXECUTION_PLAN.md) and [on-device plan](docs/ON_DEVICE_AI_PLAN.md)
retain all requirements. Source stays in the authoritative repository; tools,
scratch, builds and private evidence stay on the selected temporary drive.

## Current verified checkpoint

The native gateway status reader now preserves the SDK admission fence's owner
and rejects impossible initial/positive-epoch ownership combinations. Five new
portable regressions cover initial, frozen, released, malformed and foreign-owner
states. Observing another owner's fence gives no permission to adopt it.

Current checks pass **829 Windows methods in 1,083 cases, with 254 platform
skips**, **104 Pi cohort methods/no skips**, and eight real SDK Unix requests
across four generated-home modes. Independent raw/SQLite/native readback verifies
the exact fence owner/epoch, ended peers and stopped inspected units. The SDK
runner and ledgers are synthetic; normal GatewayRunner and real drain remain open.

Parent source `1a07bf6` independently passes **1,078 hosted Linux policy methods
with no skips**. Source `09306e6` now has a reconciled full standard Android gate:
**1,011 JVM methods, 1,142 API26 / 1,143 API37 device methods**, exact disjoint raw
unions, recomputed summary and matching actual APK hashes. Both lint variants
have zero errors (52 debug / 51 release warnings). These exact-source results do
not close original UI causes, twelve external-model or final production-phone gates.

The latest inspected upstream reports retain **37 stable / 172 main changed-
handler findings**; main is now `d1b46d7`. Those are review findings, not accepted
compatibility. Source inspection covers 1,724 staged admission/drain-related
Python files. It establishes specific call sites, not whole-fleet enforcement.
Live services and published beta.9 remain unchanged. The full goal stays active.
[Exact source, artifacts and limits](docs/contracts/2026-11-15-gateway-fence-owner-and-android-review.md).

The earlier original-child backup fix retains its independent 600 generated
decryptor starts, five native handoffs and real SDK/age/loopback backup: twelve
archive members and two committed WAL rows. The original exact failure cause
remains unproven. [Evidence](docs/contracts/2026-11-15-original-child-exec-handoff.md).

## Next action

Implement the original-journal native gateway drain using reviewed existing
per-profile admission ledgers and a fresh, exclusively owned marker; qualify
normal GatewayRunner in a finite isolated unit. Bind exact native owners, source,
ancestry, approval and expiry. Preserve any existing/uncertain fence or marker
without adoption, replay or automatic release. Then compose all-profile/worker
admission, the unified executor, reload/authenticated health and bounded runtime-
only recovery. Both canonical PM contexts, seven real modes, policy/launcher
references, real private GitHub/live-home backup and separate final live approval
remain unactivated. Continue all four original acceptance priorities.

## Remaining acceptance and operating boundaries

1. Original Demo/recent API26 failure causes, actual fixes and final-source
   standard device gates; later non-recurrence does not establish a cause.
2. Full used/transitive supported-Hermes compatibility and operational shutdown,
   including the separately retained original owner-runtime cleanup warning.
3. Authenticated chat/workspace adoption, worker identity, registered roots,
   repository/file/NAS access and reviewed execution on the owning host; finish
   Kanban/jobs/rooms/grants/delivery/integrations/configuration and doctor/graph.
4. Exact original-key production phone upgrade, signing/UID/schema/data retention,
   live/offline models, lifecycle/audio/background/notifications/accessibility,
   sustained battery/thermal, security/advisory/support and final release gates.

Isolated native/SDK fixtures do not activate production. Both canonical old-stop
and new-start PM contexts remain unprepared; seven real modes remain unchanged.
The one-validation-project policy/grant is not fleet/project-wide authority.
No credential transfer, public exposure, model eviction or cloud fallback; never
replay unknown work or invoke a reaper in a shared cgroup. Preserve the owner's
on-device-only dictation choice, saved server/local identities and all data.
The previous phone restart approval is consumed. Project working copies stay
on their registered execution owners; only control-plane material belongs on Pi.

Published beta.9/code114 remains the separately identified experimental preview.
No new APK or production acceptance is implied by this source checkpoint.
Historical records and failed/unknown receipts remain in [evidence](EVIDENCE.md),
[validation](docs/VALIDATION.md), linked contracts and the
[previous state](https://github.com/DionCroft/Signal_Grove/blob/1a07bf6ce67ba1f1847ceb983da742ce7af12733/PROJECT_STATE.md).
