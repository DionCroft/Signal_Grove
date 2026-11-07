# Signal Grove project state

The full audit goal remains active. Continue every required row in
[the execution plan](docs/AUDIT_EXECUTION_PLAN.md) in validated, regularly pushed
chunks. Preserve main6b809e6, original signers/data, TLS/profile isolation,
explicit approvals and durable no-replay handling.

## Current bounded implementation

The feat/canonical-replay-window chunk adds the production canonical event
reducer. It binds exact owner/cursor identity, buffers live frames during
attach-first recovery, atomically merges contiguous replay/live events once,
and holds output on conflicts, gaps, foreign scopes/epochs or exhausted bounds.
UTF-8 byte/depth limits run before serialization. Debug strings redact private
identity/content; execution finality and grants are never inferred from events.

Fresh638 JVM cases pass, including all prior608 and30 new reducer methods.
The new tests consume exact synthetic vectors from actual stable/prior Hermes
dispatchers, exercise racing/concurrent duplicates and malformed/oversized data,
and verify owner, cursor, overflow, cleanup and privacy behavior. Actual isolated
probes pass stable46 authority cases plus13 attach-first cases per revision;
497/486/436 imported module inventories were rehashed. Prior's seven expected
version subprocess attempts remain blocked;0 unexpected denials occur.

Required Android tasks,186 policy and both lint0 pass. The first successful
build is retained before a structural-budget review fix; revised acceptance
uses the second604-input freeze and30-method suite. Existing auth/project native
clients pass two methods on each preserved API26/large37/compact37 profile:
six method invocations with local mocks. These do not test a canonical Android
attachment. Original-key debug app6e6f/testb1e2 are verified. Both freezes are
closed and all local build/native commands are terminal.

This reducer has no UI caller or RPC transport yet. Separate authenticated
canonical attachment, legacy adoption/lineage and complete handle/grant binding
remain required before workspace operations. Project metadata is not file access.

## CI and release scope

Project-read62de33d is pushed. Its compatibility42419784648 is independently
accepted:186 policy/15 runtime/8 registry/sidecar11+13 and166 stable/main source
aliases. Android42419777378 has an independently accepted608/both lint0 build
artifact13592998973 and live API26/API37 jobs128548057758/128548057701.
Inspect the same handles without cancellation/resubmission. The project reducer
chunk still needs its own full exact-head gates before merge/release.

Earlier0c36 Android42412670080 is accepted at1015/1016 distinct native methods.
Its failed compatibility run and cronfac15fd API37 timeout stay failed; successful
descendant evidence does not rewrite them. Full scope remains in the ledger.

Release207c remains held for exact phone upgrade/connection/history/project
retention and guarded publication/download verification. Last USB inventory showed only preserved emulators;
do not repeat the pending reconnect question. Main and release refs stay fixed.

## Exact next action

Commit/push this reducer chunk and dispatch its exact-head gates once.
Implement the separate authenticated canonical channel with actual transport
fixtures and explicit resume/adoption effects, verified runtime/profile/actor/home
identity, exact server-resolved session/handle and attach-first replay. The
reviewed runtime.describe has empty params and dashboard tickets do not select a
profile; verify profile-bound acquisition before trusting a sibling's identity.
Never fabricate missing instance/execution-state fields from a resume snapshot.
Continue durable workspace approvals, worker/files ownership and every remaining
ledger row. Investigate moving Windows generated build/cache outputs to E as a
separate measured build-tool improvement; source stays on N.
