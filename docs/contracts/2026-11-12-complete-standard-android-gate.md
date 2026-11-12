# Complete hosted standard Android gate - 12 November 2026

[Android CI run 43166502402](https://github.com/DionCroft/Signal_Grove/actions/runs/43166502402)
is terminal success at source `7ee5c59172b430e6a95b5b1d27112f8b326fbf7f`.
Verification is based on independently downloaded bytes and reconstructed raw
case logs, not the workflow's green status alone. All six artifact ZIP sizes and
GitHub SHA-256 digests match. Every partition is bound to the same actual APK pair
and source revision; the separately downloaded aggregate equals recomputation.

| Standard suite | Complete inventory | Executed, positive | Partition counts |
| --- | ---: | ---: | --- |
| API26 | 1132 | 1132 | 535 + 597 |
| API37 | 1133 | 1133 | 536 + 597 |

Raw inventory/execution logs require one exact start/positive finish per distinct
case, complete successful native terminal envelopes, matching log digests, no
skip/failure/partial statuses, disjoint partitions and exact union of the complete
inventory. The actual execution command is separate from log-only inventory and
does not set log=true. The one extra API37 case is LocalNetworkPermissionTest's
denial/no-request/explicit-grant check; its source declares minSdkVersion=37.
No API26 case is absent on API37.

The downloaded raw JVM XML contains **1004 distinct cases / 96 reports**, with
zero failures, errors or skips. Both lint variants have zero errors; hosted
warnings are 52/51 (debug/release), versus local frozen 706's 51/50. The sole
additional warning in each is AndroidGradlePluginVersion reporting a newer plugin
version. No dependency was upgraded to silence it; advisory/dependency maintenance
remains a separate required gate.

The downloaded app/test APK SHA-256 values match every device partition:

| APK | SHA-256 |
| --- | --- |
| app | `becedd912bef7b0e1ba736e91756094c5b4c98308a6eec3a0e0a116c60ee9dc5` |
| test | `52d9956ef5d7b6ee9ed2485d72dafc9cf31516030c0df400316d89895b7f96f6` |

SDK apksigner validates both with the same hosted debug certificate
`f236a1bf9af46ce50ec815f9a40b1cb90935f8e26217f2f5f8585d5e15d4f3bd`.
It differs from the owner's retained local debug signer; neither this check nor
these CI artifacts establishes original production-key acceptance. aapt2 confirms
org.hermesmobile.app.debug, code111, beta.8, minimum API26 and target/compile API37.
These artifacts are not installed over the owner's retained apps or published as
the requested phone update.

Both APKs pass SDK 16 KiB ZIP alignment. All 32 native libraries have the expected
ABI layout and valid load alignments: at least 16 KiB on arm64-v8a/x86_64 and
4 KiB on 32-bit ABIs. All four ABI JNI bridge entries and the dynamic ARM CPU
backends are present. No GGUF/SafeTensors/ONNX weights are bundled. Raw build and
gate source comparison verifies **545 current files** match the tested commit
canonically, including Android source/resources, Gradle inputs and the exact
inventory/coverage runner code. Later coordinator/Python policy/docs changes do
not change these tested Android inputs.

## Explicitly separate model acceptance

The standard gate correctly records external_model_acceptance=false. It excludes
these five prerequisite-dependent classes; this is not a claim that they passed:

| Class | Methods | Required separate prerequisites |
| --- | ---: | --- |
| LocalModelPhysicalTest | 3 | actual Android phone, 64-bit process, explicitly staged verified licensed LFM3 model |
| LocalImportCommitIntegrationTest | 1 | 64-bit process, explicitly staged publisher-verified licensed LFM3 model |
| LocalModelFamilyIntegrationTest | 2 | 64-bit process, explicitly staged verified licensed LFM3 and Qwen models |
| LocalModelTransferIntegrationTest | 2 | 64-bit process, explicit public-model network and licence acknowledgement |
| LocalProcessRecoveryIntegrationTest | 3 | 64-bit process, explicitly staged verified licensed Qwen model, externally prepared interruption and reboot state |

There are 11 external methods. The prior phone/model evidence remains valid only
for its recorded source and scope. Final-source physical/model, import/transfer,
history/load/unload and interruption/reboot acceptance still require their own
actual prerequisites; a standard emulator pass cannot substitute for them.

## Reproduction and remaining release gates

Download the run's four android-device partition artifacts and standard-suite
summary, verify ZIP sizes/digests, then run:

```text
python -B scripts/verify_android_device_coverage.py --artifacts <verified-artifacts> --source 7ee5c59172b430e6a95b5b1d27112f8b326fbf7f --output <approved-scratch>/coverage.json
```

Retain the raw logs, exact APK hashes, aggregate and model-gate manifest. The
owner's private evidence is android-hosted-proof770, android-hosted-native772 and
the six digest-verified ZIPs, outside Git on approved scratch. Temporary download
URLs are not source or release assets and are not committed.

This closes the complete **standard** hosted Android gate for unchanged inputs,
not the full audit/release. Current stable compatibility still has 40 reviewed
work items open; the saved main context remains 97. Native production sign-in/
recovery/approval and worker/NAS/Android-file integration remain required. The
separate Pi code/metadata trial is still pending explicit transfer approval after
automatic review rejection and has never executed. Graph/doctor, integrations,
automation, accessibility/background/audio and support/advisory work remain.

Next independent action is to inspect the retained API37 local-model emulator's
actual idle state and data/UID/model receipts. Prepare a verified metadata recovery
and exact-source original-debug-key upgrade before any applicable nonphysical
model gates; do not wipe/restart, replay unknown work or treat an emulator as a
physical phone. The full active objective is retained.

The requested final APK still requires an increased code, original production
package/key, applicable final-source model/device/upgrade checks, release notes,
checksums/certificate/SBOM/provenance and a verified public GitHub Release asset
with direct phone download link. Published application remains v1.1.0-beta.8.
