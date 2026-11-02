package org.hermesmobile.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.HermesApplication
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class HermesViewModel(application: Application) : AndroidViewModel(application) {
    private val graph=application as HermesApplication
    private val dao=graph.database.dao()
    private val connection=HermesConnectionRepository(application)
    private val sessionRepo=HermesSessionRepository(dao)
    private val jobsRepo=HermesJobsRepository(dao)
    private val interactionsRepo=InteractionRepository(dao)
    val preferences=graph.preferences.flow.stateIn(viewModelScope,SharingStarted.Eagerly,AppPreferences())
    private val scope=MutableStateFlow("demo")
    val profileScope: StateFlow<String> get()=scope.asStateFlow()
    val jobBusy=MutableStateFlow(false)
    val serverOperations=scope.flatMapLatest {dao.serverOperations(it)}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val sessionDetailsLoading=MutableStateFlow(false)
    val jobOperations=scope.flatMapLatest {dao.jobOperations(it)}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val selected=MutableStateFlow("")
    val conversations=scope.flatMapLatest { dao.conversations(it) }.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val messages=selected.flatMapLatest { if(it.isBlank()) flowOf(emptyList()) else dao.messages(it) }.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val tasks=dao.tasks().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val connectionStatus=MutableStateFlow("demo")
    val capabilities=MutableStateFlow<HermesCapabilities?>(null)
    val discovery=MutableStateFlow(DiscoveryState())
    private val cachedJobs=scope.flatMapLatest {dao.cachedJobs(it)}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val jobs=cachedJobs.map {rows -> rows.map {it.remote()}}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val jobsFetchedAt=cachedJobs.map {it.maxOfOrNull {j -> j.fetchedAt} ?: 0L}.stateIn(viewModelScope,SharingStarted.Eagerly,0L)
    val sessionsLoading=MutableStateFlow(false)
    val sessionsHasMore=MutableStateFlow(false)
    val historyLoading=MutableStateFlow(false)
    private var sessionsOffset=0
    val jobsStatus=MutableStateFlow("idle")
    val error=MutableStateFlow<String?>(null)
    val busy=MutableStateFlow(false)
    val connecting=MutableStateFlow(true)
    val runStatus=MutableStateFlow("idle")
    val activity=MutableStateFlow("")
    val controls=selected.flatMapLatest {if(it.isBlank()) flowOf(emptyList()) else dao.controls(it)}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val toolEvents=selected.flatMapLatest {if(it.isBlank()) flowOf(emptyList()) else dao.events(it)}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val controlBusy=MutableStateFlow(false)
    val selectedConversation=combine(selected,conversations) {key,rows -> rows.firstOrNull {it.key==key}}.stateIn(viewModelScope,SharingStarted.Eagerly,null)
    val draft=MutableStateFlow("")
    val requestedRuntime=combine(preferences,scope) {p,partition -> RuntimeChoice.saved(p.runtimeChoices,partition)}.stateIn(viewModelScope,SharingStarted.Eagerly,RuntimeChoice())
    private var api: HermesApiClient=UnavailableHermesApiClient()
    private data class ConnectionContext(val client: HermesApiClient,val partition: String)
    private fun context()=ConnectionContext(api,scope.value)
    private fun current(context: ConnectionContext)=api===context.client && scope.value==context.partition
    private fun switchingBlocked()=busy.value || connecting.value || jobBusy.value || controlBusy.value
    private var worker: Job?=null
    init {viewModelScope.launch {try {dao.recoverControls();dao.recoverJobOperations();dao.recoverServerOperations();activate(graph.preferences.flow.first())} finally {connecting.value=false}}}
    fun dismissError() { error.value=null }
    private suspend fun activate(p: AppPreferences) {
        selected.value=""; capabilities.value=null;discovery.value=DiscoveryState();sessionDetailsLoading.value=false; sessionsOffset=0; sessionsHasMore.value=false; jobsStatus.value="idle"
        if(p.demo) {
            api=FakeHermesApiClient(); scope.value="demo"; connectionStatus.value="demo"
            capabilities.value=api.capabilities(); refreshJobs()
        } else {
            connectionStatus.value="offline"; api=UnavailableHermesApiClient()
            var configured=false
            try {
                val config=withContext(Dispatchers.IO) {graph.credentials.readConfig(p)} ?: throw IllegalArgumentException("Enter your server connection again.")
                api=connection.client(config); scope.value=config.scope;configured=true
                capabilities.value=connection.test(api)
                connectionStatus.value="connected";loadSessions();refreshJobs()
            } catch(e: Exception) {if(!configured) scope.value="unconfigured";error.value=connectionError(e)}
        }
    }
    fun updatePreferences(p: AppPreferences,base: AppPreferences=preferences.value) {viewModelScope.launch {graph.preferences.patchUserSettings(p,base)}}
    fun setDemo(enabled: Boolean) {
        if(switchingBlocked()) return
        connecting.value=true
        viewModelScope.launch { try {graph.preferences.connection(demoValue=enabled);activate(graph.preferences.flow.first())} finally {connecting.value=false} }
    }
    fun configure(url: String, token: String, debugHttp: Boolean) {
        if(switchingBlocked()) return
        connecting.value=true
        viewModelScope.launch {
            error.value=null
            try {
                val old=preferences.value
                val previous=withContext(Dispatchers.IO) {graph.credentials.readConfig(old)}
                val saved=if(token.isBlank() && previous?.url?.trimEnd('/')==url.trim().trimEnd('/')) previous.token else token.trim()
                val config=ServerConfig(url.trim(),saved,debugHttp)
                val candidate=connection.client(config)
                val discovered=connection.test(candidate)
                withContext(Dispatchers.IO) { graph.credentials.writeConfig(config) }
                graph.preferences.connection(urlValue=config.url,demoValue=false,httpValue=debugHttp)
                api=candidate; scope.value=config.scope; selected.value=""; capabilities.value=discovered;discovery.value=DiscoveryState();sessionDetailsLoading.value=false
                connectionStatus.value="connected"; sessionsOffset=0; sessionsHasMore.value=false; jobsStatus.value="idle"; sessionsLoading.value=false; error.value=null
                loadSessions(); refreshJobs()
            } catch(e: Exception) { error.value=connectionError(e) }
            finally { connecting.value=false }
        }
    }
    fun refresh() { if(switchingBlocked()) return; connecting.value=true; viewModelScope.launch {
        error.value=null
        try {
            capabilities.value=connection.test(api)
            connectionStatus.value=if(preferences.value.demo) "demo" else "connected"
            loadSessions(); refreshJobs()
        } catch(e: Exception) { connectionStatus.value="offline"; error.value=connectionError(e) }
        finally { connecting.value=false }
    } }
    private suspend fun loadSessions(reset: Boolean = true) {
        if(scope.value=="demo" || capabilities.value?.sessions!=true) return
        val partition=scope.value;val client=api
        sessionsLoading.value=true
        try {
            val page=sessionRepo.importSessions(client,partition,if(reset) 0 else sessionsOffset)
            if(scope.value==partition) {sessionsOffset=page.nextOffset;sessionsHasMore.value=page.hasMore}
        } catch(e: CancellationException) {throw e}
        catch(e: Exception) {if(scope.value==partition) error.value=connectionError(e)}
        finally {if(scope.value==partition) sessionsLoading.value=false}
    }
    fun refreshSessions(more: Boolean = false) {if(busy.value || sessionsLoading.value) return;viewModelScope.launch {loadSessions(!more)}}
    fun refreshHistory() {
        if(busy.value || controlBusy.value || connecting.value || historyLoading.value) return
        val key=selected.value;val operation=context()
        if(key.isBlank() || scope.value=="demo" || capabilities.value?.sessions!=true) return
        historyLoading.value=true
        viewModelScope.launch {try {
            dao.conversation(key)?.takeIf {it.scope==operation.partition}?.let {sessionRepo.importMessages(operation.client,it)}
        } catch(e: CancellationException) {throw e}
        catch(e: Exception) {if(current(operation) && selected.value==key) error.value=connectionError(e)}
        finally {historyLoading.value=false} }
    }
    fun pinConversation(key: String) {viewModelScope.launch {
        dao.conversation(key)?.takeIf {it.scope==scope.value}?.let {dao.save(it.copy(pinned=!it.pinned))}
    }}
    fun hideConversation(key: String) {if(busy.value) return;viewModelScope.launch {
        dao.conversation(key)?.takeIf {it.scope==scope.value}?.let {dao.save(it.copy(hidden=!it.hidden))}
    }}
    fun renameConversation(key: String,title: String) {if(switchingBlocked() || title.isBlank()) return;val operation=context();controlBusy.value=true;viewModelScope.launch {
        try {
            val c=dao.conversation(key)?.takeIf {it.scope==operation.partition} ?: return@launch
            if(c.scope!="demo" && c.transport!="compat" && capabilities.value?.sessions==true) {
                require(capabilities.value?.endpoints?.containsKey("session_update")==true) {"This server does not advertise session renaming."}
                operation.client.renameSession(c.remoteId,title.trim())
            }
            dao.conversation(key)?.let {dao.save(it.copy(title=title.trim()))}
        } catch(e: Exception) {error.value=connectionError(e)} finally {controlBusy.value=false}
    }}
    fun deleteConversation(key: String,server: Boolean) {if(switchingBlocked()) return;val operation=context();controlBusy.value=true;viewModelScope.launch {
        try {
            val c=dao.conversation(key)?.takeIf {it.scope==operation.partition} ?: return@launch
            require(c.status in terminal || c.status=="idle") {"Check the pending run before deleting this conversation."}
            if(server) {
                require(c.transport!="compat") {"This compatibility conversation has no verified server session identity."}
                require(capabilities.value?.endpoints?.containsKey("session_delete")==true) {"Server conversation deletion is unavailable."}
                operation.client.deleteSession(c.remoteId)
            }
            withContext(Dispatchers.IO) {graph.requests.deleteConversation(c.scope,c.key)}
            dao.deleteConversation(key)
            if(selected.value==key) {selected.value="";runStatus.value="idle"}
        } catch(e: Exception) {error.value=connectionError(e)} finally {controlBusy.value=false}
    }}
    fun refreshSessionDetails(expected: Conversation) {
        if(sessionDetailsLoading.value || expected.transport=="compat" || expected.scope!=scope.value || connecting.value || capabilities.value?.endpoints?.containsKey("session")!=true) return
        val operation=context();sessionDetailsLoading.value=true
        viewModelScope.launch {
            try {
                val result=operation.client.sessionDetails(expected.remoteId)
                require(result.text("id")==expected.remoteId)
                if(current(operation)) dao.saveSessionDetails(expected,JsonObject(SessionProtocol.sessionSnapshot(result,expected.remoteId)+mapOf("_fetched_at" to JsonPrimitive(System.currentTimeMillis()))))
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {if(current(operation)) error.value=connectionError(e)}
            finally {if(current(operation)) sessionDetailsLoading.value=false}
        }
    }
    fun reviewServerOutcome(key: String) {if(controlBusy.value) return;val partition=scope.value;viewModelScope.launch {dao.reviewServerOperation(key,partition)}}
    fun sessionAction(expected: Conversation,action: SessionAction,body: JsonObject) {
        if(connecting.value || controlBusy.value || jobBusy.value || (busy.value && action!=SessionAction.Steer)) return
        if(expected.scope!=scope.value || capabilities.value?.endpoints?.containsKey(action.endpoint)!=true || capabilities.value?.has(action.endpoint)!=true) {error.value=Words.NotAdvertised.localized(preferences.value);return}
        val operation=context();controlBusy.value=true
        viewModelScope.launch {
            try {
                val c=dao.conversation(expected.key) ?: return@launch
                require(c.transport!="compat") {"This compatibility conversation is stored locally; its server session identity is not verified."}
                require(c.sameSubmission(expected) && c.remoteId==expected.remoteId && c.key==selected.value) {"Select and review the current conversation first."}
                val target=if(action==SessionAction.Steer) c.runId else c.remoteId
                if(action==SessionAction.Steer) {
                    require(c.runId.isNotBlank() && operation.client.run(c.runId).text("status")=="running") {"The run no longer accepts steering."}
                    require(dao.conversation(c.key)?.sameSubmission(c)==true) {"This run changed while checking its state."}
                } else require(c.status in (RunStates.authoritativeTerminal+"idle")) {"Check the previous execution on Hermes before changing this session."}
                val ack=SessionOperationsRepository(dao).perform(operation.client,c,action,target,body)
                if(action==SessionAction.Fork) {
                    val remote=ack["session"]!!.jsonObject
                    val child=Conversation("${c.scope}:${remote.text("id")}",c.scope,remote.text("id"),remote.text("title"),
                        channelKey=if(capabilities.value?.features?.text("session_key_header")=="X-Hermes-Session-Key") "mobile_${UUID.randomUUID()}" else "",detailsJson=remote.toString())
                    dao.save(child);selected.value=child.key;runStatus.value="idle"
                    try {if(c.scope=="demo") dao.saveMessages(dao.history(c.key).map {it.copy(id="branch_${child.key}_${it.id}",sessionKey=child.key)}) else sessionRepo.importMessages(operation.client,child)} catch(e: Exception) {error.value=connectionError(e)}
                } else if(action==SessionAction.Steer) activity.value=Words.SteerDelivered.localized(preferences.value)
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {if(current(operation)) error.value=connectionError(e)}
            finally {controlBusy.value=false}
        }
    }
    fun chooseRuntime(expectedScope: String,choice: RuntimeChoice) {
        if(busy.value || connecting.value || controlBusy.value || expectedScope!=scope.value) return
        try {choice.validate()} catch(e: Exception) {error.value=connectionError(e);return}
        if(!choice.isDefault && capabilities.value?.has("model_options")!=true && capabilities.value?.endpoints?.containsKey("model_options")!=true) {error.value=Words.NotAdvertised.localized(preferences.value);return}
        controlBusy.value=true
        viewModelScope.launch {
            try {graph.preferences.runtime(expectedScope,choice)}
            catch(e: Exception) {error.value=connectionError(e)}
            finally {controlBusy.value=false}
        }
    }
    fun refreshDiscovery(resource: DiscoveryResource,force: Boolean=false,fullModelProbe: Boolean=false): Job? {
        if(connecting.value || resource in discovery.value.loading || (!force && resource in discovery.value.results)) return null
        if(capabilities.value?.endpoints?.containsKey(resource.endpoint)!=true) return null
        val operation=context()
        discovery.value=discovery.value.copy(loading=discovery.value.loading+resource,errors=discovery.value.errors-resource)
        return viewModelScope.launch {
            try {
                val result=operation.client.discovery(resource,fullModelProbe && resource==DiscoveryResource.Models)
                if(current(operation)) discovery.value=discovery.value.copy(results=discovery.value.results+(resource to result),fetchedAt=discovery.value.fetchedAt+(resource to System.currentTimeMillis()))
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {if(current(operation)) discovery.value=discovery.value.copy(errors=discovery.value.errors+(resource to connectionError(e)))}
            finally {if(current(operation)) discovery.value=discovery.value.copy(loading=discovery.value.loading-resource)}
        }
    }
    fun refreshJobs() {
        if(jobsStatus.value=="loading") return
        val client=api;val partition=scope.value
        jobsStatus.value="loading"
        viewModelScope.launch {
            try {jobsRepo.refresh(client,partition);if(scope.value==partition) jobsStatus.value="ready"}
            catch(e: CancellationException) {throw e}
            catch(e: HermesHttpException) {if(scope.value==partition) jobsStatus.value=if(e.code in listOf(404,405,501)) "unsupported" else "error"}
            catch(e: Exception) {if(scope.value==partition) jobsStatus.value="error"}
        }
    }
    suspend fun jobDetail(id: String): HermesJob {
        val operation=context()
        val result=jobsRepo.detail(operation.client,operation.partition,id,connectionStatus.value in listOf("connected","demo") && jobsStatus.value=="ready")
        check(current(operation)) {"The connection changed. Open job details again."}
        return result
    }
    fun mutateJob(expectedScope: String,operation: String,id: String,body: JsonObject) {
        if(switchingBlocked()) return
        if(expectedScope!=scope.value || preferences.value.automationScope!=scope.value) {error.value="Enable job changes for this connection first.";return}
        val client=api;val partition=scope.value
        jobBusy.value=true
        viewModelScope.launch {
            try {
                require(connectionStatus.value in setOf("connected","demo") && jobsStatus.value=="ready") {"Refresh online job details before changing this server."}
                JobOperationsRepository(dao).perform(client,partition,operation,id,body)
                // Refresh failure does not turn a confirmed mutation into an uncertain one.
                refreshJobs()
            } catch(e: Exception) {error.value=connectionError(e)}
            finally {jobBusy.value=false}
        }
    }
    fun acknowledgeJobOperation(key: String) {if(jobBusy.value) return;viewModelScope.launch {
        if(dao.unresolvedOperations(scope.value).any {it.key==key && it.state=="unknown"}) dao.jobOperationState(key,"reviewed")
    }}
    fun newChat() { if(busy.value || controlBusy.value) return; selected.value=""; runStatus.value="idle"; activity.value=""; draft.value="" }
    fun select(key: String) {
        if(busy.value || controlBusy.value || connecting.value) return
        val operation=context()
        selected.value=key; activity.value=""
        viewModelScope.launch {
            val c=dao.conversation(key)?.takeIf {it.scope==operation.partition} ?: return@launch
            if(!current(operation) || selected.value!=key) return@launch
            runStatus.value=c.status
            if(capabilities.value?.sessions == true && c.scope != "demo") try {sessionRepo.importMessages(operation.client,c)} catch(e: Exception) {if(current(operation) && selected.value==key) error.value=connectionError(e)}
            if(current(operation) && selected.value==key && c.runId.isNotBlank() && c.status !in terminal) reconcile(c,operation=operation)
        }
    }
    fun send(text: String = draft.value) {
        if(text.length>64000) {error.value=Words.MessageTooLong.localized(preferences.value);return}
        if(text.isBlank() || busy.value || controlBusy.value || connecting.value) return
        if(!preferences.value.demo && connectionStatus.value!="connected") { error.value="Connect to your Hermes server in Settings first."; return }
        busy.value=true
        worker=viewModelScope.launch {
            error.value=null; activity.value=""
            var receipt:Conversation?=null
            try {
                require(dao.unresolvedServerOperations(scope.value).isEmpty()) {Words.ReviewServerOutcome.localized(preferences.value)}
                var c=selected.value.takeIf { it.isNotBlank() }?.let { dao.conversation(it) }
                if(c != null && c.status !in terminal && c.status != "idle") { error.value="Check the previous run status before starting another turn."; return@launch }
                val transport=when {c?.transport=="compat"->"compat";preferences.value.demo->"demo";capabilities.value?.sessions==true && capabilities.value?.runs==true->"runs";capabilities.value?.nativeStream==true->"session";else->"compat"}
                if(c == null) {
                    val id="api_mobile_${UUID.randomUUID().toString().replace("-","")}"
                    val remote=if(transport!="compat" && capabilities.value?.sessions==true) api.createSession(id,text.take(60)) else RemoteSession(id,text.take(60))
                    c=Conversation("${scope.value}:${remote.id}",scope.value,remote.id,remote.title,
                        channelKey=if(capabilities.value?.features?.text("session_key_header")=="X-Hermes-Session-Key") "mobile_${UUID.randomUUID()}" else "")
                    dao.save(c); selected.value=c.key
                }
                val key=UUID.randomUUID().toString()
                c=c.copy(pendingInput=text,pendingKey=key,pendingAt=System.currentTimeMillis(),transport=transport,runId="",status="submitting",lastEventSeq=-1,admissionId="",executionGeneration=null,updatedAt=System.currentTimeMillis())
                val user=ChatMessage("${key}_user",c.key,"user",text)
                val requested=RuntimeChoice.saved(graph.preferences.flow.first().runtimeChoices,c.scope)
                require(requested.isDefault || capabilities.value?.has("model_options")==true || capabilities.value?.endpoints?.containsKey("model_options")==true) {Words.NotAdvertised.localized(preferences.value)}
                val request=TurnRequest.create(c,dao.history(c.key)+user,capabilities.value?.model ?: "hermes-agent",requested)
                val hash=withContext(Dispatchers.IO) {graph.requests.freeze(c.scope,c.key,key,request)}
                c=c.copy(pendingRequestHash=hash)
                dao.beginTurn(c,user,ChatMessage("${key}_assistant",c.key,"assistant","",System.currentTimeMillis()+1,"submitting"))
                receipt=c
                withContext(Dispatchers.IO) {runCatching {graph.requests.retainCurrent(c.scope,c.key,key)}}
                draft.value=""
                execute(c)
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) {error.value=if(e is RequestTooLarge) Words.RequestTooLarge.localized(preferences.value) else connectionError(e);receipt?.let {dao.submissionUnknown(it);runStatus.value=dao.conversation(it.key)?.status ?: "unknown"}}
            finally { busy.value=false }
        }
    }
    private suspend fun savedRequest(c: Conversation): TurnRequest {
        if(c.pendingRequestHash.isNotBlank()) return withContext(Dispatchers.IO) {graph.requests.read(c.scope,c.key,c.pendingKey,c.pendingRequestHash)}
        // Pre-upgrade Runs receipts had exactly these two fields and no channel header.
        require(c.transport=="runs" && c.channelKey.isBlank()) {"This older receipt cannot safely be rebuilt. Check its server status."}
        return TurnRequest("runs","v1/runs",buildJsonObject {put("session_id",c.remoteId);put("input",c.pendingInput)},"")
    }
    private suspend fun execute(initial: Conversation,resuming: Boolean = false) {
        var c=initial
        val operation=context();val client=operation.client
        require(initial.scope==operation.partition)
        val assistant=dao.history(c.key).firstOrNull {it.id=="${c.pendingKey}_assistant"}
            ?: throw IllegalArgumentException("This run has no saved response receipt. Refresh its history after it settles.")
        var text=if(resuming) assistant.content else "";var terminalSeen=false
        val path: String;var body: JsonObject?=null
        runStatus.value="running"
        val request=if(c.transport=="runs" && c.runId.isNotBlank()) null else savedRequest(c)
        if(request!=null) require(request.transport==c.transport && request.channelKey==c.channelKey) {"The saved request identity does not match."}
        when(c.transport) {
            "runs" -> {if(c.runId.isBlank()) {c=dao.recordAdmission(c,client.submitRun(request!!.body,c.pendingKey,request.channelKey))};path="v1/runs/${OkHttpHermesApiClient.segment(c.runId)}/events"}
            else -> {path=request!!.path;body=request.body}
        }
        val monitor=viewModelScope.launch {
            while(isActive) {
                delay(2500)
                if(c.runId.isNotBlank() && c.transport!="compat") try {
                    interactionsRepo.synchronize(c,client.run(c.runId))
                } catch(e: CancellationException) {throw e} catch(_: Exception) { /* The stream remains authoritative; recovery is explicit. */ }
            }
        }
        try {
            client.stream(path,body,c.pendingKey,if(resuming) c.lastEventSeq.takeIf {it>=0} else null,c.channelKey)
                .transformWhile {emit(it);it.status !in RunStates.authoritativeTerminal}
                .collect {update ->
                    if(update.replayGap) {c=c.copy(lastEventSeq=-2);throw IllegalArgumentException("The server's event replay expired. Check run status; this request will not be resent.")}
                    if(update.sequence!=null && update.sequence<=c.lastEventSeq) return@collect
                    if(update.runId.isNotBlank()) c=c.copy(runId=update.runId)
                    if(update.sessionId.isNotBlank()) c=c.copy(remoteId=update.sessionId)
                    text+=update.delta;update.finalText?.let {text=it}
                    update.activity?.let {activity.value=it.take(1000)}
                    val status=update.status?.takeUnless {c.transport!="compat" && it in RunStates.responseEnded}
                        ?: "response_finished".takeIf {update.responseBoundary && c.transport=="compat" && c.status !in RunStates.responseEnded && c.status !in RunStates.authoritativeTerminal}
                    status?.let {runStatus.value=it;c=c.copy(status=it);terminalSeen=it in terminal}
                    update.sequence?.let {c=c.copy(lastEventSeq=it)}
                    val control=update.control?.copy(runId=update.control.runId.ifBlank {c.runId})?.stored(c.key)
                    val event=update.tool?.let {t -> ToolEvent("${c.key}|${c.runId}|${update.sequence ?: java.util.UUID.randomUUID()}",c.key,c.runId,t.tool,t.kind,t.preview,t.durationMs,System.currentTimeMillis())}
                    dao.saveTurn(c,assistant.copy(content=text,status=c.status),control,event,update.settledRequestId,update.settledGeneration)
                }
            if(!terminalSeen) {
                c=c.copy(status=if(c.transport=="compat" && c.status=="response_finished") "response_finished" else "unknown")
                runStatus.value=c.status;dao.saveTurn(c,assistant.copy(content=text,status=c.status),null,null,"")
                if(c.runId.isNotBlank()) reconcile(c,operation=operation)
            }
        } catch(e: CancellationException) {throw e}
        catch(e: Exception) {
            if(!terminalSeen) {c=c.copy(status="unknown");dao.saveTurn(c,assistant.copy(content=text,status="unknown"),null,null,"");runStatus.value="unknown";throw e}
        } finally {monitor.cancel()}
    }
    fun prepareRetry() { viewModelScope.launch {
        val c=dao.conversation(selected.value) ?: return@launch
        if(c.status in setOf("failed","cancelled","interrupted","incomplete","response_failed","response_cancelled","response_incomplete")) draft.value=c.pendingInput
    } }
    fun retryOrRefresh() {
        if(busy.value || controlBusy.value || connecting.value) return
        busy.value=true
        worker=viewModelScope.launch {
            val c=dao.conversation(selected.value)
            try {
                if(c==null || c.scope!=scope.value) return@launch
                error.value=null
                when {
                    c.runId.isNotBlank()->{
                        val fresh=reconcile(c,true)
                        if(fresh!=null && fresh.status in RunStates.active && fresh.transport=="runs" && fresh.lastEventSeq>=-1) execute(fresh,true)
                    }
                    c.transport=="runs" && System.currentTimeMillis()-c.pendingAt<23*60*60*1000L -> execute(c)
                    else->error.value="This server cannot safely replay this request. Review its history before starting a new conversation."
                }
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {error.value=connectionError(e)}
            finally {busy.value=false}
        }
    }
    private suspend fun reconcile(c: Conversation,resetUnknown: Boolean = false,operation: ConnectionContext=context()): Conversation? {
        if(c.scope!=operation.partition) return null
        try {
            val j=operation.client.run(c.runId)
            if(!current(operation)) return null
            require(j.text("run_id").ifBlank {c.runId}==c.runId) {"The server returned a different run identity."}
            val fresh=dao.applyRunSnapshot(c,j) ?: return null
            interactionsRepo.synchronize(fresh,j,resetUnknown)
            if(current(operation) && selected.value==c.key) runStatus.value=fresh.status
            return fresh
        } catch(e: CancellationException) {throw e}
        catch(e: Exception) {if(current(operation) && selected.value==c.key) error.value=connectionError(e);return null}
    }
    fun respondControl(control: PendingControl,response: String) {
        if(controlBusy.value || connecting.value) return
        val operation=context()
        controlBusy.value=true
        viewModelScope.launch {
            val c=dao.conversation(control.sessionKey)
            try {
                require(c!=null && c.key==selected.value && c.scope==scope.value) {"Select the current conversation first."}
                interactionsRepo.respond(operation.client,c,control,response)
                activity.value="Response delivered; waiting for the server's run status."
                runStatus.value="response_submitted"
                if(!busy.value) reconcile(c,operation=operation)
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {error.value=connectionError(e)}
            finally {controlBusy.value=false}
        }
    }
    fun acknowledgeUnknown() {
        if(busy.value || connecting.value || controlBusy.value || capabilities.value?.has("run_unknown_resolution")!=true || capabilities.value?.endpoints?.containsKey("run_unknown_resolution")!=true) return
        val operation=context();val key=selected.value
        controlBusy.value=true
        viewModelScope.launch {
            try {
                val c=dao.conversation(key)?.takeIf {it.scope==scope.value} ?: return@launch
                val current=reconcile(c,true,operation) ?: return@launch
                require(current.status=="unknown" && current.admissionId.isNotBlank() && current.executionGeneration!=null) {"Refresh the unknown run identity first."}
                operation.client.resolveUnknown(current.runId,current.admissionId,current.executionGeneration)
                reconcile(current,true,operation)
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {error.value=connectionError(e)}
            finally {controlBusy.value=false}
        }
    }
    fun sendDemoExercise(kind: String) {if(scope.value=="demo" && !busy.value) send(if(kind=="approval") "Demo approval exercise" else "Demo clarification exercise")}
    fun stop() {
        if(controlBusy.value || connecting.value) return
        val operation=context();val key=selected.value
        controlBusy.value=true
        viewModelScope.launch {
            try {
                val c=dao.conversation(key)?.takeIf {it.scope==operation.partition} ?: return@launch
                if(c.scope=="demo") { worker?.cancelAndJoin();dao.applyRunSnapshot(c,buildJsonObject {put("status","cancelled")});dao.deleteRunControls(c.key,c.runId);runStatus.value="cancelled";busy.value=false }
                else if(c.runId.isNotBlank() && capabilities.value?.has("run_stop")==true) {operation.client.stop(c.runId);activity.value="Stop requested";reconcile(c,operation=operation)}
                else error.value="Server cancellation is unavailable. The agent may continue running."
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {if(current(operation)) error.value=connectionError(e)}
            finally {controlBusy.value=false}
        }
    }
    suspend fun taskBackup(): ByteArray=TaskTransfer.encode(dao.tasks().first())
    suspend fun importTasks(rows: List<LocalTask>) {TaskTransfer.validate(rows);dao.importTasks(rows)}
    fun saveTask(task: LocalTask) { if(task.title.isBlank()) return; viewModelScope.launch { dao.save(task.copy(title=task.title.trim())) } }
    fun deleteTask(task: LocalTask) { viewModelScope.launch { dao.delete(task) } }
    fun clearLocalChats() {if(switchingBlocked()) return;viewModelScope.launch {
        try {withContext(Dispatchers.IO) {graph.requests.clearAll()};dao.clearChats();newChat()}
        catch(e: Exception) {error.value=Words.LocalDataError.localized(preferences.value)}
    }}
    fun forgetServer() { if(switchingBlocked()) return; connecting.value=true; viewModelScope.launch {try {
        withContext(Dispatchers.IO) { graph.credentials.clear() }
        graph.preferences.connection(urlValue="",demoValue=true,httpValue=false);activate(graph.preferences.flow.first())
    } finally {connecting.value=false} } }
    companion object { val terminal=RunStates.terminal }
}
