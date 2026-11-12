# Current-source native interruption and cold recovery

The beta.9 candidate passes the original
`LocalProcessRecoveryIntegrationTest.coldRecoveryMarksInterruptedAndOnlyAnExplicitFreshSendGenerates`
case on an owned API37 x86_64 emulator. The physical phone is excluded from this
stage. All original test source and native timing assertions are unchanged.

The reviewed Qwen2.5-0.5B Q4_K_M input is 491,400,032 bytes, SHA-256
`74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`.
An independent binary stream verifies every input byte before the corrected
trial. The native preparation also checks the full input hash before creating
its new isolated fixture. No owner conversation or model is used as a test root.

## Actual interruption and positive recovery

Preparation imports and loads the real model, starts JNI generation, emits
nonblank native text and atomically records its live PID/chat/model witness.
The host verifies the durable GENERATING record, chat hash and model mapping,
then force-stops only that exact debug process. The PID disappears and the
persisted GENERATING record is unchanged. Native output reports the expected
process crash, not an OK result. This preparation is intentionally interrupted;
it is not counted as a positive JUnit test.

The fresh-process recovery case passes its complete native envelope: one exact
start and finish, OK(1 test), terminal-1 and no skipped/failed case. It verifies:

- Recovery changes only the persisted turn state to INTERRUPTED.
- Opening history performs zero native loads or generations, and does not replay.
- Only an explicit fresh question loads/generates; the earlier turn is retained.
- Real native output completes and persists, then unloading releases the model.

JUnit reports 9.344 seconds; the complete ADB command takes 12.636 seconds. Before
the subsequent reboot request, all 288 protected global file hashes and both
debug/test APK hashes and UIDs remain. A full binary read independently verifies
the recovered 491,400,032-byte installed model against its publisher hash.
These timings are individual emulator observations, not phone performance claims.

Frozen build-input digest:
`47ceba3875b9479fcfb7958a885ebe3e06a0f5fdad5f780bbc7356eb0a4f8a8c`.
APK/signature/JVM/lint scope remains the [cancellation candidate](2026-11-12-download-cancellation.md).
Preparation raw-log SHA-256 is
`695e3e4f0c9814d5793dd09e5c2a86e979817368ab7fad41c2ef6167c8f2cfde`;
the cold-recovery log is
`4cf68c16f117813debbaf119acfb1fd0dcd6c2c5163fb203245629b693fc488b`.

## Retained failures and unverified reboot

An earlier observer waits on an unrelated empty activity, gets a launch timeout
and exits before delivering the planned interruption. That original native
preparation subsequently times out and stores FAILED; its log and fixture remain.
The corrected observer removes that UI wait. It uses a fresh isolated fixture,
preserves the failed one and keeps the original native/witness limits.
An earlier slow standalone hash observation and its owned orphan reader are
also reconciled without changing the model or starting a native test.

One normal guest reboot is requested after the successful cold case and full
pre-reboot hash checks. The 240-second observation ends without a new readable
boot ID. Re-observation confirms the same host VM is live, ADB is offline and
its console read also times out. The reboot request is not repeated, the host
VM is not restarted and no data is reset. This observation timeout does not
prove that no reboot happened. Post-reboot model/history retention and the
`rebootKeepsCompletedAndInterruptedHistoryWithoutAutomaticLoad` method remain
unverified; the final method is not executed or counted as passed.

Reconcile that same VM before further recovery actions. Final standard CI,
API26 keyboard preparation, compatibility, production-key phone upgrade,
SBOM/provenance and public APK checks remain. No physical-phone action or public
release occurs here; the full original audit stays active.
