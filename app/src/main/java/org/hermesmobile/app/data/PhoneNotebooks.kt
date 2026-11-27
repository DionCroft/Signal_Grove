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
import kotlinx.coroutines.flow.Flow

internal enum class NotebookItemKind { CAPTURE, DOCUMENT, TASK, ANSWER }

@Entity(tableName = "phone_notebooks")
internal data class PhoneNotebook(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val openedAt: Long
)

@Entity(
    tableName = "phone_notebook_items",
    foreignKeys = [
        ForeignKey(entity = PhoneNotebook::class, parentColumns = ["id"], childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["notebookId", "kind", "sourceId"], unique = true)]
)
internal data class NotebookItem(
    @PrimaryKey val id: String,
    val notebookId: String,
    val kind: String,
    val sourceId: String,
    val fingerprint: String,
    val title: String,
    val answer: String,
    val addedAt: Long
)

@Entity(
    tableName = "phone_notebook_sources",
    primaryKeys = ["itemId", "ordinal"],
    foreignKeys = [
        ForeignKey(entity = NotebookItem::class, parentColumns = ["id"], childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE)
    ]
)
internal data class NotebookSource(
    val itemId: String,
    val ordinal: Int,
    val documentId: String,
    val fingerprint: String,
    val documentName: String,
    val page: Int?,
    val firstLine: Int,
    val lastLine: Int,
    val excerpt: String
)

@Dao
internal abstract class PhoneNotebookDao {
    @Query("SELECT * FROM phone_notebooks ORDER BY openedAt DESC, name COLLATE NOCASE, id")
    abstract fun notebooks(): Flow<List<PhoneNotebook>>

    @Query("SELECT * FROM phone_notebooks ORDER BY openedAt DESC, id LIMIT 1")
    abstract fun lastOpened(): Flow<PhoneNotebook?>

    @Query("SELECT * FROM phone_notebooks WHERE id = :id")
    abstract suspend fun notebook(id: String): PhoneNotebook?

    @Query("UPDATE phone_notebooks SET openedAt = :now WHERE id = :id")
    abstract suspend fun openNotebook(id: String, now: Long)

    @Query("SELECT * FROM phone_notebook_items WHERE notebookId = :notebookId ORDER BY addedAt DESC, id")
    abstract fun items(notebookId: String): Flow<List<NotebookItem>>

    @Query("SELECT * FROM phone_notebook_items WHERE notebookId = :notebookId AND id = :itemId")
    abstract suspend fun item(notebookId: String, itemId: String): NotebookItem?

    @Query("""SELECT source.* FROM phone_notebook_sources source
        INNER JOIN phone_notebook_items item ON source.itemId = item.id
        WHERE item.notebookId = :notebookId AND item.id = :itemId
        ORDER BY source.ordinal""")
    abstract fun sources(notebookId: String, itemId: String): Flow<List<NotebookSource>>

    @Query("DELETE FROM phone_notebook_items WHERE notebookId = :notebookId AND id = :itemId")
    abstract suspend fun detach(notebookId: String, itemId: String)

    @Query("SELECT COUNT(*) FROM phone_notebooks")
    protected abstract suspend fun notebookCount(): Int

    @Query("SELECT COUNT(*) FROM phone_notebook_items WHERE notebookId = :notebookId")
    protected abstract suspend fun itemCount(notebookId: String): Int

    @Query("SELECT * FROM phone_notebook_items WHERE notebookId = :notebookId AND kind = :kind AND sourceId = :sourceId")
    protected abstract suspend fun attached(notebookId: String, kind: String, sourceId: String): NotebookItem?

    @Insert
    protected abstract suspend fun insertNotebook(notebook: PhoneNotebook)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertItem(item: NotebookItem)

    @Insert
    protected abstract suspend fun insertSources(excerpts: List<NotebookSource>)

    @Transaction
    open suspend fun create(notebook: PhoneNotebook) {
        require(validCaptureId(notebook.id) && validNotebookTitle(notebook.name))
        require(notebook.createdAt > 0 && notebook.openedAt == notebook.createdAt)
        check(notebookCount() < 100)
        insertNotebook(notebook)
    }

    @Transaction
    open suspend fun attach(item: NotebookItem, excerpts: List<NotebookSource>): String {
        require(validCaptureId(item.id) && validCaptureId(item.notebookId) && item.addedAt > 0)
        val kind = NotebookItemKind.valueOf(item.kind)
        require(item.sourceId.isNotBlank() && item.sourceId.length <= 128 && validNotebookTitle(item.title, 512))
        if (kind == NotebookItemKind.ANSWER) {
            require(validNotebookBody(item.answer, 8192) && item.fingerprint.isEmpty())
            require(excerpts.size in 1..2 && excerpts.map { it.ordinal } == excerpts.indices.toList())
            require(excerpts.all { source ->
                source.itemId == item.id && validCaptureId(source.documentId) &&
                    source.fingerprint.matches(NOTEBOOK_SHA256) && validNotebookTitle(source.documentName, 512) &&
                    source.firstLine > 0 && source.lastLine >= source.firstLine &&
                    (source.page == null || source.page > 0) && validNotebookBody(source.excerpt, 1536)
            })
        } else {
            require(item.answer.isEmpty() && excerpts.isEmpty())
            require(if (kind == NotebookItemKind.DOCUMENT) item.fingerprint.matches(NOTEBOOK_SHA256) else item.fingerprint.isEmpty())
        }
        attached(item.notebookId, item.kind, item.sourceId)?.let { return it.id }
        check(itemCount(item.notebookId) < 200)
        insertItem(item)
        if (excerpts.isNotEmpty()) insertSources(excerpts)
        return item.id
    }
}

internal val NOTEBOOK_SHA256 = Regex("[a-f0-9]{64}")

internal fun validNotebookTitle(value: String, maxBytes: Int = 160): Boolean =
    validNotebookBody(value, maxBytes) && value.none { it.isISOControl() }

internal fun validNotebookBody(value: String, maxBytes: Int): Boolean =
    value.length <= maxBytes && value.isNotBlank() && value.none { it.isISOControl() && it !in "\r\n\t" } &&
        Charsets.UTF_8.newEncoder().canEncode(value) && value.toByteArray(Charsets.UTF_8).size <= maxBytes
