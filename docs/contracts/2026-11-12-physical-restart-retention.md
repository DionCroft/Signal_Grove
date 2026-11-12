# Physical native interruption and normal restart retention - 12 November 2026

The owner's M098FE ARM64 Android17/API37 phone runs the original process-recovery
test class, using an isolated publisher-verified Qwen2.5-0.5B test model. The
debug APK SHA-256 is `9dc7267f6cfcc7756de918e6854d186a96da8e58d2c64d366456066b5e7f9243`;
test companion `0262e95d6194fe7e42e570f623c47374ee8342c527e1aa3eb7e9df9ceec1cce5`.
Frozen context is `47ceba3875b9479fcfb7958a885ebe3e06a0f5fdad5f780bbc7356eb0a4f8a8c`. The production beta.8 package is
separate and unchanged. Original source/timing/native assertions are unmodified;
the subsequent keyboard candidate changes a different emulator-only runner.

## Exact sequence and verdict

1. `LocalProcessRecoveryIntegrationTest.prepareInterruptedNativeGeneration`
   witnesses real native generation, the exact debug PID/model mappings and a
   persisted GENERATING turn. That one exact process is intentionally stopped.
   Its expected interrupted command is not counted as a passing JUnit case.
2. `coldRecoveryMarksInterruptedAndOnlyAnExplicitFreshSendGenerates` passes:
   2.849s JUnit /3.352s ADB. A fresh process marks the saved turn INTERRUPTED,
   preserves its contents, performs zero automatic loads/generations, and only
   an explicit fresh question generates/completes/persists/unloads through JNI.
3. The owner explicitly approves one normal Android restart. Exactly one request
   is issued. A changed kernel boot ID and completed Android boot are observed
   after24.619s; the owner unlocks normally. No second reboot, root, wipe or host
   restart is used to obtain this result.
4. `rebootKeepsCompletedAndInterruptedHistoryWithoutAutomaticLoad` passes:
   1.856s JUnit /2.488s ADB. Exact completed/interrupted history persists without
   automatic load/generation/replay. Deleting only the isolated test model leaves
   its saved chat and both original owner model files unchanged.

That is **two positive native cases and one intentional preparation interruption**,
not three positive tests. This proves normal-restart recovery on this phone; it
does not resolve the earlier emulator guest-reboot outcome.

## Retention and bounded cleanup

Full hashes verify both original LFM model files before/after. All three protected
metadata files are byte-identical; all three package UIDs/APKs remain. Complete
SQLite schemas and owner rows are compared through verified encrypted snapshots.
Only cached-job freshness and WorkManager's last-force-stop bookkeeping values
change. No conversation rollback, production upgrade or connection rewrite occurs.
The owner's pre-existing USB-awake setting is preserved, not reset to an older
test value. After acceptance, the exact additional input copy is hash-checked and
removed; the staged source, original models and chat/witness files remain.

Short USB-charging temperature observations are27.4-27.9°C at100% charge. These
are not sustained thermal, endurance, throughput or peak-memory measurements.
No additional phone restart or model/default/gateway change is requested.

## Reproducibility and remaining scope

Private evidence retains encrypted before/cold/postboot snapshots and exact source,
PID/mmap/witness, boot, database and package receipts. Raw command-log SHA-256:

| Step | SHA-256 |
| --- | --- |
| Intentional preparation | `a2de24eafcd7e1fbffdc5cc5061472658a6461ea1bd618325ea7ff15f2fd4e58` |
| Cold recovery | `2f9d728c780163ee014ee6e6a5071ef122f887c9eaed15ebd66d5eee521f1ed2` |
| Post-reboot retention | `b703883976e76ce653b4bb65a6b5a63c4018c3caabf49e74e518fa561b498ea8` |

The older owned API37 emulator remains live/ADB offline after its one guest-reboot
request; no request is replayed or historical failure relabelled. Current full
hosted coverage, production-key physical upgrade/live connection, compatibility
review, SBOM/provenance and published-asset verification remain release gates.
Original worker/project/integration audit requirements remain active. See the
[full ledger](../AUDIT_EXECUTION_PLAN.md) and [current keyboard candidate](2026-11-12-ready-keyboard.md).
