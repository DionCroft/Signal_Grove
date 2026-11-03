package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.data.*

@Composable fun HostEnvironmentDialog(vm:ManagementViewModel,s:ManagementState,initialKey:String="",close:()->Unit) {
    var read by remember(s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedHostEnvRead?>(null)}
    var edit by remember(s.environment) {mutableStateOf<String?>(null)}
    var review by remember(s.environment) {mutableStateOf<ReviewedHostEnv?>(null)}
    var filter by remember(s.apiScope,s.url,s.profile,initialKey) {mutableStateOf(initialKey)}
    val receipts by vm.receipts.collectAsStateWithLifecycle();val pending=receipts.any {it.state in setOf("sending","unknown")}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostEnvironment))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("host-env-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(s.url);Text(s.profile);Text(label(Words.HostEnvPrivacy));TextButton({read=vm.prepareHostEnvRead()},enabled=!s.busy && s.identity!=null && GatewayCronInventory.supportedProfile(s.profile),modifier=Modifier.testTag("host-env-read")) {Text(label(Words.HostEnvRead))};if(!GatewayCronInventory.supportedProfile(s.profile)) Text(label(Words.PluginProfileUnsupported))}
            if(s.busy) item {QuietProgress();TextButton(vm::cancel) {Text(label(Words.Cancel))}}
            s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}}
            s.notice?.let {item {Text(label(it))}}
            s.environment?.let {inventory->
                item {OutlinedTextField(filter,{if(it.length<=256) filter=it},Modifier.fillMaxWidth().testTag("host-env-filter"),label={Text(label(Words.HostEnvFilter))},singleLine=true);TextButton({edit=""},enabled=!s.busy && !pending,modifier=Modifier.testTag("host-env-add")) {Text(label(Words.HostEnvAdd))};if(pending) Text(label(Words.ManagementOutcomePending));if(inventory.rows.isEmpty()) Text(label(Words.EmptyInventory))}
                items(inventory.rows.filter {filter.isBlank() || it.key.contains(filter,true) || it.category.contains(filter,true) || it.providers.any {p->p.id.contains(filter,true) || p.label.contains(filter,true)}},key={it.key}) {row->Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(row.key,style=MaterialTheme.typography.titleMedium);Text(label(if(row.isSet) Words.HostEnvSet else Words.HostEnvUnset));Text(row.description);if(row.category.isNotBlank()) Text(row.category)
                    for(provider in row.providers) Text(provider.label+" · "+provider.id)
                    if(row.tools.isNotEmpty()) Text(row.tools.joinToString(" · "))
                    if(row.channel==true) Text(label(Words.HostEnvChannel))
                    TextButton({edit=row.key},enabled=!s.busy && !pending && HostEnvPolicy.writable(row.key),modifier=Modifier.testTag("host-env-edit-${row.key}")) {Text(label(Words.HostEnvSave))}
                    if(!HostEnvPolicy.writable(row.key)) Text(label(Words.HostEnvProtected))
                    TextButton({review=vm.prepareHostEnv(row.key,HostEnvAction.Remove)},enabled=!s.busy && !pending && HostEnvPolicy.key(row.key),modifier=Modifier.testTag("host-env-remove-${row.key}")) {Text(label(Words.HostEnvRemove))}
                    if(!HostEnvPolicy.key(row.key)) Text(label(Words.HostEnvInvalid))
                }}
            }
        }
    },confirmButton={TextButton(close,Modifier.testTag("host-env-close")) {Text(label(Words.Close))}})
    read?.let {frozen->AlertDialog(onDismissRequest={read=null},title={Text(label(Words.HostEnvRead))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("host-env-read-review")) {item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor);Text(label(Words.HostEnvPrivacy))}}},confirmButton={TextButton({read=null;vm.loadHostEnvironment(frozen)},enabled=!s.busy,modifier=Modifier.testTag("host-env-read-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({read=null},Modifier.testTag("host-env-read-cancel")) {Text(label(Words.Cancel))}})}
    edit?.let {key->HostEnvEditor(key,initialKey,!s.busy,{edit=null}) {name,value->review=vm.prepareHostEnv(name,HostEnvAction.Save,value);if(review!=null) edit=null}}
    review?.let {frozen->HostEnvReview(frozen,!s.busy,{review=null}) {review=null;vm.changeHostEnvironment(frozen)}}
}
@Composable private fun HostEnvEditor(existing:String,initialKey:String,enabled:Boolean,close:()->Unit,review:(String,String)->Unit) {
    var key by remember(existing) {mutableStateOf(existing.ifBlank {initialKey})}
    var value by remember(key) {mutableStateOf("")};var show by remember(key) {mutableStateOf(false)}
    val valid=HostEnvPolicy.writable(key) && HostEnvPolicy.value(value)
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostEnvSave))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("host-env-editor"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {OutlinedTextField(key,{if(existing.isBlank() && it.length<=256) key=it},Modifier.fillMaxWidth().testTag("host-env-key"),label={Text(label(Words.HostEnvKey))},readOnly=existing.isNotBlank(),singleLine=true)}
        item {Text(label(Words.HostEnvFreshValue));OutlinedTextField(value,{if(it.length<=16384) value=it},Modifier.fillMaxWidth().testTag("host-env-value"),label={Text(label(Words.PluginSettingsValue))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false),visualTransformation=if(show) VisualTransformation.None else PasswordVisualTransformation());TextButton({show=!show},Modifier.testTag("host-env-editor-show")) {Text(label(if(show) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
        if(!valid) item {Text(label(Words.HostEnvInvalid))}
    }},confirmButton={TextButton({review(key,value)},enabled=enabled && valid,modifier=Modifier.testTag("host-env-review")) {Text(label(Words.HostEnvReview))}},dismissButton={TextButton(close,Modifier.testTag("host-env-editor-cancel")) {Text(label(Words.Cancel))}})
}
@Composable private fun HostEnvReview(review:ReviewedHostEnv,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    var show by remember(review) {mutableStateOf(false)};var approved by remember(review) {mutableStateOf(false)};val request=review.request
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostEnvReview))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("host-env-confirm-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor);Text(request.key);Text(label(if(request.action==HostEnvAction.Save) Words.HostEnvSave else Words.HostEnvRemove))}
        item {Text(label(if(request.action==HostEnvAction.Save) Words.HostEnvSaveEffect else Words.HostEnvRemoveEffect));Text(label(Words.HostEnvConcurrency))}
        request.previous?.let {row->item {Text(label(if(row.isSet) Words.HostEnvSet else Words.HostEnvUnset));row.providers.forEach {Text(it.label+" · "+it.id)};if(row.channel==true) Text(label(Words.HostEnvChannel))}}
        if(request.action==HostEnvAction.Save) {item {TextButton({show=!show},Modifier.testTag("host-env-review-show")) {Text(label(if(show) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}};if(show) item {Text(request.value)}}
        item {Row {Checkbox(approved,{approved=it},modifier=Modifier.testTag("host-env-approve"));Text(label(Words.HostEnvApprove),Modifier.weight(1f))}}
    }},confirmButton={TextButton(confirm,enabled=enabled && approved && (request.action==HostEnvAction.Remove || show),modifier=Modifier.testTag("host-env-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.testTag("host-env-cancel")) {Text(label(Words.Cancel))}})
}
