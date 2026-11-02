package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.data.*
import kotlinx.serialization.json.*

@Composable fun RuntimePicker(vm: HermesViewModel,dismiss: ()->Unit) {
    val profile by vm.profileScope.collectAsStateWithLifecycle()
    val discovery by vm.discovery.collectAsStateWithLifecycle()
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val sending by vm.controlBusy.collectAsStateWithLifecycle()
    val payload=discovery.results[DiscoveryResource.Models]
    val inventory=remember(payload) {payload?.let(ModelInventory::parse)}
    var choice by remember(profile) {mutableStateOf(RuntimeChoice.saved(vm.preferences.value.runtimeChoices,profile))}
    var manual by remember(profile) {mutableStateOf(false)}
    var modelMenu by remember {mutableStateOf(false)}
    var providerMenu by remember {mutableStateOf(false)}
    var reasoningMenu by remember {mutableStateOf(false)}
    var search by remember {mutableStateOf("")}
    val advertised=caps?.has("model_options")==true || caps?.endpoints?.containsKey("model_options")==true
    val provider=inventory?.providers?.firstOrNull {it.slug==choice.provider}
    val permitted=manual || (choice.model.isBlank() && choice.provider.isBlank()) || (provider!=null && provider.authenticated!=false && choice.model in provider.models && choice.model !in provider.unavailableModels)
    val valid=runCatching {choice.validate()}.isSuccess && permitted && (advertised || choice.isDefault)
    LaunchedEffect(profile) {vm.refreshDiscovery(DiscoveryResource.Models)}
    AlertDialog(onDismissRequest=dismiss,title={Text(label(Words.Models))},text={
        LazyColumn(Modifier.testTag("runtime-picker-list").heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(label(Words.RuntimePrecedence),style=MaterialTheme.typography.bodySmall)}
            item {Row {
                TextButton({choice=RuntimeChoice()},enabled=!sending) {Text(label(Words.ServerDefault))}
                IconButton({vm.refreshDiscovery(DiscoveryResource.Models,true)},enabled=advertised && DiscoveryResource.Models !in discovery.loading) {Icon(Icons.Outlined.Refresh,label(Words.Refresh))}
            }}
            if(!advertised) item {Text(label(Words.NotAdvertised))}
            item {Row {Text(label(Words.ManualModel),Modifier.weight(1f));Switch(manual,{manual=it},modifier=Modifier.testTag("runtime-manual"),enabled=advertised && !sending)}}
            if(manual) {
                item {Text(label(Words.ManualModelInfo),style=MaterialTheme.typography.bodySmall)}
                item {OutlinedTextField(choice.model,{choice=choice.copy(model=it)},Modifier.testTag("runtime-model"),label={Text(label(Words.ModelId))},singleLine=true,enabled=advertised && !sending)}
                item {OutlinedTextField(choice.provider,{choice=choice.copy(provider=it)},Modifier.testTag("runtime-provider"),label={Text(label(Words.Provider))},singleLine=true,enabled=advertised && !sending)}
            } else {
                item {TextButton({providerMenu=true},enabled=advertised && !sending) {Text(provider?.name ?: choice.provider.ifBlank {label(Words.Provider)})}}
                if(provider?.authenticated==false) item {Text(label(Words.ProviderUnavailable))}
                item {Text(label(Words.RequestedModel)+": "+choice.model)}
                item {TextButton({modelMenu=true},enabled=provider!=null && !sending) {Text(label(Words.ShowModels))}}
                discovery.errors[DiscoveryResource.Models]?.let {item {Text(it,color=MaterialTheme.colorScheme.error)}}
                if(DiscoveryResource.Models in discovery.loading) item {QuietProgress()}
            }
            item {TextButton({reasoningMenu=true},enabled=advertised && !sending) {Text(label(Words.ReasoningSetting)+": "+label(when(choice.reasoning) {true->Words.Enabled;false->Words.Disabled;null->Words.ServerDefault}))}}
            item {OutlinedTextField(choice.effort,{choice=choice.copy(effort=it,reasoning=if(it.isNotBlank()) true else choice.reasoning)},Modifier.testTag("runtime-effort"),label={Text(label(Words.ReasoningEffort))},singleLine=true,enabled=advertised && choice.reasoning!=false && !sending)}
            item {OutlinedTextField(choice.serviceTier,{choice=choice.copy(serviceTier=it)},Modifier.testTag("runtime-tier"),label={Text(label(Words.ServiceTier))},singleLine=true,enabled=advertised && !sending)}
        }
    },confirmButton={TextButton({vm.chooseRuntime(profile,choice);dismiss()},enabled=valid && !sending) {Text(label(Words.ApplySettings))}},dismissButton={TextButton(dismiss) {Text(label(Words.Cancel))}})
    if(providerMenu) AlertDialog(onDismissRequest={providerMenu=false},title={Text(label(Words.Provider))},text={LazyColumn(Modifier.heightIn(max=400.dp)) {
        items(inventory?.providers.orEmpty(),key={it.slug}) {p -> TextButton({choice=choice.copy(provider=p.slug,model=choice.model.takeIf {it in p.models}.orEmpty());providerMenu=false;search=""},enabled=p.authenticated!=false && p.models.isNotEmpty()) {Text(p.name+" · "+p.slug)};if(p.authenticated==false) Text(label(Words.ProviderUnavailable),style=MaterialTheme.typography.labelSmall)}
    }},confirmButton={TextButton({providerMenu=false}) {Text(label(Words.Close))}})
    if(modelMenu && provider!=null) AlertDialog(onDismissRequest={modelMenu=false},title={Text(provider.name)},text={Column(Modifier.heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(search,{search=it},label={Text(label(Words.Search))},singleLine=true)
        LazyColumn(Modifier.weight(1f,fill=false)) {
            items(provider.models.filter {it.contains(search,true)},key={it}) {model ->
                val available=provider.authenticated!=false && model !in provider.unavailableModels
                TextButton({choice=choice.copy(model=model);modelMenu=false},Modifier.testTag("choose-model-$model"),enabled=available) {Text(model)}
                val price=provider.pricing[model] as? JsonObject
                if(price!=null) {
                    if(price.text("input").isNotBlank()) Text(label(Words.InputPrice)+": "+price.text("input"),style=MaterialTheme.typography.labelSmall)
                    if(price.text("output").isNotBlank()) Text(label(Words.OutputPrice)+": "+price.text("output"),style=MaterialTheme.typography.labelSmall)
                }
                if(!available) Text(label(Words.ModelUnavailable),style=MaterialTheme.typography.labelSmall)
            }
        }
    }},confirmButton={TextButton({modelMenu=false}) {Text(label(Words.Close))}})
    if(reasoningMenu) AlertDialog(onDismissRequest={reasoningMenu=false},title={Text(label(Words.ReasoningSetting))},text={Column {
        listOf(null,true,false).forEach {value -> TextButton({choice=choice.copy(reasoning=value,effort=if(value==false) "" else choice.effort);reasoningMenu=false}) {Text(label(when(value) {null->Words.ServerDefault;true->Words.Enabled;false->Words.Disabled}))}}
    }},confirmButton={TextButton({reasoningMenu=false}) {Text(label(Words.Close))}})
}
