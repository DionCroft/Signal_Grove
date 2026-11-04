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
@Composable fun EndpointProbeReport(report:CustomEndpointProbeReport,close:()->Unit) {
    var show by remember(report) {mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.EndpointProbeReport))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("endpoint-probe-report"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {EndpointProbeReceipt(report.receipt);Text(label(Words.EndpointProbeMeaning));Text(label(Words.EndpointProbeNoSave))}
        item {TextButton({show=!show},Modifier.testTag("endpoint-probe-report-reveal")) {Text(label(if(show) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
        if(show) {
            item {Text(report.request.baseUrl);Text(report.request.model)}
            val details=report.details
            if(details==null) item {Text(label(Words.EndpointProbeDetailsUnavailable))}
            else {item {if(details.message.isNotEmpty()) Text(details.message);details.resolvedBaseUrl?.let {Text(label(Words.EndpointProbeResolved));Text(it)};details.transport?.let {Text(label(Words.HostFallbackMode));Text(it)}};items(details.models) {Text(it)};items(details.details) {Text(it.id);it.canonicalModel?.let {v->Text(v)};it.reasoningEffort?.let {v->Text(v)}}}
        }
    }},confirmButton={TextButton(close,Modifier.testTag("endpoint-probe-report-close")) {Text(label(Words.Close))}})
}
@Composable fun EndpointProbeReceipt(receipt:ManagementOperation) {
    val summary=runCatching {Json.parseToJsonElement(receipt.resultSummary).jsonObject}.getOrNull()
    Text(label(Words.EndpointProbeReported))
    summary?.let {Text(label(Words.EndpointProbeOk)+": "+label(if(it["reported_ok"]==JsonPrimitive(true)) Words.Yes else Words.No));Text(label(Words.EndpointProbeReachable)+": "+label(if(it["reported_reachable"]==JsonPrimitive(true)) Words.Yes else Words.No))}
    Text(label(Words.EndpointProbeMeaning))
}
