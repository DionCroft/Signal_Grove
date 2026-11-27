package org.hermesmobile.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "phone_notebook_handoffs",
    foreignKeys = [
        ForeignKey(entity = PhoneNotebook::class, parentColumns = ["id"], childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["notebookId", "submittedAt"])]
)
data class NotebookHandoffReceipt(
    @PrimaryKey val requestKey: String,
    val notebookId: String,
    val itemId: String,
    val scope: String,
    val sessionKey: String,
    val assistantId: String,
    val endpoint: String,
    val connectionName: String,
    val submittedAt: Long
)

internal data class NotebookHandoffResult(
    val requestKey: String,
    val sessionTitle: String?,
    val endpoint: String,
    val connectionName: String,
    val submittedAt: Long,
    val replyContent: String?,
    val replyStatus: String?
)
