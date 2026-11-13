# Fresh workspace readiness before one read admission

13 November 2026. The full audit/on-device AI goal and all four
[priorities](../GOAL.md) remain active. This chunk fixes a measured read-client
readiness defect, proves it through the existing Pi/Windows path using a
disposable candidate client and reconciles a complete Android fixture gate.
No installed worker, gateway, models, credentials, jobs or release is changed.

## The Pi refusal is now observed at its actual boundary

The previous [Pi/local transport checkpoint](2026-11-13-pipe-availability.md)
kept the Pi UNAVAILABLE separate from the local ERROR_PIPE_BUSY race. A fresh
preflight again verifies the maintained worker source, protected operator grant,
active owner, registered validation root, unchanged 302-byte text fixture and
eight completed ordinary-dispatch receipts. No worker call is sent by preflight.

One new bounded, RAM-only Pi trial then uses a disposable Windows diagnostic
client. It preserves the original RPC and native connection behavior, captures
only safe state/error metadata on the Windows owner and stops at the first
refusal. The three exact client records show:

| Call | Consumed projection | Native connection entered | Result |
| --- | --- | --- | --- |
| status | ready, age 0.565 s | no | status |
| prepare | ready, age 0.975 s | yes | reply |
| list | starting, no endpoint, age 0.618 s | no | UNAVAILABLE, unsubmitted |

This directly establishes the new trial's refusal before native connection.
A normal host tick captured a non-listening state around the earlier exchange;
that fresh projection is still starting when the next client reads it. A
previous ready observation does not reserve the next connection. Historical
trials without this trace remain failed and are not retroactively relabelled.
Instrumentation can affect timing; the state actually consumed here is measured.

## One admission budget, with no frame replay

The new optional read_host.call_current_projected helper takes a trusted protected
projection loader, rather than a phone path or a cached ready snapshot. The
loader must revalidate caller/operator policy before each metadata read. Only a
fresh starting state can wait briefly for another observation. Held/closed,
stale/future/invalid data, policy/owner change and a newly ready different worker
refuse without submitting the frame. No state is coerced to ready and no old
endpoint or binding is substituted.

One original absolute deadline covers readiness observation, the single native
availability wait/open attempt, identity checks, request/reply and ACK. The
optional call_once deadline may shorten but cannot extend its finite timeout.
Expiry during identity checking prevents the first request write. Native
admission occurs outside pre-submission exception mapping: a failed write or
lost reply keeps its exact conservative submission state. A native error never
triggers a second projection read, connection attempt or frame submission.

Waiting reads only trusted local control metadata; it is not a retry of a worker
operation. The existing interactive broker still rechecks authoritative registry,
actual root/entry identity and the enrolled scope before every admitted frame.
An ordinary SSH client's filesystem access is not substituted for that owner.
No protocol field, HTTP route, port, listener, registration or credential is added.
The original one-snapshot call_projected API keeps its existing strict refusal.

The deadline cannot force an unresponsive OS filesystem operation to finish.
After a late loader returns, expiry still refuses before request submission.
Unknown outcomes retain the existing no-replay recovery boundary.

## Frozen tests and an actual candidate-only Pi read

Thirteen new portable cases cover starting/ready/held/closed states, stale data,
revocation, changed instances, loader failures, pre-admission expiry and exact
post-admission uncertainty. Four of those cases specifically verify absolute
budget bounds and no write after expiry. Two new Windows native cases exercise
a fresh starting projection before one actual broker admission and held-state
refusal while a real pipe exists. All original native tests remain intact.

The frozen candidate passes all 469 portable policy, 15 runtime-model and 71
declared real Windows native cases. The native runner records distinct positives,
zero failures/errors/skips and unchanged source. All executable tracked inputs
are frozen during the checks; no fresh Android build is claimed for these
Python-only component changes.

Exactly seven tested module bytes are staged into disposable owner scratch.
A separate one-shot client substitutes the helper only in its own process,
with fresh protected-policy validation and the existing fixed RPC validation.
No NAS access is attempted from the SSH client's account: authoritative registry
and disk checks stay with the existing interactive broker.

One fresh Pi trial completes exactly five private worker calls: status, prepare,
list, preview and the explicit duplicate-frame refusal check. Listing returns
seven entries; preview returns the unchanged 302-byte fixture/hash. Three new
unique request IDs are used. The already-completed prepare frame is deliberately
re-presented once to prove DUPLICATE refusal without cached content; no application
or unknown job is replayed. Foreign subject and profile requests are denied
before any SSH call.

The initial candidate's five safe client traces are complete. Its last call
consumes six starting projections, then one ready projection, before duplicate
refusal. Precommit review then catches one extra CRLF blank line at EOF; removing
that line leaves the entire AST and node locations identical. A preliminary
newline-assumption error changes no source and sends no request; inspection
corrects it. The earlier passing package and trial remain immutable.

Fresh full 469/15/71 checks validate the final bytes. A separate new five-call
trial with fresh IDs passes against those exact seven module bytes. This time
list consumes eight starting projections, then one ready projection, before its
single successful admission. That directly exercises the previously failing
operation's metadata wait path. Both trials retain their own complete traces,
receipts and source manifests; they are not merged into one synthetic trial.
The earlier failed trial remains retained. One other preparation typo stopped
before a driver was written or SSH launched and was corrected before testing.

All eight ordinary-dispatch receipt rows and their hash, installed worker sources,
protected grant, registry, previewed fixture and active owner are unchanged.
Pi authority/UID and install-stamp checks pass; gateway/serve properties and stamp
are unchanged. The two public coordinator modules execute only in Pi RAM; the
script writes no Pi files. Diagnostics and source copies remain on the owner.

This proves a candidate administrative Pi-to-existing-worker read path. Its
explicit administration context/frozen preflight grant does not establish native
HTTP/PKCE/WebSocket caller binding, a deployed operator policy, Android workspace
adoption/browsing, actual NAS full-ID access or Surface behavior. No component
is installed or restarted here. Production integration remains a separate
guarded maintenance stage, with its applicable approval/backup/idle checks.

## Complete c814 standard Android gate, not final release acceptance

The [c814d743 CI cohort](https://github.com/DionCroft/Signal_Grove/actions/runs/43491494091)
is terminal success. All four exact raw partitions are independently verified
against complete inventories. They are disjoint within each API, use one exact
same-run APK pair, omit no declared standard case and reproduce the hosted
aggregate JSON byte-for-data. Actual same-run build APK bytes match that pair.

| API | Shard positives | Complete standard union |
| --- | --- | ---: |
| 26 | 540 + 601 | 1,141 |
| 37 | 541 + 601 | 1,142 |

Original clarification and scroll methods pass; actual-keyboard Apply/Cancel
route traces complete on the corrected unique target. The original API26 causes,
earlier delayed native-tap warnings and failed cohorts remain preserved rather
than claimed fixed by a later green run. Twelve external-model methods have
their own gate and gain no acceptance from this standard fixture suite.

The app APK is 92,606,086 bytes, SHA-256
ab6ce44bd1f2c3f11acf5370430ee0ba8d12a4855b1c7ac0d120848f3ab070fd;
the test APK is 8,028,751 bytes, SHA-256
35ca61c00e4e34c58e09bf0252256efac5f08d15e0a9f3cf9ece19910b0d876b.
These are debug CI artifacts, not the public production update. The aggregate
report SHA-256 is 43e01568020d01ae37c0ca892940db6bd535d4fb8f7ad1c4a3058e2eeddd5933.
Current Android inputs match this tested cohort, but final-current-head CI and
physical production upgrade/live/offline acceptance still need their own proof.

| Private evidence | SHA-256 |
| --- | --- |
| Actual unmodified-client readiness boundary | 7b2f2e60e638a4d21a046a043b8ee40d32c1349a49719343a4724c8427497c7c |
| Final-byte five-call candidate Pi trial | 875b215afb4a06957a98c7f2fff820fb1f5d74cf4b2fcf4495f780677ed17b32 |
| Five complete final-byte candidate client traces | 98694397d808269c6e4e6cb904f5bfb7333fbc8e08d573bb9f518a3aca8bbb5b |
| Native 71-case result/source set | 6e5bf3f17a633ab92f6826835850cc599950bb2d630493c18909e3ba9841189e |
| Full 469-case policy log | 61549ce9a35fd4798ca433e122229224eb0244b1da4a88ce7201ff9aadfac323 |
| Four raw partitions, summary and actual APK review | 91de98d1caaa6c27f87bc44b329179c370f5a4b74d265fd2d67f8ceb31f760e1 |

## Remaining scope

Prepare the reviewed component integration/cutover without overwriting maintained
worker fixes or changing grants/models/unknown work, then complete native caller/
profile/operator and real chat/workspace/file/worker acceptance. The full original
automation/integration, security/device and release ledger remains. Upstream
stable42/inspected-main6da124 findings and the separate pristine owner-runtime
shutdown warning stay open; the heartbeat candidate remains undeployed.

No owner phone is currently attached. Public beta.9/source0ee and stable1.0.0,
package/signing identity and owner data remain unchanged. The written setup guide
now correctly labels beta.9's implemented experimental phone AI; its separate
local history does not replace remote Hermes identity, tools or project authority.
