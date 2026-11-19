package org.hermesmobile.app.local

import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingKey
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingParameters
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import com.google.crypto.tink.util.SecretBytes
import java.io.Closeable
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.CharBuffer
import java.nio.file.Files
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Owns the authenticated plaintext stage for the lifetime of the reviewed import. */
internal class LocalHistoryProtectedInspection(
    private val stage: File,
    val inspection: LocalHistoryBackupArchive.Inspection
) : Closeable {
    override fun close() {
        Files.deleteIfExists(stage.toPath())
    }
}

/** Passphrase-protected version 1 envelope around the existing phone history archive. */
internal object LocalHistoryBackupProtection {
    private val magic = "SGBKAE01".toByteArray(Charsets.US_ASCII)
    private const val MAGIC_BYTES = 8
    private const val SALT_BYTES = 16
    private const val HEADER_BYTES = MAGIC_BYTES + SALT_BYTES
    private const val KEY_BYTES = 32
    private const val PBKDF2_ITERATIONS = 600_000
    private const val SEGMENT_BYTES = 1 shl 20
    private const val TINK_HEADER_BYTES = KEY_BYTES + 8
    private const val TAG_BYTES = 16
    private const val PAYLOAD_PER_SEGMENT = SEGMENT_BYTES - TAG_BYTES
    private const val MAX_PLAINTEXT = LocalHistoryBackupArchive.MAX_BYTES
    private const val MAX_SEGMENTS = (MAX_PLAINTEXT + TINK_HEADER_BYTES + PAYLOAD_PER_SEGMENT - 1) / PAYLOAD_PER_SEGMENT
    const val MAX_BYTES = HEADER_BYTES + TINK_HEADER_BYTES + MAX_PLAINTEXT + MAX_SEGMENTS * TAG_BYTES
    private val random by lazy { SecureRandom() }
    private val parameters by lazy {
        AesGcmHkdfStreamingParameters.builder()
            .setKeySizeBytes(KEY_BYTES)
            .setDerivedAesGcmKeySizeBytes(KEY_BYTES)
            .setHkdfHashType(AesGcmHkdfStreamingParameters.HashType.SHA256)
            .setCiphertextSegmentSizeBytes(SEGMENT_BYTES)
            .build()
    }
    private val config by lazy {
        StreamingAeadConfig.register()
        RegistryConfiguration.get()
    }

    /** The caller owns the destination stream and passphrase buffer. */
    fun write(
        selected: List<String>,
        passphrase: CharArray,
        output: OutputStream,
        load: (String) -> LocalHistoryBackupRecord,
        checkpoint: () -> Unit = {}
    ) {
        checkpoint()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val header = magic + salt
        val streaming = primitive(passphrase, salt)
        checkpoint()
        output.write(header)
        var aborted = false
        val borrowedOutput = object : OutputStream() {
            override fun write(value: Int) {
                if (!aborted) output.write(value)
            }

            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                if (!aborted) output.write(bytes, offset, length)
            }
        }
        streaming.newEncryptingStream(borrowedOutput, header).use { encrypted ->
            try {
                LocalHistoryBackupArchive.write(selected, encrypted, load, checkpoint)
                checkpoint()
            } catch (failure: Throwable) {
                // Close the crypto stream without publishing a final segment on failure.
                aborted = true
                throw failure
            }
        }
    }

    /** Authenticate to EOF in app-private storage before parsing or offering a restore review. */
    fun inspect(
        source: File,
        passphrase: CharArray,
        privateDirectory: File,
        checkpoint: () -> Unit = {}
    ): LocalHistoryProtectedInspection {
        val ciphertext = source.canonicalFile
        val directory = privateDirectory.canonicalFile
        val minBytes = HEADER_BYTES + TINK_HEADER_BYTES + TAG_BYTES
        if (!ciphertext.isFile || ciphertext.length() !in minBytes.toLong()..MAX_BYTES.toLong()) {
            throw LocalHistoryBackupException(LocalHistoryBackupProblem.INVALID)
        }
        require(directory.isDirectory)
        checkpoint()
        val stage = Files.createTempFile(directory.toPath(), "review-", ".part").toFile()
        try {
            ciphertext.inputStream().buffered().use { input ->
                val header = readHeader(input)
                val streaming = primitive(passphrase, header.copyOfRange(MAGIC_BYTES, HEADER_BYTES))
                checkpoint()
                streaming.newDecryptingStream(input, header).use { plaintext ->
                    stage.outputStream().use { output -> copyAuthenticated(plaintext, output, checkpoint) }
                }
            }
            val inspection = LocalHistoryBackupArchive.inspect(stage, checkpoint)
            return LocalHistoryProtectedInspection(stage, inspection)
        } catch (failure: Throwable) {
            try {
                Files.deleteIfExists(stage.toPath())
            } catch (cleanup: IOException) {
                failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }

    private fun readHeader(input: InputStream): ByteArray {
        val header = ByteArray(HEADER_BYTES)
        try {
            DataInputStream(input).readFully(header)
        } catch (_: IOException) {
            throw LocalHistoryBackupException(LocalHistoryBackupProblem.INVALID)
        }
        if (!header.copyOf(MAGIC_BYTES).contentEquals(magic)) {
            val hasFamily = header.copyOf(6).contentEquals(magic.copyOf(6))
            throw LocalHistoryBackupException(
                if (hasFamily) LocalHistoryBackupProblem.UNSUPPORTED_VERSION else LocalHistoryBackupProblem.INVALID
            )
        }
        return header
    }

    private fun copyAuthenticated(input: InputStream, output: OutputStream, checkpoint: () -> Unit) {
        val buffer = ByteArray(8192)
        var size = 0
        try {
            while (true) {
                checkpoint()
                val read = try {
                    input.read(buffer)
                } catch (_: IOException) {
                    throw LocalHistoryBackupException(LocalHistoryBackupProblem.AUTHENTICATION)
                }
                if (read < 0) return
                size += read
                if (size > MAX_PLAINTEXT) throw LocalHistoryBackupException(LocalHistoryBackupProblem.INVALID)
                output.write(buffer, 0, read)
            }
        } finally {
            buffer.fill(0)
        }
    }

    private fun primitive(passphrase: CharArray, salt: ByteArray): StreamingAead {
        if (passphrase.size !in 1..256 || !Charsets.UTF_8.newEncoder().canEncode(CharBuffer.wrap(passphrase))) {
            throw LocalHistoryBackupException(LocalHistoryBackupProblem.INVALID_PASSPHRASE)
        }
        val specification = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BYTES * 8)
        try {
            val material = SecretKeyFactory.getInstance("PBKDF2withHmacSHA256").generateSecret(specification).encoded
            try {
                val key = AesGcmHkdfStreamingKey.create(parameters, SecretBytes.copyFrom(material, InsecureSecretKeyAccess.get()))
                val keyset = KeysetHandle.newBuilder()
                    .addEntry(KeysetHandle.importKey(key).withFixedId(1).makePrimary())
                    .build()
                return keyset.getPrimitive(config, StreamingAead::class.java)
            } finally {
                material.fill(0)
            }
        } catch (_: GeneralSecurityException) {
            throw LocalHistoryBackupException(LocalHistoryBackupProblem.CRYPTO_UNAVAILABLE)
        } finally {
            specification.clearPassword()
        }
    }
}
