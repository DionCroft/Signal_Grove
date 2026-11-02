package org.hermesmobile.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

fun mcpOAuthStatusWord(status:String)=when(status) {"starting"->Words.McpOAuthStarting;"authorization_required"->Words.McpOAuthRequired;"approved"->Words.McpOAuthApproved;"error"->Words.McpOAuthError;"expired"->Words.McpOAuthExpired;else->Words.Unknown}

@Composable fun McpOAuthStartReview(review:ReviewedMcpOAuth,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    AlertDialog(onDismissRequest=close,title={Text(label(Words.McpOAuthStart))},text={
        LazyColumn(Modifier.heightIn(max=460.dp).testTag("mcp-oauth-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor);Text(review.server.name,style=MaterialTheme.typography.titleMedium);Text(label(Words.McpOAuthEffect))}
        }
    },confirmButton={TextButton(confirm,enabled=enabled,modifier=Modifier.testTag("mcp-oauth-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close) {Text(label(Words.Cancel))}})
}

@Composable fun McpOAuthReceipt(receipt:ManagementOperation,vm:ManagementViewModel?,s:ManagementState?) {
    val result=remember(receipt.resultSummary) {runCatching {Json.parseToJsonElement(receipt.resultSummary).jsonObject}.getOrNull()}
    val snapshot=s?.oauth?.get(receipt.key)
    val context=LocalContext.current.applicationContext
    var cancel by remember(receipt.key) {mutableStateOf<ManagementTarget?>(null)}
    var open by remember(receipt.key,snapshot) {mutableStateOf(false)}
    Text(label(Words.McpOAuthReported));Text(label(mcpOAuthStatusWord(snapshot?.status ?: result?.text("status").orEmpty())))
    if(receipt.action=="OAuthCancel") {Text(label(Words.McpOAuthCancelled));return}
    Text(label(Words.McpOAuthLifecycle),style=MaterialTheme.typography.bodySmall)
    if(snapshot?.toolCount!=null) {Text(label(Words.McpToolCount)+" "+snapshot.toolCount);snapshot.tools.forEach {Text(it)}}
    if(vm!=null && s!=null) {
        val target=vm.reviewTarget();val valid=!s.busy && target!=null && target.resourceScope==receipt.resourceScope && target.actor==receipt.actor && receipt.state=="acknowledged"
        TextButton({vm.readOAuth(receipt.key)},enabled=valid,modifier=Modifier.testTag("mcp-oauth-read-${receipt.key}")) {Text(label(Words.McpOAuthRead))}
        if(snapshot?.authorizationUrl!=null && snapshot.status=="authorization_required") TextButton({open=true},enabled=valid,modifier=Modifier.testTag("mcp-oauth-open-${receipt.key}")) {Text(label(Words.McpOAuthOpen))}
        val pending by vm.receipts.collectAsState()
        val cancelled=pending.any {it.action=="OAuthCancel" && it.state=="acknowledged" && runCatching {Json.parseToJsonElement(it.requestSummary).jsonObject.text("start_key")==receipt.key}.getOrDefault(false)}
        if(cancelled) Text(label(Words.McpOAuthCancelled))
        TextButton({cancel=target},enabled=valid && !cancelled && pending.none {it.state in setOf("sending","unknown")},modifier=Modifier.testTag("mcp-oauth-cancel-${receipt.key}")) {Text(label(Words.McpOAuthCancel))}
    }
    if(open && snapshot?.authorizationUrl!=null) AlertDialog(onDismissRequest={open=false},title={Text(label(Words.McpOAuthOpen))},text={
        LazyColumn(Modifier.heightIn(max=440.dp)) {item {Text(receipt.url);Text(receipt.profile);Text(receipt.target);Text(snapshot.destination().orEmpty());Text(label(Words.McpOAuthBrowser))}}
    },confirmButton={TextButton({
        val target=vm?.reviewTarget()
        if(target?.resourceScope==receipt.resourceScope && target.actor==receipt.actor && vm.state.value.oauth[receipt.key]===snapshot) context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(snapshot.authorizationUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        open=false
    },enabled=s?.busy==false && vm?.reviewTarget()?.let {it.resourceScope==receipt.resourceScope && it.actor==receipt.actor}==true && s.oauth[receipt.key]===snapshot,modifier=Modifier.testTag("mcp-oauth-browser-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({open=false}) {Text(label(Words.Cancel))}})
    cancel?.let {target ->AlertDialog(onDismissRequest={cancel=null},title={Text(label(Words.McpOAuthCancel))},text={
        LazyColumn(Modifier.heightIn(max=440.dp)) {item {Text(receipt.url);Text(receipt.profile);Text(receipt.actor);Text(receipt.target);Text(label(Words.McpOAuthCancelEffect))}}
    },confirmButton={TextButton({vm!!.cancelOAuth(receipt.key,target);cancel=null},enabled=s?.busy==false,modifier=Modifier.testTag("mcp-oauth-cancel-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({cancel=null}) {Text(label(Words.Cancel))}})}
}
