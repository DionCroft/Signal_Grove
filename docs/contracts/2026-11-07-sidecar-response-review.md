# Sidecar responses — socket ownership and replay review

The [c3c compatibility run](https://github.com/DionCroft/Signal_Grove/actions/runs/42349604850)
passed167 parser/15 runtime/8 pinned-registry cases. Stable v0.23.0 remained clean,
and all six migration variants applied. Main advanced to
`aa389eba3df3c5839cf00a8d5941a83bbe3435cb`, changing gateway_ws:_dispatch_request.
That canary remains failed. Its three downloaded ZIPs were digest-verified before
inspection: main `c5b3d3b4b366754b1f5628a3452d467611e0dbd381c217285944de9c2af93508`,
stable `e1cdbc116cd0af1664884080a6f24143ca4714773d0f5da4a36a224d846c1ed4`,
pinned registry `f0831337c353a1a23058864861a1c4d97ad7c438b8559adbe1180364c8f3d08c`.

## Reviewed primary source

The complete before/after [ws.py](https://github.com/NousResearch/hermes-agent/blob/aa389eba3df3c5839cf00a8d5941a83bbe3435cb/tui_gateway/ws.py)
and [server_requests.py](https://github.com/NousResearch/hermes-agent/blob/aa389eba3df3c5839cf00a8d5941a83bbe3435cb/tui_gateway/server_requests.py)
bytes were checked against Git blob identities; current ws SHA-256 also matches
the digest-verified CI report. Complete small diffs in
[compute_host_bridge.py](https://github.com/NousResearch/hermes-agent/blob/aa389eba3df3c5839cf00a8d5941a83bbe3435cb/tui_gateway/compute_host_bridge.py)
and [request contracts](https://github.com/NousResearch/hermes-agent/blob/aa389eba3df3c5839cf00a8d5941a83bbe3435cb/tui_gateway/contracts/server_requests.py)
were reviewed as transitive context. They were not executed as a gateway.

With an authority connection, ws now first tries resolve_pinned_response.
Only a response-shaped frame with a string ID, an open locally pinned request
and the identical original transport can resolve there. Foreign, unpinned,
expired and ordinary request frames keep their existing dispatch owner. Pinned
error replies, including NOT_SHOWN, refuse immediately. The normal legacy path,
authority delegation and local-Desktop context remain distinct.

The registry writes pinned frames only to their originating socket. preview.act
with action vault is live-only: the immediate wire frame is built first, its
script-bearing params are then discarded from pending state, and reconnect
snapshots exclude it. Ordinary request snapshots remain available. Timeout/cancel
removes the original request and emits cancellation metadata; a late answer does
not replay it. The compute-host bridge also omits live-only script params and
scrubs preview reply values in the owning profile's redaction scope before
forwarding. That redaction/provider integration is source-reviewed context, not
a live vault-account, real profile or desktop-browser acceptance claim.

## Source-bound watch

The accepted dispatch variant requires both complete AA module hashes:

| Module | Complete SHA-256 |
| --- | --- |
| gateway_ws | `f1f3750accad549937a0d34d3c4b0eafdffbb04fc3ea46022415bfbe5f361e2a` |
| gateway_server_requests | `47f1bac23ec50e36ebb09f9e7d9365fe703e11aca88f658c37d7a29413df6473` |

The registry source already exists at the original F0 pin. Its complete blob
`a7ee8e80fe6ac735d03d1f5dad91ee8e8da5681e` is identical at F0, stable and d4.
The original contract is extended with that verified source SHA-256
`10e64a801daf59efd1e60d085c548649ca998901ca1eb7c3a49cfd61f7e03f20`
and its42 extracted fingerprints. Every previous field/fingerprint and earlier
review entry is preserved. This is not a source introduction or an absent-handler
waiver. Future registry byte changes invalidate the AA dispatch review even if
the dispatcher AST remains unchanged.

## Regression scope and pending checks

The reusable [bounded probe](../../scripts/compatibility_probe_sidecar_responses.py)
verifies both complete public blobs before creating its fixture and refuses -O.
It loads the actual registry and only two extracted ws functions, with synthetic
contract validation, transport, authority, context and thread runner. Eleven
cases pass: same-socket settlement; pinned error refusal; unpinned/malformed
frames; live-only vault payload; ordinary snapshots; unavailable ordinary clients;
explicit pinned callback; timeout/late reply; own/foreign authority routing; and
legacy routing. No parent startup, real scheduling/thread propagation, Pydantic
schema, HTTP/authentication, vault/account, Pi deployment or owner-phone behavior
is certified. Python audit hooks are not an OS sandbox.

Three new policy cases protect the exact two-source vector, unchanged-dispatch
AST with changed registry bytes, and wrong-source/optimized refusal before
fixture creation.170 parser and15 runtime tests pass. The initial170-test run
retains one failed nine-source inventory assertion; the reviewed inventory is
now ten, with its new exact path checked and all old assertions retained.
Saved stable/d4/AA reports were augmented only with their separately verified
registry files and compare cleanly. Four missing/changed-source counterfactuals
reopen exactly the dispatcher finding. Fresh hosted checks remain required.

The native management socket never advertises server_requests or subscribes to
transcripts, and exposes no vault/preview method. Four JVM WebSocket boundary
regressions now exercise matching-ID server requests, foreign response IDs,
ordinary replies and unsolicited approval events while asserting one original
profile-scoped request and no credential/capability/approval replies. The strict
four required tasks passed:552 JVM cases, zero failures/errors/skips, including
all four new boundary cases; debug lint has zero errors.90 tasks completed,
32 executed/58 up-to-date. Private QA111 defaults are not release artifacts.
No production Android behavior changed.

The original-key QA app is byte-identical to the retained ef5 app:
`a55c47207787ab3b4b3b89fdbd201656fcdaeb96e254c364357ae170c217a236`.
The rebuilt companion is
`00754d9ddcd19909d9ab3294af1e47a1e1db538e85edd6963150373079b26fc6`.
Its raw container differs from the prior64eef companion, while every ZIP payload
entry and the original CAB signer match. The current pair was separately tested:
27 unique history/image/title/setup cases passed on each owned API26/API37,
without uninstall/data clear/display changes or owner-phone targeting. An earlier
six-case diagnostic used the retained old pair and also passed per API; it is a
separate cohort, not extra unique coverage. All564 source inputs stayed frozen.
The retained raw-source archive SHA-256 is
`7e6c2cae708fdd96915c5f63fcdf210c5518b84dc4fffad5671a4c012185c210`.

The prior ef5 full Android cohort is terminal: API37 passed937 unique cases with
no failures/skips, artifact ZIP digest
`b156e457f163ac06c3eca676f0f2b43b37da96decc642800f344b3f4818e3f08`.
API26 stopped after912 positives/913 starts out of936 with no failed assertion or
terminal verdict. Its log ends waiting for device;24 cases remain unverified,
including the running bilingual setup-guide case. Its ZIP digest is
`82a400c8a8fa30bedaea9d0df6609817c6553673a28316e820d2e35c00ab3564`.
Both archives were independently checked. The exact connection-loss cause is
unproven: the runner omitted the original command return code and pre-cleanup
device state. A fresh local six-case pass does not certify long-suite stability.
Retain this overall failed cohort and add bounded diagnostics before another
full native cohort; do not simply redispatch unchanged failing inputs. New exact
source hosted compatibility/Android gates and publication remain required.

The compatibility workflow adds an independent finite pinned-sidecar job to keep
the11-case probe recurring. It fetches only two exact public files, never runs a
gateway and retains a separate report. This does not add native vault filling,
shared-chat write authority, generic automatic approvals or a Pi update. The
full audit and final signed/publication checks remain open.
