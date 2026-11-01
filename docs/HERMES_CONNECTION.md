# Hermes connection contract

Verified against the [official API documentation](https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server/) and upstream `gateway/platforms/api_server.py` / `api_server_runs.py` on 2026-11-01. No upstream source is bundled.

| Purpose | Documented route | Client behavior |
| --- | --- | --- |
| Discovery | GET `/v1/capabilities` | Parse features/endpoints. Only 404/405 falls back to models. |
| Legacy discovery | GET `/v1/models` | Select advertised model, local-history compatibility mode. |
| List/create | GET/POST `/api/sessions` | `source=api_server`; paginated listing. Client-generated API IDs. |
| History | GET `/api/sessions/{id}/messages` | `inline_images=false`, oldest order, bounded pagination. |
| Session stream | POST `/api/sessions/{id}/chat/stream` | `{input}`; exact terminal events required. |
| Resumable turn | POST `/v1/runs` | `{session_id,input}` and unique persisted Idempotency-Key. |
| Run stream/status | GET `/v1/runs/{id}/events`, GET `/v1/runs/{id}` | Typed progress and explicit terminal state; manual recovery. |
| Stop | POST `/v1/runs/{id}/stop` | Only when advertised and ID exists; status checked separately. |
| Approval | POST `/v1/runs/{id}/approval` | `{choice,request_id}` from visible server request; once/deny only. |
| Compatibility | POST `/v1/chat/completions` | `{model,messages,stream:true}` with persisted turn key. No agent-success claim. |
| Jobs | GET `/api/jobs`, GET `/api/jobs/{id}` | Read-only documented probe (some servers omit job discovery). 404/405/501 is unsupported. |

Native events include `assistant.delta`, `assistant.commentary`, `assistant.completed`, `tool.started/completed/failed`, `approval.request`, and terminal `run.completed/failed/cancelled/interrupted`. Runs streams use `message.delta` and final `run.completed.output`. Private reasoning is not shown. Commentary appears as progress rather than final response. Job errors remain distinguishable from an empty job list.

Use a trusted HTTPS reverse proxy/private VPN, retaining path prefixes. Debug HTTP is explicit and unavailable in the release configuration; it does not weaken certificate checks. The default host binding is localhost port 8642. Phone localhost is not desktop localhost. Use the host's reachable address, not a copied desktop loopback URL.

Hermes API tokens can grant tool and terminal access. The client never auto-approves requests. It does not implement permanent approvals, unknown-execution resolution, clarification submission or arbitrary server endpoints. A 409 ownership refusal is surfaced, not worked around by changing session IDs. Desktop/CLI-owned sessions are excluded from imported API sessions.

For a dropped connection, use Check / recover run. If an existing run ID is known, only query it; if the Runs admission response was lost, the same persisted key/body can recover the run within the retention window. Legacy/native stream submissions without usable admission replay are not automatically resent. Unknown execution may need server-side review.
