package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.*

@Composable fun PluginCatalogDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    var name by remember(s.apiScope,s.url,s.profile) {mutableStateOf("")}
    var repository by remember(s.apiScope,s.url,s.profile) {mutableStateOf("")}
    var reveal by remember(s.apiScope,s.url,s.profile) {mutableStateOf(false)}
    var review by remember(s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedPluginSource?>(null)}
    val enabled=!s.busy && s.identity!=null && GatewayCronInventory.supportedProfile(s.profile)
    fun prepare(request:PluginSourceRead) {review=vm.preparePluginSource(request)}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.PluginCatalog))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("plugin-catalog-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(s.url);Text(s.profile);Text(label(Words.PluginCatalogBoundary));TextButton({prepare(PluginSourceRead(PluginSourceReadAction.Featured))},enabled=enabled,modifier=Modifier.testTag("plugin-featured-read")) {Text(label(Words.PluginFeatured))}}
            if(s.busy) item {QuietProgress();TextButton(vm::cancel) {Text(label(Words.Cancel))}}
            item {OutlinedTextField(name,{name=it;vm.clearPluginSourceResults()},Modifier.fillMaxWidth().testTag("plugin-catalog-name"),label={Text(label(Words.PluginCatalogName))},singleLine=true,enabled=enabled)
                TextButton({prepare(PluginSourceRead(PluginSourceReadAction.Lookup,PluginSource(PluginSourceKind.Catalog,name.trim())))},enabled=enabled && PluginSource.valid(PluginSourceKind.Catalog,name.trim()),modifier=Modifier.testTag("plugin-catalog-lookup")) {Text(label(Words.PluginLookup))}}
            s.plugins.lookup?.let {lookup ->
                item {Text(lookup.name);if(lookup.entry==null) Text(label(when(lookup.status) {"unknown"->Words.PluginCatalogUnknown;"invalid_name"->Words.PluginCatalogInvalidName;else->Words.PluginCatalogUnavailable}))}
                lookup.entry?.let {entry ->item {
                    Text(entry.name+" · "+entry.tier);Text(entry.displayRepo(reveal));Text(entry.subdir);Text(entry.sha.ifBlank {label(Words.Unknown)})
                    TextButton({reveal=!reveal}) {Text(label(if(reveal) Words.McpHideQuery else Words.McpShowQuery))}
                    TextButton({prepare(PluginSourceRead(PluginSourceReadAction.Inspect,PluginSource(PluginSourceKind.Catalog,entry.name),entry))},enabled=enabled,modifier=Modifier.testTag("plugin-inspect-catalog")) {Text(label(Words.PluginInspect))}
                }}
            }
            item {OutlinedTextField(repository,{repository=it;vm.clearPluginSourceResults()},Modifier.fillMaxWidth().testTag("plugin-repository"),label={Text(label(Words.PluginRepository))},singleLine=true,enabled=enabled)
                val valid=PluginSource.valid(PluginSourceKind.Repository,repository.trim())
                if(repository.isNotBlank() && !valid) Text(label(Words.PluginSourceInvalid))
                TextButton({prepare(PluginSourceRead(PluginSourceReadAction.Inspect,PluginSource(PluginSourceKind.Repository,repository.trim())))},enabled=enabled && valid,modifier=Modifier.testTag("plugin-inspect-repository")) {Text(label(Words.PluginInspect))}}
            s.plugins.featured?.let {featured ->
                item {Text(label(Words.PluginFeatured),style=MaterialTheme.typography.titleSmall);if(featured.isEmpty()) Text(label(Words.EmptyInventory))}
                itemsIndexed(featured,key={_,row->row.name}) {index,row ->Column {
                    Text(row.title.ifBlank {row.name});Text(row.description);Text(row.tier+" · "+row.platforms.joinToString(" · "));Text(row.appState);Text(row.sentence)
                    TextButton({prepare(PluginSourceRead(PluginSourceReadAction.Lookup,PluginSource(PluginSourceKind.Catalog,row.name)))},enabled=enabled,modifier=Modifier.testTag("plugin-featured-$index")) {Text(label(Words.PluginLookup))}
                }}
            }
            s.plugins.inspection?.let {result ->item {
                Text(result.source.display(reveal));TextButton({reveal=!reveal}) {Text(label(if(reveal) Words.McpHideQuery else Words.McpShowQuery))}
                if(!result.ok) Text(label(Words.PluginInspectionRejected))
                else {Text(label(Words.PluginInspectionPin));Text(result.sha,Modifier.testTag("plugin-inspection-sha"));Text(label(if(result.agent==true) Words.PluginAgentComponent else Words.PluginNoAgentComponent));Text(result.agentName);Text(label(if(result.desktop==true) Words.PluginDesktopComponent else Words.PluginNoDesktopComponent));Text(result.desktopName)}
                if(result.insecure==true) Text(label(Words.PluginSourceInsecure))
                result.warnings.forEach {Text(it)}
            }}
            s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}}
        }
    },confirmButton={TextButton(close,Modifier.testTag("plugin-catalog-close")) {Text(label(Words.Close))}})
    review?.let {frozen ->
        var query by remember(frozen) {mutableStateOf(false)}
        AlertDialog(onDismissRequest={review=null},title={Text(label(if(frozen.request.action==PluginSourceReadAction.Inspect) Words.PluginInspect else Words.PluginLookup))},text={
            LazyColumn(Modifier.heightIn(max=480.dp).testTag("plugin-source-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor)}
                frozen.request.source?.let {source ->item {Text(source.display(query));if(source.display(true)!=source.display(false)) TextButton({query=!query}) {Text(label(if(query) Words.McpHideQuery else Words.McpShowQuery))}}}
                frozen.request.expected?.let {entry ->item {Text(entry.sha);Text(entry.subdir)}}
                item {Text(label(Words.GatewayConnectionEffect))}
                item {Text(label(if(frozen.request.action==PluginSourceReadAction.Inspect) Words.PluginInspectionEffect else Words.PluginCatalogBoundary))}
            }
        },confirmButton={TextButton({review=null;vm.readPluginSource(frozen)},enabled=enabled,modifier=Modifier.testTag("plugin-source-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({review=null}) {Text(label(Words.Cancel))}})
    }
}
