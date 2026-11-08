package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun modelObservationWord(state:String)=when(state) {
    "ready"->Words.ModelObservedReady
    "offline"->Words.ModelEndpointOffline
    "unready","unavailable"->Words.ModelNotReady
    "ambiguous"->Words.ModelMultipleReady
    "not_configured"->Words.ModelObserverNotConfigured
    else->Words.ModelVerificationFailed
}
private fun observationTime(value:Long)=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))

@Composable private fun StatusRefresh(vm:HermesViewModel):Long {
    val owner=LocalLifecycleOwner.current
    val scope by vm.profileScope.collectAsStateWithLifecycle()
    val connection by vm.connectionStatus.collectAsStateWithLifecycle()
    var tick by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(owner,scope,connection) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while(isActive) {tick=System.currentTimeMillis();vm.refreshModelStatus(now=tick);delay(15_000)}
        }
    }
    return tick
}

@Composable internal fun RuntimeModelsPanel(payload:JsonObject?,fetchedAt:Long?,failed:Boolean,now:Long) {
    val parsed=remember(payload) {payload?.let {runCatching {ModelRuntimeStatus.parse(it)}.getOrNull()}}
    var expanded by remember(payload) {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.testTag("runtime-model-observations")) {
        Text(label(Words.ServingModels),style=MaterialTheme.typography.titleSmall)
        if(parsed==null) Text(label(if(payload==null) Words.ModelNotObserved else Words.ModelObservationInvalid),Modifier.testTag("model-runtime-state"))
        else {
            val visible=if(expanded) parsed.models else parsed.verifiedModels
            if(visible.isEmpty()) Text(label(Words.ModelNoVerifiedServing),Modifier.testTag("model-no-serving"))
            visible.forEach {model->Column(Modifier.fillMaxWidth().testTag("observed-model-${model.provider}")) {
                Text(model.model,style=MaterialTheme.typography.titleMedium)
                Text(if(model.verified && parsed.status=="ready") model.provider else model.provider+" · "+label(modelObservationWord(model.state)),style=MaterialTheme.typography.bodySmall)
            }}
            Text(label(modelObservationWord(parsed.status)),Modifier.testTag("model-runtime-state"))
            if(parsed.models.any {!it.verified}) TextButton({expanded=!expanded},Modifier.testTag("model-observation-details")) {Text(label(if(expanded) Words.ModelHideDetails else Words.ModelObservationDetails))}
        }
        if(payload!=null && modelObservationCached(parsed?.observedAt,fetchedAt,now,failed)) Text(label(Words.ModelCachedObservation),Modifier.testTag("model-observation-cached"),color=MaterialTheme.colorScheme.error)
        parsed?.let {Text(label(Words.ModelHostCheckedAt)+": "+observationTime(it.observedAt),Modifier.testTag("model-host-observed-at"),style=MaterialTheme.typography.labelSmall)}
        fetchedAt?.let {Text(label(Words.LastUpdated)+": "+observationTime(it),Modifier.testTag("model-observed-at"),style=MaterialTheme.typography.labelSmall)}
    }
}

@Composable internal fun ModelStatusCard(
    discovery:DiscoveryState,caps:HermesCapabilities?,connection:String,demo:Boolean,choice:RuntimeChoice,
    conversation:ConversationModelStatus?,now:Long,onRefresh:()->Unit,onChoose:()->Unit,choiceEnabled:Boolean=true,compactHeader:Boolean=false
) {
    val inventory=remember(discovery.results[DiscoveryResource.Models]) {discovery.results[DiscoveryResource.Models]?.let(ModelInventory::parse)}
    val loading=discovery.loading.any {it in setOf(DiscoveryResource.Readiness,DiscoveryResource.Models,DiscoveryResource.RuntimeModels)}
    val connected=connection in setOf("connected","demo")
    val connectionWord=if(demo) {if(compactHeader) Words.ModelDemoShort else Words.Demo} else if(connected) Words.ModelConnectionOnline else Words.ModelConnectionOffline
    HermesCard(Modifier.testTag("model-status-card")) {
        Row(Modifier.fillMaxWidth()) {
            if(compactHeader) Text(label(connectionWord),Modifier.weight(1f).testTag("model-connection-state"),style=MaterialTheme.typography.labelMedium)
            else Word(Words.ModelStatus,Modifier.weight(1f))
            IconButton(onRefresh,enabled=connected && !loading,modifier=Modifier.testTag("model-status-refresh")) {Icon(Icons.Outlined.Refresh,label(Words.Refresh))}
        }
        if(!compactHeader) Text(label(connectionWord),Modifier.testTag("model-connection-state"),style=MaterialTheme.typography.labelMedium)
        if(loading) QuietProgress()
        if(caps?.supportsDiscovery(DiscoveryResource.RuntimeModels)==true) RuntimeModelsPanel(
            discovery.results[DiscoveryResource.RuntimeModels],discovery.fetchedAt[DiscoveryResource.RuntimeModels],
            discovery.errors.containsKey(DiscoveryResource.RuntimeModels) || !connected,now
        ) else Text(label(Words.ModelRuntimeNotSupported),Modifier.testTag("model-runtime-unsupported"))
        discovery.errors[DiscoveryResource.RuntimeModels]?.let {Text(label(Words.ModelRefreshFailed),color=MaterialTheme.colorScheme.error)}
        Text(label(Words.ServerDefault),style=MaterialTheme.typography.titleSmall)
        Text(inventory?.model?.takeIf {it.isNotBlank()} ?: label(Words.Unknown),Modifier.testTag("model-configured-default"))
        inventory?.provider?.takeIf {it.isNotBlank()}?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
        if(discovery.results.containsKey(DiscoveryResource.Models) && modelStatusCached(discovery.fetchedAt[DiscoveryResource.Models],now,discovery.errors.containsKey(DiscoveryResource.Models) || !connected)) Text(label(Words.ModelCachedConfiguration),Modifier.testTag("model-configured-cached"),style=MaterialTheme.typography.labelSmall)
        Text(label(Words.ModelChatSelection),style=MaterialTheme.typography.titleSmall)
        Text(if(choice.followServing) label(Words.FollowServing) else choice.model.ifBlank {label(Words.ServerDefault)},Modifier.testTag("model-request-selection"))
        if(choice.provider.isNotBlank()) Text(choice.provider,style=MaterialTheme.typography.bodySmall)
        conversation?.reported?.let {reported->
            Text(label(Words.ModelLastReported),style=MaterialTheme.typography.titleSmall)
            Text(reported.model,Modifier.testTag("model-conversation-reported"))
            if(reported.provider.isNotBlank()) Text(reported.provider,style=MaterialTheme.typography.bodySmall)
            conversation.reportedAt?.let {Text(observationTime(it),style=MaterialTheme.typography.labelSmall)}
            if(conversation.lockReported) Text(label(Words.ModelLockReported),style=MaterialTheme.typography.labelSmall)
        }
        val ready=discovery.results[DiscoveryResource.Readiness]?.let(::readinessStatus)
        if(ready!=null) Text(label(Words.Readiness)+": "+ready,Modifier.testTag("model-gateway-readiness"),style=MaterialTheme.typography.bodySmall)
        Text(label(Words.ModelStatusInfo),style=MaterialTheme.typography.bodySmall)
        if(!choiceEnabled) Text(label(Words.ModelChoiceBusy),Modifier.testTag("model-choice-busy"),style=MaterialTheme.typography.bodySmall)
        TextButton(onChoose,Modifier.testTag("model-choose-request"),enabled=choiceEnabled && connected && caps?.supportsDiscovery(DiscoveryResource.Models)==true) {Text(label(Words.ModelChooseRequest))}
    }
}

@Composable internal fun ModelStatusOverview(vm:HermesViewModel,compact:Boolean=false,onOpen:(()->Unit)?=null,onChoose:()->Unit) {
    val p by vm.preferences.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val controlling by vm.controlBusy.collectAsStateWithLifecycle()
    val connecting by vm.connecting.collectAsStateWithLifecycle()
    val discovery by vm.discovery.collectAsStateWithLifecycle()
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val connection by vm.connectionStatus.collectAsStateWithLifecycle()
    val scope by vm.profileScope.collectAsStateWithLifecycle()
    val choice by vm.requestedRuntime.collectAsStateWithLifecycle()
    val current by vm.selectedConversation.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val tick=StatusRefresh(vm)
    val now=remember(tick,discovery) {System.currentTimeMillis()}
    val conversation=remember(choice,current,messages,scope) {conversationModelStatus(choice,current,messages,scope)}
    if(compact) {
        val observed=discovery.results[DiscoveryResource.RuntimeModels]?.let {runCatching {ModelRuntimeStatus.parse(it)}.getOrNull()}
        val cached=modelObservationCached(observed?.observedAt,discovery.fetchedAt[DiscoveryResource.RuntimeModels],now,discovery.errors.containsKey(DiscoveryResource.RuntimeModels) || connection !in setOf("connected","demo"))
        Column(Modifier.fillMaxWidth().clickable {onOpen?.invoke()}.testTag("chat-model-status").padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(label(Words.ModelChatSelection),Modifier.weight(.45f),maxLines=3,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall)
                Text(if(choice.followServing) label(Words.FollowServing) else choice.model.ifBlank {label(Words.ServerDefault)},Modifier.weight(.55f),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(label(if(cached) Words.ModelCachedShort else Words.ServingModels),Modifier.weight(.45f),maxLines=3,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall)
                Text(observed?.verifiedModels?.joinToString {it.model}?.takeIf {it.isNotBlank()} ?: label(Words.ModelNotObserved),Modifier.weight(.55f),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelMedium)
            }
        }
    } else ModelStatusCard(discovery,caps,connection,p.demo,choice,conversation,now,{vm.refreshModelStatus(true)},onChoose,choiceEnabled=!busy && !controlling && !connecting,compactHeader=true)
}
