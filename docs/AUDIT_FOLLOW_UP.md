# Hermes audit implementation

One active long-horizon goal covers the [2026-11-02 audit](audits/2026-11-02/REPORT.md). Work lands as reviewed, validated commits; the historical audit remains unchanged.

| Chunk | Scope | Status |
| --- | --- | --- |
| Audit baseline | Reproducible evidence, contract drift checker | Committed: `4d40e9f` |
| Connection isolation (HAM-A001) | Immutable operation client/scope, switch guards during controls and session mutations, discard stale details, transactional submission identity checks for run/history writes | Implemented; 3 new regressions pass on API 36, full suite 33 passed |
| Response history (HAM-A002) | Preserve compatibility answers without asserting authoritative execution success | Implemented; HTTP follow-up regression and distinct response/execution-state checks pass |
| Monotonic controls (HAM-A003) | Share generation/state merge between SSE and snapshots | Implemented; 3 new storage/concurrency regressions pass |
| Job contracts (HAM-A004) | Known 5000-character limit, structured refusal and ambiguity handling | Implemented; full 38-test API 26/36 suites and 46 JVM tests pass |
| Server discovery | Advertised capabilities, authenticated detailed readiness, provider/model catalog, installed skills and API toolsets; stale responses discarded | Implemented in Settings → Server details |
| Exact requests and stable identity | Hash-checked app-private full request blobs, atomic Room receipts, schema 5, stable advertised channel keys for new conversations, deletion cleanup | Implemented; 55 JVM / 45 device tests each on API 26/36 passed |
| Bearer API parity | Capability/readiness, model/provider choices and locks, exact durable request specs, skills/toolsets, fork/steer, activity/usage, stable identity, images | In progress |
| Management and automation | Separate authenticated management connection, integration discovery/configuration, supported cron controls and readback, scoped notifications | Pending verified contracts |
| Maintenance | Automated upstream drift checks, executed device CI, dependency/security checks, documentation | Pending |

No interface will claim that unsupported server mutations, native audio endpoints, host-file retrieval, or push delivery are available. Live Hermes interoperability and physical/OEM behavior require their actual environments and remain explicit validation gaps.

The four confirmed defects are covered by desired-behavior regressions in normal test sources. Historical diagnostic probes remain outside those sources. Job refusal classification is limited to exact pre-mutation guards verified in the pinned stable/main cron handlers; arbitrary HTTP 400, network failure, malformed acknowledgements and later exceptions remain uncertain and never auto-repeat. Structured safe error codes and Retry-After are preserved; raw server error bodies are not shown.

Primary contracts: [API reference](https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server), [pinned stable handlers](https://github.com/NousResearch/hermes-agent/blob/bf0203a794a07aac3ff764972ab9087baa4e4f4e/gateway/platforms/api_server.py).

Server discovery uses the pinned stable `api_server.py` handlers and `hermes_cli/inventory.py` payload. A normal catalog open uses the conservative server path; only **Refresh all provider models** sends `refresh=1`. Missing/false authentication metadata stays visible rather than claiming the provider is usable. This chunk browses models; per-turn selection and locks are the next chunk. Readiness uses the JSON status even on HTTP 200. Catalog expansion is lazy and provider IDs are deduplicated. The information dialog and whole-page scrolling keep bilingual/pinyin content reachable on small displays.

The request envelope freezes transport, route, full JSON and channel key before the Room submission transaction. Replay verifies the persisted hash and receipt binding; missing/corrupt files cannot be reconstructed or rebound. Only the original two-field/no-header form is rebuilt for pre-upgrade Runs receipts. Known runs can still poll/resume without needing their original inference body. Request bodies use bounded app-private files rather than large SQLite rows. A new turn retains its current request and removes older blobs for that conversation; chat deletion/cache clearing remove retained bodies. Storage remains app-private, not database/file encryption.

New mobile conversations receive a stable `X-Hermes-Session-Key` only when advertised; transcript/session rotation preserves that key. Existing/imported conversations retain their original header behavior to avoid silently moving their server memory scope. The key describes the logical conversation, never a fresh per-turn identifier or a credential. Live memory-provider behavior remains a server interoperability check.
