# Signal Grove project state

## Current checkpoint - checked worker read frames

An internal broker adapter now accepts bounded strict JSON byte frames for one
already authenticated interactive actor/profile context. Worker instance,
request correlation, policy revision and fresh context are fenced. Duplicate
IDs refuse without replay; four live reads and 256 retained IDs are maximums.
Close retires the provider; context loss/revocation drops observed contents.
The close-from-context-callback regression fails before the fix and passes after.

Source 600 passes 1004 JVM / 96 reports, 353 policies and both lint variants with
zero errors (51 debug / 50 release warnings). Original debug signing and all 32
native-library ABI/alignment checks pass. Thirty-three real Windows cases pass
with no skips/source drift; two unchanged Android project-gateway cases pass
after all 9 protected hashes and package UIDs survive the verified upgrade.
These are internal component checks, not deployed broker or phone-file acceptance.

Parent 2a763fe's full hosted native union is independently verified from all four
SHA-bound shard ZIPs, raw inventory/native logs and the separate union summary:
API26 1132=535+597; API37 1133=536+597, disjoint and complete. The eleven external
model methods remain not-run. This closes that parent's standard-suite gate,
not final-source model/phone, production or release acceptance.

The current mapped network storage still refuses full FileIdInfo (native87).
Its NTFS label and missing open-by-file-ID capability flag do not justify a
legacy-ID fallback. Existing private services, jobs, credentials, model choices
and published beta.8 are unchanged. Actual authenticated foreground transport,
optional Hermes capability, Android root/file review, NAS/Surface ownership and
every other audit/release requirement remain open; the full goal stays active.

[Adapter contract and evidence](docs/contracts/2026-11-12-worker-broker-read-adapter.md).

**Next exact action:** integrate and verify an authenticated private foreground
read transport in the actual interactive broker, with fresh actor/profile/owner
authorization and no project content in the coordinator queue. Then connect the
optional Hermes capability and Android root/file review. Resolve the separately
observed NAS identity incompatibility before enabling that storage.

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
