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

private fun effortWord(value:String)=when(value) {
    "minimal"->Words.RuntimeEffortMinimal;"low"->Words.RuntimeEffortLow;"medium"->Words.RuntimeEffortMedium
    "high"->Words.RuntimeEffortHigh;"xhigh"->Words.RuntimeEffortXHigh;"max"->Words.RuntimeEffortMax
    "ultra"->Words.RuntimeEffortUltra;else->Words.ServerDefault
}
private fun tierWord(value:String)=when(value) {
    "priority"->Words.RuntimeTierPriority;"ultrafast"->Words.RuntimeTierUltra
    "daybreak"->Words.RuntimeTierDaybreak;else->Words.ServerDefault
}

@Composable fun RuntimePicker(vm: HermesViewModel,dismiss: ()->Unit) {
    val profile by vm.profileScope.collectAsStateWithLifecycle()
    val discovery by vm.discovery.collectAsStateWithLifecycle()
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val sending by vm.controlBusy.collectAsStateWithLifecycle()
    val payload=discovery.results[DiscoveryResource.Models]
    val inventory=remember(payload) {payload?.let(ModelInventory::parse)}
    var choice by remember(profile) {mutableStateOf(RuntimeChoice.saved(vm.preferences.value.runtimeChoices,profile))}
    var manual by remember(profile) {mutableStateOf(false)}
    var providerMenu by remember(profile) {mutableStateOf(false)}
    var modelMenu by remember(profile) {mutableStateOf(false)}
    var reasoningMenu by remember(profile) {mutableStateOf(false)}
    var effortMenu by remember(profile) {mutableStateOf(false)}
    var tierMenu by remember(profile) {mutableStateOf(false)}
    var search by remember(profile) {mutableStateOf("")}
    val advertised=caps?.has("model_options")==true || caps?.endpoints?.containsKey("model_options")==true
    val automaticSupported=caps?.supportsDiscovery(DiscoveryResource.RuntimeModels)==true
    val observed=discovery.results[DiscoveryResource.RuntimeModels]?.let {runCatching {ModelRuntimeStatus.parse(it)}.getOrNull()}
    val serving=observed?.takeIf {it.status=="ready" && !modelObservationCached(it.observedAt,
        discovery.fetchedAt[DiscoveryResource.RuntimeModels],System.currentTimeMillis(),discovery.errors.containsKey(DiscoveryResource.RuntimeModels))}?.verifiedModels?.singleOrNull()
    val provider=inventory?.providers?.singleOrNull {it.slug==choice.provider}
    val optionModel=if(choice.followServing && automaticSupported) serving?.model.orEmpty() else choice.model.ifBlank {inventory?.model.orEmpty()}
    val optionProvider=if(choice.followServing && automaticSupported) serving?.provider.orEmpty() else choice.provider.ifBlank {inventory?.provider.orEmpty()}
    val presets=RuntimePresets.forModel(inventory,optionModel,optionProvider)
    val permitted=choice.followServing || manual || (choice.model.isBlank() && choice.provider.isBlank()) ||
        (provider!=null && provider.authenticated!=false && choice.model in provider.models && choice.model !in provider.unavailableModels)
    val valid=runCatching {choice.validate()}.isSuccess && permitted && presets.permits(choice) &&
        (advertised || choice.isDefault || choice.followServing && choice.copy(followServing=false).isDefault)
    LaunchedEffect(profile) {vm.refreshModelStatus(force=true)}
    AlertDialog(onDismissRequest=dismiss,title={Text(label(Words.Models))},text={
        LazyColumn(Modifier.testTag("runtime-picker-list").heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(label(Words.RuntimePrecedence),style=MaterialTheme.typography.bodySmall)}
            item {
                TextButton({choice=RuntimeChoice.automatic();manual=false},enabled=!sending,modifier=Modifier.heightIn(min=48.dp).testTag("runtime-follow-serving")) {Text(label(Words.FollowServing))}
                Text(label(if(automaticSupported) Words.FollowServingInfo else Words.FollowServingLegacy),style=MaterialTheme.typography.bodySmall)
            }
            item {Row {
                TextButton({choice=RuntimeChoice()},enabled=!sending,modifier=Modifier.heightIn(min=48.dp).testTag("runtime-server-default")) {Text(label(Words.ServerDefault))}
                IconButton({vm.refreshModelStatus(force=true)},enabled=!sending && DiscoveryResource.Models !in discovery.loading) {Icon(Icons.Outlined.Refresh,label(Words.Refresh))}
            }}
            if(!advertised) item {Text(label(Words.NotAdvertised))}
            item {Row {Text(label(Words.ManualModel),Modifier.weight(1f));Switch(manual,{
                manual=it;if(it && choice.followServing) choice=RuntimeChoice()
            },modifier=Modifier.testTag("runtime-manual"),enabled=advertised && !sending)}}
            if(manual) {
                item {Text(label(Words.ManualModelInfo),style=MaterialTheme.typography.bodySmall)}
                item {OutlinedTextField(choice.model,{choice=choice.copy(model=it,followServing=false)},Modifier.testTag("runtime-model"),label={Text(label(Words.ModelId))},singleLine=true,enabled=advertised && !sending)}
                item {OutlinedTextField(choice.provider,{choice=choice.copy(provider=it,followServing=false)},Modifier.testTag("runtime-provider"),label={Text(label(Words.Provider))},singleLine=true,enabled=advertised && !sending)}
            } else {
                item {TextButton({providerMenu=true},enabled=advertised && !sending) {Text(provider?.name ?: choice.provider.ifBlank {label(Words.Provider)})}}
                if(provider?.authenticated==false) item {Text(label(Words.ProviderUnavailable))}
                item {Text(label(Words.RequestedModel)+": "+if(choice.followServing) label(Words.FollowServing) else choice.model.ifBlank {label(Words.ServerDefault)})}
                item {TextButton({modelMenu=true},enabled=provider!=null && !sending) {Text(label(Words.ShowModels))}}
                discovery.errors[DiscoveryResource.Models]?.let {item {Text(it,color=MaterialTheme.colorScheme.error)}}
                if(DiscoveryResource.Models in discovery.loading) item {QuietProgress()}
            }
            item {Text(label(Words.RuntimePresetInfo),style=MaterialTheme.typography.bodySmall)}
            if(!presets.permits(choice)) item {Text(label(Words.RuntimePresetUnknown),color=MaterialTheme.colorScheme.error)}
            item {
                TextButton({reasoningMenu=true},enabled=advertised && !sending,modifier=Modifier.heightIn(min=48.dp).testTag("runtime-reasoning")) {
                    Text(label(Words.ReasoningSetting)+": "+label(when(choice.reasoning) {true->Words.Enabled;false->Words.Disabled;null->Words.ServerDefault}))
                }
                if(!presets.reasoning) Text(label(Words.RuntimeNoReasoningChoices),style=MaterialTheme.typography.bodySmall)
            }
            item {TextButton({effortMenu=true},enabled=advertised && choice.reasoning!=false && !sending,modifier=Modifier.heightIn(min=48.dp).testTag("runtime-effort")) {
                Text(label(Words.ReasoningEffort)+": "+if(choice.effort.isBlank() || choice.effort in RuntimePresets.effortLadder) label(effortWord(choice.effort)) else choice.effort)
            }}
            item {
                TextButton({tierMenu=true},enabled=advertised && !sending,modifier=Modifier.heightIn(min=48.dp).testTag("runtime-tier")) {
                    Text(label(Words.ServiceTier)+": "+if(choice.serviceTier.isBlank() || choice.serviceTier in listOf("priority","ultrafast","daybreak")) label(tierWord(choice.serviceTier)) else choice.serviceTier)
                }
                if(presets.tiers.isEmpty()) Text(label(Words.RuntimeNoServiceChoices),style=MaterialTheme.typography.bodySmall)
            }
        }
    },confirmButton={TextButton({vm.chooseRuntime(profile,choice);dismiss()},enabled=valid && !sending,modifier=Modifier.heightIn(min=48.dp).testTag("runtime-apply")) {Text(label(Words.ApplySettings))}},
        dismissButton={TextButton(dismiss,modifier=Modifier.heightIn(min=48.dp)) {Text(label(Words.Cancel))}})
    if(providerMenu) AlertDialog(onDismissRequest={providerMenu=false},title={Text(label(Words.Provider))},text={LazyColumn(Modifier.heightIn(max=400.dp)) {
        items(inventory?.providers.orEmpty(),key={it.slug}) {p->
            TextButton({choice=choice.copy(provider=p.slug,model=choice.model.takeIf {it in p.models}.orEmpty(),followServing=false);providerMenu=false;search=""},enabled=p.authenticated!=false && p.models.isNotEmpty()) {Text(p.name+" · "+p.slug)}
            if(p.authenticated==false) Text(label(Words.ProviderUnavailable),style=MaterialTheme.typography.labelSmall)
        }
    }},confirmButton={TextButton({providerMenu=false}) {Text(label(Words.Close))}})
    if(modelMenu && provider!=null) AlertDialog(onDismissRequest={modelMenu=false},title={Text(provider.name)},text={Column(Modifier.heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(search,{search=it},label={Text(label(Words.Search))},singleLine=true)
        LazyColumn(Modifier.weight(1f,fill=false)) {
            items(provider.models.filter {it.contains(search,true)},key={it}) {model->
                val available=provider.authenticated!=false && model !in provider.unavailableModels
                TextButton({choice=choice.copy(model=model,followServing=false);modelMenu=false},Modifier.testTag("choose-model-"+model),enabled=available) {Text(model)}
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
        listOf(null,true,false).forEach {value->
            TextButton({choice=choice.copy(reasoning=value,effort=if(value==false) "" else choice.effort);reasoningMenu=false},
                enabled=value==null || value==true && presets.reasoning || value==false && presets.canDisable,
                modifier=Modifier.testTag("runtime-reasoning-"+(value?.toString() ?: "default"))) {
                Text(label(when(value) {null->Words.ServerDefault;true->Words.Enabled;false->Words.Disabled}))
            }
        }
    }},confirmButton={TextButton({reasoningMenu=false}) {Text(label(Words.Close))}})
    if(effortMenu) AlertDialog(onDismissRequest={effortMenu=false},title={Text(label(Words.ReasoningEffort))},text={
        LazyColumn(Modifier.heightIn(max=400.dp).testTag("runtime-effort-options")) {
            items(listOf("")+presets.efforts,key={it}) {value->
                TextButton({choice=choice.copy(effort=value,reasoning=if(value.isNotBlank()) true else choice.reasoning);effortMenu=false},
                    modifier=Modifier.heightIn(min=48.dp).testTag("runtime-effort-"+value.ifBlank {"default"})) {Text(label(effortWord(value)))}
            }
        }
    },confirmButton={TextButton({effortMenu=false}) {Text(label(Words.Close))}})
    if(tierMenu) AlertDialog(onDismissRequest={tierMenu=false},title={Text(label(Words.ServiceTier))},text={
        LazyColumn(Modifier.heightIn(max=400.dp).testTag("runtime-tier-options")) {
            items(listOf("")+presets.tiers,key={it}) {value->
                TextButton({choice=choice.copy(serviceTier=value);tierMenu=false},
                    modifier=Modifier.heightIn(min=48.dp).testTag("runtime-tier-"+value.ifBlank {"default"})) {Text(label(tierWord(value)))}
            }
        }
    },confirmButton={TextButton({tierMenu=false}) {Text(label(Words.Close))}})
}
