package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

@Composable fun PluginRemovalControl(vm:ManagementViewModel,s:ManagementState,row:ManagedPlugin,review:(ReviewedPluginRemoval)->Unit) {
    val receiptRows by vm.receipts.collectAsStateWithLifecycle();val receipts=vm.visibleReceipts(s,receiptRows)
    val pending=receipts.any {it.state in setOf("sending","unknown")}
    val supported=PluginRemovalRequest.supported(row) && s.plugins.inventory?.let {!PluginRemovalRequest.collision(row,it)}==true
    TextButton({vm.preparePluginRemoval(row)?.let(review)},enabled=supported && !s.busy && !pending && s.identity!=null && vm.reviewTarget()!=null,modifier=Modifier.testTag("plugin-remove-${row.key}")) {Text(label(Words.PluginRemove))}
    if(!supported) Text(label(Words.PluginRemoveUnsupported))
    if(pending) Text(label(Words.ManagementOutcomePending))
}
@Composable fun PluginRemovalReview(review:ReviewedPluginRemoval,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    val row=review.request.expected
    AlertDialog(onDismissRequest=close,title={Text(label(Words.PluginRemove))},text={
        LazyColumn(Modifier.heightIn(max=480.dp).testTag("plugin-remove-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(review.target.url);Text(review.target.actor);Text(review.target.profile);Text(row.name);Text(row.key);Text(row.version);Text(row.source+" · "+row.status);Text(label(Words.PluginInstallDirectory));Text(review.request.directory.name)}
            item {Text(label(Words.PluginConnectionEffect));Text(label(Words.PluginRemoveEffect))}
            item {Text(label(Words.PluginRemoveLimit))}
        }
    },confirmButton={TextButton(confirm,enabled=enabled,modifier=Modifier.testTag("plugin-remove-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.testTag("plugin-remove-cancel")) {Text(label(Words.Cancel))}})
}
@Composable fun PluginRemovalReceipt(parsed:JsonObject) {
    Text(label(Words.PluginRemoveReported));Text(label(Words.PluginInstallDirectory));Text(parsed.text("install_name"))
    Text(label(Words.PluginMemoryProviderCleared)+": "+label(when(parsed["memory_provider_cleared"]) {JsonPrimitive(true)->Words.PluginReportedYes;JsonPrimitive(false)->Words.PluginReportedNo;else->Words.Unknown}))
    Text(label(Words.PluginRemoveObservation))
}
