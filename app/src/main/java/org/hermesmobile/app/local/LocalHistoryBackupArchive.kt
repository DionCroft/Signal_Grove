package org.hermesmobile.app.local

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException

internal enum class LocalHistoryBackupProblem {
    INVALID, UNSUPPORTED_VERSION, CHANGED, CAPACITY, IDENTIFIER_UNAVAILABLE,
    INVALID_PASSPHRASE, AUTHENTICATION, CRYPTO_UNAVAILABLE
}

internal class LocalHistoryBackupException(val problem: LocalHistoryBackupProblem) :
    IllegalStateException("local_history_backup_" + problem.name.lowercase())

internal class LocalHistoryBackupRecord private constructor(
    val conversation: LocalConversation,
    val labels: LocalHistoryPreferences?,
    private val transcript: ByteArray
) {
    val id: String get() = conversation.id
    val transcriptBytes: Int get() = transcript.size
    fun transcriptHash(): ByteArray = MessageDigest.getInstance("SHA-256").digest(transcript)

    fun writeTranscript(output: OutputStream, checkpoint: () -> Unit) {
        var offset = 0
        while (offset < transcript.size) {
            checkpoint()
            val end = minOf(offset + 8192, transcript.size)
            // The reviewed bytes stay private even when the caller owns the destination.
            output.write(transcript.copyOfRange(offset, end))
            offset = end
        }
    }

    companion object {
        fun checked(
            chat: LocalConversation,
            labels: LocalHistoryPreferences?,
            bytes: ByteArray,
            checkpoint: () -> Unit
        ): LocalHistoryBackupRecord {
            val snapshot = bytes.copyOf()
            try {
                require(LocalConversationStore.decodeBackupTranscript(chat.id, snapshot, checkpoint) == chat)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                throw LocalHistoryBackupException(LocalHistoryBackupProblem.INVALID)
            }
            return LocalHistoryBackupRecord(chat, labels, snapshot)
        }
    }
}

internal data class LocalHistoryBackupItem(
    val id: String,
    val modelSha256: String,
    val updatedAt: Long,
    val title: String,
    val labels: LocalHistoryPreferences?,
    val exchangeCount: Int,
    val origin: LocalBranchOrigin?
)

/** Plain versioned archive. SHA-256 detects corruption. A separate encrypted
 * envelope must authenticate the entire protected archive before restore. */
internal object LocalHistoryBackupArchive {
    const val FILE_EXTENSION = "sgbackup"
    private val MAGIC = byteArrayOf(0x53, 0x47, 0x48, 0x49, 0x53, 0x54, 0x4f, 0x52)
    fun recognizesHeader(prefix: ByteArray): Boolean = prefix.contentEquals(MAGIC)

    private const val VERSION = 1
    private const val HEADER_BYTES = 16
    private const val ID_BYTES = 36
    private const val RECORD_HEADER_BYTES = ID_BYTES + 1 + 2 + 4
    private const val SHA_BYTES = 32
    private const val MAX_TITLE_BYTES = 320
    private const val LABELS = 1
    private const val PINNED = 2
    private const val ARCHIVED = 4
    const val MIN_BYTES = HEADER_BYTES + SHA_BYTES + 1
    const val MAX_BYTES = HEADER_BYTES +
        LocalConversationStore.MAX_CHATS * (RECORD_HEADER_BYTES + MAX_TITLE_BYTES + LocalConversationStore.MAX_BYTES) + SHA_BYTES

    /** One bounded record in memory at a time. Caller controls the verified
     * selection and the destination. Neither entry reads nor the write are retried. */
    fun write(
        selected: List<String>,
        output: OutputStream,
        load: (String) -> LocalHistoryBackupRecord,
        checkpoint: () -> Unit = {}
    ) {
        require(selected.size in 1..LocalConversationStore.MAX_CHATS && selected.toSet().size == selected.size)
        selected.forEach(::requireCanonicalChatId)
        val digest = MessageDigest.getInstance("SHA-256")
        val checked = DigestOutputStream(output, digest)
        val wire = DataOutputStream(checked)
        checkpoint()
        wire.write(MAGIC.copyOf())
        wire.writeInt(VERSION)
        wire.writeInt(selected.size)
        for (id in selected) {
            checkpoint()
            val record = load(id)
            if (record.id != id) throw LocalHistoryBackupException(LocalHistoryBackupProblem.CHANGED)
            val labels = record.labels
            val title = labels?.title?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
            val flags = if (labels == null) 0 else LABELS or
                (if (labels.pinned) PINNED else 0) or (if (labels.archived) ARCHIVED else 0)
            wire.write(id.toByteArray(Charsets.US_ASCII))
            wire.writeByte(flags)
            wire.writeShort(title.size)
            wire.write(title)
            wire.writeInt(record.transcriptBytes)
            record.writeTranscript(wire, checkpoint)
        }
        checkpoint()
        checked.on(false)
        wire.write(digest.digest())
        // The caller owns flush, close and document-provider confirmation.
    }

    internal class Entry(
        val item: LocalHistoryBackupItem,
        val transcriptOffset: Long,
        val transcriptBytes: Int,
        val transcriptSha256: ByteArray
    )

    /** Bound to a private staged copy, not a URI whose provider could change its
     * contents between review and confirmation. Published only after full inspection. */
    class Inspection internal constructor(
        private val file: File,
        private val fileBytes: Long,
        private val entries: Map<String, Entry>
    ) {
        val items: List<LocalHistoryBackupItem> get() = entries.values.map { it.item }

        fun readRecord(id: String, checkpoint: () -> Unit = {}): LocalHistoryBackupRecord {
            val entry = entries[id] ?: throw LocalHistoryBackupException(LocalHistoryBackupProblem.CHANGED)
            try {
                checkpoint()
                val bytes = RandomAccessFile(file, "r").use { input ->
                    require(input.length() == fileBytes)
                    input.seek(entry.transcriptOffset)
                    val transcript = ByteArray(entry.transcriptBytes)
                    readChecked(transcript, checkpoint, input::readFully)
                    transcript
                }
                require(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(bytes), entry.transcriptSha256))
                val chat = LocalConversationStore.decodeBackupTranscript(id, bytes, checkpoint)
                require(describe(chat, entry.item.labels) == entry.item)
                return LocalHistoryBackupRecord.checked(chat, entry.item.labels, bytes, checkpoint)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                throw LocalHistoryBackupException(LocalHistoryBackupProblem.CHANGED)
            }
        }
    }

    fun inspect(stagedArchive: File, checkpoint: () -> Unit = {}): Inspection {
        try {
            val file = stagedArchive.canonicalFile
            val length = file.length()
            require(file.isFile && length in MIN_BYTES.toLong()..MAX_BYTES.toLong())
            val digest = MessageDigest.getInstance("SHA-256")
            val entries = linkedMapOf<String, Entry>()
            val checked = DigestInputStream(BufferedInputStream(file.inputStream()), digest)
            DataInputStream(checked).use { wire ->
                checkpoint()
                require(ByteArray(MAGIC.size).also(wire::readFully).contentEquals(MAGIC))
                if (wire.readInt() != VERSION) throw LocalHistoryBackupException(LocalHistoryBackupProblem.UNSUPPORTED_VERSION)
                val count = wire.readInt()
                require(count in 1..LocalConversationStore.MAX_CHATS)
                var offset = HEADER_BYTES.toLong()
                repeat(count) {
                    checkpoint()
                    val id = ByteArray(ID_BYTES).also(wire::readFully).toString(Charsets.US_ASCII)
                    requireCanonicalChatId(id)
                    require(!entries.containsKey(id))
                    val flags = wire.readUnsignedByte()
                    require(flags in 0..(LABELS or PINNED or ARCHIVED))
                    val titleSize = wire.readUnsignedShort()
                    require(titleSize <= MAX_TITLE_BYTES)
                    val title = if (titleSize == 0) null else Charsets.UTF_8.newDecoder()
                        .decode(ByteBuffer.wrap(ByteArray(titleSize).also(wire::readFully))).toString()
                    val labels = if (flags and LABELS != 0) {
                        LocalHistoryPreferences(title, flags and PINNED != 0, flags and ARCHIVED != 0)
                    } else {
                        require(flags == 0 && title == null)
                        null
                    }
                    val bytes = wire.readInt()
                    offset += RECORD_HEADER_BYTES + titleSize
                    require(bytes in 1..LocalConversationStore.MAX_BYTES && offset + bytes + SHA_BYTES <= length)
                    val transcript = ByteArray(bytes)
                    readChecked(transcript, checkpoint, wire::readFully)
                    val chat = LocalConversationStore.decodeBackupTranscript(id, transcript, checkpoint)
                    val described = describe(chat, labels)
                    entries[id] = Entry(described, offset, bytes, MessageDigest.getInstance("SHA-256").digest(transcript))
                    offset += bytes
                }
                require(offset + SHA_BYTES == length)
                checked.on(false)
                require(MessageDigest.isEqual(ByteArray(SHA_BYTES).also(wire::readFully), digest.digest()))
                require(wire.read() == -1)
            }
            require(entries.values.all { entry ->
                val item = entry.item
                val parent = item.origin?.linkedParentId?.let { entries[it]?.item }
                parent == null || parent.modelSha256 == item.modelSha256
            })
            for (start in entries.keys) {
                val visited = hashSetOf<String>()
                var next: String? = start
                while (next != null) {
                    require(visited.add(next))
                    next = entries[next]?.item?.origin?.linkedParentId
                }
            }
            checkpoint()
            return Inspection(file, length, entries.toMap())
        } catch (e: CancellationException) {
            throw e
        } catch (e: LocalHistoryBackupException) {
            throw e
        } catch (_: Exception) {
            throw LocalHistoryBackupException(LocalHistoryBackupProblem.INVALID)
        }
    }

    private fun describe(value: LocalConversation, labels: LocalHistoryPreferences?) = LocalHistoryBackupItem(
        value.id, value.modelSha256, value.updatedAt,
        labels?.title ?: value.exchanges.firstOrNull()?.question.orEmpty().filterNot(Char::isISOControl).take(80),
        labels, value.exchanges.size, value.origin
    )

    private fun readChecked(bytes: ByteArray, checkpoint: () -> Unit, read: (ByteArray, Int, Int) -> Unit) {
        var offset = 0
        while (offset < bytes.size) {
            checkpoint()
            val count = minOf(8192, bytes.size - offset)
            read(bytes, offset, count)
            offset += count
        }
    }
}
