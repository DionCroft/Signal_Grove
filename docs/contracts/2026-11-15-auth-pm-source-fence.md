# Stable authentication review with explicit PM source dependencies

15 November 2026. Exact stable Hermes
`3b421578588243f920886e6fe79a6faa87ea026e` receives three source-bound auth
variants after the remaining dependency review. Stable's used-handler findings
fall from **40 to 37**. Inspected main
`f578714e0ee122179ec28fecb9af989583b71baf` retains **169 findings** and selects
none of these new variants. The compatibility gate remains open.

## What was reviewed and accepted

The earlier ten-file middleware fence differs in exactly two whole modules:
[`gateway/run_api.py`](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/gateway/run_api.py)
adds off-loop socket-handler loading, attached-client accounting before awaiting
the loader and a second drain/readiness check before dispatch.
[`hermes_cli/web_server.py`](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/hermes_cli/web_server.py)
wraps its existing runtime body in PM's process-wide prompt-suppression scope.
The complete 90-/18-line deltas are retained. Official previous-commit bytes
match both old reviewed SHA-256 values; all other eight full modules match the
prior fence. One private read initially used the wrong Windows text encoding;
the hash mismatch refused it before any source admission, then explicit UTF-8
produced the exact reviewed bytes.

The unchanged repository probe, whose source is still
`cf2029ef201cbc6a0cb08758cb0081f16337e548fdfcdcb68899f15eb7d1d621`, runs fresh:
all **87 cases pass**, with the same complete earlier method sequence, 949
actually loaded project-module hashes and no unexpected audit denial. Its 55
critical Git blobs match the clean source. Existing auth/profile/PKCE/ticket/
cookie/deployment-header and loader/start-entry assertions are unchanged. This
retains the [explicit synthetic boundaries](2026-11-12-native-loader-startup.md).

The admitted entries are only:

- `auth_middleware:_GATE_PUBLIC_PREFIXES`
- `auth_middleware:gated_auth_middleware`
- `auth_native_run_api:GatewayRuntimeAPI._serve`

Each binds the exact handler AST and the same **18 named complete source hashes**:
the prior ten auth/callback/startup modules, `ws_boundary`, and all seven specified
PM modules (`__init__`, `client`, `extras`, `environments`, `environments_adopt`,
`package`, `paths`). Six PM aliases are newly tracked; `pm_client` already existed.
Both the historical complete upstream object and every earlier review/absence/
usage/guard datum remain exact. Removing just these three appended entries
reproduces the entire original metadata vector.

The checker permits only the seven explicitly declared PM names as additional
source-guard dependencies. They must be present and match their exact snapshot
bytes to select a variant. Changed/missing helpers or arbitrary unknown names
cannot select it. The official complete historical tree proves the six newly
tracked files also exist at the original f0 pin; no empty-source absence or
moving-commit waiver is added. Static extraction never imports the SDK.

All **54 target/dependency drift counterexamples** reopen the corresponding
finding. Main's six additional PM inputs are read from its exact official tree
and raw blobs, with Git blob and SHA-256 verification. Its different context
keeps all 169 findings. Stable's newly extracted contract preserves every older
field; the only added sources are the six explicitly named PM aliases.

Prompt suppression prevents reading stdin. The real `ensure_import` can still
call `sync_venv`: the fixture uses a stopping installer adapter, so package
installation/adoption and full runtime startup remain separate acceptance.
These variants accept only the reviewed auth/admission behavior at this source.
They grant no live write, native deployment, canonical transport, project/tool,
provider, model or package-management permission. Full helper/transitive review,
the remaining canonical/room/workspace paths and actual deployment behavior are
still required. A complete module hash binds reviewed inputs; it does not prove
all possible behavior of every function in that module.

## Policy verification and preserved failures

Seven new portable cases cover static-only extraction, every required PM input,
exact context selection, drift/absence, commit-label counterexamples, malformed/
untracked dependencies, historical snapshots and complete immutable metadata.
The full final Windows suite passes **1,015 cases: 801 positive / 214 platform
skips**. The native actor sources are unchanged; no new native actor pass is
claimed from this policy-only run.

The first 1,015-case run exposes seven historical-vector assumptions: six newly
tracked modules increase the watched inventory from 222 to 228, and three
appended auth entries change two old aggregate vectors. The original failed log
remains. Count assertions now require 228; a separate invariant proves all 222
original names remain and exactly the six specified PM aliases were added.
The two historical golden hashes are unchanged. Their test-only projection
removes only the three new entries and checks the exact commit/18-source fences;
the new tests independently check the full current data. No production
comparison uses that projection, and no test/assertion/old finding is removed.

## Hosted evidence and outstanding device runs

The [source7e1bd25 parser job](https://github.com/DionCroft/Signal_Grove/actions/runs/43665134882)
passes the actual hosted fixture setup and all **1,008 Linux policy methods**
without skips. Independent artifact verification checks its published ZIP digest,
complete raw positive method union, identical Windows method inventory and
stopped finite unit. It also runs all 15 runtime-model cases. Pinned registry,
sidecar and all three worker jobs pass. This belongs to that exact source; the
seven newer PM policy cases need their own hosted result.

The [same source's Android build](https://github.com/DionCroft/Signal_Grove/actions/runs/43665134845)
passes. Its ZIP digest, 1,011 unique JVM cases/97 XML reports, both lint variants
(zero errors; 52 debug/51 release warnings) and both actual CI APK hashes are
independently verified. Four API26/API37 device jobs remain live at this
checkpoint. Their full native inventories/unions and historical failure causes
are not accepted early. No job is restarted or silently replaced.

## Evidence identities

| Artifact | SHA-256 |
| --- | --- |
| docs/contracts/hermes-baseline.json | 4aeeb032eed3643858d1eaffb3b0781acc0bd6ad9c0f881d1723a31d01acaf46 |
| scripts/check_hermes_contract.py | 1629c3caf1d8f19954e07cb1f929eebac1215a5dad6090e7eff5637d6144424f |
| scripts/tests/test_auth_pm_source_guards.py | 47c8e7aa715ecd8b4fbce40b90221eb25f2f662911cb50acb2b77f19e7ba0ca0 |
| Independent exact-source/54-counterexample review | c6465e490a7cf9ad67c20ec2338b3fddacf841cc2d30c20d206a61c40ba4f249 |
| Fresh 87-case runtime report | 63aa19d9b4bbc13a64d62b6e06b2f94fcfd70b2d7c8d0803500b5c644af6b480 |
| Final 1,015-case Windows log | 7690ebdd574b997e61a879c95fe26c2e2c53f3adfa3a32ee15099eda5d084815 |
| Hosted full policy artifact review | 0a56f94004d0af8fc5a036326ad6c48a1065a512d184b2a26fc8ff8921dd99be |
| Hosted JVM/lint/APK review | b4454ca13db414b67644b49c978f86a2daf7a76fc341635729af8d9132776ef7 |

Private paths, fixture data, complete SDK copies and logs remain outside Git.
The full goal remains active: all unresolved upstream/shutdown, historical API26,
native admission/executor/recovery, real project/workspace/worker/NAS, automation,
integration, phone/local-AI/security and original-key release requirements stay.
Android/build inputs, Pi/Spark services, owner data and published beta.9 are unchanged.

[Full goal](../GOAL.md) · [Audit ledger](../AUDIT_EXECUTION_PLAN.md).
