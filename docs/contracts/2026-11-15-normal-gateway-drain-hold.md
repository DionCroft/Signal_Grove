# Actual gateway lifecycle and held quiescence

15 November 2026. The original full audit goal remains active. This checkpoint
adds a reusable isolated integration fixture and records a real acceptance hold.
It does not deploy a gateway, change existing policy or complete the drain stage.

## What is exercised

The new fixture runs the maintained SDK's actual `gateway.run.start_gateway`
facade and real GatewayRunner in a fresh home and separate finite native unit.
It verifies ready state, the real SDK ledger freeze, refusal of new generated
turn/tool admission, an exact boot/PID1 drain marker and normal native SIGINT
shutdown. No SDK function is replaced. The CLI `main` entrypoint is not exercised.

**Quiescence remains held.** Of seven fresh status samples, the initial one is
idle and six after the freeze report executor/periodic jobs active. Ledger work
counts stay zero. The five-second observation window expires without an idle
drain observation; the gate stays closed. There is no counter exclusion, marker
refresh, thaw or replay. The SDK process later exits normally; its controller's
failed acceptance outcome and all persistent fixture state remain preserved.

Independent raw/native/SQLite readback verifies eight wire replies, 1,139 loaded
SDK source origins, all 23,628 SDK files and the 5,033-file dependency baseline.
The SDK's derived native stop budget is 70 seconds. One root configuration file
is explicitly unreadable; its permissions are preserved and no complete SSH
posture assessment is claimed. All inspected trial owners/units have ended.

Current Windows checks pass **839 of 1,093 cases, with 254 platform skips**.
All **ten new boundary methods** also pass on Pi without skips. Parent source
`75d0167` independently passes **1,083 hosted Linux policy methods/no skips**,
including the previous 1,078 and five owner regressions. Current-source hosted
checks remain separate. The original UI, upstream, project/worker/integration,
phone/local-model and signed-production release requirements all remain.
Live Pi services and published beta.9 are unchanged. The full goal stays active.
[Exact source, evidence and limits](2026-11-15-gateway-fence-owner-and-android-review.md).

The fixture uses the real public gateway facade, constructor, startup watchers,
session authority, Unix listener, SDK Ledger methods and external marker watcher.
Its generated control-plane project/session metadata is intentionally distinct
from user projects. A seed turn is admitted and finished through the SDK. Freeze
then refuses both a new direct turn and tool, without adding active receipts.
The marker carries the same operation as the fence and the actual native boot
UUID plus PID1 start tick. Zero start ticks are valid on the observed Pi; they
are compared with procfs, not replaced with a synthetic identity.

There are no messaging credentials or submitted model requests. Model/fallback
and warmup settings are explicit in the generated configuration. Source review
and a cooperative Python audit fence constrain writes to the fresh home/socket
directory and commands to exact read-only native probes and the reviewed bounded
SDK diagnostic. The diagnostic lists process names/resources rather than argv.
This is not an OS sandbox or proof against a hostile same-user process.

The real source's host lock override isolates rendezvous in the generated home.
The socket uses Hermes's private per-home hash directory under the normal user
runtime; this preserves supported user-manager timing queries. The SDK-derived
70-second stop budget is observed in the exact unit. The original PID/start/boot,
argv, executable and cgroup remain bound before each read and stop. The two-second
control deadline is unchanged. Each supplied connection performs one request.

## Positive evidence and the held gate

| Requirement in this fixture | Result |
| --- | --- |
| Actual GatewayRunner readiness and native identify/status | Observed |
| Real existing generated ledger freeze, owner and epoch | Observed |
| New generated direct turn and tool while frozen | Both refused |
| Native epoch/principal-bound external marker | Observed |
| All registered live work idle within five seconds | **Held** |
| Normal SIGINT/native unit stop and ended original owner | Verified |
| Production journal actor, all profiles/workers and full cutover | Open |

All eight actual server replies are independently decoded and compared with
client records and request identities. Seven are status samples: one initial
idle state, then six busy states with the same frozen owner/epoch. After the
marker the observed nonzero pairs `(executor_jobs, periodic_jobs)` are
`(1,1), (1,1), (2,2), (1,2), (1,1), (1,1)`. Every registered ledger count is zero.
All responses stay within the existing control bound. These observations do not
prove the jobs' kinds, perpetual activity or behavior without instrumentation.

The first valid busy state is not an unknown response. The fixture permits only
fresh reads of the same owner while that known work settles, within its original
five-second window. Missing/foreign/unfrozen/unknown states hold immediately;
late positive observations cannot pass. No mutation is retried. The final
observation is still busy and the acceptance process exits with failure.

Failure cleanup stops only the exact owned fixture unit. The SDK gateway itself
returns true and exits normally under SIGINT; its native unit is inactive with
zero restarts and an empty cgroup. The failed controller remains failed. SQLite
integrity is `ok`: one completed seed admission, no tools/operations, one frozen
transaction, the original approved generated revision and session binding. The
marker and frozen row remain unchanged after stop. No automatic thaw/cancel or
restoration is performed. These are fixture outcomes, not a live recovery claim.

## Preserved investigation history

- The original Windows run has two errors because its Python lacks `AF_UNIX`.
  The portable check now tests Unix path policy separately and guards capability
  lookup; the failed log remains. No assertion is removed or skipped to pass.
- The first native attempt reaches a control-response hold and its short fixture
  stop limit expires. Its failed controller/unit remain. Source/guard review
  identifies blocked self-liveness and expected read-only startup probes.
- A second attempt stops before seed/gateway dispatch at an unreadable root-owned
  configuration file. It now stays explicitly opaque; no privilege/mode change.
- A third attempt reaches real ready/idle status and commits the SDK freeze,
  then rejects this Pi's native PID1 tick zero. Its frozen row stays. The corrected
  validator requires a canonical UUID and unsigned tick value with fresh procfs
  equality. Its private-runtime timing-probe issue is also preserved.
- A fourth attempt completes the freeze and marker but correctly observes busy
  work. Its SDK native stop is normal. The final fixture adds bounded settling
  reads and still holds after six busy observations; it never labels them idle.

Every attempt uses a different fresh home/operation after native confirmation
that previous owners ended. Prior controllers, receipts and frozen/marker state
are not adopted, cleared, refreshed or replayed. No sixth runtime trial occurs
in this checkpoint.

## Source basis for the next investigation

The exact staged `GatewayAgentCacheMixin._persist_active_agents` writes the
aggregate active count; the external drain watcher calls it while draining.
`work_execution.work_phase` owns finite phases and delegates executor submissions
to the runner's tracked work. The housekeeping runner wraps chores in that phase.
The supported `active-work` observer reports active units but does not itself
classify all executor/periodic ownership. These are source navigation facts,
not an attribution of the six measured busy samples to a particular chore.
The existing status counters and conservative readiness semantics remain intact.

Identify the actual SDK work owners behind the executor/periodic counts during
external drain, including startup/housekeeping scheduling and any observation
effect. Obtain bounded source/stack ownership evidence and an uninstrumented
comparison; preserve the counters and every held receipt. Resolve and requalify
quiescence before composing the original-journal native drain actor. Then finish
all-profile/worker admission, the unified executor, reload/authenticated health
and bounded runtime-only recovery. Both canonical PM contexts, seven real modes,
policy/launcher references, real private GitHub/live-home backup and separate
final live approval remain unactivated. Every original acceptance priority stays.

Keep the full original API26 failure-cause, used/transitive compatibility and
original shutdown warning reviews, authenticated project/workspace/worker/NAS,
Kanban/jobs/rooms/grants/integrations/configuration/doctor/graph, external-model/
phone/lifecycle/audio/background/notification/accessibility/battery/thermal,
security/support/original-key production upgrade and exact-source release gates.
Earlier Android source `09306e6` retains its independently verified 1,011 JVM,
1,142 API26 and 1,143 API37 standard cases; this does not supply final-head phone
or release acceptance. Last reconciled upstream findings remain 37 stable /
172 inspected-main at their recorded sources. Live services and beta.9 stay as-is.

## Reproducibility and evidence

`native.sdk_gateway_lifecycle_fixture.run_trial` requires an independently
reviewed staged SDK, its selected PM Python and a fresh private fixture root.
Run it from its own finite native controller; it creates a separate bounded
gateway unit. `tests/test_sdk_gateway_lifecycle_fixture.py` provides ten portable
boundary cases. The full Windows suite uses ordinary unittest discovery. Native
qualification failure is expected while the measured quiescence hold remains;
do not turn that into a passing baseline or rerun an existing operation.

Parent source `75d01677d518ef5f3b275ad07a84c42084a021f4` has independently
reconciled [hosted policy run 43691020783](https://github.com/DionCroft/Signal_Grove/actions/runs/43691020783),
job `132741503598`, artifact `14152621519` SHA-256
`950214aaacd3e9d592976f3908d06b9773ce1c940f905069bca56fdedd036eae`.
All 1,083 methods positively run with no skips, preserving the old 1,078 and
adding exactly five owner cases. Current-source hosted coverage is a later gate.

Private raw wire, SQL, source maps, native unit snapshots and failed logs stay
on the selected temporary drive. Only their hashes/scopes are public here.
Consolidated review SHA-256: `01860b205bfff952f321a6861d61b56310faa528e960a281c07c4a73d0b39700`.

| Current source file | SHA-256 |
| --- | --- |
| `scripts/native/sdk_gateway_lifecycle_fixture.py` | `88a044820dd2448a8383a593210597e9eada3a024c1b57b23b29feb6c4eaec77` |
| `scripts/tests/test_sdk_gateway_lifecycle_fixture.py` | `83afbc285c0a0656297013a44ea6fdd12d9cdd336fc3221d52727d6249eecf1d` |

| Private evidence record | SHA-256 |
| --- | --- |
| `normal-gateway-source2334/manifest.json` | `4e481ac295c4b69443078a463d79ba5161a89153b53a4bb9661467209e940b52` |
| `normal-gateway-hold-review2331/review.json` | `7f1296c1e8e16e8a7e649d011171ae2ba3b34f10a732f6fb1ff71d3598962291` |
| `inventory-windows2327.log` | `f7ecdd2cba77ff2c183a30de7b069e8314502dcf7678d5e400b095eb88a4ce95` |
| `inventory-windows2306.log` | `08217a467df3bec4fa8d2324287290e6b54d933366ab2c22b4ae6558edccf34c` |
| `hosted75d2301/review.json` | `980cb24d6e4c6fa5de01e595f3b306dcdbc3d58ec15fd7631622820c8f0274c4` |
| `normal-gateway-failure2309/review.json` | `5796be2ce5ea8f4ccf9692dec721908688d66ec0364b88fb3c64b21798fb8945` |
| `normal-gateway-preflight2315/review.json` | `229ec11707b04f5e6aa16522273ee774c66eed13b1c5901104a63ba6dd28dc3c` |
| `normal-gateway-freeze-failure2319/review.json` | `62d0532f613b8f4ac575d7691c43d5daa5b3237d0382954b859a4f3e64892711` |
| `normal-gateway-marker-failure2324/review.json` | `de46fccddb5cf6519e011beaccc0a531502045f31c334445a2028e2f1d7f92cd` |
| `normal-gateway-settle-failure2329/review.json` | `9cfcd26eb2bdff3a7e847f6265e1b44e8c5195c0e63b549699d821701e75a099` |
| `gateway-native-lifecycle2300/review.json` | `0db5673e8af172f95868d5f0694bd8799d5f113a11730e770dc980f340c5f7a5` |
| `gateway-startup-probes2310/review.json` | `796e051adafd289995a50d4209c3387a9e49a2d9799c32723e9c6a897cab75be` |
| `gateway-periodic-inputs2330/review.json` | `cc6ac81e003f6a9e385bea328b71b1bd25b2ed4f77499e26ffa3cf531ad00874` |
| `persist-callers2332/review.json` | `dd200b6d545b2c3f18c9061950e593851b9b551bd147f7c7c3dbb56394a492b5` |
| `gateway-persist-source2333/review.json` | `c5244ae174cf5281b642d0bfa985e452e34eed017a5a1fb6c491ed7f0e1cc76a` |
