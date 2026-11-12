# Retained model emulator: upgrade and offline failures

This is Android 17 / API37 x86_64 emulator evidence. Physical-phone, complete
model and production-release acceptance remain open.

## Upgrade and recovery

An in-place upgrade uses the original debug certificate
`cab8b5521a7bc7d6f4c7a7a3d093f4bc74549a54785152c69d1b364881fa4f2b`.
App SHA-256:
`4b24f516f421e8de0198bc24450f1557524950ad92668e84a35bcc0931bccf8e`.
The already exact test APK was left installed, SHA-256:
`56309d5b81768249ef7ffbab97edad5f828e4d6155a8b766cd6b5c49b695b352`.
Both package UIDs, all 1,544 protected file hashes and three model hashes survive
the upgrade. Neither APK is the production update; the phone is untouched.

Two independent pre-install metadata archives match. All 2,435 non-model files
are encrypted with Windows CurrentUser DPAPI; disk-read decrypt/decompress
round-trip verification passes. Three GGUF files are separately hash-verified
and excluded. No plaintext archive or upload occurs. This is account/machine-
bound metadata recovery, not portable Keystore or complete AVD recovery.

## Actual offline run

Airplane mode is on, Wi-Fi off and the active default network is confirmed absent
before and after execution. A separate log-only inventory lists three cases;
the subsequent actual native command omits `-e log true`. Publisher-verified
LFM3 and Qwen sources are used with new isolated model/chat roots.

| Case | Result |
| --- | --- |
| `LocalImportCommitIntegrationTest#cancelledCommittedImportBecomesVisibleWithoutReopeningOrLoading` | Failed: copy boundary not reached within the existing 90-second wait. |
| `LocalModelFamilyIntegrationTest#verifiedLfmQwenSwitchKeepsHistoryBoundToEachFile` | Failed: existing 120-second operation wait expires during LFM import. |
| `LocalModelFamilyIntegrationTest#verifiedQwenImportChatHistoryAndDelete` | Passed: licence refusal before opening, verified import, native chat, unload, cold history without loading, model deletion preserving chat. |

The complete run takes 500.848 seconds: **3 tests, 2 failures**. ADB exit code zero
is not a model-gate pass. Raw native log SHA-256:
`e817c3a70b74805b9b62a73a55d421436d1a3db51083ce2f8842d3c30aa7e1af`.
Qwen imports measure 93,991 and 27,981 ms. These establish variable latency, not
its cause. This run accepts neither LFM generation, the publication-cancellation
boundary nor the complete family-switch sequence. No timeout, memory guard or
assertion is weakened. Failed fixture roots/logs remain; no automatic replay.

## Reconciliation and next action

Original Airplane-off/Wi-Fi-on settings and the active network are restored.
All original model, preference and transcript hashes survive. Of 1,544 protected
metadata files, 1,539 are byte-identical. Five changes are explicitly reconciled:
profile-installer marker, WorkManager database/WAL/shared-memory files, and the
Hermes database shared-memory index. Four independent SQLite quick checks pass.
All Hermes tables/schema are logically identical. WorkManager's schema/work
tables are identical; only its `Preference` table differs. No original device
data is restored or deleted. Temporary SQLite comparisons use owner/SYSTEM/
Administrators-only storage and all temporary database files are removed.

Private owner receipts: preflight778, APK780, encrypted recovery781, upgrade782,
network783, inventory783, native786, reconciliation790 and bookkeeping793.
Next: diagnose import latency/progress, complete the unchanged assertions and
final-source physical gates, then original-production-key versioned GitHub APK
delivery. Source pushes do not fulfil that delivery.
