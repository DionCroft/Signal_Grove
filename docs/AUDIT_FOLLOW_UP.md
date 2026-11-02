# Hermes audit implementation

One active long-horizon goal covers the [2026-11-02 audit](audits/2026-11-02/REPORT.md). Work lands as reviewed, validated commits; the historical audit remains unchanged.

| Chunk | Scope | Status |
| --- | --- | --- |
| Audit baseline | Reproducible evidence, contract drift checker | Committed: `4d40e9f` |
| Connection isolation (HAM-A001) | Immutable operation client/scope, switch guards during controls and session mutations, discard stale details, transactional submission identity checks for run/history writes | Implemented; 3 new regressions pass on API 36, full suite 33 passed |
| Response history (HAM-A002) | Preserve compatibility answers without asserting authoritative execution success | Implemented; HTTP follow-up regression and distinct response/execution-state checks pass |
| Monotonic controls (HAM-A003) | Share generation/state merge between SSE and snapshots | Next |
| Job contracts (HAM-A004) | Known 5000-character limit, structured refusal and ambiguity handling | Next |
| Bearer API parity | Capability/readiness, model/provider choices and locks, exact durable request specs, skills/toolsets, fork/steer, activity/usage, stable identity, images | Pending |
| Management and automation | Separate authenticated management connection, integration discovery/configuration, supported cron controls and readback, scoped notifications | Pending verified contracts |
| Maintenance | Automated upstream drift checks, executed device CI, dependency/security checks, documentation | Pending |

No interface will claim that unsupported server mutations, native audio endpoints, host-file retrieval, or push delivery are available. Live Hermes interoperability and physical/OEM behavior require their actual environments and remain explicit validation gaps.
