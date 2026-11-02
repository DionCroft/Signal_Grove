package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.GatewayCronInventory

@Composable fun GatewayAutomationDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    var review by remember(s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedGatewayRead?>(null)}
    val supported=GatewayCronInventory.supportedProfile(s.profile)
    AlertDialog(onDismissRequest=close,title={Text(label(Words.GatewayAutomation))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("gateway-cron-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(s.url);Text(s.profile);Text(label(Words.GatewayConnectionEffect));TextButton({review=vm.prepareGatewayRead()},enabled=!s.busy && supported,modifier=Modifier.testTag("gateway-cron-load")) {Text(label(Words.GatewayLoad))}}
            if(!supported) item {Text(label(Words.GatewayScopeUnsupported))}
            if(s.busy) item {QuietProgress()}
            s.gateway.inventory?.let {inventory ->
                item {Text(label(when(inventory.gatewayRunning) {true->Words.GatewaySchedulerReported;false->Words.GatewaySchedulerUnavailable;null->Words.GatewaySchedulerUnknown}))}
                if(inventory.jobs.isEmpty()) item {Text(label(Words.EmptyInventory))}
                items(inventory.jobs,key={it.id}) {job ->Column {
                    Text(job.name,style=MaterialTheme.typography.titleMedium);Text(job.id);Text(job.schedule);Text(job.state)
                    Text(label(when(job.enabled) {true->Words.Enabled;false->Words.Disabled;null->Words.Unknown}))
                    if(job.nextRun.isNotBlank()) Text(label(Words.NextRun)+"\n"+job.nextRun)
                    if(job.lastStatus.isNotBlank()) Text(label(Words.HostCronLastStatus)+"\n"+job.lastStatus)
                    if(job.promptPreview.isNotBlank()) {Text(label(Words.GatewayPromptPreview));Text(job.promptPreview)}
                }}
            }
            s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}}
        }
    },confirmButton={TextButton(close,Modifier.testTag("gateway-cron-close")) {Text(label(Words.Close))}})
    review?.let {frozen ->AlertDialog(onDismissRequest={review=null},title={Text(label(Words.GatewayLoad))},text={
        LazyColumn(Modifier.heightIn(max=440.dp).testTag("gateway-cron-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor);Text(label(Words.GatewayConnectionEffect))}
        }
    },confirmButton={TextButton({vm.loadGatewayCron(frozen);review=null},enabled=!s.busy,modifier=Modifier.testTag("gateway-cron-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({review=null}) {Text(label(Words.Cancel))}})}
}
