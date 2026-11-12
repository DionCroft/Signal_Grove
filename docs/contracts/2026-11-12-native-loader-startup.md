# Real loader/start-entry checks and complete hosted gate - 12 November 2026

## Real source, explicit adapters

The final `scripts/compatibility_probe_native_auth_history.py` passes 87 distinct
cases against clean stable Hermes 3b421578588243f920886e6fe79a6faa87ea026e. All 77
earlier cases remain. The source fence adds seven PM modules to the existing 48
critical Git blobs, preserving every earlier key/value and revision definition.
The final report hashes 949 actually loaded project modules and records zero
unexpected audit denial. No baseline variant, handler hash or absence waiver is
admitted. Probe SHA-256: `cf2029ef201cbc6a0cb08758cb0081f16337e548fdfcdcb68899f15eb7d1d621`.

Five added cases execute the actual `preload_session_socket`,
`await_session_socket_preload` and `session_socket_handler` helpers:

- A cold preload imports off the event-loop thread.
- Its readiness timeout leaves the shared import task alive and uncancelled.
- Concurrent attach waits on the real Python import lock in a worker while the
  event loop keeps running, then receives the exact handler identity.
- An already loaded module takes the fast path without another module execution.
- A failed preload is consumed; a later explicit handler fetch can load it.

A scoped import finder supplies only a synthetic `tui_gateway.ws` module body,
with bounded entered/release events. It retains Python's real import machinery,
lock and asyncio worker scheduling. The finder/module/package attribute are
removed afterward. The real downstream socket handler is never executed and its
global import side effects are not certified. These are loader-boundary cases,
not an actual cold gateway/interactive socket acceptance result.

Five further cases execute the real `web_server.start_server` wrapper and real
`pm.extras` suppression/`ensure_import` behavior:

- Arguments and return identity are preserved; suppression reaches worker threads.
- A runtime-body exception propagates and restores the process-wide scope.
- Nested startup restores the outer counter before restoring the default.
- Simulated inherited TTYs cannot reach an input adapter during startup; the
  missing-extra path stops at an explicit synthetic installer refusal.
- Outside that scope, the same TTY contrast reaches the input witness and never
  proceeds to installation.

The runtime body, installer and stdin read are stopping adapters. This proves
wrapper/PM behavior while starting zero listeners, performing zero package
operations and reading zero actual stdin. It does not run `_start_server_runtime`
through binding, lifecycle, discovery or real backend startup. Prior ASGI, IdP,
peer, authority and downstream adapter limits stay. Python audit hooks remain
defense in depth, not an OS security sandbox. Owner credentials are absent.

Three exact final-source controls reject before fixture creation or upstream
execution: a changed newly guarded `pm/extras.py`, an unknown revision, and `-O`
disabled assertions. Portable 422/runtime 15/checksum policies pass. The earlier
87-case pilot and final repository run are distinct; no failed outcome is erased.

## Reproduction

Use the [existing isolated Windows procedure](2026-11-07-native-auth-history-matrix.md)
and an existing compatible interpreter/dependency site. Pin the exact clean source
above and select `--revision stable-3b42157`; keep `-I -S -B` and omit `-O`.
Use a fresh E output directory for each deliberate trial. No dependency install,
live Hermes configuration, credential transfer or model/default change is part
of this probe. Inspect its result and private traceback before any retry.

## Complete standard Android gate

[Run 43264707710](https://github.com/DionCroft/Signal_Grove/actions/runs/43264707710)
is bound to d50518c77767427e5ad659139661c6463404ad62. All four artifact ZIPs and
the hosted complete-summary ZIP independently match GitHub's exact byte counts
and SHA-256 digests. The unmodified repository verifier re-parses every raw
inventory/native log and reproduces the hosted summary exactly:

| API | Inventory and unique executed cases | Disjoint partitions |
| --- | ---: | --- |
| 26 | 1,132 | 535 + 597 |
| 37 | 1,133 | 536 + 597 |

Every start has its exact positive finish; no failed/skipped/duplicate/missing
case is accepted. Both inventories agree per API and the two native partitions
are disjoint with an exact full union. All partitions bind the identical pair:

- CI debug APK: `ae0a97636ce0704509dc48c3b44511264fb20cd3d8d6d357698c3db0155924a1`.
- CI test APK: `4806d5e04872affa141777544f3e2007ef104864e0b7ab2ec7173f1657ccd012`.

These CI artifacts use their CI build/signing context, not the owner's production
key. External model acceptance is explicitly false; its five classes/eleven
methods remain separate. The earlier f9e3835 failure is retained and not relabelled.
This d505 result also does not certify the three newly added workspace/outline
cases or this later source commit; complete final-head hosted acceptance remains.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
|android-device-37-shard-0|13670522|`966919ede453075756bf3a24bae6bc3edfe0f5b82e3394026aebf3e5f1b9af59`|
|android-device-26-shard-1|15422307|`ba9b9b2061ac7afbe4385aafc71f2316409d6ddd1283818d55713dfb33093218`|
|android-standard-suite-summary|1169|`31c84ac92e81a291fcf1dfa896f4bec99e7f8b953644dd776e53b2364778bf91`|
|android-device-37-shard-1|16468523|`a66e6f8a4accb95a29a8c1d54ea9f4a51ee95bdb35c2245a4106f59d956e1931`|
|android-device-26-shard-0|11857524|`b7eb05bd47aa3672560731aad370879b032de9f94af3885723ae4c2b82d79fed`|

## State and next action

This chunk changes one Python validation probe plus documentation. All frozen
Android/native/build inputs from the workspace Review candidate remain unchanged;
no APK gains a new source identity. The original-key production artifact remains
explicitly bound to f9e3835 and unpublished. The phone, model files, histories,
saved connection and live Pi/Spark services are unchanged. The completed API26
review fixture and empty API37 fixture are now normally stopped with disks and
evidence retained; the latter's guest boot remains unverified. No VM reboot is
replayed, and older unknown fixtures are untouched.

Continue final-head full hosted acceptance and current stable/main used-handler
dependency closure. Stable 40 and inspected-main 100 findings remain, not waived by
this component probe. Actual private-channel/actor/TCP/TLS/canonical WebSocket,
real Pi/worker/project actions, physical production upgrade and public APK
publication/download checks remain separate. Every unresolved requirement in the
[original audit](../AUDIT_EXECUTION_PLAN.md) stays required; the full goal is active.
