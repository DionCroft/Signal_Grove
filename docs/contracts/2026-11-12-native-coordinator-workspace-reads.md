# Optional native coordinator worker-read contract - 12 November 2026

The coordinator source adds versioned describe/read methods to the existing
authenticated Hermes authority socket. It uses the actual host-owner subject,
verified operator gate/capability, authority instance, selected served profile
and live transport registration. The ordinary API-key subject is never promoted
to the machine owner. Scope is derived internally using current explicit grants;
phone-supplied actor/profile/scope/root/command claims cannot enroll anything.

Only prepare/list/text-preview are forwarded to an enrolled fixed worker route.
The separate one-shot SSH transport handles the escaped broker reply above the
legacy generic 64 KiB limit with 16 KiB input, 512 KiB output, 8 KiB stderr and
at most 30 seconds. It refuses execution actions, caps four in-flight requests,
has no implicit retries and preserves conservative request_submitted state.
Actor/profile/transport/authority or current policy-revision changes drop results.
UNSUPPORTED remains explicit for the unresolved NAS full-ID behavior. Contents
are not persisted or inserted into a model context.

## Verification

- Full portable suite: **407 passes**, including 20 new coordinator/transport
  cases. Real child processes test large escaped Unicode replies, stdout/stderr
  limits, timeout, cancellation, failed spawn and invalid/nonzero responses.
- Reproducible actual Hermes **v0.23.1 / 3b42157** native-dispatch probe:
  **12 passes** through actual AuthorityConnection, SessionAuthority and strict
  native contracts, with isolated SQLite and a synthetic bounded worker route.
  Owner describe/prepare, absent policy, nonoperator/API/foreign native subject,
  wrong instance, actual sibling-profile routing, scope/extra claims, closed
  transport and retirement during a read are covered. Admission, worker,
  conversation and message tables remain empty; audited external network and
  execution are denied. This uses trusted constructor identities, not HTTP sign-in.
- The guarded source adapter targets controls SHA-256
  `44624fbc34c86529db0963eb9dd2183b56b2c8034650053c6a6dc8c3556dc352`;
  its in-memory patched SHA-256 is
  `3a31ac06b3d6c9f9f1bfcc8f4331b83a2581e8d6811dae0156d25a1e843ef5f9`.
  Different source requires fresh review; no baseline waiver is added.
- Frozen source 706's existing non-Markdown inputs are hash-verified unchanged.
  Its Android build/1004 JVM/lint/signing/native-library and retained-data API26
  evidence still describes those unchanged inputs. No new Android build or
  physical-device result is claimed for this Python-only source extension.

Run the ordinary script tests and, using the reviewed checkout's dependency
environment, `scripts/probe_workspace_coordinator.py --source-root
<immutable-checkout> --work-root <fresh-approved-scratch>`. The probe checks a
clean exact commit and writes its report only under the new scratch root.
Raw private evidence is retained on the execution owner as coordinator-policy745
and native-coordinator745/report.json. Earlier fixture setup failures remain:
738 denied Windows asyncio's own initial socketpair; 739 completed assertions but
refused its out-of-root evidence write. Both fences were preserved, with the
internal loop initialized before the audit and the report kept inside the fixture.

## Deployment and remaining work

This source is **not deployed to the Pi**. The installer previews two exact native
anchors by default; it stages a complete module set only with explicit --apply.
It creates no operator policy or credentials and restarts no gateway. Missing
operator policy returns UNAVAILABLE with no worker call or ready capability.
Use the existing encrypted recovery and idle/staged runtime procedure for any
actual deployment. The separately maintained Windows source package is committed
locally in its private source tree; unrelated private edits and its remote remain
untouched. The existing live Windows read daemon and all eight receipts are
unchanged by this checkpoint.

Next: bind reviewed Pi authority/profile policy to the existing strict-host-key
worker SSH route, verify the actual native HTTP/WS owner sign-in and default/other
profile behavior, then wire Android project/root/file review. No public port,
model/default, credential transfer, worker registration or execution replay is
authorized by the read capability itself.

NAS identity, derived graph repair, native renewal/recovery/approvals, worker
adoption/dispatch, automation/integrations, full upstream semantic review,
phone/model/upgrade, advisories/provenance and every original audit row remain.
Published APK remains v1.1.0-beta.8. The active goal still requires the final
original-key versioned production APK and a verified public GitHub download link.
Source pushes do not fulfill APK delivery.
