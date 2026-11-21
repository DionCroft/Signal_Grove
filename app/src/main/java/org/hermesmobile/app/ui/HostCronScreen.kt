package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.hermesmobile.app.data.CronExecutionAttempt
import org.hermesmobile.app.data.CronExecutionHistory
import org.hermesmobile.app.data.CronTimingProtocol
import org.hermesmobile.app.data.CronRunOnceProtocol
import org.hermesmobile.app.data.ManagedCronJob
import org.hermesmobile.app.data.ManagedCronRuns
import org.hermesmobile.app.data.ManagementReceiptVisibility

private fun hostCronField(key:String)=when(key) {
    "prompt"->Words.Prompt;"model"->Words.HostCronModel;"provider"->Words.HostCronProvider;"script"->Words.HostCronScript
    "workdir"->Words.HostCronWorkdir;"timezone"->Words.HostCronTimezone;"paused_reason"->Words.HostCronPauseReason
    "deliver"->Words.HostCronDelivery;"no_agent"->Words.HostCronNoAgent;"catch_up"->Words.HostCronCatchUp
    "grace_minutes"->Words.HostCronGrace;"skills"->Words.HostCronSkills;"context_from"->Words.HostCronContext
    "enabled_toolsets"->Words.HostCronToolsets;"repeat"->Words.HostCronRepeat;"repeat_completed"->Words.HostCronRepeatCompleted
    else->Words.Unknown
}

@Composable fun HostCronDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.closeHostCronRunOnce()
        vm.closeHostCronTiming()
        vm.closeHostCronOutput()
    }
    DisposableEffect(vm) {
        onDispose {
            vm.closeHostCronRunOnce()
            vm.closeHostCronTiming()
            vm.closeHostCronOutput()
        }
    }
    var creating by remember(s.apiScope,s.url,s.profile) {mutableStateOf(false)}
    val receiptRows by vm.receipts.collectAsState();val receipts=vm.visibleReceipts(s,receiptRows)
    val pending=receipts.any {it.resourceScope==vm.resourceScope(s) && it.state in setOf("sending","unknown")}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostCron))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("host-cron-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(s.url);Text(s.profile);Text(label(Words.HostCronBoundary),style=MaterialTheme.typography.bodySmall);TextButton(vm::loadHostCron,enabled=!s.busy,modifier=Modifier.testTag("host-cron-refresh")) {Text(label(Words.Refresh))}}
            item {TextButton({creating=true;vm.loadCronCreationSetup()},enabled=!s.busy && !pending && s.cron.loaded,modifier=Modifier.testTag("cron-create-open")) {Text(label(Words.HostCronCreate))}}
            if(pending) item {Text(label(Words.ManagementOutcomePending))}
            items(receipts.filter {
                (it.action == CronTimingProtocol.Action && it.state == "unknown") ||
                    it.action == CronRunOnceProtocol.Action ||
                    it.action == ManagementReceiptVisibility.SharedCronRunOncePending
            }, key = { it.key }) { ManagementReceiptCard(it, vm, s) }
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
    s.cron.detail?.let { job ->
        val home = vm.hostCronLedgerHome(s)
        val ledger = s.cron.executionHistory?.takeIf {
            it.owner.apiScope == s.apiScope && it.owner.profile == s.profile && it.homeId == home && it.jobId == job.id
        }
        val output = s.cron.output?.takeIf { it.history === ledger }
        val editor = s.cron.timingEditor?.takeIf { it.job === job && it.baseline.homeId == home }
        val runOnce = s.cron.runOnce?.takeIf { it.job == job.id && it.home == home && it.owner.apiScope == s.apiScope && it.owner.profile == s.profile }
        HostCronDetail(job, s.cron.runs?.takeIf { it.profile == s.profile && it.jobId == job.id },
            ledger, home != null, s.busy, pending, s.error.takeIf { output == null && editor == null },
            { vm.loadHostCronRuns(job) }, { vm.loadHostCronExecutionHistory(job) },
            { vm.openHostCronTiming(job) }, { vm.openHostCronRunOnce(job) },
            vm::loadHostCronOutput, vm::closeHostCronDetail)
        if (runOnce != null) HostCronRunOnceDialog(vm, runOnce, s.busy, pending)
        if (output != null) HostCronOutputDialog(output, s.busy, s.error, vm::closeHostCronOutput)
        if (editor != null) HostCronTimingDialog(vm, editor, s.busy, pending, s.error)
    }
}

@Composable private fun HostCronDetail(
    job: ManagedCronJob, history: ManagedCronRuns?, ledger: CronExecutionHistory?,
    canReadLedger: Boolean, busy: Boolean, pending: Boolean, error: Words?,
    loadRuns: () -> Unit, loadLedger: () -> Unit, editTiming: () -> Unit, reviewRunOnce: () -> Unit,
    loadOutput: (CronExecutionHistory, CronExecutionAttempt) -> Unit, close: () -> Unit
) {
    AlertDialog(onDismissRequest=close,title={Text(job.name)},text={
        LazyColumn(Modifier.heightIn(max=500.dp).testTag("host-cron-detail"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(job.id);Text(label(Words.Schedule));Text(job.schedule);Text(label(Words.HostCronScheduleState));Text(job.state)}
            item {
                val paused = job.enabled == false && job.state == "paused"
                TextButton(editTiming, enabled = paused && canReadLedger && !busy && !pending,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("host-cron-timing-open")) {
                    Text(label(Words.HostCronTimingEdit))
                }
                TextButton(reviewRunOnce, enabled = job.addressable && canReadLedger && !busy && !pending,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("host-cron-run-once-open")) {
                    Text(label(Words.HostCronRunOnceReview))
                }
                when {
                    !paused -> Text(label(Words.HostCronTimingNeedsPause))
                    pending -> Text(label(Words.ManagementOutcomePending))
                    !canReadLedger -> Text(label(Words.HostCronLedgerProfile))
                }
                error?.let { Text(label(it), color = MaterialTheme.colorScheme.error) }
            }
            if(job.createdAt.isNotBlank()) item {Text(label(Words.HostCronCreatedAt));Text(job.createdAt)}
            if(job.nextRun.isNotBlank()) item {Text(label(Words.NextRun));Text(job.nextRun)}
            if(job.lastRun.isNotBlank()) item {Text(label(Words.LastRun));Text(job.lastRun)}
            if(job.lastStatus.isNotBlank()) item {Text(label(Words.HostCronLastStatus));Text(job.lastStatus)}
            if(job.hasRunError) item {Text(label(Words.HostCronRunError))}
            item {
                TextButton(loadLedger, enabled = !busy && canReadLedger,
                    modifier = Modifier.fillMaxWidth().testTag("host-cron-ledger-load")) {
                    Text(label(Words.HostCronLedger), Modifier.fillMaxWidth())
                }
                if (!canReadLedger) Text(label(Words.HostCronLedgerProfile))
            }
            hostCronRuns(history,busy,null,loadRuns)
            hostCronExecutionHistory(ledger, busy, loadOutput)
            items(job.fields().entries.toList(),key={it.key}) {field ->Column {Text(label(hostCronField(field.key)),style=MaterialTheme.typography.titleSmall);Text(if(field.key=="enabled_toolsets" && job.noToolsAllowed) label(Words.HostCronNoTools) else if(field.key=="repeat" && field.value=="__unlimited__") label(Words.HostCronUnlimited) else field.value.ifBlank {label(Words.Unknown)})}}
        }
    },confirmButton={TextButton(close,Modifier.testTag("host-cron-detail-close")) {Text(label(Words.Close))}})
}
