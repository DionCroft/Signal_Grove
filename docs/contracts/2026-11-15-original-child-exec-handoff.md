# Original-child exec handoff: verified isolated implementation

15 November 2026. The same full goal remains active. This change qualifies the
native backup spawn boundary inside the already approved isolated staging scope.

## Observed mechanism and source basis

The Pi reports `6.18.60+rpt-rpi-v8`. Its version's kernel source applies
`do_close_on_exec()` before installing the new executable memory image. The
upstream backport moved descriptor closure to avoid a filesystem/exec-lock
deadlock. CPython's subprocess implementation uses EOF on its close-on-exec
error pipe as the normal spawn notification. Together these permit a window
in which the parent has returned from spawn while procfs still shows the
executor image. This is a source-based explanation supported by actual native
handoffs below; it does not reconstruct the exact uninstrumented old failure.
[Pinned 6.18.60 source](https://github.com/raspberrypi/linux/blob/f45ed76d6791cf72ff1265ee93511175bbbc2f18/fs/exec.c),
[backport explanation](https://github.com/raspberrypi/linux/commit/19ddef61ee0a49782a60f8f497e50597ad5de2da),
[CPython reference implementation](https://github.com/python/cpython/blob/v3.14.0/Lib/subprocess.py).
The reference is not a claim that every enrolled interpreter is version 3.14.

## Native boundary

The actor retains its exact Popen handle. Only its original child's unchanged
PID/start ticks/boot, direct parent, effective UID and exact finite cgroup may
remain pending while the native image is the current verified executor. It
accepts only the requested executable path and device/inode. A different image,
owner, parent, user, cgroup, missing sample or exited child holds immediately.
Input starts only after the positive requested-image observation returns.

The acceptance window is the earlier of the original approval deadline and
500ms, with at most 64 samples. This bounds acceptance; native reads and receipt
I/O retain their own finite-call behavior. Late observations or receipt writes
cannot authorize input. `child_exec_observing` records the original identity,
reviewed program hash, bounds and one dispatch; `child_owned` and held outcomes
record observation counts. The kernel image observation is not a stage-complete
or fleet-switch permission. The existing claimed actor/journal/approval and
program/source bindings remain required.

## Evidence and limits

The backup actor now observes the **same original child** through the known
executor-to-reviewed-program transition. It requires matching incarnation,
parent, UID, executable path/inode and cgroup before input, within the approval
deadline and a 500ms/64-sample acceptance bound. Receipt-write expiry also holds.
The original spawn is retained throughout; wrong images, exited children,
changed owners and missing evidence remain unknown without redispatch.

Final checks: **104 native methods/no skips** and **824 Windows passes in 1,078
cases, with 254 platform skips**. All **600 fresh decryptor starts** complete;
five require a second native observation of the unchanged incarnation. Five
focused receipt scenarios verify successful handoff, timeout, wrong image, exit
and receipt-write expiry. The clock/procfs stay real; one negative test injects
receipt latency. A fresh actual SDK/age/loopback stage independently verifies
**12 archive members, two committed WAL rows, 256 SDK module origins and 5,033
dependency files**, exact bytes, retained source and removed verified plaintext.

The Pi's kernel ordering and these native handoffs support the startup-window
diagnosis. The original failure lacked image details, so its exact historical
cause remains unproven; its failed unit/receipts and two reader failures stay.
All inspected fixture owners/units have ended. Previous source `51fb573` passes
**1,074 hosted Linux methods/no skips**; current-source hosted checks remain
separate. Source8d retains its independently verified **1,142 API26 / 1,143 API37**
gate. Source09306's standard Android jobs now report success; raw/APK review is
pending. All original upstream, project/worker/integration, phone and release
requirements remain. Live services and beta.9 are unchanged.
[Exact source, evidence and limits](2026-11-15-backup-child-observation.md).

The five native scenarios use actual Linux children and actual owned receipts.
The transition fixtures wait for their own receipt event before executing the
requested tool or a deliberately wrong image; they assert that no stdin arrived
early. The exit case explicitly waits for status 7. The timeout never accepts
the executor as the requested tool. The expiry case delays a real receipt write
with a real clock; even a positive image sample then stays held without input.

The 600-start diagnostic uses generated keys/data and in-memory diagnostic
receipt carriers; its five two-sample handoffs keep PID/start/boot unchanged.
The independent real SDK stage separately qualifies actual disk receipts,
snapshot selection, age round trip, loopback pair upload/download, ordinary
source preservation, SQLite integrity and two committed WAL rows. All 23,628
SDK files and 5,033 dependency code files remain bound. Non-backup prerequisite
gates are synthetic. This does not accept real GitHub/live-home/production-key,
external-provider completeness, operational restore or the full executor.

Earlier 200/400/400 diagnostic runs did not reproduce a hold and retain their
own source scopes. The earlier observer's 3/100 holds remain preserved. The
first new observer's separate 600 starts included three handoffs; the final
source's six hundred include five. Do not combine these into inflated final-
source coverage or infer that every possible native startup failure is solved.

Two private reader attempts assumed the exiting child's hold sample already
contained no owner and status 7. The actual sample retained the last valid owner
while executable access became unavailable, before poll observed completion.
The corrected reader keeps that sample inconclusive and verifies the exact test's
subsequent wait/status assertion, successful raw result, ended original native
owner and empty stopped unit. It accepts no foreign identity and reruns no test
or held operation. Both reader failures are retained.

The next action is to **implement journal-bound native gateway drain and qualify
normal GatewayRunner**, then compose the unified executor with all-profile/
worker admission, reload/authenticated health and bounded runtime-only recovery.
Reconcile source09306's completed Android artifacts and continue the recorded
37 stable / 171 inspected-main findings and original UI failure investigations.
Both canonical PM contexts, seven real modes, policy/launcher references, real
private GitHub/live-home backup and final live approval remain unactivated.
Every original project, integration, local-AI, phone and signed-release gate stays.

## Reproduction and evidence digests

Portable discovery: `python -B -m unittest discover -s scripts/tests -v`.
Controlled Linux coverage: `scripts/ci_native_policy.py --native-only` in its
required exact finite user-manager fixture. Actual SDK qualification uses
`scripts/native/backup_transaction_fixture.py` with verified immutable source,
dependencies, tools and generated state. The private probe controllers, exact
native receipts and raw logs are retained outside Git.

Consolidated review SHA-256: `2401d108e250ce5f8d3b9c48304e73cc72486a333ea776b2a780f42ea12bff60`.

| Evidence | SHA-256 |
| --- | --- |
| `native-exec-review2272/review.json` | `a9e75b33356b7adeb8822b188a64cd5698c30b1dc15f9be32cecd2e4b667ea16` |
| `exec-probe-review2267/review.json` | `84431e561f62caf11bfa155507a161c559610eb97b1adc91771125931024419c` |
| `sdk-exec-review2271/review.json` | `347c162e74ec5881c668298cb8d6e6cceb0985eec20aa6ace74a6ba9b35bd4d0` |
| `inventory-windows2266.log` | `e961b4e95d560322d5011e1b962744290641e5d26f63e1e24a2a915b838fe92d` |
| `hosted51fb2262/review.json` | `1f1906818b2c23671f6cc40788c61901be949d1f12ec034d2b8c1bf84ffb0a9f` |
| `native-failure2223/review.json` | `57aa20b66a8e166ace49d9cbfb31fccb1c5b46bf137c2e580a84a6734fc11e3a` |
| `child-probe-review2236/review.json` | `4fdfe241de1f9f38f7950f3470ed2cee81477caa601008e3b20fdc780f4b3509` |
| `sdk-exec-review2261/review.json` | `1b8bfd916ea2e78d7934be32ea283cbe663cdf633ea832a820709c0b714ad1bd` |
| `exec-probe-review2258/review.json` | `ee0ac4bc498bb4dfc44928d5aa4b2be08d627200a61097ca1d18c14463030970` |
| `standard8d2188/review.json` | `1ec4c600690ce79b3e1b6657520b46cda0d7c296fd269a4993b0eefd0fa37df7` |
| `rpi61860-exec2259.txt` | `4ebb5ce6166ed256cb6d0682fece5a2f2fd04db5688c797293ba19a2f4cae5b1` |
| `rpi-exec-change2259.json` | `86491aee8dd775af229d62e10393359db1690b81dda2dff52d749736eb4d18fa` |
| `cpython-popen2259.txt` | `cc1d198b8f853479d9ee23fe8d4bcb9cb57ca6764789db4073e5caee0cbf7310` |
| `native-exec-review2269/stderr.private.log` | `e08e7141585acfa442ed2274065f26d9b5ac324cfd7a623499816ab1957c0746` |
| `native-exec-review2270/stderr.private.log` | `e08e7141585acfa442ed2274065f26d9b5ac324cfd7a623499816ab1957c0746` |

| Final source | SHA-256 |
| --- | --- |
| `scripts/native_backup_transaction.py` | `80e9bee05f2a2ccaecf07e699bc8590c9ca61fc82085c72fb02c99163956f312` |
| `scripts/tests/test_native_backup_transaction.py` | `e7312030dc059ea11abd58351402c00572f5728d4e842153ae4989e20d44de26` |

No live runtime, mode, policy, grant, model, credential or launcher is changed.
The published APK and the full goal retain their previous scope.
