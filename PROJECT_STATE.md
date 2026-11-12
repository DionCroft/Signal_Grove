# Signal Grove project state

Current milestone: managed worker read host and private RPC cutover preparation.
Current objective: integrate verified owner reads with the existing worker daemon.
Assurance state: native/private-candidate proofs pass; production/phone/release open.
Last checkpoint: 12 November 2026, managed-worker-read-host.
Last handover: HANDOVER.md.

## Current checkpoint

An explicit lifetime owner now runs one dedicated pipe thread per enrolled scope.
It publishes only currently accepting listeners, retires held sessions without
replacement, bounds transport-fault loops and cancels/joins on closure. Native
identity-revert failures never invoke a file/receipt writer from that thread.
Private projections have closed schemas, exact scope/instance correlation and a
10-second freshness limit; native process identity is rechecked before submission.

The new code passes 65 distinct Windows native cases without skips or source
drift. Frozen source 706 also passes all 387 portable policies, 1004 JVM cases / 96
reports, both lint variants (zero errors, 51/50 warnings), original debug signing
and 32 native ABI/alignment checks. The exact API26 upgrade retains all nine
protected hashes and both package UIDs; both project contract cases pass. Earlier readiness-assertion and error-code expectation
failures are retained; existing peer-spoof denial behavior remains unchanged.

A private fixed-RPC candidate passes seven separate-process requests using the
actual interactive owner, existing worker validator and already registered local
validation root. It reads 302 existing text bytes, rejects scope spoofing before
submission, holds on revocation and never revives on restoring old policy. The
live worker/daemon source and all eight durable queue receipts remain unchanged.
An import-only staging failure and a stopped fixture-hash edit are retained.

The maintained older worker template differs from deployed native-runtime fixes.
The candidate preserves every execution/routing function and changes only fixed
RPC handling and daemon main wiring. A tested maintenance hold blocks new stages
without enqueueing, preserving the health schema. Private policy is staged from
observed Tailscale self identity and default owner/profile metadata; authenticated
coordinator request binding still needs live acceptance. Canonical-LF runtime
packaging permits exact future Git-blob reconstruction. Its seven-process proof
passes too. The affected worker source/consistent queue recovery is encrypted
and round-trip verified locally, with no plaintext ZIP or upload. Nothing is
installed; native coordinator binding still needs separate acceptance.

The preceding 6d9863c main gate is independently verified: all 58 hosted native
cases, exact Git/source inventory and artifact digest pass. Its raw evidence is
retained; it does not certify this newer code. Source 677's 1004 JVM/373 policy,
lint/signing/32 native-library and nine-file/two-project-case retained-data checks
remain historical evidence, not the final current-source build.

Owner-local derived graph indexing/context-pack identity remains unresolved;
no freshness claim or coordinator-local substitute is made. All authoritative
files stay on their verified owner, with private staging/evidence on E.

[Managed-host contract and evidence](docs/contracts/2026-11-12-managed-worker-read-host.md).

**Next exact action:** recheck encrypted recovery, actual idle work, source/ACL
and process identity, then deploy the reviewed private helper with a controlled
hold and prove its fixed RPC on the actual owner. Wire the optional native-auth
coordinator capability and Android review next. NAS, physical/model, compatibility
and every original audit/downloadable signed APK requirement remain open.

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
