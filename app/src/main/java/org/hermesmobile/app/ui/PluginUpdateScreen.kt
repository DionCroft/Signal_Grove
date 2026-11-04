package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

private fun sensitiveUpdateText(s:String)=s.contains("://") || s.contains('\\') || Regex("(?:^|[\\s:])/|(?i)(?:token|password|secret)\\s*[:=]").containsMatchIn(s)
private fun sensitiveFinding(f:PluginUpdateFinding)=f.category.contains("credential",true) || sensitiveUpdateText(f.file) || sensitiveUpdateText(f.description)
private fun hiddenUpdateDetails(d:PluginUpdateDecision)=d.delta.values.flatten().any(::sensitiveUpdateText) || d.findings.any(::sensitiveFinding)
@Composable fun PluginUpdateControl(vm:ManagementViewModel,s:ManagementState,row:ManagedPlugin,review:(ReviewedPluginUpdate)->Unit) {
    val receiptRows by vm.receipts.collectAsStateWithLifecycle();val receipts=vm.visibleReceipts(s,receiptRows);val pending=receipts.any {it.state in setOf("sending","unknown")}
    val supported=PluginUpdateRequest.supported(row) && s.plugins.inventory?.let {!PluginRemovalRequest.collision(row,it)}==true
    TextButton({vm.preparePluginUpdate(row)?.let(review)},enabled=supported && !s.busy && !pending && s.identity!=null && vm.reviewTarget()!=null,modifier=Modifier.testTag("plugin-update-${row.key}")) {Text(label(Words.PluginUpdate))}
    if(!supported) Text(label(Words.PluginUpdateUnsupported))
    if(pending) Text(label(Words.ManagementOutcomePending))
}
private fun LazyListScope.updateDecisionItems(d:PluginUpdateDecision,show:Boolean) {
    item {Text(label(if(d.kind==PluginUpdateKind.Capabilities) Words.PluginUpdateCapabilities else Words.PluginUpdateScan));if(d.pin.isNotBlank()) {Text(label(Words.PluginUpdateProposalPin));Text(d.pin)};d.catalog?.let {Text(it.name);Text(it.displayRepo(show));Text(it.subdir)}}
    d.delta.forEach {(surface,values)->item {Text(surface.replace('_',' '))};items(values) {value->Text(if(!show && sensitiveUpdateText(value)) label(Words.PluginUpdateHiddenDetails) else value)}}
    if(d.verdict.isNotBlank()) item {Text(d.verdict)}
    items(d.findings) {f->Text(listOf(f.pattern,f.severity,f.category).joinToString(" · "));if(!show && sensitiveFinding(f)) Text(label(Words.PluginUpdateHiddenDetails)) else {if(f.file.isNotBlank()) Text(f.file+(f.line?.let {":$it"}.orEmpty()));Text(f.description)}}
}
@Composable fun PluginUpdateDecisionView(vm:ManagementViewModel,s:ManagementState,d:PluginUpdateDecision,review:(ReviewedPluginUpdate)->Unit) {
    val receiptRows by vm.receipts.collectAsStateWithLifecycle();val receipts=vm.visibleReceipts(s,receiptRows);val pending=receipts.any {it.state in setOf("sending","unknown")}
    Column(Modifier.testTag("plugin-update-decision"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label(if(d.kind==PluginUpdateKind.Capabilities) Words.PluginUpdateCapabilities else Words.PluginUpdateScan))
        if(d.pin.isNotBlank()) Text(d.pin)
        d.delta.forEach {(surface,values)->Text(surface.replace('_',' ')+": "+values.size)}
        if(d.verdict.isNotBlank()) Text(d.verdict+" · "+d.findings.size)
        Text(label(Words.PluginUpdateNoPublication))
        TextButton({vm.preparePluginUpdate(d.expected,d)?.let(review)},enabled=!s.busy && !pending && d.eligible(),modifier=Modifier.testTag("plugin-update-review-decision")) {Text(label(Words.PluginUpdateReviewDecision))}
        if(pending) Text(label(Words.ManagementOutcomePending))
        if(!d.eligible()) Text(label(when {d.kind==PluginUpdateKind.Scan && d.verdict=="dangerous"->Words.PluginUpdateDangerous;d.kind==PluginUpdateKind.Scan && d.expected.catalog.isNotBlank()->Words.PluginUpdateCatalogScanLimit;else->Words.PluginUpdateDecisionUnavailable}))
    }
}
@Composable fun PluginUpdateReview(review:ReviewedPluginUpdate,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    val request=review.request;val row=request.expected;val d=request.decision
    var details by remember(review) {mutableStateOf(false)};var approved by remember(review) {mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.PluginUpdate))},text={
        LazyColumn(Modifier.heightIn(max=480.dp).testTag("plugin-update-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor);Text(row.name);Text(row.key);Text(row.version);Text(request.directory.name);if(row.catalog.isNotBlank()) {Text(row.catalog);Text(label(Words.PluginUpdateProposalPin));Text(row.catalogSha)}}
            item {Text(label(Words.PluginConnectionEffect));Text(label(Words.PluginUpdateEffect))}
            item {Text(label(Words.PluginUpdateLimit))}
            if(d!=null) {
                updateDecisionItems(d,details)
                if(hiddenUpdateDetails(d)) item {TextButton({details=!details},Modifier.testTag("plugin-update-review-show-details")) {Text(label(if(details) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
                item {Text(label(if(request.acceptCapabilities) Words.PluginUpdateCapabilityGrant else Words.PluginUpdateCautionGrant));Row {Checkbox(approved,{approved=it},modifier=Modifier.testTag("plugin-update-grant"));Text(label(Words.PluginUpdateApproveNext),Modifier.weight(1f))}}
            }
        }
    },confirmButton={TextButton(confirm,enabled=enabled && (d==null || d.eligible() && approved && (!hiddenUpdateDetails(d) || details)),modifier=Modifier.testTag("plugin-update-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.testTag("plugin-update-cancel")) {Text(label(Words.Cancel))}})
}
@Composable fun PluginUpdateReceipt(parsed:JsonObject) {
    val outcome=parsed.text("outcome")
    Text(label(when(outcome) {"Capabilities"->Words.PluginUpdateCapabilities;"Scan"->Words.PluginUpdateScan;else->Words.PluginUpdateResult}))
    if(parsed["pin_changed_during_update"]==JsonPrimitive(true)) Text(label(Words.PluginUpdateMoved))
    Text(parsed.text("reported_name"));if(parsed.text("reported_pin").isNotBlank()) {Text(label(Words.PluginUpdateReportedPin));Text(parsed.text("reported_pin"))}
    if(outcome!="Updated") Text(label(Words.PluginUpdateNoPublication)) else {Text(label(Words.PluginUpdateUnchanged)+": "+label(when(parsed["unchanged"]) {JsonPrimitive(true)->Words.PluginReportedYes;JsonPrimitive(false)->Words.PluginReportedNo;else->Words.Unknown}));Text(label(Words.PluginUpdateObservation))}
}
