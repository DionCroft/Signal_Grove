package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

private fun poolSourceWord(kind:PoolSourceKind)=when(kind) {PoolSourceKind.Manual->Words.CredentialPoolManual;PoolSourceKind.Environment->Words.CredentialPoolEnvironment;PoolSourceKind.Configuration->Words.CredentialPoolConfiguration;PoolSourceKind.HermesOAuth->Words.CredentialPoolOAuth;PoolSourceKind.ExternalCLI->Words.CredentialPoolExternal;PoolSourceKind.ProviderHook->Words.CredentialPoolHook}
private fun poolStatusWord(status:PoolReportedStatus)=when(status) {PoolReportedStatus.Unreported->Words.CredentialPoolUnreported;PoolReportedStatus.Ok->Words.CredentialPoolOk;PoolReportedStatus.Exhausted->Words.CredentialPoolExhausted;PoolReportedStatus.Dead->Words.CredentialPoolDead;PoolReportedStatus.Other->Words.CredentialPoolOtherStatus}
@Composable fun CredentialPoolDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    var read by remember(s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedCredentialPoolRead?>(null)}
    var edit by remember(s.credentialPool) {mutableStateOf<String?>(null)}
    var review by remember(s.credentialPool) {mutableStateOf<ReviewedCredentialPool?>(null)}
    val report=s.poolCleanup?.takeIf {it.owner==vm.reviewTarget()}
    var details by remember(report) {mutableStateOf(false)}
    var filter by remember(s.apiScope,s.url,s.profile) {mutableStateOf("")}
    val receiptRows by vm.receipts.collectAsStateWithLifecycle();val receipts=vm.visibleReceipts(s,receiptRows);val pending=receipts.any {it.state in setOf("sending","unknown")}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.CredentialPool))},text={LazyColumn(Modifier.heightIn(max=520.dp).testTag("credential-pool-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(s.url);Text(s.profile);Text(label(Words.CredentialPoolReadEffect));TextButton({read=vm.prepareCredentialPoolRead()},enabled=!s.busy && s.identity!=null && GatewayCronInventory.supportedProfile(s.profile),modifier=Modifier.testTag("credential-pool-read")) {Text(label(Words.CredentialPoolRead))};if(!GatewayCronInventory.supportedProfile(s.profile)) Text(label(Words.PluginProfileUnsupported))}
        if(s.busy) item {QuietProgress();TextButton(vm::cancel) {Text(label(Words.Cancel))}}
        s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}}
        s.notice?.let {item {Text(label(it))}}
        if(report?.hasMessages==true) item {TextButton({details=true},enabled=!s.busy,modifier=Modifier.testTag("credential-pool-report-open")) {Text(label(Words.CredentialPoolDetails))}}
        if(pending) item {Text(label(Words.ManagementOutcomePending))}
        s.credentialPool?.let {inventory->
            item {OutlinedTextField(filter,{if(it.length<=256) filter=it},Modifier.fillMaxWidth().testTag("credential-pool-filter"),label={Text(label(Words.CredentialPoolProvider))},singleLine=true);TextButton({edit=""},enabled=!s.busy && !pending,modifier=Modifier.testTag("credential-pool-add")) {Text(label(Words.CredentialPoolAdd))};Text(label(Words.CredentialPoolCli));if(inventory.providers.isEmpty()) Text(label(Words.EmptyInventory))}
            for(provider in inventory.providers.filter {filter.isBlank() || it.provider.contains(filter,true)}) {
                item(key="provider:"+provider.provider) {Text(provider.provider,style=MaterialTheme.typography.titleMedium);Text(label(Words.CredentialPoolEntryCount)+" "+provider.entries.size);TextButton({edit=provider.provider},enabled=!s.busy && !pending && provider.addressable,modifier=Modifier.testTag("credential-pool-add-${provider.provider}")) {Text(label(Words.CredentialPoolAdd))};if(!provider.addressable) Text(label(Words.CredentialPoolProviderInvalid))}
                items(provider.entries,key={"entry:"+provider.provider+":"+it.index}) {entry->Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    PoolCredentialDetails(entry)
                    TextButton({review=vm.prepareCredentialPool(provider.provider,entry=entry)},enabled=!s.busy && !pending && provider.addressable,modifier=Modifier.testTag("credential-pool-remove-${provider.provider}-${entry.index}")) {Text(label(Words.CredentialPoolRemove))}
                }}
            }
        }
        items(receipts.filter {it.action in setOf("CredentialPoolAdd","CredentialPoolRemove")},key={"receipt:"+it.key}) {receipt->Column(Modifier.testTag("credential-pool-receipt-${receipt.key}"),verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(label(mcpActionWord(receipt.action))+" · "+receipt.target);Text(receipt.actor);Text(label(managementStateWord(receipt.state)));if(receipt.resultSummary.isNotBlank()) CredentialPoolReceiptSummary(receipt)}}
    }},confirmButton={TextButton(close,Modifier.testTag("credential-pool-close")) {Text(label(Words.Close))}})
    if(details && report!=null) CredentialPoolDetailsDialog(report) {details=false}
    read?.let {frozen->AlertDialog(onDismissRequest={read=null},title={Text(label(Words.CredentialPoolRead))},text={LazyColumn(Modifier.heightIn(max=460.dp)) {item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor);Text(label(Words.CredentialPoolReadEffect));Text(label(Words.CredentialPoolSharedEffect))}}},confirmButton={TextButton({read=null;vm.loadCredentialPool(frozen)},enabled=!s.busy,modifier=Modifier.testTag("credential-pool-read-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({read=null},Modifier.testTag("credential-pool-read-cancel")) {Text(label(Words.Cancel))}})}
    edit?.let {provider->CredentialPoolEditor(provider,!s.busy,{edit=null}) {name,value,label->review=vm.prepareCredentialPool(name,value,label);if(review!=null) edit=null}}
    review?.let {frozen->CredentialPoolReview(frozen,!s.busy,{review=null}) {review=null;vm.changeCredentialPool(frozen)}}
}
@Composable private fun CredentialPoolDetailsDialog(report:PoolCleanupReport,close:()->Unit) {
    var show by remember(report) {mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.CredentialPoolDetails))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("credential-pool-details"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(report.owner.url);Text(report.owner.profile);Text(report.owner.actor);Text(report.provider);Text(report.receiptKey);Text(label(Words.CredentialPoolPrivateDetails));TextButton({show=!show},Modifier.testTag("credential-pool-details-reveal")) {Text(label(if(show) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
        if(show) {
            item {Text(label(Words.CredentialPoolCleanupMessages)+" "+report.cleaned.size)}
            items(report.cleaned) {Text(it)}
            item {Text(label(Words.CredentialPoolHints)+" "+report.hints.size)}
            items(report.hints) {Text(it)}
        }
    }},confirmButton={TextButton(close,Modifier.testTag("credential-pool-details-close")) {Text(label(Words.Close))}})
}
@Composable private fun PoolCredentialDetails(entry:PoolCredential) {
    Text(label(Words.CredentialPoolIndex)+" "+entry.index);if(entry.label.isNotEmpty()) Text(entry.label,style=MaterialTheme.typography.titleSmall)
    Text(label(Words.CredentialPoolReviewedId)+" · "+entry.id);Text(entry.authType);Text(label(poolSourceWord(entry.sourceKind)));Text(label(poolStatusWord(entry.status)))
    Text(label(Words.CredentialPoolRequests)+" "+entry.requestCount);Text(label(Words.CredentialPoolPriority)+" "+entry.priority);Text(label(if(entry.hasRefresh) Words.CredentialPoolRefreshPresent else Words.CredentialPoolRefreshAbsent))
}
@Composable private fun CredentialPoolEditor(initial:String,enabled:Boolean,close:()->Unit,review:(String,String,String)->Unit) {
    var provider by remember(initial) {mutableStateOf(initial)};var value by remember(initial) {mutableStateOf("")};var name by remember(initial) {mutableStateOf("")};var show by remember(initial) {mutableStateOf(false)}
    val valid=CredentialPoolProtocol.provider(provider) && HostEnvPolicy.value(value.trim()) && name.length<=256 && name.none(Char::isISOControl)
    val keyboard=LocalSoftwareKeyboardController.current
    AlertDialog(onDismissRequest=close,title={Text(label(Words.CredentialPoolAdd))},text={LazyColumn(Modifier.heightIn(max=460.dp).testTag("credential-pool-editor"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {OutlinedTextField(provider,{if(it.length<=256) provider=it},Modifier.fillMaxWidth().testTag("credential-pool-provider"),label={Text(label(Words.CredentialPoolProvider))},singleLine=true)}
        item {OutlinedTextField(name,{if(it.length<=256) name=it},Modifier.fillMaxWidth().testTag("credential-pool-label"),label={Text(label(Words.CredentialPoolLabel))},singleLine=true)}
        item {OutlinedTextField(value,{if(it.length<=16384) value=it},Modifier.fillMaxWidth().testTag("credential-pool-value"),label={Text(label(Words.CredentialPoolFresh))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false),visualTransformation=if(show) VisualTransformation.None else PasswordVisualTransformation());TextButton({show=!show}) {Text(label(if(show) Words.PluginUpdateHideDetails else Words.CredentialPoolReveal))}}
        if(!valid) item {Text(label(Words.CredentialPoolInputInvalid))}
    }},confirmButton={TextButton({keyboard?.hide();review(provider,value,name)},enabled=enabled && valid,modifier=Modifier.testTag("credential-pool-review-open")) {Text(label(Words.HostEnvReview))}},dismissButton={TextButton(close,Modifier.testTag("credential-pool-editor-cancel")) {Text(label(Words.Cancel))}})
}
@Composable private fun CredentialPoolReview(review:ReviewedCredentialPool,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    val request=review.request;var show by remember(review) {mutableStateOf(false)};var approved by remember(review) {mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(label(mcpActionWord(request.action.receiptAction)))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("credential-pool-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor);Text(request.provider,style=MaterialTheme.typography.titleMedium);Text(label(if(request.action==CredentialPoolAction.Add) Words.CredentialPoolAddEffect else Words.CredentialPoolRemoveEffect));Text(label(Words.CredentialPoolConcurrency));Text(label(Words.CredentialPoolSharedEffect))}
        request.entry?.let {entry->item {PoolCredentialDetails(entry);Text(label(Words.CredentialPoolIndexEffect))}}
        if(request.action==CredentialPoolAction.Add) {
            item {Text(label(Words.CredentialPoolLabel));Text(request.freshLabel.ifEmpty {label(Words.CredentialPoolDefaultLabel)});TextButton({show=!show},Modifier.testTag("credential-pool-review-reveal")) {Text(label(if(show) Words.PluginUpdateHideDetails else Words.CredentialPoolReveal))}}
            if(show) item {OutlinedTextField(request.freshValue,{},Modifier.fillMaxWidth().testTag("credential-pool-reviewed-value"),readOnly=true,singleLine=true,label={Text(label(Words.CredentialPoolFresh))})}
        }
        item {Row {Checkbox(approved,{approved=it},modifier=Modifier.testTag("credential-pool-approve"));Text(label(Words.CredentialPoolApprove),Modifier.weight(1f))}}
    }},confirmButton={TextButton(confirm,enabled=enabled && approved && (request.action==CredentialPoolAction.Remove || show),modifier=Modifier.testTag("credential-pool-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.testTag("credential-pool-review-cancel")) {Text(label(Words.Cancel))}})
}
@Composable fun CredentialPoolReceiptSummary(receipt:ManagementOperation) {
    val result=remember(receipt.resultSummary) {runCatching {Json.parseToJsonElement(receipt.resultSummary).jsonObject}.getOrDefault(JsonObject(emptyMap()))}
    Text(label(Words.CredentialPoolAcknowledged));Text(label(Words.CredentialPoolReportedCount)+" "+result.text("reported_count"))
    if(receipt.action=="CredentialPoolRemove") {
        Text(label(Words.CredentialPoolIndex)+" "+result.text("requested_index"));Text(label(Words.CredentialPoolReviewedId)+" · "+result.text("reviewed_id"));Text(label(Words.CredentialPoolIndexReceipt))
        if(result["cleanup_report_available"]==JsonPrimitive(true)) {Text(label(Words.CredentialPoolCleanupMessages)+" "+result.text("cleanup_message_count"));Text(label(Words.CredentialPoolHints)+" "+result.text("hint_message_count"))} else Text(label(Words.CredentialPoolCleanupUnavailable))
    }
    Text(label(Words.CredentialPoolReceipt))
}
