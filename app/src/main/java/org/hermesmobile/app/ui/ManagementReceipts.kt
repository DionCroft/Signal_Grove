package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

fun mcpActionWord(action:String)=when(action) {"PluginRemove"->Words.PluginRemove;"PluginEnable"->Words.PluginEnable;"PluginDisable"->Words.PluginDisable;"PluginInstall"->Words.PluginInstall;"HubRemove"->Words.SkillHubRemove;"HubUpdateAll"->Words.SkillHubUpdateAll;"HubInstall"->Words.SkillHubInstall;"GatewayCronPause"->Words.GatewayCronPause;"GatewayCronResume"->Words.GatewayCronResume;"GatewayCronDelete"->Words.GatewayCronDelete;"SkillToggle"->Words.SkillToggle;"SkillLock"->Words.SkillLockControl;"CronCreate"->Words.HostCronCreate;"OAuthStart"->Words.McpOAuthStart;"OAuthCancel"->Words.McpOAuthCancel;"CatalogInstall"->Words.McpCatalogInstall;"Create"->Words.McpAdd;"Toggle"->Words.McpToggle;"Delete"->Words.McpRemove;"Probe"->Words.McpProbe;else->Words.Management}
fun managementStateWord(state:String)=when(state) {"sending"->Words.ManagementSending;"unknown"->Words.ManagementUncertain;"acknowledged"->Words.ManagementAcknowledged;"confirmed"->Words.ManagementVerified;"reviewed"->Words.ManagementReviewed;else->Words.Management}

@Composable fun McpActionReview(review:ReviewedMcpAction,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    AlertDialog(onDismissRequest=close,title={Text(label(mcpActionWord(review.request.action.name)),style=MaterialTheme.typography.titleMedium)},text={
        LazyColumn(Modifier.heightIn(max=440.dp).testTag("mcp-action-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor);Text(review.request.name,style=MaterialTheme.typography.titleMedium)}
            if(review.request.action==McpAction.Toggle) item {Text(label(if(review.request.enabled==true) Words.McpEnable else Words.McpDisable))}
            item {Text(label(if(review.request.action==McpAction.Probe) Words.McpProbeEffect else Words.McpChangeEffect))}
        }
    },confirmButton={TextButton(confirm,enabled=enabled,modifier=Modifier.testTag("mcp-action-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close) {Text(label(Words.Cancel))}})
}

@Composable fun ManagementReceiptCard(receipt:ManagementOperation,vm:ManagementViewModel?=null,s:ManagementState?=null) {
    var plugins by remember(receipt.key) {mutableStateOf(false)}
    var gateway by remember(receipt.key) {mutableStateOf(false)}
    var skills by remember(receipt.key) {mutableStateOf(false)}
    var cron by remember(receipt.key) {mutableStateOf(false)}
    var result by remember(receipt.key) {mutableStateOf(false)}
    var review by remember(receipt.key) {mutableStateOf<ManagementTarget?>(null)}
    Column(Modifier.fillMaxWidth().testTag("management-receipt-${receipt.key}"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label(mcpActionWord(receipt.action))+" · "+receipt.target,style=MaterialTheme.typography.titleSmall)
        Text(label(if(receipt.state=="acknowledged" && receipt.action=="PluginInstall") {if(runCatching {Json.parseToJsonElement(receipt.resultSummary).jsonObject["source_changed"]==JsonPrimitive(true)}.getOrDefault(false)) Words.PluginInstallSourceChanged else Words.PluginInstallReported} else if(receipt.state=="acknowledged" && receipt.action in setOf("HubRemove","HubUpdateAll")) Words.SkillHubMaintenanceAccepted else if(receipt.action=="HubInstall" && receipt.state=="acknowledged") Words.SkillHubInstallAccepted else managementStateWord(receipt.state)))
        Text(receipt.url+"\n"+receipt.profile+"\n"+receipt.actor,style=MaterialTheme.typography.bodySmall)
        Text(label(Words.ManagementRequestedAt)+" "+java.time.Instant.ofEpochMilli(receipt.createdAt).toString(),style=MaterialTheme.typography.bodySmall)
        if(receipt.action in setOf("OAuthStart","OAuthCancel") && receipt.resultSummary.isNotBlank()) McpOAuthReceipt(receipt,vm,s)
        else if(receipt.resultSummary.isNotBlank()) TextButton({result=true},Modifier.testTag("management-result-${receipt.key}")) {Text(label(Words.ManagementResult))}
        if(vm!=null && s!=null) {
            val enabled=!s.busy && s.identity!=null && vm.reviewTarget()!=null
            TextButton({if(receipt.action in setOf("PluginInstall","PluginEnable","PluginDisable","PluginRemove")) {plugins=true} else if(receipt.action in setOf("GatewayCronPause","GatewayCronResume","GatewayCronDelete")) {gateway=true} else if(receipt.action in setOf("HubInstall","HubRemove","HubUpdateAll")) {skills=true;vm.observeHubReceipt(receipt.key)} else if(receipt.action in setOf("SkillToggle","SkillLock")) {skills=true;vm.observeSkillReceipt(receipt.key)} else if(receipt.action=="CronCreate") {cron=true;vm.observeCronReceipt(receipt.key)} else vm.observeReceipt(receipt.key)},enabled=enabled,modifier=Modifier.testTag("management-observe-${receipt.key}")) {Text(label(Words.ManagementReadCurrent))}
            if(receipt.state=="unknown") TextButton({review=vm.reviewTarget()},enabled=enabled,modifier=Modifier.testTag("management-review-${receipt.key}")) {Text(label(Words.ManagementReview))}
        }
    }
    if(plugins && vm!=null && s!=null) PluginInventoryDialog(vm,s) {vm.cancel();plugins=false}
    if(gateway && vm!=null && s!=null) GatewayAutomationDialog(vm,s) {vm.cancel();gateway=false}
    if(skills && vm!=null && s!=null) HostSkillsDialog(vm,s) {vm.cancel();skills=false}
    if(cron && vm!=null && s!=null) HostCronDialog(vm,s) {vm.cancel();cron=false}
    review?.let {target ->AlertDialog(onDismissRequest={review=null},title={Text(label(Words.ManagementReview))},text={
        LazyColumn(Modifier.heightIn(max=440.dp)) {item {Text(receipt.url);Text(receipt.profile);Text(receipt.target);Text(label(Words.ManagementReviewEffect))}}
    },confirmButton={TextButton({vm!!.reviewReceipt(receipt.key,target);review=null},enabled=s?.busy!=true,modifier=Modifier.testTag("management-review-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({review=null}) {Text(label(Words.Cancel))}})}
    if(result) AlertDialog(onDismissRequest={result=false},title={Text(receipt.target)},text={
        val parsed=remember(receipt.resultSummary) {runCatching {Json.parseToJsonElement(receipt.resultSummary).jsonObject}.getOrDefault(JsonObject(emptyMap()))}
        LazyColumn(Modifier.heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item {Text(label(if(receipt.state=="acknowledged" && receipt.action in setOf("HubRemove","HubUpdateAll")) Words.SkillHubMaintenanceAccepted else if(receipt.action=="HubInstall" && receipt.state=="acknowledged") Words.SkillHubInstallAccepted else managementStateWord(receipt.state)))}
            if(receipt.action=="PluginRemove") item {PluginRemovalReceipt(parsed)}
            else if(receipt.action in setOf("PluginEnable","PluginDisable")) item {Text(label(Words.PluginToggleReported));Text(label(if(parsed["requested_enable"]==JsonPrimitive(true)) Words.PluginEnable else Words.PluginDisable));Text(label(Words.PluginToggleObservation));Text(label(if(parsed["requested_enable"]==JsonPrimitive(true)) Words.PluginEnableEffect else Words.PluginDisableEffect))}
            else if(receipt.action=="PluginInstall") item {PluginInstallReceiptResult(parsed)}
            else if(receipt.action=="Probe") {
                item {Text(label(if(parsed["ok"]?.jsonPrimitive?.booleanOrNull==true) Words.McpProbeReady else Words.McpProbeFailed));Text(label(Words.McpToolCount)+" "+parsed.text("tool_count"))}
                items((parsed["tools"] as? JsonArray).orEmpty()) {Text((it as? JsonPrimitive)?.contentOrNull.orEmpty())}
            } else if(receipt.action in setOf("GatewayCronPause","GatewayCronResume","GatewayCronDelete")) item {Text(label(if(receipt.state=="confirmed") Words.GatewayCronChanged else Words.GatewayCronAccepted));Text(parsed.text("job_id"));Text(label(Words.GatewayReceiptEffect))}
            else if(receipt.action=="CronCreate") item {Text(label(Words.HostCronCreatedPaused));Text(parsed.text("job_id"))}
            else if(receipt.action in setOf("HubRemove","HubUpdateAll")) item {Text(label(Words.SkillHubMaintenanceAccepted))}
            else if(receipt.action=="HubInstall") item {Text(label(Words.SkillHubInstallAccepted))}
            else if(receipt.action=="CatalogInstall") item {Text(label(Words.McpCatalogAccepted));if(parsed["background"]?.jsonPrimitive?.booleanOrNull==true) Text(label(Words.McpCatalogBackground))}
            else if(receipt.action=="Create") item {Text(label(Words.McpCreated));Text(parsed.text("transport")+" · "+parsed.text("auth"))}
            else if(receipt.action=="SkillLock") item {Text(label(if(parsed["locked"]?.jsonPrimitive?.booleanOrNull==true) Words.SkillLocked else Words.SkillUnlocked))}
            else if(parsed.containsKey("enabled")) item {Text(label(if(parsed["enabled"]?.jsonPrimitive?.booleanOrNull==true) Words.Enabled else Words.Disabled))}
        }
    },confirmButton={TextButton({result=false},Modifier.testTag("management-result-close")) {Text(label(Words.Close))}})
}
