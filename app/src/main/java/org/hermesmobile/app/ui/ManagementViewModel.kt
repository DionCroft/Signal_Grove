package org.hermesmobile.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.hermesmobile.app.BuildConfig
import org.hermesmobile.app.HermesApplication
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*

data class ManagedServer(val name:String,val transport:String,val auth:String,val enabled:Boolean,val source:String,val tools:List<String>)
data class ManagementState(val apiScope:String="",val url:String="",val debugHttp:Boolean=false,val signedIn:Boolean=false,
    val identity:ManagementIdentity?=null,val profiles:List<ManagedProfile> = emptyList(),val profile:String="",
    val servers:List<ManagedServer> = emptyList(),val loaded:Boolean=false,val busy:Boolean=false,val error:Words?=null)

class ManagementViewModel(application:Application):AndroidViewModel(application) {
    private val app=application as HermesApplication
    val state=MutableStateFlow(ManagementState())
    private var generation=0L
    private var work:Job?=null
    private var login:NativeLogin?=null
    private var tokens:ManagementTokens?=null
    private fun managementError(e:Throwable)=when(e) {
        is LocalNetworkPermissionException->Words.ManagementLanError
        is javax.net.ssl.SSLException->Words.ManagementTlsError
        is HermesHttpException->when(e.code) {401->Words.ManagementExpired;404,405->Words.ManagementUnsupported;else->Words.ManagementFailed}
        is IllegalArgumentException->Words.ManagementInvalid
        else->Words.ManagementInterrupted
    }
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
            client(next);tokens=restored;state.value=next
        } catch(e:CancellationException) {throw e} catch(e:Exception) {if(id==generation) state.value=state.value.copy(error=managementError(e))}}
    }
    private suspend fun save(s:ManagementState,t:ManagementTokens) {withContext(Dispatchers.IO) {
        // Never restore management access for a removed inference credential.
        require(app.credentials.profile(s.apiScope)!=null) {"The API connection credential was removed."}
        app.credentials.saveManagement(s.apiScope,buildJsonObject {put("url",s.url);put("debugHttp",s.debugHttp);put("profile",s.profile);put("tokens",t.json())})
    }}
    private fun operation(block:suspend (Long)->Unit) {
        if(state.value.busy || !state.value.apiScope.matches(Regex("[a-f0-9]{64}"))) return
        val id=++generation;state.value=state.value.copy(busy=true,error=null)
        work=viewModelScope.launch {try {block(id)} catch(e:CancellationException) {throw e}
            catch(e:Exception) {if(id==generation) state.value=if(e is HermesHttpException && e.code==401) state.value.copy(identity=null,profiles=emptyList(),servers=emptyList(),loaded=false,error=managementError(e)) else state.value.copy(error=managementError(e))}
            finally {if(id==generation) state.value=state.value.copy(busy=false)}}
    }
    fun signIn(url:String,debugHttp:Boolean,openBrowser:(String)->Unit)=operation {id ->
        val current=state.value.copy(url=url.trim(),debugHttp=debugHttp,identity=null,profiles=emptyList(),profile="",servers=emptyList(),loaded=false)
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
        generation++;login?.close();login=null;work?.cancel();state.value=state.value.copy(busy=false)
    }
    fun refresh(renew:Boolean=false)=operation {id ->
        val saved=state.value;val original=tokens ?: error("Sign in to management first.")
        val api=client(saved)
        val current=if(renew) api.refresh(original).also {save(saved,it);if(id==generation) tokens=it} else original
        require(current.expiresAt>System.currentTimeMillis()/1000) {"Management sign-in expired. Renew it or sign in again."}
        val identity=api.identity(current);val profiles=api.profiles(current)
        if(id!=generation) return@operation
        val profile=saved.profile.takeIf {name->profiles.any {it.name==name}}.orEmpty()
        state.value=state.value.copy(identity=identity,profiles=profiles,profile=profile,servers=emptyList(),loaded=false)
        if(profile.isNotBlank()) loadServers(id,api,current,profile)
    }
    fun chooseProfile(profile:String)=operation {id ->
        val saved=state.value;require(saved.identity!=null && saved.profiles.any {it.name==profile}) {"Refresh verified management profiles first."}
        val current=tokens ?: error("Sign in to management first.")
        require(current.expiresAt>System.currentTimeMillis()/1000) {"Management sign-in expired. Renew it or sign in again."}
        val selected=saved.copy(profile=profile,servers=emptyList(),loaded=false)
        save(selected,current)
        if(id!=generation) return@operation
        state.value=selected;loadServers(id,client(selected),current,profile)
    }
    private suspend fun loadServers(id:Long,api:ManagementClient,current:ManagementTokens,profile:String) {
        val j=api.mcp(current,profile);require(j["servers"] is JsonArray)
        val rows=j.items("servers");require(rows.size<=500)
        val servers=rows.map {row ->ManagedServer(row.text("name").take(256),row.text("transport").take(32),row.text("auth").take(64),row["enabled"]?.jsonPrimitive?.booleanOrNull ?: error("Invalid management inventory."),row.text("source").take(32),(row["tools"] as? JsonArray).orEmpty().take(100).mapNotNull {(it as? JsonPrimitive)?.contentOrNull?.take(256)})}.distinctBy {it.name}
        if(id==generation) state.value=state.value.copy(servers=servers,loaded=true)
    }
    fun disconnect()=operation {id ->
        val scope=state.value.apiScope
        withContext(Dispatchers.IO) {app.credentials.saveManagement(scope,null)}
        if(id==generation) {tokens=null;state.value=ManagementState(apiScope=scope)}
    }
    override fun onCleared() {login?.close();super.onCleared()}
}
