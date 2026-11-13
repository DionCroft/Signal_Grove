# Demo failure and native shutdown diagnostics

13 November 2026. The existing full audit goal remains active with all four
[explicit acceptance priorities](../GOAL.md). This chunk improves test evidence;
it changes no product behavior, server deployment or published APK.

## API26 reproduction and failure-only evidence

The retained source 1180 [hosted failure](https://github.com/DionCroft/Signal_Grove/actions/runs/43343517764)
is `Milestone03Test.demoClarificationChoiceIsReviewedBeforeSubmission`: its exact
multilingual Demo-question node was missing at `performScrollTo`. The failure
remains open; no actual cause or correction has been demonstrated.

On the owned API26/x86_64 fixture, five preplanned unchanged three-method class
trials passed 15 cases. One separate original-pair shard1/2 reproduction then
passed all 598 cases in 1,332.787 seconds, including the failed method at its original
position after the scheduled-job and task-search tests. Its complete inventory
is 1,135; the 12 external-model methods remain excluded. This is one shard, not a
complete new standard Android gate. Raw shard SHA-256:
`35eec11bb92aafda2ae5a7af353f5215d1d4b97fef29d73ea98d9ff8e3ed3ea5`.

`Milestone03Test` now wraps the same Demo interaction with a failure-only safe
snapshot. It records fixture booleans, bounded counts, lifecycle/IME state and
exact/English node counts; it excludes message/draft text, URLs, credentials and
session identities. It preserves the original assertion as the cause and never
retries a failed click. Existing waits, review/submission assertions and all
three test-method identities are unchanged. Standard CI already retains `qa`
JSON files. The snapshot is diagnostic instrumentation, not a claimed fix.

The fresh original-key debug/test pair passes all 5 focused native cases:
the two original predecessor methods and all 3 Milestone03 methods. There are no
failure snapshots. This positive run does not exercise the failure-only branch
or explain the old failure. Focused raw log SHA-256:
`bf483ec05fd3b05be52b4b233dc736c045ee3fde4641f7ab6c8fb63a07429dee`.

## Actual SDK shutdown ordering

The existing isolated native-Windows probe adds default-off `--trace-close-order`
for exact stable 3b421578588243f920886e6fe79a6faa87ea026e only. Unreviewed or older
trace targets refuse before home creation. Default empty mode still emits no
trace. Optional mode records bounded call/return metadata using Python profiling;
it refuses an existing profiler and changes no SDK function or callback. The
original source/import/socket/auth/profile guards remain required.

Default empty mode validates 1,206 real source modules. Traced seeded mode
validates 1,207 modules and all 20 native owner room checks. Both retain one shutdown
warning and parent exit 2. The optional trace contains 38 events with no truncation:
`acc11cf01f3489613af0a795ea92b23bd6f2f2ee28d3a883645b0f18518b830c`. Complete runtime acceptance remains false.

The actual call order is shared database `close_all`, followed by the registered
heartbeat `_atexit_clear`, `clear_backend_heartbeat` through a closed connection,
then `_reopen_after_close_locked`. The runner's executor quiescence does not stop
that refresher. SDK source locations at the reviewed pin:

- [Shared DB shutdown](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/gateway/run_shutdown.py).
- [Heartbeat lifecycle](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/tui_gateway/session_reaper.py).
- [Reopen warning](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/hermes_state.py).

A correction must stop/drain the refresher before closing its DBs, make repeated
cleanup safe and prove no late writer/reopen. Warning suppression, callback
removal or source-guard relaxation is not acceptance. No SDK patch or live
gateway restart is part of this chunk.

## Fresh upstream static review

The [resumed-source compatibility run](https://github.com/DionCroft/Signal_Grove/actions/runs/43426665676)
passes parser, pinned registry and pinned sidecar jobs; upstream review still
fails. Both current report ZIPs match GitHub digests. Stable3b42157 remains 40
changed used handlers. New inspected main 0e72e8a has 104, up from 101 on 415fa33;
neither cohort removes a used API or management route.

The three newly flagged handlers were inspected at their exact source hashes:

- `model_switch:model_selection_config_updates`: imported credential/pointer
  tuples equal the previous literals; route/context guards remain. Static
  equivalence of this refactor does not accept the whole model-assignment path.
- `profile_helpers:_fallback_profile_entry`: metadata is read once and `ui_meta`
  is added. The inspected metadata reader returns a copied dictionary and
  normalizes that field; wider cache/identity behavior remains to validate.
- `profiles:_profile_to_dict`: `ui_meta` is additive. Android's profile reader
  projects known identity/display/path fields and does not execute it.

Only AST/source inspection was performed for the new pin. No new reviewed
variant, trusted execution target, baseline digest or compatibility waiver was
added. The full transitive/semantic and deployed-version review remains open.

## Build and delivery scope

The frozen local build is b5433027 plus the two reviewed diagnostic code changes,
dirty by design, with 876 exact source inputs. It passes 1,011 JVM cases/97 reports,
422 policy cases, 15 runtime-model cases and both lint variants with 0 errors
(51/50 warnings). The existing dependency policy passes 495 components/1071 artifacts.
`testDebugUnitTest lint lintRelease assembleDebug assembleDebugAndroidTest`
complete with strict dependency verification, original debug signing, all 4 ABIs,
32 native libraries, aligned uncompressed DEX, 16KiB 64-bit ELF/RELRO alignment,
the native-extraction manifest and actual SDK `zipalign` checks.

Three private-checker failures remain recorded: the first incorrectly required
ZIP offsets for compressed libraries; the second expected a hexadecimal aapt2
boolean where the observed manifest prints `true`; the third assumed RELRO must
end exactly at a LOAD boundary, excluding safe padding. Two real-header cases
and two unsafe-writable-tail counterexamples verify the corrected range model.
The final checker keeps ELF requirements and verifies extraction/SDK alignment according to
[Android's packaging guidance](https://developer.android.com/guide/practices/page-sizes).
No app/build configuration was changed to pass these checks.

Frozen source digest: `c57826be4a550ffd8a5902eeb3d6e5e345a294177613f54508e7fae016f6d854`.
The reviewed code input hashes are:

| Input | SHA-256 |
| --- | --- |
| Milestone03Test.kt | `af99dfec1d0dcfc8d9bfdf0adbd76f71e9d3432b4448ca94818284bdb8beb9f8` |
| compatibility_probe_owner_runtime.py | `b32429c1be85225a455dc0ebbef40877de73cd887741acd1e668f68da865a2c2` |

| Artifact | SHA-256 |
| --- | --- |
| Debug APK | `6161b6092d20e63eb3f0724b1405754def69872b7349d778a8f1580aab5e9442` |
| Test companion | `a9e4f284e94e179fd5a60f4940cbb46caa8bcb324e2260832fa3ec1339761526` |

The published beta.9 production APK/source 0ee08e3 is unchanged. The preceding
[a2a3dd2 hosted Android run](https://github.com/DionCroft/Signal_Grove/actions/runs/43420802803)
is green; its raw partition union has not been independently checked here.
Fresh clean-head standard CI for this diagnostic chunk remains required.
Physical production upgrade/live/offline acceptance is pending a connected
phone. No physical data, worker receipt, model, credential or live service was
changed. Project/worker, automation/integration and every original audit row
remain open until separately evidenced.

Private E-drive records: original-shard1296, baseline1295, diagnostic-demo1310,
resumed-diagnostics-candidate1303/artifact-proof1309, diagnostics-policy1305,
owner-trace-validation1304 and upstream-profile-review1307/review1308. They retain
the raw failures, exact input hashes, receipts and source-bound scope.
