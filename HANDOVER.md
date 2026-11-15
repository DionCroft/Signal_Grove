# Signal Grove handover

15 November 2026. Continue the **active full goal**, using [PROJECT_STATE](PROJECT_STATE.md),
[GOAL](docs/GOAL.md), the [complete audit](docs/AUDIT_EXECUTION_PLAN.md) and exact
source/evidence. No original requirement or failed/unknown receipt is removed.

## Current runtime finding

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

## One next action

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

## Preserve and finish

- Finish original API26 Demo/recent failure causes, full upstream used/transitive
  compatibility and real operational shutdown; keep the original warning.
- Finish real authenticated project/workspace/worker/NAS execution adoption,
  automation/Kanban/rooms/grants/delivery, integrations/configuration/doctor/graph.
- Finish twelve external-model, final physical phone/lifecycle/audio/background/
  notification/accessibility/battery/thermal and original-key production upgrade,
  security/support/exact-source release checks. Beta.9/code114 is unchanged.
- Preserve production/debug signing separation, UID/schema and connections,
  chats/projects/models/local history. Keep on-device-only dictation, no automatic
  cloud fallback, no unknown-work replay, no credential transfer/public exposure.
- Isolated staging approval does not authorize live cutover, canonical provisioning,
  real-home backup or a gateway restart. Finish the concrete packet before its
  separate final approval. The previous physical restart approval is consumed.
- Source remains in the authoritative working copy; tools/cache/temp/build/private
  evidence stay on the selected temporary drive. User projects and application
  execution remain on their enrolled owners, not Pi.

The prior backup component keeps its separately verified real SDK/age/loopback
scope and generated-data native handoff evidence. Full cutover/runtime-only
recovery and real private GitHub/live-home/external-provider backup stay open.
Historical details remain in [EVIDENCE](EVIDENCE.md), [VALIDATION](docs/VALIDATION.md),
linked contracts and the
[previous handover](https://github.com/DionCroft/Signal_Grove/blob/1a07bf6ce67ba1f1847ceb983da742ce7af12733/HANDOVER.md).
