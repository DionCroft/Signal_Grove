package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import java.util.UUID

private fun sessionActionWord(action: SessionAction)=when(action) {SessionAction.Lock->Words.LockModel;SessionAction.Fork->Words.ForkSession;SessionAction.Steer->Words.SteerRun}
private data class SessionReview(val session:Conversation,val action:SessionAction,val body:JsonObject)
@Composable fun SessionControls(vm:HermesViewModel) {
    val c by vm.selectedConversation.collectAsStateWithLifecycle()
    val operations by vm.serverOperations.collectAsStateWithLifecycle()
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val controlBusy by vm.controlBusy.collectAsStateWithLifecycle()
    val jobBusy by vm.jobBusy.collectAsStateWithLifecycle()
    val loading by vm.sessionDetailsLoading.collectAsStateWithLifecycle()
    val runtime by vm.requestedRuntime.collectAsStateWithLifecycle()
    val unresolved=operations.filter {it.state in setOf("sending","unknown")}
    var open by remember {mutableStateOf(false)}
    var review by remember {mutableStateOf<SessionReview?>(null)}
    var reviewed by remember {mutableStateOf<ServerOperation?>(null)}
    var forkTitle by remember(c?.key) {mutableStateOf((c?.title.orEmpty().take(170)+" fork").trim())}
    var guidance by remember(c?.key,c?.runId) {mutableStateOf("")}
    if((c!=null && c?.transport!="compat" && caps?.sessions==true) || unresolved.isNotEmpty()) IconButton({open=true}) {
        BadgedBox(badge={if(unresolved.isNotEmpty()) Badge {Text(unresolved.size.toString())}}) {Icon(Icons.Outlined.AccountTree,label(Words.SessionControls))}
    }
    LaunchedEffect(open,c?.key) {if(open) c?.let(vm::refreshSessionDetails)}
    if(open) AlertDialog(onDismissRequest={open=false},title={Text(label(Words.SessionControls))},text={
        LazyColumn(Modifier.testTag("session-control-list").heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(unresolved.isNotEmpty()) item {Text(label(Words.ReviewServerOutcome),color=MaterialTheme.colorScheme.error)}
            items(unresolved,key={it.key}) {op ->
                Text(op.action+" · "+op.target);Text(op.requestJson,style=MaterialTheme.typography.bodySmall)
                if(op.state=="unknown") TextButton({reviewed=op},enabled=!controlBusy) {Text(label(Words.ReviewedOutcome))}
            }
            c?.let {current ->
                val settled=current.status in (RunStates.authoritativeTerminal+"idle")
                val readOnly=SessionOrigin.readOnly(current)
                val allowed=!busy && !controlBusy && !jobBusy && unresolved.isEmpty() && settled && !readOnly
                if(readOnly) item {Text(label(Words.ReadOnlySession))}
                item {Text(current.title);Text(label(Words.SessionIdentity));Text(current.remoteId,style=MaterialTheme.typography.bodySmall)}
                item {Text(label(Words.SessionBusyInfo),style=MaterialTheme.typography.bodySmall)}
                val lock=operations.firstOrNull {it.sessionKey==current.key && it.action==SessionAction.Lock.name && it.state=="confirmed"}
                if(lock!=null) item {Text(label(Words.LastLockAck));Text(lock.responseJson,style=MaterialTheme.typography.bodySmall)}
                if(caps?.endpoints?.containsKey(SessionAction.Lock.endpoint)==true && caps?.has(SessionAction.Lock.endpoint)==true) {
                    item {TextButton({review=SessionReview(current,SessionAction.Lock,runtime.fields())},Modifier.testTag("session-lock"),enabled=allowed && runtime.model.isNotBlank()) {Text(label(Words.LockModel))}}
                    if(runtime.model.isBlank()) item {Text(label(Words.ChooseLockModel),style=MaterialTheme.typography.bodySmall)}
                }
                if(caps?.endpoints?.containsKey(SessionAction.Fork.endpoint)==true && caps?.has(SessionAction.Fork.endpoint)==true) {
                    item {OutlinedTextField(forkTitle,{forkTitle=it},Modifier.testTag("fork-title"),label={Text(label(Words.ConversationTitle))},enabled=allowed)}
                    item {TextButton({review=SessionReview(current,SessionAction.Fork,buildJsonObject {put("id","api_mobile_${UUID.randomUUID().toString().replace("-","")}");put("title",forkTitle)})},Modifier.testTag("session-fork"),enabled=allowed && forkTitle.isNotBlank() && forkTitle.length<=200) {Text(label(Words.ForkSession))}}
                }
                if(caps?.endpoints?.containsKey(SessionAction.Steer.endpoint)==true && caps?.has(SessionAction.Steer.endpoint)==true) {
                    item {OutlinedTextField(guidance,{guidance=it},Modifier.testTag("steer-input"),label={Text(label(Words.Guidance))},maxLines=5,enabled=!readOnly && !controlBusy && unresolved.isEmpty() && current.status=="running")}
                    item {TextButton({review=SessionReview(current,SessionAction.Steer,buildJsonObject {put("input",guidance)})},Modifier.testTag("session-steer"),enabled=!readOnly && !controlBusy && !jobBusy && unresolved.isEmpty() && current.status=="running" && guidance.isNotBlank() && guidance.length<=16384) {Text(label(Words.SteerRun))}}
                }
                item {Text(label(Words.SessionMetadata));TextButton({vm.refreshSessionDetails(current)},enabled=!loading && caps?.endpoints?.containsKey("session")==true) {Text(label(Words.Refresh))}}
                if(loading) item {QuietProgress()}
                val details=runCatching {Json.parseToJsonElement(current.detailsJson).jsonObject}.getOrNull()
                details?.entries?.filter {it.key in setOf("id","source","model","parent_session_id","end_reason","message_count","tool_call_count","input_tokens","output_tokens","cache_read_tokens","cache_write_tokens","estimated_cost_usd","actual_cost_usd","_fetched_at")}?.forEach {(key,value) -> item(key="detail:$key") {Text("$key: $value",style=MaterialTheme.typography.bodySmall)} }
            }
        }
    },confirmButton={TextButton({open=false}) {Text(label(Words.Close))}})
    review?.let {pending ->
        val info=when(pending.action) {SessionAction.Lock->Words.LockModelInfo;SessionAction.Fork->Words.ForkInfo;SessionAction.Steer->Words.SteerInfo}
        AlertDialog(onDismissRequest={review=null},title={Text(label(sessionActionWord(pending.action)))},text={LazyColumn(Modifier.heightIn(max=450.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(pending.session.title);Text(pending.session.remoteId,style=MaterialTheme.typography.bodySmall);Text(label(info))}
            item {Text(pending.body.toString())}
        }},confirmButton={TextButton({vm.sessionAction(pending.session,pending.action,pending.body);review=null;open=false}) {Text(label(Words.ConfirmChange))}},dismissButton={TextButton({review=null}) {Text(label(Words.Cancel))}})
    }
    reviewed?.let {op -> AlertDialog(onDismissRequest={reviewed=null},title={Text(label(Words.ReviewedOutcome))},text={Text(label(Words.ServerOutcomeReview))},confirmButton={TextButton({vm.reviewServerOutcome(op.key);reviewed=null}) {Text(label(Words.ConfirmChange))}},dismissButton={TextButton({reviewed=null}) {Text(label(Words.Cancel))}}) }
}
