# Gateway work diagnostics and profiler-free startup hold

15 November 2026. Isolated generated-fixture qualification only. The full goal
is active. No live cutover, canonical PM provisioning, permission/ref/policy/grant
change, gateway restart, model change or real credential/backup action occurs.

## What changed

`sdk_work_diagnostics.py` records code locations, Python thread identifiers, coroutine
await chains and future states beside real known-busy SDK snapshots. Bounds are
64 threads, 24 frames, 64 tasks, 24 await nodes and 256 future rows; at most eight
observations, each at most 512 KiB, are retained privately. Registry locks have
a 20 ms observation budget; unavailable remains unknown. No task/thread names, arguments,
locals, results, exception text or object representations are persisted. Source
paths outside the reviewed SDK are redacted. No SDK functions are replaced and
the observations do not grant quiescence or cancel/change any work.

The lifecycle fixture has explicit `profile` and `none` modes. It refuses an
unknown mode or existing profiler; its own hooks are restored even on failure.
The `none` mode installs no fixture profiler, checks unset entry/exit state and
requires the absence of profiler-produced evidence. Readiness comes from native
identify replies; it is never treated as idle. One-shot requests retain the
existing native owner/peer, freshness, framing and two-second deadline checks.

## Two distinct real SDK results

The isolated gateway fixture now captures bounded code-only thread/coroutine
stacks and native future states beside actual busy replies. It serializes no
arguments, local values, task names or object representations. Five intermediate
profiled busy snapshots include delegation schema reconciliation, handoff store
reads and Kanban polling. These non-atomic observations correlate work; they do
not map each future to a callable or prove the old timeout's cause.

That intermediate profiled trial reaches zero registered counts within the
unchanged five-second drain window and stops normally. Its eight raw replies,
seven status samples and persistent frozen ledger/marker are independently
verified. It does not retrospectively pass the original held trial.

The final-source profiler-free comparison reports ready, then observes one
periodic job at its first status. The existing initial-idle gate holds **before
any freeze or drain**. Normal native stop succeeds; the ledger remains unfrozen
and no marker exists. This establishes that readiness is distinct from idle;
final-source startup-idle/drain acceptance remains open. No counters are removed.

Current Windows checks pass **852 of 1,106 cases, with 254 platform skips**.
All **23 focused boundary/privacy/observer methods** pass on Pi without skips.
Parent source `1a401da` independently passes **1,093 hosted Linux methods/no
skips**. Its upstream reports retain **37 stable / 172 inspected-main findings**
at recorded main `fd8a6c4`; compatibility remains unaccepted. Both isolated trials
preserve all 23,628 SDK files, 5,033 dependency files, native facts/modes and live
services; all inspected owners ended. Published beta.9 is unchanged. Every full
audit, phone/local-model and original-key release requirement remains active.

The profiled trial used the intermediate lifecycle hash
`cf9171dcb9490ad6964e2ea51c30ea94d80ceba4a044ae24e7e4af6aff817ab1`
and 18 positive focused Pi methods. Its five busy snapshots contain 16-17 threads
and 20 tasks, without thread/task omissions. Await chains show delegation sweep,
handoff store access and Kanban process-service work. Matching SDK source hashes
and code-line bounds are independently checked. Snapshot collection and work
registries are non-atomic, and instrumentation can affect scheduling. No causal
or one-to-one future attribution claim is made.

The final-source `none` trial has four `starting` identify replies, one `ready`
reply and one status: running, periodic=1, executor=0, every other live/ledger
count zero, epoch=0, owner=null, frozen=false. Its existing initial-idle gate
fails before dispatching the freeze helper. SDK stop returns normally; its
controller retains a failed acceptance outcome. Readback proves no freeze intent,
transaction or drain marker and an unchanged epoch-zero ledger. The six decoded
replies and projections are retained/recomputed; original wire bytes are not
independently retained for this uninstrumented trial. It is not a drain result.

Both native SDK owners and both controller groups ended without restart. The
SDK-derived native stop budget remains 70 seconds. All 23,628 SDK source files,
the 5,033-file dependency baseline, PM facts, existing modes and live native
incarnations are independently conserved. Loaded SDK-origin unions are 1,139 in
the intermediate profiled trial and 1,126 in the final unprofiled trial. One
root configuration file remains unreadable; opaque identity conservation is
verified without permission changes or a complete SSH-posture claim.

## Validation and retained limitations

Final source passes 1,106 Windows cases: 852 positive, 254 platform skips. Its
23 focused fixture/privacy/observer methods run positively on Pi with no skips.
The first full Windows invocation ran outside the repository root and failed
ten extension imports (1,007 discovered cases). Its raw failure is preserved;
the corrected-root invocation and final-source run have separate evidence.

Parent source `1a401da454a23bd01aabc15900b6ce0d4d704a04` has independently
verified complete hosted Linux coverage: 1,093 positive methods/no skips, exact
native/Windows union, all 1,083 prior methods plus ten new boundary methods,
real native group closure and bounded generated crypto fixtures. Run
[43697564501](https://github.com/DionCroft/Signal_Grove/actions/runs/43697564501),
parser job 132761366917, artifact 14155232631. This is parent-source evidence.

The same run's upstream job is held for semantic review: 37 used handlers at
stable `v0.23.1` / `3b421578588243f920886e6fe79a6faa87ea026e`, 172 at recorded
main `fd8a6c41ace59b476193c61002ce6a86d667b18a`. Neither report accepts full
compatibility. No original API26 failure cause, all-profile/worker admission,
original-journal drain actor, unified executor, production upgrade or release
gate is closed by this work. Android/Kotlin sources and published beta.9 are
unchanged. Prior exact-source Android evidence retains its own scope.

## Exact source and private evidence digests

The final source files have these SHA-256 values:

| File | SHA-256 |
| --- | --- |
| `scripts/native/sdk_gateway_lifecycle_fixture.py` | `ea506b54060332b2dd157b396ad4bf4fef75b9189928e3ce85cf8b31dfbe10f8` |
| `scripts/native/sdk_work_diagnostics.py` | `004162aee21dbcddefa72868a5bd1962f802fbaac8f6cab25698b8f36dbf5638` |
| `scripts/tests/test_sdk_gateway_lifecycle_fixture.py` | `7374d4830882c85ef1317c9b0220371d318982b86440bc0a7e1312e1dbd1aea7` |
| `scripts/tests/test_sdk_work_diagnostics.py` | `ce13c37fc67fc0ab7a5298196c7a683d21404c238349653d17ad845e81db0155` |

Consolidated private review SHA-256:
`64883ac65693f4e53c5fe123b8bd9611c74a049ad36086895e027f054090801c`.
Raw evidence stays on the selected temporary drive; no private bundle is committed.

| Private evidence identifier | SHA-256 |
| --- | --- |
| `normal-gateway-diagnostics2344/review.json` | `e6f923be6ad133d0f5fb554d0b7d834afed8c255f3e73ad50af2ef4baecdba2c` |
| `normal-gateway-unprofiled2350/review.json` | `d062a77ae8f691193cb76e7e6a58960517560d8739ce6565dbeca7e53db99ea2` |
| `hosted1a4012342/review.json` | `74dac96e37b01ec3cc60c1c48c5e4289c55faa411a61e1adc6bc83946668475a` |
| `hosted1a4012342/upstream-review.json` | `118c5c714d6295739a42708fe1036f64faeaac7d4cc5c3d12fbac0b10e67b2e8` |
| `inventory-windows2346.log` | `6ac6c7c043f50817f19a717db6c9eeecd11de8dfa08bbcdcc89169f8e6b57ddf` |
| `inventory-windows2341.log` | `3044332acbe284c06255a4e8915afcc81079e477f9d3b90d9cc9e7ea9749fae0` |
| `normal-gateway-hold-review2331/review.json` | `7f1296c1e8e16e8a7e649d011171ae2ba3b34f10a732f6fb1ff71d3598962291` |

## Exact next action

Add a separately bounded startup-idle observation phase which treats known
busy as busy, validates the original unfrozen owner/fence on every fresh reply
and holds on unknown, foreign state or expiry. Keep the existing five-second
drain deadline unchanged. Requalify final-source profiled and profiler-free
paths before composing the original-journal drain actor. Then finish all-profile/
worker admission, unified executor, reload/authenticated health and runtime-only
recovery. Canonical PM contexts, seven real modes, policy/launcher changes, real
private GitHub/live-home backup and final live approval remain unactivated.

Preserve every failure and unknown result. Never re-open an old operation to
refresh/thaw/cancel its fence or replay work. Complete all original audit,
local-model, final phone and original-key release acceptance before declaring
the full goal complete.
