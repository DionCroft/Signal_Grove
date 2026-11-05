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
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

val kanbanTaskEditActions=listOf(HostKanbanTaskAction.Metadata,HostKanbanTaskAction.Priority,HostKanbanTaskAction.Assignee,HostKanbanTaskAction.Comment,HostKanbanTaskAction.Delete)
fun kanbanTaskActionWord(action:HostKanbanTaskAction)=when(action) {
    HostKanbanTaskAction.Create->Words.KanbanTaskCreate
    HostKanbanTaskAction.Metadata->Words.KanbanTaskEdit
    HostKanbanTaskAction.Priority->Words.KanbanTaskPriority
    HostKanbanTaskAction.Assignee->Words.KanbanTaskAssign
    HostKanbanTaskAction.Comment->Words.KanbanTaskComment
    HostKanbanTaskAction.Link->Words.KanbanTaskLink
    HostKanbanTaskAction.Unlink->Words.KanbanTaskUnlink
    HostKanbanTaskAction.Delete->Words.KanbanTaskDelete
}
fun kanbanTaskStateWord(state:String)=when(state) {"acknowledged"->Words.KanbanTaskAcknowledged;"confirmed"->Words.KanbanTaskObserved;else->managementStateWord(state)}
private fun taskFieldWord(key:String)=when(key) {"title"->Words.KanbanTaskTitle;"body"->Words.KanbanBody;"priority"->Words.KanbanPriority;"assignee"->Words.KanbanAssignee;"author"->Words.KanbanTaskAuthor;else->null}
@Composable private fun TaskChoice(key:String,word:Words,value:Boolean,changed:(Boolean)->Unit) {
    val text=label(word)
    Row(Modifier.fillMaxWidth()) {Checkbox(value,changed,Modifier.heightIn(min=48.dp).testTag("task-set-"+key).semantics {contentDescription=text});Text(text,Modifier.weight(1f))}
}
@Composable private fun TaskInput(key:String,word:Words,value:String,changed:(String)->Unit,multiline:Boolean=false) {
    val text=label(word)
    Column(Modifier.fillMaxWidth().testTag("task-input-"+key),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(text,Modifier.testTag("task-label-"+key),style=MaterialTheme.typography.labelMedium)
        OutlinedTextField(value,changed,Modifier.fillMaxWidth().testTag("task-field-"+key).semantics {contentDescription=text},singleLine=!multiline,minLines=if(multiline) 2 else 1,maxLines=if(multiline) 6 else 1)
    }
}

@Composable fun KanbanTaskForm(editor:ReviewedKanbanTaskEditor,close:()->Unit,review:(HostKanbanTaskChange)->Unit) {
    val task=editor.subject.task
    var title by remember(editor) {mutableStateOf(task.title)}
    var body by remember(editor) {mutableStateOf(task.body.orEmpty())}
    var priority by remember(editor) {mutableStateOf(task.priority.toString())}
    var assignee by remember(editor) {mutableStateOf(task.assignee.orEmpty())}
    var comment by remember(editor) {mutableStateOf("")}
    var author by remember(editor) {mutableStateOf("dashboard")}
    var setTitle by remember(editor) {mutableStateOf(false)}
    var setBody by remember(editor) {mutableStateOf(false)}
    val request=runCatching {
        when(editor.action) {
            HostKanbanTaskAction.Metadata->HostKanbanTaskChange.metadata(editor.inventory,editor.snapshot,editor.subject,title.takeIf {setTitle},body.takeIf {setBody})
            HostKanbanTaskAction.Priority->HostKanbanTaskChange.priority(editor.inventory,editor.snapshot,editor.subject,priority.toInt())
            HostKanbanTaskAction.Assignee->HostKanbanTaskChange.assignee(editor.inventory,editor.snapshot,editor.subject,assignee)
            HostKanbanTaskAction.Comment->HostKanbanTaskChange.comment(editor.inventory,editor.snapshot,editor.subject,comment,author)
            HostKanbanTaskAction.Delete->HostKanbanTaskChange.delete(editor.inventory,editor.snapshot,editor.subject)
            else->throw HostKanbanTaskInvalid()
        }
    }.getOrNull()
    AlertDialog(onDismissRequest=close,title={Text(label(kanbanTaskActionWord(editor.action)))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("task-form"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(editor.inventory.owner.url);Text(editor.inventory.owner.profile);Text(editor.snapshot.board.slug);SelectionContainer {Column {Text(task.id);Text(task.title)}};Text(label(Words.ProjectDraftPrivate))}
            when(editor.action) {
                HostKanbanTaskAction.Metadata->{
                    item {TaskChoice("title",Words.KanbanTaskSetTitle,setTitle,{setTitle=it});if(setTitle) TaskInput("title",Words.KanbanTaskTitle,title,{title=it})}
                    item {TaskChoice("body",Words.KanbanTaskSetBody,setBody,{setBody=it});if(setBody) TaskInput("body",Words.KanbanBody,body,{body=it},true)}
                }
                HostKanbanTaskAction.Priority->item {TaskInput("priority",Words.KanbanPriority,priority,{priority=it})}
                HostKanbanTaskAction.Assignee->item {TaskInput("assignee",Words.KanbanAssignee,assignee,{assignee=it});Text(label(Words.KanbanTaskAssignEffect))}
                HostKanbanTaskAction.Comment->{item {TaskInput("body",Words.KanbanTaskCommentBody,comment,{comment=it},true)};item {TaskInput("author",Words.KanbanTaskAuthor,author,{author=it});Text(label(Words.KanbanTaskCommentEffect))}}
                HostKanbanTaskAction.Delete->item {Text(label(Words.KanbanTaskDeleteEffect))}
                else->Unit
            }
            if(request==null) item {Text(label(Words.KanbanTaskInvalid))}
        }
    },confirmButton={TextButton({request?.let(review)},enabled=request!=null,modifier=Modifier.heightIn(min=48.dp).testTag("task-form-review")) {Text(label(Words.Review))}},dismissButton={TextButton(close,Modifier.heightIn(min=48.dp).testTag("task-form-cancel")) {Text(label(Words.Cancel))}})
}

@Composable fun KanbanTaskChangeReview(review:ReviewedKanbanTaskChange,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    val request=review.request
    var granted by remember(review) {mutableStateOf(false)}
    var deleteId by remember(review) {mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text(label(kanbanTaskActionWord(request.action)))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("task-change-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(request.owner.url);Text(request.owner.profile);Text(request.owner.actor);Text(request.board.slug);Text(label(Words.KanbanDbPath));SelectionContainer {Text(request.board.dbPath)}}
            request.subject?.let {detail->item {Text(label(Words.KanbanTaskId));SelectionContainer {Column {Text(detail.task.id);Text(detail.task.title)}}}}
            items(request.values.entries.toList(),key={it.key}) {(key,value)->taskFieldWord(key)?.let {word->Column {Text(label(if(key=="body" && request.action==HostKanbanTaskAction.Comment) Words.KanbanTaskCommentBody else word));SelectionContainer {Text(value.jsonPrimitive.content.ifEmpty {label(Words.ProjectEmptyValue)})}}}}
            item {
                Text(label(Words.KanbanTaskEditEffect))
                when(request.action) {HostKanbanTaskAction.Assignee->Text(label(Words.KanbanTaskAssignEffect));HostKanbanTaskAction.Comment->Text(label(Words.KanbanTaskCommentEffect));HostKanbanTaskAction.Delete->Text(label(Words.KanbanTaskDeleteEffect));else->Unit}
                Text(label(Words.KanbanBoundary));Text(label(Words.KanbanTaskReceiptBoundary))
            }
            if(request.action==HostKanbanTaskAction.Delete) item {TaskInput("delete-id",Words.KanbanTaskDeleteId,deleteId,{deleteId=it})}
            item {TaskChoice("grant",Words.KanbanTaskGrant,granted,{granted=it})}
        }
    },confirmButton={TextButton(confirm,enabled=enabled && granted && (request.action!=HostKanbanTaskAction.Delete || deleteId==request.id),modifier=Modifier.heightIn(min=48.dp).testTag("task-change-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.heightIn(min=48.dp).testTag("task-change-cancel")) {Text(label(Words.Cancel))}})
}

@Composable fun KanbanTaskReceiptSummary(summary:JsonObject,state:String) {
    Text(label(kanbanTaskStateWord(state)))
    if(summary["host_acknowledged"]==JsonPrimitive(true) && summary["effect_reported"]!=JsonPrimitive(true)) Text(label(Words.KanbanBoardEffectNotReported))
    val fields=mutableListOf<String>()
    for(field in (summary["fields"] as? JsonArray).orEmpty()) (field as? JsonPrimitive)?.content?.let(::taskFieldWord)?.let {fields.add(label(it))}
    if(fields.isNotEmpty()) Text(fields.joinToString(" · "))
    Text(label(Words.KanbanTaskReceiptBoundary))
}
@Composable fun KanbanTaskReport(report:HostKanbanTaskReport) {
    val summary=remember(report.receipt) {runCatching {Json.parseToJsonElement(report.receipt.resultSummary).jsonObject}.getOrDefault(JsonObject(emptyMap()))}
    OutlinedCard(Modifier.fillMaxWidth().testTag("task-change-report")) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label(mcpActionWord(report.receipt.action)),style=MaterialTheme.typography.titleMedium);KanbanTaskReceiptSummary(summary,report.receipt.state)
        report.warning?.let {SelectionContainer {Text(it)}}
    }}
}
