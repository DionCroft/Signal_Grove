package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.data.*

private fun fallbackSectionWord(section:FallbackSection)=if(section==FallbackSection.Primary) Words.HostFallbackPrimary else Words.HostFallbackLegacy
private fun fallbackAuthWord(auth:FallbackAuth)=when(auth) {FallbackAuth.Keep->Words.HostFallbackKeep;FallbackAuth.Host->Words.HostFallbackHost;FallbackAuth.Environment->Words.HostFallbackEnvironment;FallbackAuth.Fresh->Words.HostFallbackFresh}
private class FallbackEdit(val section:FallbackSection,val index:Int?,val row:FallbackRow?)
@Composable fun HostFallbackDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    var read by remember(s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedHostFallbackRead?>(null)}
    var draft by remember(s.fallback) {mutableStateOf(s.fallback?.let(HostFallbackDraft::from))}
    var edit by remember(s.fallback) {mutableStateOf<FallbackEdit?>(null)}
    var review by remember(s.fallback) {mutableStateOf<ReviewedHostFallback?>(null)}
    val receiptRows by vm.receipts.collectAsStateWithLifecycle();val receipts=vm.visibleReceipts(s,receiptRows);val pending=receipts.any {it.state in setOf("sending","unknown")}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostFallback))},text={LazyColumn(Modifier.heightIn(max=520.dp).testTag("fallback-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(s.url);Text(s.profile);Text(label(Words.HostFallbackReadEffect));TextButton({read=vm.prepareHostFallbackRead()},enabled=!s.busy && s.identity!=null && GatewayCronInventory.supportedProfile(s.profile),modifier=Modifier.testTag("fallback-read")) {Text(label(Words.HostFallbackRead))};if(!GatewayCronInventory.supportedProfile(s.profile)) Text(label(Words.PluginProfileUnsupported))}
        if(s.busy) item {QuietProgress();TextButton(vm::cancel) {Text(label(Words.Cancel))}}
        s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}};s.notice?.let {item {Text(label(it))}}
        if(pending) item {Text(label(Words.ManagementOutcomePending))}
        draft?.let {d->
            item {Text(label(Words.HostFallbackOrder));Text(label(Words.HostFallbackPreserve));TextButton({draft=d.clear()},enabled=!s.busy && !pending,modifier=Modifier.testTag("fallback-clear")) {Text(label(Words.HostFallbackClear))}}
            for(section in FallbackSection.entries) {
                item {Text(label(fallbackSectionWord(section)),style=MaterialTheme.typography.titleMedium);TextButton({edit=FallbackEdit(section,null,null)},enabled=!s.busy && !pending && d.rows(section).size<200,modifier=Modifier.testTag("fallback-add-${section.name}")) {Text(label(Words.HostFallbackAdd))};if(d.rows(section).isEmpty()) Text(label(Words.EmptyInventory))}
                itemsIndexed(d.rows(section),key={index,_->section.name+index}) {index,row->Column(Modifier.testTag("fallback-row-${section.name}-$index"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    FallbackRowSummary(row,index)
                    Row {TextButton({draft=d.move(section,index,-1)},enabled=!s.busy && !pending && index>0,modifier=Modifier.testTag("fallback-up-${section.name}-$index")) {Text(label(Words.HostFallbackUp))};TextButton({draft=d.move(section,index,1)},enabled=!s.busy && !pending && index<d.rows(section).lastIndex,modifier=Modifier.testTag("fallback-down-${section.name}-$index")) {Text(label(Words.HostFallbackDown))}}
                    Row {TextButton({edit=FallbackEdit(section,index,row)},enabled=!s.busy && !pending && row.editable,modifier=Modifier.testTag("fallback-edit-${section.name}-$index")) {Text(label(Words.Edit))};TextButton({draft=d.remove(section,index)},enabled=!s.busy && !pending,modifier=Modifier.testTag("fallback-remove-${section.name}-$index")) {Text(label(Words.HostFallbackRemove))}}
                    if(!row.editable) Text(label(Words.HostFallbackUnsupported))
                }}
            }
            item {TextButton({review=vm.prepareHostFallback(d)},enabled=!s.busy && !pending && d.changed.isNotEmpty(),modifier=Modifier.testTag("fallback-review-open")) {Text(label(Words.HostEnvReview))};TextButton({draft=s.fallback?.let(HostFallbackDraft::from)},enabled=!s.busy && d.changed.isNotEmpty(),modifier=Modifier.testTag("fallback-discard")) {Text(label(Words.HostFallbackDiscard))}}
        }
    }},confirmButton={TextButton(close,Modifier.testTag("fallback-close")) {Text(label(Words.Close))}})
    read?.let {frozen->AlertDialog(onDismissRequest={read=null},title={Text(label(Words.HostFallbackRead))},text={LazyColumn(Modifier.heightIn(max=460.dp)) {item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor);Text(label(Words.HostFallbackReadEffect))}}},confirmButton={TextButton({read=null;vm.loadHostFallback(frozen)},enabled=!s.busy,modifier=Modifier.testTag("fallback-read-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({read=null}) {Text(label(Words.Cancel))}})}
    edit?.let {e->FallbackRowEditor(e.row,!s.busy,{edit=null}) {row->draft=draft?.let {if(e.index==null) it.add(e.section,row) else it.replace(e.section,e.index,row)};edit=null}}
    review?.let {frozen->FallbackSaveReview(frozen,!s.busy,{review=null}) {review=null;vm.changeHostFallback(frozen)}}
}
@Composable private fun FallbackRowSummary(row:FallbackRow,index:Int) {
    if(row.editable) {Text("${index+1}. "+row.provider);Text(row.model)} else Text("${index+1}. "+label(Words.HostFallbackUnsupported))
    if(row.baseUrl.isNotEmpty()) Text(label(Words.HostFallbackEndpointPresent))
    if(row.hasInlineCredential) Text(label(Words.HostFallbackInlinePresent))
    if(row.hasEnvironment) Text(label(Words.HostFallbackEnvironmentPresent))
    if(row.extensionCount>0) Text(label(Words.HostFallbackExtensions)+" "+row.extensionCount)
}
@Composable private fun FallbackRowEditor(row:FallbackRow?,enabled:Boolean,close:()->Unit,save:(FallbackRow)->Unit) {
    var show by remember(row) {mutableStateOf(false)};var provider by remember(row) {mutableStateOf(row?.provider.orEmpty())};var model by remember(row) {mutableStateOf(row?.model.orEmpty())};var url by remember(row) {mutableStateOf(row?.baseUrl.orEmpty())};var mode by remember(row) {mutableStateOf<String?>(if(row==null) "" else null)}
    var auth by remember(row) {mutableStateOf(if(row==null) FallbackAuth.Host else FallbackAuth.Keep)};var credential by remember(row,auth) {mutableStateOf("")}
    val valid=HostFallbackPolicy.route(provider.trim(),model.trim(),url.trim(),mode ?: "") && when(auth) {FallbackAuth.Fresh->HostEnvPolicy.value(credential.trim());FallbackAuth.Environment->HostFallbackPolicy.environment(credential.trim());else->true}
    val keyboard=LocalSoftwareKeyboardController.current
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostFallbackEdit))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("fallback-editor"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(label(Words.HostFallbackPrivate));TextButton({show=!show},Modifier.testTag("fallback-editor-reveal")) {Text(label(if(show) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
        if(show) {
            item {OutlinedTextField(provider,{if(it.length<=256) provider=it},Modifier.fillMaxWidth().testTag("fallback-provider"),label={Text(label(Words.CredentialPoolProvider))},singleLine=true)}
            item {OutlinedTextField(model,{if(it.length<=2048) model=it},Modifier.fillMaxWidth().testTag("fallback-model"),label={Text(label(Words.RequestedModel))},singleLine=true)}
            item {OutlinedTextField(url,{if(it.length<=8192) url=it},Modifier.fillMaxWidth().testTag("fallback-url"),label={Text(label(Words.HostFallbackUrl))},singleLine=true)}
            item {Text(label(Words.HostFallbackMode));for(option in (if(row==null) HostFallbackPolicy.apiModes else listOf(null)+HostFallbackPolicy.apiModes)) TextButton({mode=option},modifier=Modifier.testTag("fallback-mode-$option")) {Text((if(mode==option) "✓ " else "")+(option?.ifEmpty {label(Words.HostFallbackAuto)} ?: label(Words.HostFallbackKeepMode)))}}
            item {Text(label(Words.HostFallbackAuth));for(option in FallbackAuth.entries.filter {row!=null || it!=FallbackAuth.Keep}) TextButton({auth=option},modifier=Modifier.testTag("fallback-auth-${option.name}")) {Text((if(auth==option) "✓ " else "")+label(fallbackAuthWord(option)))}}
            if(auth==FallbackAuth.Fresh || auth==FallbackAuth.Environment) item {OutlinedTextField(credential,{if(it.length<=16384) credential=it},Modifier.fillMaxWidth().testTag("fallback-credential"),label={Text(label(if(auth==FallbackAuth.Fresh) Words.CredentialPoolFresh else Words.HostFallbackEnvironmentName))},singleLine=true,visualTransformation=if(auth==FallbackAuth.Fresh) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)}
            item {Text(label(Words.HostFallbackAuthEffect));if(!valid) Text(label(Words.HostFallbackInputInvalid))}
        }
    }},confirmButton={TextButton({keyboard?.hide();save(if(row==null) FallbackRow.fresh(provider,model,url,mode ?: "",auth,credential) else row.edit(provider,model,url,mode,auth,credential))},enabled=enabled && show && valid,modifier=Modifier.testTag("fallback-stage-row")) {Text(label(Words.HostFallbackStage))}},dismissButton={TextButton(close,Modifier.testTag("fallback-editor-cancel")) {Text(label(Words.Cancel))}})
}
@Composable private fun FallbackSaveReview(review:ReviewedHostFallback,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    val request=review.request;var show by remember(review) {mutableStateOf(false)};var approved by remember(review) {mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostFallbackSave))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("fallback-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor);Text(label(Words.HostFallbackSaveEffect));Text(label(Words.HostFallbackConcurrency));Text(label(Words.HostFallbackPreserve));Text(label(Words.HostFallbackPrivate));TextButton({show=!show},Modifier.testTag("fallback-review-reveal")) {Text(label(if(show) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
        for(section in FallbackSection.entries) {
            item {Text(label(fallbackSectionWord(section))+" · "+request.draft.rows(section).size);Text(label(if(section in request.sections) Words.HostFallbackReplaced else Words.HostFallbackUnchanged))}
            if(show) itemsIndexed(request.draft.rows(section)) {index,row->Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {FallbackRowSummary(row,index);val details=row.reviewDetails();for((key,value) in details) if(value.toString()!="\"\"") {Text(label(when(key) {"base_url"->Words.HostFallbackUrl;"api_mode","transport"->Words.HostFallbackMode;"fresh_api_key"->Words.CredentialPoolFresh;else->Words.HostFallbackEnvironmentName}));Text((value as kotlinx.serialization.json.JsonPrimitive).content)};if(!row.editable) Text(label(Words.HostFallbackUnsupported))}}
        }
        item {Row {Checkbox(approved,{approved=it},modifier=Modifier.testTag("fallback-approve"));Text(label(Words.HostFallbackApprove),Modifier.weight(1f))}}
    }},confirmButton={TextButton(confirm,enabled=enabled && show && approved,modifier=Modifier.testTag("fallback-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.testTag("fallback-review-cancel")) {Text(label(Words.Cancel))}})
}
@Composable fun HostFallbackReceipt() {Text(label(Words.HostFallbackAcknowledged));Text(label(Words.HostFallbackReceipt))}
