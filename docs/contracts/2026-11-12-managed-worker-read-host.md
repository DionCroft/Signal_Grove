# Managed worker read host and private RPC preparation

12 November 2026. Extends explicit owner enrollment and the existing local pipe.

## Lifetime and readiness

`ReadHost` starts explicitly, with one dedicated pipe thread for each of at most
eight already enrolled scopes. Construction opens no listener. Current accept
readiness is observed after the native pending-connect operation starts and is
cleared when it settles. Closure cancels pending accepts, retires leases and
joins threads with a bounded deadline. A thread-start failure removes the
unstarted thread from cleanup. A closed adapter cannot spin in a retry loop.
Transport-fault loops have bounded pacing and never reissue a submitted frame.

A held session is not recreated when policy returns. The adapter's duplicate-ID
set remains attached to the same instance across connections. Identity-revert
failure stops its dedicated thread; that thread performs only memory/native
cleanup, with endpoint-file publication left to the ordinary host thread.

Private endpoint projections have a closed schema, eight-scope/32 KiB bounds,
exact scope/instance correlation and a 10-second timestamp limit. Non-ready
states cannot publish old endpoints. The projected call submits once and retains
native peer PID/start/user/session checks and submission uncertainty. Metadata
freshness alone never authenticates a live worker. Authenticated coordinator
identity must supply the scope; a phone's claimed actor/profile cannot enroll it.

## Source and verification

Frozen source 706 contains 801 inputs, digest
`38c6006d5ad0140c30b22cd7a817fffe36fe4fa2da48922c957bd7e1646fb3a9`.
Required builds pass: 1004 JVM cases / 96 reports, debug/release lint zero errors
(51/50 warnings), debug APK and instrumentation compilation. All 387 portable
policy cases and all 65 distinct Windows cases pass without skips/source drift.
Original debug signing and all 32 native ABI/alignment checks pass. The exact
API26 upgrade preserves all nine protected files and both package UIDs; the two
unchanged project gateway contract methods pass on that installed APK.
App SHA-256: `4b24f516f421e8de0198bc24450f1557524950ad92668e84a35bcc0931bccf8e`.
Companion: `56309d5b81768249ef7ffbab97edad5f828e4d6155a8b766cd6b5c49b695b352`.
This debug validation build is not a newly published version.

New native coverage includes real managed prepare/list/preview, duplicate frames
across connections, idle revocation without revival, accept cancellation, closed
endpoints, current projections and spoofed process-start refusal before submission.
Initial test assertions incorrectly assumed immediate post-ACK readiness and
expected CHANGED for an existing DENIED peer-spoof contract; those failures are
retained. Correcting the assertions does not relax native authorization.

The preceding [6d9863c hosted run](https://github.com/DionCroft/Signal_Grove/actions/runs/43157206161)
independently passes all 58 older native cases. Artifact 13916920131, ZIP SHA-256
`0f980b91e46ff9a7aa524a5d01ea0176101ace1a79f4a33090a4c3099107f634`,
raw positive-case inventory and all 11 committed source hashes are verified.
That historical hosted proof does not certify this newer managed-host code.

## Reviewed private candidate

The installed worker has newer runtime fixes than its maintained older template.
The staged candidate preserves all 19 existing execution/routing functions and
changes only fixed RPC handling; daemon registry, step and recovery are unchanged.
A controlled maintenance marker holds new admissions and reports execution
unready without changing the health schema, so an idle source cutover can avoid
cancelling queued work. Its synthetic matrix never calls a real execution queue.

Private source/control templates and operator policy stay outside public Git.
The runtime's seven generic source modules are packaged as canonical LF, checked
against the reviewed ASTs and sealed with byte hashes. Component digest:
`4b0fd16222dac670ecf9abb85383936102466e17fe54952ad15377b6276b2e48`.
Its future source pin must match committed Git blobs before installation.

The canonical package passes seven separate-process private RPC requests using
the actual interactive owner, locked canonical registry and existing worker
validator on the already registered local validation root. It reads 302 existing
text bytes, refuses a spoofed scope before submission, publishes no endpoint on
revocation and stays held when old policy returns. All eight live queue receipts
and the deployed source remain unchanged. An isolated-candidate dependency import
failure and a stopped diagnostic hash edit remain retained; no uncertain project
write is replayed. No execution queue is created by the private candidate.

Read-only Pi observation verifies its Tailscale self identity and default owner
process/home fingerprint. This supports a privately staged intended scope; it
still needs actual native coordinator-request acceptance. Identifiers are not
credentials, and no key, service or model is changed by that observation.

Before any live cutover, an affected-component CurrentUser DPAPI archive is
created and round-trip verified. The current snapshot covers exact worker/daemon
source, a consistent eight-terminal-receipt queue image and status/registry
metadata. Encrypted size 12526 bytes, SHA-256
`56f68fd660cff2fd7bb487902a6be469abc1005d62c87800253f8c41d219bf98`.
No plaintext ZIP is written or uploaded. This recovery is Windows-account-bound;
existing verified age Hermes/fleet recovery has its separate retained scope.
Production source, policy and the scheduled task remain untouched at this checkpoint.

## Remaining acceptance

Recheck source/ACL/process identity and idle receipts at the actual cutover, hold
new intake, drain old helper invocations, preserve the exact stop identity and
verify the new daemon/read RPC before releasing the hold. Keep unverified outcomes
for inspection instead of replaying work. Native coordinator authentication and
capability, Android file review, NAS identity support, deliberate session renewal,
physical/model/recovery tests and every original audit/release gate remain open.
Owner-local derived graph indexing/context-pack identity is still unresolved.
Published beta.8 is unchanged. Final delivery still requires a new original-key
production APK on GitHub Releases with verified download and a direct phone link.
