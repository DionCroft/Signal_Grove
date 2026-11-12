# Fresh original-key beta.9 candidate from reviewed main

The clean main source is `b182ae87d1240cdeb6eece402ae2ff9f6baecb3d`:
853 frozen files, digest
`3d0c87966e6ece382932ec9b76cae5e0ee6b6894ff8845c6ddb73379398bb22d`.
The complete signed build finishes successfully in 976.99 seconds. Independent
verification accepts 1,006 JVM cases in 96 reports with no failures/errors/skips,
both lint variants with zero errors and 51 debug/50 release warnings, all four
native ABIs, 64-bit 16 KiB alignment and uncompressed 4-byte-aligned DEX.
Models are not bundled. This records new artifacts, not a relabelled earlier build.

| APK | Bytes | SHA-256 |
| --- | ---: | --- |
| Production | 70,993,354 | `137d00f73229988ad64af7a61ab6e2ef3d8442f6519805cd485750b5147a8ec9` |
| Original-key debug | 92,543,394 | `2f87245a2045d6e51d0f460766c9b37be54ad604eb190f6cdbd839c347255a31` |
| Test companion | 7,901,783 | `bf55f3be92d2a9107100deafdc111c79fabc1406d3f7f2a84d1eaa4991227c48` |

Both app packages are 1.1.0-beta.9/code112; original package IDs and certificate
identities verify. Production signer SHA-256 is
`bbffacc53cc2dfecd78c598a41a44caf89623827b315bb873f62129da771e40f`;
debug/test signer is
`cab8b5521a7bc7d6f4c7a7a3d093f4bc74549a54785152c69d1b364881fa4f2b`.
The test APK is byte-identical to the preceding frozen workspace-test candidate.

## Inventory and advisory coverage

All 95 resolved production dependency artifacts match strict verification
metadata; 150 Maven components, 32 packaged native files and the pinned native
source make 183 CycloneDX 1.6 components. Official pinned schema validation,
public certificate, checksums and source/build provenance verify. Six matching
release files are prepared locally. They are not externally attested provenance
or a reproducible-build certification. Fresh OSV queries at 20:31 UTC on
12 November cover all 150 exact Maven versions, positive control and pagination,
and the pinned native commit. Neither query finds a matching indexed advisory;
this is limited known-advisory coverage, not complete security certification.

## Phone preparation and remaining acceptance

The actual owner's ARM64/API37 phone is connected. Before staging, both apps are
idle; three original package UIDs/APK hashes, both original model hashes and an
encrypted, round-trip-verified debug metadata snapshot are retained. Production
private data is inaccessible through Android shell: its retention is not claimed
from that snapshot. A too-narrow preflight package-path regex first refused normal
package-ID dots; the failed guard is retained and corrected before snapshotting.
No phone restart or setting change occurs.

`SignalGrove-beta9-production-137d00f73229.apk` is staged in Downloads, and its
device SHA-256 equals the verified production APK. The normal Xiaomi installer
is required; installation and saved production connection/history/live behavior
are pending. The debug app and companion are unchanged.

The complete hosted standard source2970 gate independently passes 1,135 API26 /
1,136 API37 cases. All 543 Android/native/build inputs remain identical at this
candidate. This does not turn a queued exact-head CI job into a completed run.
At this checkpoint b599 Android is running and b182 Android is pending; b182
Windows CI passes, while Hermes compatibility remains failed with stable40 /
inspected-main100 used-handler findings. No compatibility baseline or source
waiver changes. Forty Git-blob-verified predecessor/current handler diffs across
33 modules are prepared for semantic/transitive review, without SDK execution;
that preparation is not acceptance. Two partial review-helper guard failures
remain retained.

The real native-owner seeded/empty trials still return 2 for the upstream shutdown
warning. Actual Pi/worker file access remains intermittently unavailable. All
original audit, physical model/performance and integration requirements remain;
the goal stays active. Complete applicable compatibility/exact-source CI,
original-key physical upgrade/live checks and public asset/download verification
before publication. Beta.8 remains the public preview; beta.9 is unpublished.

Private build1077, artifacts1078, advisory1079, inventory1080, phone1081/1082,
source-diffs1083-1085 and staging1086 preserve raw evidence on the execution host.
[Current state](../../PROJECT_STATE.md) · [Audit](../AUDIT_EXECUTION_PLAN.md).
