package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.*

fun hostAuxEffortWord(effort:HostAuxEffort)=when(effort) {
    HostAuxEffort.Keep->Words.HostAuxKeep;HostAuxEffort.Inherit->Words.HostAuxInherit;HostAuxEffort.None->Words.HostAuxNone
    HostAuxEffort.Minimal->Words.HostAuxMinimal;HostAuxEffort.Low->Words.HostAuxLow;HostAuxEffort.Medium->Words.HostAuxMedium;HostAuxEffort.High->Words.HostAuxHigh;HostAuxEffort.Xhigh->Words.HostAuxXhigh;HostAuxEffort.Max->Words.HostAuxMax;HostAuxEffort.Ultra->Words.HostAuxUltra
}
@Composable fun HostAuxModelEditor(inventory:HostModelInventory,task:String,close:()->Unit,review:(HostAuxModelRequest)->Unit) {
    val original=inventory.auxiliary.singleOrNull {it.task==task}
    var route by remember(inventory,task) {mutableStateOf(original?.route)}
    var keepRoute by remember(inventory,task) {mutableStateOf(original!=null)}
    var effort by remember(inventory,task) {mutableStateOf(HostAuxEffort.Keep)}
    var picking by remember {mutableStateOf(false)};var selected by remember {mutableStateOf<HostModelProvider?>(null)}
    val request=remember(inventory,task,route,effort,keepRoute) {route?.let {runCatching {HostAuxModelRequest.assign(inventory,task,it,effort,keepRoute)}.getOrNull()}}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostAuxEdit))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("host-aux-editor"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {Text(if(task.isEmpty()) label(Words.HostAuxAll) else task);Text(route?.provider.orEmpty());Text(route?.model.orEmpty());Text(label(Words.HostAuxRouteEffect));TextButton({picking=true},Modifier.testTag("host-aux-pick")) {Text(label(Words.HostModelsCatalog))};TextButton({route=HostModelRoute("auto","");keepRoute=false},Modifier.testTag("host-aux-auto")) {Text(label(Words.HostAuxAuto))}}
        item {Text(label(Words.HostModelsReasoning));Text(label(Words.HostAuxReasoningEffect))}
        items(HostAuxEffort.entries,key={it.name}) {value->Row {RadioButton(effort==value,{effort=value},Modifier.testTag("host-aux-effort-${value.name}"));Text(label(hostAuxEffortWord(value)),Modifier.weight(1f))}}
        if(request==null) item {Text(label(Words.ManagementInvalid))}
    }},confirmButton={TextButton({request?.let(review)},enabled=request!=null,modifier=Modifier.testTag("host-aux-editor-review")) {Text(label(Words.HostAuxReview))}},dismissButton={TextButton(close,Modifier.testTag("host-aux-editor-cancel")) {Text(label(Words.Cancel))}})
    if(picking) {
        var query by remember {mutableStateOf("")}
        AlertDialog(onDismissRequest={picking=false;selected=null},title={Text(label(Words.HostModelsCatalog))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("host-aux-catalog"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item {OutlinedTextField(query,{if(it.length<=256) query=it},Modifier.fillMaxWidth().testTag("host-aux-filter"),label={Text(label(Words.HostModelsFilter))},singleLine=true)}
            val row=selected
            if(row==null) items(inventory.providers.filter {query.isBlank() || it.slug.contains(query,true) || it.name.contains(query,true)},key={it.slug}) {p->TextButton({selected=p;query=""},Modifier.testTag("host-aux-provider-${p.slug}")) {Text(p.name.ifBlank {p.slug})}}
            else {
                item {Text(row.name);TextButton({selected=null;query=""}) {Text(label(Words.HostModelsProviders))}}
                items(row.models.filter {query.isBlank() || it.contains(query,true)},key={it}) {model->Column {
                    TextButton({route=HostModelRoute(row.slug,model);keepRoute=false;picking=false;selected=null},enabled=HostMainModelRequest.eligible(row,model),modifier=Modifier.testTag("host-aux-model-${row.slug}-$model")) {Text(model)}
                    if(model in row.unavailableModels || row.entitlementPending==true) Text(label(Words.ModelUnavailable)) else if(row.authenticated!=true) Text(label(Words.HostMainProviderUnready)) else if(!HostMainModelRequest.eligible(row,model)) Text(label(Words.ManagementInvalid))
                }}
                if(row.models.isEmpty()) item {Text(label(Words.EmptyInventory))}
            }
        }},confirmButton={TextButton({picking=false;selected=null},Modifier.testTag("host-aux-catalog-close")) {Text(label(Words.Close))}})
    }
}
@Composable fun HostAuxModelReview(review:ReviewedHostAuxModel,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    var approved by remember(review) {mutableStateOf(false)};val request=review.request
    AlertDialog(onDismissRequest=close,title={Text(label(if(request.warning!=null) Words.HostMainWarningTitle else if(request.reset) Words.HostAuxReset else Words.HostAuxReview))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("host-aux-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor);if(request.reset || request.task.isEmpty()) Text(label(Words.HostAuxAll));request.targets.forEach {Text(it)};if(!request.reset) {Text(request.route.provider);Text(request.route.model);Text(label(hostAuxEffortWord(request.effort)))}}
        item {Text(label(if(request.reset) Words.HostAuxResetEffect else Words.HostAuxRouteEffect));Text(label(Words.HostAuxReasoningEffect));if(request.reset || request.task.isEmpty()) Text(label(Words.HostAuxBulkEffect));Text(label(Words.HostAuxConcurrency))}
        request.warning?.let {warning->item {Text(label(Words.HostMainWarningEffect));Text(warning.message,Modifier.testTag("host-aux-warning-message"))}}
        item {Row {Checkbox(approved,{approved=it},modifier=Modifier.testTag("host-aux-approve"));Text(label(if(request.warning==null) Words.HostAuxApprove else Words.HostMainWarningApprove),Modifier.weight(1f))}}
    }},confirmButton={TextButton(confirm,enabled=enabled && approved,modifier=Modifier.testTag("host-aux-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.testTag("host-aux-cancel")) {Text(label(Words.Cancel))}})
}
