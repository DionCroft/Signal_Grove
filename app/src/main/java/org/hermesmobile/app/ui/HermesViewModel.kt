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
    private val jobsRepo=HermesJobsRepository()
    val preferences=graph.preferences.flow.stateIn(viewModelScope,SharingStarted.Eagerly,AppPreferences())
    private val scope=MutableStateFlow("demo")
    val selected=MutableStateFlow("")
    val conversations=scope.flatMapLatest { dao.conversations(it) }.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val messages=selected.flatMapLatest { if(it.isBlank()) flowOf(emptyList()) else dao.messages(it) }.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val tasks=dao.tasks().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val connectionStatus=MutableStateFlow("demo")
    val capabilities=MutableStateFlow<HermesCapabilities?>(null)
    val jobs=MutableStateFlow<List<HermesJob>>(emptyList())
    val jobsStatus=MutableStateFlow("idle")
    val error=MutableStateFlow<String?>(null)
    val busy=MutableStateFlow(false)
    val connecting=MutableStateFlow(false)
    val runStatus=MutableStateFlow("idle")
    val activity=MutableStateFlow("")
    val approval=MutableStateFlow<Approval?>(null)
    val draft=MutableStateFlow("")
    private var api: HermesApiClient=UnavailableHermesApiClient()
    private var worker: Job?=null
    init { viewModelScope.launch { val p=graph.preferences.flow.first(); activate(p) } }
    fun dismissError() { error.value=null }
    private suspend fun activate(p: AppPreferences) {
        selected.value=""; capabilities.value=null; jobs.value=emptyList()
        if(p.demo) {
            api=FakeHermesApiClient(); scope.value="demo"; connectionStatus.value="demo"
            capabilities.value=api.capabilities(); refreshJobs()
        } else {
            connectionStatus.value="offline"; api=UnavailableHermesApiClient()
            try {
                val token=withContext(Dispatchers.IO) { graph.credentials.read() }
                val config=ServerConfig(p.url,token,p.debugHttp)
                api=connection.client(config); scope.value=config.scope
                refresh()
            } catch(e: Exception) { scope.value="unconfigured"; error.value=connectionError(e) }
        }
    }
    fun updatePreferences(p: AppPreferences) { viewModelScope.launch { graph.preferences.update(p) } }
    fun setDemo(enabled: Boolean) {
        if(busy.value || connecting.value) return
        viewModelScope.launch { val p=preferences.value.copy(demo=enabled); graph.preferences.update(p); activate(p) }
    }
    fun configure(url: String, token: String, debugHttp: Boolean) {
        if(busy.value || connecting.value) return
        viewModelScope.launch {
            connecting.value=true; error.value=null
            try {
                val old=preferences.value
                val saved=if(token.isBlank() && old.url.trimEnd('/')==url.trim().trimEnd('/')) withContext(Dispatchers.IO) { graph.credentials.read() } else token.trim()
                val config=ServerConfig(url.trim(),saved,debugHttp)
                val candidate=connection.client(config)
                val discovered=connection.test(candidate)
                withContext(Dispatchers.IO) { graph.credentials.write(saved) }
                val p=old.copy(url=url.trim(),debugHttp=debugHttp,demo=false)
                graph.preferences.update(p)
                api=candidate; scope.value=config.scope; selected.value=""; capabilities.value=discovered
                connectionStatus.value="connected"; jobs.value=emptyList(); error.value=null
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
    private suspend fun loadSessions() {
        if(!preferences.value.demo && capabilities.value?.sessions == true) try { sessionRepo.importSessions(api,scope.value) }
        catch(e: Exception) { error.value=connectionError(e) }
    }
    fun refreshJobs() { viewModelScope.launch {
        jobsStatus.value="loading"
        try { jobs.value=jobsRepo.list(api); jobsStatus.value=if(jobs.value.isEmpty()) "empty" else "ready" }
        catch(e: HermesHttpException) { jobs.value=emptyList(); jobsStatus.value=if(e.code in listOf(404,405,501)) "unsupported" else "error" }
        catch(e: Exception) { jobs.value=emptyList(); jobsStatus.value="error" }
    } }
    suspend fun jobDetail(id: String) = jobsRepo.detail(api,id)
    fun newChat() { if(busy.value) return; selected.value=""; runStatus.value="idle"; activity.value=""; approval.value=null; draft.value="" }
    fun select(key: String) {
        if(busy.value) return
        selected.value=key; approval.value=null; activity.value=""
        viewModelScope.launch {
            val c=dao.conversation(key) ?: return@launch
            runStatus.value=c.status
            if(capabilities.value?.sessions == true && c.scope != "demo") try { sessionRepo.importMessages(api,c) } catch(e: Exception) { error.value=connectionError(e) }
            if(c.runId.isNotBlank() && c.status !in terminal) reconcile(c)
        }
    }
    fun send(text: String = draft.value) {
        if(text.isBlank() || busy.value || connecting.value) return
        if(!preferences.value.demo && connectionStatus.value!="connected") { error.value="Connect to your Hermes server in Settings first."; return }
        worker=viewModelScope.launch {
            busy.value=true; error.value=null; activity.value=""; approval.value=null
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
                c=c.copy(pendingInput=text,pendingKey=key,pendingAt=System.currentTimeMillis(),transport=transport,runId="",status="submitting",updatedAt=System.currentTimeMillis())
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
    private suspend fun execute(initial: Conversation) {
        var c=initial
        var text=""; var terminalSeen=false
        runStatus.value="running"
        val path: String; var body: JsonObject?=null
        when(c.transport) {
            "runs" -> { if(c.runId.isBlank()) { c=c.copy(runId=api.submitRun(c.remoteId,c.pendingInput,c.pendingKey)); dao.save(c) }; path="v1/runs/${OkHttpHermesApiClient.segment(c.runId)}/events" }
            "session", "demo" -> { path="api/sessions/${OkHttpHermesApiClient.segment(c.remoteId)}/chat/stream"; body=buildJsonObject { put("input",c.pendingInput) } }
            else -> {
                path="v1/chat/completions"
                val history=dao.history(c.key).filter { it.role=="user" || (it.role=="assistant" && it.status=="completed") }
                body=buildJsonObject { put("model",capabilities.value?.model ?: "hermes-agent"); put("stream",true); put("messages",buildJsonArray { history.forEach { m -> add(buildJsonObject { put("role",m.role); put("content",m.content) }) } }) }
            }
        }
        val assistant=dao.history(c.key).first { it.id=="${c.pendingKey}_assistant" }
        try {
            api.stream(path,body,c.pendingKey).transformWhile { emit(it); it.status !in terminal.filter { s -> s != "response_finished" } }.collect { update ->
                if(update.runId.isNotBlank()) c=c.copy(runId=update.runId)
                if(update.sessionId.isNotBlank() && update.sessionId!=c.remoteId) c=c.copy(remoteId=update.sessionId)
                text+=update.delta
                update.finalText?.let { text=it }
                update.activity?.let { activity.value=it.take(1000) }
                update.approval?.let { approval.value=it }
                update.status?.let {
                    // OpenAI's finish_reason / [DONE] describe response transport, not Hermes run success.
                    runStatus.value=it; c=c.copy(status=it)
                    if(it in terminal) { terminalSeen=true; approval.value=null }
                    if(it=="running") approval.value=null
                }
                dao.save(c)
                dao.save(assistant.copy(content=text,status=c.status))
            }
            if(!terminalSeen) {
                c=c.copy(status=if(c.transport=="compat" && c.status=="response_finished") "response_finished" else "unknown")
                runStatus.value=c.status; dao.save(c); dao.save(assistant.copy(content=text,status=c.status))
                if(c.runId.isNotBlank()) reconcile(c)
            }
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) {
            if(!terminalSeen) { c=c.copy(status="unknown"); dao.save(c); dao.save(assistant.copy(content=text,status="unknown")); runStatus.value="unknown"; throw e }
        }
    }
    fun prepareRetry() { viewModelScope.launch {
        val c=dao.conversation(selected.value) ?: return@launch
        if(c.status in setOf("failed","cancelled","interrupted")) draft.value=c.pendingInput
    } }
    fun retryOrRefresh() {
        if(busy.value) return
        worker=viewModelScope.launch {
            val c=dao.conversation(selected.value) ?: return@launch
            busy.value=true; error.value=null
            try {
                when {
                    c.runId.isNotBlank() -> reconcile(c)
                    c.transport=="runs" && System.currentTimeMillis()-c.pendingAt < 23*60*60*1000L -> execute(c)
                    else -> error.value="This server cannot safely replay this request. Review its history before starting a new conversation."
                }
            } catch(e: Exception) { error.value=connectionError(e) }
            finally { busy.value=false }
        }
    }
    private suspend fun reconcile(c: Conversation) {
        try {
            val j=api.run(c.runId); val status=j.text("status").ifBlank { "unknown" }
            runStatus.value=status; dao.save(c.copy(status=status))
            val a=dao.history(c.key).lastOrNull { it.role=="assistant" }
            if(a != null) dao.save(a.copy(content=j.text("output").ifBlank { a.content },status=status))
            if(status in terminal) approval.value=null
            val pending=j["approval"] as? JsonObject
            if(status=="waiting_for_approval" && pending!=null) approval.value=Approval(c.runId,pending.text("request_id"),pending.text("description").ifBlank { pending.text("command") },(pending["choices"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty())
        } catch(e: Exception) { error.value=connectionError(e) }
    }
    fun stop() { viewModelScope.launch {
        val c=dao.conversation(selected.value) ?: return@launch
        try {
            if(c.scope=="demo") { worker?.cancelAndJoin(); dao.save(c.copy(status="cancelled")); runStatus.value="cancelled"; busy.value=false; approval.value=null }
            else if(c.runId.isNotBlank() && capabilities.value?.has("run_stop")==true) { api.stop(c.runId); activity.value="Stop requested"; reconcile(c) }
            else { error.value="Server cancellation is unavailable. The agent may continue running." }
        } catch(e: Exception) { error.value=connectionError(e) }
    } }
    fun approve(choice: String) { val a=approval.value ?: return; viewModelScope.launch {
        try { api.approve(a,choice); approval.value=null; activity.value="Approval response sent" }
        catch(e: Exception) { error.value=connectionError(e) }
    } }
    fun saveTask(task: LocalTask) { if(task.title.isBlank()) return; viewModelScope.launch { dao.save(task.copy(title=task.title.trim())) } }
    fun deleteTask(task: LocalTask) { viewModelScope.launch { dao.delete(task) } }
    fun clearLocalChats() { if(busy.value) return; viewModelScope.launch { dao.clearChats(); newChat() } }
    fun forgetServer() { if(busy.value || connecting.value) return; viewModelScope.launch {
        withContext(Dispatchers.IO) { graph.credentials.clear() }
        val p=preferences.value.copy(url="",demo=true,debugHttp=false); graph.preferences.update(p); activate(p)
    } }
    companion object { val terminal=setOf("completed","failed","cancelled","interrupted","response_finished") }
}
