package org.hermesmobile.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.*
import java.text.DateFormat
import java.util.Date

private fun kanbanTime(seconds:Long)=DateFormat.getDateTimeInstance().format(Date(seconds*1000))
private fun kanbanStatus(value:String):Words?=when(value) {"triage"->Words.KanbanTriage;"todo"->Words.KanbanTodo;"scheduled"->Words.KanbanScheduled;"ready"->Words.KanbanReady;"running"->Words.KanbanRunning;"blocked"->Words.KanbanBlocked;"review"->Words.KanbanReview;"done"->Words.KanbanDone;"archived"->Words.HostProjectArchived;else->null}
@Composable private fun KanbanStatus(value:String) {Text(kanbanStatus(value)?.let {label(it)} ?: value)}
@Composable private fun KanbanValue(word:Words,value:String) {Text(label(word),style=MaterialTheme.typography.labelLarge);SelectionContainer {Text(value)}}
@Composable private fun KanbanStamp(word:Words,time:Long?) {time?.let {Text(label(word).replace("{time}",kanbanTime(it)))}}
private fun kanbanField(key:String)=when(key) {
    "workspace_kind"->Words.KanbanWorkspaceKind;"workspace_path"->Words.KanbanWorkdir;"branch_name"->Words.KanbanBranch;"project_id"->Words.HostProjectId;"workflow_template_id"->Words.KanbanWorkflow;"current_step_key"->Words.KanbanStep;"model_override"->Words.KanbanModel;"provider_override"->Words.KanbanProvider;"reasoning_effort"->Words.KanbanReasoning;"completion_contract"->Words.KanbanContract;"last_failure_error"->Words.KanbanFailure;"current_run_started_at"->Words.KanbanCurrentStarted;"max_runtime_seconds"->Words.KanbanRuntime;"consecutive_failures"->Words.KanbanFailures;"max_retries"->Words.KanbanRetries;"goal_max_turns"->Words.KanbanGoalTurns;"block_recurrences"->Words.KanbanBlockRecurrences;"goal_mode"->Words.KanbanGoal;else->Words.KanbanSkills
}

@Composable fun HostKanbanDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    val visible=vm.visibleHostKanban(s)
    val generation=vm.hostProjectReviewGeneration()
    var review by remember(s.apiScope,s.url,s.profile,s.identity,vm.projectReceiptHome(s),generation) {mutableStateOf<ReviewedHostKanbanRead?>(null)}
    var query by remember(visible.board) {mutableStateOf("")}
    var column by remember(visible.board) {mutableStateOf<String?>(null)}
    fun offer(board:HostKanbanBoard?=null,id:String?=null) {review=vm.prepareHostKanbanRead(board,id)}
    fun dismiss() {review=null;close()}
    val read=vm.prepareHostKanbanRead()
    AlertDialog(onDismissRequest=::dismiss,title={Text(label(Words.HostKanban))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("host-kanban-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {if(vm.hostProjectsContextCurrent(s)) {Text(s.url);Text(s.profile)};Text(label(Words.KanbanBoundary))}
            item {TextButton({review=read},enabled=read!=null,modifier=Modifier.heightIn(min=48.dp).testTag("kanban-inventory-read")) {Text(label(Words.KanbanInventoryRead))};if(read==null && !s.busy) Text(label(Words.HostProjectsReload))}
            if(s.busy) item {QuietProgress();Text(label(Words.Loading));TextButton(vm::cancel,Modifier.heightIn(min=48.dp).testTag("kanban-cancel-work")) {Text(label(Words.Cancel))}}
            visible.inventory?.let {inventory->
                item {Text(label(Words.HostProjectsSnapshot).replace("{time}",DateFormat.getDateTimeInstance().format(Date(inventory.observedAt))));KanbanValue(Words.KanbanCurrent,inventory.current);if(inventory.boards.isEmpty()) Text(label(Words.EmptyInventory))}
                items(inventory.boards,key={"board:"+it.slug}) {board->OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(board.name.ifBlank {board.slug},style=MaterialTheme.typography.titleMedium);SelectionContainer {Text(board.slug)}
                    board.total?.let {Text(label(Words.KanbanTotal).replace("{count}",it.toString()))};board.projectName?.let {KanbanValue(Words.KanbanProject,it)}
                    if(board.current) Text(label(Words.KanbanCurrent))
                    if(board.archived) Text(label(Words.KanbanArchivedRead))
                    TextButton({offer(board)},enabled=!s.busy && !board.archived,modifier=Modifier.heightIn(min=48.dp).testTag("kanban-board-read-"+board.slug)) {Text(label(Words.KanbanBoardRead))}
                }}}
            }
            visible.board?.let {snapshot->
                item {Text(snapshot.board.name.ifBlank {snapshot.board.slug},style=MaterialTheme.typography.titleLarge);SelectionContainer {Text(snapshot.board.slug)};Text(label(Words.HostProjectsSnapshot).replace("{time}",DateFormat.getDateTimeInstance().format(Date(snapshot.observedAt))));Text(label(Words.KanbanObservation))}
                item {val b=snapshot.board;b.description?.let {KanbanValue(Words.HostProjectDescription,it)};KanbanValue(Words.KanbanDbPath,b.dbPath);b.workdir?.let {KanbanValue(Words.KanbanWorkdir,it)};b.workspaceKind?.let {KanbanValue(Words.KanbanWorkspaceKind,it)};b.projectId?.let {KanbanValue(Words.HostProjectId,it)};b.projectName?.let {KanbanValue(Words.KanbanProject,it)};b.icon?.let {KanbanValue(Words.HostProjectIcon,it)};b.color?.let {KanbanValue(Words.HostProjectColor,it)};TextButton({offer(b)},enabled=!s.busy,modifier=Modifier.heightIn(min=48.dp).testTag("kanban-board-refresh")) {Text(label(Words.KanbanBoardRead))}}
                item {OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().testTag("kanban-search"),label={Text(label(Words.Search))},singleLine=true)}
                item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(column==null,{column=null},label={Text(label(Words.KanbanFilter))},modifier=Modifier.heightIn(min=48.dp).testTag("kanban-filter-all"))
                    snapshot.columns.forEach {c->FilterChip(column==c.name,{column=c.name},label={KanbanStatus(c.name)},modifier=Modifier.heightIn(min=48.dp).testTag("kanban-filter-"+c.name))}
                }}
                val groups=snapshot.columns.filter {column==null || it.name==column}.map {c->c to c.tasks.filter {t->query.isBlank() || listOf(t.id,t.title,t.assignee.orEmpty(),t.tenant.orEmpty()).any {it.contains(query,ignoreCase=true)}}}
                if(groups.all {it.second.isEmpty()}) item {Text(label(Words.KanbanNoMatches))}
                groups.forEach {(c,tasks)->
                    item(key="column:"+c.name) {KanbanStatus(c.name)}
                    items(tasks,key={"task:"+it.id}) {task->OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(task.title,style=MaterialTheme.typography.titleMedium);SelectionContainer {Text(task.id)};KanbanStatus(task.status)
                        task.assignee?.let {KanbanValue(Words.KanbanAssignee,it)};task.tenant?.let {KanbanValue(Words.KanbanTenant,it)};KanbanValue(Words.KanbanPriority,task.priority.toString())
                        task.summary?.let {SelectionContainer {Text(it)}}
                        TextButton({offer(snapshot.board,task.id)},enabled=!s.busy,modifier=Modifier.heightIn(min=48.dp).testTag("kanban-task-read-"+task.id)) {Text(label(Words.KanbanTaskRead))}
                    }}}
                }
            }
            s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}}
        }
    },confirmButton={TextButton(::dismiss,Modifier.heightIn(min=48.dp).testTag("kanban-close")) {Text(label(Words.Close))}})
    visible.detail?.let {detail->HostKanbanDetails(detail,s.busy,vm::closeHostKanbanDetail) {id->offer(detail.board,id)}}
    review?.takeIf(vm::hostKanbanReadCurrent)?.let {frozen->AlertDialog(onDismissRequest={review=null},title={Text(label(if(frozen.board==null) Words.KanbanInventoryRead else if(frozen.taskId==null) Words.KanbanBoardRead else Words.KanbanTaskRead))},text={
        LazyColumn(Modifier.heightIn(max=480.dp).testTag("kanban-read-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(frozen.owner.url);Text(frozen.owner.profile);Text(frozen.owner.actor)}
            frozen.board?.let {b->item {Text(b.name.ifBlank {b.slug});SelectionContainer {Text(b.slug)};KanbanValue(Words.KanbanDbPath,b.dbPath)}}
            frozen.taskId?.let {id->item {KanbanValue(Words.KanbanTaskId,id)}}
            item {Text(label(Words.KanbanReadEffect));Text(label(Words.KanbanBoundary));Text(label(Words.KanbanObservation))}
        }
    },confirmButton={TextButton({review=null;vm.loadHostKanban(frozen)},enabled=!s.busy,modifier=Modifier.heightIn(min=48.dp).testTag("kanban-read-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({review=null},Modifier.heightIn(min=48.dp).testTag("kanban-read-cancel")) {Text(label(Words.Cancel))}})}
}

@Composable private fun HostKanbanDetails(detail:HostKanbanDetail,busy:Boolean,close:()->Unit,read:(String)->Unit) {
    val task=detail.task
    AlertDialog(onDismissRequest=close,title={Text(task.title)},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("kanban-task-detail"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(detail.owner.url);Text(detail.owner.profile);Text(detail.board.slug);Text(label(Words.KanbanObservation));KanbanValue(Words.KanbanTaskId,task.id);KanbanStatus(task.status)}
            item {task.assignee?.let {KanbanValue(Words.KanbanAssignee,it)};task.tenant?.let {KanbanValue(Words.KanbanTenant,it)};KanbanValue(Words.KanbanPriority,task.priority.toString());KanbanStamp(Words.HostProjectCreated,task.createdAt);KanbanStamp(Words.KanbanTaskStarted,task.startedAt);KanbanStamp(Words.KanbanEnded,task.completedAt);KanbanStamp(Words.KanbanHeartbeat,task.heartbeat);task.currentRunId?.let {KanbanValue(Words.KanbanRunId,it.toString())}}
            task.body?.let {item {KanbanValue(Words.KanbanBody,it)}};task.summary?.let {item {KanbanValue(Words.KanbanSummary,it)}};task.result?.let {item {KanbanValue(Words.KanbanResult,it)}}
            items(task.fields.entries.toList(),key={"field:"+it.key}) {(key,value)->
                when(key) {"current_run_started_at"->KanbanStamp(Words.KanbanCurrentStarted,value.toLongOrNull()?.takeIf {it<=Long.MAX_VALUE/1000});"goal_mode"->KanbanValue(kanbanField(key),label(if(value=="true") Words.Yes else Words.No));else->KanbanValue(kanbanField(key),value)}
            }
            for((word,ids) in listOf(Words.KanbanParents to detail.parents,Words.KanbanChildren to detail.children)) {
                item {Text(label(word),style=MaterialTheme.typography.titleMedium);if(ids.isEmpty()) Text(label(Words.EmptyInventory))}
                items(ids,key={word.name+":"+it}) {id->val linked=detail.linked.singleOrNull {it.id==id};Column {SelectionContainer {Text(id)};linked?.let {Text(it.title);KanbanStatus(it.status)};TextButton({read(id)},enabled=!busy,modifier=Modifier.heightIn(min=48.dp).testTag("kanban-link-read-"+word.name+"-"+id)) {Text(label(Words.KanbanTaskRead))}}}
            }
            if(detail.childResults.isNotEmpty()) item {Text(label(Words.KanbanChildResults),style=MaterialTheme.typography.titleMedium)}
            items(detail.childResults,key={"child-result:"+it.id}) {v->Column {Text(v.id);Text(v.title);KanbanStatus(v.status);v.summary?.let {KanbanValue(Words.KanbanSummary,it)};v.result?.let {KanbanValue(Words.KanbanResult,it)}}}
            item {Text(label(Words.KanbanComments),style=MaterialTheme.typography.titleMedium);if(detail.comments.isEmpty()) Text(label(Words.EmptyInventory))}
            items(detail.comments,key={"comment:"+it.id}) {c->Column {SelectionContainer {Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(c.author);Text(c.body)}};Text(kanbanTime(c.createdAt))}}
            item {Text(label(Words.KanbanRuns),style=MaterialTheme.typography.titleMedium);if(detail.runs.isEmpty()) Text(label(Words.EmptyInventory))}
            items(detail.runs,key={"run:"+it.id}) {v->OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {KanbanValue(Words.KanbanRunId,v.id.toString());Text(v.status);v.outcome?.let {KanbanValue(Words.KanbanOutcome,it)};v.profile?.let {KanbanValue(Words.KanbanAssignee,it)};v.step?.let {KanbanValue(Words.KanbanStep,it)};KanbanStamp(Words.KanbanStarted,v.startedAt);KanbanStamp(Words.KanbanEnded,v.endedAt);KanbanStamp(Words.KanbanHeartbeat,v.heartbeat);v.summary?.let {KanbanValue(Words.KanbanSummary,it)};v.error?.let {KanbanValue(Words.KanbanFailure,it)}}}}
            item {Text(label(Words.KanbanEvents),style=MaterialTheme.typography.titleMedium);if(detail.events.isEmpty()) Text(label(Words.EmptyInventory))}
            items(detail.events,key={"event:"+it.id}) {v->Column {SelectionContainer {Text(v.kind)};Text(kanbanTime(v.createdAt));v.runId?.let {KanbanValue(Words.KanbanRunId,it.toString())}}}
            item {Text(label(Words.KanbanAttachments),style=MaterialTheme.typography.titleMedium);Text(label(Words.KanbanAttachmentInfo));if(detail.attachments.isEmpty()) Text(label(Words.EmptyInventory))}
            items(detail.attachments,key={"attachment:"+it.id}) {v->Column {SelectionContainer {Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(v.filename);v.type?.let {Text(it)};Text(v.size.toString()+" B");v.author?.let {Text(it)}}};Text(kanbanTime(v.createdAt))}}
        }
    },confirmButton={TextButton(close,Modifier.heightIn(min=48.dp).testTag("kanban-detail-close")) {Text(label(Words.Close))}})
}
