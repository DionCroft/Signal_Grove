# Signal Grove project state

## Current checkpoint - exact complete device partitions

Standard CI now executes two AndroidJUnitRunner shards on each API, keeping the
same test bodies/assertions, 100-minute command limit and five-class/eleven-method
external-model boundary. Each fresh owned device records a full log-only
inventory, installed APK hashes and native case evidence. A separate union gate
requires both APIs, the same source/APK pair, identical per-API full inventories,
one positive start/finish per distinct case and disjoint complete native unions.
Inventory, missing/duplicated/failed/skipped/partial output and changed raw logs
cannot become a complete standard-suite pass.

Source 530 passes 1004 JVM / 96 reports, 335 policies, both lint variants with zero
errors (51 debug/50 release warnings), original debug signing and all 32 packaged
native-library ABI/alignment checks. Full inventory-only partitions are
1132=535+597 on API26 and 1133=536+597 on API37. Six real focused methods per API
pass across both shards (1+5), with settings restored. A real focused-only union
is refused as incomplete. The CLI also accepts a synthetic valid matrix and
rejects altered evidence; that fixture is not Android acceptance.

Protected upgrades retain all 9 / 1547 file hashes and UIDs. Preserve the new
API37 disposable preboot failure, the host log-name collision after independently
verified upgrades and the focused zero-test rejection; none is relabelled as a
pass or blindly replayed. Full hosted native union, monolithic timeout/long-lived
performance, physical/models/lifecycle, production and every worker/files,
integration/advisory and signed-release row remain open. Beta.8 remains published.

[Scope/evidence](docs/contracts/2026-11-12-complete-device-partitions.md).

**Next exact action:** retain new-source full four-partition CI/union evidence and
review unwaived compatibility reports, then continue verified worker/root/file
access and the complete original audit ledger. Preserve live parent CI jobs.

## Continuing scope and authoritative records

Published application: v1.1.0-beta.8 (code111). Current source includes later
optional phone inference, user-model/helper and canonical/project-navigation
work. Source changes do not certify that published APK or the final phone build.
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
