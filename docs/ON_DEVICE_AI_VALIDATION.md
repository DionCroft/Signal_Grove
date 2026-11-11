# On-device inference validation

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
