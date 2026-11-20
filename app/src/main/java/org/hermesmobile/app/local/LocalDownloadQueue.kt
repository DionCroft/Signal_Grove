package org.hermesmobile.app.local

import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Ordered, explicitly reviewed catalogue pins. Reading it never starts work. */
internal class LocalDownloadQueue(private val store: LocalModelStore) {
    private val name = "download-queue"
    private val pins = CuratedPhoneModel.entries.associateBy { model ->
        val identity = listOf(model.spec.url, model.bytes, model.sha256, model.licence).joinToString("\n")
        MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun pending(installed: Set<String>): List<CuratedPhoneModel> {
        val saved = read()
        val complete = saved.filter { pins[it]?.sha256 in installed }
        if (complete.isNotEmpty()) write(saved - complete.toSet())
        return saved.mapNotNull(pins::get).filterNot { it.sha256 in installed }
    }

    fun add(model: CuratedPhoneModel) {
        val saved = read()
        val pin = pin(model)
        if (pin !in saved) write(saved.filter { it in pins } + pin)
    }

    fun assertReviewed(model: CuratedPhoneModel) {
        if (pin(model) !in read()) throw LocalModelStorageException("download_review_required")
    }

    fun remove(model: CuratedPhoneModel) {
        val saved = read()
        val remaining = saved - pin(model)
        if (remaining != saved) write(remaining)
    }

    private fun pin(model: CuratedPhoneModel) = pins.entries.first { it.value == model }.key

    private fun read(): List<String> {
        val file = store.owned(name)
        if (!file.exists()) return emptyList()
        return try {
            if (!file.isFile || file.length() !in 2..4096) throw LocalModelStorageException("download_queue_unreadable")
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
        val target = store.owned(name)
        val staging = store.owned("download-queue.tmp")
        try {
            staging.outputStream().use { output ->
                output.write((listOf("1") + entries).joinToString("\n", postfix = "\n").toByteArray(Charsets.US_ASCII))
                output.fd.sync()
            }
            Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: IOException) {
            throw LocalModelStorageException("download_queue_unreadable")
        } finally {
            staging.delete()
        }
    }
}
