# Signal Grove project state

The full audit goal remains active. Continue every required row in
[the execution plan](docs/AUDIT_EXECUTION_PLAN.md) in validated, regularly pushed
chunks. Preserve main6b809e6, original signers/data, TLS/profile isolation,
explicit approvals and durable no-replay handling.

## Current bounded implementation

The feat/canonical-gateway-negotiation chunk adds a separate internal canonical
socket and authenticated actor/profile/home verification before and after
negotiation. Ready must explicitly advertise authority; runtime.describe sends
empty params exactly once. The reported profile home must match the selected
authenticated home before accepting instance/epoch identity. Sibling identity
mismatch is refused before any session read or adoption. Protocol features are
not grants. Frames, nesting, decoded duplicate keys and notifications are bounded;
unexpected requests/replies fail closed. The existing operator socket is retained.

The revised 610-input build freshly passes 665 JVM cases, including 27 handshake
methods, plus 191 policy tests and both lint variants with zero errors. The
duplicate-ready regression initially exposed a lost failure cause; the fix retains
the original cause without weakening the test. That failed build remains retained
and did not publish new APKs. A private verifier regex error was corrected against
the same successful build; no additional application rerun was inferred.

One new native method passes nine invocations: mock, actual stable and actual
prior Hermes on all three preserved emulator profiles, ten semantic checks per
invocation. Real fixtures use the actual auth/WS/authority dispatcher and a
synthetic IdP. Source-bound traces verify exactly 19 HTTP/WS/RPC records and no
session operations; 903 stable and 828 prior imported modules are rehashed.
History, credentials, keys, data, geometry and reverse bindings are preserved;
all local commands are terminal and both source freezes are closed.

Two verified F0 source aliases extend the original baseline without replacing
existing entries. Twelve selected projections and eight descriptor fields are
watched; changed variants bind exact declaring/wire/socket modules. The complete
static stable comparison passes 168 aliases. Original-key debug appd8123881 and
testb5a9ece3 are verified. No UI caller or session attachment/adoption is added yet.

## CI and release scope

The preceding Windows chunk12a97ae is pushed. Android42429507721 has a successful
verify job128575054331; device jobs128577043902/API26 and128577043817/API37 remain
live. Inspect those same handles without cancellation or resubmission.

Compatibility42429514096 remains failed on current main72d4433130823f506081cc1189a923b5aa67dbf0.
Parser, registry and sidecar jobs succeed, but the upstream canary correctly
refuses a changed saved_gateway_routes dependency. Middleware bytes match the
previously reviewed main071; the whole-source guard prevents reusing that approval
for a changed callback dependency. Separate review and exact-head gates remain
required before merge/release. Do not label that run passed.

Canonical replaye3d1b08 Android42422978712 remains independently accepted at
638 JVM/both lint0 and 1016/API26 +1017/API37 distinct native methods, with accepted
compatibility42422984535. Project-read62de33d's API37 timeout remains failed;
accepted descendants do not rewrite earlier failures.

Release207c remains held for exact physical upgrade/connection/history/project
retention and guarded publication/download verification. Latest USB inventory
showed only the preserved emulators; do not repeat the pending reconnect question.
Main and release refs stay fixed.

## Exact next action

Commit/push this validated negotiation chunk. Implement the explicit reviewed
resume/adoption operation, server-resolved lineage and attach-first replay using
the existing reducer. Verify operation-specific runtime/profile/actor/home and
revision/generation grants before workspace or worker/file controls. The full
CanonicalSessionHandle is a session.interrupt result in the reviewed wire; never
interrupt work merely to acquire it, and never invent execution state from
running=false. Review the actual workspace/CAS contracts for the required stamps.

A dashboard ticket cannot select a sibling and runtime.describe rejects extra
params. Continue a verified profile-bound acquisition path for that case rather
than borrowing the primary identity. Review the changed main callback dependency
with exact source and counterfactuals. Keep every remaining ledger row required.
