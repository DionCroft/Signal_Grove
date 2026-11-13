# Signal Grove handover

The full audit/on-device AI goal remains **active**, retaining all four
[explicit priorities](docs/GOAL.md) and every original audit requirement.

## Latest tested chunk

Failure-only snapshots distinguish rendered navigation/list state from the
ViewModel's data, plus locale/lifecycle/IME. Exact reverse source comparison
preserves original successful actions/waits/assertions. A controlled native
counterexample satisfies the original 16-message data predicates on Home with
no message list, verifies safe retained JSON/cause and then renders Chat with
the same data. Private fixture values are excluded. This does not reproduce or
fix either original hosted failure. All 13 focused API26 cases pass; required
checks pass 1,011 JVM/97 reports, lint 0 errors (51/50 warnings), original debug
signing and 4-ABI/DEX/native packaging. New API26 inventory is 1,136, not a claimed
complete new standard gate. [Exact evidence](docs/contracts/2026-11-13-rendered-ui-failure-diagnostics.md).

## Exact next action

Test the original onLast Chat-description click across a controlled runtime-
picker keyboard dismissal, inspecting whether the navigation bar or an off-
screen recent-chat action is selected. The Home snapshot has five matches;
ambiguity is observed but the original wrong target is unproven. Also inspect
new exact-source hosted reports and any failure JSON before changing behavior. Passing replays and the controlled counterexample do not
establish the old failures' cause. Do not add waits, weaken assertions, blindly
retry jobs or mix source/APK cohorts to claim a complete gate.

## Remaining scope and safeguards

The last independently verified complete device gate remains c9c0718's1,135
API26/1,136 API37 cases. Default pristine Hermes retains the shutdown warning;
the explicit finite experimental native candidate passes but is not deployed.
Wider upstream semantic/transitive, real project/workspace/worker, automation/
integration, approvals/images/recovery, background/notifications/audio/
accessibility/performance and final production upgrade/live/offline release
gates stay required. Public beta.9/source 0ee and stable 1.0.0 are unchanged.

Only the verified owned disposable emulator is used. The owner phone is absent
from the current USB inventory; a connection question is already pending.
Preserve owner package/signing/UID/model/chat/connection data. Do not clear it,
repeat the earlier phone reboot, restart worker daemons, replay unknown worker
receipts or change live gateway/models/credentials/jobs without applicable
authorization. Source remains on its authoritative share; all scratch/builds/
models/AVDs/private evidence stay on E:. All original audit rows are retained.
