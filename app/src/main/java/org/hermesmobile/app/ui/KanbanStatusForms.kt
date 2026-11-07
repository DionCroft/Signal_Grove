package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import java.text.DateFormat
import java.util.Date

internal fun isKanbanTaskReceipt(action:String)=HostKanbanTaskAction.receipt(action) || HostKanbanStatusChange.receipt(action)
internal fun kanbanReceiptStateWord(action:String,state:String)=if(HostKanbanStatusChange.receipt(action) && state=="confirmed") Words.KanbanStatusObserved else kanbanTaskStateWord(state)

internal fun kanbanStatusEffectWord(effect:HostKanbanStatusEffect)=when(effect) {
    HostKanbanStatusEffect.LatestTaskAtWrite->Words.KanbanStatusLatestEffect
    HostKanbanStatusEffect.HostMayRefuseAfterAuditWrite->Words.KanbanStatusRefusalEffect
    HostKanbanStatusEffect.MayCloseAttempt->Words.KanbanStatusCloseEffect
    HostKanbanStatusEffect.ForceCurrentClaim->Words.KanbanStatusForceEffect
    HostKanbanStatusEffect.MayRequestWorkerTermination->Words.KanbanStatusTerminateEffect
    HostKanbanStatusEffect.WorkerMayContinue->Words.KanbanStatusContinueEffect
    HostKanbanStatusEffect.MayQueueWork->Words.KanbanStatusQueueEffect
    HostKanbanStatusEffect.MayRestoreImplementer->Words.KanbanStatusRestoreEffect
    HostKanbanStatusEffect.MayInheritReviewer->Words.KanbanStatusReviewerEffect
    HostKanbanStatusEffect.MayResumeReview->Words.KanbanStatusResumeEffect
    HostKanbanStatusEffect.MayInvalidateDescendants->Words.KanbanStatusInvalidateEffect
    HostKanbanStatusEffect.MayCleanManagedAndParentWorkspaces->Words.KanbanStatusCleanupEffect
    HostKanbanStatusEffect.MayStageReferencedFiles->Words.KanbanStatusStageEffect
    HostKanbanStatusEffect.ReplacesStoredResult->Words.KanbanStatusReplaceEffect
    HostKanbanStatusEffect.ParkWithoutWakeTime->Words.KanbanStatusParkEffect
    HostKanbanStatusEffect.MayInvokeLifecycleHook->Words.KanbanStatusHookEffect
}

@Composable fun KanbanStatusForm(editor:ReviewedKanbanStatusEditor,close:()->Unit,read:(HostKanbanStatusPlan)->Unit) {
    var target by remember(editor) {mutableStateOf<HostKanbanStatusTarget?>(null)}
    var statement by remember(editor) {mutableStateOf("")}
    var include by remember(editor) {mutableStateOf(false)}
    val required=target in setOf(HostKanbanStatusTarget.Done,HostKanbanStatusTarget.Blocked)
    val optional=target in setOf(HostKanbanStatusTarget.Review,HostKanbanStatusTarget.Scheduled)
    val plan=target?.let {runCatching {HostKanbanStatusPlan.review(editor.subject,it,if(required || optional && include) statement else null)}.getOrNull()}
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val dismiss={focus.clearFocus();keyboard?.hide();close()}
    val fullCaption=label(Words.KanbanStatusPrepare)
    AlertDialog(onDismissRequest=dismiss,modifier=Modifier.safeDrawingPadding(),properties=DialogProperties(decorFitsSystemWindows=false),text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("status-form"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(label(Words.KanbanStatusChange),style=MaterialTheme.typography.headlineSmall)}
            item {Text(editor.inventory.owner.url);Text(editor.inventory.owner.profile);Text(editor.subject.task.id);Text(editor.subject.task.title);KanbanStatus(editor.subject.task.status)}
            item {Text(label(Words.KanbanStatusTarget),style=MaterialTheme.typography.titleMedium)}
            items(HostKanbanStatusTarget.entries,key={it.name}) {choice->
                val available=HostKanbanStatusPlan.supports(editor.subject.task.status,choice)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    RadioButton(target==choice,{if(target!=choice) {target=choice;statement="";include=false}},enabled=available,modifier=Modifier.heightIn(min=48.dp).testTag("status-target-"+choice.name))
                    Column(Modifier.weight(1f)) {Text(label(kanbanStatusWord(choice.wire)!!));if(!available) Text(label(Words.KanbanStatusUnavailable),style=MaterialTheme.typography.bodySmall)}
                }
            }
            if(optional) item {TaskChoice("status-include",if(target==HostKanbanStatusTarget.Review) Words.KanbanStatusIncludeSummary else Words.KanbanStatusIncludeReason,include,{include=it})}
            if(required || optional && include) item {TaskInput("status-statement",when(target) {HostKanbanStatusTarget.Done->Words.KanbanStatusResult;HostKanbanStatusTarget.Review->Words.KanbanStatusSummary;else->Words.KanbanStatusReason},statement,{statement=it},multiline=true)}
            if(plan==null) item {Text(label(Words.KanbanStatusInvalid))}
            item {Text(label(Words.KanbanStatusReadEffect))}
        }
    },confirmButton={
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            TextButton({plan?.let {focus.clearFocus();keyboard?.hide();read(it)}},enabled=plan!=null,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("status-read-impact").semantics {contentDescription=fullCaption}) {Text(label(Words.KanbanStatusReadShort))}
            TextButton(dismiss,Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("status-form-cancel")) {Text(label(Words.Cancel))}
        }
    })
}

@Composable fun KanbanStatusReview(review:ReviewedKanbanStatusChange,enabled:Boolean,close:()->Unit,confirm:(String,Boolean)->Unit) {
    val request=review.request
    var typed by remember(review) {mutableStateOf("")}
    var granted by remember(review) {mutableStateOf(false)}
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val dismiss={focus.clearFocus();keyboard?.hide();close()}
    AlertDialog(onDismissRequest=dismiss,modifier=Modifier.safeDrawingPadding(),properties=DialogProperties(decorFitsSystemWindows=false),text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("status-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(label(Words.KanbanStatusReview),style=MaterialTheme.typography.headlineSmall)}
            item {Text(request.owner.url);Text(request.owner.profile);Text(request.owner.actor);Text(request.board.slug);SelectionContainer {Text(request.board.dbPath)};Text(request.id);Text(request.plan.subject.task.title);KanbanStatus(request.plan.subject.task.status);Text(label(Words.KanbanStatusTarget));KanbanStatus(request.plan.target.wire)}
            if(request.plan.target==HostKanbanStatusTarget.Done) item {Text(label(Words.KanbanStatusStoredResult));TaskReviewedValue(request.plan.subject.task.result?.let(::JsonPrimitive) ?: JsonNull)}
            items(request.body().entries.filter {it.key!="status"}.toList(),key={"field:"+it.key}) {(key,value)->
                Column(Modifier.testTag("status-reviewed-"+key)) {Text(label(when(key) {"result"->Words.KanbanStatusResult;"summary"->Words.KanbanStatusSummary;else->Words.KanbanStatusReason}));TaskReviewedValue(value)}
            }
            items(request.plan.effects.toList(),key={"effect:"+it.name}) {effect->Text(label(kanbanStatusEffectWord(effect)),Modifier.testTag("status-effect-"+effect.name))}
            item {Text(label(Words.KanbanStatusImpact).replace("{count}",request.observations.size.toString()),style=MaterialTheme.typography.titleMedium);Text(label(Words.HostProjectsSnapshot).replace("{time}",DateFormat.getDateTimeInstance().format(Date(request.observations.values.maxOf {it.detail.observedAt}))))}
            items(request.observations.toSortedMap().values.toList(),key={"impact:"+it.detail.task.id}) {node->
                val detail=node.detail;val task=detail.task
                OutlinedCard(Modifier.fillMaxWidth().testTag("status-impact-"+task.id)) {SelectionContainer {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(task.id);Text(task.title);KanbanStatus(task.status)
                    task.assignee?.let {Text(label(Words.KanbanAssignee));Text(it)}
                    task.currentRunId?.let {Text(label(Words.KanbanRunId));Text(it.toString())}
                    for(key in listOf("workspace_kind","workspace_path")) task.fields[key]?.let {Text(label(if(key=="workspace_kind") Words.KanbanWorkspaceKind else Words.KanbanWorkdir));Text(it)}
                    for((word,ids) in listOf(Words.KanbanParents to detail.parents,Words.KanbanChildren to detail.children)) {Text(label(word));if(ids.isEmpty()) Text(label(Words.EmptyInventory)) else Text(ids.joinToString("\n"))}
                }}}
            }
            item {TaskInput("status-confirm-id",Words.KanbanStatusTypeId,typed,{typed=it})}
            item {TaskChoice("status-grant",Words.KanbanStatusGrant,granted,{granted=it});Text(label(Words.KanbanStatusReceiptBoundary))}
        }
    },confirmButton={
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            TextButton({focus.clearFocus();keyboard?.hide();confirm(typed,granted)},enabled=enabled && granted && typed==request.id,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("status-confirm")) {Text(label(Words.Confirm))}
            TextButton(dismiss,Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("status-review-cancel")) {Text(label(Words.Cancel))}
        }
    })
}

@Composable fun KanbanStatusReceiptSummary(receipt:ManagementOperation) {
    val summary=remember(receipt) {runCatching {Json.parseToJsonElement(receipt.resultSummary.ifBlank {receipt.requestSummary}).jsonObject}.getOrDefault(JsonObject(emptyMap()))}
    Text(label(kanbanReceiptStateWord(receipt.action,receipt.state)))
    summary.text("requested_status").takeIf {it.isNotBlank()}?.let {Text(label(Words.KanbanStatusTarget));KanbanStatus(it)}
    summary.text("reported_status").takeIf {it.isNotBlank()}?.let {Text(label(Words.KanbanStatusReported));KanbanStatus(it)}
    if(summary["requested_status_reported"]==JsonPrimitive(false)) Text(label(Words.KanbanStatusDifferent))
    if(summary["reported_state_observed"]==JsonPrimitive(true)) Text(label(Words.KanbanStatusObserved))
    else if(summary["host_acknowledged"]==JsonPrimitive(true)) Text(label(Words.KanbanStatusReadback))
    if(receipt.state=="unknown") Text(label(Words.KanbanStatusUnknown))
    Text(label(Words.KanbanStatusReceiptBoundary))
}

@Composable fun KanbanStatusReport(report:HostKanbanStatusReport) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("status-report")) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label(Words.KanbanStatusChange),style=MaterialTheme.typography.titleMedium);KanbanStatusReceiptSummary(report.receipt)
    }}
}
