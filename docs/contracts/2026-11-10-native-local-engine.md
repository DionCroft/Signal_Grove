# Optional native local inference foundation

The engine embeds CPU-only llama.cpp from pinned source
`940a30ee3b5593e1a66fe0aadc3b731989022552`, with the archive digest recorded in
CMake. The Windows wrapper keeps native staging, compiler caches and downloads
on E:. NDK 29, CMake 3.31.6 and a shared C++ runtime are explicit. ARM64 and x86_64
provide inference; 32-bit shims keep the Hermes client usable and report inference
unavailable. All packaged native libraries pass ELF alignment checks, including
16 KiB alignment for 64-bit libraries. This is not a physical 16 KiB-page test.

JNI uses registered handles, retained ownership, serialized operations and
monotonically numbered cancellation. Files must be directly within the supplied
private model root and pass bounded GGUF preflight. A request supplies its full
explicit history; transient contexts are recreated while weights remain loaded.
Context, threads, message bytes, output and token counts are bounded. UTF-8 output
handles split Chinese characters and emoji. Native prompt/path logging is disabled.
The engine exposes no Hermes client, credentials, actions or tool execution.

Sampling uses the upstream common sampler with deterministic temperature zero
and bounded scalar model recommendations. The official LFM3 file declares a 1.1
repetition penalty. Omitting it caused a fresh synthetic request to emit a
reasoning block despite the disabled-thinking template and exhaust its limit.
Same-library phone controls establish matching prompt/token formatting and show
the declared penalty completing the exact request. No assertion was relaxed.
Earlier failed guesses/runs and blocked activity fixtures remain in private
evidence. The final device harness foregrounds the existing empty debug activity
after a method starts, without ActivityScenario's blocked cross-package bootstrap.

Accepted checks on 10 November 2026:

- Both physical methods pass with the official SHA-256-pinned LFM3 weights:
  exact first and second replies, explicit cancellation, the exact recovery
  reply, coroutine cancellation, rejecting context overflow without output,
  unload, reload with a different context/thread preset and the exact reload
  reply. The separate metadata scan takes 38 ms and cancels at its checkpoint.
  The two-method instrumentation run reports 5.812 seconds; this is not a chat
  latency/throughput benchmark.
- Native handle/cancellation/disposal boundaries pass on the phone and API 26/37.
  A forced 32-bit API 26 process passes the unavailable-inference path and
  launches the core MainActivity. Existing debug UIDs are retained.
- The measurement build passes 895 JVM cases in 88 reports and both lint variants
  with zero errors (50 debug / 49 release warnings). Three UTF-8 decoder cases
  cover streamed character boundaries. The source-identical CI invocation changes
  pass the 229 policy tests. CI's standard emulator suite excludes only the
  separately required staged physical-model class; skipped/failed cases are not
  accepted as passes.
- Original signing keys, the private model hash and debug package identities
  remain intact. The production beta.8 APK hash is unchanged; no production-data,
  Pi, server-default or credential operation was performed.

The physical APK SHA-256 is
`3859d51dc73f339e1fa54203a0e8dbfc730b099987ec85cf2bae05d14df383d4`;
the test APK is
`db0d11e611f01d7abb6a023324177888349afed1a173f651861739f910ce97c3`.
The [provenance manifest](../on-device-ai-provenance.json) pins model bytes.
This development tree contains additional component work; the totals do not
constitute an exact-commit signed release.

Reproduction requires an explicitly staged private `probe-` directory with
twelve lowercase hex characters, containing the verified file as `model.gguf`.
Run `LocalNativeBoundaryTest`, then `LocalModelPhysicalTest` with instrumentation
argument `localModelProbeDirectory` naming that directory. Bind execution to the
exact signed app/test APKs and a specific debug device; never target production.
Keep the debug activity foregrounded and retain the complete status stream and
terminal result. Missing staging assumptions are not native acceptance.

The final embedded test used normal radios. Earlier CLI generation independently
passes with Airplane mode enabled and Wi-Fi disabled. Still open: app-level
offline chat, picker/download controls, background/process/upgrade recovery,
reviewed routing/handoff, grounded helper, broader devices/battery behavior,
complete standard instrumentation/release gates and the original Hermes audit.
