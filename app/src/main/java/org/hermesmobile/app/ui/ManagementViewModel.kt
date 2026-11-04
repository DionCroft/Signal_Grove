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
    val poolCleanup:PoolCleanupReport?=null,val credentialPool:CredentialPoolInventory?=null,val providerFlowKey:String?=null,val providerAccounts:ProviderOAuthInventory?=null,val providerFlows:Map<String,ProviderOAuthObservation> = emptyMap(),val moa:HostMoaSnapshot?=null,val hostAuxModel:HostAuxModelState=HostAuxModelState(),val hostMainModel:HostMainModelState=HostMainModelState(),val hostModels:HostModelInventory?=null,val environment:HostEnvInventory?=null,val plugins:PluginInventoryState=PluginInventoryState(),val hub:SkillHubState=SkillHubState(),val gateway:GatewayAutomationState=GatewayAutomationState(),val skills:HostSkillState=HostSkillState(),val cron:HostCronState=HostCronState(),val oauth:Map<String,McpOAuthSnapshot> = emptyMap(),val catalog:List<McpCatalogEntry> = emptyList(),val catalogLoaded:Boolean=false,val servers:List<ManagedServer> = emptyList(),val loaded:Boolean=false,val busy:Boolean=false,val error:Words?=null,val notice:Words?=null,val observation:ManagementObservation?=null)

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
    fun visibleReceipts(s:ManagementState,rows:List<ManagementOperation>)=ManagementReceiptVisibility.project(rows,s.apiScope,resourceScope(s))
    val receipts=state.map {resourceScope(it) to it.apiScope}.distinctUntilChanged().flatMapLatest {(scope,apiScope)->
        if(scope.isBlank()) flowOf(emptyList()) else app.database.dao().managementVisibleResourceOperations(scope,apiScope).map {ManagementReceiptVisibility.project(it,apiScope,scope)}
    }.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    private var generation=0L
    private var work:Job?=null
    private var login:NativeLogin?=null
    private var tokens:ManagementTokens?=null
    private fun managementError(e:Throwable)=when(e) {
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
            catch(e:Exception) {if(id==generation) state.value=if(e is CredentialPoolReadbackPending) state.value.copy(poolCleanup=e.report?.takeIf {it.owner==target()},error=managementError(e)) else if(e is HostMoaChanged) state.value.copy(moa=e.snapshot,hostModels=e.snapshot?.catalog,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),error=managementError(e)) else if(e is HostAuxModelChanged) state.value.copy(hostModels=e.inventory,hostAuxModel=HostAuxModelState(),hostMainModel=HostMainModelState(),error=managementError(e)) else if(e is HostMainModelChanged) state.value.copy(hostModels=e.inventory,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),error=managementError(e)) else if(e is PluginUpdateChanged) state.value.copy(plugins=PluginInventoryState(inventory=e.inventory),error=managementError(e)) else if(e is PluginRemovalChanged) state.value.copy(plugins=PluginInventoryState(inventory=e.inventory),error=managementError(e)) else if(e is PluginToggleChanged) state.value.copy(plugins=PluginInventoryState(inventory=e.inventory),error=managementError(e)) else if(e is McpReviewChanged) state.value.copy(servers=e.servers,loaded=true,error=managementError(e)) else if(e is HermesHttpException && e.code==401) state.value.copy(identity=null,profiles=emptyList(),moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),environment=null,plugins=PluginInventoryState(),hub=SkillHubState(),gateway=GatewayAutomationState(),skills=HostSkillState(),cron=HostCronState(),oauth=emptyMap(),providerAccounts=null,customEndpoints=null,customEndpointProbe=null,fallback=null,credentialPool=null,poolCleanup=null,providerFlows=emptyMap(),providerFlowKey=null,catalog=emptyList(),catalogLoaded=false,servers=emptyList(),loaded=false,error=managementError(e)) else state.value.copy(error=managementError(e))}
            finally {if(id==generation) state.value=state.value.copy(busy=false)}}
    }
    fun signIn(url:String,debugHttp:Boolean,openBrowser:(String)->Unit)=operation {id ->
        val current=state.value.copy(url=url.trim(),debugHttp=debugHttp,identity=null,profiles=emptyList(),profile="",moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),environment=null,plugins=PluginInventoryState(),hub=SkillHubState(),gateway=GatewayAutomationState(),skills=HostSkillState(),cron=HostCronState(),oauth=emptyMap(),providerAccounts=null,customEndpoints=null,customEndpointProbe=null,fallback=null,credentialPool=null,poolCleanup=null,providerFlows=emptyMap(),providerFlowKey=null,catalog=emptyList(),catalogLoaded=false,servers=emptyList(),loaded=false)
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
        generation++;login?.close();login=null;work?.cancel();state.value=state.value.copy(busy=false,moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),environment=null,oauth=emptyMap(),providerAccounts=null,customEndpoints=null,customEndpointProbe=null,fallback=null,credentialPool=null,poolCleanup=null,providerFlows=emptyMap(),providerFlowKey=null,plugins=state.value.plugins.copy(lookup=null,inspection=null,installReport=null,toggleReport=null,updateDecision=null,updateReport=null,updateResult=null,settings=null),hub=state.value.hub.copy(preview=null,scan=null))
    }
    fun refresh(renew:Boolean=false)=operation {id ->
        val saved=state.value;val original=tokens ?: error("Sign in to management first.")
        val api=client(saved)
        val current=if(renew) api.refresh(original).also {save(saved,it);if(id==generation) tokens=it} else original
        require(current.expiresAt>System.currentTimeMillis()/1000) {"Management sign-in expired. Renew it or sign in again."}
        val identity=api.identity(current);val profiles=api.profiles(current)
        if(id!=generation) return@operation
        val profile=saved.profile.takeIf {name->profiles.any {it.name==name}}.orEmpty()
        state.value=state.value.copy(identity=identity,profiles=profiles,profile=profile,moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),environment=null,plugins=PluginInventoryState(),hub=SkillHubState(),gateway=GatewayAutomationState(),skills=HostSkillState(),cron=HostCronState(),oauth=emptyMap(),providerAccounts=null,customEndpoints=null,customEndpointProbe=null,fallback=null,credentialPool=null,poolCleanup=null,providerFlows=emptyMap(),providerFlowKey=null,catalog=emptyList(),catalogLoaded=false,servers=emptyList(),loaded=false)
        if(profile.isNotBlank()) loadServers(id,api,current,profile)
    }
    fun chooseProfile(profile:String)=operation {id ->
        val saved=state.value;require(saved.identity!=null && saved.profiles.any {it.name==profile}) {"Refresh verified management profiles first."}
        val current=tokens ?: error("Sign in to management first.")
        require(current.expiresAt>System.currentTimeMillis()/1000) {"Management sign-in expired. Renew it or sign in again."}
        val selected=saved.copy(profile=profile,moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),environment=null,plugins=PluginInventoryState(),hub=SkillHubState(),gateway=GatewayAutomationState(),skills=HostSkillState(),cron=HostCronState(),oauth=emptyMap(),providerAccounts=null,customEndpoints=null,customEndpointProbe=null,fallback=null,credentialPool=null,poolCleanup=null,providerFlows=emptyMap(),providerFlowKey=null,catalog=emptyList(),catalogLoaded=false,servers=emptyList(),loaded=false)
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
    fun closeHostEnvironment() {if(state.value.busy) cancel() else {generation++;state.value=state.value.copy(moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),environment=null)}}
    fun prepareHostEnvRead():ReviewedHostEnvRead?=if(state.value.busy || !GatewayCronInventory.supportedProfile(state.value.profile)) null else target()?.let {ReviewedHostEnvRead(it,generation)}
    fun loadHostEnvironment(review:ReviewedHostEnvRead) {
        if(review.generation!=generation || target()!=review.target) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),environment=null)
        operation {id->val result=hostEnvironment.read(review.target,client(state.value),tokens ?: error("Sign in first."));if(id==generation) state.value=state.value.copy(environment=result)}
    }
    fun prepareHostEnv(key:String,action:HostEnvAction,value:String=""):ReviewedHostEnv? {
        val inventory=state.value.environment ?: return null
        if(state.value.busy || inventory.owner!=target() || receipts.value.any {it.state in setOf("sending","unknown")}) return null
        return runCatching {ReviewedHostEnv(inventory.owner,HostEnvRequest(inventory,key,action,value),generation)}.getOrNull()
    }
    fun changeHostEnvironment(review:ReviewedHostEnv) {
        if(review.generation!=generation || target()!=review.target || review.request.expected!==state.value.environment) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        state.value=state.value.copy(moa=null,hostModels=null,hostMainModel=HostMainModelState(),hostAuxModel=HostAuxModelState(),environment=null)
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
    fun reviewReceipt(key:String,expected:ManagementTarget) {
        if(target()!=expected) {state.value=state.value.copy(error=Words.ManagementReviewChanged);return}
        operation {operations.review(expected,key)}
    }
    fun reviewTarget():ManagementTarget?=target()
    fun disconnect()=operation {id ->
        val scope=state.value.apiScope
        withContext(Dispatchers.IO) {app.credentials.saveManagement(scope,null)}
        if(id==generation) {tokens=null;state.value=ManagementState(apiScope=scope)}
    }
    override fun onCleared() {login?.close();super.onCleared()}
}
