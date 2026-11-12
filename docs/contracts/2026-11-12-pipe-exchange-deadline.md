# A late pipe client receives a bounded exchange budget

The explicitly approved in-memory Pi trial encounters intermittent UNAVAILABLE
reads. Its first attempt stops on an incorrect fixture filename after successful
describe/prepare/list. Later terminal read failures are retained; the last trace
reaches ready status, then a submitted prepare returns UNAVAILABLE. All eight
completed job receipts/live source hashes remain unchanged and the worker is
ready. No complete Pi acceptance or persistent deployment is claimed.

Source inspection and a real Windows regression reproduce a deadline defect:
`serve_one` starts its three-second budget before waiting for a client. A request
arriving after two seconds has less than one second to finish. A real late-client
case with a bounded 1.25-second adapter delay fails against the original source.
This is a contributing defect; deployment and a successful Pi trial are still
required to establish that it resolves the observed setup failure completely.

The fix keeps idle accept bounded, then starts one separate exchange deadline.
Request, identity check, adapter, reply and acknowledgement share it. There is
no per-I/O extension, retry/replay, token relaxation, cached reply or new listener.
The maximum is one accept plus one exchange budget; close still interrupts I/O.
Expired adapter work cannot emit a reply.

- **417 portable checks pass**, including deterministic late-client, expired
  adapter, bounded idle accept and missing-ack/no-replay regressions.
- **66 distinct real Windows cases pass** on Python 3.13.2 and the actual worker
  Python 3.14.9, with zero failures/errors/skips and source unchanged throughout.
  The new case uses the real owner-only pipe, identity checks and provider.
- Valid failing-before/fixed-after evidence is retained. An initial portable
  fixture missing its endpoint is corrected before the valid reproduction.
- Android/build inputs are unchanged; prior complete Android standard-gate
  evidence remains scoped evidence. This Python fix is not a new APK.

Reproduce using ordinary scripts/tests discovery and
`scripts/run_workspace_provider_tests.py --output <approved-scratch-report>` on
real Windows. Private evidence: Pi failures788/789/792, worker791, red795,
policy796 and native796/797. The installed runtime is unchanged. Next: review
the immutable runtime manifest, encrypted recovery, idle state and exact staged
cutover, then a fresh approved Pi trial. Actual native HTTP/WS owner/profile
binding, Android workspace browsing, NAS and all audit/release gates remain.

## Exact optional Windows component deployment and remaining failure

The reviewed immutable source fix is now deployed on the existing Windows worker
through an idle, journaled component cutover. Before/after scoped DPAPI recovery
verifies source, unchanged scheduled-task definition and a consistent eight-row
receipt snapshot. Policy, worker/daemon source, all eight completed receipts and
task definition remain unchanged. Old runtime/recovery are retained. No Pi file,
gateway, credential, model, default or job changes occur. Post-change path/ACL
checks pass. Read-only doctor retains the existing optional key/tool findings;
ContextGraph retains the different-project/unindexed findings without repair.

The fresh in-memory Pi trial reaches ready status and completes prepare, then
list returns terminal UNAVAILABLE with requestSubmitted:false. No complete Pi
coordinator acceptance is claimed and the failed request is not replayed. A later
independent projection/status observation is fresh and ready, with no maintenance
or stop marker. A transient readiness snapshot is only a hypothesis, not a cause
established by that trace. Further native diagnosis is required.

Hosted source c55d070 Windows run 43194783802 passes all 66 distinct native cases.
Its exact artifact digest, complete raw case inventory and all 13 native source
hashes are independently verified (including recorded CRLF normalization).
Hosted Android run 43194783773 also completes successfully; full raw acceptance
still uses the independently reconciled earlier unchanged Android source. Hermes
compatibility run 43194783793 remains failing in upstream source inspection;
parser, pinned registry and sidecar jobs pass. Fresh finding counts are not yet
accepted, and saved stable40/main97 findings remain open.

Private cutover814/trial815, doctor/graph816, recovery810/817, ACL818 and later
status833 record the exact scope. Verified hosted artifact13932711219 ZIP SHA-256:
`7beff017a5d39169e0b3bd58d7a7cf82a2de3aa4fb2ff90ee21df4a62352a5ed`.

## Subsequent bounded diagnosis

A fresh local status is ready, but a new prepare loses its reply with Win32233
and requestSubmitted:true. Existing sources/eight receipts remain. Three protected
registry reads take about24ms; a disposable real-policy provider prepares/lists in
about194/174ms, and a deliberately preconnected real native client passes. Those
isolated checks do not identify the live daemon's intermittent cause. No pending
read is replayed, daemon restarted or timeout/identity control relaxed. Private
847-850 and the maintained local-only deployment note retain this scope.
