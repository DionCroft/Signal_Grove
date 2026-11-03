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

@Composable fun HostModelsDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    var read by remember(s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedHostModelsRead?>(null)}
    var provider by remember(s.hostModels) {mutableStateOf<HostModelProvider?>(null)}
    var filter by remember(s.apiScope,s.url,s.profile) {mutableStateOf("")}
    val supported=GatewayCronInventory.supportedProfile(s.profile)
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostModels))},text={LazyColumn(Modifier.heightIn(max=520.dp).testTag("host-models-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(s.url);Text(s.profile);Text(label(Words.HostModelsBoundary));TextButton({read=vm.prepareHostModelsRead()},enabled=!s.busy && s.identity!=null && supported,modifier=Modifier.testTag("host-models-read")) {Text(label(Words.HostModelsRead))};if(!supported) Text(label(Words.PluginProfileUnsupported))}
        if(s.busy) item {QuietProgress();TextButton(vm::cancel) {Text(label(Words.Cancel))}}
        s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}}
        s.hostModels?.let {inventory->
            item {Text(label(Words.HostModelsMain),style=MaterialTheme.typography.titleMedium);ModelRoute(inventory.main);Text(label(Words.HostModelsSnapshot));TextButton({read=vm.prepareHostModelsRead(true)},enabled=!s.busy,modifier=Modifier.testTag("host-models-refresh")) {Text(label(Words.HostModelsRefresh))};OutlinedTextField(filter,{if(it.length<=256) filter=it},Modifier.fillMaxWidth().testTag("host-models-filter"),label={Text(label(Words.HostModelsFilter))},singleLine=true)}
            item {Text(label(Words.HostModelsAuxiliary),style=MaterialTheme.typography.titleMedium)}
            items(inventory.auxiliary.filter {filter.isBlank() || listOf(it.task,it.label,it.plugin,it.route.provider,it.route.model).any {v->v.contains(filter,true)}},key={"task:${it.task}"}) {row->Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(row.label.ifBlank {row.task},style=MaterialTheme.typography.titleSmall);Text(row.task);ModelRoute(row.route)
                if(row.plugin.isNotBlank()) Text(row.plugin)
                if(row.hint.isNotBlank()) Text(row.hint)
                Text(label(Words.HostModelsReasoning)+" · "+(row.reasoning ?: label(Words.HostModelsInherited)))
                Text(label(if(row.endpointPresent) Words.HostModelsEndpoint else Words.HostModelsNoEndpoint));row.localEndpoint?.let {if(it) Text(label(Words.HostModelsLocal))}
                row.inheritFrom?.let {Text(label(Words.HostModelsInheritFrom)+" · "+it);row.effective?.let {route->Text(label(Words.HostModelsEffective));ModelRoute(route)}}
            }}
            item {Text(label(Words.HostModelsProviders),style=MaterialTheme.typography.titleMedium);if(inventory.providers.isEmpty()) Text(label(Words.EmptyInventory))}
            items(inventory.providers.filter {filter.isBlank() || it.slug.contains(filter,true) || it.name.contains(filter,true) || it.models.any {m->m.contains(filter,true)}},key={"provider:${it.slug}"}) {row->Column {
                Text(row.name.ifBlank {row.slug},style=MaterialTheme.typography.titleSmall);Text(row.slug)
                Text(label(Words.HostModelsReportedAuth)+" · "+label(when(row.authenticated) {true->Words.HostModelsAuthYes;false->Words.HostModelsAuthNo;null->Words.HostModelsAuthUnknown}))
                if(row.warningPresent) Text(label(Words.HostModelsWarning))
                TextButton({provider=row},Modifier.testTag("host-models-provider-${row.slug}")) {Text(label(Words.HostModelsCatalog)+" · "+row.models.size)}
            }}
        }
    }},confirmButton={TextButton(close,Modifier.testTag("host-models-close")) {Text(label(Words.Close))}})
    read?.let {frozen->AlertDialog(onDismissRequest={read=null},title={Text(label(if(frozen.refresh) Words.HostModelsRefresh else Words.HostModelsRead))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("host-models-read-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor);Text(label(Words.HostModelsReadEffect));if(frozen.refresh) Text(label(Words.HostModelsRefreshEffect))}}},confirmButton={TextButton({read=null;vm.loadHostModels(frozen)},enabled=!s.busy,modifier=Modifier.testTag("host-models-read-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({read=null},Modifier.testTag("host-models-read-cancel")) {Text(label(Words.Cancel))}})}
    provider?.let {row->var query by remember(row) {mutableStateOf("")};AlertDialog(onDismissRequest={provider=null},title={Text(row.name.ifBlank {row.slug})},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("host-models-catalog"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {Text(row.slug);if(row.source.isNotBlank()) Text(row.source);Text(label(Words.HostModelsSnapshot));OutlinedTextField(query,{if(it.length<=256) query=it},Modifier.fillMaxWidth().testTag("host-models-catalog-filter"),label={Text(label(Words.HostModelsFilter))},singleLine=true)}
        items(row.models.filter {query.isBlank() || it.contains(query,true)},key={it}) {Text(it)}
        if(row.models.isEmpty()) item {Text(label(Words.EmptyInventory))}
    }},confirmButton={TextButton({provider=null},Modifier.testTag("host-models-catalog-close")) {Text(label(Words.Close))}})}
}
@Composable private fun ModelRoute(route:HostModelRoute) {Text(route.provider.ifBlank {label(Words.HostModelsUnspecified)});Text(route.model.ifBlank {label(Words.HostModelsUnspecified)})}
