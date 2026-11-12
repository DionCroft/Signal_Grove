# Signal Grove project state

Current milestone: explicit worker read enrollment (foreground proof).
Current objective: connect reviewed workspace reads to actual owner policy.
Assurance state: component verified; production/phone/release acceptance open.
Last checkpoint: 12 November 2026, worker-read-enrollment.
Last handover: HANDOVER.md.

## Current checkpoint

Explicit operator scope/project allowlists now reuse the worker's existing
project validator, fresh locked registry bytes and actual Windows token/WTS
identity. Policy, registry or owner change permanently retires the session and
leases. No caller-selected root or claimed interactive identity is trusted.
The configured-source reader holds ancestor/source handles; it does not loosen
mandatory full 128-bit workspace IDs or enable NAS workspace reads.

The actual foreground proof passes against the already registered local
validation project: pipe prepare/list/preview, seven entries, 302 existing text
bytes, 22 fresh registry reads and six existing worker validations. The paused
and foreign-worker projects remain denied. This changes no production daemon,
project contents, coordinator, credentials or models.

Frozen source 677 passes 1004 JVM / 96 reports, 373 policies and 58 Windows native
cases on the installed worker Python3.14.9, without skips or drift. Both lint
variants have no errors (51/50 warnings), original debug signing and all 32
native ABI/alignment checks pass. The exact API26 upgrade retains all nine
protected hashes and both package UIDs; two project contract cases pass.
Failed newline-fixture, pre-install inventory and UNC-path attempts
are retained. The earlier corrected 50-case SID gate independently passes on
hosted main run 43150240082; its artifact/source hashes are verified. Hosted
acceptance of this new enrollment chunk remains separate.

Missing lifecycle metadata was initialized non-destructively through the owner
ContextGraph MCP. Derived state/config is ignored. Indexing timed out and the
context pack refuses its incomplete graph identity; no freshness/graph acceptance
is claimed. The first zero-node/schema-only partial index was hash-preserved
before recovery. Existing source files remain authoritative and bounded.

[Enrollment contract and evidence](docs/contracts/2026-11-12-worker-read-enrollment.md).

**Next exact action:** review the private host-daemon/helper enrollment diff,
verify recovery and fresh idle receipts, then enable only the explicitly scoped
read endpoint on its actual owner. Wire the optional authenticated coordinator
capability and Android review after that proof. Reconcile the owner-local derived
graph separately. Keep NAS support, physical/model/live interoperability and all
original audit and downloadable signed APK requirements open.

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
