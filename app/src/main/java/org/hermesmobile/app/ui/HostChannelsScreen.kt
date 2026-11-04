package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

private fun channelHomeLabel(key:String)=when(key) {"platform"->Words.ChannelsHomePlatform;"chat_id"->Words.ChannelsHomeChat;"name"->Words.ChannelsHomeName;"thread_id"->Words.ChannelsHomeThread;"user_id"->Words.ChannelsHomeUser;else->Words.ChannelsHomeScope}
private fun channelState(value:String)=when(value) {"connected"->Words.ChannelsConnected;"disabled"->Words.Disabled;"not_configured"->Words.ChannelsNeedsSetup;"pending_restart"->Words.ChannelsRestart;"gateway_stopped","stopped"->Words.ChannelsStopped;"startup_failed","fatal","error","failed"->Words.ChannelsFailed;else->Words.ChannelsOtherState}
@Composable fun HostChannelsDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    var read by remember(s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedChannelsRead?>(null)}
    var edit by remember(s.channels) {mutableStateOf<HostChannelRow?>(null)}
    var details by remember(s.channels) {mutableStateOf<HostChannelRow?>(null)}
    var review by remember(s.channels) {mutableStateOf<ReviewedChannel?>(null)}
    var report by remember(s.channelPosture) {mutableStateOf(false)}
    val receiptRows by vm.receipts.collectAsStateWithLifecycle();val pending=vm.visibleReceipts(s,receiptRows).any {it.state in setOf("sending","unknown")}
    val inventory=s.channels?.takeIf {it.owner==vm.reviewTarget()}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.Channels))},text={LazyColumn(Modifier.heightIn(max=520.dp).testTag("host-channels-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(s.url);Text(s.profile)};item {Text(label(Words.ChannelsPrivacy))};item {TextButton({read=vm.prepareChannelsRead()},enabled=!s.busy && s.identity!=null && GatewayCronInventory.supportedProfile(s.profile),modifier=Modifier.testTag("channels-read")) {Text(label(Words.ChannelsRead))};if(!GatewayCronInventory.supportedProfile(s.profile)) Text(label(Words.PluginProfileUnsupported))}
        if(s.busy) item {QuietProgress();TextButton(vm::cancel) {Text(label(Words.Cancel))}}
        if(pending) item {Text(label(Words.ManagementOutcomePending))}
        s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}}
        s.notice?.let {item {Text(label(it))}}
        if(inventory!=null) {
            if(inventory.rows.isEmpty()) item {Text(label(Words.EmptyInventory))}
            for(row in inventory.rows) {
                item(key="summary:"+row.id) {Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(row.name,style=MaterialTheme.typography.titleMedium);Text(row.description);Text(label(channelState(row.state)));Text(label(if(row.enabled) Words.Enabled else Words.Disabled));Text(label(if(row.configured) Words.ChannelsConfigured else Words.ChannelsNeedsSetup));if(row.relayFronted || row.relayDisabled || row.relayExclusive) Text(label(Words.ChannelsRelay))}}
                item(key="details:"+row.id) {TextButton({details=row},enabled=!s.busy,modifier=Modifier.testTag("channels-details-"+row.id)) {Text(label(Words.PluginUpdateShowDetails))}}
                val ready=!s.busy && !pending && HostChannelPolicy.id(row.id)
                item(key="edit:"+row.id) {TextButton({edit=row},enabled=ready,modifier=Modifier.testTag("channels-edit-"+row.id)) {Text(label(Words.ChannelsEdit))};if(!HostChannelPolicy.id(row.id)) Text(label(Words.ChannelsUnsupported))}
                item(key="posture:"+row.id) {TextButton({review=vm.prepareHostChannel(inventory,row.id)},enabled=ready,modifier=Modifier.testTag("channels-posture-"+row.id)) {Text(label(Words.ChannelsPosture))}}
            }
        }
        s.channelPosture?.takeIf {it.inventory.owner==vm.reviewTarget()}?.let {item {Text(label(Words.ChannelsReported));TextButton({report=true},enabled=!s.busy,modifier=Modifier.testTag("channels-report-open")) {Text(label(Words.ChannelsReport))}}}
    }},confirmButton={TextButton(close,Modifier.testTag("channels-close")) {Text(label(Words.Close))}})
    read?.let {frozen->AlertDialog(onDismissRequest={read=null},title={Text(label(Words.ChannelsRead))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("channels-read-review")) {item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor);Text(label(Words.ChannelsPrivacy))}}},confirmButton={TextButton({read=null;vm.loadHostChannels(frozen)},enabled=!s.busy,modifier=Modifier.testTag("channels-read-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({read=null}) {Text(label(Words.Cancel))}})}
    if(inventory!=null) edit?.let {row->ChannelEditor(inventory,row,!s.busy,{edit=null}) {draft->review=vm.prepareHostChannel(inventory,row.id,draft);if(review!=null) edit=null}}
    details?.let {ChannelDetails(it) {details=null}}
    review?.let {frozen->ChannelReview(frozen,!s.busy,{review=null}) {review=null;vm.changeHostChannel(frozen)}}
    if(report) s.channelPosture?.takeIf {it.inventory.owner==vm.reviewTarget()}?.let {ChannelPostureReport(it) {report=false}}
}
@Composable private fun ChannelDetails(row:HostChannelRow,close:()->Unit) {
    var show by remember(row) {mutableStateOf(false)};var docsFailed by remember(row) {mutableStateOf(false)};val uri=LocalUriHandler.current
    AlertDialog(onDismissRequest=close,title={Text(row.name)},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("channels-details"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(label(Words.ChannelsPrivate))};item {TextButton({show=!show},Modifier.testTag("channels-details-reveal")) {Text(label(if(show) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
        if(show) {
            item {Text(row.id);Text(row.state);if(row.errorPresent) Text(label(Words.ChannelsErrorPresent));Text(label(if(row.gatewayRunning) Words.ChannelsGatewayRunning else Words.ChannelsStopped))}
            if(row.docsUrl.isNotBlank()) {item {Text(row.docsUrl)};item {TextButton({docsFailed=runCatching {uri.openUri(row.docsUrl)}.isFailure},enabled=HostChannelPolicy.browser(row.docsUrl),modifier=Modifier.testTag("channels-docs")) {Text(label(Words.ChannelsDocs))};if(!HostChannelPolicy.browser(row.docsUrl)) Text(label(Words.ChannelsUnsupported));if(docsFailed) Text(label(Words.ChannelsDocsFailed))}}
            if(row.ingressUrl.isNotBlank()) item {Text(label(Words.ChannelsIngress));Text(row.ingressUrl)}
            row.home?.let {home->item {Text(label(Words.ChannelsHome))};items(home.entries.toList()) {entry->Text(label(channelHomeLabel(entry.key))+" · "+entry.value.jsonPrimitive.content)}}
            items(row.fields,key={it.key}) {field->Column {Text(field.prompt.ifBlank {field.key});Text(field.key);Text(field.description);Text(field.help);Text(label(if(field.isSet) Words.HostEnvSet else Words.HostEnvUnset));if(field.required) Text(label(Words.ChannelsRequired));field.savedList?.let {Text(it)}}}
        }
    }},confirmButton={TextButton(close) {Text(label(Words.Close))}})
}
private enum class ChannelChoice {Keep,Replace,Clear}
@Composable private fun ChannelEditor(inventory:HostChannelInventory,row:HostChannelRow,enabled:Boolean,close:()->Unit,review:(HostChannelDraft)->Unit) {
    var show by remember(row) {mutableStateOf(false)};var choice by remember(row) {mutableStateOf<Boolean?>(null)}
    val actions=remember(row) {mutableStateMapOf<String,ChannelChoice>()};val values=remember(row) {mutableStateMapOf<String,String>()}
    val draft=runCatching {HostChannelDraft(choice,actions.filterValues {it==ChannelChoice.Replace}.keys.associateWith {values[it].orEmpty()},actions.filterValues {it==ChannelChoice.Clear}.keys.toList())}.getOrNull()
    val valid=draft!=null && runCatching {HostChannelRequest(inventory,row.id,draft)}.isSuccess
    val keyboard=LocalSoftwareKeyboardController.current
    AlertDialog(onDismissRequest=close,title={Text(label(Words.ChannelsEdit))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("channels-editor"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(row.name);Text(label(Words.ChannelsPrivate))};item {TextButton({show=!show},Modifier.testTag("channels-editor-reveal")) {Text(label(if(show) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
        if(show) {
            item {Text(label(Words.ChannelsEnableChoice));for(option in listOf<Boolean?>(null,true,false)) TextButton({choice=option},Modifier.testTag("channels-enable-"+option)) {Text((if(choice==option) "✓ " else "")+label(if(option==null) Words.ChannelsKeep else if(option) Words.Enabled else Words.Disabled))};if(row.relayFronted || row.relayDisabled || row.relayExclusive) Text(label(Words.ChannelsRelay))}
            for(field in row.fields) {
                item(key="field:"+field.key) {Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(field.prompt.ifBlank {field.key});Text(field.key);Text(field.description);if(field.help.isNotBlank()) Text(field.help);Text(label(if(field.isSet) Words.HostEnvSet else Words.HostEnvUnset));if(field.required) Text(label(Words.ChannelsRequired))}}
                item(key="choice:"+field.key) {Row(Modifier.fillMaxWidth()) {for(option in ChannelChoice.entries) TextButton({if(option==ChannelChoice.Keep) {actions.remove(field.key);values.remove(field.key)} else {actions[field.key]=option;values[field.key]=if(option==ChannelChoice.Replace && field.list) field.savedList.orEmpty() else ""}},enabled=option==ChannelChoice.Keep || HostEnvPolicy.writable(field.key),modifier=Modifier.weight(1f).testTag("channels-field-"+field.key+"-"+option.name)) {Text((if((actions[field.key] ?: ChannelChoice.Keep)==option) "✓ " else "")+label(when(option) {ChannelChoice.Keep->Words.ChannelsKeep;ChannelChoice.Replace->Words.ChannelsReplace;ChannelChoice.Clear->Words.ChannelsClear}))}};if(!HostEnvPolicy.writable(field.key)) Text(label(Words.ChannelsUnsupported))}
                if(actions[field.key]==ChannelChoice.Replace) item(key="value:"+field.key) {OutlinedTextField(values[field.key].orEmpty(),{if(it.length<=16384) values[field.key]=it},Modifier.fillMaxWidth().testTag("channels-value-"+field.key),label={Text(label(Words.HostEnvFreshValue))},singleLine=true,keyboardOptions=KeyboardOptions(autoCorrectEnabled=false,keyboardType=if(field.password) KeyboardType.Password else KeyboardType.Text),visualTransformation=if(field.password) PasswordVisualTransformation() else VisualTransformation.None)}
            }
            item {Text(label(Words.ChannelsLimits));if(!valid) Text(label(Words.ChannelsInvalid),Modifier.testTag("channels-invalid"))}
        }
    }},confirmButton={TextButton({keyboard?.hide();draft?.let(review)},enabled=enabled && show && valid,modifier=Modifier.testTag("channels-review-open")) {Text(label(Words.HostEnvReview))}},dismissButton={TextButton(close,Modifier.testTag("channels-editor-cancel")) {Text(label(Words.Cancel))}})
}
@Composable private fun ChannelReview(review:ReviewedChannel,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    val request=review.request;val draft=request.draft;var show by remember(review) {mutableStateOf(false)};var approved by remember(review) {mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(label(if(draft==null) Words.ChannelsPosture else Words.ChannelsSave))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("channels-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor);Text(request.row.name);Text(request.id)}
        item {Text(label(if(draft==null) Words.ChannelsPostureEffect else Words.ChannelsSaveEffect));Text(label(Words.ChannelsConcurrency));if(request.row.relayFronted || request.row.relayDisabled || request.row.relayExclusive) Text(label(Words.ChannelsRelay))}
        item {Text(label(Words.ChannelsPrivate))};item {TextButton({show=!show},Modifier.testTag("channels-review-reveal")) {Text(label(if(show) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
        if(show && draft==null) {
            item {Text(request.row.state)}
            items(request.row.fields.filter {it.list && it.savedList!=null}) {field->Column {Text(field.prompt.ifBlank {field.key});Text(field.savedList.orEmpty())}}
        }
        if(show && draft!=null) {
            item {Text(label(Words.ChannelsEnableChoice));Text(label(draft.enabled?.let {if(it) Words.Enabled else Words.Disabled} ?: Words.ChannelsKeep))}
            items(draft.replacements.entries.toList(),key={"replace:"+it.key}) {entry->Column {Text(label(Words.ChannelsReplace)+" · "+entry.key);Text(entry.value,Modifier.testTag("channels-reviewed-value-"+entry.key))}}
            items(draft.clears,key={"clear:"+it}) {key->Text(label(Words.ChannelsClear)+" · "+key)}
        }
        item {Row {Checkbox(approved,{approved=it},modifier=Modifier.testTag("channels-approve"));Text(label(Words.ChannelsApprove),Modifier.weight(1f))}}
    }},confirmButton={TextButton(confirm,enabled=enabled && show && approved,modifier=Modifier.testTag("channels-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.testTag("channels-review-cancel")) {Text(label(Words.Cancel))}})
}
@Composable private fun ChannelPostureReport(report:HostChannelPosture,close:()->Unit) {
    var show by remember(report) {mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.ChannelsReport))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("channels-report"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(label(Words.ChannelsReported));Text(label(channelState(report.ack.state.orEmpty())));Text(label(Words.ChannelsPostureEffect))};item {TextButton({show=!show},Modifier.testTag("channels-report-reveal")) {Text(label(if(show) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
        if(show) {
            item {Text(report.id);Text(report.ack.state.orEmpty());Text(label(if(report.ack.ok) Words.ChannelsHostPositive else Words.ChannelsHostNegative))}
            report.details?.let {details->if(details.posture.isNotBlank()) item {Text(details.posture)};if(details.message.isNotBlank()) item {Text(details.message)}} ?: item {Text(label(Words.ChannelsDetailsUnavailable))}
        }
    }},confirmButton={TextButton(close,Modifier.testTag("channels-report-close")) {Text(label(Words.Close))}})
}
@Composable fun HostChannelReceipt(receipt:ManagementOperation) {
    val values=remember(receipt.resultSummary) {runCatching {Json.parseToJsonElement(receipt.resultSummary).jsonObject}.getOrDefault(JsonObject(emptyMap()))}
    Text(label(if(receipt.action=="ChannelPosture") Words.ChannelsReported else Words.ChannelsReceiptAck));Text(label(Words.ChannelsConcurrency))
    if(values["hot_served"]==JsonPrimitive(true)) Text(label(Words.ChannelsHotServed))
    if(receipt.action=="ChannelPosture") Text(label(Words.ChannelsPostureEffect))
}
