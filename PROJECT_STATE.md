# Signal Grove project state

15 November 2026. The full goal is **active**. The [goal](docs/GOAL.md),
[full audit](docs/AUDIT_EXECUTION_PLAN.md) and [on-device plan](docs/ON_DEVICE_AI_PLAN.md)
retain all requirements. Source stays in the authoritative repository; tools,
scratch, builds and private evidence stay on the selected temporary drive.

## Current verified checkpoint

The maintained Pi SDK now has a source-bound correction for two actual findings:
its ordinary turn path referenced a missing peer-context module, and its status
observer omitted active unbound turns. A fresh immutable SDK copy adds the exact
86-line maintained peer module and includes the existing unbound counter inside
the same read-only database snapshot. The original staged SDK is unchanged.

The corrected SDK passes **16 read-only status checks using two real native turn
receipts**. The original observer reports zero while the correction reports one
active outer turn and two during nesting. Checks retain strict peer-context
routing, profile separation, freeze/new-admission and active-thaw refusals,
completion while frozen, and five expected UNKNOWN outcomes for incomplete,
missing or locked ledgers. Every status observation leaves generated files
unchanged. Independent readback verifies both finished receipts, the retained
frozen fence, ended native groups and all source/dependency/PM/live baselines.

The exact patch postimages verify. Windows completes **1,158 policy cases:
885 positive / 273 platform skips**, matching the earlier distinct inventory.
The 16 native observations are separate component checks, not additional policy
methods. Android sources still match the previously verified build snapshot:
1,011 JVM tests, 27 focused methods per API26/API37 and zero lint errors, with
51 debug / 50 release warnings. No new Android build or production APK is claimed.

Both failed native preparations remain preserved: the first exposed the missing
module; the next hit a fixture assumption about cold configuration initialization.
The final probe accounts separately for the SDK's default SOUL/config backup,
then verifies read-only status behavior. No SDK function or counter is mocked.
The original SDK's prior configured freeze/drain/native-stop result remains
separate. The corrected SDK still needs actual gateway-wire/original-journal
qualification, concurrent snapshot races and a final PM generation. Upstream,
API26 original/navigation, fleet/worker/integration and production gates remain
open. Live Hermes, the owner's phone and published beta.9 are unchanged.
[Evidence and limits](docs/contracts/2026-11-15-maintained-unbound-dependency-and-status.md).

## Next action

Exercise the declared corrected SDK through actual normal-gateway status and
the original-journal drain with owned unbound work. Verify known-busy reporting,
bounded completion, one freeze/exclusive marker and normal native stop without
relaxing the original observation/expiry, native owner or no-replay checks.
Qualify concurrent snapshots and unprofiled behavior; prepare a final PM context
only from the reviewed declared source. The existing staged candidate stays intact.

Then reproduce and fix the retained API26 phone-draft navigation and original
Demo/history causes; finish exact-source standard device coverage and supported
used/transitive upstream compatibility. Complete marker/source/owner races,
all-profile/worker admission, unified execution, authenticated health and runtime
recovery before a concrete final live packet. Canonical PM contexts, seven real
modes, policy/launcher changes and private GitHub/live-home backup remain
unactivated. Preserve every original audit, local-model, physical-phone and
original-key APK delivery requirement. The full goal remains active.

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
