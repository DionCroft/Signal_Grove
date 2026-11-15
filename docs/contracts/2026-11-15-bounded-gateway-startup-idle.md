# Bounded startup-idle and two-mode gateway qualification

15 November 2026. The full goal remains active. This is approved isolated SDK
qualification with generated identities/data, not a live cutover or release.

## Behavior and deadline contract

The fresh-home fixture previously held on the first busy startup response even
when the SDK had advertised ready. It now observes fresh known-busy replies
within a separate five-second startup-idle window. Each reply must retain the
original native owner/home, running lifecycle, epoch-zero unfrozen ledger and
registered-only coverage. Unknown/foreign/frozen state stops immediately. An
idle response is required before the one SDK freeze helper can be dispatched.

The freeze and marker are dispatched once, followed by the existing separate
five-second drain window. The same original owner, frozen operation/epoch and
fresh complete counters must be verified on every reply; the final state must
be draining and idle. No counters or work families are excluded. A busy reply
does not retry a mutation, refresh a marker, thaw a fence or renew a deadline.

`wait_for_status` checks finite nondecreasing monotonic time before and after
each fresh observation and predicate. Windows cannot exceed five seconds.
Recording time is included; an already-expired window dispatches nothing, and
idle at or after expiry cannot pass. The receipt records the exact checked
acceptance instant. Request start/finish monotonic stamps bind the sampled
replies to each phase. Readiness also rejects replies after its original bound.

The fixture requires epoch zero because it creates a new ledger. That is not
a production restriction: a future native actor must review the actual fence
preimage and support valid released nonzero epochs without adopting a frozen
foreign operation. The SDK's idempotent same-owner freeze is not permission to
replay an unknown action.

## Final-source native evidence

Final-source fresh-home trials now pass startup-idle, actual SDK admission
freeze/refusal, external drain and normal native shutdown in **both profiler
modes**. Startup-idle has its own five-second window; drain keeps its original
five-second window. Neither window renews, masks work nor accepts late/unknown
observations. The SDK facade and real GatewayRunner run without SDK patches;
the CLI `main` entrypoint is not exercised.

Without profiling, four busy startup and three busy drain replies precede idle
observations in 1.971 s and 1.368 s respectively. With profiling, startup is idle
in 0.178 s and four busy drain replies precede idle in 3.439 s. All registered
counts are zero at each accepted gate. These timings describe two generated
fixtures; they do not establish the earlier timeout's cause or fleet readiness.

Independent readback verifies 21 decoded replies, 15 status projections, seven
original profiled wire replies, both frozen ledgers/markers, three ended native
groups, all 23,628 SDK files and 5,033 dependency files. The SDK source-origin
union is 1,139 and its native stop budget remains 70 seconds. Live source/facts,
existing modes, services and every previous held outcome are preserved.

Current Windows checks pass **864 of 1,118 cases, with 254 platform skips**.
All **35 focused methods** pass on Pi without skips, including twelve new window
regressions. Parent source `d1f8a75` independently passes **1,106 hosted Linux
methods/no skips**; its 37 stable / 172 recorded-main semantic findings remain
unaccepted. The original-journal drain actor, all-profile/worker admission,
unified executor and full audit/release gates remain open. Published beta.9 and
live services are unchanged. The full goal stays active.

| Mode | Startup samples / busy | Startup seconds | Drain samples / busy | Drain seconds |
| --- | --- | --- | --- | --- |
| No fixture profiler | 5 / 4 | 1.970897 | 4 / 3 | 1.367557 |
| Fixture profiler | 1 / 0 | 0.177740 | 5 / 4 | 3.439245 |

Both modes use the same final executor source and staged maintained SDK in
different new homes/operations, sequentially under one finite controller.
Four `starting` identify replies precede ready in the unprofiled run; the
profiled run observes ready after its traced publication. There are 14 decoded
replies/9 statuses without profiling and 7 replies/6 statuses with profiling.
Only the seven profiled replies have independently retained original wire
bytes; all status projections are independently recomputed. The unprofiled
mode installs no fixture profiler, verifies unset entry/exit hooks, and produces
no lifecycle/ready-source/wire/stack profiler evidence.

Both real SDK ledgers retain epoch 1 with their exact generated transaction
owner, one finished seed turn and no tools/operations. New generated turn/tool
admission is refused. Their boot/PID1-bound drain markers remain after stop,
owner-private, with exact payloads and inode metadata. The SDK marker writer
uses replacement; exclusive production marker publication is **not** accepted.
No SDK functions are replaced; the actual `gateway.run.start_gateway` facade
and GatewayRunner run, while the CLI `main` entrypoint is not exercised.

Independent native/SQLite/raw readback confirms both SDK owners and the
controller group ended without restart, markers/fences remained and native stop
completed normally. Full source, dependency, PM-facts and live incarnation/mode
conservation passes. One protected root SSH configuration remains unreadable;
its opaque metadata is conserved without permission changes or a complete SSH
posture claim. Earlier failed/held trials remain unmodified; neither timing nor
later success retrospectively proves their original cause or outcome.

## Coverage and parent CI

All 1,118 current Windows cases complete: 864 positive and 254 platform skips.
The 35 focused methods run positively on Pi without skips. Twelve new
regressions cover known-busy to fresh idle, perpetual busy, pre-dispatch expiry,
exact/late expiry, delayed predicate/receipt work, unknown single dispatch,
malformed/backwards clocks, widened windows, non-boolean verdicts and foreign
startup states. Existing owner, profile, privacy and deadline boundaries remain.

Parent source `d1f8a75a41ebdc237b8cfa0c26692390b2945a4a` independently passes
all 1,106 hosted Linux methods without skips, exact native/Windows inventory,
all prior 1,093 plus 13 privacy/observer methods, raw logs and native unit closure.
Run [43699664343](https://github.com/DionCroft/Signal_Grove/actions/runs/43699664343),
parser job 132767803589, artifact 14155544029. It is parent-source coverage.

The same run retains semantic review for 37 used handlers in stable
`v0.23.1` / `3b421578588243f920886e6fe79a6faa87ea026e` and 172 at recorded main
`fd8a6c41ace59b476193c61002ce6a86d667b18a`. No full compatibility is accepted.
The staged maintained derivative is not claimed to be pristine upstream.
Android/Kotlin sources and beta.9 are unchanged; prior standard Android gates
retain their exact-source scope. Original API26 causes, twelve external-model
gates and final physical-phone/original-key production acceptance remain open.

## Exact source and evidence

| Final source file | SHA-256 |
| --- | --- |
| `scripts/native/sdk_gateway_lifecycle_fixture.py` | `942fec6abd8f8eb398efded8802d5154ba2b531bbd031eb0cc68711d997c8976` |
| `scripts/native/sdk_work_diagnostics.py` | `c2c7df4fba9e47796aa93f7ec26bba2cb288c642b49828faedf660813f47d1c9` |
| `scripts/tests/test_sdk_gateway_observation_windows.py` | `7754e5c5976e84e94c6e8a41a55f56f6beff98777bf3378315f4e6b9d68ce42e` |

Consolidated private review SHA-256:
`b49d68c3d1b3f137068f60dc97ca440604d57972d3830af00e330e71a920c523`.
Private bundles stay on the selected temporary drive and are not committed.

| Private evidence identifier | SHA-256 |
| --- | --- |
| `gateway-windows-review2362/review.json` | `aad9e3f4c4724146ac2eb520097e6c8320352c3c6ebdef140bca9fd6fab9111b` |
| `hostedd1f82359/review.json` | `8d010d84b312ac87c56bf88ff9a55b198f2a4f3cb4c2d3d447e73a0d9cbf41d6` |
| `hostedd1f82359/upstream-review.json` | `bc1172123630d544f778601e08dc00993b3a3d66f56d85c29ed2d88cad1ccba5` |
| `inventory-windows2358.log` | `0f7465dd0b6f2adc77d7242e582353a9324fd147f434f9a5049046cbdf8aae9b` |
| `normal-gateway-unprofiled2350/review.json` | `d062a77ae8f691193cb76e7e6a58960517560d8739ce6565dbeca7e53db99ea2` |
| `normal-gateway-hold-review2331/review.json` | `7f1296c1e8e16e8a7e649d011171ae2ba3b34f10a732f6fb1ff71d3598962291` |

## Next native actor and retained gates

Implement and qualify the original-journal native gateway-drain actor using
reviewed source/home/manager identities, the original approval and dispatch
claim, one SDK freeze and exclusive non-overwriting marker publication. Record
the reviewed fence preimage, including valid released nonzero epochs; never
adopt an existing frozen owner or treat SDK idempotence as retry permission.
Use fresh owner-bound status reads with fixed expiry and preserved unknowns.
Then qualify all-profile/worker admission, unified executor, reload/authenticated
health and bounded runtime-only recovery before preparing final live approval.
Canonical PM contexts, seven real modes, policy/launcher changes and real
private GitHub/live-home backup remain unactivated. Every original audit,
local-model, phone and original-key release requirement remains required.

The actor must bind the original live journal owner, fixed stage/prefix,
approval deadline, one dispatch claim, reviewed source/PM executor, home and
native unit configuration. Prepare immutable inventories before sensitive
dispatch and recheck them afterward. Exclusively publish the exact supported
drain payload under the reviewed parent without overwriting or adopting an
arrival. Persist intent/effect/unknown receipts; never automatically release,
cancel, restore data or replay work. Exercise real SDK mutation and hostile
preimage/expiry/native-owner/journal scenarios in owned fixtures before any live
packet can use it. These are next implementation requirements, not completed
production effects. All-profile/worker and whole-fleet admission remain separate
from the verified single generated home.
