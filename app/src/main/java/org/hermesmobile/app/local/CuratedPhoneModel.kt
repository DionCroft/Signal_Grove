package org.hermesmobile.app.local

import okhttp3.HttpUrl.Companion.toHttpUrl

/** Packaged publisher pins as reviewed for this version of Signal Grove. */
enum class CuratedPhoneModel(
    val displayName: String,
    val repository: String,
    val revision: String,
    val filename: String,
    val bytes: Long,
    val sha256: String,
    val licence: String,
    internal val licenceAsset: String
) {
    LFM3(
        "LFM3-1.7B QAD Q4_0", "LiquidAI/LFM3-1.7B-GGUF",
        "cd0c74c575b5c416f3011b5b4c298077f26ca8c2", "LFM3-1.7B-QAD-Q4_0.gguf",
        1_049_615_680L, "43a7d09a0e9cba91b63eb7052488b9564569bddfd65754d2e426465e572a7452",
        "LFM Open License v1.0", "LFM3-LICENSE.txt"
    ),
    QWEN(
        "Qwen2.5-0.5B Instruct Q4_K_M", "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
        "9217f5db79a29953eb74d5343926648285ec7e67", "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        491_400_032L, "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
        "Apache License 2.0", "Qwen-APACHE-LICENSE.txt"
    );

    internal val spec = ModelDownloadSpec(
        "https://huggingface.co/$repository/resolve/$revision/$filename".toHttpUrl(), bytes, sha256
    )
}
