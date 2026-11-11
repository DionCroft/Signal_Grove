# Two-family Android import, generation and history integration

Accepted scope: the production LocalAiController, LocalModelStore,
LocalConversationStore and default NativeLocalInference/JNI operate on the pinned
Qwen2.5-0.5B Q4_K_M file on API 26/API 37. The same production path also switches
between that file and LFM3-1.7B QAD Q4_0 on API 37. The new fixture uses no fake
engine, injected answers, Hermes client or credential store. It writes only
unique test-owned model/history directories and performs no network download.

| Executed method | API 26 x86_64 | API 37 x86_64 |
| --- | --- | --- |
| verifiedQwenImportChatHistoryAndDelete | Pass; 2 GiB configured RAM | Pass; 8 GiB configured RAM |
| verifiedLfmQwenSwitchKeepsHistoryBoundToEachFile | Not run | Pass; 8 GiB configured RAM |

The Qwen method proves that refused licence consent never opens the stream or
installs a model. Reviewed import copies the real file; architecture, declared
context, hash and size match. An actual embedded generation returns exactly the
requested synthetic phrase and persists COMPLETE under the model hash. Explicit
unload retains the file/history. A new controller reads the same transcript
without loading weights and without changing its file hash. Deleting the model
retains that transcript and the original input file. Controller recreation is
not Android process-death or reboot acceptance.

The switch method imports both publisher-matching files, loads Qwen and generates
its first saved reply, then loads LFM3. Sending from the still-Qwen-bound chat is
rejected with the original transcript/hash unchanged and no second history entry.
An explicit new LFM3 chat receives its own reply. Loading Qwen again and opening
its original chat continues the correct history. Deleting the other model keeps
the current one loaded; deleting the current model unloads it and retains both
histories. The real ActivityManager memory estimate is used throughout; no
resource guard is replaced with an unlimited test value.

Model provenance remains pinned in the [LFM3 manifest](../on-device-ai-provenance.json)
and [Qwen preflight](2026-11-11-qwen-compatibility-preflight.md). Each fixture input
is verified again against its exact size and publisher SHA-256. The initial API
37 ADB copy was denied when run-as read external storage. That attempt is retained;
a read-only reconciliation confirmed the existing public transfer and absent
private destination. A standard stdin relay then populated a fresh private
partial, verified it and atomically renamed it. No storage permission, SELinux
setting or existing model was changed. API 26's direct owned copy succeeded.

Both runs verify the installed app/test SHA-256 and the fixture's frozen source
before launching. All expected methods have terminal success and no skips.
The APKs retain the original debug signer. The API 37 AVD kept its configuration,
packages and IDs, ran at 8 GiB for the switch test and was restored to its original
4 GiB runtime allocation afterward. The original phone/production app was not
used or changed by these emulator runs.

Required builds pass 916 JVM cases in 90 reports, 229 policy cases, both APK
assemblies and zero-error lint (50 debug / 49 release warnings). All main/debug
application and native source files are byte-identical to the preceding
nine-case-per-emulator local UI measurement. These are three new integration
executions, not a rerun of the full instrumentation or release suite.

Frozen source digest: `47fff72e18fe6c9ba69ea58a91b57fbea93adf3d02112ace195f39006656df9e`.

- app APK SHA-256: `76c2050cdd091bf44319b757ba07d2385227eca66f83a188639442ebc7100960`
- test APK SHA-256: `5632b0a96521a70e23124e7f625b42ed7fbc14fcf6b7d0a1f183abc0da1cc52a`

Reproduce by staging the exact official files as qwen.gguf and lfm3.gguf under
an explicitly created no-backup directory named other-model-probe-<12 hex digits>.
Run LocalModelFamilyIntegrationTest with the localOtherModelProbeDirectory
instrumentation argument. The switch method needs enough real available RAM for
LFM3's existing conservative load guard. API 26 was targeted to the Qwen method
only. Without explicitly staged inputs these opt-in large-model methods are
skipped; an ordinary CI run therefore does not establish model acceptance.

Still open: Qwen's normal Android document picker and physical-phone import/chat/
switch checks, cancellation/recovery for that second file, live HTTPS transfer /
resume / cancellation, extended background/process/reboot behavior and broader
devices. The staged phone helper update still needs its manual installer and
physical selector/Chinese fixture. Neither these component tests nor LFM3's
preceding real offline phone flow closes the original Hermes audit or signed
release gates. This is development-branch evidence, not an APK publication.
