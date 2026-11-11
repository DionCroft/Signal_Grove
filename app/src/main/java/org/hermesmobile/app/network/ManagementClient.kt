package org.hermesmobile.app.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.hermesmobile.app.data.*
import java.io.IOException
import java.util.concurrent.TimeUnit

class ManagementTokens(val access:String,val refresh:String,val provider:String,val userId:String,val expiresAt:Long) {
    fun json()=buildJsonObject {put("access_token",access);put("refresh_token",refresh);put("provider",provider);put("user_id",userId);put("expires_at",expiresAt);put("token_type","Bearer")}
    override fun toString()="ManagementTokens(redacted)"
    companion object {
        fun parse(j:JsonObject):ManagementTokens {
            require(j.text("token_type").equals("Bearer",ignoreCase=true)) {"Unsupported management sign-in response."}
            fun field(name:String,max:Int)=j.text(name).also {require(it.isNotBlank() && it.length<=max && it.none(Char::isISOControl)) {"Invalid management sign-in response."}}
            val expires=(j["expires_at"] as? JsonPrimitive)?.longOrNull ?: error("Missing management expiry.")
            require(expires>0)
            val access=field("access_token",16384);require(access.all {it.code in 33..126}) {"Invalid management sign-in response."}
            return ManagementTokens(access,field("refresh_token",16384),field("provider",128),field("user_id",512),expires)
        }
    }
}
data class ManagementIdentity(val userId:String,val displayName:String,val provider:String)
data class ManagedProfile(val name:String,val displayName:String,val pluginHome:org.hermesmobile.app.data.PluginHome?=null)

/** Dashboard listener and native session credentials are independent of the inference API. */
class ManagementClient(url:String,debugHttp:Boolean,debugBuild:Boolean=false,localNetworkAllowed:()->Boolean={true}) {
    val base=OkHttpHermesApiClient.validateBaseUrl(url,debugHttp,debugBuild)
    private val dns=PermissionAwareDns(localNetworkAllowed)
    private val http=OkHttpClient.Builder().dns(dns).connectTimeout(15,TimeUnit.SECONDS).readTimeout(45,TimeUnit.SECONDS)
        .writeTimeout(30,TimeUnit.SECONDS).callTimeout(60,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    // Only pure identity/profile metadata reads may recover a broken pooled connection.
    // Mutation, token and ticket requests keep the original one-send client.
    private val metadataHttp=http.newBuilder().retryOnConnectionFailure(true).build()
    private val hubHttp=http.newBuilder().readTimeout(180,TimeUnit.SECONDS).callTimeout(210,TimeUnit.SECONDS).build()
    private suspend fun element(path:String,token:String?=null,body:JsonObject?=null,profile:String?=null,method:String=if(body==null) "GET" else "POST",tail:List<String> = emptyList(),query:Map<String,String> = emptyMap(),slow:Boolean=false,relayMint:Boolean=false):JsonElement=suspendCancellableCoroutine {continuation ->
        dns.checkHost(base.host)
        val endpoint=base.newBuilder().addPathSegments(path).apply {tail.forEach {addPathSegment(it)};query.forEach {(key,value)->addQueryParameter(key,value)};profile?.let {require(it.isNotBlank() && it.length<=128);addQueryParameter("profile",it)}}.build()
        val request=Request.Builder().url(endpoint).header("Accept","application/json").apply {
            token?.let {header("Authorization","Bearer $it")}
            method(method,body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType()))
        }.build()
        val metadataRead=method=="GET" && body==null && profile==null && tail.isEmpty() && query.isEmpty() &&
            path in setOf("api/auth/me","api/profiles")
        val call=(if(metadataRead) metadataHttp else if(slow) hubHttp else http).newCall(request)
        continuation.invokeOnCancellation {call.cancel()}
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:IOException) {if(continuation.isActive) continuation.resumeWith(Result.failure(e))}
            override fun onResponse(call:Call,response:Response) {
                val result=runCatching {response.use {
                    if(!it.isSuccessful) {
                        // Only these exact typed 409 guards precede connector minting in this route.
                        if(relayMint && it.code==409 && it.header("Content-Type").orEmpty().substringBefore(';').trim().equals("application/json",true)) {
                            val detail=runCatching {Json.parseToJsonElement(it.peekBody(16_384).string()).jsonObject["detail"] as? JsonObject}.getOrNull()
                            val code=(detail?.get("code") as? JsonPrimitive)?.takeIf {v->v.isString}?.content
                            if(code in setOf("platform_not_fronted","managed_by_portal")) throw HostRelayLinkRefused(code=="managed_by_portal")
                        }
                        throw HermesHttpException.from(it)
                    }
                    require(it.header("Content-Type").orEmpty().substringBefore(';').trim().equals("application/json",ignoreCase=true)) {"Management server returned a non-JSON response."}
                    if(it.body.contentLength()>2_000_000) throw HermesResponseTooLargeException()
                    val source=it.body.source();val buffer=okio.Buffer()
                    while(buffer.size<=2_000_000 && source.read(buffer,8192)!=-1L) {}
                    if(buffer.size>2_000_000) throw HermesResponseTooLargeException()
                    Json.parseToJsonElement(buffer.readUtf8())
                }}
                if(continuation.isActive) continuation.resumeWith(result)
            }
        })
    }
    private suspend fun json(path:String,token:String?=null,body:JsonObject?=null,profile:String?=null,method:String=if(body==null) "GET" else "POST",tail:List<String> = emptyList()):JsonObject=element(path,token,body,profile,method,tail).jsonObject
    suspend fun startHostRelayLink(tokens:ManagementTokens,platform:String):JsonObject {require(HostChannelPolicy.id(platform));return element("api/messaging/relay",tokens.access,JsonObject(emptyMap()),tail=listOf(platform,"link"),relayMint=true).jsonObject}
    suspend fun hostRelayLinkState(tokens:ManagementTokens,code:String):JsonObject {require(HostRelayLinkPolicy.code(code));return json("api/messaging/relay/link",tokens.access,tail=listOf(code))}
    suspend fun unlinkHostRelay(tokens:ManagementTokens,request:HostRelayUnlinkRequest):JsonObject=json("api/messaging/relay",tokens.access,method="DELETE",tail=listOf(request.platform,"accounts",request.account.id))
    suspend fun changeKanbanBoard(tokens:ManagementTokens,profile:String,request:HostKanbanBoardChange):JsonObject {
        GatewayCronInventory.requireProfile(profile);require(profile==request.expected.owner.profile)
        val tail=if(request.action==HostKanbanBoardAction.Create) emptyList() else if(request.action==HostKanbanBoardAction.Switch) listOf(request.slug,"switch") else listOf(request.slug)
        val body=if(request.action in setOf(HostKanbanBoardAction.Archive,HostKanbanBoardAction.Delete)) null else request.body()
        val query=if(request.action in setOf(HostKanbanBoardAction.Archive,HostKanbanBoardAction.Delete)) mapOf("delete" to (request.action==HostKanbanBoardAction.Delete).toString()) else emptyMap()
        return element("api/plugins/kanban/boards",tokens.access,body,profile,request.action.method,tail,query).jsonObject
    }
    suspend fun kanbanBoards(tokens:ManagementTokens,profile:String):JsonObject {GatewayCronInventory.requireProfile(profile);return element("api/plugins/kanban/boards",tokens.access,profile=profile,query=mapOf("include_archived" to "true")).jsonObject}
    suspend fun changeKanbanTask(tokens:ManagementTokens,profile:String,request:HostKanbanTaskChange):JsonObject {
        GatewayCronInventory.requireProfile(profile);require(profile==request.owner.profile)
        val dependency=request.action in setOf(HostKanbanTaskAction.Link,HostKanbanTaskAction.Unlink)
        val path=if(dependency) "api/plugins/kanban/links" else "api/plugins/kanban/tasks"
        val tail=if(dependency || request.action==HostKanbanTaskAction.Create) emptyList() else listOfNotNull(request.id)+(if(request.action==HostKanbanTaskAction.Comment) listOf("comments") else emptyList())
        val query=mapOf("board" to request.board.slug)+(if(request.action==HostKanbanTaskAction.Unlink) mapOf("parent_id" to request.related!!.task.id,"child_id" to request.id!!) else emptyMap())
        return element(path,tokens.access,if(request.action.method=="DELETE") null else request.body(),profile=profile,tail=tail,query=query,method=request.action.method,slow=true).jsonObject
    }
    suspend fun kanbanBoard(tokens:ManagementTokens,profile:String,board:String):JsonObject {GatewayCronInventory.requireProfile(profile);require(HostKanbanProtocol.slug(board));return element("api/plugins/kanban/board",tokens.access,profile=profile,query=mapOf("board" to board,"include_archived" to "true")).jsonObject}
    suspend fun kanbanTask(tokens:ManagementTokens,profile:String,board:String,id:String):JsonObject {GatewayCronInventory.requireProfile(profile);require(HostKanbanProtocol.slug(board) && HostKanbanProtocol.id(id));return element("api/plugins/kanban/tasks",tokens.access,profile=profile,tail=listOf(id),query=mapOf("board" to board)).jsonObject}
    suspend fun changeKanbanStatus(tokens:ManagementTokens,profile:String,request:HostKanbanStatusChange):JsonObject {
        GatewayCronInventory.requireProfile(profile);require(profile==request.owner.profile)
        return element("api/plugins/kanban/tasks",tokens.access,request.body(),profile=profile,tail=listOf(request.id),query=mapOf("board" to request.board.slug),method="PATCH",slow=true).jsonObject
    }
    suspend fun hostRelayAccounts(tokens:ManagementTokens):JsonObject=json("api/messaging/relay",tokens.access)
    suspend fun probeCustomEndpoint(tokens:ManagementTokens,request:CustomEndpointProbeRequest):JsonObject = element("api/providers/custom-endpoints/validate",tokens.access,request.body(),slow=true).jsonObject
    suspend fun customEndpointConfig(tokens:ManagementTokens,profile:String):JsonObject {GatewayCronInventory.requireProfile(profile);return element("api/config",tokens.access,profile=profile,query=mapOf("include_defaults" to "false"),slow=true).jsonObject}
    suspend fun customEndpoints(tokens:ManagementTokens,profile:String):JsonObject {GatewayCronInventory.requireProfile(profile);return element("api/providers/custom-endpoints",tokens.access,profile=profile,slow=true).jsonObject}
    suspend fun changeCustomEndpoint(tokens:ManagementTokens,profile:String,request:CustomEndpointRequest):JsonObject {
        GatewayCronInventory.requireProfile(profile);require(profile==request.expected.owner.profile)
        val tail=when(request.action) {CustomEndpointAction.Save->emptyList();CustomEndpointAction.Activate->listOf(request.id,"activate");CustomEndpointAction.Remove->listOf(request.id)}
        val method=if(request.action==CustomEndpointAction.Remove) "DELETE" else "POST"
        val body=when(request.action) {CustomEndpointAction.Save->request.draft!!.body();CustomEndpointAction.Activate->JsonObject(emptyMap());CustomEndpointAction.Remove->null}
        return element("api/providers/custom-endpoints",tokens.access,body,profile,method,tail,slow=true).jsonObject
    }
    suspend fun fallbackConfig(tokens:ManagementTokens,profile:String):JsonObject {GatewayCronInventory.requireProfile(profile);return element("api/config",tokens.access,profile=profile,query=mapOf("include_defaults" to "false")).jsonObject}
    suspend fun changeFallbackConfig(tokens:ManagementTokens,profile:String,request:HostFallbackRequest):JsonObject {GatewayCronInventory.requireProfile(profile);require(profile==request.expected.owner.profile);return element("api/config",tokens.access,request.body(),profile,method="PUT").jsonObject}
    suspend fun credentialPool(tokens:ManagementTokens,profile:String):JsonObject {GatewayCronInventory.requireProfile(profile);return element("api/credentials/pool",tokens.access,profile=profile,slow=true).jsonObject}
    suspend fun changeCredentialPool(tokens:ManagementTokens,profile:String,request:CredentialPoolRequest):JsonObject {
        GatewayCronInventory.requireProfile(profile);require(CredentialPoolProtocol.provider(request.provider))
        return if(request.action==CredentialPoolAction.Add) element("api/credentials/pool",tokens.access,request.body(),profile,slow=true).jsonObject
        else element("api/credentials/pool",tokens.access,profile=profile,method="DELETE",tail=listOf(request.provider,request.entry!!.index.toString()),slow=true).jsonObject
    }
    suspend fun providerOAuth(tokens:ManagementTokens,profile:String):JsonObject {GatewayCronInventory.requireProfile(profile);return json("api/providers/oauth",tokens.access,profile=profile)}
    suspend fun startProviderOAuth(tokens:ManagementTokens,profile:String,provider:String):JsonObject {GatewayCronInventory.requireProfile(profile);require(ProviderOAuthProtocol.providerId(provider));return element("api/providers/oauth",tokens.access,JsonObject(emptyMap()),profile,tail=listOf(provider,"start"),slow=true).jsonObject}
    suspend fun pollProviderOAuth(tokens:ManagementTokens,profile:String,provider:String,id:String):JsonObject {GatewayCronInventory.requireProfile(profile);require(ProviderOAuthProtocol.providerId(provider) && ProviderOAuthProtocol.sessionId(id));return element("api/providers/oauth",tokens.access,profile=profile,tail=listOf(provider,"poll",id),query=mapOf("wait" to "0")).jsonObject}
    suspend fun cancelProviderOAuth(tokens:ManagementTokens,profile:String,id:String):JsonObject {GatewayCronInventory.requireProfile(profile);require(ProviderOAuthProtocol.sessionId(id));return json("api/providers/oauth",tokens.access,profile=profile,method="DELETE",tail=listOf("sessions",id))}
    suspend fun disconnectProviderOAuth(tokens:ManagementTokens,profile:String,provider:String):JsonObject {GatewayCronInventory.requireProfile(profile);require(ProviderOAuthProtocol.providerId(provider));return json("api/providers/oauth",tokens.access,profile=profile,method="DELETE",tail=listOf(provider))}
    suspend fun moa(tokens:ManagementTokens,profile:String):JsonObject {GatewayCronInventory.requireProfile(profile);return json("api/model/moa",tokens.access,profile=profile)}
    suspend fun saveMoa(tokens:ManagementTokens,profile:String,request:HostMoaRequest):JsonObject {GatewayCronInventory.requireProfile(profile);return element("api/model/moa",tokens.access,request.body(profile),profile,method="PUT",slow=true).jsonObject}
    suspend fun setAuxModel(tokens:ManagementTokens,profile:String,request:HostAuxModelRequest):JsonObject {GatewayCronInventory.requireProfile(profile);return element("api/model/set",tokens.access,request.body(profile),profile,slow=true).jsonObject}
    suspend fun setMainModel(tokens:ManagementTokens,profile:String,request:HostMainModelRequest):JsonObject {GatewayCronInventory.requireProfile(profile);return element("api/model/set",tokens.access,request.body(profile),profile,slow=true).jsonObject}
    suspend fun modelAuxiliary(tokens:ManagementTokens,profile:String):JsonObject {GatewayCronInventory.requireProfile(profile);return json("api/model/auxiliary",tokens.access,profile=profile)}
    suspend fun modelOptions(tokens:ManagementTokens,profile:String,refresh:Boolean=false):JsonObject {GatewayCronInventory.requireProfile(profile);return element("api/model/options",tokens.access,profile=profile,query=mapOf("refresh" to refresh.toString(),"include_unconfigured" to "true","explicit_only" to "false"),slow=true).jsonObject}
    suspend fun hostChannels(tokens:ManagementTokens,profile:String):JsonObject {GatewayCronInventory.requireProfile(profile);return element("api/messaging/platforms",tokens.access,profile=profile,slow=true).jsonObject}
    suspend fun changeHostChannel(tokens:ManagementTokens,profile:String,request:HostChannelRequest):JsonObject {GatewayCronInventory.requireProfile(profile);require(profile==request.expected.owner.profile && request.draft!=null);return element("api/messaging/platforms",tokens.access,request.body(profile),profile,method="PUT",tail=listOf(request.id),slow=true).jsonObject}
    suspend fun hostChannelPosture(tokens:ManagementTokens,profile:String,id:String):JsonObject {GatewayCronInventory.requireProfile(profile);require(HostChannelPolicy.id(id));return element("api/messaging/platforms",tokens.access,JsonObject(emptyMap()),profile,method="POST",tail=listOf(id,"test"),slow=true).jsonObject}
    suspend fun environment(tokens:ManagementTokens,profile:String):JsonObject {GatewayCronInventory.requireProfile(profile);return json("api/env",tokens.access,profile=profile)}
    suspend fun environmentControl(tokens:ManagementTokens,profile:String,request:HostEnvRequest):JsonObject {GatewayCronInventory.requireProfile(profile);return json("api/env",tokens.access,request.body(profile),profile,method=if(request.action==HostEnvAction.Remove) "DELETE" else "PUT")}
    suspend fun hubSources(tokens:ManagementTokens,profile:String):JsonObject {SkillProtocol.profile(profile);return element("api/skills/hub/sources",tokens.access,profile=profile,slow=true).jsonObject}
    suspend fun hubOfficial(tokens:ManagementTokens,profile:String):JsonObject {SkillProtocol.profile(profile);return element("api/skills/hub/official",tokens.access,profile=profile).jsonObject}
    suspend fun hubSearch(tokens:ManagementTokens,profile:String,query:String,source:String):JsonObject {SkillProtocol.profile(profile);require(query.isNotBlank() && query.length<=256 && query.none(Char::isISOControl) && source.isNotBlank() && source.length<=128 && source.none(Char::isISOControl));return element("api/skills/hub/search",tokens.access,profile=profile,query=mapOf("q" to query,"source" to source,"limit" to "20"),slow=true).jsonObject}
    suspend fun hubPreview(tokens:ManagementTokens,profile:String,skill:HubSkill):JsonObject {SkillProtocol.profile(profile);require(skill.addressable);return element("api/skills/hub/preview",tokens.access,profile=profile,query=mapOf("identifier" to skill.canonicalId),slow=true).jsonObject}
    suspend fun hubScan(tokens:ManagementTokens,profile:String,skill:HubSkill):JsonObject {SkillProtocol.profile(profile);require(skill.addressable);return element("api/skills/hub/scan",tokens.access,profile=profile,query=mapOf("identifier" to skill.canonicalId),slow=true).jsonObject}
    suspend fun installHubSkill(tokens:ManagementTokens,profile:String,request:HubInstallRequest):JsonObject {SkillProtocol.profile(profile);return json("api/skills/hub/install",tokens.access,request.body(profile),profile)}
    suspend fun maintainHubSkills(tokens:ManagementTokens,profile:String,request:HubMaintenanceRequest):JsonObject {SkillProtocol.profile(profile);return json("api/skills/hub/"+request.endpoint,tokens.access,request.body(profile),profile)}
    private fun gatewayTicketValue(response:JsonObject):String {
        val ticket=response["ticket"] as? JsonPrimitive
        val ttl=response["ttl_seconds"] as? JsonPrimitive
        require(ticket!=null && ticket.isString && ttl!=null && !ttl.isString && ttl.intOrNull in 1..120) {"Invalid gateway ticket."}
        return ticket.content
    }
    private suspend fun gatewayTicket(tokens:ManagementTokens):String =
        gatewayTicketValue(json("api/auth/ws-ticket",tokens.access,JsonObject(emptyMap())))
    private suspend fun profileGatewayTicket(tokens:ManagementTokens,profile:String):String {
        val response=try {
            element("api/webapp/socket-ticket",tokens.access,JsonObject(emptyMap()),profile,
                query=mapOf("socket" to "gateway")).jsonObject
        } catch(error:HermesHttpException) {
            if(error.code==404) throw CanonicalProfileTransportUnavailable()
            throw error
        }
        return gatewayTicketValue(response)
    }
    suspend fun gateway(tokens:ManagementTokens):GatewayRpcSession {
        val ticket=gatewayTicket(tokens);dns.checkHost(base.host)
        return GatewayRpcSession.connect(http,base,ticket)
    }
    /** Explicit separate connection; identity negotiation cannot restore or adopt a session. */
    internal suspend fun canonicalGateway(tokens:ManagementTokens,target:ManagementTarget,homeId:String):CanonicalGatewayChannel {
        GatewayCronInventory.requireProfile(target.profile)
        require(target.url==base.toString() && target.actor==tokens.provider+" · "+tokens.userId)
        require(homeId.matches(Regex("[a-f0-9]{64}")))
        suspend fun verifyHome() {
            if(profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome?.identity()!=homeId) throw CanonicalScopeChanged()
        }
        identity(tokens);verifyHome()
        suspend fun connect(ticket:String):CanonicalGatewayChannel {
            dns.checkHost(base.host)
            return CanonicalGatewayChannel.connect(http,base,ticket,target,homeId)
        }
        val channel=try {connect(gatewayTicket(tokens))}
        catch(error:CanonicalTicketProfileMismatch) {
            // The rejected connection is closed and has sent only runtime.describe.
            // Recheck the same actor/home before the sole profile-bound handshake.
            identity(tokens);verifyHome()
            connect(profileGatewayTicket(tokens,target.profile))
        }
        try {verifyHome();return channel}
        catch(error:Exception) {channel.close();throw error}
    }
    suspend fun skills(tokens:ManagementTokens,profile:String):JsonArray {SkillProtocol.profile(profile);return element("api/skills",tokens.access,profile=profile).jsonArray}
    suspend fun skillContent(tokens:ManagementTokens,profile:String,name:String):JsonObject {SkillProtocol.profile(profile);require(SkillProtocol.name(name));return element("api/skills/content",tokens.access,profile=profile,query=mapOf("name" to name)).jsonObject}
    suspend fun skillControl(tokens:ManagementTokens,profile:String,request:SkillRequest):JsonObject {SkillProtocol.profile(profile);return json("api/skills/"+if(request.action==SkillAction.Toggle) "toggle" else "lock",tokens.access,request.body(profile),profile,method="PUT")}
    suspend fun cronSchema(tokens:ManagementTokens)=json("openapi.json",tokens.access)
    suspend fun cronDeliveries(tokens:ManagementTokens,profile:String):JsonObject {require(profile.isNotBlank() && !profile.trim().equals("all",ignoreCase=true));return json("api/cron/delivery-targets",tokens.access,profile=profile)}
    suspend fun createCron(tokens:ManagementTokens,profile:String,request:CronCreation):JsonObject {require(profile.isNotBlank() && !profile.trim().equals("all",ignoreCase=true));return json("api/cron/jobs",tokens.access,request.body(),profile)}
    suspend fun cronJobs(tokens:ManagementTokens,profile:String):JsonArray {require(profile.isNotBlank() && !profile.trim().equals("all",ignoreCase=true));return element("api/cron/jobs",tokens.access,profile=profile).jsonArray}
    suspend fun cronJob(tokens:ManagementTokens,profile:String,id:String):JsonObject {require(profile.isNotBlank() && !profile.trim().equals("all",ignoreCase=true) && id!="unknown" && id.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}")));return json("api/cron/jobs",tokens.access,profile=profile,tail=listOf(id))}
    suspend fun cronRuns(tokens:ManagementTokens,profile:String,id:String,limit:Int=20):JsonObject {
        require(profile.isNotBlank() && profile.length<=128 && !profile.trim().equals("all",true) && profile.none(Char::isISOControl))
        require(id!="unknown" && id.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}")) && limit in 1..100)
        return element("api/cron/jobs",tokens.access,profile=profile,tail=listOf(id,"runs"),query=mapOf("limit" to limit.toString())).jsonObject
    }

    suspend fun exchange(code:String,verifier:String)=ManagementTokens.parse(json("auth/native/token",body=buildJsonObject {put("code",code);put("code_verifier",verifier)}))
    suspend fun refresh(tokens:ManagementTokens)=ManagementTokens.parse(json("auth/native/refresh",body=buildJsonObject {put("refresh_token",tokens.refresh);put("provider",tokens.provider)})).also {
        require(it.userId==tokens.userId && it.provider==tokens.provider) {"Management identity changed. Sign in again."}
    }
    suspend fun identity(tokens:ManagementTokens):ManagementIdentity {
        val j=json("api/auth/me",tokens.access)
        require(j.text("user_id")==tokens.userId && j.text("provider")==tokens.provider) {"Management identity verification failed."}
        return ManagementIdentity(tokens.userId,j.text("display_name").take(160),tokens.provider)
    }
    suspend fun profiles(tokens:ManagementTokens):List<ManagedProfile> {
        val j=json("api/profiles",tokens.access);require(j["profiles"] is JsonArray)
        val rows=j.items("profiles");require(rows.size<=500)
        val duplicates=rows.groupingBy {it.text("name")}.eachCount().filterValues {it>1}.keys
        return rows.map {row ->val name=row.text("name");require(name.isNotBlank() && name.length<=128 && name.none(Char::isISOControl));ManagedProfile(name,row.text("display_name").take(160),if(name in duplicates) null else org.hermesmobile.app.data.PluginHome.project(row["path"]))}.distinctBy {it.name}
    }
    suspend fun startMcpOAuth(tokens:ManagementTokens,profile:String,name:String)=json("api/mcp/servers",tokens.access,JsonObject(emptyMap()),profile,tail=listOf(name,"auth"))
    suspend fun mcpOAuthFlow(tokens:ManagementTokens,id:String):JsonObject {require(McpOAuthSnapshot.validId(id));return json("api/mcp/oauth/flows",tokens.access,tail=listOf(id))}
    suspend fun cancelMcpOAuth(tokens:ManagementTokens,id:String):JsonObject {require(McpOAuthSnapshot.validId(id));return json("api/mcp/oauth/flows",tokens.access,method="DELETE",tail=listOf(id))}
    suspend fun catalog(tokens:ManagementTokens,profile:String)=json("api/mcp/catalog",tokens.access,profile=profile)
    suspend fun installCatalog(tokens:ManagementTokens,profile:String,request:McpCatalogInstall)=json("api/mcp/catalog/install",tokens.access,request.body(profile),profile)
    suspend fun createMcp(tokens:ManagementTokens,profile:String,creation:McpCreation)=json("api/mcp/servers",tokens.access,creation.body(profile),profile)
    suspend fun mcpControl(tokens:ManagementTokens,profile:String,request:McpRequest):JsonObject {
        val tail=listOf(request.name)+when(request.action) {McpAction.Toggle->listOf("enabled");McpAction.Probe->listOf("test");McpAction.Delete->emptyList()}
        val method=when(request.action) {McpAction.Toggle->"PUT";McpAction.Probe->"POST";McpAction.Delete->"DELETE"}
        return json("api/mcp/servers",tokens.access,if(request.action==McpAction.Delete) null else request.body(profile),profile,method,tail)
    }
    suspend fun mcp(tokens:ManagementTokens,profile:String)=json("api/mcp/servers",tokens.access,profile=profile)
}
