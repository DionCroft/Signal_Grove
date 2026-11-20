package org.hermesmobile.app.local

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.UUID

/** Profiles have a separate protected format and never join a chat archive. */
internal object PhoneProfileBackup {
    const val FILENAME = "SignalGrove-phone-profiles.sgprofiles"
    const val MAX_PLAINTEXT = 450_000
    const val MAX_FILE = MAX_PLAINTEXT + 80

    fun encrypt(profiles: PhonePersonalProfiles, passphrase: CharArray, checkpoint: () -> Unit = {}): ByteArray {
        require(profiles.entries.isNotEmpty())
        val bytes = profiles.encode().toByteArray(Charsets.UTF_8)
        try {
            require(bytes.size <= MAX_PLAINTEXT)
            val result = ByteArrayOutputStream()
            LocalHistoryBackupProtection.writeProfilePayload(bytes, passphrase, result, checkpoint)
            return result.toByteArray()
        } finally {
            bytes.fill(0)
        }
    }

    fun readFile(input: InputStream, checkpoint: () -> Unit): ByteArray {
        // Read at most one byte beyond the limit to distinguish a complete file from a prefix.
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() <= MAX_FILE) {
            checkpoint()
            val read = input.read(buffer, 0, minOf(buffer.size, MAX_FILE + 1 - output.size()))
            if (read < 0) {
                return output.toByteArray().also {
                    if (!LocalHistoryBackupProtection.recognizesProfileEnvelope(it)) {
                        throw LocalHistoryBackupException(LocalHistoryBackupProblem.INVALID)
                    }
                }
            }
            if (read == 0) throw LocalHistoryBackupException(LocalHistoryBackupProblem.INVALID)
            output.write(buffer, 0, read)
        }
        throw LocalHistoryBackupException(LocalHistoryBackupProblem.INVALID)
    }

    fun unlock(ciphertext: ByteArray, passphrase: CharArray, checkpoint: () -> Unit = {}): PhonePersonalProfiles {
        if (!LocalHistoryBackupProtection.recognizesProfileEnvelope(ciphertext)) {
            throw LocalHistoryBackupException(LocalHistoryBackupProblem.INVALID)
        }
        val plaintext = object : ByteArrayOutputStream() {
            fun decode(): PhonePersonalProfiles {
                val json = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(buf, 0, count)).toString()
                return PhonePersonalProfiles.decode(json).also { require(it.entries.isNotEmpty()) }
            }
            fun wipe() { buf.fill(0); reset() }
        }
        try {
            LocalHistoryBackupProtection.readProfilePayload(ciphertext.inputStream(), passphrase, plaintext, checkpoint)
            return try { plaintext.decode() } catch (_: Exception) {
                throw LocalHistoryBackupException(LocalHistoryBackupProblem.INVALID)
            }
        } finally {
            plaintext.wipe()
        }
    }
}

internal enum class ProfileRestoreDisposition { ADD, COPY, ALREADY_PRESENT }
internal data class ProfileRestoreItem(val profile: PhonePersonalProfile, val disposition: ProfileRestoreDisposition)

internal class ProfileRestorePlan(private val baseline: PhonePersonalProfiles, incoming: PhonePersonalProfiles) {
    val available = PHONE_PROFILE_MAX - baseline.entries.size
    val items = incoming.entries.map { profile ->
        val disposition = when {
            baseline.entries.any { it.copy(id = profile.id) == profile } -> ProfileRestoreDisposition.ALREADY_PRESENT
            baseline.entries.any { it.id == profile.id } -> ProfileRestoreDisposition.COPY
            else -> ProfileRestoreDisposition.ADD
        }
        ProfileRestoreItem(profile, disposition)
    }

    fun restore(current: PhonePersonalProfiles, selected: Set<String>,
        newId: () -> String = { UUID.randomUUID().toString() }): PhonePersonalProfiles {
        if (current != baseline) throw LocalHistoryBackupException(LocalHistoryBackupProblem.CHANGED)
        val chosen = items.filter { it.profile.id in selected && it.disposition != ProfileRestoreDisposition.ALREADY_PRESENT }
        if (chosen.isEmpty() || chosen.size != selected.size) throw LocalHistoryBackupException(LocalHistoryBackupProblem.INVALID)
        if (chosen.size > available) throw LocalHistoryBackupException(LocalHistoryBackupProblem.CAPACITY)
        val ids = (current.entries.map { it.id } + items.map { it.profile.id }).toMutableSet()
        val added = chosen.map { item ->
            if (item.disposition == ProfileRestoreDisposition.ADD) item.profile
            else {
                val id = generateSequence { newId() }.take(16).firstOrNull { ids.add(it) }
                    ?: throw LocalHistoryBackupException(LocalHistoryBackupProblem.IDENTIFIER_UNAVAILABLE)
                item.profile.copy(id = id)
            }
        }
        return current.copy(entries = current.entries + added)
    }
}
