# Haru-4B Android preflight

The isolated Android arm64 CPU timing harness built on 27 November 2026. The
build linked the Haru model layout, VibeVoice codec, MeanFlow speech head, and
dialogue implementation. ELF inspection confirmed AArch64, Android's 64-bit
dynamic linker, and dependencies on the platform C, math, and dynamic-loader
libraries. The physical run below covers the verified checkpoint and CPU
frame timing.

| Input | Verified identity |
| --- | --- |
| Runtime | speech.cpp v0.11.0, commit 8d18924cd238b87c0297df112f7ff40783a6414f |
| Tensor backend | ggml commit 79b50d239d0b88852814c873db0d85d43ed66d9b |
| Target | Android API 26+, arm64-v8a, NDK 29.0.14206865, CPU backend |
| Benchmark binary SHA-256 | 92b12dd0ab2b8fc5ef179a4ea222aa8b6fd4f7ba3d25c9e9eb7bf5960e8349c6 |
| Published candidate | sakasegawa/Haru-4B-GGUF at fc026182f7bbfaa0400b2d44358a5e9e327b957f, Haru-4B-Q4_K.gguf, 4,522,673,920 bytes |
| Published candidate SHA-256 | e0e5fdd049b41c0510cac04f30468795565ec7f532aeb4552730d75406be19df |
| Published weight license | Apache-2.0 |

[Original Haru model and license](https://huggingface.co/higurashi-ai/Haru-4B) ·
[Port source](https://github.com/nyosegawa/speech.cpp/tree/v0.11.0) ·
[Pinned converted model](https://huggingface.co/sakasegawa/Haru-4B-GGUF/tree/fc026182f7bbfaa0400b2d44358a5e9e327b957f).

The port's quantization includes the language model, codec, and speech head.
The port's published measurements are from desktop hardware. Haru advances one
frame every 133 ms. The following tests use the physical Xiaomi.
[Published port measurements](https://github.com/nyosegawa/speech.cpp/blob/v0.11.0/docs/models/haru.md#speed).

## Physical Xiaomi result

Android 17 launched the exact verified arm64 binary. The trial used the
verified Q4_K file, CPU backend, a 12-second session window, no voice
reference, and 8.895 seconds of locally synthesized speech. The runtime
completed successfully with Airplane mode enabled, Wi-Fi disabled, and no
default network at both ends. No app installation was involved.

| Measurement | Observed |
| --- | --- |
| End-to-end process time | 20.48 s, including load and warm-up |
| Warm start to first decoded frame | 145.7 ms |
| Frame duration budget | 133.3 ms |
| Frame processing p50 / p90 / p99 | 216.0 / 237.1 / 286.4 ms |
| Late frames | 66 of 66 |
| Processing speed | 0.61× real time |
| Peak sampled resident memory | 4.39 GiB |
| Available device memory before / lowest sample | 6.31 / 3.03 GiB |
| Battery temperature before / after | 26.2 / 26.5 °C |
| Android thermal severity before / after | 0 / 0 |

The CPU run passed compatibility for the checkpoint, input codec, speech
head, and output decoding on this handset. **No-go for live full-duplex
conversation with the tested CPU build.** Every frame missed its deadline.
The harness measured the input encoding and output decoding separately.
Those steps took 72.7 ms and 69.8 ms per frame,
respectively, in the longer run below. The result covers codec timing.
Audible playback, dialogue quality and Vulkan performance remain unmeasured.

## Longer offline run and cancellation

The same pinned CPU build processed 28.284 seconds
of synthetic speech in a 32-second session window. The input
repeated the original sample with two 0.8-second pauses.

| Measurement | Observed |
| --- | --- |
| Wall time including load and warm-up | 53.47 s |
| Warm start to first decoded frame | 145.5 ms |
| Frame processing p50 / p90 / p99 | 226.8 / 240.5 / 253.7 ms |
| Late frames | 212 of 212 |
| Processing speed | 0.59× real time |
| Peak sampled resident memory | 4.40 GiB |
| Lowest sampled available device memory | 3.77 GiB |
| Battery temperature before / peak / after | 30.0 / 32.1 / 32.1 °C |
| Peak sampled Android thermal severity | 0 |

A second run received SIGTERM 7.32 seconds
after launch, with the model resident. ADB reported the exit after
0.380 seconds, and the subsequent PID check confirmed
the process was gone. Available device memory increased from 4.56 GiB at cancellation
to 8.94 GiB at the next sample. This
measured cancellation of the native benchmark process. In-app speech
session cancellation remains unmeasured. Both tests stayed offline and
the phone's network and temporary storage were restored afterward.

The earlier radio precheck stopped before loading weights because a shell
diagnostic appended a broken-pipe line to the connectivity result. Its
radio changes were restored. The corrected run restored networking and
removed the temporary phone files. Signal Grove’s screenshot OCR and
recording transcription use dedicated offline engines.
[Offline media validation](../VALIDATION.md#offline-media-capture-and-haru).
