# Haru-4B Android preflight

The isolated Android arm64 CPU timing harness built on 27 November 2026. The
build linked the Haru model layout, VibeVoice codec, MeanFlow speech head, and
dialogue implementation. ELF inspection confirmed AArch64, Android's 64-bit
dynamic linker, and dependencies on the platform C, math, and dynamic-loader
libraries. This preflight covers compilation. Model loading, device memory,
speech quality and realtime operation are unmeasured.

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
Its published measurements are from desktop hardware. Haru advances one frame
every 133 ms, so sustained frame times still need to be measured on the Xiaomi.
[Published port measurements](https://github.com/nyosegawa/speech.cpp/blob/v0.11.0/docs/models/haru.md#speed).

## Device acceptance still open

The Xiaomi was absent from ADB during preflight. The binary remains on the
Windows execution host. The weights were identified from publisher metadata.
Check available RAM and storage on the handset and verify a no-model launcher
smoke test before staging or loading weights. Use representative synthetic or
consented speech and measure loading, first audio, frame delay distribution,
resident memory, heat, sustained playback and cancellation separately from
transcript accuracy. A go/no-go for phone interaction follows those checks.
