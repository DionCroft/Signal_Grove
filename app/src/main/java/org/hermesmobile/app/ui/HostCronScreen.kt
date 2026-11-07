package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.ManagedCronJob

private fun hostCronField(key:String)=when(key) {
    "prompt"->Words.Prompt;"model"->Words.HostCronModel;"provider"->Words.HostCronProvider;"script"->Words.HostCronScript
    "workdir"->Words.HostCronWorkdir;"timezone"->Words.HostCronTimezone;"paused_reason"->Words.HostCronPauseReason
    "deliver"->Words.HostCronDelivery;"no_agent"->Words.HostCronNoAgent;"catch_up"->Words.HostCronCatchUp
    "grace_minutes"->Words.HostCronGrace;"skills"->Words.HostCronSkills;"context_from"->Words.HostCronContext
    "enabled_toolsets"->Words.HostCronToolsets;"repeat"->Words.HostCronRepeat;"repeat_completed"->Words.HostCronRepeatCompleted
    else->Words.Unknown
}

@Composable fun HostCronDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    var creating by remember(s.apiScope,s.url,s.profile) {mutableStateOf(false)}
    val receiptRows by vm.receipts.collectAsState();val receipts=vm.visibleReceipts(s,receiptRows)
    val pending=receipts.any {it.resourceScope==vm.resourceScope(s) && it.state in setOf("sending","unknown")}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostCron))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("host-cron-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(s.url);Text(s.profile);Text(label(Words.HostCronBoundary),style=MaterialTheme.typography.bodySmall);TextButton(vm::loadHostCron,enabled=!s.busy,modifier=Modifier.testTag("host-cron-refresh")) {Text(label(Words.Refresh))}}
            item {TextButton({creating=true;vm.loadCronCreationSetup()},enabled=!s.busy && !pending && s.cron.loaded,modifier=Modifier.testTag("cron-create-open")) {Text(label(Words.HostCronCreate))}}
            if(pending) item {Text(label(Words.ManagementOutcomePending))}
            s.notice?.let {word->item {Text(label(word))}}
            if(s.busy) item {QuietProgress()}
            if(s.cron.loaded && s.cron.jobs.isEmpty()) item {Text(label(Words.EmptyInventory))}
            items(s.cron.jobs,key={it.id}) {job ->Column {
                Text(job.name,style=MaterialTheme.typography.titleMedium);Text(job.schedule.ifBlank {label(Words.Unknown)})
                Text(label(Words.HostCronScheduleState)+": "+job.state.ifBlank {label(Words.Unknown)})
                Text(label(when(job.enabled) {true->Words.Enabled;false->Words.Disabled;null->Words.Unknown}))
                TextButton({vm.loadHostCronDetail(job)},enabled=!s.busy && job.addressable,modifier=Modifier.testTag("host-cron-detail-${job.id}")) {Text(label(Words.JobDetails))}
                if(!job.addressable) Text(label(Words.HostCronUnsupportedId),style=MaterialTheme.typography.bodySmall)
            }}
            s.error?.let {word->item {Text(label(word),color=MaterialTheme.colorScheme.error)}}
        }
    },confirmButton={TextButton(close,Modifier.testTag("host-cron-close")) {Text(label(Words.Close))}})
    if(creating && s.cron.contract!=null && !s.busy) CronCreationForm(vm,s) {creating=false}
    s.cron.detail?.let {job ->HostCronDetail(job,s.cron.runs?.takeIf {it.profile==s.profile && it.jobId==job.id},s.busy,s.error,{vm.loadHostCronRuns(job)},vm::closeHostCronDetail)}
}

@Composable private fun HostCronDetail(job:ManagedCronJob,history:org.hermesmobile.app.data.ManagedCronRuns?,busy:Boolean,error:Words?,loadRuns:()->Unit,close:()->Unit) {
    AlertDialog(onDismissRequest=close,title={Text(job.name)},text={
        LazyColumn(Modifier.heightIn(max=500.dp).testTag("host-cron-detail"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(job.id);Text(label(Words.Schedule));Text(job.schedule);Text(label(Words.HostCronScheduleState));Text(job.state)}
            if(job.createdAt.isNotBlank()) item {Text(label(Words.HostCronCreatedAt));Text(job.createdAt)}
            if(job.nextRun.isNotBlank()) item {Text(label(Words.NextRun));Text(job.nextRun)}
            if(job.lastRun.isNotBlank()) item {Text(label(Words.LastRun));Text(job.lastRun)}
            if(job.lastStatus.isNotBlank()) item {Text(label(Words.HostCronLastStatus));Text(job.lastStatus)}
            if(job.hasRunError) item {Text(label(Words.HostCronRunError))}
            hostCronRuns(history,busy,error,loadRuns)
            items(job.fields().entries.toList(),key={it.key}) {field ->Column {Text(label(hostCronField(field.key)),style=MaterialTheme.typography.titleSmall);Text(if(field.key=="enabled_toolsets" && job.noToolsAllowed) label(Words.HostCronNoTools) else if(field.key=="repeat" && field.value=="__unlimited__") label(Words.HostCronUnlimited) else field.value.ifBlank {label(Words.Unknown)})}}
        }
    },confirmButton={TextButton(close,Modifier.testTag("host-cron-detail-close")) {Text(label(Words.Close))}})
}
