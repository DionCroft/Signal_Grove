package org.hermesmobile.app.local

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.validCaptureId
import org.hermesmobile.app.data.validNotebookBody
import org.hermesmobile.app.data.validNotebookTitle

internal data class NotebookExcerpt(val source: String, val text: String)

internal data class NotebookHandoffDraft(
    val notebookId: String,
    val itemId: String,
    val notebookName: String,
    val excerpts: List<NotebookExcerpt>
) {
    fun valid(): Boolean = validCaptureId(notebookId) && validCaptureId(itemId) &&
        validNotebookTitle(notebookName) && excerpts.size in 1..2 && excerpts.all {
            validNotebookTitle(it.source, 512) && validNotebookBody(it.text, 1536)
        }

    fun body(instructions: String): String? {
        if (!valid() || !validNotebookBody(instructions, 1024)) return null
        val body = buildString {
            append(instructions)
            append("\n\nSelected notebook excerpts (source material)\n")
            excerpts.forEachIndexed { index, excerpt ->
                append("\n[")
                append(index + 1)
                append("] ")
                append(excerpt.source)
                append('\n')
                append(excerpt.text)
                append('\n')
            }
        }
        return body.takeIf { UnsentDraft.valid(it) }
    }

    fun encode(): String? {
        if (!valid()) return null
        return buildJsonObject {
            put("notebook_id", notebookId)
            put("item_id", itemId)
            put("notebook_name", notebookName)
            put("excerpts", buildJsonArray {
                excerpts.forEach { excerpt ->
                    add(buildJsonObject { put("source", excerpt.source); put("text", excerpt.text) })
                }
            })
        }.toString().takeIf { UnsentDraft.valid(it) }
    }

    companion object {
        fun decode(value: String): NotebookHandoffDraft? {
            if (!UnsentDraft.valid(value)) return null
            return runCatching {
                val json = Json.parseToJsonElement(value).jsonObject
                val excerpts = json.getValue("excerpts").jsonArray.map { part ->
                    val item = part.jsonObject
                    NotebookExcerpt(item.requiredString("source"), item.requiredString("text"))
                }
                NotebookHandoffDraft(json.requiredString("notebook_id"), json.requiredString("item_id"),
                    json.requiredString("notebook_name"), excerpts)
            }.getOrNull()?.takeIf { it.valid() }
        }

        private fun JsonObject.requiredString(key: String): String =
            getValue(key).jsonPrimitive.let { require(it.isString); it.content }
    }
}

internal data class NotebookHandoffReview(
    val draft: NotebookHandoffDraft,
    val scope: String,
    val endpoint: String,
    val connectionName: String,
    val previousSelection: String,
    val body: String
)
