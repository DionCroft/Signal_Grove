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

fun mcpActionWord(action:String)=when(action) {"Create"->Words.McpAdd;"Toggle"->Words.McpToggle;"Delete"->Words.McpRemove;"Probe"->Words.McpProbe;else->Words.Management}
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
    var result by remember(receipt.key) {mutableStateOf(false)}
    var review by remember(receipt.key) {mutableStateOf<ManagementTarget?>(null)}
    Column(Modifier.fillMaxWidth().testTag("management-receipt-${receipt.key}"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label(mcpActionWord(receipt.action))+" · "+receipt.target,style=MaterialTheme.typography.titleSmall)
        Text(label(managementStateWord(receipt.state)))
        Text(receipt.url+"\n"+receipt.profile+"\n"+receipt.actor,style=MaterialTheme.typography.bodySmall)
        if(receipt.resultSummary.isNotBlank()) TextButton({result=true},Modifier.testTag("management-result-${receipt.key}")) {Text(label(Words.ManagementResult))}
        if(vm!=null && s!=null) {
            val enabled=!s.busy && s.identity!=null && vm.reviewTarget()!=null
            TextButton({vm.observeReceipt(receipt.key)},enabled=enabled,modifier=Modifier.testTag("management-observe-${receipt.key}")) {Text(label(Words.ManagementReadCurrent))}
            if(receipt.state=="unknown") TextButton({review=vm.reviewTarget()},enabled=enabled,modifier=Modifier.testTag("management-review-${receipt.key}")) {Text(label(Words.ManagementReview))}
        }
    }
    review?.let {target ->AlertDialog(onDismissRequest={review=null},title={Text(label(Words.ManagementReview))},text={
        LazyColumn(Modifier.heightIn(max=440.dp)) {item {Text(receipt.url);Text(receipt.profile);Text(receipt.target);Text(label(Words.ManagementReviewEffect))}}
    },confirmButton={TextButton({vm!!.reviewReceipt(receipt.key,target);review=null},enabled=s?.busy!=true,modifier=Modifier.testTag("management-review-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({review=null}) {Text(label(Words.Cancel))}})}
    if(result) AlertDialog(onDismissRequest={result=false},title={Text(receipt.target)},text={
        val parsed=remember(receipt.resultSummary) {runCatching {Json.parseToJsonElement(receipt.resultSummary).jsonObject}.getOrDefault(JsonObject(emptyMap()))}
        LazyColumn(Modifier.heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item {Text(label(managementStateWord(receipt.state)))}
            if(receipt.action=="Probe") {
                item {Text(label(if(parsed["ok"]?.jsonPrimitive?.booleanOrNull==true) Words.McpProbeReady else Words.McpProbeFailed));Text(label(Words.McpToolCount)+" "+parsed.text("tool_count"))}
                items((parsed["tools"] as? JsonArray).orEmpty()) {Text((it as? JsonPrimitive)?.contentOrNull.orEmpty())}
            } else if(receipt.action=="Create") item {Text(label(Words.McpCreated));Text(parsed.text("transport")+" · "+parsed.text("auth"))}
            else if(parsed.containsKey("enabled")) item {Text(label(if(parsed["enabled"]?.jsonPrimitive?.booleanOrNull==true) Words.Enabled else Words.Disabled))}
        }
    },confirmButton={TextButton({result=false},Modifier.testTag("management-result-close")) {Text(label(Words.Close))}})
}
