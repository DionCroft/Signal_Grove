package org.hermesmobile.app.local

import okhttp3.HttpUrl.Companion.toHttpUrl

/** Packaged publisher pins as reviewed for this version of Signal Grove. */
data class CuratedPhoneModel internal constructor(
    val name: String,
    val displayName: String,
    val repository: String,
    val revision: String,
    val filename: String,
    val bytes: Long,
    val sha256: String,
    val licence: String,
    internal val licenceAsset: String
) {
    init {
        require(Regex("[A-Z0-9_]{1,40}").matches(name))
        require(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(repository) &&
            repository.split('/').none { it == "." || it == ".." })
        require(Regex("[a-f0-9]{40}").matches(revision))
        require(Regex("[A-Za-z0-9_.-]+\\.gguf").matches(filename))
    }

    companion object {
        val LFM3 = CuratedPhoneModel(
            "LFM3",
            "LFM3-1.7B QAD Q4_0", "LiquidAI/LFM3-1.7B-GGUF",
            "cd0c74c575b5c416f3011b5b4c298077f26ca8c2", "LFM3-1.7B-QAD-Q4_0.gguf",
            1_049_615_680L, "43a7d09a0e9cba91b63eb7052488b9564569bddfd65754d2e426465e572a7452",
            "LFM Open License v1.0", "LFM3-LICENSE.txt"
        )
        val QWEN = CuratedPhoneModel(
            "QWEN",
            "Qwen2.5-0.5B Instruct Q4_K_M", "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
            "9217f5db79a29953eb74d5343926648285ec7e67", "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            491_400_032L, "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
            "Apache License 2.0", "Qwen-APACHE-LICENSE.txt"
        )
    }

    internal val spec = ModelDownloadSpec(
        "https://huggingface.co/$repository/resolve/$revision/$filename".toHttpUrl(), bytes, sha256
    )
}

/** A bundled revision remains eligible to finish a saved download after a replacement is released. */
class PhoneModelCatalog internal constructor(
    current: List<CuratedPhoneModel>,
    older: Map<CuratedPhoneModel, List<CuratedPhoneModel>> = emptyMap()
) {
    val current = current.toList()
    private val older = older.mapValues { (_, revisions) -> revisions.toList() }
    internal val known = this.current + this.older.values.flatten()

    init {
        require(this.older.keys.all { it in this.current })
        require(this.older.all { (latest, revisions) -> revisions.all { it.repository == latest.repository } })
        require(known.map { it.name }.toSet().size == known.size)
        // Two active recipes cannot share a partial file. Keep the old pin if only repository metadata changed.
        require(known.map { it.sha256 }.toSet().size == known.size)
    }

    internal fun olderVersions(model: CuratedPhoneModel): List<CuratedPhoneModel> = older[model].orEmpty()

    internal fun updateFor(previousSha256: String): CuratedPhoneModel? =
        older.entries.firstOrNull { (_, revisions) -> revisions.any { it.sha256 == previousSha256 } }?.key

    companion object {
        internal val Bundled = PhoneModelCatalog(listOf(CuratedPhoneModel.LFM3, CuratedPhoneModel.QWEN))
    }
}
