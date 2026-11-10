# Attribution and licences

Original code is MIT. No upstream Hermes source/branding or third-party fonts are copied. System fonts remain supplied by Android. The independent-project disclaimer applies throughout.

- [AndroidX](https://android.googlesource.com/platform/frameworks/support/) — Apache 2.0
- [Kotlin](https://github.com/JetBrains/kotlin), [coroutines](https://github.com/Kotlin/kotlinx.coroutines), [serialization](https://github.com/Kotlin/kotlinx.serialization) — Apache 2.0
- [OkHttp](https://github.com/square/okhttp), [Okio](https://github.com/square/okio) — Apache 2.0
- [Markwon](https://github.com/noties/Markwon) — Apache 2.0
- [CommonMark Java](https://github.com/commonmark/commonmark-java) — BSD 2-clause
- [Material icons](https://github.com/google/material-design-icons) — Apache 2.0
- [llama.cpp and GGML](https://github.com/ggml-org/llama.cpp/tree/940a30ee3b5593e1a66fe0aadc3b731989022552) — MIT; optional native local-inference engine. Its bundled common code uses nlohmann JSON (MIT), cpp-httplib (MIT), xxHash (BSD 2-clause), rotate-bits (MIT), public-domain SHA-1/SHA-256 implementations and sheredom subprocess headers (Unlicense). Their notices are included in the app and `docs/licenses/llama-cpp-dependencies.txt`. KleidiAI, OpenSSL and subprocess support are disabled in this build.
- Android NDK r29 LLVM C++ runtime — Apache 2.0 with LLVM exceptions and included component notices, reproduced from the official NDK in the app and `docs/licenses/llvm-ndk-r29.txt`. Native libraries share one C++ runtime.
- LiquidAI model weights are separate, optional downloads under their own [LFM Open License v1.0](https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF/blob/cd0c74c575b5c416f3011b5b4c298077f26ca8c2/LICENSE), with commercial-use conditions. No model weights are bundled or relicensed under the application MIT licence.
- [JUnit](https://github.com/junit-team/junit4) — EPL 1.0 (tests); [Hamcrest](https://github.com/hamcrest/JavaHamcrest) — BSD 3-clause (tests)

Full runtime licence texts are included in the app's Settings → Open-source licences and under `docs/licenses/`. Tooling and test libraries remain under their own licences.
