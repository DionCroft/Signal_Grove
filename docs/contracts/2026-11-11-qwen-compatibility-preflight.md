# Second model family: Qwen compatibility preflight

This checks an official optional import candidate. It does not add another
recommended download, establish embedded-app acceptance, or change Hermes/Spark.

| Field | Verified value |
| --- | --- |
| Publisher | [Qwen/Qwen2.5-0.5B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF) |
| Revision | `9217f5db79a29953eb74d5343926648285ec7e67` |
| File | `qwen2.5-0.5b-instruct-q4_k_m.gguf` |
| Bytes | 491,400,032 |
| SHA-256 | `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db` |
| Licence | [Apache 2.0, publisher file at that revision](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/blob/9217f5db79a29953eb74d5343926648285ec7e67/LICENSE) |

The public manifest's LFS size/hash matched the downloaded file and the USB copy
in a unique shell-owned phone directory. No model bytes enter Git or the APK.
The app's actual compiled GgufModelReader accepted the exact file on Windows:
qwen2 architecture, valid tensor ranges, declared 32,768-token trained context
and a 2,509-byte chat template. The bounded scan took 92 ms. That does not change
the app's 512–4,096 context presets or establish Android scan timing.

On the owner's ARM64 phone, the already verified official b12255 native CLI
loaded those bytes and returned exactly `Signal Grove Qwen OK`, exit 0, with no
remaining CLI PID. The finite request used four CPU threads, a 2,048-token context,
96-token output cap, seed 42, temperature 0 and `--offline`. Its 6.986-second wall
time includes loading/startup and is not a throughput benchmark or first-token
measurement. Normal Wi-Fi remained enabled and Airplane mode remained off; this
is not a radio-isolated offline proof. The CLI uses its temporary loopback service;
the embedded app's native library does not include a server.

No saved application model, phone history, production connection, remote model
selection or server data was changed. The imported-model UI still requires the
user to review the publisher licence. An import's own file hash is identity, not
publisher authentication; this probe does not create a catalogue-match label.

Still required: normal Android picker/import, embedded load/generation,
model-switch/history isolation, cancellation/recovery, lifecycle and upgrade
checks for this file. The six-topic helper's new APK phone gate also remains
open. LFM3's earlier real offline app proof is independent. Other admitted
architectures/quantizations are not accepted merely because these probes passed.
