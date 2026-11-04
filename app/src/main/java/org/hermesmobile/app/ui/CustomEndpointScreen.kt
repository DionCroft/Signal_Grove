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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.data.*

private fun endpointKeyWord(key:CustomEndpointKey)=when(key) {CustomEndpointKey.Keep->Words.CustomEndpointsKeepKey;CustomEndpointKey.Fresh->Words.CustomEndpointsFreshKey;CustomEndpointKey.Clear->Words.CustomEndpointsClearKey}
private fun endpointSourceWord(source:CustomEndpointSource)=when(source) {CustomEndpointSource.Provider->Words.CustomEndpointsProvider;CustomEndpointSource.Legacy->Words.CustomEndpointsLegacy;CustomEndpointSource.Direct->Words.CustomEndpointsDirect;else->Words.CustomEndpointsUnsupported}
private class EndpointEdit(val row:CustomEndpointRow?)
@Composable fun CustomEndpointsDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    var read by remember(s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedCustomEndpointRead?>(null)}
    var edit by remember(s.customEndpoints) {mutableStateOf<EndpointEdit?>(null)}
    var details by remember(s.customEndpoints) {mutableStateOf<CustomEndpointRow?>(null)}
    var review by remember(s.customEndpoints) {mutableStateOf<ReviewedCustomEndpoint?>(null)}
    val receiptRows by vm.receipts.collectAsStateWithLifecycle();val receipts=vm.visibleReceipts(s,receiptRows);val pending=receipts.any {it.state in setOf("sending","unknown")}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.CustomEndpoints))},text={LazyColumn(Modifier.heightIn(max=520.dp).testTag("endpoint-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(s.url);Text(s.profile)}
        item {Text(label(Words.CustomEndpointsReadEffect))}
        item {TextButton({read=vm.prepareCustomEndpointRead()},enabled=!s.busy && s.identity!=null && GatewayCronInventory.supportedProfile(s.profile),modifier=Modifier.testTag("endpoint-read")) {Text(label(Words.CustomEndpointsRead))};if(!GatewayCronInventory.supportedProfile(s.profile)) Text(label(Words.PluginProfileUnsupported))}
        if(s.busy) item {QuietProgress();TextButton(vm::cancel) {Text(label(Words.Cancel))}}
        s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}};s.notice?.let {item {Text(label(it))}}
        if(pending) item {Text(label(Words.ManagementOutcomePending))}
        s.customEndpoints?.let {inventory->
            item {TextButton({edit=EndpointEdit(null)},enabled=!s.busy && !pending && inventory.canCreate,modifier=Modifier.testTag("endpoint-create")) {Text(label(Words.CustomEndpointsNew))};if(!inventory.canCreate) Text(label(Words.CustomEndpointsUnsupported));if(inventory.rows.isEmpty()) Text(label(Words.EmptyInventory))}
            items(inventory.rows,key={it.id}) {row->Column(Modifier.testTag("endpoint-row-${row.id}"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(row.name,style=MaterialTheme.typography.titleMedium);Text(row.id);Text(row.model);Text(label(endpointSourceWord(row.source)));if(row.current) Text(label(Words.CustomEndpointsCurrent))
                if(row.hasKey) Text(label(Words.CustomEndpointsCredentialPresent))
                TextButton({details=row},enabled=!s.busy,modifier=Modifier.testTag("endpoint-details-${row.id}")) {Text(label(Words.CustomEndpointsDetails))}
                val writable=inventory.canAddress(row);val editable=inventory.canEdit(row);val enabled=!s.busy && !pending && writable
                Row {TextButton({edit=EndpointEdit(row)},enabled=!s.busy && !pending && editable,modifier=Modifier.testTag("endpoint-edit-${row.id}")) {Text(label(Words.Edit))};TextButton({review=vm.prepareCustomEndpoint(CustomEndpointAction.Remove,row)},enabled=enabled,modifier=Modifier.testTag("endpoint-remove-${row.id}")) {Text(label(Words.CustomEndpointsRemove))}}
                TextButton({review=vm.prepareCustomEndpoint(CustomEndpointAction.Activate,row)},enabled=enabled && CustomEndpointPolicy.route(row.baseUrl,row.model),modifier=Modifier.testTag("endpoint-activate-${row.id}")) {Text(label(Words.CustomEndpointsActivate))}
                if(!editable) Text(label(if(row.source==CustomEndpointSource.Direct) Words.CustomEndpointsDirect else Words.CustomEndpointsUnsupported))
                else if(!writable) Text(label(Words.CustomEndpointsPathUnavailable))
                else if(!CustomEndpointPolicy.route(row.baseUrl,row.model)) Text(label(Words.CustomEndpointsInputInvalid))
            }}
        }
        items(receipts.filter {it.action.startsWith("CustomEndpoint")},key={"receipt:"+it.key}) {receipt->Column(Modifier.testTag("endpoint-receipt-${receipt.key}"),verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(label(mcpActionWord(receipt.action)));Text(label(managementStateWord(receipt.state)));if(receipt.resultSummary.isNotEmpty()) {if(receipt.action=="CustomEndpointProbe") EndpointProbeReceipt(receipt) else CustomEndpointReceipt()}}}
    }},confirmButton={TextButton(close,Modifier.testTag("endpoint-close")) {Text(label(Words.Close))}})
    read?.let {frozen->AlertDialog(onDismissRequest={read=null},title={Text(label(Words.CustomEndpointsRead))},text={LazyColumn(Modifier.heightIn(max=460.dp)) {item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor)};item {Text(label(Words.CustomEndpointsReadEffect))}}},confirmButton={TextButton({read=null;vm.loadCustomEndpoints(frozen)},enabled=!s.busy,modifier=Modifier.testTag("endpoint-read-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({read=null},Modifier.testTag("endpoint-read-cancel")) {Text(label(Words.Cancel))}})}
    edit?.let {e->s.customEndpoints?.let {inventory->EndpointEditor(inventory,e.row,vm,s,!s.busy,{if(s.busy) vm.cancel() else vm.clearCustomEndpointProbe();edit=null}) {draft->review=vm.prepareCustomEndpoint(CustomEndpointAction.Save,e.row,draft);if(review!=null) edit=null}}}
    details?.let {row->EndpointDetails(row) {details=null}}
    review?.let {frozen->EndpointReview(frozen,!s.busy,{review=null}) {review=null;vm.changeCustomEndpoint(frozen)}}
}
@Composable private fun EndpointDetails(row:CustomEndpointRow,close:()->Unit) {
    var show by remember(row) {mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.CustomEndpointsDetails))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("endpoint-details"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(row.name);Text(row.id);Text(label(Words.CustomEndpointsPrivate))}
        item {TextButton({show=!show},Modifier.testTag("endpoint-details-reveal")) {Text(label(if(show) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
        if(show) {
            item {Text(label(Words.HostFallbackUrl));Text(row.baseUrl);Text(label(Words.RequestedModel));Text(row.model);Text(label(Words.HostFallbackMode));Text(row.apiMode.ifEmpty {label(Words.CustomEndpointsReportedAuto)});Text(label(Words.CustomEndpointsContext));Text(row.contextLength?.toString() ?: label(Words.CustomEndpointsContextKeep));Text(label(Words.CustomEndpointsDiscovery));Text(label(if(row.discoverModels) Words.Enabled else Words.Disabled))}
            items(row.models) {Text(it)}
        }
    }},confirmButton={TextButton(close,Modifier.testTag("endpoint-details-close")) {Text(label(Words.Close))}})
}
@Composable private fun EndpointEditor(inventory:CustomEndpointSnapshot,row:CustomEndpointRow?,vm:ManagementViewModel,s:ManagementState,enabled:Boolean,close:()->Unit,save:(CustomEndpointDraft)->Unit) {
    var show by remember(row) {mutableStateOf(false)};var id by remember(row) {mutableStateOf(row?.id.orEmpty())};var name by remember(row) {mutableStateOf(row?.name.orEmpty())};var url by remember(row) {mutableStateOf(row?.baseUrl.orEmpty())};var model by remember(row) {mutableStateOf(row?.model.orEmpty())}
    var key by remember(row) {mutableStateOf(CustomEndpointKey.Keep)};var secret by remember(row,key) {mutableStateOf("")};var mode by remember(row) {mutableStateOf<String?>(if(row==null) "" else null)};var setContext by remember(row) {mutableStateOf(false)};var context by remember(row) {mutableStateOf("")};var models by remember(row) {mutableStateOf("")};var discover by remember(row) {mutableStateOf(row?.discoverModels ?: true)};var main by remember(row) {mutableStateOf(false)}
    val candidate=remember(id,name,url,model,key,secret,mode,setContext,context,models,discover,main) {runCatching {val limit=if(setContext) context.toLongOrNull() ?: error("Invalid limit") else null;CustomEndpointDraft(if(row==null && id.isBlank()) CustomEndpointPolicy.slug(name) else id,name,url,model,key,if(key==CustomEndpointKey.Fresh) secret else "",mode,limit,discover,main,models.lines().map {it.trim()}.filter {it.isNotEmpty()}.distinct())}.getOrNull()}
    val duplicate=row==null && candidate!=null && !inventory.freshId(candidate.id)
    val valid=candidate!=null && !duplicate && (row!=null || CustomEndpointPolicy.newId(candidate.id))
    var probeReview by remember(row) {mutableStateOf<ReviewedCustomEndpointProbe?>(null)}
    var probeReport by remember(row) {mutableStateOf(false)}
    val receiptRows by vm.receipts.collectAsStateWithLifecycle();val receipts=vm.visibleReceipts(s,receiptRows);val pending=receipts.any {it.state in setOf("sending","unknown")}
    val report=s.customEndpointProbe?.takeIf {candidate!=null && it.request.expected===inventory && it.request.same(candidate)}
    val keyboard=LocalSoftwareKeyboardController.current
    AlertDialog(onDismissRequest=close,title={Text(label(if(row==null) Words.CustomEndpointsNew else Words.CustomEndpointsSave))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("endpoint-editor"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(label(Words.CustomEndpointsPrivate));TextButton({show=!show},Modifier.testTag("endpoint-editor-reveal")) {Text(label(if(show) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
        if(show) {
            item {OutlinedTextField(id,{if(row==null && it.length<=128) id=it},Modifier.fillMaxWidth().testTag("endpoint-id"),label={Text(label(Words.CustomEndpointsId))},singleLine=true,readOnly=row!=null)}
            item {OutlinedTextField(name,{if(it.length<=512) name=it},Modifier.fillMaxWidth().testTag("endpoint-name"),label={Text(label(Words.CustomEndpointsName))},singleLine=true)}
            item {OutlinedTextField(url,{if(it.length<=8192) url=it},Modifier.fillMaxWidth().testTag("endpoint-url"),label={Text(label(Words.HostFallbackUrl))},singleLine=true)}
            item {OutlinedTextField(model,{if(it.length<=2048) model=it},Modifier.fillMaxWidth().testTag("endpoint-model"),label={Text(label(Words.RequestedModel))},singleLine=true)}
            item {Text(label(Words.HostFallbackMode));for(option in (if(row==null) HostFallbackPolicy.apiModes else listOf(null)+HostFallbackPolicy.apiModes)) TextButton({mode=option},modifier=Modifier.testTag("endpoint-mode-$option")) {Text((if(mode==option) "✓ " else "")+(option?.ifEmpty {label(Words.HostFallbackAuto)} ?: label(Words.HostFallbackKeepMode)))}}
            item {for(option in CustomEndpointKey.entries.filter {row!=null || it!=CustomEndpointKey.Clear}) TextButton({key=option},modifier=Modifier.testTag("endpoint-key-${option.name}")) {Text((if(key==option) "✓ " else "")+label(endpointKeyWord(option)))}}
            if(key==CustomEndpointKey.Fresh) item {OutlinedTextField(secret,{if(it.length<=16384) secret=it},Modifier.fillMaxWidth().testTag("endpoint-secret"),label={Text(label(Words.CredentialPoolFresh))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false),visualTransformation=PasswordVisualTransformation())}
            item {Text(label(Words.CustomEndpointsKeyEffect))}
            item {Row {Checkbox(setContext,{setContext=it},modifier=Modifier.testTag("endpoint-context-set"));Text(label(Words.CustomEndpointsSetContext),Modifier.weight(1f))};if(setContext) OutlinedTextField(context,{if(it.length<=10) context=it},Modifier.fillMaxWidth().testTag("endpoint-context"),label={Text(label(Words.CustomEndpointsContext))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))}
            item {OutlinedTextField(models,{if(it.length<=131072) models=it},Modifier.fillMaxWidth().testTag("endpoint-models"),label={Text(label(Words.CustomEndpointsAdditionalModels))},minLines=2,maxLines=5)}
            item {Row {Checkbox(discover,{discover=it},modifier=Modifier.testTag("endpoint-discover"));Text(label(Words.CustomEndpointsDiscovery),Modifier.weight(1f))};Row {Checkbox(main,{main=it},modifier=Modifier.testTag("endpoint-main"));Text(label(Words.CustomEndpointsMakeDefault),Modifier.weight(1f))}}
            item {Text(label(Words.EndpointProbeEffect));TextButton({candidate?.let {probeReview=vm.prepareCustomEndpointProbe(it)}},enabled=enabled && !pending && candidate!=null,modifier=Modifier.testTag("endpoint-probe-open")) {Text(label(Words.EndpointProbe))};if(report!=null) TextButton({probeReport=true},enabled=enabled,modifier=Modifier.testTag("endpoint-probe-report-open")) {Text(label(Words.EndpointProbeReport))};if(pending) Text(label(Words.ManagementOutcomePending));if(s.busy) {QuietProgress();TextButton(vm::cancel,Modifier.testTag("endpoint-probe-cancel-work")) {Text(label(Words.Cancel))}};s.error?.let {Text(label(it))}}
            item {Text(label(Words.CustomEndpointsLimits));if(duplicate) Text(label(Words.CustomEndpointsDuplicate));else if(!valid) Text(label(Words.CustomEndpointsInputInvalid))}
        }
    }},confirmButton={TextButton({keyboard?.hide();candidate?.let(save)},enabled=enabled && !pending && show && valid,modifier=Modifier.testTag("endpoint-review-open")) {Text(label(Words.HostEnvReview))}},dismissButton={TextButton(close,Modifier.testTag("endpoint-editor-cancel")) {Text(label(Words.Cancel))}})
    probeReview?.let {frozen->EndpointProbeReview(frozen,enabled,{probeReview=null}) {probeReview=null;vm.probeCustomEndpoint(frozen)}}
    if(probeReport && report!=null) EndpointProbeReport(report) {probeReport=false}
}
@Composable private fun EndpointReview(review:ReviewedCustomEndpoint,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    val request=review.request;val draft=request.draft;val row=request.row;var show by remember(review) {mutableStateOf(false)};var approved by remember(review) {mutableStateOf(false)}
    val effect=when(request.action) {CustomEndpointAction.Save->Words.CustomEndpointsSaveEffect;CustomEndpointAction.Activate->Words.CustomEndpointsActivateEffect;CustomEndpointAction.Remove->Words.CustomEndpointsRemoveEffect}
    AlertDialog(onDismissRequest=close,title={Text(label(mcpActionWord(request.action.receiptAction)))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("endpoint-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor)}
        item {Text(label(effect))};item {Text(label(Words.CustomEndpointsConcurrency))};item {Text(label(Words.CustomEndpointsShared))}
        if(request.expected.sharesGeneratedKey(request.id)) item {Text(label(Words.CustomEndpointsKnownShared))}
        item {Text(label(Words.CustomEndpointsPrivate));TextButton({show=!show},Modifier.testTag("endpoint-review-reveal")) {Text(label(if(show) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
        if(show) {
            item {Text(label(Words.CustomEndpointsId));Text(request.id);Text(label(Words.CustomEndpointsName));Text(draft?.name ?: row!!.name);Text(label(Words.HostFallbackUrl));Text(draft?.baseUrl ?: row!!.baseUrl);Text(label(Words.RequestedModel));Text(draft?.model ?: row!!.model)}
            item {Text(label(Words.CustomEndpointsCurrent));Text(request.expected.current.provider);Text(request.expected.current.model);Text(request.expected.current.baseUrl)}
            draft?.let {d->
                item {Text(label(endpointKeyWord(d.key)));Text(label(Words.CustomEndpointsKeyEffect));Text(label(Words.HostFallbackMode));Text(d.apiMode?.ifEmpty {label(Words.HostFallbackAuto)} ?: label(Words.HostFallbackKeepMode));Text(label(Words.CustomEndpointsContext));Text(d.contextLength?.toString() ?: label(Words.CustomEndpointsContextKeep));Text(label(Words.CustomEndpointsDiscovery));Text(label(if(d.discoverModels) Words.Enabled else Words.Disabled));Text(label(Words.CustomEndpointsMakeDefault));Text(label(if(d.makeDefault) Words.Enabled else Words.Disabled))}
                if(d.key==CustomEndpointKey.Fresh) item {OutlinedTextField(d.secret,{},Modifier.fillMaxWidth().testTag("endpoint-reviewed-secret"),readOnly=true,singleLine=true,label={Text(label(Words.CredentialPoolFresh))})}
                items(d.models) {Text(it)}
                item {Text(label(Words.CustomEndpointsLimits))}
            }
        }
        item {Row {Checkbox(approved,{approved=it},modifier=Modifier.testTag("endpoint-approve"));Text(label(Words.CustomEndpointsApprove),Modifier.weight(1f))}}
    }},confirmButton={TextButton(confirm,enabled=enabled && show && approved,modifier=Modifier.testTag("endpoint-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.testTag("endpoint-review-cancel")) {Text(label(Words.Cancel))}})
}
@Composable fun CustomEndpointReceipt() {Text(label(Words.CustomEndpointsAcknowledged));Text(label(Words.CustomEndpointsReceipt))}
