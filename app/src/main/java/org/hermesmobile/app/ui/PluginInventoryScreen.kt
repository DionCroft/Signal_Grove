package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.*

@Composable fun PluginInventoryDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    var catalog by remember(s.apiScope,s.url,s.profile) {mutableStateOf(false)}
    var review by remember(s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedPluginRead?>(null)}
    var removal by remember(s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedPluginRemoval?>(null)}
    var toggle by remember(s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedPluginToggle?>(null)}
    var details by remember(s.plugins.inventory) {mutableStateOf<ManagedPlugin?>(null)}
    val supported=GatewayCronInventory.supportedProfile(s.profile)
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostPlugins))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("plugins-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(s.url);Text(s.profile);Text(label(Words.PluginInventoryBoundary));TextButton({review=vm.preparePluginRead()},enabled=!s.busy && s.identity!=null && supported,modifier=Modifier.testTag("plugins-read")) {Text(label(Words.PluginRead))};if(!supported) Text(label(Words.PluginProfileUnsupported))}
            item {TextButton({catalog=true},enabled=!s.busy && s.identity!=null && supported,modifier=Modifier.testTag("plugin-catalog-open")) {Text(label(Words.PluginCatalog))}}
            if(s.busy) item {QuietProgress();TextButton(vm::cancel) {Text(label(Words.Cancel))}}
            s.plugins.inventory?.let {inventory ->
                item {Text(label(Words.PluginUserCount)+" "+inventory.userCount);Text(label(Words.PluginBundledCount)+" "+inventory.bundledCount);if(inventory.plugins.isEmpty()) Text(label(Words.EmptyInventory))}
                itemsIndexed(inventory.plugins,key={_,row->row.key}) {index,row ->Column {Text(row.name,style=MaterialTheme.typography.titleMedium);Text(row.version+" · "+row.source);Text(row.status);TextButton({details=row},modifier=Modifier.testTag("plugin-details-$index")) {Text(label(Words.PluginDetails))}}}
            }
            s.plugins.toggleReport?.let {item {PluginActivationView(it,Words.PluginToggleReported)}}
            s.notice?.let {item {Text(label(it))}}
            s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}}
        }
    },confirmButton={TextButton(close,Modifier.testTag("plugins-close")) {Text(label(Words.Close))}})
    removal?.let {frozen->PluginRemovalReview(frozen,!s.busy,{removal=null}) {removal=null;vm.removePlugin(frozen)}}
    toggle?.let {frozen->PluginToggleReview(frozen,!s.busy,{toggle=null}) {toggle=null;vm.togglePlugin(frozen)}}
    if(catalog) PluginCatalogDialog(vm,s) {vm.cancel();catalog=false}
    review?.let {frozen ->AlertDialog(onDismissRequest={review=null},title={Text(label(Words.PluginRead))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("plugins-connection-review")) {item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor);Text(label(Words.PluginConnectionEffect));Text(label(Words.PluginInventoryBoundary))}}},confirmButton={TextButton({review=null;vm.loadPlugins(frozen)},enabled=!s.busy,modifier=Modifier.testTag("plugins-read-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({review=null}) {Text(label(Words.Cancel))}})}
    details?.let {row ->AlertDialog(onDismissRequest={details=null},title={Text(row.name)},text={
        LazyColumn(Modifier.heightIn(max=500.dp).testTag("plugin-detail-view"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(row.key);Text(row.version);Text(row.description);Text(row.source+" · "+row.status);Text(label(Words.PluginInventoryBoundary))}
            for(value in listOf(row.catalog,row.tier,row.installedSha,row.catalogSha,row.pinnedSha)) if(value.isNotBlank()) item {Text(value)}
            if(row.updateAvailable!=null) item {Text(label(if(row.updateAvailable) Words.PluginUpdateReported else Words.PluginNoUpdateReported))}
            item {PluginToggleControl(vm,s,row) {toggle=it}}
            item {PluginRemovalControl(vm,s,row) {removal=it}}
            item {Text(label(Words.PluginServers))};items(row.servers,key={it.name}) {server->Column {Text(server.name+" · "+server.state);Text(server.sentence)}}
            item {Text(label(Words.PluginTools))};items(row.tools) {Text(it)}
        }
    },confirmButton={TextButton({details=null},Modifier.testTag("plugin-detail-close")) {Text(label(Words.Close))}})}
}
