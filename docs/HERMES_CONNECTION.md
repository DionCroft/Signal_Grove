# Hermes connection contract

Reviewed against the [official API reference](https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server/) and upstream gateway/API/pending-control implementations during these milestones through 2026-11-02. No upstream source is bundled. Compatibility depends on the server's advertised features and route behavior; no version string alone establishes support.

| Purpose | Route | Behavior |
| --- | --- | --- |
| Discovery | GET `/v1/capabilities` | Parse features/endpoints; only 404/405 falls back to GET `/v1/models`. |
| Session inventory/create | GET/POST `/api/sessions` | GET includes all origins with explicit pagination; POST creates API-owned sessions. Advertised PATCH/DELETE control API-owned rename/delete. |
| History | GET `/api/sessions/{id}/messages` | `inline_images=false`, oldest-first bounded pagination; stable IDs and compression continuation retained. |
| Native stream | POST `/api/sessions/{id}/chat/stream` | `{input}`; actual terminal run events required. |
| Runs admission | POST `/v1/runs` | Exact `{session_id,input}` plus a durable Idempotency-Key. |
| Run events/status | GET `/v1/runs/{id}/events`, GET `/v1/runs/{id}` | Saved sequence / Last-Event-ID resume; repeated events ignored. Replay gaps use status-only recovery. |
| Stop | POST `/v1/runs/{id}/stop` | Only with an advertised control and known ID; acceptance is reconciled separately. |
| Approval | POST `/v1/runs/{id}/approval` | Exact `{request_id,execution_generation,choice}` for canonical controls; legacy identity-bearing controls omit generation. Only current once/deny choices. |
| Clarification | POST `/v1/runs/{id}/clarify` | Exact `{request_id,execution_generation,answer}`; no new inference turn. |
| Unknown execution | POST `/v1/runs/{id}/resolve-unknown` | Only when advertised; exact admission/generation identity, fresh status and explicit confirmation. Never reruns the lost execution. |
| Compatibility | POST `/v1/chat/completions` | `{model,messages,stream:true}` with local history. Response completion is not proof of agent success. |
| Read jobs | GET `/api/jobs`, GET `/api/jobs/{id}` | Profile-specific offline snapshots; unsupported/auth/network errors remain distinct from an empty list. |
| Change jobs | POST `/api/jobs`, PATCH/DELETE `/api/jobs/{id}`, POST pause/resume/run subroutes | Per-connection opt-in, review/confirmation and one-shot requests with durable uncertainty receipts. |

Use a trusted HTTPS reverse proxy/private VPN and retain profile prefixes. Redirects, certificate bypass and automatic transport retries are disabled. Debug HTTP requires explicit opt-in and is unavailable in production. Phone localhost is not desktop localhost; use the host's reachable trusted address. Connection settings activate only after authenticated discovery succeeds. Workers read URL/token/debug policy as one atomically encrypted vault snapshot.

Native streams separate text, commentary, visible tool activity, approval/question requests and terminal run events. Private reasoning is not displayed. Assistant finalisation, EOF, compatibility finish reasons and accepted stop requests do not establish completed tool execution. A known run is queried/resumed without a new submission. Only a lost Runs admission can retry the exact persisted key/body within the supported retention window; no new-key replay is automatic. Expired receipts or unknown execution require server review.

Canonical pending controls carry exact prompt identity and execution generation. The app compares current identity, kind, prompt contents, choices and visibility to the displayed request before responding. Missing/incomplete consent cannot allow work. An uncertain acknowledgement requires explicit status review and never automatically resends a decision. Permanent/bulk approvals and arbitrary endpoint access are outside scope. Hosted/ownership refusals are surfaced rather than worked around. Desktop/Discord and other non-API histories can be imported read-only. Reply, lock, fork, steer, remote rename/delete and pending-run write controls are blocked for these origins, including newly missing/unknown source metadata. Fork is not a non-mutating workaround: the reviewed server implementation ends the original session when branching. Legacy cached records retain their earlier API-only behavior; fresh origin metadata replaces that assumption.

Job creates request paused state and local delivery. Resume/run can execute server tools; delete also cancels an in-flight job run. The editor changes only name/prompt and an explicitly replaced schedule; blank replacement preserves existing schedule fields. Server validation/timezone apply. Skills/provider/advanced delivery remain server-owned and are preserved. A lost mutation acknowledgement blocks subsequent changes until human review. Acknowledging that local marker never sends/repeats/undoes the action, especially for create/run whose effects cannot safely be inferred from missing acknowledgements.

Notifications need explicit opt-in and Android permission. Optional background checks only read already tracked run IDs; they never stream, submit, cancel or approve work. [WorkManager scheduling](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started) is best effort and subject to force-stop, battery, permission and network policies. See [compatibility](COMPATIBILITY.md) and the [user guide](USER_GUIDE.md).
