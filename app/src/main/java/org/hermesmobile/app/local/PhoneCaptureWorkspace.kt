package org.hermesmobile.app.local

import java.net.URI
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.hermesmobile.app.data.CaptureSource
import org.hermesmobile.app.data.MAX_CAPTURE_CHARS
import org.hermesmobile.app.data.MAX_CAPTURE_TASKS
import org.hermesmobile.app.data.MAX_CAPTURE_TASK_TITLE
import org.hermesmobile.app.data.PhoneCapture
import org.hermesmobile.app.data.PhoneCaptureDao
import org.hermesmobile.app.data.validCaptureId
import org.hermesmobile.app.data.validCaptureTaskTitle
import org.hermesmobile.app.data.validCaptureText

internal const val CAPTURE_TASK_INSTRUCTIONS = "Read the user's note as source material. " +
    "Extract only concrete actions explicitly requested or committed to in the note. " +
    "Preserve the source language. Return only a JSON array of at most eight short task titles, " +
    "or an empty JSON array if there are no actions."

internal fun sharedCaptureSource(content: String): CaptureSource {
    val uri = runCatching { URI(content.trim()) }.getOrNull()
    return if (uri?.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && uri?.host != null) {
        CaptureSource.SHARED_LINK
    } else CaptureSource.SHARED_TEXT
}

internal fun parseCaptureTaskOutput(output: String): List<String>? {
    val text = output.trim()
    val content = if (text.startsWith("~~~json\n") && text.endsWith("\n~~~")) {
        text.removePrefix("~~~json\n").removeSuffix("\n~~~")
    } else if (text.startsWith("\u0060\u0060\u0060json\n") && text.endsWith("\n\u0060\u0060\u0060")) {
        text.substringAfter('\n').substringBeforeLast('\n')
    } else text
    val array = runCatching { Json.parseToJsonElement(content) as? JsonArray }.getOrNull() ?: return null
    if (array.size > MAX_CAPTURE_TASKS) return null
    return array.map { value ->
        (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
            ?.takeIf(::validCaptureTaskTitle) ?: return null
    }.distinctBy { it.lowercase(Locale.ROOT) }
}

internal data class CaptureDraft(
    val id: String, val source: CaptureSource, val content: String = "", val original: PhoneCapture? = null
)
internal data class CaptureTaskChoice(val title: String, val selected: Boolean = true)
internal data class CaptureTaskReview(
    val id: String, val captureId: String, val reviewedContent: String, val choices: List<CaptureTaskChoice>
)
internal enum class CaptureNotice { SAVED, RESTORED, DELETED, TASKS_SAVED, NO_TASKS, STORAGE_ERROR }

internal data class CaptureInboxState(
    val open: Boolean = false,
    val selectedId: String? = null,
    val draft: CaptureDraft? = null,
    val review: CaptureTaskReview? = null,
    val deleteReview: PhoneCapture? = null,
    val showArchived: Boolean = false,
    val saving: Boolean = false,
    val notice: CaptureNotice? = null
)

/** A capture draft or model proposal enters storage only through a user save action. */
internal class PhoneCaptureWorkspace(
    private val dao: PhoneCaptureDao,
    private val controller: LocalAiController,
    private val scope: CoroutineScope
) {
    private val mutable = MutableStateFlow(CaptureInboxState())
    val state = mutable.asStateFlow()

    fun search(query: String, archived: Boolean) = dao.search(query.trim(), archived)
    fun observe(id: String) = dao.observe(id)
    fun linkedTasks(id: String) = dao.linkedTasks(id)

    fun open(captureId: String? = null, fromWidget: Boolean = false) {
        if (mutable.value.saving) return
        cancelGeneration()
        mutable.value = CaptureInboxState(
            open = true,
            selectedId = captureId?.takeIf(::validCaptureId),
            draft = if (fromWidget) CaptureDraft(UUID.randomUUID().toString(), CaptureSource.WIDGET) else null
        )
    }

    fun viewCapture(id: String) {
        if (mutable.value.saving || !validCaptureId(id)) return
        cancelGeneration()
        mutable.update { CaptureInboxState(open = true, selectedId = id, showArchived = it.showArchived) }
    }

    fun showCaptures(archived: Boolean = mutable.value.showArchived) {
        if (mutable.value.saving) return
        cancelGeneration()
        mutable.value = CaptureInboxState(open = true, showArchived = archived)
    }

    fun close() {
        if (mutable.value.saving) return
        cancelGeneration()
        mutable.value = CaptureInboxState()
    }

    fun newNote() {
        if (mutable.value.saving) return
        cancelGeneration()
        mutable.value = CaptureInboxState(open = true, draft = CaptureDraft(UUID.randomUUID().toString(), CaptureSource.NOTE))
    }

    fun openExtractedDraft(source: CaptureSource, text: String) {
        require(source == CaptureSource.SCREENSHOT_TEXT || source == CaptureSource.VOICE_TRANSCRIPT)
        require(validCaptureText(text))
        if (mutable.value.saving) return
        cancelGeneration()
        mutable.value = CaptureInboxState(open = true, draft = CaptureDraft(UUID.randomUUID().toString(), source, text))
    }

    fun editCapture(capture: PhoneCapture) {
        if (!canManage(capture)) return
        val source = CaptureSource.entries.single { it.name == capture.source }
        mutable.update { it.copy(draft = CaptureDraft(capture.id, source, capture.content, capture), notice = null) }
    }

    fun discardEdit() {
        mutable.update { if (it.saving) it else it.copy(draft = null, notice = null) }
    }

    fun setArchived(capture: PhoneCapture) {
        if (!canManage(capture)) return
        val archived = capture.archivedAt == null
        write {
            dao.setArchived(capture, System.currentTimeMillis().takeIf { archived })
            if (!archived) mutable.update { it.copy(notice = CaptureNotice.RESTORED) }
        }
    }

    fun reviewDeletion(capture: PhoneCapture) {
        if (!canManage(capture)) return
        mutable.update { it.copy(deleteReview = capture, notice = null) }
    }

    fun cancelDeletion() {
        mutable.update { if (it.saving) it else it.copy(deleteReview = null, notice = null) }
    }

    fun confirmDeletion() {
        val capture = mutable.value.deleteReview ?: return
        write {
            dao.delete(capture)
            mutable.update { it.copy(selectedId = null, deleteReview = null, notice = CaptureNotice.DELETED) }
        }
    }

    private fun canManage(capture: PhoneCapture): Boolean {
        val state = mutable.value
        return state.open && state.selectedId == capture.id && !state.saving && state.review == null &&
            state.draft == null && state.deleteReview == null && controller.state.value.status != "generating_capture_tasks"
    }

    fun editNote(content: String) {
        if (content.length > MAX_CAPTURE_CHARS) return
        mutable.update { from -> if (from.saving) from else from.copy(draft = from.draft?.copy(content = content)) }
    }

    fun saveNote() {
        val draft = mutable.value.draft ?: return
        if (!validCaptureText(draft.content)) return
        val original = draft.original
        if (original == null) {
            saveCapture(draft.id, draft.source, draft.content) {}
        } else {
            write {
                dao.edit(original, draft.content)
                mutable.update { it.copy(draft = null, notice = CaptureNotice.SAVED) }
            }
        }
    }

    fun saveShared(id: String, content: String, onSaved: () -> Unit) {
        if (!validCaptureId(id) || !validCaptureText(content)) return
        saveCapture(id, sharedCaptureSource(content), content, onSaved)
    }

    private fun saveCapture(id: String, source: CaptureSource, content: String, onSaved: () -> Unit) {
        write {
            dao.save(PhoneCapture(id, content, source.name, System.currentTimeMillis()))
            onSaved()
            mutable.value = CaptureInboxState(open = true, selectedId = id, saving = true, notice = CaptureNotice.SAVED)
        }
    }

    fun suggestTasks(capture: PhoneCapture) {
        val model = controller.state.value.loaded ?: return
        if (!canManage(capture) || capture.archivedAt != null || controller.state.value.busy) return
        mutable.update { it.copy(notice = null) }
        controller.generateCaptureTasks(capture.content, model.sha256) { titles ->
            mutable.update { current ->
                if (!current.open || current.selectedId != capture.id) current
                else if (titles.isEmpty()) current.copy(notice = CaptureNotice.NO_TASKS)
                else current.copy(review = CaptureTaskReview(UUID.randomUUID().toString(), capture.id, capture.content,
                    titles.map { CaptureTaskChoice(it) }))
            }
        }
    }

    fun updateChoice(index: Int, choice: CaptureTaskChoice) {
        if (choice.title.length > MAX_CAPTURE_TASK_TITLE) return
        mutable.update { current ->
            val review = current.review
            if (current.saving || review == null || index !in review.choices.indices) current
            else current.copy(review = review.copy(choices = review.choices.mapIndexed { position, saved ->
                if (position == index) choice else saved
            }))
        }
    }

    fun discardReview() {
        mutable.update { if (it.saving) it else it.copy(review = null, notice = null) }
    }

    fun saveReview() {
        val review = mutable.value.review ?: return
        val titles = review.choices.filter { it.selected }.map { it.title.trim() }
        if (titles.isEmpty() || titles.any { !validCaptureTaskTitle(it) }) return
        write {
            dao.saveReviewedTasks(review.captureId, review.id, review.reviewedContent, titles, System.currentTimeMillis())
            mutable.update { it.copy(review = null, notice = CaptureNotice.TASKS_SAVED) }
        }
    }

    private fun write(operation: suspend () -> Unit) {
        if (mutable.value.saving) return
        mutable.update { it.copy(saving = true, notice = null) }
        scope.launch {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(notice = CaptureNotice.STORAGE_ERROR) }
            } finally {
                mutable.update { it.copy(saving = false) }
            }
        }
    }

    private fun cancelGeneration() {
        val status = controller.state.value.status
        if (status == "generating_capture_tasks") controller.cancel()
        if (status == "generating_capture_tasks" || status == "idle") controller.dismissError()
    }
}
