# Exact current-stable canonical authority - 12 November 2026

## Source and unchanged behavior

`scripts/compatibility_probe_session_authority.py` adds exact stable Hermes
`3b421578588243f920886e6fe79a6faa87ea026e`, with 57 explicit critical Git-blob
guards. All older revision definitions remain byte-for-byte equivalent as data.
The only executable admission change permits the new immutable revision through
the existing authority-wire flow selector. Existing fixture/audit functions,
case bodies, strict assertions and expected 46/13 counts are unchanged. This adds
no compatibility baseline variant, handler hash, absence or used-source waiver.

Final probe SHA-256: `72833bd86f5f54c15cb6811d96b2c6b1e93bd655f0db3175c9a55272d28eb1f0`.
Both final repository-source runs use the exact clean checkout and fresh isolated
Windows homes/databases. Every actually loaded project module is hashed again:

| Flow | Positive cases | Loaded module records | Actual dispatcher cases |
| --- | ---: | ---: | ---: |
| authority-wire |46|532|22|
| attach-first |13|520|10|

Counts describe separate flows, not 59 distinct combined cases. Each flow rejects
duplicates and checks its exact expected count, with no unexpected audit denial.
Earlier discovery trials and final-source runs are separate; none replaces an
old failed/unknown outcome.

## What executes

The real SessionDB, SessionAuthority, AuthorityConnection, runtime store, strict
wire parameter/result registry, SessionEvents ring and fanout execute. Trusted
actors, the small runner and in-memory sinks are explicit synthetic fixture
inputs, with two seeded transcript messages. No dispatcher or registry stub
replaces upstream behavior.

The authority/wire flow retains the original authority/profile/instance/epoch/
revision/generation binding; foreign actor/reference/LOCAL-history owner refusals;
missing capabilities/identity and forged dictionary operator refusal; subscription
reuse/separation and foreign-detach refusal; exact queued-receipt reuse and
changed-payload refusal; claimed work recovering unknown in a new epoch without
another claim; and stale/retired authority refusal. Actual JSON-RPC checks retain
request-ID echo, strict malformed/unknown/foreign parameter refusal, read-only
mutation refusal, runtime metadata without actor grants, history-preserving
resume, replay epoch/cursor checks, contiguous events and teardown.

The attach-first flow makes a real synthetic admission/claim, recovers it unknown,
then attaches before requesting replay. Detached and post-attach/pre-replay events
are recovered/merged contiguously without duplicates. An advanced cursor returns
only the new tail. Unknown work is never replayed, the transcript remains exact
and its registration is released on teardown.

The tests write only their fresh fixture stores; no producer, inference, tool
turn or external service runs. Python's owned local event-loop socket primitives
do not establish HTTP/WebSocket authentication or network-protocol acceptance.
The source fence includes canonical/room/workspace dependency modules, but a
pinned module is not proof that all of its room/workspace behavior was exercised.
Python audit hooks remain defense in depth, not an OS security sandbox.

## Refusal and reproduction

Three final-source controls refuse before output-home creation or any upstream
import: tampered newly pinned `gateway/session_controls.py`, an unknown revision
and `-O` disabled assertions. Full portable 422/runtime 15/checksum policies pass.
All existing baseline/source-golden tests remain unchanged.

Follow the [original isolated component procedure](2026-11-07-canonical-authority-component.md)
with an existing compatible Windows interpreter/dependency site, `-I -S -B`,
the exact clean source above and `--revision stable-3b42157`. Run each deliberately
chosen `--flow authority-wire` or `--flow attach-first` once into a distinct fresh
E output root. Inspect exact result/traceback and reconcile uncertain effects
before another trial. No dependency installation or live configuration change
belongs to this check.

## What remains

Current stable 40/inspected-main 100 used-handler findings remain. Complete changed
dependency/semantic review, real socket authentication/actor/profile/transport,
full backend startup, actual owner runtime shutdown, real private/shared/paged
rooms and workspace/worker/root/NAS effects still need matching evidence.
Component passes are not combined into an end-to-end success claim.

The earlier d50518c complete standard Android gate retains its verified 1,132
API26/1,133 API37 scope; new workspace/final-source gates remain separate.
This chunk changes one Python validation probe plus documentation. Android/native/
build inputs, phone models/history/settings/signing/package identity and Pi/Spark
services are unchanged. No new APK/release is created. Continue the [full original
audit](../AUDIT_EXECUTION_PLAN.md), final production upgrade/live retention and
public APK/checksum/download acceptance. The full goal remains active.
