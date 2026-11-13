# Transitive compatibility guards and paired upstream shutdown review

13 November 2026. All four [acceptance priorities](../GOAL.md) and the full
original audit/on-device AI objective remain active. This chunk strengthens
development review policy and records actual regression evidence. It changes
no app runtime, live gateway, models/defaults, credentials, jobs or release.

## Unchanged callers no longer hide changed helper sources

Nine already-watched callers receive original-source context guards: two
channel environment publication functions; registry resolution/configured-entry
listing; four custom/user model catalog builders/persistence functions; and
credential-pool addition. Each now requires its original containing module as
well as the original caller fingerprint, unless an independently reviewed,
fully source-bound variant explicitly applies. No new variant is approved here.

The original upstream object, every fingerprint/source hash, mobile usage list,
reviewed variant/replacement/absence and all prior guard entries are unchanged.
Removing exactly the nine new guard entries restores the complete previous JSON
data. These are additional review requirements, not a baseline reset.

Nine counterexamples change only the helper-containing source hash while keeping
the watched parent body identical. The old policy misses each; the strengthened
policy requires review. Three new portable behavior cases cover helper drift,
missing dependencies and parent-body changes. Their caller list is independent
of configured guards, so deleting a guard cannot silently delete its test scope.

The first full 456-case run exposes three fixture assumptions: two synthetic
channel tests claimed the whole report was accepted after only seven channel
variants were reviewed; one room test counted unrelated guards. The corrected
channel assertions require the two newly visible registry-context findings to
remain open, preserve all seven reviewed-variant checks and prove a commit label
cannot waive findings. The room assertion still checks all fourteen original
room guards, scoped to that family. No production guard is relaxed or test
removed/skipped. The initial failed log is retained separately.

Final source passes all 456 policy cases. The 15 runtime-model cases pass too;
their inputs are unchanged by the final test-target strengthening. The previously
passed 69-case Windows native source set is byte-identical and was not rerun.

## Exact inspected main deltas, with no execution or waiver

The new main report inspects 6da444ebda7533429b655aeca2b02d77fd900238.
Eight official before/after modules are verified against both Git blob IDs and
SHA-256 values in the two pinned reports; all nine newly flagged definitions
match the checker's full normalization routine. The prior report used
438008f475fd89dd661ab2fda8355303a9f7bda6. This is static triage, not runtime
interoperability or complete transitive review.

- [Environment publication](https://github.com/NousResearch/hermes-agent/blob/6da444ebda7533429b655aeca2b02d77fd900238/hermes_cli/env_loader.py)
  releases YAML bridge ownership markers when an operator secret source owns a
  value, including a supplied existing value. Profile and child-environment
  effects still need bounded behavioral acceptance.
- [Channel registry](https://github.com/NousResearch/hermes-agent/blob/6da444ebda7533429b655aeca2b02d77fd900238/gateway/platform_registry.py)
  records failed lazy loads and reports whether a wanted platform remains
  unresolved. Missing load results must not be treated as deleted settings.
- [Model catalog](https://github.com/NousResearch/hermes-agent/blob/6da444ebda7533429b655aeca2b02d77fd900238/hermes_cli/model_switch_providers.py)
  obtains a configured, origin-aware key for a bare custom-endpoint catalog
  probe. Removed facade imports use a new local header-normalization helper;
  that helper is actually defined, rather than an inferred missing name.
  Destination/credential/profile behavior remains part of semantic acceptance.
- [Credential-pool addition](https://github.com/NousResearch/hermes-agent/blob/6da444ebda7533429b655aeca2b02d77fd900238/hermes_cli/web_routers/ops.py)
  persists the provider's configured base URL with a manually added key. No
  phone credential is read, transferred or exercised in this static review.

| Exact retained report | Previous policy | Strengthened policy |
| --- | ---: | ---: |
| Stable 3b421578588243f920886e6fe79a6faa87ea026e | 40 findings | 42 findings |
| Main 6da444ebda7533429b655aeca2b02d77fd900238 | 124 findings | 124 findings |

The two additional stable findings are unchanged registry caller bodies in a
changed full module. Every original finding remains. All other main deltas,
dependency paths and live effects remain open; no source variant is waived.

## Official shutdown behavior on pristine and candidate sources

The required upstream scripts/run_tests.sh runs each file in its own process,
with retries explicitly disabled and skips treated as failure. Fresh disposable
source copies keep the immutable checkout and canonical candidate untouched;
the runner's bytecode/timing caches stay in those disposable copies. Its normal
credential-environment and temporary-home isolation remains in use. No live
gateway or model action is requested.

Both cohorts use exact stable 3b42157. The candidate differs only in the two
already-reviewed files, gateway/run_shutdown.py and tui_gateway/session_reaper.py.
All 23,586 tracked input bytes remain unchanged during each cohort. The two
JUnit case sets are independently reconciled and identical, with distinct
positive outcomes for every case, no failures/errors/skips/retries and no
matched captured lifecycle warning lines in these runs.

| Official test file | Positive cases in each cohort |
| --- | ---: |
| tests/tui_gateway/test_backend_heartbeat.py | 5 |
| tests/tui_gateway/test_session_db_ownership_teardown.py | 17 |
| tests/gateway/test_shutdown_executor_quiesce.py | 10 |
| tests/gateway/test_shutdown_teardown_profile_scope.py | 2 |
| tests/gateway/test_mcp_shutdown_profile_passes.py | 2 |
| tests/gateway/test_gateway_shutdown.py | 14 |

That is 50 behavior cases per cohort. They broaden regression coverage of
heartbeat startup, handle ownership, executor/writer drain, profile teardown,
MCP cleanup budgets and gateway stop behavior. Because pristine also passes,
they do not reproduce or close the separate original native-owner shutdown
warning. The prior pristine finding, eight helper regressions and three
explicit candidate owner trials remain separate evidence. The heartbeat
candidate stays undeployed; whole upstream/lifecycle acceptance is unproven.

To reproduce, use the exact reviewed sources and patch, fresh disposable copies,
the checkout's prepared PM test interpreter via HERMES_PYTHON, and approved
scratch. Run each listed file through scripts/run_tests.sh with -j 1,
--file-retries 0, --fail-on-skip and a unique JUnit path. Retain failed output;
do not replace the immutable base or reuse an uncertain partial run.

## API26 full raw union; the two-API gate remains pending

The preceding f0e999e cohort remains failed: its API26 shard 1 starts 601 cases,
records 600 positives and fails the old Home-picker/Chat-selector diagnostic.
The retained trace has 16 selected conversation messages but Home selected,
Chat unselected and no message list, matching the earlier controlled failure.
The original clarification method is positive in that failed partition; the
original scroll method is absent. No old failure is hidden or replayed.

The c814d743 route-fix cohort now passes both API26 shards. Independent validation
checks the raw streams, exact source/APK binding, equal complete inventories and
disjoint partitions: 540 + 601 distinct positives cover all 1,141 standard
methods, with none missing or repeated. Both original clarification and scroll
methods pass. Apply and Cancel traces use the unique nav-chat route and complete
with real dialog keyboard verification. This does not establish the causes of
the two original intermittent failures or accept external-model methods.

At this checkpoint both API37 jobs are still live. The complete two-API
aggregator is not bypassed or narrowed; no complete c814 gate is claimed yet.
The last fully reconciled two-API gate remains source c9c0718's 1,135/1,136.
All app/build inputs match the prior required Android checks; no fresh Android
build is claimed for these policy/test-data changes. Final-head CI stays open.

| Evidence | SHA-256 |
| --- | --- |
| Paired official shutdown run record | 81a538cead87762f2ab78661fbc65b8e957d13e8ed984bb9eccb28f5a6ba0790 |
| Independent paired-case/source review | af2805ae75d4a143c2e6d9cd6be2f21a3528e1dc4d0dffc1cd6e2883457e5c9b |
| Eight modules/nine definitions | a418016b4c21496c43aef4d12f99e9f69675b8bb62df4f655e4d1a3da06744fd |
| Nine helper-only counterexamples | 6bb1d9a44d19468dbf3d5cb74dfe7aeff09660ecdc3fe920b401f30f81c08d53 |
| Final 456-case policy log | 7c1dd051c4c292f8e3189f7d11a92411d7abb129bf5f5c30ba99246073aa8fa5 |
| c814 API26 shard 0 raw log | 0ab32ac4fd21e67d967f63d0cc14fca434df6621ac7320d57d3844f16e1ebf58 |
| c814 API26 shard 1 raw log | 140818ea6eebaf105fd9617404f28c7299bddc6ac940a7ba05e3e7c879419e62 |

Raw logs, derived SDK copies, drivers and private identities remain in approved
scratch. The Pi UNAVAILABLE and separate local BUSY finding remain as documented
in the [previous checkpoint](2026-11-13-pipe-availability.md); no component is
deployed. Native actor/profile/operator/NAS/workspace, automation/integration,
security/device and final original-key production upgrade/live/offline release
gates remain. The owner phone is absent; beta.9/source0ee and stable1.0.0,
signing/package identity and existing owner data remain unchanged.
