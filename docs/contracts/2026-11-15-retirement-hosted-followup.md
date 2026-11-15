# Hosted retirement checks and newly inspected upstream drift

Later full-inventory work passes 83 controlled Pi methods and 1,039 Windows
cases (806 positive / 233 platform skips). Actual SDK login succeeds, but its
retirement actor exposes a preclaim socket-timing hold; that real gate remains
open. [Current failure, exact proof and next implementation](2026-11-15-sdk-retirement-inventory-and-timing.md).

15 November 2026. Source `87615c44a100804605b7fe6a6b0ea467b4cdb6fd` passes
the [hosted full policy job](https://github.com/DionCroft/Signal_Grove/actions/runs/43671597650):
**1,037 positive Linux methods without skips**. Independent published-ZIP digest,
CRC and raw-log verification proves the exact planned/started/stopped/successful
union and matches every method to the Windows inventory. All 17 native and five
portable retirement cases have positive results. The finite controller is stopped
with zero main PID/restarts and empty cgroup. The 15 runtime-model cases also pass.
This closes the new component's hosted policy gate, with the [81-method Pi
component evidence](2026-11-15-native-serve-retirement.md) retaining its exact scope.
It does not establish actual SDK actor, live cutover or production acceptance.

## Upstream changed during observation

The exact artifact for stable `3b421578588243f920886e6fe79a6faa87ea026e` still
has **37 used-handler findings** and requires review. Inspected `main` has moved
from `f578714e0ee122179ec28fecb9af989583b71baf` (**169 findings**) to
`b2c6bc4e1e183263907cf81cf070aa8ebdd5a553` (**171 findings**). Independent artifact
digests and JSON readback preserve both sources. The two newly changed entries are:

- `gateway_ws:_dispatch_request`
- `canonical_controls:binding:_PROFILE_IMPLICIT`

No earlier finding disappears. No baseline, variant or review exception is
changed to admit this drift. The upstream job remains failed at the required
used-handler review gate; registry and sidecar jobs pass. These two additional
findings extend the same full audit objective.

## Newer build and device scope

Source8d's [Android verify job](https://github.com/DionCroft/Signal_Grove/actions/runs/43667437840)
is now independently verified from its published ZIP and actual members:
**1,011 unique JVM cases / 97 XML reports**, zero failures/skips, both lint
variants with zero errors (52 debug / 51 release warnings). Exact CI APK hashes:

| APK | SHA-256 |
| --- | --- |
| Debug app | f7f6ead61db68b6284db5c9e97ca3d92860af0a0f441b80ca8bd6bc635e3f239 |
| Debug test companion | 446379b181d1283fabbe4fa7e7b1ef48ef929613157f698bfa04cbc507faae3f |

Its four API26/API37 shards remain running; no job is restarted, replaced or
accepted early. Source876's Android workflow is queued separately. The completed
source7e standard gate remains 1,142 API26 / 1,143 API37 methods with matching raw
unions and actual build hashes. The historical Demo/navigation cases pass there;
their original causes remain unproven. Twelve external-model methods, final
physical-phone, signer/upgrade retention and production-release gates stay open.

## Exact evidence

| Artifact | SHA-256 |
| --- | --- |
| Hosted source876 full policy ZIP | 2e8373dbd9853df4e6e518351826c98de8be908cf5a32eddf35341f10d379803 |
| Independent source876 raw/method review | 687dc64babb20db9b75b77203817308851d1407ba4e00ba2bfbe88d554c37cdd |
| Source8d actual JVM/lint/APK review | 2efa815c2940591607fbdd3c1d88c79dbcb03ca0d46175f8128fa5f6d7bc202f |
| Current stable/main artifact review | b6325274ef7aaa0af7cdf7dc2bf22d87b03908284df2d3d2aecef91ebccb2b77 |
| Retained old/new main drift | 95020198d6eaf1ae47bfb479fd378e164ef59f06b5123ccdedf82f07646e9744 |

Private paths, SDK copies, fixture data, signed download URLs and full logs remain
outside Git. No app/build source changes here. Live services, owner state,
production identity and published beta.9 remain unchanged. Continue with the
actual normal-SDK journal-bound retirement trial, gateway drain and unified
executor/reload/health/runtime recovery, alongside the full original
[goal](../GOAL.md) and [audit ledger](../AUDIT_EXECUTION_PLAN.md). No live cutover
is authorized by these isolated results.
