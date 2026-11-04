package org.hermesmobile.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

@Composable fun ProviderAccountsDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    var read by remember(s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedProviderOAuthRead?>(null)}
    var review by remember(s.providerAccounts) {mutableStateOf<ReviewedProviderOAuth?>(null)}
    val receiptRows by vm.receipts.collectAsStateWithLifecycle();val receipts=vm.visibleReceipts(s,receiptRows);val pending=receipts.any {it.state in setOf("sending","unknown")}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.ProviderAccounts))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("provider-accounts-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(s.url);Text(s.profile);Text(label(Words.ProviderOAuthPrivacy));TextButton({read=vm.prepareProviderOAuthRead()},enabled=!s.busy && s.identity!=null && GatewayCronInventory.supportedProfile(s.profile),modifier=Modifier.testTag("provider-accounts-read")) {Text(label(Words.ProviderAccountsRead))};if(!GatewayCronInventory.supportedProfile(s.profile)) Text(label(Words.PluginProfileUnsupported))}
            if(s.busy) item {QuietProgress();TextButton(vm::cancel) {Text(label(Words.Cancel))}}
            s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}}
            s.notice?.let {item {Text(label(it))}}
            if(pending) item {Text(label(Words.ManagementOutcomePending))}
            s.providerAccounts?.let {inventory->
                if(inventory.rows.isEmpty()) item {Text(label(Words.EmptyInventory))}
                items(inventory.rows,key={it.id}) {row->Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(row.name,style=MaterialTheme.typography.titleMedium);Text(row.id);Text(label(if(row.loggedIn) Words.ProviderOAuthConnected else Words.ProviderOAuthNotConnected))
                    TextButton({review=vm.prepareProviderOAuth(row,ProviderOAuthAction.Start)},enabled=!s.busy && !pending && row.canStart,modifier=Modifier.testTag("provider-start-${row.id}")) {Text(label(Words.ProviderOAuthStart))}
                    if(!row.canStart) Text(label(Words.ProviderOAuthExternal))
                    TextButton({review=vm.prepareProviderOAuth(row,ProviderOAuthAction.Disconnect)},enabled=!s.busy && !pending && row.disconnectable,modifier=Modifier.testTag("provider-disconnect-${row.id}")) {Text(label(Words.ProviderDisconnect))}
                    if(!row.disconnectable) Text(label(Words.ProviderOAuthExternalDisconnect))
                }}
            }
            items(receipts.filter {it.action in setOf("ProviderOAuthStart","ProviderOAuthCancel","ProviderDisconnect")},key={"receipt:"+it.key}) {receipt->ManagementReceiptCard(receipt,vm,s)}
        }
    },confirmButton={TextButton(close,Modifier.testTag("provider-accounts-close")) {Text(label(Words.Close))}})
    read?.let {frozen->AlertDialog(onDismissRequest={read=null},title={Text(label(Words.ProviderAccountsRead))},text={LazyColumn(Modifier.heightIn(max=460.dp)) {item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor);Text(label(Words.ProviderOAuthPrivacy))}}},confirmButton={TextButton({read=null;vm.loadProviderAccounts(frozen)},enabled=!s.busy,modifier=Modifier.testTag("provider-accounts-read-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({read=null},Modifier.testTag("provider-accounts-read-cancel")) {Text(label(Words.Cancel))}})}
    review?.let {frozen->ProviderOAuthWriteReview(frozen.target,frozen.request.provider.id,frozen.request.action,!s.busy,{review=null}) {review=null;vm.changeProviderOAuth(frozen)}}
}
@Composable private fun ProviderOAuthWriteReview(target:ManagementTarget,provider:String,action:ProviderOAuthAction,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    var approved by remember(target,provider,action) {mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(label(mcpActionWord(action.receiptAction)))},text={LazyColumn(Modifier.heightIn(max=460.dp).testTag("provider-oauth-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(target.url);Text(target.profile);Text(target.actor);Text(provider);Text(label(when(action) {ProviderOAuthAction.Start->Words.ProviderOAuthStartEffect;ProviderOAuthAction.Cancel->Words.ProviderOAuthCancelEffect;ProviderOAuthAction.Disconnect->Words.ProviderDisconnectEffect}))}
        item {Row {Checkbox(approved,{approved=it},modifier=Modifier.testTag("provider-oauth-approve"));Text(label(Words.ProviderOAuthApprove),Modifier.weight(1f))}}
    }},confirmButton={TextButton(confirm,enabled=enabled && approved,modifier=Modifier.testTag("provider-oauth-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.testTag("provider-oauth-review-cancel")) {Text(label(Words.Cancel))}})
}
@Composable fun ProviderOAuthReceipt(receipt:ManagementOperation,vm:ManagementViewModel?,s:ManagementState?) {
    val result=remember(receipt.resultSummary) {runCatching {Json.parseToJsonElement(receipt.resultSummary).jsonObject}.getOrNull()}
    if(receipt.action!="ProviderOAuthStart") {Text(label(if(receipt.action=="ProviderDisconnect") Words.ProviderDisconnectEffect else if(result?.get("session_not_found")==JsonPrimitive(true)) Words.ProviderOAuthUnavailable else Words.ProviderOAuthCancelEffect));return}
    Text(label(Words.ProviderOAuthAcknowledged));Text(label(Words.ProviderOAuthLifecycle),style=MaterialTheme.typography.bodySmall)
    val valid=vm!=null && s!=null && !s.busy && runCatching {ProviderOAuthBinding.parse(receipt,vm.reviewTarget() ?: error("No verified owner."))}.isSuccess
    TextButton({vm?.inspectProviderFlow(receipt.key)},enabled=valid,modifier=Modifier.testTag("provider-flow-${receipt.key}")) {Text(label(Words.ProviderOAuthInspect))}
}
/** Kept above lazy receipt rows, so a new cancellation receipt cannot dispose the inspector. */
@Composable fun ProviderOAuthInspector(vm:ManagementViewModel,s:ManagementState) {
    val key=s.providerFlowKey ?: return
    val receiptRows by vm.receipts.collectAsStateWithLifecycle();val receipts=vm.visibleReceipts(s,receiptRows)
    val receipt=receipts.singleOrNull {it.key==key} ?: return
    ProviderOAuthFlowDialog(receipt,vm,s,vm::closeProviderFlow)
}
private fun providerStatusWord(status:String)=when(status) {"pending"->Words.ProviderOAuthPending;"approved"->Words.McpOAuthApproved;"denied"->Words.ProviderOAuthDenied;"expired"->Words.McpOAuthExpired;"error"->Words.McpOAuthError;"cancelled"->Words.ProviderOAuthCancelled;else->Words.Unknown}
@Composable private fun ProviderOAuthFlowDialog(receipt:ManagementOperation,vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    val snapshot=s.providerFlows[receipt.key]
    var showCode by remember(snapshot) {mutableStateOf(false)}
    var browser by remember(snapshot) {mutableStateOf(false)}
    var cancel by remember(receipt.key) {mutableStateOf<ReviewedProviderOAuthCancel?>(null)}
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(snapshot) {while(true) {now=System.currentTimeMillis();kotlinx.coroutines.delay(1000)}}
    val receiptRows by vm.receipts.collectAsStateWithLifecycle();val receipts=vm.visibleReceipts(s,receiptRows)
    val settled=receipts.filter {it.action=="ProviderOAuthCancel" && it.state=="acknowledged" && runCatching {Json.parseToJsonElement(it.requestSummary).jsonObject.text("start_key")==receipt.key}.getOrDefault(false)}
    val cancelled=settled.any {runCatching {Json.parseToJsonElement(it.resultSummary).jsonObject["cancel_acknowledged"]==JsonPrimitive(true)}.getOrDefault(false)}
    val valid=!s.busy && runCatching {ProviderOAuthBinding.parse(receipt,vm.reviewTarget() ?: error("No verified owner."))}.isSuccess
    val code=snapshot?.deviceCode?.takeIf {snapshot.status=="pending" && it.expiresAt>now && settled.isEmpty()}
    val context=LocalContext.current.applicationContext
    AlertDialog(onDismissRequest=close,title={Text(label(Words.ProviderOAuthInspect))},text={LazyColumn(Modifier.heightIn(max=500.dp).testTag("provider-flow-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(receipt.url);Text(receipt.profile);Text(receipt.actor);Text(receipt.target);Text(label(Words.ProviderOAuthLifecycle));Text(label(providerStatusWord(snapshot?.status.orEmpty())))}
        s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}}
        if(s.busy) item {QuietProgress();TextButton(vm::cancel) {Text(label(Words.Cancel))}}
        item {TextButton({vm.readProviderFlow(receipt.key)},enabled=valid && settled.isEmpty(),modifier=Modifier.testTag("provider-flow-read")) {Text(label(Words.ProviderOAuthStatus))}}
        item {TextButton({showCode=!showCode},enabled=valid && code!=null,modifier=Modifier.testTag("provider-flow-code")) {Text(label(Words.ProviderOAuthShowCode))};if(showCode && code!=null && valid) {Text(code.code,style=MaterialTheme.typography.headlineSmall);Text(java.time.Instant.ofEpochMilli(code.expiresAt).toString())};if(code==null) Text(label(Words.ProviderOAuthNoCode))}
        item {TextButton({browser=true},enabled=valid && code!=null,modifier=Modifier.testTag("provider-flow-browser")) {Text(label(Words.McpOAuthOpen))}}
        item {TextButton({cancel=vm.prepareProviderOAuthCancel(receipt.key)},enabled=valid && settled.isEmpty() && receipts.none {it.state in setOf("sending","unknown")},modifier=Modifier.testTag("provider-flow-cancel")) {Text(label(Words.ProviderOAuthCancel))};if(settled.isNotEmpty()) Text(label(if(cancelled) Words.ProviderOAuthCancelled else Words.ProviderOAuthUnavailable))}
    }},confirmButton={TextButton(close,Modifier.testTag("provider-flow-close")) {Text(label(Words.Close))}})
    if(browser && code!=null) AlertDialog(onDismissRequest={browser=false},title={Text(label(Words.McpOAuthOpen))},text={LazyColumn(Modifier.heightIn(max=440.dp)) {item {Text(receipt.url);Text(receipt.profile);Text(receipt.actor);Text(receipt.target);Text(code.destination());Text(label(Words.ProviderOAuthBrowser))}}},confirmButton={TextButton({browser=false;vm.openProviderBrowser(receipt.key,snapshot) {url->context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}},enabled=valid && code.expiresAt>now,modifier=Modifier.testTag("provider-browser-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({browser=false},Modifier.testTag("provider-browser-cancel")) {Text(label(Words.Cancel))}})
    cancel?.let {frozen->ProviderOAuthWriteReview(frozen.target,receipt.target,ProviderOAuthAction.Cancel,!s.busy,{cancel=null}) {cancel=null;vm.cancelProviderFlow(frozen)}}
}
