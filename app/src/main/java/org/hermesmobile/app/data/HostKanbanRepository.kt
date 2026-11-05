package org.hermesmobile.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.hermesmobile.app.network.ManagementClient
import org.hermesmobile.app.network.ManagementTokens

/** Profile home fences access routing, not Kanban storage: the host shares its Kanban root across profiles. */
class HostKanbanRepository {
    private fun scope(owner:ManagementTarget,home:String,api:ManagementClient,tokens:ManagementTokens) {
        GatewayCronInventory.requireProfile(owner.profile)
        require(owner.url==api.base.toString() && owner.actor==tokens.provider+" · "+tokens.userId && home.matches(Regex("[a-f0-9]{64}")))
    }
    private suspend fun verifyHome(owner:ManagementTarget,expected:String,api:ManagementClient,tokens:ManagementTokens) {
        if(api.profiles(tokens).singleOrNull {it.name==owner.profile}?.pluginHome?.identity()!=expected) throw HostKanbanScopeChanged()
        currentCoroutineContext().ensureActive()
    }
    private suspend fun catalog(owner:ManagementTarget,home:String,api:ManagementClient,tokens:ManagementTokens):HostKanbanInventory {
        val response=api.kanbanBoards(tokens,owner.profile)
        return withContext(Dispatchers.Default) {HostKanbanProtocol.inventory(response,owner,home)}
    }
    private suspend fun mapping(owner:ManagementTarget,home:String,expected:HostKanbanBoard,api:ManagementClient,tokens:ManagementTokens):HostKanbanBoard {
        val row=catalog(owner,home,api,tokens).boards.singleOrNull {it.slug==expected.slug}
        if(row==null || row.archived || !row.sameMapping(expected)) throw HostKanbanScopeChanged()
        return row
    }
    suspend fun inventory(owner:ManagementTarget,home:String,api:ManagementClient,tokens:ManagementTokens):HostKanbanInventory {
        scope(owner,home,api,tokens);api.identity(tokens);verifyHome(owner,home,api,tokens)
        val result=catalog(owner,home,api,tokens);verifyHome(owner,home,api,tokens);return result
    }
    suspend fun board(owner:ManagementTarget,home:String,expected:HostKanbanBoard,api:ManagementClient,tokens:ManagementTokens):HostKanbanSnapshot {
        scope(owner,home,api,tokens);require(!expected.archived && HostKanbanProtocol.slug(expected.slug));api.identity(tokens);verifyHome(owner,home,api,tokens)
        mapping(owner,home,expected,api,tokens)
        val response=api.kanbanBoard(tokens,owner.profile,expected.slug)
        val result=withContext(Dispatchers.Default) {HostKanbanProtocol.snapshot(response,owner,home,expected)}
        val observed=mapping(owner,home,expected,api,tokens);verifyHome(owner,home,api,tokens)
        return result.copy(board=observed)
    }
    suspend fun detail(owner:ManagementTarget,home:String,expected:HostKanbanBoard,id:String,api:ManagementClient,tokens:ManagementTokens):HostKanbanDetail {
        scope(owner,home,api,tokens);require(!expected.archived && HostKanbanProtocol.slug(expected.slug) && HostKanbanProtocol.id(id));api.identity(tokens);verifyHome(owner,home,api,tokens)
        mapping(owner,home,expected,api,tokens)
        val response=api.kanbanTask(tokens,owner.profile,expected.slug,id)
        val result=withContext(Dispatchers.Default) {HostKanbanProtocol.detail(response,owner,home,expected,id)}
        val observed=mapping(owner,home,expected,api,tokens);verifyHome(owner,home,api,tokens)
        return result.copy(board=observed)
    }
}
