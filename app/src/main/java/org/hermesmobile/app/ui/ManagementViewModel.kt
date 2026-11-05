package org.hermesmobile.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.BuildConfig
import org.hermesmobile.app.HermesApplication
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*

data class HostKanbanState(val inventory:HostKanbanInventory?=null,val board:HostKanbanSnapshot?=null,val detail:HostKanbanDetail?=null,val projects:HostProjectSnapshot?=null,val report:HostKanbanBoardReport?=null,val taskReport:HostKanbanTaskReport?=null)
data class HostKanbanBoardReport(val owner:ManagementTarget,val home:String,val receipt:ManagementOperation,val archivePath:String?=null) {override fun toString()="HostKanbanBoardReport(redacted)"}
data class HostKanbanTaskReport(val owner:ManagementTarget,val home:String,val receipt:ManagementOperation,val warning:String?=null) {override fun toString()="HostKanbanTaskReport(redacted)"}
data class ReviewedKanbanTaskEditor(val inventory:HostKanbanInventory,val snapshot:HostKanbanSnapshot,val subject:HostKanbanDetail,val action:HostKanbanTaskAction,val generation:Long) {override fun toString()="ReviewedKanbanTaskEditor(redacted)"}
data class ReviewedKanbanTaskChange(val request:HostKanbanTaskChange,val generation:Long) {override fun toString()="ReviewedKanbanTaskChange(redacted)"}
data class ReviewedKanbanProjects(val inventory:HostKanbanInventory,val generation:Long) {override fun toString()="ReviewedKanbanProjects(redacted)"}
data class ReviewedKanbanBoardEditor(val expected:HostKanbanInventory,val projects:HostProjectSnapshot?,val action:HostKanbanBoardAction,val slug:String?,val generation:Long) {override fun toString()="ReviewedKanbanBoardEditor(redacted)"}
data class ReviewedKanbanBoardChange(val request:HostKanbanBoardChange,val generation:Long) {override fun toString()="ReviewedKanbanBoardChange(redacted)"}
data class ReviewedHostKanbanRead(val owner:ManagementTarget,val profileHomeId:String,val generation:Long,val board:HostKanbanBoard?=null,val taskId:String?=null) {override fun toString()="ReviewedHostKanbanRead(redacted)"}
data class HostProjectsState(val inventory:HostProjectSnapshot?=null,val detail:HostProjectDetail?=null,val report:HostProjectChangeReport?=null)
data class HostProjectChangeReport(val owner:ManagementTarget,val homeId:String,val receipt:ManagementOperation) {override fun toString()="HostProjectChangeReport(redacted)"}
data class ReviewedHostProjectEditor(val expected:HostProjectSnapshot,val action:HostProjectAction,val id:String?,val generation:Long) {override fun toString()="ReviewedHostProjectEditor(redacted)"}
data class ReviewedHostProjectChange(val request:HostProjectChange,val generation:Long) {
    val target get()=request.expected.owner
    override fun toString()="ReviewedHostProjectChange(redacted)"
}
data class ReviewedHostProjectsRead(val target:ManagementTarget,val homeId:String,val generation:Long) {
    override fun toString()="ReviewedHostProjectsRead(redacted)"
}
data class ReviewedHostProjectDetail(val target:ManagementTarget,val homeId:String,val id:String,val generation:Long) {
    override fun toString()="ReviewedHostProjectDetail(redacted)"
}

data class PluginInventoryState(val inventory:GatewayPluginInventory?=null,val featured:List<FeaturedPlugin>?=null,val lookup:PluginLookup?=null,val inspection:PluginInspection?=null,val installReport:PluginInstallReport?=null,val toggleReport:PluginInstallReport?=null,val updateDecision:PluginUpdateDecision?=null,val updateReport:PluginInstallReport?=null,val updateResult:JsonObject?=null,val settings:PluginSettingsSnapshot?=null)

data class ReviewedCustomEndpointProbe(val target:ManagementTarget,val request:CustomEndpointProbeRequest,val generation:Long)

data class ReviewedCustomEndpointRead(val target:ManagementTarget,val generation:Long)
data class ReviewedCustomEndpoint(val target:ManagementTarget,val request:CustomEndpointRequest,val generation:Long)

data class ReviewedHostFallbackRead(val target:ManagementTarget,val generation:Long)
data class ReviewedHostFallback(val target:ManagementTarget,val request:HostFallbackRequest,val generation:Long)

data class ReviewedCredentialPoolRead(val target:ManagementTarget,val generation:Long)
data class ReviewedCredentialPool(val target:ManagementTarget,val request:CredentialPoolRequest,val generation:Long)

data class ReviewedProviderOAuthRead(val target:ManagementTarget,val generation:Long)
data class ReviewedProviderOAuth(val target:ManagementTarget,val request:ProviderOAuthRequest,val generation:Long)
data class ReviewedProviderOAuthCancel(val target:ManagementTarget,val key:String,val generation:Long)

data class ReviewedHostMoaRead(val target:ManagementTarget,val generation:Long)
data class ReviewedHostMoa(val target:ManagementTarget,val request:HostMoaRequest,val generation:Long)
data class HostAuxModelState(val warning:HostAuxModelWarning?=null)
data class ReviewedHostAuxModel(val target:ManagementTarget,val request:HostAuxModelRequest,val generation:Long)
data class HostMainModelState(val warning:HostMainModelWarning?=null,val notice:String?=null)
data class ReviewedHostMainModel(val target:ManagementTarget,val request:HostMainModelRequest,val generation:Long)

data class ReviewedHostModelsRead(val target:ManagementTarget,val refresh:Boolean,val generation:Long)

data class ReviewedRelayLink(val target:ManagementTarget,val request:HostRelayLinkRequest,val generation:Long)
data class ReviewedRelayCodesClear(val target:ManagementTarget,val generation:Long)
data class ReviewedRelayUnlink(val target:ManagementTarget,val request:HostRelayUnlinkRequest,val generation:Long)
data class ReviewedRelayClear(val target:ManagementTarget,val generation:Long)
data class ReviewedRelayRead(val target:ManagementTarget,val generation:Long)
data class ReviewedRelayPortal(val target:ManagementTarget,val inventory:HostRelayInventory,val generation:Long)
data class ReviewedChannelsRead(val target:ManagementTarget,val generation:Long)
data class ReviewedChannel(val target:ManagementTarget,val request:HostChannelRequest,val generation:Long)
data class ReviewedHostEnvRead(val target:ManagementTarget,val generation:Long)
data class ReviewedHostEnv(val target:ManagementTarget,val request:HostEnvRequest,val generation:Long)

data class ReviewedPluginSource(val target:ManagementTarget,val request:PluginSourceRead,val generation:Long)

data class ReviewedPluginInstall(val target:ManagementTarget,val request:PluginInstallRequest,val generation:Long)

data class ReviewedPluginSettingsRead(val target:ManagementTarget,val row:ManagedPlugin,val generation:Long)
data class ReviewedPluginSettings(val target:ManagementTarget,val request:PluginSettingsRequest,val generation:Long)

data class ReviewedPluginUpdate(val target:ManagementTarget,val request:PluginUpdateRequest,val generation:Long)

data class ReviewedPluginRemoval(val target:ManagementTarget,val request:PluginRemovalRequest,val generation:Long)

data class ReviewedPluginToggle(val target:ManagementTarget,val request:PluginToggleRequest,val generation:Long)

data class ReviewedPluginRead(val target:ManagementTarget,val generation:Long)

data class ReviewedHubMaintenance(val target:ManagementTarget,val request:HubMaintenanceRequest,val generation:Long)

data class ReviewedHubInstall(val target:ManagementTarget,val request:HubInstallRequest,val generation:Long)

data class SkillHubState(val loaded:Boolean=false,val sources:HubSources?=null,val official:List<HubSkill>?=null,val search:HubSearch?=null,val preview:HubPreview?=null,val scan:HubScan?=null)

data class ReviewedGatewayCron(val target:ManagementTarget,val request:GatewayCronRequest,val generation:Long)
data class ReviewedGatewayRead(val target:ManagementTarget,val generation:Long)
data class GatewayAutomationState(val inventory:GatewayCronInventory?=null)

data class ReviewedSkillAction(val target:ManagementTarget,val request:SkillRequest,val generation:Long)
data class HostSkillState(val rows:List<ManagedSkill> = emptyList(),val loaded:Boolean=false,val contentName:String="",val content:String?=null)

data class ReviewedCronCreation(val target:ManagementTarget,val request:CronCreation,val contract:CronCreateContract,val generation:Long)

data class ReviewedMcpOAuth(val target:ManagementTarget,val server:ManagedServer,val generation:Long)

data class ReviewedCatalogInstall(val target:ManagementTarget,val request:McpCatalogInstall,val generation:Long)

data class ReviewedMcpCreation(val target:ManagementTarget,val creation:McpCreation,val generation:Long)
data class ReviewedMcpAction(val target:ManagementTarget,val request:McpRequest,val generation:Long)
data class ManagementObservation(val key:String,val name:String,val exists:Boolean,val enabled:Boolean?)
data class HostCronState(val jobs:List<ManagedCronJob> = emptyList(),val loaded:Boolean=false,val detail:ManagedCronJob?=null,val contract:CronCreateContract?=null,val deliveries:List<CronDeliveryTarget> = emptyList())

data class ManagementState(val apiScope:String="",val url:String="",val debugHttp:Boolean=false,val signedIn:Boolean=false,
    val identity:ManagementIdentity?=null,val profiles:List<ManagedProfile> = emptyList(),val profile:String="",
    val customEndpoints:CustomEndpointSnapshot?=null,val customEndpointProbe:CustomEndpointProbeReport?=null,
    val fallback:HostFallbackSnapshot?=null,
    val poolCleanup:PoolCleanupReport?=null,val credentialPool:CredentialPoolInventory?=null,val providerFlowKey:String?=null,val providerAccounts:ProviderOAuthInventory?=null,val providerFlows:Map<String,ProviderOAuthObservation> = emptyMap(),val moa:HostMoaSnapshot?=null,val hostAuxModel:HostAuxModelState=HostAuxModelState(),val hostMainModel:HostMainModelState=HostMainModelState(),val hostModels:HostModelInventory?=null,val environment:HostEnvInventory?=null,val relayLink:HostRelayLinkObservation?=null,val relayTarget:HostRelayLocalTarget?=null,val relay:HostRelayInventory?=null,val channels:HostChannelInventory?=null,val channelPosture:HostChannelPosture?=null,val plugins:PluginInventoryState=PluginInventoryState(),val hub:SkillHubState=SkillHubState(),val gateway:GatewayAutomationState=GatewayAutomationState(),val projects:HostProjectsState=HostProjectsState(),val kanban:HostKanbanState=HostKanbanState(),val skills:HostSkillState=HostSkillState(),val cron:HostCronState=HostCronState(),val oauth:Map<String,McpOAuthSnapshot> = emptyMap(),val catalog:List<McpCatalogEntry> = emptyList(),val catalogLoaded:Boolean=false,val servers:List<ManagedServer> = emptyList(),val loaded:Boolean=false,val busy:Boolean=false,val error:Words?=null,val notice:Words?=null,val observation:ManagementObservation?=null)

@OptIn(ExperimentalCoroutinesApi::class)
class ManagementViewModel(application:Application):AndroidViewModel(application) {
    private val app=application as HermesApplication
    val state=MutableStateFlow(ManagementState())
    private val updateWorkOwner=Any()
    init {viewModelScope.launch {state.map {it.busy}.distinctUntilChanged().onCompletion {app.updateWorkGuard.set(updateWorkOwner,false)}.collect {app.updateWorkGuard.set(updateWorkOwner,it)}}}

    private val endpoints=CustomEndpointRepository(app.database.dao(),app.managementReady)
    private val fallbacks=HostFallbackRepository(app.database.dao(),app.managementReady)
    private val credentialPools=CredentialPoolRepository(app.database.dao(),app.managementReady)
    private val providerAccounts=ProviderOAuthRepository(app.database.dao(),app.managementReady,app.credentials.providerCodes)
    private val hostModels=HostModelsRepository()
    private val moaRepository=HostMoaRepository(app.database.dao(),app.managementReady)
    private val auxModel=HostAuxModelRepository(app.database.dao(),app.managementReady)
    private val mainModel=HostMainModelRepository(app.database.dao(),app.managementReady)
    private val hostChannels=HostChannelsRepository(app.database.dao(),app.managementReady)
    private val hostRelay=HostRelayRepository(hostChannels)
    private val relayLinks=HostRelayLinkRepository(app.database.dao(),app.managementReady,hostRelay,app.credentials.relayCodes)
    private val relayUnlink=HostRelayUnlinkRepository(app.database.dao(),app.managementReady,hostRelay,app.credentials.relayTargets)
    private val hostEnvironment=HostEnvironmentRepository(app.database.dao(),app.managementReady)
    private val pluginSettings=PluginSettingsRepository(app.database.dao(),app.managementReady)
    private val pluginUpdates=PluginUpdateRepository(app.database.dao(),app.managementReady)
    private val pluginRemovals=PluginRemovalRepository(app.database.dao(),app.managementReady)
    private val pluginToggles=PluginToggleRepository(app.database.dao(),app.managementReady)
    private val pluginInstalls=PluginInstallRepository(app.database.dao(),app.managementReady)
    private val hubMaintenance=HubMaintenanceRepository(app.database.dao(),app.managementReady)
    private val hubInstalls=HubInstallRepository(app.database.dao(),app.managementReady)
    private val gatewayOperations=GatewayCronRepository(app.database.dao(),app.managementReady)
    private val skillOperations=SkillOperationsRepository(app.database.dao(),app.managementReady)
    private val cronCreations=CronCreationRepository(app.database.dao(),app.managementReady)
    private val oauthOperations=McpOAuthRepository(app.database.dao(),app.managementReady)
    private val catalogOperations=McpCatalogRepository(app.database.dao(),app.managementReady)
    private val creations=McpCreationRepository(app.database.dao(),app.managementReady)
    private val operations=ManagementOperationsRepository(app.database.dao(),app.managementReady)
    fun resourceScope(s:ManagementState):String=if(s.profile.isBlank()) "" else runCatching {ManagementTarget(s.apiScope,baseUrl(s).toString(),s.profile,"scope").resourceScope}.getOrDefault("")
    private fun dashboardUrl(s:ManagementState)=runCatching {baseUrl(s).toString()}.getOrDefault("")
    fun projectReceiptHome(s:ManagementState):String=if(s.identity==null || !GatewayCronInventory.supportedProfile(s.profile)) "" else s.profiles.singleOrNull {it.name==s.profile}?.pluginHome?.identity().orEmpty()
    fun visibleReceipts(s:ManagementState,rows:List<ManagementOperation>)=ManagementReceiptVisibility.project(rows,s.apiScope,resourceScope(s),dashboardUrl(s),projectReceiptHome(s))
    private data class ReceiptScope(val resource:String,val api:String,val url:String,val projectHome:String)
    val receipts=state.map {ReceiptScope(resourceScope(it),it.apiScope,dashboardUrl(it),projectReceiptHome(it))}.distinctUntilChanged().flatMapLatest {(scope,apiScope,url,home)->
        if(scope.isBlank()) flowOf(emptyList()) else app.database.dao().managementVisibleResourceOperations(scope,apiScope,url,home).map {ManagementReceiptVisibility.project(it,apiScope,scope,url,home)}
    }.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    private var generation=0L
    private var work:Job?=null
    private var login:NativeLogin?=null
    private var tokens:ManagementTokens?=null
    private fun managementError(e:Throwable)=when(e) {
        is HostKanbanTaskInvalid->Words.KanbanTaskInvalid
        is HostKanbanTaskChanged->Words.ManagementReviewChanged
        is HostKanbanTaskUncertain->Words.KanbanTaskUnknown
        is HostKanbanTaskReadbackPending->Words.KanbanBoardReadbackPending
        is HostKanbanTaskRefused->Words.ProjectChangeRefused
        is HostKanbanBoardInvalid->Words.KanbanBoardInvalid
        is HostKanbanBoardChanged->Words.ManagementReviewChanged
        is HostKanbanBoardUncertain->Words.KanbanBoardUnknown
        is HostKanbanBoardReadbackPending->Words.KanbanBoardReadbackPending
        is HostKanbanBoardRefused->Words.ProjectChangeRefused
        is HostKanbanUnsupported->Words.KanbanUnsupported
        is HostKanbanScopeChanged->Words.KanbanScopeChanged
        is HostProjectChangeInvalid->Words.ProjectDraftInvalid
        is HostProjectChangeUncertain->Words.ProjectChangeUnknown
        is HostProjectChangeReadbackPending->Words.ManagementReadbackPending
        is HostProjectChangeRefused->Words.ProjectChangeRefused
        is HostProjectChangeChanged->Words.ManagementReviewChanged
        is HostProjectsScopeChanged->Words.HostProjectsScopeChanged
        is HostProjectsUnsupported->Words.HostProjectsUnsupported
        is CustomEndpointChanged->Words.ManagementReviewChanged
        is CustomEndpointUnavailable->Words.CustomEndpointsUnavailable
        is CustomEndpointDuplicate->Words.CustomEndpointsDuplicate
        is CustomEndpointUncertain->Words.ManagementOutcomePending
        is CustomEndpointReadbackPending->Words.ManagementReadbackPending
        is HostFallbackChanged->Words.ManagementReviewChanged
        is HostFallbackUnavailable->Words.HostFallbackUnavailable
        is HostFallbackUncertain->Words.ManagementOutcomePending
        is HostFallbackReadbackPending->Words.ManagementReadbackPending
        is CredentialPoolChanged->Words.ManagementReviewChanged
        is CredentialPoolUnavailable->Words.CredentialPoolUnavailable
        is CredentialPoolUncertain->Words.ManagementOutcomePending
        is CredentialPoolReadbackPending->Words.ManagementReadbackPending
        is ProviderOAuthChanged->Words.ManagementReviewChanged
        is ProviderOAuthUnavailable->Words.ProviderOAuthUnavailable
        is ProviderOAuthUncertain->Words.ManagementOutcomePending
        is ProviderOAuthReadbackPending->Words.ManagementReadbackPending
        is HostMoaChanged->Words.ManagementReviewChanged
        is HostMoaUnavailable->Words.HostMoaUnavailable
        is HostMoaUncertain->Words.ManagementOutcomePending
        is HostMoaReadbackPending->Words.ManagementReadbackPending
        is HostAuxModelChanged->Words.ManagementReviewChanged
        is HostAuxModelUncertain->Words.ManagementOutcomePending
        is HostAuxModelReadbackPending->Words.ManagementReadbackPending
        is HostMainModelChanged->Words.ManagementReviewChanged
        is HostMainModelUncertain->Words.ManagementOutcomePending
        is HostMainModelReadbackPending->Words.ManagementReadbackPending
        is HostModelsChanged->Words.ManagementReviewChanged
        is HostModelsUnavailable->Words.HostModelsUnavailable
        is HostRelayLinkUncertain->Words.RelayCodeUnknown
        is HostRelayLinkReadbackPending->Words.ManagementReadbackPending
        is HostRelayCodeUnavailable->Words.RelayCodeUnavailable
        is HostRelayCodeFull->Words.RelayCodeFull
        is HostRelayNotForeground->Words.RelayCodeForeground
        is HostRelayUnlinkUncertain->Words.ManagementOutcomePending
        is HostRelayUnlinkReadbackPending->Words.ManagementReadbackPending
        is HostRelayPrivateUnavailable->Words.RelayPrivateUnavailable
        is HostRelayPrivateFull->Words.RelayPrivateFull
        is HostRelayChanged->Words.ManagementReviewChanged
        is HostRelayScopeUnavailable->Words.RelayScopeUnavailable
        is HostRelayUnavailable->Words.RelayUnavailable
        is HostChannelsChanged->Words.ManagementReviewChanged
        is HostChannelsUnavailable->Words.ChannelsUnavailable
        is HostChannelsUncertain->Words.ManagementOutcomePending
        is HostChannelsReadbackPending->Words.ManagementReadbackPending
        is HostEnvChanged->Words.ManagementReviewChanged
        is HostEnvUnavailable->Words.HostEnvUnavailable
        is HostEnvUncertain->Words.ManagementOutcomePending
        is HostEnvReadbackPending->Words.ManagementReadbackPending
        is PluginSettingsChanged->Words.ManagementReviewChanged
        is PluginSettingsUnavailable->Words.PluginSettingsUnavailable
        is PluginSettingsUncertain->Words.ManagementOutcomePending
        is PluginSettingsReadbackPending->Words.ManagementReadbackPending
        is PluginUpdateChanged->Words.ManagementReviewChanged
        is PluginUpdateUncertain->Words.ManagementOutcomePending
        is PluginUpdateReadbackPending->Words.ManagementReadbackPending
        is PluginRemovalChanged->Words.ManagementReviewChanged
        is PluginRemovalUncertain->Words.ManagementOutcomePending
        is PluginRemovalReadbackPending->Words.ManagementReadbackPending
        is PluginToggleChanged->Words.ManagementReviewChanged
        is PluginToggleOutcomeUncertain->Words.ManagementOutcomePending
        is PluginToggleReadbackPending->Words.ManagementReadbackPending
        is PluginSourceChanged->Words.ManagementReviewChanged
        is GatewayPluginInvalid->Words.PluginInventoryInvalid
        is SkillHubChanged->Words.ManagementReviewChanged
        is SkillHubUnsupported->Words.ManagementUnsupported
        is SkillHubInvalid->Words.SkillHubInvalid
        is GatewayCronReviewChanged->Words.ManagementReviewChanged
        is GatewayCronOutcomeUncertain->Words.ManagementOutcomePending
        is GatewayCronReadbackPending->Words.ManagementReadbackPending
        is GatewayScopeUnproven->Words.GatewayScopeUnsupported
        is GatewayProtocolException->Words.GatewayScopeUnsupported
        is GatewayRpcException->Words.GatewayScopeUnsupported
        is GatewayUnavailable->Words.ManagementInterrupted
        is SkillReviewChanged->Words.ManagementReviewChanged
        is SkillReadbackPending->Words.ManagementReadbackPending
        is CronCreationUnsupported->Words.HostCronCreateUnsupported
        is CronCreationChanged->Words.ManagementReviewChanged
        is CronCreationReadbackPending->Words.ManagementReadbackPending
        is InvalidCronCreation->Words.HostCronCreateInvalid
        is HermesResponseTooLargeException->Words.ManagementTooLarge
        is McpOAuthUnavailable->Words.McpOAuthExpired
        is McpCatalogChanged->Words.ManagementReviewChanged
        is McpReviewChanged->Words.ManagementReviewChanged
        is McpReadbackPending->Words.ManagementReadbackPending
        is ManagementOutcomePending->Words.ManagementOutcomePending
        is LocalNetworkPermissionException->Words.ManagementLanError
        is javax.net.ssl.SSLException->Words.ManagementTlsError
        is HermesHttpException->when(e.code) {401->Words.ManagementExpired;404,405->Words.ManagementUnsupported;else->Words.ManagementFailed}
        is IllegalArgumentException->Words.ManagementInvalid
        else->Words.ManagementInterrupted
    }
    private fun baseUrl(s:ManagementState)=OkHttpHermesApiClient.validateBaseUrl(s.url,s.debugHttp,BuildConfig.DEBUG)
    private fun client(s:ManagementState)=ManagementClient(s.url,s.debugHttp,BuildConfig.DEBUG) {LocalNetworkAccess.granted(app)}
    fun bind(scope:String) {
        if(state.value.apiScope==scope) return
        generation++;login?.close();login=null;work?.cancel();tokens=null
        val id=generation;state.value=ManagementState(apiScope=scope)
        if(!scope.matches(Regex("[a-f0-9]{64}"))) return
        work=viewModelScope.launch {try {
            val saved=withContext(Dispatchers.IO) {app.credentials.management(scope)}
            if(id!=generation || saved==null) return@launch
            val restored=ManagementTokens.parse(saved["tokens"]!!.jsonObject)
            val next=state.value.copy(url=saved.text("url"),debugHttp=saved["debugHttp"]?.jsonPrimitive?.booleanOrNull==true,signedIn=true,profile=saved.text("profile"))
            baseUrl(next);tokens=restored;state.value=next
        } catch(e:CancellationException) {throw e} catch(e:Exception) {if(id==generation) state.value=state.value.copy(error=managementError(e))}}
    }
    private suspend fun save(s:ManagementState,t:ManagementTokens) {withContext(Dispatchers.IO) {
        // Never restore management access for a removed inference credential.
        require(app.credentials.profile(s.apiScope)!=null) {"The API connection credential was removed."}
        app.credentials.saveManagement(s.apiScope,buildJsonObject {put("url",s.url);put("debugHttp",s.debugHttp);put("profile",s.profile);put("tokens",t.json())})
    }}
    private fun operation(block:suspend (Long)->Unit) {
        if(state.value.busy || !state.value.apiScope.matches(Regex("[a-f0-9]{64}"))) return
        val id=++generation;state.value=state.value.copy(busy=true,error=null,notice=null,observation=null)
        work=viewModelScope.launch {try {block(id)} catch(e:CancellationException) {throw e}
            catch(e:Exception) {if(id==generation) state.value=if(e is HostKanbanScopeChanged || e is HostProjectsScopeChanged) state.value.copy(kanban=HostKanbanState(),error=managementError(e)) else if(e is CredentialPoolReadbackPending) state.value.copy(poolCleanup=e.report?.takeIf {it.owner==target()},error=managementError(e)) else if(e is HostMoaChanged) state.value.copy(moa=e.snapshot,hostModels=e.snapshot?.catalog,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),error=managementError(e)) else if(e is HostAuxModelChanged) state.value.copy(hostModels=e.inventory,hostAuxModel=HostAuxModelState(),hostMainModel=HostMainModelState(),error=managementError(e)) else if(e is HostMainModelChanged) state.value.copy(hostModels=e.inventory,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),error=managementError(e)) else if(e is PluginUpdateChanged) state.value.copy(plugins=PluginInventoryState(inventory=e.inventory),error=managementError(e)) else if(e is PluginRemovalChanged) state.value.copy(plugins=PluginInventoryState(inventory=e.inventory),error=managementError(e)) else if(e is PluginToggleChanged) state.value.copy(plugins=PluginInventoryState(inventory=e.inventory),error=managementError(e)) else if(e is McpReviewChanged) state.value.copy(servers=e.servers,loaded=true,error=managementError(e)) else if(e is HermesHttpException && e.code==401) state.value.copy(identity=null,profiles=emptyList(),moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),environment=null,channels=null,channelPosture=null,relay=null,relayTarget=null,relayLink=null,plugins=PluginInventoryState(),hub=SkillHubState(),gateway=GatewayAutomationState(),projects=HostProjectsState(),kanban=HostKanbanState(),skills=HostSkillState(),cron=HostCronState(),oauth=emptyMap(),providerAccounts=null,customEndpoints=null,customEndpointProbe=null,fallback=null,credentialPool=null,poolCleanup=null,providerFlows=emptyMap(),providerFlowKey=null,catalog=emptyList(),catalogLoaded=false,servers=emptyList(),loaded=false,error=managementError(e)) else state.value.copy(error=managementError(e))}
            finally {if(id==generation) state.value=state.value.copy(busy=false)}}
    }
    fun signIn(url:String,debugHttp:Boolean,openBrowser:(String)->Unit)=operation {id ->
        val current=state.value.copy(url=url.trim(),debugHttp=debugHttp,identity=null,profiles=emptyList(),profile="",moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),environment=null,channels=null,channelPosture=null,relay=null,relayTarget=null,relayLink=null,plugins=PluginInventoryState(),hub=SkillHubState(),gateway=GatewayAutomationState(),projects=HostProjectsState(),kanban=HostKanbanState(),skills=HostSkillState(),cron=HostCronState(),oauth=emptyMap(),providerAccounts=null,customEndpoints=null,customEndpointProbe=null,fallback=null,credentialPool=null,poolCleanup=null,providerFlows=emptyMap(),providerFlowKey=null,catalog=emptyList(),catalogLoaded=false,servers=emptyList(),loaded=false)
        val api=client(current)
        val pending=withContext(Dispatchers.IO) {NativeLogin()};login=pending
        try {
            openBrowser(pending.authorize(api.base).toString())
            val code=pending.awaitCode()
            val result=api.exchange(code,pending.verifier);val identity=api.identity(result)
            if(id!=generation) return@operation
            val accepted=current.copy(signedIn=true,identity=identity)
            save(accepted,result)
            if(id!=generation) return@operation
            tokens=result;state.value=accepted
            val profiles=api.profiles(result)
            if(id==generation) state.value=state.value.copy(profiles=profiles)
        } finally {pending.close();if(login===pending) login=null}
    }
    fun cancel() {
        generation++;login?.close();login=null;work?.cancel();state.value=state.value.copy(busy=false,projects=HostProjectsState(),kanban=HostKanbanState(),moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),environment=null,channels=null,channelPosture=null,relay=null,relayTarget=null,relayLink=null,oauth=emptyMap(),providerAccounts=null,customEndpoints=null,customEndpointProbe=null,fallback=null,credentialPool=null,poolCleanup=null,providerFlows=emptyMap(),providerFlowKey=null,plugins=state.value.plugins.copy(lookup=null,inspection=null,installReport=null,toggleReport=null,updateDecision=null,updateReport=null,updateResult=null,settings=null),hub=state.value.hub.copy(preview=null,scan=null))
    }
    fun refresh(renew:Boolean=false)=operation {id ->
        val saved=state.value;val original=tokens ?: error("Sign in to management first.")
        val api=client(saved)
        val current=if(renew) api.refresh(original).also {save(saved,it);if(id==generation) tokens=it} else original
        require(current.expiresAt>System.currentTimeMillis()/1000) {"Management sign-in expired. Renew it or sign in again."}
        val identity=api.identity(current);val profiles=api.profiles(current)
        if(id!=generation) return@operation
        val profile=saved.profile.takeIf {name->profiles.any {it.name==name}}.orEmpty()
        state.value=state.value.copy(identity=identity,profiles=profiles,profile=profile,moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),environment=null,channels=null,channelPosture=null,relay=null,relayTarget=null,relayLink=null,plugins=PluginInventoryState(),hub=SkillHubState(),gateway=GatewayAutomationState(),projects=HostProjectsState(),kanban=HostKanbanState(),skills=HostSkillState(),cron=HostCronState(),oauth=emptyMap(),providerAccounts=null,customEndpoints=null,customEndpointProbe=null,fallback=null,credentialPool=null,poolCleanup=null,providerFlows=emptyMap(),providerFlowKey=null,catalog=emptyList(),catalogLoaded=false,servers=emptyList(),loaded=false)
        if(profile.isNotBlank()) loadServers(id,api,current,profile)
    }
    fun chooseProfile(profile:String)=operation {id ->
        val saved=state.value;require(saved.identity!=null && saved.profiles.any {it.name==profile}) {"Refresh verified management profiles first."}
        val current=tokens ?: error("Sign in to management first.")
        require(current.expiresAt>System.currentTimeMillis()/1000) {"Management sign-in expired. Renew it or sign in again."}
        val selected=saved.copy(profile=profile,moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),environment=null,channels=null,channelPosture=null,relay=null,relayTarget=null,relayLink=null,plugins=PluginInventoryState(),hub=SkillHubState(),gateway=GatewayAutomationState(),projects=HostProjectsState(),kanban=HostKanbanState(),skills=HostSkillState(),cron=HostCronState(),oauth=emptyMap(),providerAccounts=null,customEndpoints=null,customEndpointProbe=null,fallback=null,credentialPool=null,poolCleanup=null,providerFlows=emptyMap(),providerFlowKey=null,catalog=emptyList(),catalogLoaded=false,servers=emptyList(),loaded=false)
        save(selected,current)
        if(id!=generation) return@operation
        state.value=selected;loadServers(id,client(selected),current,profile)
    }
    private suspend fun loadServers(id:Long,api:ManagementClient,current:ManagementTokens,profile:String) {
        val servers=McpProtocol.inventory(api.mcp(current,profile))
        if(id==generation) state.value=state.value.copy(servers=servers,loaded=true)
    }
    private fun target():ManagementTarget? {
        val saved=state.value;val identity=saved.identity ?: return null
        if(!saved.signedIn || saved.profile.isBlank() || saved.profiles.none {it.name==saved.profile}) return null
        return ManagementTarget(saved.apiScope,baseUrl(saved).toString(),saved.profile,identity.provider+" · "+identity.userId)
    }


    fun prepareCustomEndpointRead():ReviewedCustomEndpointRead?=if(state.value.busy || !GatewayCronInventory.supportedProfile(state.value.profile)) null else target()?.let {ReviewedCustomEndpointRead(it,generation)}
    fun loadCustomEndpoints(review:ReviewedCustomEndpointRead) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(customEndpoints=null,customEndpointProbe=null)
        operation {id->val snapshot=endpoints.read(review.target,client(state.value),tokens ?: error("Sign in first."));if(id==generation) state.value=state.value.copy(customEndpoints=snapshot)}
    }
    fun prepareCustomEndpoint(action:CustomEndpointAction,row:CustomEndpointRow?=null,draft:CustomEndpointDraft?=null):ReviewedCustomEndpoint? {
        val inventory=state.value.customEndpoints ?: return null
        if(state.value.busy || inventory.owner!=target() || receipts.value.any {it.state in setOf("sending","unknown")}) return null
        return try {val request=when(action) {CustomEndpointAction.Save->CustomEndpointRequest.save(inventory,draft ?: return null,row);CustomEndpointAction.Activate->CustomEndpointRequest.activate(inventory,row ?: return null);CustomEndpointAction.Remove->CustomEndpointRequest.remove(inventory,row ?: return null)};ReviewedCustomEndpoint(inventory.owner,request,generation)} catch(e:Exception) {state.value=state.value.copy(error=managementError(e));null}
    }
    fun changeCustomEndpoint(review:ReviewedCustomEndpoint) {
        if(review.generation!=generation || target()!=review.target || review.request.expected!==state.value.customEndpoints) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(customEndpoints=null,customEndpointProbe=null,fallback=null,credentialPool=null,poolCleanup=null,hostModels=null,moa=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState())
        operation {id->val out=endpoints.execute(review.target,review.request,client(state.value),tokens ?: error("Sign in first."));if(id==generation) state.value=state.value.copy(customEndpoints=out.snapshot,notice=if(out.different) Words.CustomEndpointsDifferent else Words.CustomEndpointsAcknowledged)}
    }
    fun prepareCustomEndpointProbe(draft:CustomEndpointDraft):ReviewedCustomEndpointProbe? {
        val inventory=state.value.customEndpoints ?: return null
        if(state.value.busy || inventory.owner!=target() || receipts.value.any {it.state in setOf("sending","unknown")}) return null
        return ReviewedCustomEndpointProbe(inventory.owner,CustomEndpointProbeRequest(inventory,draft),generation)
    }
    fun probeCustomEndpoint(review:ReviewedCustomEndpointProbe) {
        if(review.generation!=generation || target()!=review.target || review.request.expected!==state.value.customEndpoints) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(customEndpointProbe=null)
        operation {id->val out=endpoints.probe(review.target,review.request,client(state.value),tokens ?: error("Sign in first."));if(id==generation && target()==review.target) state.value=state.value.copy(customEndpointProbe=out,notice=Words.EndpointProbeReported)}
    }
    fun clearCustomEndpointProbe() {state.value=state.value.copy(customEndpointProbe=null)}
    fun closeCustomEndpoints() {if(state.value.busy) cancel() else {generation++;state.value=state.value.copy(customEndpoints=null,customEndpointProbe=null)}}

    fun prepareHostFallbackRead():ReviewedHostFallbackRead?=if(state.value.busy || !GatewayCronInventory.supportedProfile(state.value.profile)) null else target()?.let {ReviewedHostFallbackRead(it,generation)}
    fun loadHostFallback(review:ReviewedHostFallbackRead) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(customEndpoints=null,customEndpointProbe=null,fallback=null)
        operation {id->val snapshot=fallbacks.read(review.target,client(state.value),tokens ?: error("Sign in first."));if(id==generation) state.value=state.value.copy(fallback=snapshot)}
    }
    fun prepareHostFallback(draft:HostFallbackDraft):ReviewedHostFallback? {
        if(state.value.busy || draft.expected!==state.value.fallback || draft.expected.owner!=target() || receipts.value.any {it.state in setOf("sending","unknown")}) return null
        return runCatching {ReviewedHostFallback(draft.expected.owner,HostFallbackRequest(draft),generation)}.getOrNull()
    }
    fun changeHostFallback(review:ReviewedHostFallback) {
        if(review.generation!=generation || target()!=review.target || review.request.expected!==state.value.fallback) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(customEndpoints=null,customEndpointProbe=null,fallback=null,hostModels=null,moa=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState())
        operation {id->val out=fallbacks.execute(review.target,review.request,client(state.value),tokens ?: error("Sign in first."));if(id==generation) state.value=state.value.copy(fallback=out.snapshot,notice=if(out.matches) Words.HostFallbackAcknowledged else Words.HostFallbackDifferent)}
    }
    fun closeHostFallback() {if(state.value.busy) cancel() else {generation++;state.value=state.value.copy(customEndpoints=null,customEndpointProbe=null,fallback=null)}}

    fun prepareCredentialPoolRead():ReviewedCredentialPoolRead?=if(state.value.busy || !GatewayCronInventory.supportedProfile(state.value.profile)) null else target()?.let {ReviewedCredentialPoolRead(it,generation)}
    fun loadCredentialPool(review:ReviewedCredentialPoolRead) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(customEndpoints=null,customEndpointProbe=null,fallback=null,credentialPool=null,poolCleanup=null)
        operation {id->val inventory=credentialPools.read(review.target,client(state.value),tokens ?: error("Sign in first."));if(id==generation) state.value=state.value.copy(credentialPool=inventory)}
    }
    fun prepareCredentialPool(provider:String,value:String="",label:String="",entry:PoolCredential?=null):ReviewedCredentialPool? {
        val s=state.value;val inventory=s.credentialPool ?: return null
        if(s.busy || inventory.owner!=target() || receipts.value.any {it.state in setOf("sending","unknown")}) return null
        return runCatching {ReviewedCredentialPool(inventory.owner,if(entry==null) CredentialPoolRequest.add(inventory,provider,value,label) else CredentialPoolRequest.remove(inventory,provider,entry),generation)}.getOrNull()
    }
    fun changeCredentialPool(review:ReviewedCredentialPool) {
        if(review.generation!=generation || target()!=review.target || review.request.expected!==state.value.credentialPool) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(customEndpoints=null,customEndpointProbe=null,fallback=null,credentialPool=null,poolCleanup=null,providerAccounts=null,providerFlows=emptyMap(),providerFlowKey=null,hostModels=null,moa=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState())
        operation {id->val out=credentialPools.execute(review.target,review.request,client(state.value),tokens ?: error("Sign in first."));if(id==generation) state.value=state.value.copy(credentialPool=out.inventory,poolCleanup=out.report,notice=if(out.reviewedIdPresent==true) Words.CredentialPoolIdStillReported else Words.CredentialPoolAcknowledged)}
    }
    fun closeCredentialPool() {if(state.value.busy) cancel() else {generation++;state.value=state.value.copy(customEndpoints=null,customEndpointProbe=null,fallback=null,credentialPool=null,poolCleanup=null)}}

    fun prepareProviderOAuthRead():ReviewedProviderOAuthRead?=if(state.value.busy || !GatewayCronInventory.supportedProfile(state.value.profile)) null else target()?.let {ReviewedProviderOAuthRead(it,generation)}
    fun loadProviderAccounts(review:ReviewedProviderOAuthRead) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(providerAccounts=null,customEndpoints=null,customEndpointProbe=null,fallback=null,credentialPool=null,poolCleanup=null,providerFlows=emptyMap(),providerFlowKey=null)
        operation {id->val inventory=providerAccounts.read(review.target,client(state.value),tokens ?: error("Sign in first."));if(id==generation) state.value=state.value.copy(providerAccounts=inventory)}
    }
    fun prepareProviderOAuth(provider:OAuthProvider,action:ProviderOAuthAction):ReviewedProviderOAuth? {
        val s=state.value;val inventory=s.providerAccounts ?: return null
        if(s.busy || inventory.owner!=target() || receipts.value.any {it.state in setOf("sending","unknown")}) return null
        return runCatching {ReviewedProviderOAuth(inventory.owner,ProviderOAuthRequest(inventory,provider,action),generation)}.getOrNull()
    }
    fun changeProviderOAuth(review:ReviewedProviderOAuth) {
        if(review.generation!=generation || target()!=review.target || review.request.expected!==state.value.providerAccounts) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(providerAccounts=null,customEndpoints=null,customEndpointProbe=null,fallback=null,credentialPool=null,poolCleanup=null,providerFlows=emptyMap(),providerFlowKey=null,hostModels=null,moa=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState())
        operation {id->
            val t=tokens ?: error("Sign in first.");val api=client(state.value)
            val receipt=providerAccounts.execute(review.target,review.request,api,t)
            if(id==generation) state.value=state.value.copy(notice=Words.ProviderOAuthAcknowledged)
            if(review.request.action==ProviderOAuthAction.Start) {
                val out=providerAccounts.poll(review.target,receipt.key,api,t)
                if(id==generation) state.value=state.value.copy(providerFlows=mapOf(receipt.key to out))
            }
        }
    }
    fun inspectProviderFlow(key:String) {
        val s=state.value;val t=target() ?: return
        if(s.busy || runCatching {ProviderOAuthBinding.parse(receipts.value.single {it.key==key},t)}.isFailure) return
        state.value=s.copy(providerFlowKey=key)
    }
    fun closeProviderFlow() {
        if(state.value.busy) cancel() else {generation++;state.value=state.value.copy(providerFlows=state.value.providerFlows.filterKeys {it!=state.value.providerFlowKey},providerFlowKey=null)}
    }
    fun readProviderFlow(key:String)=operation {id->
        val target=target() ?: error("Verify management identity first.")
        state.value=state.value.copy(providerFlows=state.value.providerFlows-key)
        val out=providerAccounts.poll(target,key,client(state.value),tokens ?: error("Sign in first."))
        if(id==generation) state.value=state.value.copy(providerFlows=state.value.providerFlows+(key to out))
    }
    fun prepareProviderOAuthCancel(key:String):ReviewedProviderOAuthCancel? {
        val s=state.value;val target=target() ?: return null
        if(s.busy || receipts.value.any {it.state in setOf("sending","unknown")}) return null
        val receipt=receipts.value.singleOrNull {it.key==key} ?: return null
        return runCatching {ProviderOAuthBinding.parse(receipt,target);ReviewedProviderOAuthCancel(target,key,generation)}.getOrNull()
    }
    fun cancelProviderFlow(review:ReviewedProviderOAuthCancel) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(providerFlows=state.value.providerFlows-review.key)
        operation {id->providerAccounts.cancel(review.target,review.key,client(state.value),tokens ?: error("Sign in first."));if(id==generation) state.value=state.value.copy(notice=Words.ProviderOAuthCancelEffect)}
    }
    fun openProviderBrowser(key:String,shown:ProviderOAuthObservation,open:(String)->Unit) {
        if(state.value.providerFlows[key]!==shown || shown.deviceCode==null || shown.status!="pending" || shown.deviceCode.expiresAt<=System.currentTimeMillis()) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {id->
            val target=target() ?: error("Verify management identity first.")
            val out=providerAccounts.poll(target,key,client(state.value),tokens ?: error("Sign in first."))
            if(id!=generation) return@operation
            state.value=state.value.copy(providerFlows=state.value.providerFlows+(key to out))
            val code=out.deviceCode ?: throw ProviderOAuthChanged()
            if(out.status!="pending" || code.url!=shown.deviceCode.url || code.code!=shown.deviceCode.code || code.expiresAt<=System.currentTimeMillis()) throw ProviderOAuthChanged()
            open(code.url)
        }
    }
    fun closeProviderAccounts() {if(state.value.busy) cancel() else {generation++;state.value=state.value.copy(providerAccounts=null,customEndpoints=null,customEndpointProbe=null,fallback=null,credentialPool=null,poolCleanup=null,providerFlows=emptyMap(),providerFlowKey=null)}}

    fun prepareHostMoaRead():ReviewedHostMoaRead?=if(state.value.busy || !GatewayCronInventory.supportedProfile(state.value.profile)) null else target()?.let {ReviewedHostMoaRead(it,generation)}
    fun loadHostMoa(review:ReviewedHostMoaRead) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState())
        operation {id->val out=moaRepository.read(review.target,client(state.value),tokens ?: error("Sign in first."));if(id==generation) state.value=state.value.copy(moa=out,hostModels=out.catalog)}
    }
    fun prepareHostMoa(desired:HostMoaConfig):ReviewedHostMoa? {
        val s=state.value;val snapshot=s.moa ?: return null
        if(s.busy || snapshot.owner!=target() || receipts.value.any {it.state in setOf("sending","unknown")}) return null
        return runCatching {ReviewedHostMoa(snapshot.owner,HostMoaRequest(snapshot,desired),generation)}.getOrNull()
    }
    fun saveHostMoa(review:ReviewedHostMoa) {
        if(review.generation!=generation || target()!=review.target || review.request.expected!==state.value.moa) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState())
        operation {id->val out=moaRepository.execute(review.target,review.request,client(state.value),tokens ?: error("Sign in first."));if(id==generation) state.value=state.value.copy(moa=out.snapshot,hostModels=out.snapshot.catalog,notice=if(out.normalized) Words.HostMoaNormalized else Words.HostMoaAcknowledged)}
    }
    fun closeHostMoa() {if(state.value.busy) cancel() else {generation++;state.value=state.value.copy(moa=null)}}
    fun prepareHostAuxModel(request:HostAuxModelRequest,warning:HostAuxModelWarning?=null):ReviewedHostAuxModel? {
        val s=state.value;val inventory=s.hostModels ?: return null
        if(s.busy || request.expected!==inventory || inventory.owner!=target() || receipts.value.any {it.state in setOf("sending","unknown")}) return null
        if(warning!=null && (warning!==s.hostAuxModel.warning || warning.request!==request)) return null
        return runCatching {ReviewedHostAuxModel(inventory.owner,if(warning==null) request else request.approve(warning),generation)}.getOrNull()
    }
    fun dismissHostAuxModelWarning() {if(!state.value.busy) {generation++;state.value=state.value.copy(hostAuxModel=HostAuxModelState(),notice=null)}}
    fun changeHostAuxModel(review:ReviewedHostAuxModel) {
        val s=state.value
        if(review.generation!=generation || target()!=review.target || review.request.expected!==s.hostModels || review.request.warning?.let {it!==s.hostAuxModel.warning}==true) {state.value=s.copy(error=Words.ManagementReviewChanged);return}
        state.value=s.copy(moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState())
        operation {id->when(val out=auxModel.execute(review.target,review.request,client(state.value),tokens ?: error("Sign in first."))) {
            is HostAuxModelOutcome.Warning->if(id==generation) state.value=state.value.copy(hostModels=out.inventory,hostAuxModel=HostAuxModelState(warning=out.warning),notice=Words.HostMainWarningRequired)
            is HostAuxModelOutcome.Saved->if(id==generation) state.value=state.value.copy(hostModels=out.inventory,notice=Words.HostAuxAcknowledged)
        }}
    }
    fun prepareHostMainModel(provider:String,model:String,warning:HostMainModelWarning?=null):ReviewedHostMainModel? {
        val s=state.value;val inventory=s.hostModels ?: return null
        if(s.busy || inventory.owner!=target() || receipts.value.any {it.state in setOf("sending","unknown")}) return null
        if(warning!=null && (warning!==s.hostMainModel.warning || warning.request.expected!==inventory || warning.request.provider!=provider || warning.request.model!=model)) return null
        return runCatching {ReviewedHostMainModel(inventory.owner,if(warning==null) HostMainModelRequest(inventory,provider,model) else warning.request.approve(warning),generation)}.getOrNull()
    }
    fun dismissHostMainModelWarning() {if(!state.value.busy) {generation++;state.value=state.value.copy(hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),notice=null)}}
    fun changeHostMainModel(review:ReviewedHostMainModel) {
        val s=state.value
        if(review.generation!=generation || target()!=review.target || review.request.expected!==s.hostModels || review.request.warning?.let {it!==s.hostMainModel.warning}==true) {state.value=s.copy(error=Words.ManagementReviewChanged);return}
        state.value=s.copy(moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState())
        operation {id->when(val out=mainModel.execute(review.target,review.request,client(state.value),tokens ?: error("Sign in first."))) {
            is HostMainModelOutcome.Warning->if(id==generation) state.value=state.value.copy(hostModels=out.inventory,hostMainModel=HostMainModelState(warning=out.warning),notice=Words.HostMainWarningRequired)
            is HostMainModelOutcome.Saved->if(id==generation) state.value=state.value.copy(hostModels=out.inventory,hostMainModel=HostMainModelState(notice=out.notice),notice=if(out.inventory.main!=HostModelRoute(review.request.provider,review.request.model)) Words.HostMainNormalized else Words.HostMainAcknowledged)
        }}
    }
    fun closeHostModels() {if(state.value.busy) cancel() else {generation++;state.value=state.value.copy(moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState())}}
    fun prepareHostModelsRead(refresh:Boolean=false):ReviewedHostModelsRead?=if(state.value.busy || !GatewayCronInventory.supportedProfile(state.value.profile)) null else target()?.let {ReviewedHostModelsRead(it,refresh,generation)}
    fun loadHostModels(review:ReviewedHostModelsRead) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState())
        operation {id->val result=hostModels.read(review.target,client(state.value),tokens ?: error("Sign in first."),review.refresh);if(id==generation) state.value=state.value.copy(hostModels=result)}
    }
    fun prepareRelayLink(inventory:HostRelayInventory,platform:String):ReviewedRelayLink? {
        val s=state.value;if(s.busy || s.relay!==inventory || inventory.owner!=target() || visibleReceipts(s,receipts.value).any {it.state in setOf("sending","unknown")}) return null
        return runCatching {ReviewedRelayLink(inventory.owner,HostRelayLinkRequest(inventory,platform),generation)}.getOrNull()
    }
    fun createRelayLink(review:ReviewedRelayLink) {
        if(review.generation!=generation || target()!=review.target || state.value.relay!==review.request.expected) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(relay=null,relayTarget=null,relayLink=null)
        operation {id->val out=relayLinks.execute(review.target,review.request,client(state.value),tokens ?: error("Sign in first."));if(id==generation && target()==review.target) state.value=state.value.copy(relayLink=out)}
    }
    fun refreshRelayCode(shown:HostRelayLinkObservation) {
        val owner=target() ?: return;if(state.value.busy || state.value.relayLink!==shown) return
        state.value=state.value.copy(relayLink=HostRelayLinkObservation(shown.binding,null,shown.available,false))
        operation {id->val out=relayLinks.poll(owner,shown.binding.receipt.key,client(state.value),tokens ?: error("Sign in first."));if(id==generation && target()==owner) state.value=state.value.copy(relayLink=out)}
    }
    fun shareRelayCode(shown:HostRelayLinkObservation,browser:Boolean,foreground:()->Boolean,act:(String)->Unit) {
        val owner=target() ?: return;val original=shown.code ?: return
        if(state.value.busy || state.value.relayLink!==shown || !shown.scopeVerified || shown.binding.status !in setOf("unverified","pending") || original.expiresAt<=System.currentTimeMillis()) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(relayLink=HostRelayLinkObservation(shown.binding,null,shown.available,false))
        operation {id->
            val current=relayLinks.poll(owner,shown.binding.receipt.key,client(state.value),tokens ?: error("Sign in first."))
            if(id!=generation || target()!=owner) return@operation
            state.value=state.value.copy(relayLink=current)
            val code=current.code ?: throw HostRelayCodeUnavailable()
            if(current.binding.status!="pending" || !code.same(original) || code.expiresAt<=System.currentTimeMillis()) throw HostRelayChanged()
            if(!foreground()) throw HostRelayNotForeground()
            try {act(if(browser) code.botUrl ?: throw HostRelayCodeUnavailable() else code.command)} catch(e:CancellationException) {throw e} catch(e:Exception) {state.value=state.value.copy(error=if(browser) Words.RelayCodeBrowserFailed else Words.RelayCodeClipboardFailed)}
        }
    }
    fun expireRelayCode(key:String) {val shown=state.value.relayLink ?: return;if(shown.binding.receipt.key==key && shown.code?.expiresAt?.let {it<=System.currentTimeMillis()}==true) state.value=state.value.copy(relayLink=HostRelayLinkObservation(shown.binding,null,false,false))}
    fun closeRelayCode() {if(state.value.busy) cancel() else {generation++;state.value=state.value.copy(relayLink=null)}}
    fun prepareRelayCodesClear():ReviewedRelayCodesClear?=if(state.value.busy) null else target()?.let {ReviewedRelayCodesClear(it,generation)}
    fun clearRelayCodes(review:ReviewedRelayCodesClear) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {id->withContext(Dispatchers.IO) {try {app.credentials.relayCodes.clear(review.target)} catch(e:CancellationException) {throw e} catch(e:Exception) {throw HostRelayCodeUnavailable()}};if(id==generation && target()==review.target) state.value=state.value.copy(relayLink=null,notice=Words.RelayCodesCleared)}
    }
    fun prepareRelayUnlink(inventory:HostRelayInventory,platform:String,account:HostRelayAccount):ReviewedRelayUnlink? {
        val s=state.value
        if(s.busy || s.relay!==inventory || inventory.owner!=target() || visibleReceipts(s,receipts.value).any {it.state in setOf("sending","unknown")}) return null
        return runCatching {ReviewedRelayUnlink(inventory.owner,HostRelayUnlinkRequest(inventory,platform,account),generation)}.getOrNull()
    }
    fun unlinkRelay(review:ReviewedRelayUnlink) {
        if(review.generation!=generation || target()!=review.target || state.value.relay!==review.request.expected) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(relay=null,relayTarget=null,relayLink=null)
        operation {id->val out=relayUnlink.execute(review.target,review.request,client(state.value),tokens ?: error("Sign in first."));if(id==generation && target()==review.target) state.value=state.value.copy(relay=out.inventory,notice=when(out.absent) {true->Words.RelayUnlinkAbsent;false->Words.RelayUnlinkStillListed;null->Words.RelayUnlinkContextChanged})}
    }
    fun loadRelayTarget(key:String) {
        val expected=target() ?: return;state.value=state.value.copy(relayTarget=null,relayLink=null)
        operation {id->
            val row=app.database.dao().managementOperation(key) ?: throw HostRelayCodeUnavailable()
            if(row.action=="RelayLinkStart") {
                if(row.state!="acknowledged") throw HostRelayLinkUncertain()
                val out=relayLinks.local(expected,key);if(id==generation && target()==expected) state.value=state.value.copy(relayLink=out)
            } else {val out=relayUnlink.local(expected,key);if(id==generation && target()==expected) state.value=state.value.copy(relayTarget=out)}
        }
    }
    fun prepareRelayClear():ReviewedRelayClear?=if(state.value.busy) null else target()?.let {ReviewedRelayClear(it,generation)}
    fun clearRelayTargets(review:ReviewedRelayClear) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {id->withContext(Dispatchers.IO) {try {app.credentials.relayTargets.clear(review.target)} catch(e:CancellationException) {throw e} catch(e:Exception) {throw HostRelayPrivateUnavailable()}};if(id==generation && target()==review.target) state.value=state.value.copy(relay=null,relayTarget=null,relayLink=null,notice=Words.RelayPrivateCleared)}
    }
    fun prepareRelayRead():ReviewedRelayRead?=if(state.value.busy || !GatewayCronInventory.supportedProfile(state.value.profile)) null else target()?.let {ReviewedRelayRead(it,generation)}
    fun loadHostRelay(review:ReviewedRelayRead) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(relay=null,relayTarget=null,relayLink=null)
        operation {id->val result=hostRelay.read(review.target,client(state.value),tokens ?: error("Sign in first."));if(id==generation && target()==review.target) state.value=state.value.copy(relay=result)}
    }
    fun prepareRelayPortal(inventory:HostRelayInventory):ReviewedRelayPortal? {
        if(state.value.busy || state.value.relay!==inventory || inventory.owner!=target() || inventory.manager!=HostRelayManager.Portal || !HostRelayPolicy.portal(inventory.portalUrl)) return null
        return ReviewedRelayPortal(inventory.owner,inventory,generation)
    }
    fun openRelayPortal(review:ReviewedRelayPortal,open:(String)->Unit) {
        if(review.generation!=generation || target()!=review.target || state.value.relay!==review.inventory) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(relay=null,relayTarget=null,relayLink=null)
        operation {id->
            val current=hostRelay.portal(review.target,review.inventory,client(state.value),tokens ?: error("Sign in first."))
            if(id!=generation || target()!=review.target) return@operation
            state.value=state.value.copy(relay=current)
            try {open(current.portalUrl)} catch(e:Exception) {state.value=state.value.copy(error=Words.RelayBrowserFailed)}
        }
    }
    fun closeHostRelay() {if(state.value.busy) cancel() else {generation++;state.value=state.value.copy(relay=null,relayTarget=null,relayLink=null)}}
    fun closeHostChannels() {if(state.value.busy) cancel() else {generation++;state.value=state.value.copy(channels=null,channelPosture=null,relay=null,relayTarget=null,relayLink=null)}}
    fun prepareChannelsRead():ReviewedChannelsRead?=if(state.value.busy || !GatewayCronInventory.supportedProfile(state.value.profile)) null else target()?.let {ReviewedChannelsRead(it,generation)}
    fun loadHostChannels(review:ReviewedChannelsRead) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(channels=null,channelPosture=null,relay=null,relayTarget=null,relayLink=null)
        operation {id->val result=hostChannels.read(review.target,client(state.value),tokens ?: error("Sign in first."));if(id==generation) state.value=state.value.copy(channels=result)}
    }
    fun prepareHostChannel(inventory:HostChannelInventory,id:String,draft:HostChannelDraft?=null):ReviewedChannel? {
        val live=state.value
        if(live.busy || live.channels!==inventory || inventory.owner!=target() || visibleReceipts(live,receipts.value).any {it.state in setOf("sending","unknown")}) return null
        return runCatching {ReviewedChannel(inventory.owner,HostChannelRequest(inventory,id,draft),generation)}.getOrNull()
    }
    fun changeHostChannel(review:ReviewedChannel) {
        if(review.generation!=generation || target()!=review.target || review.request.expected!==state.value.channels) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(channelPosture=null)
        if(review.request.draft!=null) state.value=state.value.copy(environment=null,channels=null,channelPosture=null,relay=null,relayTarget=null,relayLink=null,hostModels=null,moa=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),credentialPool=null,poolCleanup=null,providerAccounts=null,customEndpoints=null,customEndpointProbe=null,fallback=null)
        operation {id->val out=hostChannels.execute(review.target,review.request,client(state.value),tokens ?: error("Sign in first."));if(id==generation) state.value=state.value.copy(channels=out.inventory,channelPosture=out.posture,notice=if(out.posture!=null) Words.ChannelsReported else if(out.different) Words.ChannelsDifferent else if(out.hotServed==true) Words.ChannelsHotServed else Words.ChannelsAcknowledged)}
    }
    fun closeHostEnvironment() {if(state.value.busy) cancel() else {generation++;state.value=state.value.copy(moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),environment=null,channels=null,channelPosture=null,relay=null,relayTarget=null,relayLink=null)}}
    fun prepareHostEnvRead():ReviewedHostEnvRead?=if(state.value.busy || !GatewayCronInventory.supportedProfile(state.value.profile)) null else target()?.let {ReviewedHostEnvRead(it,generation)}
    fun loadHostEnvironment(review:ReviewedHostEnvRead) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),environment=null,channels=null,channelPosture=null,relay=null,relayTarget=null,relayLink=null)
        operation {id->val result=hostEnvironment.read(review.target,client(state.value),tokens ?: error("Sign in first."));if(id==generation) state.value=state.value.copy(environment=result)}
    }
    fun prepareHostEnv(key:String,action:HostEnvAction,value:String=""):ReviewedHostEnv? {
        val inventory=state.value.environment ?: return null
        if(state.value.busy || inventory.owner!=target() || receipts.value.any {it.state in setOf("sending","unknown")}) return null
        return runCatching {ReviewedHostEnv(inventory.owner,HostEnvRequest(inventory,key,action,value),generation)}.getOrNull()
    }
    fun changeHostEnvironment(review:ReviewedHostEnv) {
        if(review.generation!=generation || target()!=review.target || review.request.expected!==state.value.environment) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),environment=null,channels=null,channelPosture=null,relay=null,relayTarget=null,relayLink=null)
        operation {id->val out=hostEnvironment.execute(review.target,review.request,client(state.value),tokens ?: error("Sign in first."));if(id==generation) state.value=state.value.copy(environment=out.inventory,notice=Words.HostEnvAcknowledged)}
    }
    fun preparePluginRead():ReviewedPluginRead?=if(state.value.busy || !GatewayCronInventory.supportedProfile(state.value.profile)) null else target()?.let {ReviewedPluginRead(it,generation)}
    fun loadPlugins(review:ReviewedPluginRead) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {id ->
            val current=tokens ?: error("Sign in first.");val api=client(state.value);api.identity(current)
            val profiles=api.profiles(current);val profile=profiles.singleOrNull {it.name==review.target.profile} ?: error("Refresh the management profile.")
            val result=api.gateway(current).use {GatewayPluginInventory.parse(it.plugins(review.target.profile),review.target.profile,profile.pluginHome)}
            if(id==generation) state.value=state.value.copy(profiles=profiles,plugins=PluginInventoryState(result))
        }
    }
    fun preparePluginSettingsRead(row:ManagedPlugin):ReviewedPluginSettingsRead? {
        val s=state.value;val inventory=s.plugins.inventory ?: return null
        if(s.busy || inventory.plugins.none {it===row} || !PluginToggleRequest.supported(row) || PluginToggleRequest.collision(row,inventory)) return null
        return target()?.let {ReviewedPluginSettingsRead(it,row,generation)}
    }
    fun loadPluginSettings(review:ReviewedPluginSettingsRead) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(plugins=state.value.plugins.copy(settings=null))
        operation {id->val result=pluginSettings.read(review.target,review.row,client(state.value),tokens ?: error("Sign in first."));if(id==generation) state.value=state.value.copy(plugins=state.value.plugins.copy(settings=result))}
    }
    fun clearPluginSettings() {state.value=state.value.copy(plugins=state.value.plugins.copy(settings=null))}
    fun preparePluginSetting(snapshot:PluginSettingsSnapshot,field:PluginSettingField,value:JsonElement):ReviewedPluginSettings? {
        if(state.value.busy || state.value.plugins.settings!==snapshot || snapshot.owner!=target() || receipts.value.any {it.state in setOf("sending","unknown")}) return null
        return runCatching {ReviewedPluginSettings(snapshot.owner,PluginSettingsRequest(snapshot,field,value),generation)}.getOrNull()
    }
    fun savePluginSetting(review:ReviewedPluginSettings) {
        if(review.generation!=generation || target()!=review.target || state.value.plugins.settings!==review.request.expected) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(plugins=state.value.plugins.copy(settings=null))
        operation {id->val result=pluginSettings.execute(review.target,review.request,client(state.value),tokens ?: error("Sign in first."));if(id==generation) state.value=state.value.copy(plugins=state.value.plugins.copy(settings=result.snapshot),notice=Words.PluginSettingsSaved)}
    }
    fun preparePluginUpdate(row:ManagedPlugin,decision:PluginUpdateDecision?=null):ReviewedPluginUpdate? {
        val saved=state.value;val inventory=saved.plugins.inventory ?: return null
        val currentTarget=target() ?: return null
        if(decision!=null && (!decision.eligible() || decision!==saved.plugins.updateDecision || decision.owner!=currentTarget)) {state.value=saved.copy(error=Words.PluginUpdateDecisionUnavailable);return null}
        if(saved.busy || receipts.value.any {it.state in setOf("sending","unknown")} || inventory.plugins.none {it===row} || PluginRemovalRequest.collision(row,inventory)) return null
        val request=runCatching {PluginUpdateRequest(row,decision)}.getOrNull() ?: return null
        return ReviewedPluginUpdate(currentTarget,request,generation)
    }
    fun updatePlugin(review:ReviewedPluginUpdate) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        if(review.request.decision?.eligible()==false) {state.value=state.value.copy(error=Words.PluginUpdateDecisionUnavailable);return}
        state.value=state.value.copy(plugins=PluginInventoryState())
        operation {id ->
            val current=tokens ?: error("Sign in first.")
            val result=pluginUpdates.execute(review.target,review.request,client(state.value),current)
            if(id==generation) state.value=state.value.copy(plugins=PluginInventoryState(inventory=result.inventory,updateDecision=result.decision,updateReport=result.report,updateResult=Json.parseToJsonElement(result.receipt.resultSummary).jsonObject),notice=when(result.kind) {PluginUpdateKind.Capabilities->Words.PluginUpdateCapabilities;PluginUpdateKind.Scan->Words.PluginUpdateScan;else->if(review.request.expected.catalog.isBlank()) Words.PluginUpdateGitReported else Words.PluginUpdateResult})
        }
    }
    fun preparePluginRemoval(row:ManagedPlugin):ReviewedPluginRemoval? {
        val saved=state.value;val inventory=saved.plugins.inventory ?: return null
        if(saved.busy || receipts.value.any {it.state in setOf("sending","unknown")} || inventory.plugins.none {it===row} || PluginRemovalRequest.collision(row,inventory)) return null
        val request=runCatching {PluginRemovalRequest(row)}.getOrNull() ?: return null
        return target()?.let {ReviewedPluginRemoval(it,request,generation)}
    }
    fun removePlugin(review:ReviewedPluginRemoval) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(plugins=PluginInventoryState())
        operation {id ->
            val current=tokens ?: error("Sign in first.")
            val result=pluginRemovals.execute(review.target,review.request,client(state.value),current)
            if(id==generation) state.value=state.value.copy(plugins=PluginInventoryState(result.inventory),notice=if(result.observedOnly) Words.PluginRemoveNotReported else Words.PluginRemoveReported)
        }
    }
    fun preparePluginToggle(row:ManagedPlugin):ReviewedPluginToggle? {
        val saved=state.value;val inventory=saved.plugins.inventory ?: return null
        if(saved.busy || receipts.value.any {it.state in setOf("sending","unknown")} || inventory.plugins.none {it===row} || PluginToggleRequest.collision(row,inventory)) return null
        val request=runCatching {PluginToggleRequest(row,row.status!="enabled")}.getOrNull() ?: return null
        return target()?.let {ReviewedPluginToggle(it,request,generation)}
    }
    fun togglePlugin(review:ReviewedPluginToggle) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {id ->
            val current=tokens ?: error("Sign in first.")
            val result=pluginToggles.execute(review.target,review.request,client(state.value),current,state.value.profiles.singleOrNull {it.name==review.target.profile}?.pluginHome)
            if(id==generation) state.value=state.value.copy(plugins=PluginInventoryState(inventory=result.inventory,toggleReport=result.report),notice=if(result.report==null) Words.PluginToggleDetailsUnavailable else Words.PluginToggleReported)
        }
    }
    fun preparePluginInstall(enable:Boolean):ReviewedPluginInstall? {
        val saved=state.value;val inspected=saved.plugins.inspection ?: return null
        if(saved.busy || receipts.value.any {it.state in setOf("sending","unknown")}) return null
        val catalog=if(inspected.source.kind==PluginSourceKind.Catalog) saved.plugins.lookup?.entry else null
        val request=runCatching {PluginInstallRequest(inspected,catalog,enable)}.getOrNull() ?: return null
        return target()?.let {ReviewedPluginInstall(it,request,generation)}
    }
    fun installPlugin(review:ReviewedPluginInstall) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {id ->
            val current=tokens ?: error("Sign in first.")
            val result=pluginInstalls.install(review.target,review.request,client(state.value),current)
            if(id==generation) state.value=state.value.copy(plugins=state.value.plugins.copy(inventory=null,lookup=null,inspection=null,installReport=result.report),notice=if(result.sourceChanged) Words.PluginInstallSourceChanged else if(result.report==null) Words.PluginInstallDetailsUnavailable else Words.PluginInstallReported)
        }
    }
    fun clearPluginSourceResults() {state.value=state.value.copy(plugins=state.value.plugins.copy(lookup=null,inspection=null,installReport=null,toggleReport=null,updateDecision=null,updateReport=null,updateResult=null))}
    fun preparePluginSource(request:PluginSourceRead):ReviewedPluginSource?=if(state.value.busy || !GatewayCronInventory.supportedProfile(state.value.profile)) null else target()?.let {ReviewedPluginSource(it,request,generation)}
    fun readPluginSource(review:ReviewedPluginSource) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {id ->
            if(review.request.action!=PluginSourceReadAction.Featured) state.value=state.value.copy(plugins=state.value.plugins.copy(inspection=null,lookup=if(review.request.action==PluginSourceReadAction.Lookup) null else state.value.plugins.lookup))
            val current=tokens ?: error("Sign in first.");val api=client(state.value);api.identity(current)
            val result=api.gateway(current).use {socket ->when(review.request.action) {
                PluginSourceReadAction.Featured->state.value.plugins.copy(featured=PluginSourcesProtocol.featured(socket.featuredPlugins(review.target.profile)))
                PluginSourceReadAction.Lookup->{val source=review.request.source!!;state.value.plugins.copy(lookup=PluginSourcesProtocol.lookup(socket.lookupPlugin(review.target.profile,source.value),source.value),inspection=null)}
                PluginSourceReadAction.Inspect->{
                    val source=review.request.source!!;val expected=review.request.expected
                    if(expected!=null) PluginSourcesProtocol.requireCatalog(expected,PluginSourcesProtocol.lookup(socket.lookupPlugin(review.target.profile,expected.name),expected.name))
                    val inspected=PluginSourcesProtocol.inspection(socket.inspectPlugin(review.target.profile,source),source,expected)
                    if(expected!=null) PluginSourcesProtocol.requireCatalog(expected,PluginSourcesProtocol.lookup(socket.lookupPlugin(review.target.profile,expected.name),expected.name))
                    state.value.plugins.copy(inspection=inspected)
                }
            }}
            if(id==generation) state.value=state.value.copy(plugins=result)
        }
    }
    fun loadSkillHub()=operation {id ->
        val target=target() ?: error("Verify management profile first.");val current=tokens ?: error("Sign in first.");val api=client(state.value);api.identity(current)
        val sources=try {SkillHubProtocol.sources(api.hubSources(current,target.profile))} catch(e:HermesHttpException) {if(e.code in setOf(404,405)) null else throw e}
        val official=try {SkillHubProtocol.official(api.hubOfficial(current,target.profile))} catch(e:HermesHttpException) {if(e.code in setOf(404,405)) null else throw e}
        if(sources==null && official==null) throw SkillHubUnsupported()
        if(id==generation) state.value=state.value.copy(hub=SkillHubState(true,sources,official))
    }
    fun searchSkillHub(query:String,source:String)=operation {id ->
        val target=target() ?: error("Verify management profile first.");val current=tokens ?: error("Sign in first.");val saved=state.value
        require(source=="all" || saved.hub.sources?.sources?.any {it.id==source && it.searchable!=false && it.available!=false && it.rateLimited!=true}==true)
        val api=client(saved);api.identity(current);val result=SkillHubProtocol.search(api.hubSearch(current,target.profile,query.trim(),source))
        if(id==generation) state.value=state.value.copy(hub=state.value.hub.copy(search=result,preview=null,scan=null))
    }
    fun previewHubSkill(skill:HubSkill)=operation {id ->
        val target=target() ?: error("Verify management profile first.");val current=tokens ?: error("Sign in first.")
        require(skill.addressable && listOfNotNull(state.value.hub.official,state.value.hub.sources?.featured,state.value.hub.search?.results).flatten().any {it===skill})
        val api=client(state.value);api.identity(current);val preview=SkillHubProtocol.preview(api.hubPreview(current,target.profile,skill),skill)
        if(id==generation) state.value=state.value.copy(hub=state.value.hub.copy(preview=preview,scan=null))
    }
    fun scanHubSkill()=operation {id ->
        val target=target() ?: error("Verify management profile first.");val current=tokens ?: error("Sign in first.");val preview=state.value.hub.preview ?: error("Preview a skill first.")
        val api=client(state.value);api.identity(current);val result=SkillHubProtocol.scan(api.hubScan(current,target.profile,preview.skill),preview.skill)
        if(id==generation) state.value=state.value.copy(hub=state.value.hub.copy(scan=result))
    }
    fun prepareHubMaintenance(action:HubMaintenanceAction,name:String=""):ReviewedHubMaintenance? {
        val saved=state.value;val sources=saved.hub.sources ?: return null
        if(saved.busy || receipts.value.any {it.state in setOf("sending","unknown")}) return null
        val request=runCatching {HubMaintenanceRequest(action,HubInstalledInventory.from(sources),name)}.getOrNull() ?: return null
        return target()?.let {ReviewedHubMaintenance(it,request,generation)}
    }
    fun maintainHubSkills(review:ReviewedHubMaintenance) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {id ->
            val current=tokens ?: error("Sign in first.")
            hubMaintenance.execute(review.target,review.request,client(state.value),current)
            if(id==generation) state.value=state.value.copy(hub=SkillHubState(),notice=Words.SkillHubMaintenanceAccepted)
        }
    }
    fun prepareHubInstall():ReviewedHubInstall? {
        val saved=state.value;val preview=saved.hub.preview ?: return null;val scan=saved.hub.scan ?: return null
        if(saved.busy || receipts.value.any {it.state in setOf("sending","unknown")}) return null
        val request=runCatching {HubInstallRequest(preview,scan)}.getOrNull() ?: return null
        return target()?.let {ReviewedHubInstall(it,request,generation)}
    }
    fun installHubSkill(review:ReviewedHubInstall) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {id ->
            val current=tokens ?: error("Sign in first.")
            hubInstalls.install(review.target,review.request,client(state.value),current)
            if(id==generation) state.value=state.value.copy(hub=SkillHubState(),notice=Words.SkillHubInstallAccepted)
        }
    }
    fun observeHubReceipt(key:String)=operation {id ->
        val target=target() ?: error("Verify management profile first.")
        val record=app.database.dao().managementOperation(key) ?: error("Missing receipt.")
        require(record.resourceScope==target.resourceScope && record.apiScope==target.apiScope && record.action in setOf("HubInstall","HubRemove","HubUpdateAll"))
        val current=tokens ?: error("Sign in first.");val api=client(state.value);api.identity(current)
        val rows=SkillProtocol.inventory(api.skills(current,target.profile))
        if(id==generation) state.value=state.value.copy(skills=HostSkillState(rows,true),notice=Words.ManagementObservationOnly)
    }
    fun closeHubScan() {state.value=state.value.copy(hub=state.value.hub.copy(scan=null))}
    fun closeHubPreview() {cancel();state.value=state.value.copy(hub=state.value.hub.copy(preview=null,scan=null))}
    fun prepareGatewayCron(job:GatewayCronJob,action:GatewayCronAction):ReviewedGatewayCron? {
        val saved=state.value;val inventory=saved.gateway.inventory ?: return null
        if(saved.busy || !GatewayCronInventory.supportedProfile(saved.profile) || inventory.profile!=saved.profile || inventory.jobs.none {it===job} || GatewayCronRequest.collision(job,inventory.jobs)) return null
        val request=runCatching {GatewayCronRequest(job,action)}.getOrNull() ?: return null
        return target()?.let {ReviewedGatewayCron(it,request,generation)}
    }
    fun performGatewayCron(review:ReviewedGatewayCron) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {id ->
            val current=tokens ?: error("Sign in first.")
            try {
                val outcome=gatewayOperations.execute(review.target,review.request,client(state.value),current)
                if(id==generation) state.value=state.value.copy(gateway=GatewayAutomationState(outcome.inventory),notice=if(outcome.observedOnly) Words.ManagementObservationOnly else Words.GatewayCronChanged)
            } catch(e:GatewayCronReviewChanged) {
                if(id==generation) state.value=state.value.copy(gateway=GatewayAutomationState(e.inventory))
                throw e
            }
        }
    }
    private val kanbanBoardChanges=HostKanbanBoardRepository(app.database.dao(),app.managementReady)
    fun kanbanBoardBlock(s:ManagementState):Words? {
        if(s.busy) return Words.Loading
        if(hostProjectPending(s)) return Words.ManagementOutcomePending
        val inventory=visibleHostKanban(s).inventory ?: return Words.KanbanReadFirst
        if(inventory.boards.count {it.slug=="default" && !it.archived}!=1) return Words.KanbanAnchorRequired
        return null
    }
    fun prepareKanbanProjects():ReviewedKanbanProjects? {
        if(state.value.busy) return null
        return visibleHostKanban(state.value).inventory?.let {ReviewedKanbanProjects(it,generation)}
    }
    fun kanbanProjectsCurrent(review:ReviewedKanbanProjects)=!state.value.busy && review.generation==generation && visibleHostKanban(state.value).inventory===review.inventory && projectContext()==(review.inventory.owner to review.inventory.profileHomeId)
    fun loadKanbanProjects(review:ReviewedKanbanProjects) {
        if(!kanbanProjectsCurrent(review)) {hostProjectReviewChanged();return}
        val before=visibleHostKanban(state.value);state.value=state.value.copy(kanban=before.copy(projects=null))
        operation {id->
            val result=projectReads.read(review.inventory.owner,review.inventory.profileHomeId,client(state.value),tokens ?: error("Sign in first."))
            if(id==generation && projectContext()==(review.inventory.owner to review.inventory.profileHomeId)) state.value=state.value.copy(kanban=before.copy(projects=result))
        }
    }
    fun prepareKanbanBoardEditor(action:HostKanbanBoardAction,slug:String?=null):ReviewedKanbanBoardEditor? {
        if(kanbanBoardBlock(state.value)!=null || action !in setOf(HostKanbanBoardAction.Create,HostKanbanBoardAction.Update)) return null
        val visible=visibleHostKanban(state.value);val inventory=visible.inventory ?: return null
        if(action==HostKanbanBoardAction.Update && inventory.boards.none {it.slug==slug}) return null
        return ReviewedKanbanBoardEditor(inventory,visible.projects,action,slug,generation)
    }
    fun kanbanBoardEditorCurrent(editor:ReviewedKanbanBoardEditor)=editor.generation==generation && kanbanBoardBlock(state.value)==null && visibleHostKanban(state.value).inventory===editor.expected && visibleHostKanban(state.value).projects===editor.projects
    private fun kanbanBoardRequestCurrent(request:HostKanbanBoardChange):Boolean {
        if(kanbanBoardBlock(state.value)!=null || visibleHostKanban(state.value).inventory!==request.expected || projectContext()!=(request.expected.owner to request.expected.profileHomeId)) return false
        return request.project==null || visibleHostKanban(state.value).projects?.let(request::projectCurrent)==true
    }
    fun prepareKanbanBoardChange(request:HostKanbanBoardChange)=if(kanbanBoardRequestCurrent(request)) ReviewedKanbanBoardChange(request,generation) else null
    fun kanbanBoardChangeCurrent(review:ReviewedKanbanBoardChange)=review.generation==generation && kanbanBoardRequestCurrent(review.request)
    private fun kanbanBoardReport(owner:ManagementTarget,home:String,receipt:ManagementOperation?,path:String?=null)=receipt?.takeIf {it.apiScope==owner.apiScope && it.resourceScope==owner.resourceScope && it.url==owner.url && it.profile==owner.profile && it.actor==owner.actor && it.projectHome.isEmpty() && HostKanbanBoardAction.receipt(it.action)}?.let {HostKanbanBoardReport(owner,home,it,path)}
    fun performKanbanBoardChange(review:ReviewedKanbanBoardChange) {
        if(!kanbanBoardChangeCurrent(review)) {hostProjectReviewChanged();return}
        val request=review.request;val owner=request.expected.owner;val home=request.expected.profileHomeId
        state.value=state.value.copy(kanban=HostKanbanState())
        operation {id->
            try {
                val result=kanbanBoardChanges.execute(owner,request,client(state.value),tokens ?: error("Sign in first."))
                if(id==generation && projectContext()==(owner to home)) state.value=state.value.copy(kanban=HostKanbanState(inventory=result.inventory,report=kanbanBoardReport(owner,home,result.receipt,result.acknowledgement.archivePath)))
            } catch(e:Exception) {
                val receipt=when(e) {is HostKanbanBoardUncertain->e.receipt;is HostKanbanBoardReadbackPending->e.receipt;is HostKanbanBoardRefused->e.receipt;else->null}
                if(e !is CancellationException && id==generation && projectContext()==(owner to home)) state.value=state.value.copy(kanban=HostKanbanState(report=kanbanBoardReport(owner,home,receipt)))
                throw e
            }
        }
    }
    private val kanbanTaskChanges=HostKanbanTaskRepository(app.database.dao(),app.managementReady)
    fun kanbanTaskBlock(s:ManagementState):Words? {
        kanbanBoardBlock(s)?.let {return it}
        val visible=visibleHostKanban(s);val board=visible.board ?: return Words.KanbanTaskReadFirst
        val detail=visible.detail ?: return Words.KanbanTaskReadFirst
        if(board.board.definition()!=detail.board.definition() || board.board.archived) return Words.KanbanTaskReadFirst
        return null
    }
    fun prepareKanbanTaskEditor(action:HostKanbanTaskAction):ReviewedKanbanTaskEditor? {
        if(action !in kanbanTaskEditActions || kanbanTaskBlock(state.value)!=null) return null
        val visible=visibleHostKanban(state.value)
        return ReviewedKanbanTaskEditor(visible.inventory!!,visible.board!!,visible.detail!!,action,generation)
    }
    fun kanbanTaskEditorCurrent(editor:ReviewedKanbanTaskEditor):Boolean {
        val visible=visibleHostKanban(state.value)
        return editor.generation==generation && kanbanTaskBlock(state.value)==null && visible.inventory===editor.inventory && visible.board===editor.snapshot && visible.detail===editor.subject
    }
    private fun kanbanTaskRequestCurrent(request:HostKanbanTaskChange):Boolean {
        val visible=visibleHostKanban(state.value)
        return request.action in kanbanTaskEditActions && kanbanTaskBlock(state.value)==null && visible.inventory===request.inventory && visible.board===request.snapshot && visible.detail===request.subject && projectContext()==(request.owner to request.home)
    }
    fun prepareKanbanTaskChange(request:HostKanbanTaskChange)=if(kanbanTaskRequestCurrent(request)) ReviewedKanbanTaskChange(request,generation) else null
    fun kanbanTaskChangeCurrent(review:ReviewedKanbanTaskChange)=review.generation==generation && kanbanTaskRequestCurrent(review.request)
    private fun kanbanTaskReport(owner:ManagementTarget,home:String,receipt:ManagementOperation?,warning:String?=null)=receipt?.takeIf {it.apiScope==owner.apiScope && it.resourceScope==owner.resourceScope && it.url==owner.url && it.profile==owner.profile && it.actor==owner.actor && it.projectHome.isEmpty() && HostKanbanTaskAction.receipt(it.action)}?.let {HostKanbanTaskReport(owner,home,it,warning)}
    fun performKanbanTaskChange(review:ReviewedKanbanTaskChange) {
        if(!kanbanTaskChangeCurrent(review)) {hostProjectReviewChanged();return}
        val request=review.request;val owner=request.owner;val home=request.home
        state.value=state.value.copy(kanban=HostKanbanState())
        operation {id->
            try {
                val result=kanbanTaskChanges.execute(owner,request,client(state.value),tokens ?: error("Sign in first."))
                if(id==generation && projectContext()==(owner to home)) state.value=state.value.copy(kanban=HostKanbanState(detail=result.detail,board=result.board,taskReport=kanbanTaskReport(owner,home,result.receipt,result.acknowledgement.warning)))
            } catch(e:Exception) {
                val receipt=when(e) {is HostKanbanTaskUncertain->e.receipt;is HostKanbanTaskReadbackPending->e.receipt;is HostKanbanTaskRefused->e.receipt;else->null}
                if(e !is CancellationException && id==generation && projectContext()==(owner to home)) state.value=state.value.copy(kanban=HostKanbanState(taskReport=kanbanTaskReport(owner,home,receipt)))
                throw e
            }
        }
    }
    private val kanbanReads=HostKanbanRepository()
    fun visibleHostKanban(s:ManagementState):HostKanbanState {
        val context=projectContext() ?: return HostKanbanState()
        if(s.apiScope!=context.first.apiScope || s.profile!=context.first.profile || s.url!=state.value.url || s.identity!=state.value.identity) return HostKanbanState()
        return HostKanbanState(s.kanban.inventory?.takeIf {it===state.value.kanban.inventory && it.owner==context.first && it.profileHomeId==context.second},s.kanban.board?.takeIf {it===state.value.kanban.board && it.owner==context.first && it.profileHomeId==context.second},s.kanban.detail?.takeIf {it===state.value.kanban.detail && it.owner==context.first && it.profileHomeId==context.second},s.kanban.projects?.takeIf {it===state.value.kanban.projects && it.owner==context.first && it.homeId==context.second},s.kanban.report?.takeIf {it===state.value.kanban.report && it.owner==context.first && it.home==context.second},s.kanban.taskReport?.takeIf {it===state.value.kanban.taskReport && it.owner==context.first && it.home==context.second})
    }
    fun prepareHostKanbanRead(board:HostKanbanBoard?=null,taskId:String?=null):ReviewedHostKanbanRead? {
        if(state.value.busy) return null
        val context=projectContext() ?: return null
        val visible=visibleHostKanban(state.value)
        if(board!=null && (board.archived || visible.inventory?.boards?.none {it===board}!=false && visible.board?.board!==board && visible.detail?.board!==board)) return null
        if(taskId!=null) {
            if(board==null || !HostKanbanProtocol.id(taskId)) return null
            val inBoard=visible.board?.takeIf {it.board.sameMapping(board)}?.columns?.any {c->c.tasks.any {it.id==taskId}}==true
            val inDetail=visible.detail?.takeIf {it.board.sameMapping(board)}?.let {taskId==it.task.id || taskId in it.parents || taskId in it.children}==true
            if(!inBoard && !inDetail) return null
        }
        return ReviewedHostKanbanRead(context.first,context.second,generation,board,taskId)
    }
    fun hostKanbanReadCurrent(review:ReviewedHostKanbanRead)=review.generation==generation && projectContext()==(review.owner to review.profileHomeId) && prepareHostKanbanRead(review.board,review.taskId)!=null
    fun loadHostKanban(review:ReviewedHostKanbanRead) {
        if(!hostKanbanReadCurrent(review)) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        val previous=visibleHostKanban(state.value)
        state.value=state.value.copy(kanban=if(review.board==null) HostKanbanState() else HostKanbanState(previous.inventory,if(review.taskId!=null) previous.board else null))
        operation {id->
            val api=client(state.value);val current=tokens ?: error("Sign in first.")
            val result=if(review.board==null) HostKanbanState(inventory=kanbanReads.inventory(review.owner,review.profileHomeId,api,current))
                else if(review.taskId==null) HostKanbanState(previous.inventory,kanbanReads.board(review.owner,review.profileHomeId,review.board,api,current))
                else HostKanbanState(previous.inventory,previous.board,kanbanReads.detail(review.owner,review.profileHomeId,review.board,review.taskId,api,current))
            if(id==generation && projectContext()==(review.owner to review.profileHomeId)) state.value=state.value.copy(kanban=result)
        }
    }
    fun closeHostKanbanDetail() {if(state.value.busy) cancel() else {generation++;state.value=state.value.copy(kanban=state.value.kanban.copy(detail=null))}}
    fun closeHostKanban() {if(state.value.busy) cancel() else {generation++;state.value=state.value.copy(kanban=HostKanbanState())}}
    private val projectReads=HostProjectsRepository()
    private val projectChanges=HostProjectOperationsRepository(app.database.dao(),app.managementReady)
    private fun projectContext():Pair<ManagementTarget,String>? {
        val owner=runCatching {target()}.getOrNull() ?: return null
        if(!GatewayCronInventory.supportedProfile(owner.profile)) return null
        val home=state.value.profiles.singleOrNull {it.name==owner.profile}?.pluginHome?.identity() ?: return null
        return owner to home
    }
    fun hostProjectsContextCurrent(s:ManagementState):Boolean {
        val context=projectContext() ?: return false
        return s.apiScope==context.first.apiScope && s.profile==context.first.profile && s.url==state.value.url && s.identity==state.value.identity
    }
    fun hostProjectReadCurrent(review:ReviewedHostProjectsRead)=review.generation==generation && projectContext()==(review.target to review.homeId)
    fun visibleHostProjects(s:ManagementState):HostProjectsState {
        val context=projectContext() ?: return HostProjectsState()
        if(s.apiScope!=context.first.apiScope || s.profile!=context.first.profile || s.url!=state.value.url || s.identity!=state.value.identity) return HostProjectsState()
        return HostProjectsState(s.projects.inventory?.takeIf {it===state.value.projects.inventory && it.owner==context.first && it.homeId==context.second},s.projects.detail?.takeIf {it===state.value.projects.detail && it.owner==context.first && it.homeId==context.second},s.projects.report?.takeIf {it===state.value.projects.report && it.owner==context.first && it.homeId==context.second})
    }
    fun hostProjectReviewGeneration()=generation
    fun hostProjectPending(s:ManagementState)=visibleReceipts(s,receipts.value).any {it.state in setOf("sending","unknown")}
    fun hostProjectBlockReason(s:ManagementState,row:HostProjectInfo?=null):Words? {
        if(s.busy) return Words.Loading
        if(hostProjectPending(s)) return Words.ManagementOutcomePending
        val inventory=visibleHostProjects(s).inventory ?: return Words.ProjectReadFirst
        if(row!=null) {
            if(HostProjectChange.collision(inventory,row.id)) return Words.ProjectCollision
            if(inventory.projects.singleOrNull {it.id==row.id}!=row) return Words.ProjectReloadInventory
        }
        return null
    }
    private fun projectRequestCurrent(request:HostProjectChange):Boolean {
        val s=state.value;val expected=request.expected
        if(s.busy || projectContext()!=(expected.owner to expected.homeId) || visibleHostProjects(s).inventory!==expected || hostProjectPending(s)) return false
        val row=request.selected
        return row==null || !HostProjectChange.collision(expected,row.id) && visibleHostProjects(s).detail?.project==row
    }
    fun prepareHostProjectEditor(action:HostProjectAction,id:String?=null):ReviewedHostProjectEditor? {
        val inventory=visibleHostProjects(state.value).inventory ?: return null
        if(action !in setOf(HostProjectAction.Create,HostProjectAction.Update,HostProjectAction.AddFolder) || hostProjectBlockReason(state.value)!=null) return null
        if(action!=HostProjectAction.Create && (id==null || inventory.projects.singleOrNull {it.id==id}?.let {hostProjectBlockReason(state.value,it)==null && visibleHostProjects(state.value).detail?.project==it}!=true)) return null
        return ReviewedHostProjectEditor(inventory,action,id,generation)
    }
    fun hostProjectEditorCurrent(editor:ReviewedHostProjectEditor)=editor.generation==generation && prepareHostProjectEditor(editor.action,editor.id)?.expected===editor.expected
    fun prepareHostProjectChange(request:HostProjectChange)=if(projectRequestCurrent(request)) ReviewedHostProjectChange(request,generation) else null
    fun hostProjectChangeCurrent(review:ReviewedHostProjectChange)=review.generation==generation && projectRequestCurrent(review.request)
    fun hostProjectReviewChanged() {state.value=state.value.copy(error=Words.ManagementReviewChanged)}
    private fun projectReport(owner:ManagementTarget,home:String,receipt:ManagementOperation?):HostProjectChangeReport?=receipt?.takeIf {it.apiScope==owner.apiScope && it.resourceScope==owner.resourceScope && it.url==owner.url && it.profile==owner.profile && it.actor==owner.actor && it.projectHome==home && HostProjectAction.receipt(it.action)}?.let {HostProjectChangeReport(owner,home,it)}
    fun performHostProjectChange(review:ReviewedHostProjectChange) {
        if(!hostProjectChangeCurrent(review)) {hostProjectReviewChanged();return}
        val owner=review.target;val home=review.request.expected.homeId
        state.value=state.value.copy(projects=HostProjectsState())
        operation {id->
            try {
                val result=projectChanges.execute(owner,review.request,client(state.value),tokens ?: error("Sign in first."))
                if(id==generation && projectContext()==(owner to home)) state.value=state.value.copy(projects=HostProjectsState(result.inventory,report=projectReport(owner,home,result.receipt)))
            } catch(e:Exception) {
                val receipt=when(e) {is HostProjectChangeUncertain->e.receipt;is HostProjectChangeReadbackPending->e.receipt;is HostProjectChangeRefused->e.receipt;else->null}
                if(e !is CancellationException && id==generation && projectContext()==(owner to home)) state.value=state.value.copy(projects=HostProjectsState(report=projectReport(owner,home,receipt)))
                throw e
            }
        }
    }
    fun prepareHostProjectsRead():ReviewedHostProjectsRead? {
        if(state.value.busy) return null
        return projectContext()?.let {(owner,home)->ReviewedHostProjectsRead(owner,home,generation)}
    }
    fun loadHostProjects(review:ReviewedHostProjectsRead) {
        if(state.value.busy || review.generation!=generation || projectContext()!=(review.target to review.homeId)) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(projects=HostProjectsState())
        operation {id->
            val result=projectReads.read(review.target,review.homeId,client(state.value),tokens ?: error("Sign in first."))
            if(id==generation && projectContext()==(review.target to review.homeId)) state.value=state.value.copy(projects=HostProjectsState(result))
        }
    }
    fun prepareHostProjectDetail(row:HostProjectInfo):ReviewedHostProjectDetail? {
        if(state.value.busy) return null
        val snapshot=visibleHostProjects(state.value).inventory ?: return null
        if(snapshot.projects.none {it===row}) return null
        return ReviewedHostProjectDetail(snapshot.owner,snapshot.homeId,row.id,generation)
    }
    fun loadHostProjectDetail(review:ReviewedHostProjectDetail) {
        val snapshot=visibleHostProjects(state.value).inventory
        if(state.value.busy || review.generation!=generation || projectContext()!=(review.target to review.homeId) || snapshot==null || snapshot.projects.none {it.id==review.id}) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(projects=HostProjectsState())
        operation {id->
            val result=projectReads.detail(review.target,review.homeId,review.id,client(state.value),tokens ?: error("Sign in first."))
            if(id==generation && projectContext()==(review.target to review.homeId)) state.value=state.value.copy(projects=HostProjectsState(snapshot,result))
        }
    }
    fun closeHostProjectDetail() {if(state.value.busy) cancel() else {generation++;state.value=state.value.copy(projects=state.value.projects.copy(detail=null))}}
    fun closeHostProjects() {if(state.value.busy) cancel() else {generation++;state.value=state.value.copy(projects=HostProjectsState())}}
    fun prepareGatewayRead():ReviewedGatewayRead?=if(state.value.busy || !GatewayCronInventory.supportedProfile(state.value.profile)) null else target()?.let {ReviewedGatewayRead(it,generation)}
    fun loadGatewayCron(review:ReviewedGatewayRead) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {id ->
            val current=tokens ?: error("Sign in first.");val api=client(state.value);api.identity(current)
            val inventory=api.gateway(current).use {GatewayCronInventory.parse(it.cronList(review.target.profile),review.target.profile)}
            if(id==generation) state.value=state.value.copy(gateway=GatewayAutomationState(inventory))
        }
    }
    fun loadHostSkills()=operation {id ->
        val target=target() ?: error("Verify management profile first.");val current=tokens ?: error("Sign in first.")
        val api=client(state.value);api.identity(current)
        val rows=SkillProtocol.inventory(api.skills(current,target.profile))
        if(id==generation) state.value=state.value.copy(skills=HostSkillState(rows,true))
    }
    fun loadSkillContent(row:ManagedSkill)=operation {id ->
        val target=target() ?: error("Verify management profile first.");val current=tokens ?: error("Sign in first.")
        val api=client(state.value);api.identity(current)
        val content=SkillProtocol.content(api.skillContent(current,target.profile,row.name),row.name)
        if(id==generation) state.value=state.value.copy(skills=state.value.skills.copy(contentName=row.name,content=content))
    }
    fun closeSkillContent() {state.value=state.value.copy(skills=state.value.skills.copy(contentName="",content=null))}
    fun prepareSkill(row:ManagedSkill,action:SkillAction):ReviewedSkillAction? {
        val saved=state.value
        if(saved.busy || !saved.skills.loaded || row !in saved.skills.rows) return null
        val request=runCatching {SkillRequest(row,action)}.getOrNull() ?: return null
        return target()?.let {ReviewedSkillAction(it,request,generation)}
    }
    fun performSkill(review:ReviewedSkillAction) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {id ->
            val current=tokens ?: error("Sign in first.")
            val result=skillOperations.execute(review.target,review.request,client(state.value),current)
            if(id==generation) state.value=state.value.copy(skills=HostSkillState(result.rows,true),notice=Words.SkillChanged)
        }
    }
    fun observeSkillReceipt(key:String)=operation {id ->
        val target=target() ?: error("Verify management profile first.")
        val record=app.database.dao().managementOperation(key) ?: error("Missing receipt.")
        require(record.resourceScope==target.resourceScope && record.apiScope==target.apiScope && record.action in setOf("SkillToggle","SkillLock"))
        val current=tokens ?: error("Sign in first.");val api=client(state.value);api.identity(current)
        val rows=SkillProtocol.inventory(api.skills(current,target.profile))
        if(id==generation) state.value=state.value.copy(skills=HostSkillState(rows,true),notice=Words.ManagementObservationOnly)
    }
    fun loadCronCreationSetup()=operation {id ->
        val target=target() ?: error("Verify management profile first.");val current=tokens ?: error("Sign in first.")
        val api=client(state.value);api.identity(current)
        val contract=try {CronCreateContract.parse(api.cronSchema(current))} catch(e:HermesHttpException) {if(e.code in setOf(404,405)) throw CronCreationUnsupported() else throw e}
        val deliveries=try {CronDeliveryTarget.inventory(api.cronDeliveries(current,target.profile))} catch(e:HermesHttpException) {if(e.code in setOf(404,405)) listOf(CronDeliveryTarget.local) else throw e}
        if(id==generation) state.value=state.value.copy(cron=state.value.cron.copy(contract=contract,deliveries=(listOf(CronDeliveryTarget.local)+deliveries.filter {it.id!="local"})))
    }
    fun prepareCronCreation(request:CronCreation):ReviewedCronCreation? {
        val saved=state.value;val contract=saved.cron.contract ?: return null
        if(saved.busy || !saved.cron.loaded || !contract.accepts(request)) return null
        return target()?.let {ReviewedCronCreation(it,request,contract,generation)}
    }
    fun createCron(review:ReviewedCronCreation) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {id ->
            val current=tokens ?: error("Sign in first.")
            val result=cronCreations.create(review.target,review.request,review.contract,client(state.value),current)
            if(id==generation) state.value=state.value.copy(cron=state.value.cron.copy(jobs=(listOf(result.job)+state.value.cron.jobs).distinctBy {it.id},detail=result.job,contract=null),notice=Words.HostCronCreatedPaused)
        }
    }
    fun observeCronReceipt(key:String)=operation {id ->
        val target=target() ?: error("Verify management profile first.")
        val record=app.database.dao().managementOperation(key) ?: error("Missing receipt.")
        require(record.resourceScope==target.resourceScope && record.apiScope==target.apiScope && record.action=="CronCreate")
        val current=tokens ?: error("Sign in first.");val api=client(state.value);api.identity(current)
        val rows=ManagedCronJob.inventory(api.cronJobs(current,target.profile),target.profile)
        if(id==generation) state.value=state.value.copy(cron=HostCronState(jobs=rows,loaded=true),notice=Words.ManagementObservationOnly)
    }
    fun loadHostCron()=operation {id ->
        val target=target() ?: error("Verify management profile first.");val current=tokens ?: error("Sign in first.")
        val api=client(state.value);api.identity(current)
        val rows=ManagedCronJob.inventory(api.cronJobs(current,target.profile),target.profile)
        if(id==generation) state.value=state.value.copy(cron=HostCronState(jobs=rows,loaded=true))
    }
    fun loadHostCronDetail(job:ManagedCronJob)=operation {id ->
        val saved=state.value;val target=target() ?: error("Verify management profile first.")
        require(saved.cron.jobs.any {it===job} && job.addressable)
        state.value=state.value.copy(cron=saved.cron.copy(detail=null))
        val current=tokens ?: error("Sign in first.");val api=client(saved);api.identity(current)
        val detail=ManagedCronJob.parse(api.cronJob(current,target.profile,job.id),target.profile);require(detail.id==job.id)
        if(id==generation) state.value=state.value.copy(cron=state.value.cron.copy(detail=detail))
    }
    fun closeHostCronDetail() {state.value=state.value.copy(cron=state.value.cron.copy(detail=null))}
    fun prepareOAuth(server:ManagedServer):ReviewedMcpOAuth? {
        if(state.value.busy || !state.value.loaded || state.value.servers.none {it==server} || !McpOAuthSnapshot.eligible(server)) return null
        return target()?.let {ReviewedMcpOAuth(it,server,generation)}
    }
    fun startOAuth(review:ReviewedMcpOAuth) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {id ->
            val current=tokens ?: error("Sign in first.")
            val (receipt,snapshot)=oauthOperations.start(review.target,review.server,client(state.value),current,BuildConfig.DEBUG && state.value.debugHttp)
            if(id==generation) state.value=state.value.copy(oauth=state.value.oauth+(receipt.key to snapshot),notice=Words.McpOAuthReported)
        }
    }
    fun readOAuth(key:String)=operation {id ->
        val target=target() ?: error("Verify management profile first.");val current=tokens ?: error("Sign in first.")
        state.value=state.value.copy(oauth=state.value.oauth-key)
        val snapshot=oauthOperations.read(target,key,client(state.value),current,BuildConfig.DEBUG && state.value.debugHttp)
        if(id==generation) state.value=state.value.copy(oauth=state.value.oauth+(key to snapshot))
    }
    fun cancelOAuth(key:String,expected:ManagementTarget) {
        if(target()!=expected) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {id ->
            val current=tokens ?: error("Sign in first.")
            state.value=state.value.copy(oauth=state.value.oauth-key)
            oauthOperations.cancel(expected,key,client(state.value),current)
            if(id==generation) state.value=state.value.copy(oauth=state.value.oauth-key,notice=Words.McpOAuthCancelled)
        }
    }
    fun loadCatalog()=operation {id ->
        val target=target() ?: error("Verify management profile first.")
        val current=tokens ?: error("Sign in first.");val api=client(state.value);api.identity(current)
        val entries=McpCatalogEntry.inventory(api.catalog(current,target.profile))
        if(id==generation) state.value=state.value.copy(catalog=entries,catalogLoaded=true)
    }
    fun prepareCatalog(request:McpCatalogInstall):ReviewedCatalogInstall? {
        if(state.value.busy || !state.value.catalogLoaded || state.value.catalog.none {request.entry.same(it)}) return null
        return target()?.let {ReviewedCatalogInstall(it,request,generation)}
    }
    fun installCatalog(review:ReviewedCatalogInstall) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {id ->
            val current=tokens ?: error("Sign in first.")
            catalogOperations.install(review.target,review.request,client(state.value),current)
            if(id==generation) state.value=state.value.copy(catalog=emptyList(),catalogLoaded=false,notice=Words.McpCatalogAccepted)
        }
    }
    fun prepareCreation(creation:McpCreation):ReviewedMcpCreation? {
        if(state.value.busy || !state.value.loaded) return null
        return target()?.let {ReviewedMcpCreation(it,creation,generation)}
    }
    fun createMcp(review:ReviewedMcpCreation) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {id ->
            val current=tokens ?: error("Sign in to management first.")
            val result=creations.create(review.target,review.creation,client(state.value),current)
            if(id==generation) state.value=state.value.copy(servers=result.servers,loaded=true,notice=Words.McpCreated)
        }
    }
    fun prepareMcp(server:ManagedServer,action:McpAction):ReviewedMcpAction? {
        val saved=state.value
        if(saved.busy || !saved.loaded || saved.servers.none {it==server}) return null
        val target=target() ?: return null
        return runCatching {ReviewedMcpAction(target,McpRequest(action,server.name,server.enabled,server.source,if(action==McpAction.Toggle) !server.enabled else null),generation)}.getOrNull()
    }
    fun performMcp(review:ReviewedMcpAction) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {id ->
            val current=tokens ?: error("Sign in to management first.")
            val result=operations.execute(review.target,review.request,client(state.value),current)
            if(id==generation) state.value=state.value.copy(servers=result.servers,loaded=true,notice=if(result.observedOnly) Words.ManagementAlreadyObserved else if(review.request.action==McpAction.Probe) {
                if(Json.parseToJsonElement(result.receipt!!.resultSummary).jsonObject["ok"]?.jsonPrimitive?.booleanOrNull==true) Words.McpProbeReady else Words.McpProbeFailed
            } else Words.ManagementVerified)
        }
    }
    fun observeReceipt(key:String)=operation {id ->
        val target=target() ?: error("Verify management identity first.")
        val receipt=app.database.dao().managementOperation(key)?.takeIf {it.resourceScope==target.resourceScope && it.apiScope==target.apiScope} ?: error("Management receipt no longer exists.")
        val current=tokens ?: error("Sign in to management first.")
        val api=client(state.value);api.identity(current)
        val servers=McpProtocol.inventory(api.mcp(current,target.profile));val row=servers.singleOrNull {it.name==receipt.target}
        if(id==generation) state.value=state.value.copy(servers=servers,loaded=true,observation=ManagementObservation(key,receipt.target,row!=null,row?.enabled))
    }
    fun projectReceipt(key:String):ManagementOperation?=visibleReceipts(state.value,receipts.value).singleOrNull {it.key==key && it.apiScope==state.value.apiScope && HostProjectAction.receipt(it.action)}
    fun prepareManagementUncertainty(key:String):ReviewedManagementUncertainty? {
        val s=state.value
        if(s.busy) return null
        val owner=target() ?: return null
        val row=visibleReceipts(s,receipts.value).singleOrNull {it.key==key && it.state=="unknown"} ?: return null
        val project=row.projectHome.isNotBlank() || row.action==ManagementReceiptVisibility.SharedProjectPending || HostProjectAction.receipt(row.action)
        val home=if(project) projectReceiptHome(s).takeIf {it.isNotBlank() && it==row.projectHome} ?: return null else ""
        return ReviewedManagementUncertainty(key,owner,ManagementReceiptVisibility.instanceAction(row.action) || row.action==ManagementReceiptVisibility.SharedInstancePending,home)
    }
    fun managementUncertaintyCurrent(review:ReviewedManagementUncertainty)=prepareManagementUncertainty(review.key)==review
    fun reviewReceipt(key:String,expected:ManagementTarget,expectedProjectHome:String="") {
        if(target()!=expected) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        val row=visibleReceipts(state.value,receipts.value).singleOrNull {it.key==key}
        val project=row?.let {it.projectHome.isNotBlank() || it.action==ManagementReceiptVisibility.SharedProjectPending || HostProjectAction.receipt(it.action)}==true
        if(project || expectedProjectHome.isNotBlank()) {
            val frozen=ReviewedManagementUncertainty(key,expected,projectHome=expectedProjectHome)
            if(!project || !managementUncertaintyCurrent(frozen)) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
            operation {projectChanges.review(expected,expectedProjectHome,key,client(state.value),tokens ?: error("Sign in first."))}
        } else operation {operations.review(expected,key)}
    }
    fun reviewTarget():ManagementTarget?=target()
    fun disconnect()=operation {id ->
        val scope=state.value.apiScope
        withContext(Dispatchers.IO) {app.credentials.saveManagement(scope,null)}
        if(id==generation) {tokens=null;state.value=ManagementState(apiScope=scope)}
    }
    override fun onCleared() {login?.close();super.onCleared()}
}
