# Response-error logging review — 7 November 2026

The previous exact-source canary correctly rejected the complete request-registry
bytes at Hermes main [E826](https://github.com/NousResearch/hermes-agent/commit/e826a14a467fa74a718b0010487a8eb12421f879).
The selected WebSocket dispatcher AST and complete ws.py bytes were unchanged.
This review adds a separate E826 source vector; the original F0 baseline and all
prior reviews, including AA, remain intact.

## Reviewed change

The complete [server_requests.py](https://github.com/NousResearch/hermes-agent/blob/e826a14a467fa74a718b0010487a8eb12421f879/tui_gateway/server_requests.py)
diff moves response-error logging outside the request lock and changes it from
debugging the entire error object to a warning with code/message fields. A
non-dictionary error becomes an empty dictionary for that log statement. The
request settlement, socket ownership, callback/event, refusal and replay rules
are unchanged. This excludes error.data and malformed payloads from that
statement; it does not establish that arbitrary message text is redacted.

The small complete [rpc_dispatch.py](https://github.com/NousResearch/hermes-agent/blob/e826a14a467fa74a718b0010487a8eb12421f879/tui_gateway/rpc_dispatch.py)
diff was also reviewed as transitive context: a logger.exception call is added
inside the existing exception handler before the unchanged error response and
transport write. The real RPC pool was not executed by this probe. Desktop/shared
TypeScript error-reporting changes are not Android feature certification.

| Complete source | Git blob | SHA-256 |
| --- | --- | --- |
| ws.py (unchanged AA/E826) | `833751e0df038b0c551814ee3bfea95a27d0dabf` | `f1f3750accad549937a0d34d3c4b0eafdffbb04fc3ea46022415bfbe5f361e2a` |
| E826 server_requests.py | `7d089f8ad2701f8b220ffc061d2872cf6ecf13ef` | `04b0fc8f1302ec1efcd36027f155f64dd8654bda5576ce4c37667a7e47eccadd` |

Both complete blobs were verified, with the registry SHA-256 matching the
independently digest-verified hosted main report. The accepted dispatch AST stays
`aa59f2ac28285aebb7b01d7616aa0e9ab40539831e656aaef1567df9652ec8ec`,
bound to these two complete module hashes. Missing or changed bytes reopen
review even with the same AST. AA retains its distinct registry hash.

## Checks and limits

The canonical bounded probe verifies AA and E826 separately: the original eleven
socket/refusal/replay cases pass on both; E826 adds two cases for logging outside
the lock, warning fields, excluded error.data, malformed errors and unchanged
refusal settlement. A logging handler nonblockingly acquires the registry lock,
so the old behavior fails immediately rather than hanging. Enabling the new
assertion against unchanged, blob-verified AA source produces the expected lock
assertion failure after the eleven existing cases pass. This retained negative
probe does not bypass the source guard.

All 179 policy and 15 runtime tests pass. Saved digest-verified stable, AA and E826
snapshots compare cleanly; eight missing/changed-source counterfactuals reopen
exactly the dispatch finding. Every original baseline field and prior reviewed
entry is preserved. All four embedded workflow Python snippets compile and use
actual newline values. The recurring four-job compatibility workflow now keeps
separate AA and E826 result JSONs within its sidecar artifact.

No app, Android test or build input changed in this chunk. The earlier f793
552-JVM/27-native-per-API results remain their original cohort. This probe uses
the actual registry and two extracted WebSocket functions, with synthetic
validation, transports, authority, context and thread execution. It certifies no
parent startup, real scheduling, schemas, HTTP/authentication, vault/account,
Pi deployment or owner-phone behavior. Python audit hooks are not an OS sandbox.

Fresh final-source hosted compatibility and complete Android gates, original-key
signed assets, advisory/provenance review and public download verification remain
required before release. The prior API26 incomplete outcome and all earlier
failed cohorts stay retained; the diagnostic improvement is not a proven fix.
