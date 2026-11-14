# Expanded Signal Grove goal

Complete the existing Signal Grove/Hermes audit in tested, regularly committed and pushed chunks, retaining every unresolved requirement in docs/AUDIT_EXECUTION_PLAN.md and preserving current data, package/signing identity, TLS/authentication, profile isolation, approvals and exact no-replay recovery.

Priority extension authorized 10 November 2026: first test LiquidAI LFM3-1.7B QAD Q4_0 on the owner's actual Android phone, verifying source/hash/native compatibility, offline generation, memory, latency/throughput, cancellation/lifecycle and bounded thermal/battery observations. Then implement optional native offline chat with separate local/server identity and history, explicit opt-in fallback for new unsent messages only, and reviewed handoff back to Hermes. Implement user-controlled verified model downloads/import, compatible architecture/chat-template checks, licence/storage/context and load/unload/delete management. Implement an offline onboarding/troubleshooting helper grounded in versioned setup documentation and redacted observed diagnostics; keep credentials out of model context and all actions behind existing review/approval controls.

Validate applicable unit/policy/native checks plus representative emulator and physical-device behavior. Preserve failed/unknown outcomes and original phone connection/history data. Update requirements, state and validation evidence and commit/push coherent tested chunks. Keep source in N:/Projects/Project_Agentic/hermes_mobile_app and all tools, model downloads, caches, scratch, builds and private evidence on E:. The full original audit, real Hermes/project/worker/automation/integration, security and signed-release acceptance remains required. Local chat does not close remote audit requirements. Do not add silent retries, credential transfer, public service exposure, server model changes or automatic cloud fallback. Continue until the full agreed scope is verified or genuinely blocked.

APK delivery explicitly requested 12 November 2026: after the applicable release checks pass, publish the exact-source, original-production-key signed APK as a public versioned GitHub Release asset so the owner can download and update directly on the phone. Preserve the production package/signing identity, increase the Android version code, and retain the updater-compatible `hermes-agent-mobile-<version>.apk` filename convention. Include release notes, SHA256SUMS, the public signing certificate and reviewed SBOM/provenance; keep model weights and credentials out of the APK/release. Verify the public download matches the reviewed APK hash and confirm an in-place upgrade retains the saved Hermes connection/history and applicable local data. Return the direct APK download link. A source push or a temporary CI artifact alone does not fulfil this delivery requirement. Existing publication authorization covers this release; do not request it again.

## Explicit acceptance priorities resumed 13 November 2026

The owner resumed the existing goal and explicitly retained these four tasks.
They add concrete acceptance detail to the full objective above; they replace
none of the original audit, local-AI, security or delivery requirements.

1. **Investigate the API26 Demo UI failure.** Reconcile the retained source1180
   `Milestone03Test.demoClarificationChoiceIsReviewedBeforeSubmission` failure
   with its original raw log/state and later passing runs. Reproduce the
   relevant transition, fix its actual cause and preserve explicit review/
   submission assertions. Verify focused native behavior and complete standard
   coverage for the final source. Do not waive, hide or blindly retry failures.
2. **Complete upstream Hermes compatibility and shutdown review.** Review exact
   used-handler/dependency changes on supported pinned stable/prior and inspected
   main sources. Resolve and validate the observed SDK shutdown-cleanup ordering
   issue through supported lifecycle behavior. Keep immutable sources and every
   earlier warning/failure; no baseline reset or callback suppression to pass.
3. **Finish real project/workspace/worker, automation and integration acceptance.**
   Verify explicit chat/workspace adoption, owner/worker identity, registered
   roots, repository/folder/file/NAS access and reviewed dispatch boundaries on
   the correct host. Complete the remaining Kanban, jobs, rooms/grants/delivery,
   plugin/channel/configuration and recovery checks in the original ledger.
   Actual authenticated effects/outcomes and exact receipts are required;
   metadata, an ACK or a live gateway alone is not execution proof.
4. **Complete final production upgrade and release checks.** Beta.9's six public
   assets and anonymous download hashes already verify; publication does not
   close final acceptance. Verify the exact original-key production APK's
   in-place phone upgrade, UID/signing/schema retention, saved Hermes connection,
   current chats/projects and applicable local models/history. Finish live and
   offline, lifecycle/device and exact-source CI/compatibility acceptance, then
   reconcile release assets, notes, README and support evidence. Any subsequent
   published update needs the correct higher version code and matching signer.

Continue in tested chunks with regular commits/pushes to main. Preserve user data,
unknown operations and every original audit requirement. Source stays in the
authoritative root; tools, temporary material and private evidence stay on E:.
The approved HTTP auth source remains applied without Pi deployment authorization.
The Windows read-client correction and its recovery/receipt evidence are separate.

The dormant operator binding now passes 493 portable cases, including eight new
policy behavior cases. It accepts only explicit owner/profile/device/project
rows observed through a protected reader and fixed operator routes. Native file
generation and contents both enter the revision; changed/revoked in-flight reads
drop contents and preserve submitted state. No policy, grant or service is created.
Windows refuses POSIX protection; eight real Linux filesystem cases and their
strict source-bound gate are prepared. Initial Linux8/Windows72/protocol119
functional gates pass; Windows source bytes are verified as the exact CRLF form.
Python checkouts are now pinned to LF, with a new exact-byte native gate pending.

Next: Run the new Linux policy lane on this exact feature source and reconcile the
raw report/log and every source hash before promoting it to main. After it passes,
prepare the exact maintained-runtime bundle and private policy for the already
reviewed worker enrollment, plus current encrypted recovery and idle checks.
The previous authentication approval covers source only: do not install the Pi
operator, create/expand live grants or restart services under that approval.
Real native HTTPS/Android/worker content, independent chat/execution adoption,
NAS, original automation/integration and final phone/release acceptance remain.


Forty stable findings/main semantics, pristine shutdown, original API26 causes,
actual integrations/automation, doctor/graph, external-model/final-source/device
and signed production-upgrade/release acceptance remain open. Source 4444's
1,141/1,142 raw standard positives and beta.9 publication do not replace those
gates. See [the exact source checkpoint](contracts/2026-11-14-protected-operator-policy.md).
