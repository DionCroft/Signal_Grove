# Native process interruption and host restart recovery

The opt-in Android fixture uses the production controller, private model/chat
stores and real embedded JNI inference with the publisher-verified Qwen2.5-0.5B
Q4_K_M model. It does not generate a simulated completion or patch recovery.

The host first waits for actual nonblank native output, a live process and model
mapping, and a fsynced pending conversation marked GENERATING. It force-stops only
the debug package on the selected Android 17 x86_64 emulator. The process exits
and the pending file remains byte-identical. This deliberately interrupted
preparation is not counted as a passed JUnit method. Partial streamed text is
not promised to survive: only the already-persisted pending exchange is checked.

A new process opens that exact test directory. Startup marks the pending turn
INTERRUPTED. Reading it loads no weights and makes no native generation call.
Only an explicit load and a fresh question invoke inference. The original
interrupted exchange remains unchanged and is excluded from the recorded native
user input. The new completed reply must equal the actual native output and is
saved before unloading. The native result must be COMPLETE, with exactly one
explicit load and generation.

The requested guest reboot timed out, followed by read-only shell and console
probe timeouts. Its outcome was kept unknown; no generation or preparation was
replayed. A bounded normal console-close attempt also timed out. Only the exact
identified hung emulator processes were stopped on the host and the same AVD
was relaunched with its existing storage, original 4 GiB runtime allocation and
unchanged configuration. No wipe, snapshot restore or ADB-server reset was used.
This host restart changes the kernel boot ID. Full model/chat hashes,
installed APKs and package UIDs remain unchanged. A separate post-host-restart read
returns exactly the earlier interrupted and completed exchanges without loading
weights, generating or rewriting the transcript. Explicit test cleanup deletes
only its model copy and preserves the transcript and original staged input.
Shared preferences, preference datastore and the AVD configuration are retained.

## Retained failed and unknown attempts

The first cold-recovery method failed an exact wording assertion: the real Qwen
model paraphrased the synthetic request. The complete failed log and resulting
files are retained. Read-only reconciliation found INTERRUPTED/COMPLETE states,
no mapped model and no reboot. That run is not counted as a pass or retried in
place. The corrected lifecycle oracle compares the durable reply with observed
native text and checks native completion and exact request inputs. It makes no
new instruction-following or general answer-quality claim.

## Acceptance and reproduction

Two successful Android methods and one intentionally interrupted preparation
are verified on Android 17 x86_64, using a host restart after an unaccepted guest
reboot timeout. Normal guest reboot remains open. This test-only chunk retains
production main, debug and native sources from the stopped-transfer correction. Required checks
pass 921 JVM cases in 90 reports, 229 policies, both APK assemblies and zero-error
lint (50 debug / 49 release warnings). The original debug signer, four packaged
ABIs and native segment alignment are verified.

Frozen source digest: `4bb3b6fe228bbc6e7c4bac5e1574f419a96f82f5c6a6c0fbe19883c0406912e0`.

- app APK SHA-256: `1718b3e8a5ca6fc15e362809cca03026635dc258297d71bcefc91ad46ee95919`
- test APK SHA-256: `78de930ccc6472a6f9f2ee8a8fd296fe3882a0ee23e51fb6613874bd7b931e25`

Stage the exact official Qwen file documented in
[the provenance contract](2026-11-11-qwen-compatibility-preflight.md) as qwen.gguf
inside a unique app-private other-model-probe-<12 hex digits> directory. Set
localOtherModelProbeDirectory to that directory when selecting
LocalProcessRecoveryIntegrationTest#prepareInterruptedNativeGeneration. Without
explicit staged input, this opt-in fixture is skipped.

The host must wait for native-output-ready-for-external-force-stop and independently
verify the marker's PID, mapped model and pending file hash before force-stopping
the debug package. Save its exact interruption-<UUID> directory and chat ID;
never rerun a preparation against uncertain existing results. In a new process,
run coldRecoveryMarksInterruptedAndOnlyAnExplicitFreshSendGenerates with
localProcessRecoveryProbe set to that exact directory. After a successful terminal
result, save the full chat hash. Independently record whether a normal guest
reboot actually completes; a transport timeout is unknown until reconciled.
This accepted run instead required the scoped host restart described above,
followed by a changed boot ID and unchanged files/packages. Run
rebootKeepsCompletedAndInterruptedHistoryWithoutAutomaticLoad with the same probe
and localRecoveredChatSha256 set to that hash. A failed or unknown phase requires
inspection before any new action. Preparation has bounded waits and releases its
native callback in finally if the external interruption does not arrive.

This is Android 17 x86_64 process/host-restart component recovery acceptance.
Normal guest reboot remains open. It does not close phone
upgrade/helper UI, import/download process death, general activity/background or
OEM battery behavior, all corruption-recovery UI, the existing history keyboard
finding, other device coverage, any original remote Hermes audit requirement or
exact-source signed-release acceptance. The pending phone helper installer files
and production beta.8 app are unchanged. No APK release is claimed.
