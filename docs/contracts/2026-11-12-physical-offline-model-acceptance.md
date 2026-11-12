# Physical-phone offline inference and retained-data acceptance

The owner's physical ARM64 Android 17 / API37 phone runs the exact original-debug-
key candidate, with no model, inference or setup-service connection during these
checks. Airplane mode, actual Wi-Fi service and the absence of a default network
are checked before, during and after both native batches. The original normal
network is restored afterward. Production Signal Grove is unchanged.

## Six exact final-source native cases pass

The first three cases verify real LFM3 output, independent replies, cancellation
and recovery, coroutine cancellation, context-overflow refusal, unload/reload and
bounded cancellable metadata scanning. The helper selects reviewed source IDs
for all six topics: first setup, network, authentication, models, history and
projects; versioned English/Chinese/pinyin documentation and Chinese generation
are checked. Metadata scanning takes 38 ms. Three distinct complete native cases
finish in 25.974 seconds, with zero failures or skips.

The next three cases verify the committed-import cancellation boundary without
reopening or loading weights, real Qwen chat/history/deletion, and switching
between LFM3 and Qwen while preserving separate model-bound histories. A model
mismatch is refused before generation. All original memory guards and timing
assertions remain. Imports take 685 ms (Qwen), 1438 ms (LFM3), and 649 ms (Qwen).
Three distinct complete native cases finish in 18.833 seconds, with no failures
or skips. Inventory/diagnostic events are reconciled separately from case passes.

Model files are independently pinned:

- LFM3-1.7B QAD Q4_0: 1,049,615,680 bytes, SHA-256
  `43a7d09a0e9cba91b63eb7052488b9564569bddfd65754d2e426465e572a7452`.
- Official Qwen2.5-0.5B-Instruct Q4_K_M: 491,400,032 bytes, SHA-256
  `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`.

The installed app SHA-256 is
`4b24f516f421e8de0198bc24450f1557524950ad92668e84a35bcc0931bccf8e`;
the test companion is
`56309d5b81768249ef7ffbab97edad5f828e4d6155a8b766cd6b5c49b695b352`.
Their Android/build inputs still match the frozen candidate and the independently
accepted standard gate. Debug signing retains its existing identity; this is not
the production signer or an incremented release APK.

## Retention, recovery and observations

Before opening the upgrade, all 14 protected metadata files, two original models
and all three package UIDs are retained. After testing, four original local turns,
their chat identity and model binding remain exact. The owner confirms sending a
fifth completed turn outside the test windows; it is preserved without rollback.
Original preferences and both original model hashes remain. Independent SQLite
checks preserve schemas and all other logical content: one cached job changes
only fetchedAt, and WorkManager changes only its last_force_stop_ms bookkeeping.
Production APK/data are not modified. Encrypted before/after metadata snapshots
pass disk round-trip verification; these are scoped, machine-bound recovery,
not a portable Android Keystore backup.

The debug app is left available to the owner. After confirming no active native
run or mapping, only three exact owned duplicate test inputs are removed by
canonical path, size, hash and link checks (2,590,631,392 bytes reclaimed).
Original models, owner chats, generated isolated test histories and the failed
16-byte transport fixture are preserved. The failed Windows binary-stdin copy
is reconciled; tests instead use the already hash-verified phone source. Control
operations use exit-status-carrying shell calls rather than exec-out status.

Highest observed VmHWM is 2,608,447,488 bytes across the two short batches. USB
charging remains active: battery 95 -> 95% / 27.9 -> 28.0 C and 96 -> 96% /
30.1 -> 30.4 C. Sampling does not establish a full peak, battery endurance,
sustained thermal behavior or exact token throughput.

## Remaining acceptance

These six passes cover six of eleven external-model methods. Two real-HTTPS
transfer methods and three externally orchestrated process-recovery methods
still need final-source acceptance. The earlier emulator run retains its one
Qwen pass and two LFM import timing failures; those outcomes are not erased or
waived by the physical passes. Full physical UI/background/audio performance,
real Hermes integration, and the original-key production upgrade and public APK
remain separate gates. All original audit rows remain in the active goal.

Private evidence: physical821/proof822, family824/proof825, restore826,
retention827, owner history828/829, database830/cache831 and cleanup832. Raw native
log SHA-256 values are respectively
`3681591101e020d69b3d3437c390530d079867c21edb0e63bf70059f0ef9f562` and
`f4a6e99efe863c327fc2f90b3d5552b8b70972b99ca5e548dea8f6cf0d2649d8`.
Use the explicit opt-in classes in the Android test source and reviewed fixtures;
never replay a completed or interrupted external fixture automatically.
