# Signal Grove project state

Last checkpoint:12 November 2026, natural-chat follow-up. The full audit/on-device
AI goal is active. Published preview is beta.8/code111; beta.9/code114 remains
unpublished. Normal phone app is code112; debug/test code114 is installed.

## Current evidence and defect

- Checked source0bb325e builds original-key production/debug/test APKs:1,011 JVM
  cases/97 reports, lint0 errors (51/50 warnings), all four ABIs,64-bit16-KiB and
  uncompressed/aligned DEX. Production71,052,646 bytes; debug92,609,254;
  test7,909,383. These artifacts must keep their original source identity.
- Debug114 upgrade retains all20 protected metadata files, both original model
  hashes and all package UIDs. Encrypted snapshots round-trip. The normal APK is
  unchanged; production-private file byte retention is not claimed.
- Two isolated actual offline native cases pass: six reviewed helper topics and
  exact Chinese; exact generation/cancellation/recovery/overflow/unload/reload.
  The initial four-case batch remains failed. Garden output still exposes a
  reasoning marker, including in a fresh foreground case.
- Controlled same-runtime/system/context diagnosis reproduces the forced-budget
  marker and gets a clean natural answer. Source now reserves forced reasoning
  closure for constrained helper selections.51 native checks pass. App token
  limits stay unchanged; the reload test retains48 and checks exact96 completion
  after a known limit. Fresh APK acceptance is pending.
- Register the new garden method explicitly: five external classes/twelve methods,
  every original method and prerequisite retained.422 policies pass in the
  corrected source copy and authoritative working tree. Fresh public CI is required.
  The b297 compatibility workflow fails its parser inventory guard before the
  upstream jobs; it is not a new40/101 upstream review.

[Current physical diagnosis/source scope](docs/contracts/2026-11-12-natural-chat-followup.md) ·
[Checked build/garden inventory](docs/contracts/2026-11-12-external-garden-gate.md).

## Original audit remains required

Stable3b42157/inspected-main1773ba3 retain40/101 used-handler review findings.
Native auth87, authority46/13 and owner-room20 component scopes remain separate
from live acceptance; actual SDK shutdown still emits a cleanup warning.
[Compatibility](docs/COMPATIBILITY.md) · [Owner runtime](docs/contracts/2026-11-12-native-owner-room-reads.md).

The Pi/Windows read path remains intermittent; preserve all eight completed
receipts and unknown work. Do not restart its daemon or replay requests.
Project metadata does not verify workers, roots, repositories, files or NAS.
Auth renewal/approvals/images/reconnect/integrations, advanced Kanban/jobs/rooms/
configuration, notifications/multiple connections/background/audio/accessibility/
performance, remaining model-family and community-release gates stay open.
No original unresolved audit row is removed by this concise state.
[Full audit](docs/AUDIT_EXECUTION_PLAN.md) · [On-device plan](docs/ON_DEVICE_AI_PLAN.md).

## One exact next action

Validate and commit the corrected authoritative source for a clean signed build.
Then verify the fresh APK's garden/helper/Chinese/cancellation/recovery/family
behavior, retained-data production upgrade and exact passing public release CI.
No APK/tag/release may be published while a required gate fails.

Preserve original phone models/history, TLS/authentication/profile/approval
boundaries and no-replay behavior. One approved normal phone restart already
occurred; do not reboot it again. Awake setting15 was observed unchanged. The
owner may restore normal networking after completed tests; don't infer radio
state from a reply. Keep all workstation scratch/builds/private evidence on E.

[Validation](docs/VALIDATION.md) · [Evidence](EVIDENCE.md) · [Handover](HANDOVER.md).
