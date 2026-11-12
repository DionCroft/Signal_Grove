# On-device inference validation

## 12 November 2026 - full foreground phone model download passes

The original HTTPS resume case passes on the retained-data beta.9 debug phone:
3 ms cancellation, 8,451,250-byte retained prefix without automatic resume, then
full 1,049,615,680-byte download/hash/atomic installation in 27,010 ms. Scoped model
deletion passes; both owner models/chat/settings/UIDs/APKs remain. Six sampled
foreground/unlocked/awake/kernel-freezer checks pass. The temporary USB-awake
setting is restored. Both HTTPS methods now have positive physical evidence;
earlier timeout failures remain. [Exact source, case and scope](contracts/2026-11-12-foreground-model-download.md).
Process/reboot, complete final standard CI, production upgrade, compatibility and
publication remain open; this is not a new public release.

## 12 November 2026 - cancellation watcher fix and retained resume failures

Both no-free-I/O-thread regressions fail before the fix and pass after it. Full
build:1006 JVM /96 reports, no failures/errors/skips; lint0 errors51/50 warnings;
original signers/native/page/DEX checks pass. Real HTTPS cancellation takes3 ms
on the phone and18-34 ms on API37 emulator; explicit discard passes on both.
Resume still times out at the unchanged300s bound; partials668,174,446 and
23,246,323 bytes remain. Phone kernel-freezer recovery is documented, not accepted
as a clean resume pass. Original models/chat/preferences/UIDs remain; production
beta.8 is unchanged. [Exact fix, hashes, scope and gates](contracts/2026-11-12-download-cancellation.md).

The current hosted API26 shard1 fails keyboard preparation before inventory;
complete candidate standard union remains open. Earlier scoped checks below
remain historical. No new public release is claimed.

## 12 November 2026 - uncompressed DEX beta.9 candidate

A stored API37 startup ANR identifies compressed DEX inflation before app classes
initialize. The candidate stores every DEX uncompressed/aligned and increments
the planned release to beta.9/code112 with both original signing identities.
1004 JVM /96 reports, build/signature/native alignment and zero-error lint51/50
pass. In-place emulator upgrades retain UIDs and351 API37 /66 API26 scoped core
hashes; the API26 host timeout is reconciled from native installer success without
replay. All10 API26 local-AI UI cases pass, including bilingual large-text/helper
review. [Exact candidate and failure scope](contracts/2026-11-12-uncompressed-dex-candidate.md).

The new real-HTTPS batch retains two timeout failures, not acceptance: cancellation
10s and resume300s (partial384 MB). The existing run is observed to termination
without restart after a foreground-helper failure. All assertions stay unchanged.
Physical beta.9, process/reboot, complete standard CI, compatibility review and
original-key publication remain. Fresh reports flag stable40/current main100
handlers. OSV finds no matching known advisories for150 Maven modules; the pinned
native commit query is similarly limited to indexed knowledge. Public beta.8 and
the owner's phone are unchanged; this candidate is not a downloadable release.

## 12 November 2026 - six final-source physical offline cases and retention

The original-debug-key candidate passes six exact native cases on the owner's
physical ARM64 API37 phone with Airplane mode/Wi-Fi/default-network observations
proving both batches offline. Real LFM generation/cancellation/recovery/unload,
all six grounded helper topics and Chinese text, committed-import cancellation,
Qwen chat/history/deletion and two-family history isolation pass unchanged memory
and timing guards. Model imports take 649-1438 ms. Six of eleven external methods
are now accepted; two HTTPS and three process-recovery methods remain. The earlier
emulator failures are retained. [Cases and observations](contracts/2026-11-12-physical-offline-model-acceptance.md).

Normal phone networking is restored. Original model/preference/package identities
and earlier chat are retained; the owner's confirmed new turn is preserved.
SQLite changes are limited to cached-job freshness and framework bookkeeping.
Encrypted scoped recovery verifies; only owned duplicate test inputs are removed.
The production app is unchanged. Short USB-charging observations are not an
endurance, sustained thermal or throughput benchmark. No new public APK is claimed.

## 12 November 2026 - retained upgrade and offline model-gate failure

Exact original-debug-key upgrade retains both UIDs, all 1,544 protected files
and three model hashes. Actual offline API37 emulator run: one Qwen lifecycle
pass and two LFM import timing failures, not complete model acceptance. Core
database/preferences/earlier transcripts/models survive; five platform bookkeeping
changes are independently reconciled. Original network is restored. Encrypted
recovery and failed roots/logs remain. [Cases, timings and scope](contracts/2026-11-12-retained-model-upgrade.md).
No timeout or memory guard is weakened. Final-source physical/model and the
original-production-key downloadable APK remain in the active goal.

## 11 November 2026 - reviewed unsent questions can move offline

Chat can copy a reviewed new text-only question into a separate phone draft,
keeping the Hermes draft and both histories unchanged. Exact revision/context
and stored-receipt checks reject attempted/staged retries, images, unknown or
active work and response-only endings. A readiness-only failure leaves an unsent
question eligible. One-use expiring in-memory delivery carries no text in intents
and never loads a model, generates, sends or transfers credentials/approvals.

Required checks pass 931 JVM / 91 reports, 229 policies and zero-error lint,
with both APK assemblies. Selected Android method sets pass on API 26/API 37,
including native keyboard/control bounds and an Android Cancel tap with all
three languages at font scale 1.3. Settings are restored and screenshots reviewed.
The wrapping-dialog finding and earlier fixture failures are retained. Exact
scope, counts and hashes are in [the routing contract](contracts/2026-11-11-reviewed-phone-draft.md).
Exact-source physical-phone and full original audit/release checks remain open.

## 11 November 2026 - real native process and host restart recovery

A new opt-in fixture verifies actual native output and a durable pending chat
before the host force-stops the exact debug process on Android 17 x86_64. Cold
startup marks the old turn interrupted without loading or replaying it. Only an
explicit fresh send generates; its stored reply equals the real native output.
A restart of the preserved AVD changes the boot ID and retains model/chat hashes,
APKs, package UIDs, preferences and AVD configuration. Reading afterward performs no
native load/generation and leaves the transcript unchanged. The guest reboot
command and console close timed out; only the exact hung emulator was stopped
on the host and relaunched with its original storage/configuration. Normal
guest reboot is not accepted. Those timeout receipts are retained.

Two methods pass; the deliberately interrupted preparation is not a JUnit pass.
The first attempt's stochastic exact-wording failure is retained and reconciled
without replay. The corrected output oracle is described in
[the recovery contract](contracts/2026-11-11-native-process-recovery.md).
Required checks pass 921 JVM / 90 reports, 229 policies, both APK assemblies and
zero-error lint (50 / 49 warnings). This is component recovery acceptance, not
physical-phone, transfer process-death, full UI or full release acceptance.
The main/debug/native application sources and pending phone candidate are unchanged.

## 11 November 2026 - stopped imports reflect committed files

The controller reconciles actual models/partials before returning a stopped
transfer to idle, handles provider close exceptions as cancellation when the
coroutine is cancelled, and safely records reconciliation failures. Three new
JVM regressions reproduce the previous missing-model, false-error and unreported
metadata-error behavior before the fix. A real 1 GB Android import now passes the
controlled publication/Main-dispatch cancellation boundary without reopening its
source, loading weights or creating a chat. Fresh real HTTPS checks still pass
on API 26/API 37. Required checks pass 921 JVM / 90 reports, 229 policies and
zero-error lint (50 / 49 warnings), with both APK assemblies. These three new
Android executions are component acceptance; process/reboot, phone and full
release gates remain open. See [scope and exact hashes](contracts/2026-11-11-model-stop-reconciliation.md).

## 11 November 2026 - live HTTPS transfer and stopped-read correction

Real publisher HTTPS checks pass on Android emulators: cancellation/partial
retention and explicit discard on API 26, plus cancellation, cold-controller
retention and explicit full resume on API 37. The full LFM3 file matches the exact
size and publisher checksum before atomic installation. A real API 37 failure
exposed cancelled blocking reads escaping as I/O errors; the narrow cancellation
checkpoint now fixes that symptom with the original Android test APK unchanged.
Paired JVM regressions retain active I/O failures and their cause.

The corrected snapshot passes 918 JVM / 90 reports, 229 policies, both APK
assemblies and zero-error lint (50 / 49 warnings). Phone UI/process/reboot and
full release acceptance remain open. Earlier failed runs and the corrected
exception-identity oracle are retained. See
[scope, reproduction and hashes](contracts/2026-11-11-live-model-transfer.md).

## 11 November 2026 - real two-family Android integration

Three fresh integration executions pass: Qwen import, embedded generation,
unloaded history and deletion on API 26/API 37, plus LFM3-to-Qwen switching on
API 37 with 8 GiB test RAM. The production controller/store/JNI are used with
actual pinned weights and memory guards. A mismatched chat is refused without
changing its file; both model-bound histories survive explicit model deletion.
The original emulator RAM/configuration/package IDs are restored or retained.

Required checks pass 916 JVM / 90 reports, 229 policies and zero-error lint
(50 / 49 warnings), with both APK assemblies. Main/debug application and native
sources are unchanged from the preceding local UI measurement. These three new
native executions do not establish a full instrumentation/release pass, a normal
Qwen picker flow, physical second-model acceptance or process-death behavior.
See [scope, model provenance and exact APK hashes](contracts/2026-11-11-two-family-android-integration.md).

## 11 November 2026 - second model family preflight only

The official Qwen2.5-0.5B Q4_K_M file is pinned and publisher-hash verified.
The app's compiled metadata reader accepts it on Windows, and the verified phone
native CLI loads it and returns an exact synthetic reply. Normal phone radios
remained enabled. This is separate parser/native feasibility; Android import,
embedded generation and model-switch/lifecycle acceptance remain open. See the
[scope and provenance](contracts/2026-11-11-qwen-compatibility-preflight.md).

## 11 November 2026 - real offline app flow and reviewed helper checks

The owner imported LFM3 through Android's normal document picker and separate
licence confirmation, loaded it and received a newly typed local reply with
Airplane mode on and Wi-Fi independently confirmed disabled. The complete reply
was saved in phone history, bound to the publisher-matching model hash. A normal
return to the main app released both model mappings in the same process; VmRSS
fell from about 2.42 GiB to 458 MiB. The model and chat hashes were unchanged.
The owner reopened the completed reply without loading weights. Normal radios
were restored. Foreground VmHWM was about 2.43 GiB; a short charging temperature
observation does not establish sustained thermal or battery performance.

That preceding APK passes one native boundary and all three physical methods.
Later semantic review found its freeform helper could misattribute host commands
to the phone. The new helper instead permits only strict selection of reviewed
checks with typed execution locations; the source guide remains available.

The new combined development snapshot passes 916 JVM / 90 reports, 229 policy
cases, required APK assemblies, zero-error lint (50 / 49 warnings) and all nine
local UI cases on each of API 26/API 37. Exact-source phone upgrade and selector
checks remain pending. The retained broader history keyboard/native-input
failure remains open; no full instrumentation or release acceptance is claimed.
The production beta.8 app remains unchanged. See the
[reviewed-helper contract and exact hashes](contracts/2026-11-11-reviewed-local-checks.md)
and [on-device usage guide](ON_DEVICE_AI.md).

## 10 November 2026: physical CPU feasibility

Accepted scope: the official native llama.cpp CLI, with the official LiquidAI
LFM3-1.7B QAD Q4_0 weights, produces text on the owner's physical Android phone
with Airplane mode enabled and Wi-Fi disabled. This is a feasibility result,
not acceptance of an embedded Signal Grove feature or the remaining Hermes audit.
The installed production application and its data were not changed by this probe.

Provenance is pinned in [the manifest](on-device-ai-provenance.json). Model bytes
were checked against the official Hugging Face LFS SHA-256, and the Android CPU
archive against the GitHub release asset's SHA-256. Every transferred native file
and the model were hashed again on the phone before execution. Runtime identity
reports `b12255-940a30ee3`. This is an upstream prerelease build, deliberately
pinned because the stable version's release metadata had no Android binaries.
The weights are optional downloads under the LFM Open License v1.0; they are
not bundled or relicensed as MIT. The model licence must be presented separately
in the eventual manager, including its commercial-use conditions.

The phone reports Android 17 / API 37, ARM64 only, SM8850, 15,447,612 KiB total
RAM and approximately 7.3 GiB available at the initial snapshot. No root or
unrelated process termination was used. A unique shell-owned test directory
held the model/runtime. The official CLI uses a temporary loopback HTTP service
for its finite request and shuts it down on exit; later verbose controls clarified
this implementation detail. No public listener or persistent service was deployed.

| Check | Actual result |
| --- | --- |
| Offline exact reply | `Signal Grove offline OK`, exit 0; Airplane mode 1 and Wi-Fi 0 before and after |
| Offline longer reply | Completed, exit 0; reported generation 49.0 tokens/s |
| Offline cancellation | Interrupted the exact generating PID after visible output; exit 0, PID gone; observed signal-to-terminal interval about 0.66 s |
| Three-repetition benchmark | CPU only, four threads, batch/microbatch 128; prompt 128: 39.061837 +/- 0.332385 tokens/s; generation 128: 51.436483 +/- 0.050385 tokens/s |
| Memory | Largest recorded process high-water mark 2,140,392 KiB (about 2.04 GiB) during the 2,048-token-context CLI tests |
| Bounded temperature | Battery sensor 26.5 to 26.6 C across offline reply/cancellation; 27.1 C before/after the 22.25-second benchmark |
| Battery | 91%, charging throughout these short tests; no energy-use or long-duration battery claim |

Generation used four CPU threads, context 2,048, batch/microbatch 128, no GPU
layers, temperature 0, seed 42, reasoning off and explicit `--offline`. Each
request was finite and single-turn. Memory was sampled from the owned PID's
`/proc/status`; a very short process can exit between samples, so this is an
observed high-water mark, not an exhaustive system-wide peak measurement.
Observed first stdout is not time to first token: the CLI prints a loading banner.
The benchmark uses its own prompt/generation contexts and is separate from the
2,048-context functional runs. Its reported rates do not predict all chat lengths.

One first offline run completed between the monitor's receipt and `/proc` reads.
The monitor failed rather than claiming success. Its exact saved output, exit 0,
vanished PID and unchanged offline radio state were then reconciled read-only;
the request was not replayed. The failure receipt remains beside the separate
reconciliation receipt in private E: evidence. Remaining tests used a monitor that
handles process exit during sampling. No ambiguous request was silently retried.

The ungrounded setup prompt produced inaccurate Tailscale instructions. It is
not an accepted onboarding answer. The helper needs a versioned, verified guide,
explicitly redacted observations and evaluation against that evidence. Successful
text generation does not establish reliable troubleshooting advice.

Reproduction uses the pinned ARM64 CPU archive, verified model and an explicit
ADB serial in a fresh `/data/local/tmp/signal-grove-lfm3-<unique-id>` directory.
Set `LD_LIBRARY_PATH` to the extracted runtime directory, then run:

```sh
./llama-cli -m /path/to/model.gguf --offline -c 2048 -t 4 -tb 4 \
  -b 128 -ub 128 -ngl 0 -n 96 --temp 0 --seed 42 --reasoning off \
  --single-turn --simple-io --no-display-prompt --no-warmup \
  -p 'Reply with exactly: Signal Grove offline OK'
./llama-bench -m /path/to/model.gguf --offline -p 128 -n 128 \
  -r 3 -t 4 -b 128 -ub 128 -ngl 0 -o json
```

Disable radios explicitly for the offline proof and restore the owner's prior
settings afterward. Save the exact PID, command, output and terminal receipt;
inspect those records before any recovery. Cancellation may signal only that
verified process. Keep serials, full device dumps and private paths out of Git.

The embedded native foundation subsequently passes its bounded physical gate;
see [the exact checks and APK hashes](contracts/2026-11-10-native-local-engine.md).
That test used normal radios and does not replace the earlier offline CLI proof.

Still required: app-level offline chat, app background/process
recovery, model-management error paths, independent local history, routing and
handoff isolation, grounded helper acceptance, representative devices/emulators,
and complete signed-release gates. This result closes none of those checks.
