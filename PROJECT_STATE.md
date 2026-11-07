# Signal Grove project state

The full audit goal remains active. Continue every required row in
[the execution plan](docs/AUDIT_EXECUTION_PLAN.md) in tested, regularly pushed
chunks. Preserve data, original signers, main, TLS/profile isolation, explicit
approvals and durable no-replay handling.

## Current bounded implementation

Conversation history now measures the hosting activity's visible viewport on
Android31+ and limits the entire dialog, leaving its weighted list scrollable
and footer above the keyboard. Older Android retains its existing inset path.
The compact1920px profile reproduces the original failure and passes the same
unchanged native paging/Close test after the correction. The first dialog-local
measurement attempt failed on compact37 and remains retained.

Local acceptance:599 distinct JVM cases, fresh debug/release lint0, original CAB
signers and30 unchanged history/image/title/setup native methods on each preserved
API26, largeAPI37 and compactAPI37 profile.583 source inputs were frozen through
build/native acceptance; only these documents change after freeze closure.
Feature defaults remain111/beta.8 and unpublished. This is bounded local
acceptance; complete new-head hosted and physical upgrade gates remain open.

## Retained hosted results

Foundation ffd6607:599 JVM and both fresh lint pass. Full native run42376822956
fails API26 at1010/1011 (Demo send test) and API37 at1011/1012 (compact history
keyboard clipping), with no skips or unfinished methods. Both original artifacts
were independently digest checked. No full feature acceptance is claimed.
Compatibility42376830167 passes179 policy,15 observer,eight registry cases and
source inspections; this does not certify deployed canonical session authority.

Release2e10173 remains held at110/beta.7. Its API26 full936 methods pass; API37
fails936/937 at the compact keyboard case. The original-key signed assets are
explicitly held and must not be published or reused as corrected-source assets.
Stable1.0/main6b809e6 and public beta.6 remain unchanged.

## Exact next action

Investigate the retained API26 Demo send/input failure without relaxing its
outcome assertions. Backport the byte-identical compact-history correction to
the release branch, build fresh original-key assets and complete exact-head
hosted gates. The owner phone is disconnected; prior installed chat/history
retention and read-aloud evidence do not accept a new APK. Complete approved
matching-signer upgrade/readback when reconnected, then publish verified assets.

Continue canonical attachment/adoption, exact session handles, worker routing,
files, automation/rooms/grants, integrations/config, notifications, voice/OEM and
security/community closure. Project metadata is not workspace adoption or
execution. See docs/contracts/2026-11-07-canonical-workspace-authority.md.
