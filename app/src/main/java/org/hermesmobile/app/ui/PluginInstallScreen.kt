package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

@Composable fun PluginInstallControls(vm:ManagementViewModel,s:ManagementState,result:PluginInspection,review:(ReviewedPluginInstall)->Unit) {
    var enable by remember(result) {mutableStateOf(false)}
    val receiptRows by vm.receipts.collectAsStateWithLifecycle();val receipts=vm.visibleReceipts(s,receiptRows)
    val pending=receipts.any {it.state in setOf("sending","unknown")}
    val catalog=if(result.source.kind==PluginSourceKind.Catalog) s.plugins.lookup?.entry else null
    val supported=PluginInstallRequest.supported(result,catalog)
    val enabled=!s.busy && !pending && s.identity!=null && vm.reviewTarget()!=null
    Column {
        Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(enable,{enable=it},enabled=enabled && supported,modifier=Modifier.testTag("plugin-install-enable"));Text(label(Words.PluginInstallEnable),Modifier.weight(1f))}
        TextButton({vm.preparePluginInstall(enable)?.let(review)},enabled=enabled && supported,modifier=Modifier.testTag("plugin-install-review")) {Text(label(Words.PluginInstall))}
        if(!supported) Text(label(Words.PluginInstallUnsupported))
        if(pending) Text(label(Words.ManagementOutcomePending))
        if(s.busy) {QuietProgress();TextButton(vm::cancel,Modifier.testTag("plugin-install-wait-cancel")) {Text(label(Words.Cancel))}}
    }
}
@Composable fun PluginInstallReview(review:ReviewedPluginInstall,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    var query by remember(review) {mutableStateOf(false)}
    val request=review.request
    AlertDialog(onDismissRequest=close,title={Text(label(Words.PluginInstall))},text={
        LazyColumn(Modifier.heightIn(max=480.dp).testTag("plugin-install-confirmation"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor);Text(request.name);Text(request.inspection.source.display(query));if(request.inspection.source.display(true)!=request.inspection.source.display(false)) TextButton({query=!query}) {Text(label(if(query) Words.McpHideQuery else Words.McpShowQuery))}}
            item {Text(label(Words.PluginApprovedCommit));Text(request.inspection.sha);Text(label(if(request.enable) Words.PluginEnableRequested else Words.PluginEnableNotRequested))}
            request.catalog?.let {entry->item {Text(entry.name);Text(entry.displayRepo(query));Text(entry.subdir);Text(entry.sha)}}
            item {Text(label(Words.PluginConnectionEffect));Text(label(Words.PluginInstallEffect))}
            if(request.enable) item {Text(label(Words.PluginInstallEnableEffect))}
            item {Text(label(Words.PluginInstallGuardEffect))}
        }
    },confirmButton={TextButton(confirm,enabled=enabled,modifier=Modifier.testTag("plugin-install-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.testTag("plugin-install-review-cancel")) {Text(label(Words.Cancel))}})
}
@Composable private fun reported(value:Boolean?)=label(when(value) {true->Words.PluginReportedYes;false->Words.PluginReportedNo;null->Words.Unknown})
@Composable fun PluginActivationView(report:PluginInstallReport,title:Words=Words.PluginInstallReported) {
    Column(Modifier.testTag("plugin-activation-report"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label(title),style=MaterialTheme.typography.titleSmall);Text(report.name)
        Text(label(Words.PluginRestartRequired)+": "+reported(report.restartRequired));Text(label(Words.PluginGatewayReloaded)+": "+reported(report.gatewayReloaded))
        Text(label(Words.PluginPythonDependencyCount)+": "+(report.dependencyCount?.toString() ?: label(Words.Unknown)))
        if(report.missingEnv.isNotEmpty()) {Text(label(Words.PluginMissingEnv));Text(report.missingEnv.joinToString("\n"))}
        if(report.activationReported) {
            Text(label(Words.PluginActivatedNow));report.activated.forEach {(kind,names)->Text(kind.replace('_',' '));Text(names.joinToString("\n"))}
            Text(label(Words.PluginDeferred));report.deferred.forEach {(kind,names)->Text(kind.replace('_',' '));Text(names.joinToString("\n"))}
            if(report.liveReported) {Text(label(Words.PluginReportedLive));report.servers.forEach {server->Text(server.name+" · "+reported(server.connected));Text(server.tools.joinToString("\n"));if(server.hasError) Text(label(Words.PluginHostDetails))};report.skills.forEach {Text(it)}}
            if((report.missingDependencyCount ?: 0)>0 || report.missingIn.isNotEmpty()) {Text(label(Words.PluginHostDetails));Text(report.missingIn.joinToString(" · "))}
        } else Text(label(Words.PluginActivationUnavailable))
    }
}
@Composable fun PluginInstallReceiptResult(parsed:JsonObject) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label(if(parsed["source_changed"]==JsonPrimitive(true)) Words.PluginInstallSourceChanged else Words.PluginInstallReported))
        Text(label(Words.PluginApprovedCommit));Text(parsed.text("approved_commit"))
        Text(label(if(parsed["requested_enable"]==JsonPrimitive(true)) Words.PluginEnableRequested else Words.PluginEnableNotRequested))
        if(parsed["details_available"]==JsonPrimitive(true)) {
            Text(label(Words.PluginRestartRequired)+": "+reported((parsed["restart_required"] as? JsonPrimitive)?.booleanOrNull));Text(label(Words.PluginGatewayReloaded)+": "+reported((parsed["gateway_reloaded"] as? JsonPrimitive)?.booleanOrNull))
            Text(label(Words.PluginPythonDependencyCount)+": "+parsed.text("python_dependency_count"));Text(label(Words.PluginDeferred));(parsed["deferred_counts"] as? JsonObject)?.forEach {(kind,count)->Text(kind.replace('_',' ')+": "+count.jsonPrimitive.content)}
        } else if(parsed["source_changed"]!=JsonPrimitive(true)) Text(label(Words.PluginInstallDetailsUnavailable))
    }
}
