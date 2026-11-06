package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.*

val kanbanTaskOverrideActions=listOf(HostKanbanTaskAction.ModelOverride,HostKanbanTaskAction.ReasoningOverride)
private val overrideFields=listOf("model_override","provider_override","reasoning_effort")
internal fun kanbanOverrideTaskBlock(task:HostKanbanTask):Words?=when {
    task.status=="archived"->Words.KanbanOverrideArchived
    !task.reportedOverrideFields.containsAll(overrideFields)->Words.KanbanOverrideMissing
    else->null
}
private enum class OverrideMode { Set,Clear }
internal fun kanbanOverrideTitle(action:HostKanbanTaskAction)=if(action==HostKanbanTaskAction.ModelOverride) Words.KanbanOverrideModelTitle else Words.KanbanOverrideReasoningTitle

@Composable internal fun KanbanOverrideCurrent(task:HostKanbanTask) {
    Text(label(Words.KanbanOverrideCurrent),style=MaterialTheme.typography.titleMedium)
    for(field in overrideFields) Column(Modifier.fillMaxWidth().testTag("task-current-"+field)) {
        Text(label(taskFieldWord(field)!!),style=MaterialTheme.typography.labelMedium)
        when {
            field !in task.reportedOverrideFields->Text(label(Words.KanbanOverrideUnreported))
            task.fields[field]==null->Text(label(Words.KanbanOverrideUnset))
            else->SelectionContainer {Text(task.fields.getValue(field).ifEmpty {label(Words.ProjectEmptyValue)})}
        }
    }
}
@Composable internal fun KanbanOverrideEffect(action:HostKanbanTaskAction) {
    Text(label(Words.KanbanOverrideNextDispatch))
    Text(label(if(action==HostKanbanTaskAction.ModelOverride) Words.KanbanOverrideModelEffect else Words.KanbanOverrideReasoningEffect))
}
@Composable private fun OverrideOption(tag:String,word:Words,selected:Boolean,choose:()->Unit) {
    val title=label(word)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        RadioButton(selected,choose,Modifier.heightIn(min=48.dp).testTag(tag).semantics {contentDescription=title})
        Text(title,Modifier.weight(1f).testTag(tag+"-label"))
    }
}

/** Private, unsaved draft. Set/clear is always an explicit choice, independent of empty inputs. */
@Composable fun KanbanOverrideForm(editor:ReviewedKanbanTaskEditor,close:()->Unit,review:(HostKanbanTaskChange)->Unit) {
    val task=editor.subject.task
    val modelPhase=editor.action==HostKanbanTaskAction.ModelOverride
    val blocked=kanbanOverrideTaskBlock(task)
    var mode by remember(editor) {mutableStateOf<OverrideMode?>(null)}
    var model by remember(editor) {mutableStateOf(task.fields["model_override"].orEmpty())}
    var provider by remember(editor) {mutableStateOf(task.fields["provider_override"].orEmpty())}
    var effort by remember(editor) {mutableStateOf<HostAuxEffort?>(null)}
    val request=if(blocked!=null || editor.action !in kanbanTaskOverrideActions) null else runCatching {
        when(mode) {
            OverrideMode.Clear->if(modelPhase) HostKanbanTaskChange.clearModelOverride(editor.inventory,editor.snapshot,editor.subject) else HostKanbanTaskChange.clearReasoningOverride(editor.inventory,editor.snapshot,editor.subject)
            OverrideMode.Set->if(modelPhase) HostKanbanTaskChange.modelOverride(editor.inventory,editor.snapshot,editor.subject,model,provider) else HostKanbanTaskChange.reasoningOverride(editor.inventory,editor.snapshot,editor.subject,requireNotNull(effort?.wire))
            null->null
        }
    }.getOrNull()
    AlertDialog(onDismissRequest=close,title={Text(label(kanbanOverrideTitle(editor.action)))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("task-override-form"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {SelectionContainer {Column {Text(task.id);Text(task.title)}}}
            if(blocked!=null) item {Text(label(blocked))} else {
                item {OverrideOption("task-override-set",if(modelPhase) Words.KanbanOverrideSetModel else Words.KanbanOverrideSetReasoning,mode==OverrideMode.Set) {mode=OverrideMode.Set}}
                item {OverrideOption("task-override-clear",if(modelPhase) Words.KanbanTaskClearModel else Words.KanbanTaskClearReasoning,mode==OverrideMode.Clear) {mode=OverrideMode.Clear}}
                if(mode==OverrideMode.Set) {
                    if(modelPhase) {
                        item {TaskInput("model_override",Words.KanbanModel,model,{model=it})}
                        item {TaskInput("provider_override",Words.KanbanProvider,provider,{provider=it});if(provider.isBlank()) Text(label(Words.KanbanOverrideProviderClear))}
                    } else items(HostAuxEffort.entries.filter {it.wire!=null},key={it.name}) {choice->
                        OverrideOption("task-override-effort-"+choice.wire,hostAuxEffortWord(choice),effort==choice) {effort=choice}
                    }
                }
                if(request==null) item {Text(label(if(mode==null) Words.KanbanOverrideChoose else Words.KanbanOverrideInvalid))}
            }
            item {KanbanOverrideCurrent(task)}
            item {KanbanOverrideEffect(editor.action)}
            item {Text(editor.inventory.owner.url);Text(editor.inventory.owner.profile);Text(editor.inventory.owner.actor);Text(editor.snapshot.board.slug);Text(label(Words.ProjectDraftPrivate))}
        }
    },confirmButton={TextButton({request?.let(review)},enabled=request!=null,modifier=Modifier.heightIn(min=48.dp).testTag("task-form-review")) {Text(label(Words.Review))}},dismissButton={TextButton(close,Modifier.heightIn(min=48.dp).testTag("task-form-cancel")) {Text(label(Words.Cancel))}})
}
