# Signal Grove project state

## Current checkpoint - private local worker read transport

The first hosted 48-case gate found a SID text-alias test defect (LA versus the
same numeric Administrator SID). Runtime security code is unchanged. Binary
ACL/SID/rights/inheritance checks plus alias/group regressions now pass 50 native
cases on both Python3.13 and 3.14.9. Only that native test file changes after source
630; all 653 other frozen executable inputs remain identical. The failed hosted
artifact is retained and the corrected hosted gate still needs its own result.

An opt-in local Windows pipe now carries the fixed read frames without a public
port, shared secret, persistent queue or content cache. A protected owner-only
DACL and local-client flag restrict access. The client verifies actual broker
PID, creation time, user and session before submission; the server checks the
client token and reverts impersonation before project reads. Actor/profile/root
authorization remains the existing broker's separate responsibility.

Message I/O has finite deadlines and no implicit retry. Failures conservatively
report whether submission may have occurred. Reply hashes are acknowledged;
closure cancels pending pipe I/O, retires leases and discards in-flight contents.
A real regression now prevents a grant committing after provider retirement.
No live service, worker enrollment, Pi, credential or model change is performed.

Source 630 passes 1004 JVM / 96 reports, 359 policies, both lint variants with no
errors (51/50 warnings), original debug signing and 32 native ABI/alignment
checks. All 48 distinct Windows cases pass on Python3.13 and the installed
worker Python3.14.9, without skips or source drift. They include actual restricted-
token denial, a separate client process, spoofed peer metadata, size limits,
timeouts/disconnect/no replay, cancellation and reported identity failures.
Two unchanged Android project-gateway cases pass after all 9 protected hashes
and package UIDs survive the exact installed upgrade.

Read-only live-broker observation verifies fresh ready/registry health, exact
process identity, current owner/nonzero session, maintained runtime declaration
and eight completed queue/receipts only. This is a point-in-time observation,
not authorization for a later restart or proof of phone integration. The new
transport still needs enrollment with actual broker policy and the private
helper, followed by Hermes capability and Android wiring. NAS full-ID support,
Surface, production/physical/model and every audit/release gate remain open.
Published beta.8 and the full active goal are unchanged.

[Transport contract and evidence](docs/contracts/2026-11-12-local-worker-read-transport.md).

**Next exact action:** integrate the pipe with the actual interactive broker
and private helper using fresh authenticated actor/profile and registry/root
policy. Recheck live receipts/process identity, review the exact install diff
and verify recovery before enabling it. Then wire the optional Hermes capability
and Android review. Resolve NAS identity support separately; keep APK release
and every original audit requirement in scope.

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
