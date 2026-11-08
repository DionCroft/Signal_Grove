# Current-main canonical compatibility review - 8 November 2026

Review target:21ae0e1238fdf466fa52a85a6ebf75d6899e49da. Stable remains
2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19. The failed0b8cd53 canary artifact
was downloaded and its advertised ZIP SHA-256 verified; all177 existing parsed
source identities/definitions match the immutable Git checkout. Failures are
retained, never reclassified as a passing run.

The main mode adds optional session-client attribution, stored transcript
reads, workspace/picker metadata and gated plugin RPC. Existing default
session.list, explicit resume with omit_messages, and replay shapes stay
compatible. Runtime capabilities do not grant actor rights. Signal Grove adds
no new application RPC in this compatibility chunk.

The saved-gateway dependency change since the prior main review renames
_http_error to http_error and reuses it from the probe route. Authentication,
callback matching and native ticket semantics are unchanged by that rename;
complete callback/private-grant module fences remain required.

The canonical listing refactor validates stored closed receipts through
restorable_receipt. A moved store may retain its original receipt-home string;
route/source/entry/owner/policy/lineage checks still bind it. The phone continues
to bind its selected authenticated profile/home and runtime independently.
The stored read path may backfill a derived display index; it is not a promise
of zero writes for every historical store. No transcript read is added here.

Eight newly tracked dependencies cover stored reads, transcript row projection,
picker metadata, plugin RPC, receipt recovery, local receipts/lineage and policy.
Old missing files are accepted only at immutable Git-tree-proven older pins.
Every preexisting F0 entry remains intact. The eleven changed/refenced handlers
require one complete exact source mode; a commit label, matching handler alone
or mixture of old/new dependencies cannot authorize it.

The isolated real-main component checks pass 46 authority/dispatcher and 13
attach-first cases with no unexpected denied operations. The policy suite
passes 201 tests, including missing/changed source and unknown/missing handler
counterfactuals. Native TCP/auth/attachment and final build checks are pending.
Python audit hooks provide bounded test defenses, not an OS security sandbox.
No Pi deployment, inference, worker or owner-data acceptance is implied.

Primary source at the reviewed immutable pin:
- [Authority dispatcher](https://github.com/NousResearch/hermes-agent/blob/21ae0e1238fdf466fa52a85a6ebf75d6899e49da/gateway/session_controls.py)
- [Stored listing](https://github.com/NousResearch/hermes-agent/blob/21ae0e1238fdf466fa52a85a6ebf75d6899e49da/gateway/session_listing.py)
- [Receipt recovery](https://github.com/NousResearch/hermes-agent/blob/21ae0e1238fdf466fa52a85a6ebf75d6899e49da/gateway/session_local_recovery.py)
- [Canonical schema](https://github.com/NousResearch/hermes-agent/blob/21ae0e1238fdf466fa52a85a6ebf75d6899e49da/tui_gateway/contracts/canonical_sessions.py)
- [Saved-gateway routes](https://github.com/NousResearch/hermes-agent/blob/21ae0e1238fdf466fa52a85a6ebf75d6899e49da/hermes_cli/web_routers/gateway_connections_reach.py)

The advertised transcript/picker/client-attribution features still need deliberate
client implementation and their own authorization/cursor/compaction tests where
applicable. Finish the preserved canonical viewer, exact workspace/CAS/worker/file
routing and every other open audit row. Full exact-head hosted/production/phone
release gates remain separate; the audit goal is active.

## Post-acknowledgement metadata read recovery

The first required build passed721 JVM and both lint variants. Three mock native
runs passed. The first actual main/API26 attempt recorded a valid adoption ACK,
then the final authenticated profile GET failed with an EOF before response
headers. The acknowledged receipt and transcript were preserved; admissions and
worker executions stayed0. The failed native attempt remains retained.

Six focused tests against unchanged production transport completed with three
expected read-recovery failures and three passing one-send/redirect checks.
The fix gives only GET api/auth/me and GET api/profiles the library's connection
recovery policy, with no body/profile/query/tail. The selected origin/prefix,
credential, certificate/DNS/permission rules and aggregate call timeout remain.
Token rotation, ticket creation, mutations, other reads and WebSocket RPCs keep
the original transport. Identity and profile-home checks still run on responses.

A native fault regression drops the post-ACK profile response once and requires
one resume, one replay and exactly the original two sockets. Existing lost-ACK/
replay/cancellation assertions stay intact. The final source passes 727 JVM cases
and 25 method executions across 13 native controllers, plus both lint variants.
All 623 source inputs, original debug keys, 28 legacy caches and published
outputs are independently verified. Full hosted/production acceptance remains
separate.
The underlying live EOF timing is not conclusively isolated. No server operation
from the failed fixture was replayed; the new cohort uses fresh synthetic homes.

The older prior pin's full185-module static inspection stops at the absent
cron/jobs_time.py. No absence/compatibility waiver was added. Its19 canonical
Git bindings and explicitly scoped native tests are separate evidence.

[OkHttp connection recovery policy](https://github.com/lysine-dev/okhttp/blob/main/okhttp/src/commonJvmAndroid/kotlin/okhttp3/OkHttpClient.kt)
documents recovery from closed pooled connections and the need to disable
retries for destructive requests. The library version stays pinned at5.5.1;
the focused behavioral tests use that resolved version.
