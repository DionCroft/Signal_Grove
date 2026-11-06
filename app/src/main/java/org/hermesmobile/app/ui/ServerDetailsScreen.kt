package org.hermesmobile.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun resourceWord(resource: DiscoveryResource)=when(resource) {
    DiscoveryResource.Readiness->Words.Readiness;DiscoveryResource.Models->Words.Models;DiscoveryResource.RuntimeModels->Words.ServingModels
    DiscoveryResource.Skills->Words.Skills;DiscoveryResource.Toolsets->Words.Toolsets
}
@Composable fun ServerDetailsScreen(vm: HermesViewModel,close: ()->Unit) {
    val profile by vm.profileScope.collectAsStateWithLifecycle()
    val capabilities by vm.capabilities.collectAsStateWithLifecycle()
    val state by vm.discovery.collectAsStateWithLifecycle()
    var info by rememberSaveable {mutableStateOf(false)}
    var tab by rememberSaveable {mutableStateOf("capabilities")}
    val resource=DiscoveryResource.entries.firstOrNull {it.name==tab}
    LaunchedEffect(profile,resource) {resource?.let {vm.refreshDiscovery(it)}}
    val payload=resource?.let {state.results[it]}
    val expandedProviders=remember(payload) {mutableStateMapOf<String,Boolean>()}
    LazyColumn(Modifier.testTag("server-details-list").fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {Row(Modifier.fillMaxWidth()) {
            Word(Words.ServerDetails,Modifier.weight(1f))
            IconButton({info=true}) {Icon(Icons.Outlined.Info,label(Words.MoreInformation))}
            IconButton(close) {Icon(Icons.Outlined.Close,label(Words.Close))}
        }}
        item {Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(tab=="capabilities",{tab="capabilities"},label={Text(label(Words.Capabilities))})
            DiscoveryResource.entries.forEach {r -> FilterChip(resource==r,{tab=r.name},modifier=Modifier.testTag("discovery-tab-${r.name}"),label={Text(label(resourceWord(r)))})}
        }}
        if(resource==null) {
            item {Text(capabilities?.model.orEmpty(),Modifier.testTag("capability-list"),style=MaterialTheme.typography.titleMedium)}
            capabilities?.features?.entries?.sortedBy {it.key}?.forEach {(key,value) -> item(key="feature:$key") {Text("$key: $value")} }
            capabilities?.endpoints?.entries?.sortedBy {it.key}?.forEach {(key,value) -> item(key="endpoint:$key") {HermesCard {Text(key);val endpoint=value as? JsonObject;Text(endpoint?.let {it.text("method")+" "+it.text("path")} ?: value.toString(),style=MaterialTheme.typography.bodySmall)}} }
        } else if(capabilities?.supportsDiscovery(resource)!=true) item {Text(label(Words.NotAdvertised))}
        else {
            item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                IconButton({vm.refreshDiscovery(resource,true)},enabled=resource !in state.loading) {Icon(Icons.Outlined.Refresh,label(Words.Refresh))}
                if(resource==DiscoveryResource.Models) TextButton({vm.refreshDiscovery(resource,true,true)},enabled=resource !in state.loading) {Text(label(Words.ProbeModels))}
            }}
            if(resource in state.loading) item {QuietProgress()}
            state.errors[resource]?.let {item {Text(it,color=MaterialTheme.colorScheme.error)}}
            state.fetchedAt[resource]?.let {item {Text(label(Words.LastUpdated)+": "+DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it)),style=MaterialTheme.typography.labelSmall)}}
            if(payload!=null) discoveryItems(resource,payload,expandedProviders,state.fetchedAt[resource],state.errors.containsKey(resource))
        }
    }
    if(info) AlertDialog(onDismissRequest={info=false},title={Text(label(Words.ServerDetails))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {Text(label(Words.DiscoveryInfo));Text(label(Words.ProbeModelsInfo))}},confirmButton={TextButton({info=false}) {Text(label(Words.Close))}})
}
@Composable private fun Flag(word: Words,value: Boolean?) {Text(label(word)+": "+label(when(value) {true->Words.Yes;false->Words.No;null->Words.Unknown}),style=MaterialTheme.typography.bodySmall)}
private fun LazyListScope.discoveryItems(resource: DiscoveryResource,payload: JsonObject,expandedProviders: androidx.compose.runtime.snapshots.SnapshotStateMap<String,Boolean>,fetchedAt:Long?,failed:Boolean) {
    val inventory=ModelInventory.parse(payload)
    when(resource) {
        DiscoveryResource.Readiness -> {
            item {HermesCard {
                Word(Words.Readiness);Text(readinessStatus(payload),Modifier.testTag("readiness-status"),style=MaterialTheme.typography.titleLarge)
                listOf(Words.Version to "version",Words.GatewayState to "gateway_state",Words.ActiveAgents to "active_agents").forEach {(word,key) -> if(payload.text(key).isNotBlank()) Text(label(word)+": "+payload.text(key))}
                val readiness=payload["readiness"] as? JsonObject
                readiness?.entries?.filter {it.key!="status"}?.forEach {(key,value) -> Text("$key: "+value.toString().take(4000),style=MaterialTheme.typography.bodySmall)}
            }}
        }
        DiscoveryResource.RuntimeModels -> {
            item {RuntimeModelsPanel(payload,fetchedAt,failed,System.currentTimeMillis())}
        }
        DiscoveryResource.Models -> {
            item {Text(label(Words.ServerDefault)+": "+inventory.provider+" / "+inventory.model)}
            if(inventory.providers.isEmpty()) item {Text(label(Words.EmptyInventory))}
            inventory.providers.forEach {provider ->
                item(key="provider:${provider.slug}") {HermesCard {
                    Text(provider.name,style=MaterialTheme.typography.titleMedium);Text(provider.slug,style=MaterialTheme.typography.labelSmall)
                    Flag(Words.Authentication,provider.authenticated)
                    if(provider.warning.isNotBlank()) Text(provider.warning,style=MaterialTheme.typography.bodySmall)
                    TextButton({expandedProviders[provider.slug]=expandedProviders[provider.slug]!=true}) {Text(label(Words.ShowModels)+" (${provider.models.size})")}
                }}
                if(expandedProviders[provider.slug]==true) items(provider.models,key={"model:${provider.slug}:$it"}) {model -> HermesCard {
                    Text(model)
                    provider.capabilities[model]?.let {Text(it.toString().take(2000),style=MaterialTheme.typography.bodySmall)}
                    provider.pricing[model]?.let {Text(it.toString().take(2000),style=MaterialTheme.typography.bodySmall)}
                }}
            }
        }
        DiscoveryResource.Skills,DiscoveryResource.Toolsets -> {
            val rows=payload.items("data")
            if(rows.isEmpty()) item {Text(label(Words.EmptyInventory))}
            itemsIndexed(rows,key={index,row -> "${row.text("name")}:$index"}) {_,row -> HermesCard {
                Text(row.text("label").ifBlank {row.text("name")},style=MaterialTheme.typography.titleMedium)
                if(row.text("description").isNotBlank()) Text(row.text("description"))
                if(row.text("category").isNotBlank()) Text(row.text("category"),style=MaterialTheme.typography.labelSmall)
                if(resource==DiscoveryResource.Toolsets) {
                    Flag(Words.Enabled,(row["enabled"] as? JsonPrimitive)?.booleanOrNull);Flag(Words.Configured,(row["configured"] as? JsonPrimitive)?.booleanOrNull)
                    (row["tools"] as? JsonArray).orEmpty().forEach {tool -> (tool as? JsonPrimitive)?.contentOrNull?.let {Text(it,style=MaterialTheme.typography.bodySmall)}}
                } else if(row.containsKey("disabled")) Flag(Words.Enabled,(row["disabled"] as? JsonPrimitive)?.booleanOrNull?.not())
            }}
        }
    }
}
