# Signal Grove project state

## Current checkpoint - Windows worker file-read provider

An internal owning-host provider now prepares registered project/root bindings,
lists bounded directories and previews exact listed text files through real
Windows disk handles. Authenticated actor/profile scopes, actual hostname,
process instance, registry revision, full 128-bit file IDs, entry tokens and expiry are
fenced. Held ancestor handles prevent replacement during reads; junctions,
reparse/hard links, traversal/ADS/device aliases and credential trees refuse.
Lists/previews are bounded and contents stay out of logs/model context. Full
file-ID support is mandatory; there is no legacy 64-bit fallback. The current
mapped network repository returns native error 87 for FileIdInfo and is explicitly
UNSUPPORTED. This is a compatibility finding, not completed NAS project access.

This is not yet a broker, Hermes or Android file-browser integration. A trusted
interactive worker must supply fresh authorized registry bindings. Native Hermes
filesystem defaults do not certify the separate Windows project owner.

Source 566 passes 1004 JVM / 96 reports, 347 script policies, both lint variants
with zero errors (51/50 warnings), original signing and all 32 native-library
ABI/alignment checks. Twenty-three real Windows filesystem cases pass, without
skips and with source hashes unchanged. Two unchanged Android project-gateway
cases pass on the installed source 566 pair after all 9 protected file hashes/UIDs
are retained. These scopes do not certify deployed/NAS/Surface/phone access.

Hosted Windows provider acceptance is independently verified for main commit
0ad6c6a: 23 distinct native successes, no skips/errors/source drift. The retained
ZIP digest and all three source hashes match the committed blobs with Windows
checkout line endings. This closes only that hosted provider gate; Android,
broker, NAS/Surface, production and physical/release acceptance stay open.

The older daa3ec8 hosted suite is now independently verified: 1132 API26 and 1133
API37 exact positive cases, no missing/skipped/failed cases; eleven external-model
methods stay not-run. Its success covers only that parent. The newer 2a763fe
four-shard jobs were still live at this checkpoint; new-source full union,
compatibility, production/physical/model/lifecycle and every broader audit row
remain required. Beta.8 remains published; goal stays active.

[Provider boundary and evidence](docs/contracts/2026-11-12-windows-workspace-provider.md).

**Next exact action:** implement and test the fixed read adapter in the trusted
interactive broker, preserving authenticated actor/profile-to-worker binding
and keeping preview contents out of coordinator storage. Then wire the optional
Hermes capability and Android root/file review; retain live full CI separately.

## Continuing scope and authoritative records

Published application: v1.1.0-beta.8 (code111). Current source includes later
optional phone inference, user-model/helper and canonical/project-navigation
work. Source changes do not certify that published APK or the final phone build.
Final delivery explicitly includes the original-key production APK as a public
versioned GitHub Release asset, verified download/checksums and retained-data
phone upgrade, with a direct APK link. Publish after applicable release gates;
source pushes or temporary CI artifacts do not fulfil that delivery.
The goal remains active; all unresolved requirements stay in the
[full audit ledger](docs/AUDIT_EXECUTION_PLAN.md) and
[on-device AI plan](docs/ON_DEVICE_AI_PLAN.md).

Worker/root/file ownership and reviewed dispatch, real production auth/recovery/
approvals/images/integrations, advanced Kanban/automation/rooms/configuration,
notification/multi-connection/voice/background performance, physical/model
acceptance, advisories/provenance and the signed community release remain open.
Project metadata or a passing fixture does not establish a live worker root.

Prior checkpoints remain in Git and the detailed evidence records:

- [Default-keyboard readiness](docs/contracts/2026-11-12-owned-keyboard-readiness.md).
- [Actual-owner project reads](docs/contracts/2026-11-11-actual-owner-project-conversations.md).
- [Project navigation](docs/contracts/2026-11-11-project-conversation-navigation.md).
- [Native input diagnostics](docs/contracts/2026-11-11-native-input-diagnostics.md).
- [Complete validation history](docs/VALIDATION.md).
