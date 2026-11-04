package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.*

@Composable fun GatewayAutomationDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    var review by remember(s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedGatewayRead?>(null)}
    var actionReview by remember(s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedGatewayCron?>(null)}
    val receiptRows by vm.receipts.collectAsState();val receipts=vm.visibleReceipts(s,receiptRows)
    val pending=receipts.any {it.resourceScope==vm.resourceScope(s) && it.state in setOf("sending","unknown")}
    val supported=GatewayCronInventory.supportedProfile(s.profile)
    AlertDialog(onDismissRequest=close,title={Text(label(Words.GatewayAutomation))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("gateway-cron-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(s.url);Text(s.profile);Text(label(Words.GatewayConnectionEffect));TextButton({review=vm.prepareGatewayRead()},enabled=!s.busy && supported,modifier=Modifier.testTag("gateway-cron-load")) {Text(label(Words.GatewayLoad))}}
            if(!supported) item {Text(label(Words.GatewayScopeUnsupported))}
            if(s.busy) item {QuietProgress()}
            if(pending) item {Text(label(Words.ManagementOutcomePending))}
            s.notice?.let {item {Text(label(it))}}
            s.gateway.inventory?.let {inventory ->
                item {Text(label(when(inventory.gatewayRunning) {true->Words.GatewaySchedulerReported;false->Words.GatewaySchedulerUnavailable;null->Words.GatewaySchedulerUnknown}))}
                if(inventory.jobs.isEmpty()) item {Text(label(Words.EmptyInventory))}
                items(inventory.jobs,key={it.id}) {job ->Column {
                    Text(job.name,style=MaterialTheme.typography.titleMedium);Text(job.id);Text(job.schedule);Text(job.state)
                    Text(label(when(job.enabled) {true->Words.Enabled;false->Words.Disabled;null->Words.Unknown}))
                    if(job.nextRun.isNotBlank()) Text(label(Words.NextRun)+"\n"+job.nextRun)
                    if(job.lastStatus.isNotBlank()) Text(label(Words.HostCronLastStatus)+"\n"+job.lastStatus)
                    if(job.promptPreview.isNotBlank()) {Text(label(Words.GatewayPromptPreview));Text(job.promptPreview)}
                    val collision=GatewayCronRequest.collision(job,inventory.jobs)
                    val canControl=!s.busy && !pending && supported && job.addressable && !collision
                    TextButton({actionReview=vm.prepareGatewayCron(job,GatewayCronAction.Pause)},enabled=canControl && job.enabled==true,modifier=Modifier.testTag("gateway-pause-${job.id}")) {Text(label(Words.GatewayCronPause))}
                    TextButton({actionReview=vm.prepareGatewayCron(job,GatewayCronAction.Resume)},enabled=canControl && job.enabled==false && job.state=="paused",modifier=Modifier.testTag("gateway-resume-${job.id}")) {Text(label(Words.GatewayCronResume))}
                    TextButton({actionReview=vm.prepareGatewayCron(job,GatewayCronAction.Delete)},enabled=canControl,modifier=Modifier.testTag("gateway-delete-${job.id}")) {Text(label(Words.GatewayCronDelete))}
                    if(collision) Text(label(Words.GatewaySelectorCollision))
                    if(!job.addressable) Text(label(Words.HostCronUnsupportedId))
                    if(job.enabled==null || job.enabled==false && job.state!="paused") Text(label(Words.GatewayControlStateUnknown))
                }}
            }
            s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}}
        }
    },confirmButton={TextButton(close,Modifier.testTag("gateway-cron-close")) {Text(label(Words.Close))}})
    actionReview?.let {frozen ->GatewayCronActionReview(frozen,!s.busy,{actionReview=null}) {vm.performGatewayCron(frozen);actionReview=null}}
    review?.let {frozen ->AlertDialog(onDismissRequest={review=null},title={Text(label(Words.GatewayLoad))},text={
        LazyColumn(Modifier.heightIn(max=440.dp).testTag("gateway-cron-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor);Text(label(Words.GatewayConnectionEffect))}
        }
    },confirmButton={TextButton({vm.loadGatewayCron(frozen);review=null},enabled=!s.busy,modifier=Modifier.testTag("gateway-cron-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({review=null}) {Text(label(Words.Cancel))}})}
}


fun gatewayCronWord(action:GatewayCronAction)=when(action) {GatewayCronAction.Pause->Words.GatewayCronPause;GatewayCronAction.Resume->Words.GatewayCronResume;GatewayCronAction.Delete->Words.GatewayCronDelete}
private fun gatewayFieldWord(key:String)=when(key) {
    "model"->Words.HostCronModel;"provider"->Words.HostCronProvider;"deliver"->Words.HostCronDelivery;"script"->Words.HostCronScript;"workdir"->Words.HostCronWorkdir;"paused_reason"->Words.HostCronPauseReason
    "skills"->Words.HostCronSkills;"context_from"->Words.HostCronContext;"enabled_toolsets"->Words.HostCronToolsets;"repeat"->Words.HostCronRepeat;"no_agent"->Words.HostCronNoAgent
    "reasoning_effort"->Words.GatewayReasoning;"monitor_script"->Words.GatewayMonitorScript;"interpreter"->Words.GatewayInterpreter;"continuity"->Words.HostCronSelfContext;"attach_to_session"->Words.GatewayAttach;"pinned"->Words.GatewayPinned
    else->Words.Unknown
}
@Composable fun GatewayCronActionReview(review:ReviewedGatewayCron,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    val job=review.request.expected
    var query by remember(review) {mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(label(gatewayCronWord(review.request.action)))},text={
        LazyColumn(Modifier.heightIn(max=480.dp).testTag("gateway-action-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor);Text(job.name,style=MaterialTheme.typography.titleMedium);Text(job.id);Text(job.schedule)}
            if(job.promptPreview.isNotBlank()) item {Text(label(Words.GatewayPromptPreview));Text(job.promptPreview)}
            items(job.fields().entries.toList(),key={it.key}) {field ->Column {Text(label(gatewayFieldWord(field.key)));Text(if(field.key=="enabled_toolsets" && field.value.isBlank()) label(Words.HostCronNoTools) else field.value)}}
            if(!job.reportsToolsets) item {Text(label(Words.GatewayToolsetsUnreported))}
            for(key in listOf("base_url","monitor_url")) job.reviewUrl(key,query)?.let {url ->item {
                Text(label(if(key=="base_url") Words.HostCronProviderUrl else Words.GatewayMonitorUrl));Text(url.ifBlank {label(Words.Unknown)})
                if(job.reviewUrl(key,true)!=job.reviewUrl(key,false)) TextButton({query=!query}) {Text(label(if(query) Words.McpHideQuery else Words.McpShowQuery))}
            }}
            item {Text(label(when(review.request.action) {GatewayCronAction.Pause->Words.GatewayPauseEffect;GatewayCronAction.Resume->Words.GatewayResumeEffect;GatewayCronAction.Delete->Words.GatewayDeleteEffect}))}
            item {Text(label(Words.GatewayMutationConnection));Text(label(Words.GatewayMutationRace))}
        }
    },confirmButton={TextButton(confirm,enabled=enabled,modifier=Modifier.testTag("gateway-action-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close) {Text(label(Words.Cancel))}})
}
