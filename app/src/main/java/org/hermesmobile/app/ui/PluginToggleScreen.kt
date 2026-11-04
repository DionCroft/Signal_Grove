package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.data.*

@Composable fun PluginToggleControl(vm:ManagementViewModel,s:ManagementState,row:ManagedPlugin,review:(ReviewedPluginToggle)->Unit) {
    val receiptRows by vm.receipts.collectAsStateWithLifecycle();val receipts=vm.visibleReceipts(s,receiptRows)
    val pending=receipts.any {it.state in setOf("sending","unknown")}
    val supported=PluginToggleRequest.supported(row) && s.plugins.inventory?.let {!PluginToggleRequest.collision(row,it)}==true
    TextButton({vm.preparePluginToggle(row)?.let(review)},enabled=supported && !pending && !s.busy && s.identity!=null && vm.reviewTarget()!=null,modifier=Modifier.testTag("plugin-toggle-${row.key}")) {Text(label(if(row.status=="enabled") Words.PluginDisable else Words.PluginEnable))}
    if(!supported) Text(label(Words.PluginToggleUnsupported))
    if(pending) Text(label(Words.ManagementOutcomePending))
}
@Composable fun PluginToggleReview(review:ReviewedPluginToggle,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    val request=review.request;val row=request.expected
    AlertDialog(onDismissRequest=close,title={Text(label(if(request.enable) Words.PluginEnable else Words.PluginDisable))},text={
        LazyColumn(Modifier.heightIn(max=480.dp).testTag("plugin-toggle-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(review.target.url);Text(review.target.actor);Text(review.target.profile);Text(row.name);Text(row.key);Text(row.version);Text(row.source+" · "+row.status)}
            item {Text(label(Words.PluginConnectionEffect));Text(label(if(request.enable) Words.PluginEnableEffect else Words.PluginDisableEffect))}
            item {Text(label(Words.PluginToggleLimit))}
        }
    },confirmButton={TextButton(confirm,enabled=enabled,modifier=Modifier.testTag("plugin-toggle-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.testTag("plugin-toggle-cancel")) {Text(label(Words.Cancel))}})
}
