# Reviewed helper checks and real offline phone acceptance

The helper ranks a bounded set of reviewed checks for six setup topics. Model
output must be a JSON array of one to three distinct, eligible IDs. Only the
matching code-defined Words entries, source references and typed execution
locations reach the screen. Raw model prose, commands, URLs and configuration
values are never rendered by this path. Ordinary local chat remains freeform.
Unknown/cross-topic IDs, duplicates, non-string values, extra text, malformed or
oversized JSON and incomplete generation leave the deterministic guide available.

The change follows actual LFM3 probes. Freeform summaries invented a setting,
returned the wrong language and attributed host CLI commands to the phone, even
after explicit host-only instructions. Those candidates and results are retained;
they are not accepted guidance. The earlier freeform helper contract is historical
and superseded here. This is an implementation correction, not an assertion waiver.

The knowledge revision is `signal-grove-setup-checks-2026-11-11`. Reviewed text
states whether work occurs in the Android app, on the Hermes host or on the
verified execution worker. Selection uses a fixed language-independent prompt;
the UI renders exact English/Chinese/pinyin translations. Twelve earlier compiled
selection probes produced valid lists; two Chinese-prompt CLI runs were slow.
They motivated keeping model selection independent of display language. Full
CLI wall times include startup/local-loopback service work, not just inference.

Observation sharing defaults off. Optional typed snapshots contain a capture
time, Demo mode, address scheme category, process bitness and local-model-loaded
state. No hostnames, paths, profile names, tokens, chat history or logs are sent
to the model. Server readiness is unknown. Known disabled Demo mode removes the
inapplicable Demo-off choice. Ranking does not establish the cause of a problem
or prove a server check was performed. Nothing executes a command or approves work.

## Real phone flow, preceding source-28 APK

The original-key debug app and test companion were installed with UIDs retained;
the production APK stayed unchanged. Android confirmed Airplane mode and disabled
Wi-Fi before and after native execution. Xiaomi reports the saved Wi-Fi setting
as 3; the independent `cmd wifi status` check confirms disabled Wi-Fi. An earlier
host precondition stopped before starting a test because it expected only 0.

One native boundary and all three model methods passed. They cover consecutive
replies, explicit/coroutine cancellation, recovery, context refusal, unload/reload,
metadata scanning and freeform helper-generation mechanics. Semantic review of
those helper answers exposed the host/phone issue above; native test success did
not close helper quality. The subsequent structured fixture instead checks valid
reviewed selections for all six topics and retains an exact Chinese chat assertion.

The owner then used the normal Android document picker and licence confirmation,
loaded LFM3 and sent a fresh local exercise with radios off. The imported private
file has the publisher SHA-256 and 1,049,615,680 bytes. Its generated reply is
persisted COMPLETE in independent phone history, bound to that model hash.

Observed foreground app VmHWM was 2,548,368 KiB (about 2.43 GiB); this includes the
app, not just the model. A normal return to MainActivity with no draft or active
generation released both model mappings in the same process. VmRSS fell from
2,540,440 to 468,652 KiB (about 458 MiB). Model/chat hashes were unchanged. The
owner reopened the saved conversation without loading weights; the completed
reply was visible and the model remained unmapped. Normal radios were restored.
One post-exercise battery observation was 100%, full/plugged in, 28.9 C. This is
not sustained thermal, energy, throughput or multi-device acceptance.

## Retained broader regression finding

On the exact source-28 APK, seven of eight ExistingSessionHistoryFlowTest cases
passed on API 37. The keyboard case initially timed out with the emulator's
software-keyboard-with-hardware setting at 0. A targeted run with that setting
temporarily 1 reached and passed the real IME/caption-bound checks, then failed
the native Tap.SINGLE injection-status assertion. The original setting was
restored in finally. Both terminal failures are retained; no assertion is relaxed
and no full history/lifecycle acceptance is claimed. This remains an original
Hermes-audit follow-up.

## Development snapshot and reproduction

The combined snapshot passes 916 JVM cases in 90 reports, 229 policy cases,
required debug/test APK assembly and both lint variants with zero errors
(50 debug / 49 release warnings). All nine LocalAiFlowTest cases pass on each
of API 26 and API 37, including exact reviewed EN/Chinese/pinyin instructions
and the execution-location label. One earlier fixture used a substring label
query that matched two correct texts; its retained failure was corrected by
asserting the exact three-language label. No application assertion was removed.
Own-root multilingual UI captures are reviewed at font scale 1.3.

The package service of the existing API 37 emulator failed during an earlier
test-companion install. A normal reboot preserved the AVD/configuration and
reconciled the old companion hash before the new upgrade. An isolated alternative
AVD failed startup because its image lacked the initial encryption partition;
it contributes no accepted tests. No phone security setting or application data
was cleared for recovery.

Run the required Gradle tasks through scripts/build-windows.ps1 with E: work,
SDK and signing roots, then LocalAiFlowTest on API 26/API 37. Physical acceptance
uses LocalNativeBoundaryTest and LocalModelPhysicalTest with the verified model
in a unique debug-private probe directory. Native C++ and NativeLocalEngine
source hashes are unchanged from the passing preceding offline phone snapshot.
The current helper's six compiled prompts are byte-identical to the successful
English phone selector preflight; display language only chooses reviewed text.
Those CLI probes do not replace the new APK's physical fixture.

Frozen source digest: `bb02c962b7d70e1222688a5033a7d28998998fe8590a65142d61b88a4bc46199`.

- app APK SHA-256: `6e109d90dc83e60ec1173c83a4c2d73e7c3eb915535f3e676204186ff4b72abf`
- test APK SHA-256: `694ba30a8cafd85fa97b3a3aa8bf95374e9b81975172ef4076a942b89afd042f`

Both APKs retain the original debug certificate. No model weights are bundled.
This is source-branch development acceptance, not a production APK release.

Still open: new-source physical upgrade/selector gates, another model family,
real HTTPS transfer/resume/cancellation, process/reboot/extended lifecycle, broader
devices/battery behavior, original Hermes integrations and exact-source release
acceptance. This is development work, not a published APK release.
