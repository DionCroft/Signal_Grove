# Signal Grove project state

15 November 2026. The full goal is **active**. The [goal](docs/GOAL.md),
[full audit](docs/AUDIT_EXECUTION_PLAN.md) and [on-device plan](docs/ON_DEVICE_AI_PLAN.md)
retain all requirements. Source stays in the authoritative repository; tools,
scratch, builds and private evidence stay on the selected temporary drive.

## Current verified checkpoint

Final-source fresh-home trials now pass startup-idle, actual SDK admission
freeze/refusal, external drain and normal native shutdown in **both profiler
modes**. Startup-idle has its own five-second window; drain keeps its original
five-second window. Neither window renews, masks work nor accepts late/unknown
observations. The SDK facade and real GatewayRunner run without SDK patches;
the CLI `main` entrypoint is not exercised.

Without profiling, four busy startup and three busy drain replies precede idle
observations in 1.971 s and 1.368 s respectively. With profiling, startup is idle
in 0.178 s and four busy drain replies precede idle in 3.439 s. All registered
counts are zero at each accepted gate. These timings describe two generated
fixtures; they do not establish the earlier timeout's cause or fleet readiness.

Independent readback verifies 21 decoded replies, 15 status projections, seven
original profiled wire replies, both frozen ledgers/markers, three ended native
groups, all 23,628 SDK files and 5,033 dependency files. The SDK source-origin
union is 1,139 and its native stop budget remains 70 seconds. Live source/facts,
existing modes, services and every previous held outcome are preserved.

Current Windows checks pass **864 of 1,118 cases, with 254 platform skips**.
All **35 focused methods** pass on Pi without skips, including twelve new window
regressions. Parent source `d1f8a75` independently passes **1,106 hosted Linux
methods/no skips**; its 37 stable / 172 recorded-main semantic findings remain
unaccepted. The original-journal drain actor, all-profile/worker admission,
unified executor and full audit/release gates remain open. Published beta.9 and
live services are unchanged. The full goal stays active.
[Evidence and limits](docs/contracts/2026-11-15-bounded-gateway-startup-idle.md).

## Next action

Implement and qualify the original-journal native gateway-drain actor using
reviewed source/home/manager identities, the original approval and dispatch
claim, one SDK freeze and exclusive non-overwriting marker publication. Record
the reviewed fence preimage, including valid released nonzero epochs; never
adopt an existing frozen owner or treat SDK idempotence as retry permission.
Use fresh owner-bound status reads with fixed expiry and preserved unknowns.
Then qualify all-profile/worker admission, unified executor, reload/authenticated
health and bounded runtime-only recovery before preparing final live approval.
Canonical PM contexts, seven real modes, policy/launcher changes and real
private GitHub/live-home backup remain unactivated. Every original audit,
local-model, phone and original-key release requirement remains required.

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
