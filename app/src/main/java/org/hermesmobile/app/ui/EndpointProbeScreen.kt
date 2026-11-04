package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

@Composable fun EndpointProbeReview(review:ReviewedCustomEndpointProbe,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    val request=review.request;var show by remember(review) {mutableStateOf(false)};var approved by remember(review) {mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.EndpointProbe))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("endpoint-probe-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor)}
        item {Text(label(Words.EndpointProbeEffect));Text(label(Words.EndpointProbeScope));Text(label(Words.EndpointProbeCredentials));Text(label(Words.EndpointProbeMeaning))}
        item {TextButton({show=!show},Modifier.testTag("endpoint-probe-reveal")) {Text(label(if(show) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
        if(show) {item {Text(request.name);Text(request.baseUrl);Text(request.model);Text(request.apiMode.ifEmpty {label(Words.HostFallbackAuto)});Text(label(if(request.fresh) Words.CustomEndpointsFreshKey else Words.EndpointProbeNoKey))};if(request.fresh) item {OutlinedTextField(request.secret,{},Modifier.fillMaxWidth().testTag("endpoint-probe-reviewed-secret"),readOnly=true,singleLine=true,label={Text(label(Words.CredentialPoolFresh))})}}
        item {Row {Checkbox(approved,{approved=it},modifier=Modifier.testTag("endpoint-probe-approve"));Text(label(Words.EndpointProbeApprove),Modifier.weight(1f))}}
    }},confirmButton={TextButton(confirm,enabled=enabled && show && approved,modifier=Modifier.testTag("endpoint-probe-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.testTag("endpoint-probe-review-cancel")) {Text(label(Words.Cancel))}})
}
@Composable fun EndpointProbeReport(report:CustomEndpointProbeReport,plan:((CustomEndpointProbeAdoptionKind,String?)->CustomEndpointProbeAdoption?)?=null,stage:((CustomEndpointProbeAdoption)->Unit)?=null,enabled:Boolean=true,close:()->Unit) {
    var show by remember(report) {mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.EndpointProbeReport))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("endpoint-probe-report"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {EndpointProbeReceipt(report.receipt);Text(label(Words.EndpointProbeMeaning));Text(label(Words.EndpointProbeNoSave))}
        item {TextButton({show=!show},Modifier.testTag("endpoint-probe-report-reveal")) {Text(label(if(show) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
        if(show) {
            item {Text(report.request.baseUrl);Text(report.request.model)}
            val details=report.details
            if(details==null) item {Text(label(Words.EndpointProbeDetailsUnavailable))}
            else {
                if(details.message.isNotEmpty()) item {Text(details.message)}
                if(plan!=null) item {Text(label(Words.EndpointStageOnly));if(!enabled) Text(label(Words.ManagementOutcomePending))}
                details.resolvedBaseUrl?.let {value->
                    item {Text(label(Words.EndpointProbeResolved));Text(value)}
                    if(plan!=null) item {EndpointStageButton(Words.EndpointStageUrl,"endpoint-probe-stage-url",plan(CustomEndpointProbeAdoptionKind.Url,null),enabled,stage)}
                }
                details.transport?.let {value->
                    item {Text(label(Words.HostFallbackMode));Text(value)}
                    if(plan!=null) item {EndpointStageButton(Words.EndpointStageMode,"endpoint-probe-stage-mode",plan(CustomEndpointProbeAdoptionKind.Mode,null),enabled,stage)}
                }
                if(plan!=null) item {EndpointStageButton(Words.EndpointStageCatalog,"endpoint-probe-stage-catalog",plan(CustomEndpointProbeAdoptionKind.Catalog,null),enabled,stage)}
                itemsIndexed(details.models) {index,id->Column {Text(id);if(plan!=null) EndpointStageButton(Words.EndpointStageModel,"endpoint-probe-stage-model-"+index,plan(CustomEndpointProbeAdoptionKind.Model,id),enabled,stage)}}
                itemsIndexed(details.details) {index,detail->Column {Text(detail.id);detail.canonicalModel?.let {Text(it)};detail.reasoningEffort?.let {Text(it)};if(plan!=null) EndpointStageButton(Words.EndpointStageModel,"endpoint-probe-stage-detail-"+index,plan(CustomEndpointProbeAdoptionKind.Model,detail.id),enabled,stage)}}
            }
        }
    }},confirmButton={TextButton(close,Modifier.testTag("endpoint-probe-report-close")) {Text(label(Words.Close))}})
}
@Composable private fun EndpointStageButton(word:Words,tag:String,plan:CustomEndpointProbeAdoption?,enabled:Boolean,stage:((CustomEndpointProbeAdoption)->Unit)?) {
    TextButton({plan?.let {stage?.invoke(it)}},enabled=enabled && plan?.changes==true && stage!=null,modifier=Modifier.testTag(tag)) {Text(label(word))}
    if(plan==null) Text(label(Words.EndpointStageUnavailable)) else if(!plan.changes) Text(label(Words.EndpointStageAlready))
}
@Composable fun EndpointProbeReceipt(receipt:ManagementOperation) {
    val summary=runCatching {Json.parseToJsonElement(receipt.resultSummary).jsonObject}.getOrNull()
    Text(label(Words.EndpointProbeReported))
    summary?.let {Text(label(Words.EndpointProbeOk)+": "+label(if(it["reported_ok"]==JsonPrimitive(true)) Words.Yes else Words.No));Text(label(Words.EndpointProbeReachable)+": "+label(if(it["reported_reachable"]==JsonPrimitive(true)) Words.Yes else Words.No))}
    Text(label(Words.EndpointProbeMeaning))
}
