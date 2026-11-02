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
    private val connection=HermesConnectionRepository()
    private val sessionRepo=HermesSessionRepository(dao)
    private val jobsRepo=HermesJobsRepository(dao)
    private val interactionsRepo=InteractionRepository(dao)
    val preferences=graph.preferences.flow.stateIn(viewModelScope,SharingStarted.Eagerly,AppPreferences())
    private val scope=MutableStateFlow("demo")
    val profileScope: StateFlow<String> get()=scope.asStateFlow()
    val jobBusy=MutableStateFlow(false)
    val jobOperations=scope.flatMapLatest {dao.jobOperations(it)}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val selected=MutableStateFlow("")
    val conversations=scope.flatMapLatest { dao.conversations(it) }.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val messages=selected.flatMapLatest { if(it.isBlank()) flowOf(emptyList()) else dao.messages(it) }.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val tasks=dao.tasks().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val connectionStatus=MutableStateFlow("demo")
    val capabilities=MutableStateFlow<HermesCapabilities?>(null)
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
    val connecting=MutableStateFlow(false)
    val runStatus=MutableStateFlow("idle")
    val activity=MutableStateFlow("")
    val controls=selected.flatMapLatest {if(it.isBlank()) flowOf(emptyList()) else dao.controls(it)}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val toolEvents=selected.flatMapLatest {if(it.isBlank()) flowOf(emptyList()) else dao.events(it)}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val controlBusy=MutableStateFlow(false)
    val selectedConversation=combine(selected,conversations) {key,rows -> rows.firstOrNull {it.key==key}}.stateIn(viewModelScope,SharingStarted.Eagerly,null)
    val draft=MutableStateFlow("")
    private var api: HermesApiClient=UnavailableHermesApiClient()
    private var worker: Job?=null
    init { viewModelScope.launch { dao.recoverControls();dao.recoverJobOperations();val p=graph.preferences.flow.first(); activate(p) } }
    fun dismissError() { error.value=null }
    private suspend fun activate(p: AppPreferences) {
        selected.value=""; capabilities.value=null; sessionsOffset=0; sessionsHasMore.value=false; jobsStatus.value="idle"
        if(p.demo) {
            api=FakeHermesApiClient(); scope.value="demo"; connectionStatus.value="demo"
            capabilities.value=api.capabilities(); refreshJobs()
        } else {
            connectionStatus.value="offline"; api=UnavailableHermesApiClient()
            try {
                val config=withContext(Dispatchers.IO) {graph.credentials.readConfig(p)} ?: throw IllegalArgumentException("Enter your server connection again.")
                api=connection.client(config); scope.value=config.scope
                refresh()
            } catch(e: Exception) { scope.value="unconfigured"; error.value=connectionError(e) }
        }
    }
    fun updatePreferences(p: AppPreferences) { viewModelScope.launch { graph.preferences.update(p) } }
    fun setDemo(enabled: Boolean) {
        if(busy.value || connecting.value || jobBusy.value) return
        viewModelScope.launch { val p=preferences.value.copy(demo=enabled); graph.preferences.update(p); activate(p) }
    }
    fun configure(url: String, token: String, debugHttp: Boolean) {
        if(busy.value || connecting.value || jobBusy.value) return
        viewModelScope.launch {
            connecting.value=true; error.value=null
            try {
                val old=preferences.value
                val previous=withContext(Dispatchers.IO) {graph.credentials.readConfig(old)}
                val saved=if(token.isBlank() && previous?.url?.trimEnd('/')==url.trim().trimEnd('/')) previous.token else token.trim()
                val config=ServerConfig(url.trim(),saved,debugHttp)
                val candidate=connection.client(config)
                val discovered=connection.test(candidate)
                withContext(Dispatchers.IO) { graph.credentials.writeConfig(config) }
                val p=old.copy(url=url.trim(),debugHttp=debugHttp,demo=false)
                graph.preferences.update(p)
                api=candidate; scope.value=config.scope; selected.value=""; capabilities.value=discovered
                connectionStatus.value="connected"; sessionsOffset=0; sessionsHasMore.value=false; jobsStatus.value="idle"; sessionsLoading.value=false; error.value=null
                loadSessions(); refreshJobs()
            } catch(e: Exception) { error.value=connectionError(e) }
            finally { connecting.value=false }
        }
    }
    fun refresh() { if(connecting.value || busy.value) return; viewModelScope.launch {
        connecting.value=true; error.value=null
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
        if(busy.value || historyLoading.value) return
        val key=selected.value;val client=api
        if(key.isBlank() || scope.value=="demo" || capabilities.value?.sessions!=true) return
        historyLoading.value=true
        viewModelScope.launch {try {
            dao.conversation(key)?.let {sessionRepo.importMessages(client,it)}
        } catch(e: CancellationException) {throw e}
        catch(e: Exception) {if(selected.value==key) error.value=connectionError(e)}
        finally {historyLoading.value=false} }
    }
    fun pinConversation(key: String) {viewModelScope.launch {
        dao.conversation(key)?.takeIf {it.scope==scope.value}?.let {dao.save(it.copy(pinned=!it.pinned))}
    }}
    fun hideConversation(key: String) {if(busy.value) return;viewModelScope.launch {
        dao.conversation(key)?.takeIf {it.scope==scope.value}?.let {dao.save(it.copy(hidden=!it.hidden))}
    }}
    fun renameConversation(key: String,title: String) {if(busy.value || title.isBlank()) return;viewModelScope.launch {
        val c=dao.conversation(key)?.takeIf {it.scope==scope.value} ?: return@launch
        try {
            if(c.scope!="demo" && capabilities.value?.sessions==true) {
                require(capabilities.value?.endpoints?.containsKey("session_update")==true) {"This server does not advertise session renaming."}
                api.renameSession(c.remoteId,title.trim())
            }
            dao.conversation(key)?.let {dao.save(it.copy(title=title.trim()))}
        } catch(e: Exception) {error.value=connectionError(e)}
    }}
    fun deleteConversation(key: String,server: Boolean) {if(busy.value) return;viewModelScope.launch {
        val c=dao.conversation(key)?.takeIf {it.scope==scope.value} ?: return@launch
        try {
            require(c.status in terminal || c.status=="idle") {"Check the pending run before deleting this conversation."}
            if(server) {
                require(capabilities.value?.endpoints?.containsKey("session_delete")==true) {"Server conversation deletion is unavailable."}
                api.deleteSession(c.remoteId)
            }
            dao.deleteConversation(key)
            if(selected.value==key) newChat()
        } catch(e: Exception) {error.value=connectionError(e)}
    }}
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
    suspend fun jobDetail(id: String) = jobsRepo.detail(api,scope.value,id,connectionStatus.value in listOf("connected","demo") && jobsStatus.value=="ready")
    fun mutateJob(expectedScope: String,operation: String,id: String,body: JsonObject) {
        if(jobBusy.value || busy.value || connecting.value) return
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
    fun newChat() { if(busy.value) return; selected.value=""; runStatus.value="idle"; activity.value=""; draft.value="" }
    fun select(key: String) {
        if(busy.value) return
        selected.value=key; activity.value=""
        viewModelScope.launch {
            val c=dao.conversation(key) ?: return@launch
            runStatus.value=c.status
            if(capabilities.value?.sessions == true && c.scope != "demo") try { val client=api;sessionRepo.importMessages(client,c) } catch(e: Exception) { error.value=connectionError(e) }
            if(selected.value==key && c.runId.isNotBlank() && c.status !in terminal) reconcile(c)
        }
    }
    fun send(text: String = draft.value) {
        if(text.isBlank() || busy.value || connecting.value) return
        if(!preferences.value.demo && connectionStatus.value!="connected") { error.value="Connect to your Hermes server in Settings first."; return }
        busy.value=true
        worker=viewModelScope.launch {
            error.value=null; activity.value=""
            try {
                var c=selected.value.takeIf { it.isNotBlank() }?.let { dao.conversation(it) }
                if(c != null && c.status !in terminal && c.status != "idle") { error.value="Check the previous run status before starting another turn."; return@launch }
                if(c == null) {
                    val id="api_mobile_${UUID.randomUUID().toString().replace("-","")}"
                    val remote=if(capabilities.value?.sessions==true) api.createSession(id,text.take(60)) else RemoteSession(id,text.take(60))
                    c=Conversation("${scope.value}:${remote.id}",scope.value,remote.id,remote.title)
                    dao.save(c); selected.value=c.key
                }
                val key=UUID.randomUUID().toString()
                val transport=when { preferences.value.demo -> "demo"; capabilities.value?.sessions==true && capabilities.value?.runs==true -> "runs"; capabilities.value?.nativeStream==true -> "session"; else -> "compat" }
                c=c.copy(pendingInput=text,pendingKey=key,pendingAt=System.currentTimeMillis(),transport=transport,runId="",status="submitting",lastEventSeq=-1,admissionId="",executionGeneration=null,updatedAt=System.currentTimeMillis())
                dao.save(c)
                dao.save(ChatMessage("${key}_user",c.key,"user",text))
                dao.save(ChatMessage("${key}_assistant",c.key,"assistant","",System.currentTimeMillis()+1,"submitting"))
                draft.value=""
                execute(c)
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { error.value=connectionError(e); runStatus.value="unknown"; markUnknown() }
            finally { busy.value=false }
        }
    }
    private suspend fun markUnknown() { val c=dao.conversation(selected.value) ?: return; if(c.pendingKey.isNotBlank()) dao.save(c.copy(status="unknown")) }
    private suspend fun execute(initial: Conversation,resuming: Boolean = false) {
        var c=initial
        val client=api
        val assistant=dao.history(c.key).firstOrNull {it.id=="${c.pendingKey}_assistant"}
            ?: throw IllegalArgumentException("This run has no saved response receipt. Refresh its history after it settles.")
        var text=if(resuming) assistant.content else "";var terminalSeen=false
        val path: String;var body: JsonObject?=null
        runStatus.value="running"
        when(c.transport) {
            "runs"->{if(c.runId.isBlank()) {c=c.copy(runId=client.submitRun(c.remoteId,c.pendingInput,c.pendingKey));dao.save(c)};path="v1/runs/${OkHttpHermesApiClient.segment(c.runId)}/events"}
            "session","demo"->{path="api/sessions/${OkHttpHermesApiClient.segment(c.remoteId)}/chat/stream";body=buildJsonObject {put("input",c.pendingInput)}}
            else->{
                path="v1/chat/completions"
                val history=dao.history(c.key).filter {it.role=="user" || (it.role=="assistant" && it.status=="completed")}
                body=buildJsonObject {put("model",capabilities.value?.model ?: "hermes-agent");put("stream",true);put("messages",buildJsonArray {history.forEach {m -> add(buildJsonObject {put("role",m.role);put("content",m.content)})}})}
            }
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
            client.stream(path,body,c.pendingKey,if(resuming) c.lastEventSeq.takeIf {it>=0} else null)
                .transformWhile {emit(it);it.status !in terminal.filter {s -> s!="response_finished"}}
                .collect {update ->
                    if(update.replayGap) {c=c.copy(lastEventSeq=-2);throw IllegalArgumentException("The server's event replay expired. Check run status; this request will not be resent.")}
                    if(update.sequence!=null && update.sequence<=c.lastEventSeq) return@collect
                    if(update.runId.isNotBlank()) c=c.copy(runId=update.runId)
                    if(update.sessionId.isNotBlank()) c=c.copy(remoteId=update.sessionId)
                    text+=update.delta;update.finalText?.let {text=it}
                    update.activity?.let {activity.value=it.take(1000)}
                    update.status?.let {status -> runStatus.value=status;c=c.copy(status=status);terminalSeen=status in terminal}
                    update.sequence?.let {c=c.copy(lastEventSeq=it)}
                    val control=update.control?.copy(runId=update.control.runId.ifBlank {c.runId})?.stored(c.key)
                    val event=update.tool?.let {t -> ToolEvent("${c.key}|${c.runId}|${update.sequence ?: java.util.UUID.randomUUID()}",c.key,c.runId,t.tool,t.kind,t.preview,t.durationMs,System.currentTimeMillis())}
                    dao.saveTurn(c,assistant.copy(content=text,status=c.status),control,event,update.settledRequestId)
                }
            if(!terminalSeen) {
                c=c.copy(status=if(c.transport=="compat" && c.status=="response_finished") "response_finished" else "unknown")
                runStatus.value=c.status;dao.saveTurn(c,assistant.copy(content=text,status=c.status),null,null,"")
                if(c.runId.isNotBlank()) reconcile(c)
            }
        } catch(e: CancellationException) {throw e}
        catch(e: Exception) {
            if(!terminalSeen) {c=c.copy(status="unknown");dao.saveTurn(c,assistant.copy(content=text,status="unknown"),null,null,"");runStatus.value="unknown";throw e}
        } finally {monitor.cancel()}
    }
    fun prepareRetry() { viewModelScope.launch {
        val c=dao.conversation(selected.value) ?: return@launch
        if(c.status in setOf("failed","cancelled","interrupted","incomplete")) draft.value=c.pendingInput
    } }
    fun retryOrRefresh() {
        if(busy.value || controlBusy.value) return
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
    private suspend fun reconcile(c: Conversation,resetUnknown: Boolean = false): Conversation? {
        try {
            val j=api.run(c.runId);val status=j.text("status").ifBlank {"unknown"}
            val fresh=(dao.conversation(c.key) ?: return null).copy(status=status,
                remoteId=j.text("session_id").ifBlank {c.remoteId},admissionId=j.text("admission_id").ifBlank {c.admissionId},
                executionGeneration=(j["execution_generation"] as? JsonPrimitive)?.longOrNull ?: c.executionGeneration)
            dao.save(fresh)
            val a=dao.history(c.key).lastOrNull {it.role=="assistant"}
            if(a!=null) dao.save(a.copy(content=j.text("output").ifBlank {a.content},status=status))
            interactionsRepo.synchronize(fresh,j,resetUnknown)
            if(selected.value==c.key && scope.value==c.scope) runStatus.value=status
            return fresh
        } catch(e: CancellationException) {throw e}
        catch(e: Exception) {if(selected.value==c.key) error.value=connectionError(e);return null}
    }
    fun respondControl(control: PendingControl,response: String) {
        if(controlBusy.value) return
        controlBusy.value=true
        viewModelScope.launch {
            val c=dao.conversation(control.sessionKey)
            try {
                require(c!=null && c.key==selected.value && c.scope==scope.value) {"Select the current conversation first."}
                interactionsRepo.respond(api,c,control,response)
                activity.value="Response delivered; waiting for the server's run status."
                runStatus.value="response_submitted"
                if(!busy.value) reconcile(c)
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {error.value=connectionError(e)}
            finally {controlBusy.value=false}
        }
    }
    fun acknowledgeUnknown() {
        if(busy.value || controlBusy.value || capabilities.value?.has("run_unknown_resolution")!=true || capabilities.value?.endpoints?.containsKey("run_unknown_resolution")!=true) return
        controlBusy.value=true
        viewModelScope.launch {
            try {
                val c=dao.conversation(selected.value)?.takeIf {it.scope==scope.value} ?: return@launch
                val current=reconcile(c,true) ?: return@launch
                require(current.status=="unknown" && current.admissionId.isNotBlank() && current.executionGeneration!=null) {"Refresh the unknown run identity first."}
                api.resolveUnknown(current.runId,current.admissionId,current.executionGeneration)
                reconcile(current,true)
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {error.value=connectionError(e)}
            finally {controlBusy.value=false}
        }
    }
    fun sendDemoExercise(kind: String) {if(scope.value=="demo" && !busy.value) send(if(kind=="approval") "Demo approval exercise" else "Demo clarification exercise")}
    fun stop() { viewModelScope.launch {
        val c=dao.conversation(selected.value) ?: return@launch
        try {
            if(c.scope=="demo") { worker?.cancelAndJoin(); dao.save(c.copy(status="cancelled"));dao.deleteRunControls(c.key,c.runId); runStatus.value="cancelled"; busy.value=false }
            else if(c.runId.isNotBlank() && capabilities.value?.has("run_stop")==true) { api.stop(c.runId); activity.value="Stop requested"; reconcile(c) }
            else { error.value="Server cancellation is unavailable. The agent may continue running." }
        } catch(e: Exception) { error.value=connectionError(e) }
    } }
    fun saveTask(task: LocalTask) { if(task.title.isBlank()) return; viewModelScope.launch { dao.save(task.copy(title=task.title.trim())) } }
    fun deleteTask(task: LocalTask) { viewModelScope.launch { dao.delete(task) } }
    fun clearLocalChats() { if(busy.value) return; viewModelScope.launch { dao.clearChats(); newChat() } }
    fun forgetServer() { if(busy.value || connecting.value || jobBusy.value) return; viewModelScope.launch {
        withContext(Dispatchers.IO) { graph.credentials.clear() }
        val p=preferences.value.copy(url="",demo=true,debugHttp=false); graph.preferences.update(p); activate(p)
    } }
    companion object { val terminal=RunStates.terminal }
}
