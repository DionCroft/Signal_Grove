# Signal Grove project state

19 November 2026. The full goal is **active**, resumed by the owner. Fix UI
blockers first, then OF-01 through OF-10; commit/push tested chunks. The
[goal](docs/GOAL.md), [audit](docs/AUDIT_EXECUTION_PLAN.md) and
[ordered backlog](docs/ON_DEVICE_AI_BACKLOG.md) retain all original requirements.

## Current source and evidence

Main `a992cf4` contains the UI readiness/viewport fixes, with independently
verified identical-tree PR coverage of 1,149 API26 / 1,150 API37 standard methods,
no skips. Main Android/Windows pass. Upstream scan fails closed on moved/missing
tracked modules at v0.23.2/current main. Replacements/transitive contracts and
prior semantic/shutdown findings need review; no compatibility pass is inferred.

Unreleased `feat/offline-chat-actions` implements reviewed edit/regenerate/fork,
scoped drafts, request provenance, passive reply/code readers and local history
text/model/date search. The next organization store keeps titles/pin/archive
metadata outside transcripts, but has no connected UI yet. Latest required
builds pass 1,082 JVM cases/103 reports and zero-error lint (51/50 warnings).
The same new APK pair passes 40 selected native regression methods on each
API26/API37 without skips. Organization behavior has 11 new JVM regressions;
Android-specific organization cases/controls remain to implement.
[Current source bindings](docs/contracts/2026-11-19-history-organization-foundation.md).

The earlier phone pairing passes one actual offline native branch/Stop method,
then actual UI fork/edit/regenerate, each preserving earlier transcripts. The
matching search build is then upgraded in place, retaining UIDs, all 16 protected
metadata files and both models. Actual offline/unloaded Search/Clear/garden read
works; four chat files and model bytes stay unchanged. Networking/wake settings
and default network restore. The normal code112 app is untouched. These exact
phone builds do not qualify the newer organization APK.
[Phone search evidence](docs/contracts/2026-11-19-phone-history-search-physical.md).

The separate four-case phone search instrumentation stalls before first render;
one foreground request does not advance it, then one debug-only stop produces
an unsuccessful receipt. No case pass/retry is claimed. Diagnose its startup
before rerunning. SQLite main/WAL and other metadata retain bytes; changed
transient SHM is reconciled with read-only integrity `ok`, without phone writes.

## Next action

Wire reviewed rename/pin/archive into controller/history UI and search, retaining
unfiltered inventory, parent access, model bindings and drafts. Add real Android
index/keyboard/cancellation checks before claiming those controls. Continue
phone instrumentation diagnosis and remaining OF-01 accessibility/lifecycle
acceptance. Then finish OF-02 Markdown export and protected backup/import.

The original upstream/shutdown, SDK/gateway/unbound drain/concurrency/PM/fleet/
recovery, real workspace/worker/files/NAS, automation/rooms/integrations/config,
resource/security and final original-key production upgrade/release gates remain.
Published beta.9 is unchanged. No Pi service/gateway/credential/model/default/
grant changes. Isolated staging does not authorize live cutover, canonical PM/
policy/launcher/private backup or restart changes.
