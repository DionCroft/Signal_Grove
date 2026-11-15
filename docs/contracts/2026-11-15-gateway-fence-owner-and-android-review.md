# Gateway fence ownership and completed Android artifact review

15 November 2026. The full audit goal remains active. This is an isolated
read-path correction and evidence checkpoint, not a deployment or release.

## Problem and resulting behavior

The registered gateway projection dropped the SDK ledger's owner and accepted a
positive fence epoch with no owner. That could hide whether a later drain
observation belonged to the intended transaction. The reader now returns
`fence_owner` alongside the epoch/frozen fields and accepts only states the
reviewed SDK can produce:

- Initial: epoch zero, no owner and unfrozen.
- Previously acquired: positive epoch and a valid SDK transaction identifier,
  whether still frozen or explicitly released. Thaw retains owner and epoch.

Malformed ownership stays unknown. A valid foreign owner remains visible and
`fleet_ready_to_switch` stays false. This reader neither authorizes a journal
operation nor mutates a ledger, marker, gateway or service. Its existing exact
PID/home/freshness/counter/coverage checks remain.

## Tested scope

The native gateway status reader now preserves the SDK admission fence's owner
and rejects impossible initial/positive-epoch ownership combinations. Five new
portable regressions cover initial, frozen, released, malformed and foreign-owner
states. Observing another owner's fence gives no permission to adopt it.

Current checks pass **829 Windows methods in 1,083 cases, with 254 platform
skips**, **104 Pi cohort methods/no skips**, and eight real SDK Unix requests
across four generated-home modes. Independent raw/SQLite/native readback verifies
the exact fence owner/epoch, ended peers and stopped inspected units. The SDK
runner and ledgers are synthetic; normal GatewayRunner and real drain remain open.

Parent source `1a07bf6` independently passes **1,078 hosted Linux policy methods
with no skips**. Source `09306e6` now has a reconciled full standard Android gate:
**1,011 JVM methods, 1,142 API26 / 1,143 API37 device methods**, exact disjoint raw
unions, recomputed summary and matching actual APK hashes. Both lint variants
have zero errors (52 debug / 51 release warnings). These exact-source results do
not close original UI causes, twelve external-model or final production-phone gates.

The latest inspected upstream reports retain **37 stable / 172 main changed-
handler findings**; main is now `d1b46d7`. Those are review findings, not accepted
compatibility. Source inspection covers 1,724 staged admission/drain-related
Python files. It establishes specific call sites, not whole-fleet enforcement.
Live services and published beta.9 remain unchanged. The full goal stays active.
[Exact source, artifacts and limits](2026-11-15-original-child-exec-handoff.md).

There are now 31 focused gateway cases: 23 portable cases and eight actual Linux
socket/peer cases. The Windows suite positively runs the 23 and skips the eight
native cases; the Pi cohort positively runs those eight. The cohorts overlap
and must not be summed into a fleet acceptance total. The five added methods
are exercised on Windows; current-source complete hosted Linux coverage remains
a separate follow-up after this commit.

The separate real SDK listener/status trial uses generated SQLite rows and a
synthetic runner in four fresh homes: idle, busy, unknown and unenrolled. Its
eight requests preserve PID/start/boot/UID identity and one request per supplied
connection. The final-source projection is recomputed independently from raw
responses; read-only SQLite confirms `(epoch=1, owner=fixture-owner, frozen=1)`.
All four peers ended, sockets/pointers were removed, the fixture controller
unit is stopped, and the original failed trial remains failed. Each mode loads
228 hash-bound SDK module origins; the full 23,628-file SDK inventory conserves.
This trial does not perform a real SDK Ledger.freeze or boot GatewayRunner.

The fresh 104-method native cohort has exact expected/started/stopped/successful
identity equality and no failures, errors, skips or unexpected outcomes. Raw
gateway peers have the expected one-use request lists, no foreign-owner input,
and independently verified ended native incarnations. The cohort unit and 27
serve fixture units are independently observed stopped with no restarts and
empty cgroups. This is the inspected 28-unit scope, not a scan of every fleet job.

## Exact hosted sources and artifacts

Parent policy source `1a07bf6ce67ba1f1847ceb983da742ce7af12733`:
[run 43688427487](https://github.com/DionCroft/Signal_Grove/actions/runs/43688427487),
parser job `132733705992`, artifact `14151376969`, SHA-256
`a19ae0e095a3f02421596afdd73ba5b52a7195026f6c47b73308685b2421d7c7`.
All 1,078 Linux methods positively run without skips; raw logs, stopped native
unit and complete Windows/native inventory equality are independently checked.
Those results predate the five new owner regressions and are not attributed to
this changed source. The separate upstream job fails for review-required changes.

Android source `09306e6636c073964fab71d1324d41009a88af48`:
[run 43678205067](https://github.com/DionCroft/Signal_Grove/actions/runs/43678205067),
build job `132708869443`; four shards and standard-summary job completed.
Every ZIP has exact source/size/SHA-256/CRC and unique-member verification.
The committed source's verifier is exported and executed against original raw
partition inventories and instrumentation logs. It reproduces the hosted JSON
object exactly. Windows line endings differ in summary bytes; no byte-equality
claim is made. Four partitions are disjoint within each API and match the full
declared standard inventory. No failures or skips are waived.

| Artifact | ID | SHA-256 |
| --- | ---: | --- |
| Build/JVM/lint/APKs | 14147529948 | `0833ad5db1517cd998273800a5053edfd241d4e1f0b5ead84c93ec0fc6de9a8a` |
| API26 shard 0 | 14149089207 | `d166de2c1eb34a60a96ea983e233ca11d1136735df3c15d681c24972cc861db1` |
| API26 shard 1 | 14149837341 | `f5715dc5636cb5eff5fc1fd10c46a828d90d387b9aac3a3f5431afcfc8131024` |
| API37 shard 0 | 14150200207 | `22c35aa1596f69f38f0c2907e63079e2265f63c87f47540c094a9be0a2ba97cf` |
| API37 shard 1 | 14150930792 | `457a79459c49f2590d6865617f39d275bf8cf3ec08b10731b8043ebaebf46b98` |
| Hosted summary | 14150756278 | `901e3e3395864cd06530e1f8337937749aa7c7a9518bffa0b812c5ad05282ee3` |

API26 partitions contain 541 and 601 cases; API37 contains 542 and 601.
The two extracted build APKs match every device partition: application
`59d2cb91ebd3ee07ff3a16a2dd2866d9b7c1975f342e902192b8ee14f19f9986`
(92,606,058 bytes) and test companion
`8f97b233e77f40923e635854c0f67949cfca73a89ef4346d6d4f357c28b7ea0b`
(8,041,603 bytes). These are CI debug artifacts, not an original-key production
release. JVM XML has 1,011 unique cases in 97 reports without failures or skips.

Stable remains `v0.23.1` / `3b421578588243f920886e6fe79a6faa87ea026e`,
37 changed used handlers, artifact `14151451784` SHA-256
`1d8007af6168211a497feb95fe53d871bffc8a4e7cf5caff74f5b202d0e5d297`.
Inspected main is `d1b46d718ffe675427928eff7e5fff69bcb4519b`, now 172
changed used handlers, artifact `14151426973` SHA-256
`d16d57cbdd23ff716f54ffcdde4f363d9c23141824ee8d58eb1c3f12c23dfdb9`.
The previous 171-count observation retains its older source scope. Both current
reports require review; this checkpoint does not approve their semantics.

## Drain source review and next implementation boundary

The hash-checked staged modules establish:

- The external marker watcher sets `_external_drain_active` while leaving the
  process running and existing turns uninterrupted. Messaging excludes new
  non-internal turns; the API request reservation uses the same drain helper.
  Its exception-to-false fallback is not strong absence/admission evidence.
- The in-process cron provider gets `can_dispatch`; external cron fire checks
  the owning runner after startup waits. These paths require runtime validation.
- With the opt-in project policy enabled, strict and general/unbound turn
  admission both consult the ledger's unfrozen condition. The general-chat
  passthrough is not an independent bypass: `turn_scope` reserves its durable
  receipt in that transaction, and unbound work contributes to fence counts.
  Disabled, missing or unproven policy/ledger remains unknown.
- `Ledger.freeze` persists the owner/epoch and refuses a competing owner;
  `thaw` retains them. Its constructor can initialize/migrate schema, so a drain
  actor must validate existing enrollment rather than silently create it.
- The SDK marker writer replaces existing content and refreshes its time. A
  journal actor must instead refuse foreign/raced preimages and publish once
  without overwrite. Epoch/age/lenient-read behavior cannot establish absence.
- Normal startup can claim ownership, attach/replace, recover sessions, start
  cron/watchers and later reap children. A real qualification must use a fresh
  generated home, closed configuration and its own finite native unit, with no
  shared-cgroup reaper, live home, transport credentials or foreign network.

The 1,724-file literal call-site scan is navigation evidence, not proof that
aliases, indirect dispatch or every source of work is covered. Source files
used for this review are hash-bound in the private evidence below. No drain
mutation, policy enrollment, model change or live gateway action occurs here.

Implement the original-journal native gateway drain using reviewed existing
per-profile admission ledgers and a fresh, exclusively owned marker; qualify
normal GatewayRunner in a finite isolated unit. Bind exact native owners, source,
ancestry, approval and expiry. Preserve any existing/uncertain fence or marker
without adoption, replay or automatic release. Then compose all-profile/worker
admission, the unified executor, reload/authenticated health and bounded runtime-
only recovery. Both canonical PM contexts, seven real modes, policy/launcher
references, real private GitHub/live-home backup and separate final live approval
remain unactivated. Continue all four original acceptance priorities.

Every original API26 cause, used/transitive compatibility, actual authenticated
project/workspace/worker/NAS, Kanban/jobs/rooms/grants/integrations, doctor/graph,
local-model/phone/audio/background/notification/accessibility/battery/thermal,
and final-source/original-key upgrade/release/security/support requirement stays.
The consumed phone restart approval is not renewed. Earlier failed/unknown
receipts and historical source-specific evidence remain.

## Reproducibility and private evidence

Windows uses `python -B -m unittest discover -s scripts/tests -v`. The Pi uses
the committed `ci_native_policy.py --native-only` with fresh private work root,
reviewed tool paths and the real finite manager fixture. The SDK trial uses
`native.sdk_gateway_control_fixture.run_trial` in its separate finite unit.
Android uses the exact committed `verify_android_device_coverage.py` with raw
four-part artifacts and the source revision above. Re-observations do not
redispatch a held operation. Raw/private files stay on the selected temp drive;
public records contain hashes and scopes, not credentials or private locations.

Consolidated review SHA-256: `869f00791ff574ef05b328cd0d64c6894eea5596b8ec5d94335ee751782ad9ce`.

| Current source file | SHA-256 |
| --- | --- |
| `scripts/native_gateway_control.py` | `5d2c7ba9170ff1b5177aadc71322eaa7d81948a0a6c44de53a3b3121e8e4fb6c` |
| `scripts/tests/test_native_gateway_control.py` | `891901553278700767ccf73dd786e44b3c1558dfd84a05798bb4c20335d77fb4` |
| `scripts/native/sdk_gateway_control_fixture.py` | `07dfc3cef290b3caf192842bd05390dc605e0d7119406cd94691d313b59cd1c8` |

| Private evidence record | SHA-256 |
| --- | --- |
| `fence-owner-source2288/manifest.json` | `c8c661e80fd0a6bd694b8639319cf8136d0f86b76c38648ce69fad06962bd639` |
| `fence-owner-native-review2295/review.json` | `923a08a8a02ce9207ff26f6a93c1631f869bdbae7ee8f8dcf7aa4c3869c4fc25` |
| `fence-owner-sdk-review2294/review.json` | `3c76853c503f29c3f827ab2923b8d5e93fbf70003ae928de11ae86c814e0bcdd` |
| `inventory-windows2291.log` | `47051e0884362b884359d48d521151a5244213991dd31bf1dda4cfee50182e1f` |
| `hosted1a072286/review.json` | `1757daa557cfd664c9fdfe602139a47f250bb1bae8a8e5d94f17cd2f69188e0e` |
| `hosted1a072286/upstream-review.json` | `716b6495c9b07cf8ac29cb9d8d5c5eaf2dfa59ec789234db511e38a40d6b6dba` |
| `ci-android-build2284/review.json` | `db98aba5825f24e869439e0df62257d773db6061f6a2150f403401454d56eb1a` |
| `standard093062285/review.json` | `8a8e42be2dc0b5421d26f49abab55c1ab995bb3763907bcec71ca6702030a124` |
| `gateway-drain-inputs2279/review.json` | `c531f67ec657ad4ecf79af603bce4462f65fa11db33336acf18b5fbae316e737` |
| `native-admission-inputs2281/review.json` | `db8f3a567929f6490e0421f55ef1353e98d23a4037d3458a8d806a828c086155` |
| `gateway-bootstrap-inputs2287/review.json` | `81f81b18762721fb9dd770b75942d7242ed94584b74b394d2a4def13cc1cc010` |
| `drain-path-inputs2293/review.json` | `848f1a39687d21979f2f6922fbf1fb60cf842898b98a21d27d2adcb371bb39fe` |
| `drain-callers2292/review.json` | `47602c9ae084fde1d0c58f43b708a2c97f2a7865d4a4db00d43c066120bc0689` |
