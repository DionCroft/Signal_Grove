package org.hermesmobile.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.hermesmobile.app.network.ManagementClient
import org.hermesmobile.app.network.ManagementTokens

/** Each explicit read verifies its frozen profile home before and after one fresh gateway connection. */
class HostProjectsRepository {
    private fun scope(target:ManagementTarget,homeId:String,api:ManagementClient,tokens:ManagementTokens) {
        GatewayCronInventory.requireProfile(target.profile)
        require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)
        require(homeId.matches(Regex("[a-f0-9]{64}")))
    }
    private suspend fun home(target:ManagementTarget,homeId:String,api:ManagementClient,tokens:ManagementTokens) {
        val current=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome?.identity()
        if(current!=homeId) throw HostProjectsScopeChanged()
        currentCoroutineContext().ensureActive()
    }
    private suspend fun projectCall(owner:ManagementTarget,home:String,api:ManagementClient,tokens:ManagementTokens,id:String?=null,conversations:Boolean=false):kotlinx.serialization.json.JsonObject =
        api.gateway(tokens).use {probe->
            when(probe.sessionAuthorityAdvertisement) {
                org.hermesmobile.app.network.GatewayAuthorityAdvertisement.Advertised->{
                    // The hint selects a protocol, never authority. A failed canonical binding cannot downgrade.
                    probe.close()
                    try {api.canonicalGateway(tokens,owner,home).use {channel->channel.projectMetadata(id,conversations)}}
                    catch(_:org.hermesmobile.app.network.CanonicalScopeChanged) {throw HostProjectsScopeChanged()}
                }
                org.hermesmobile.app.network.GatewayAuthorityAdvertisement.NotAdvertised->
                    if(conversations) probe.projectConversations(owner.profile,checkNotNull(id)) else if(id==null) probe.projects(owner.profile) else probe.project(owner.profile,id)
                org.hermesmobile.app.network.GatewayAuthorityAdvertisement.Unknown->throw org.hermesmobile.app.network.GatewayProtocolException()
            }
        }
    suspend fun read(target:ManagementTarget,homeId:String,api:ManagementClient,tokens:ManagementTokens):HostProjectSnapshot {
        scope(target,homeId,api,tokens);api.identity(tokens);home(target,homeId,api,tokens)
        val result=withContext(Dispatchers.Default) {HostProjectProtocol.inventory(projectCall(target,homeId,api,tokens),target,homeId)}
        home(target,homeId,api,tokens)
        return result
    }
    suspend fun detail(target:ManagementTarget,homeId:String,id:String,api:ManagementClient,tokens:ManagementTokens):HostProjectDetail {
        scope(target,homeId,api,tokens);require(HostProjectProtocol.validId(id));api.identity(tokens);home(target,homeId,api,tokens)
        val result=withContext(Dispatchers.Default) {HostProjectProtocol.detail(projectCall(target,homeId,api,tokens,id),target,homeId,id)}
        home(target,homeId,api,tokens)
        return result
    }
    suspend fun conversations(owner:ManagementTarget,homeId:String,project:HostProjectInfo,api:ManagementClient,tokens:ManagementTokens):HostProjectConversations {
        scope(owner,homeId,api,tokens);require(!project.archived);api.identity(tokens);home(owner,homeId,api,tokens)
        suspend fun verifyProject() {
            val observed=HostProjectProtocol.detail(projectCall(owner,homeId,api,tokens,project.id),owner,homeId,project.id).project
            if(observed!=project) throw HostProjectsScopeChanged()
            currentCoroutineContext().ensureActive()
        }
        verifyProject()
        val result=withContext(Dispatchers.Default) {
            HostProjectConversationProtocol.parse(projectCall(owner,homeId,api,tokens,project.id,true),owner,homeId,project)
        }
        api.identity(tokens);home(owner,homeId,api,tokens);verifyProject();home(owner,homeId,api,tokens)
        return result
    }

}
