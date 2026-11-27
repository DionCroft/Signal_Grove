package org.hermesmobile.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class CaptureSource { SHARED_TEXT, SHARED_LINK, WIDGET, NOTE }

@Entity(tableName = "phone_captures", indices = [Index("createdAt")])
data class PhoneCapture(
    @PrimaryKey val id: String,
    val content: String,
    val source: String,
    val createdAt: Long,
    val processedAt: Long? = null
)

data class PhoneCapturePreview(val id: String, val preview: String, val source: String, val createdAt: Long, val processedAt: Long?)

@Entity(
    tableName = "phone_capture_tasks",
    foreignKeys = [
        ForeignKey(entity = LocalTask::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PhoneCapture::class, parentColumns = ["id"], childColumns = ["captureId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("captureId")]
)
data class PhoneCaptureTaskLink(@PrimaryKey val taskId: String, val captureId: String)

@Entity(
    tableName = "phone_capture_task_reviews",
    foreignKeys = [
        ForeignKey(entity = PhoneCapture::class, parentColumns = ["id"], childColumns = ["captureId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("captureId")]
)
data class PhoneCaptureTaskReceipt(
    @PrimaryKey val reviewId: String,
    val captureId: String,
    val selectionHash: String,
    val taskCount: Int
)

@Dao
abstract class PhoneCaptureDao {
    @Query("""SELECT id, substr(content, 1, 180) AS preview, source, createdAt, processedAt
        FROM phone_captures
        WHERE :query = '' OR instr(lower(content), lower(:query)) > 0
        ORDER BY createdAt DESC, id""")
    abstract fun search(query: String): Flow<List<PhoneCapturePreview>>

    @Query("SELECT * FROM phone_captures WHERE id = :id")
    abstract fun observe(id: String): Flow<PhoneCapture?>

    @Query("SELECT * FROM phone_captures WHERE id = :id")
    protected abstract suspend fun capture(id: String): PhoneCapture?

    @Query("SELECT * FROM phone_capture_tasks")
    abstract fun taskSources(): Flow<List<PhoneCaptureTaskLink>>

    @Query("""SELECT tasks.* FROM tasks INNER JOIN phone_capture_tasks link ON tasks.id = link.taskId
        WHERE link.captureId = :captureId ORDER BY tasks.completed, tasks.title, tasks.id""")
    abstract fun linkedTasks(captureId: String): Flow<List<LocalTask>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertCapture(value: PhoneCapture): Long

    @Insert
    protected abstract suspend fun insertTasks(tasks: List<LocalTask>)

    @Insert
    protected abstract suspend fun insertTaskLinks(links: List<PhoneCaptureTaskLink>)

    @Insert
    protected abstract suspend fun insertReceipt(receipt: PhoneCaptureTaskReceipt)

    @Query("SELECT * FROM phone_capture_task_reviews WHERE reviewId = :reviewId")
    protected abstract suspend fun reviewReceipt(reviewId: String): PhoneCaptureTaskReceipt?

    @Query("UPDATE phone_captures SET processedAt = COALESCE(processedAt, :now) WHERE id = :id")
    protected abstract suspend fun markProcessed(id: String, now: Long)

    @Transaction
    open suspend fun save(value: PhoneCapture) {
        require(validCaptureId(value.id) && validCaptureText(value.content) && value.createdAt > 0 && value.processedAt == null)
        require(CaptureSource.entries.any { it.name == value.source })
        if (insertCapture(value) == -1L) {
            val existing = checkNotNull(capture(value.id))
            check(existing.content == value.content && existing.source == value.source)
        }
    }

    /** A persisted review receipt holds the commit even if the caller loses the response. */
    @Transaction
    open suspend fun saveReviewedTasks(captureId: String, reviewId: String, titles: List<String>, now: Long): Int {
        require(validCaptureId(captureId) && validCaptureId(reviewId))
        require(titles.size in 1..MAX_CAPTURE_TASKS && titles.all(::validCaptureTaskTitle))
        val selection = MessageDigest.getInstance("SHA-256").digest(titles.joinToString("\u0000").toByteArray())
            .joinToString("") { "%02x".format(it) }
        reviewReceipt(reviewId)?.let { prior ->
            check(prior.captureId == captureId && prior.selectionHash == selection)
            return prior.taskCount
        }
        checkNotNull(capture(captureId))
        val tasks = titles.mapIndexed { index, title ->
            LocalTask(UUID.nameUUIDFromBytes("capture-task/$reviewId/$index".toByteArray()).toString(), title)
        }
        insertTasks(tasks)
        insertTaskLinks(tasks.map { PhoneCaptureTaskLink(it.id, captureId) })
        insertReceipt(PhoneCaptureTaskReceipt(reviewId, captureId, selection, tasks.size))
        markProcessed(captureId, now)
        return tasks.size
    }
}

internal const val MAX_CAPTURE_CHARS = 6144
internal const val MAX_CAPTURE_TASKS = 8
internal const val MAX_CAPTURE_TASK_TITLE = 180

internal fun validCaptureText(value: String): Boolean =
    value.length <= MAX_CAPTURE_CHARS && value.isNotBlank() &&
        value.none { it.isISOControl() && it !in "\n\r\t" } &&
        Charsets.UTF_8.newEncoder().canEncode(value) && value.toByteArray().size <= MAX_CAPTURE_CHARS

internal fun validCaptureTaskTitle(value: String): Boolean =
    value == value.trim() && value.isNotEmpty() && value.length <= MAX_CAPTURE_TASK_TITLE &&
        value.none { it.isISOControl() || it == '\u2028' || it == '\u2029' } && Charsets.UTF_8.newEncoder().canEncode(value)

internal fun validCaptureId(id: String): Boolean =
    id.length == 36 && runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
