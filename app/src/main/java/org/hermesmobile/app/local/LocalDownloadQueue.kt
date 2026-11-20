package org.hermesmobile.app.local

import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Ordered, explicitly reviewed catalogue pins. Reading it never starts work. */
internal class LocalDownloadQueue(
    private val store: LocalModelStore, catalogue: PhoneModelCatalog = PhoneModelCatalog.Bundled
) {
    data class Pending(val models: List<CuratedPhoneModel>, val unavailable: Int)

    private val name = "download-queue"
    private val pins = catalogue.known.associateBy { model ->
        val identity = listOf(model.spec.url, model.bytes, model.sha256, model.licence).joinToString("\n")
        MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun pending(installed: Set<String>): Pending {
        val saved = read()
        val remaining = saved.filterNot { pins[it]?.sha256 in installed }
        if (remaining.size != saved.size) write(remaining)
        return Pending(remaining.mapNotNull(pins::get), remaining.count { it !in pins })
    }

    fun add(model: CuratedPhoneModel) {
        val saved = read()
        val pin = pin(model)
        if (pin !in saved) write(saved + pin)
    }

    fun assertReviewed(model: CuratedPhoneModel) {
        if (pin(model) !in read()) throw LocalModelStorageException("download_review_required")
    }

    fun remove(model: CuratedPhoneModel) {
        val saved = read()
        val remaining = saved - pin(model)
        if (remaining != saved) write(remaining)
    }

    private fun pin(model: CuratedPhoneModel) = pins.entries.firstOrNull { it.value == model }?.key
        ?: throw LocalModelStorageException("download_review_required")

    private fun read(): List<String> {
        val file = store.owned(name)
        if (!file.exists()) return emptyList()
        return try {
            if (!file.isFile || file.length() !in 2..MAX_BYTES) throw LocalModelStorageException("download_queue_unreadable")
            val lines = file.readLines(Charsets.US_ASCII)
            val entries = lines.drop(1)
            if (lines.firstOrNull() != "1" || entries.toSet().size != entries.size ||
                entries.any { !Regex("[a-f0-9]{64}").matches(it) }) throw LocalModelStorageException("download_queue_unreadable")
            entries
        } catch (_: IOException) {
            throw LocalModelStorageException("download_queue_unreadable")
        }
    }

    private fun write(entries: List<String>) {
        val bytes = (listOf("1") + entries).joinToString("\n", postfix = "\n").toByteArray(Charsets.US_ASCII)
        if (bytes.size > MAX_BYTES) throw LocalModelStorageException("download_queue_full")
        val target = store.owned(name)
        val staging = store.owned("download-queue.tmp")
        try {
            staging.outputStream().use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: IOException) {
            throw LocalModelStorageException("download_queue_unreadable")
        } finally {
            staging.delete()
        }
    }

    companion object {
        private const val MAX_BYTES = 4096
    }
}
