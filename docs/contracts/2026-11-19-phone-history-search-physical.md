# Actual phone offline history search — 18–19 November 2026

The original-key matching search app/companion is upgraded in place on the owner's
ARM64/API37 phone. Exact installed APK hashes and all three package UIDs match;
all 16 protected metadata files and both model files retain their bytes at that
upgrade checkpoint. The normal code112 Signal Grove app is unchanged, separate
from this debug app. All four prior real UI test chats remain intact.

With Airplane mode enabled, Wi-Fi disabled, no default network and no mapped GGUF,
the owner uses the actual app: Phone history → Search, types `Forest` and confirms
once. Read-only native screen inspection shows two of four conversations and
both expected forest titles. Case-insensitive text matching works on real stored
histories. All four transcript bytes/IDs remain unchanged; no inference runs.

The owner then clears the filter. Inspection observes Clear/count controls gone,
and the owner opens a saved garden chat. Its exact earlier question and reply
are visible while the phone remains offline and weights stay unloaded. All four
chat bytes and both model hashes remain unchanged. Original Airplane/Wi-Fi/
Bluetooth/stay-awake settings are restored exactly; a default network returns.
No pending instrumentation or new model load remains. This verifies these
interactions, not every model/date filter, TalkBack, lifecycle or full OF-02.

## Retained instrumentation failure and SQLite reconciliation

Before the actual UI check, one separately invoked four-case synthetic search
cohort stalls at its first method without rendering its screen. The phone stays
on the launcher. One explicit foreground request for the debug ComponentActivity
returns successfully but renders no nodes and does not advance that method.
After more than 150 seconds, only the debug process is stopped once. The exact
native terminal is unsuccessful; zero of these four phone methods is accepted.
Original assertions and failed raw output remain. There is no automatic rerun,
new companion workaround, removed assertion or inferred whole-device pass.
The ActivityScenario/instrumentation startup cause still needs investigation;
the actual app UI checkpoint does not turn that run into a success.

The post-run byte guard detects the SQLite shared-memory WAL index changed.
The main database and WAL are byte-identical; all other 15 protected metadata
files and both model files are unchanged. Exact debug-only database/WAL/SHM
copies are hash-verified on the execution host, then read-only integrity_check
returns `ok` (schema 11). SHM is a transient index and is not counted byte-identical.
This review writes no phone database and reads no normal-app private database.

Separately, the owner reopens the earlier branch build's Request settings after
leaving local AI. The UI shows the stored model hash, context 4,096 and four
threads with no GGUF mappings; all four chat files remain unchanged. This is an
unloaded metadata read, not process-death/reboot or performance qualification.

## Pairing and remaining gates

Search source snapshot: `1ce13080e320cd5b3875d1f0e36a91e27ebd25ee228cb63ccb809996bf7bcf50`.

App SHA-256: `d9a0fc372eba6136ca62d1cb67fa0334ae4a0156d52ccc6d3216ed3d80711611`.

Companion SHA-256: `3594d52ad09626c109ded1576d81723a5d67759af05ebd5488e4b37f1cd8908f`.

Unsuccessful four-case native raw SHA-256: `69de75bdb3f55c6bdc877b13faf341c79d575352f1e72a9089bca033576b6420`.

This source separately passes 1,071 JVM cases and 40 selected native methods on
each emulator API26/API37, including those same four synthetic search cases.
[Source-bound search controls](2026-11-18-phone-history-search-controls.md).
The next organization foundation uses a newer app; it has no physical claim.

Final-head standard CI, phone instrumentation diagnosis, model/date/device
coverage, accessibility/lifecycle/resource/security and original-key production
upgrade/release remain. Rename/pin/archive UI, Markdown export and protected
backup/import remain. The full Hermes/upstream/shutdown/project/worker/automation/
integration audit is unchanged. No public release or Pi deployment is performed.
