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
