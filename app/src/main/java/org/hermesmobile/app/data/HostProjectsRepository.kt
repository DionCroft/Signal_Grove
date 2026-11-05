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
    suspend fun read(target:ManagementTarget,homeId:String,api:ManagementClient,tokens:ManagementTokens):HostProjectSnapshot {
        scope(target,homeId,api,tokens);api.identity(tokens);home(target,homeId,api,tokens)
        val result=api.gateway(tokens).use {socket->withContext(Dispatchers.Default) {HostProjectProtocol.inventory(socket.projects(target.profile),target,homeId)}}
        home(target,homeId,api,tokens)
        return result
    }
    suspend fun detail(target:ManagementTarget,homeId:String,id:String,api:ManagementClient,tokens:ManagementTokens):HostProjectDetail {
        scope(target,homeId,api,tokens);require(HostProjectProtocol.validId(id));api.identity(tokens);home(target,homeId,api,tokens)
        val result=api.gateway(tokens).use {socket->withContext(Dispatchers.Default) {HostProjectProtocol.detail(socket.project(target.profile,id),target,homeId,id)}}
        home(target,homeId,api,tokens)
        return result
    }
}
