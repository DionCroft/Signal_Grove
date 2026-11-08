package org.hermesmobile.app.data

import kotlinx.coroutines.*
import org.hermesmobile.app.network.*

internal class HostedRoomScopeChanged:Exception("Hosted room identity changed.")
internal data class HostedRoomListing(val capabilities:HostedRoomCapabilities,val page:HostedRoomPage,val includeDisbanded:Boolean) {
    init {require(capabilities.scope==page.scope)}
    val scope get()=page.scope
    override fun toString()="HostedRoomListing(redacted)"
}
internal interface HostedRoomReader {
    suspend fun page(scope:HostedRoomScope,api:ManagementClient,tokens:ManagementTokens,offset:Int,includeDisbanded:Boolean):HostedRoomListing
    suspend fun detail(listing:HostedRoomListing,selected:HostedRoom,api:ManagementClient,tokens:ManagementTokens):HostedRoomDetail
}

/** Each explicit bounded read uses one fresh ticketed socket and verifies its owner/home on both sides. */
internal class HostedRoomsRepository(private val now:()->Long=System::currentTimeMillis):HostedRoomReader {
    private fun validate(scope:HostedRoomScope,api:ManagementClient,tokens:ManagementTokens) {
        if(scope.owner.url!=api.base.toString() || scope.owner.actor!=tokens.provider+" · "+tokens.userId || tokens.expiresAt<=now()/1000) throw HostedRoomScopeChanged()
    }
    private suspend fun verify(scope:HostedRoomScope,api:ManagementClient,tokens:ManagementTokens) {
        api.identity(tokens)
        val home=api.profiles(tokens).singleOrNull {it.name==scope.owner.profile}?.pluginHome?.identity()
        if(home!=scope.homeId) throw HostedRoomScopeChanged()
        currentCoroutineContext().ensureActive()
    }
    private fun requireMethod(capabilities:HostedRoomCapabilities,method:String) {
        if(method !in capabilities.methods) throw HostedRoomsUnsupported()
    }
    private fun sameAuthority(before:HostedRoomCapabilities,after:HostedRoomCapabilities) {
        if(before.scope!=after.scope || before.protocolVersion!=after.protocolVersion || before.authorityId!=after.authorityId) throw HostedRoomScopeChanged()
    }
    override suspend fun page(scope:HostedRoomScope,api:ManagementClient,tokens:ManagementTokens,offset:Int,includeDisbanded:Boolean):HostedRoomListing {
        require(offset>=0);validate(scope,api,tokens);verify(scope,api,tokens)
        val result=api.gateway(tokens).use {socket->withContext(Dispatchers.Default) {
            val before=HostedRoomProtocol.capabilities(socket.roomCapabilities(scope.owner.profile),scope)
            requireMethod(before,"groups.list")
            val page=HostedRoomProtocol.page(socket.rooms(scope.owner.profile,offset,includeDisbanded),scope,offset,now())
            val after=HostedRoomProtocol.capabilities(socket.roomCapabilities(scope.owner.profile),scope)
            sameAuthority(before,after);requireMethod(after,"groups.list")
            // A forwarded first page can legitimately contain rooms held by another authority.
            HostedRoomListing(after,page,includeDisbanded)
        }}
        verify(scope,api,tokens);validate(scope,api,tokens);return result
    }
    override suspend fun detail(listing:HostedRoomListing,selected:HostedRoom,api:ManagementClient,tokens:ManagementTokens):HostedRoomDetail {
        val scope=listing.scope
        if(listing.page.rooms.none {it===selected}) throw HostedRoomScopeChanged()
        validate(scope,api,tokens);verify(scope,api,tokens)
        val result=api.gateway(tokens).use {socket->withContext(Dispatchers.Default) {
            val before=HostedRoomProtocol.capabilities(socket.roomCapabilities(scope.owner.profile),scope)
            sameAuthority(listing.capabilities,before);requireMethod(before,"groups.state")
            val detail=HostedRoomProtocol.detail(socket.room(scope.owner.profile,selected.id,listing.includeDisbanded),scope,selected.id,now())
            if(detail.room.authorityId!=selected.authorityId || detail.room.authorityEpoch!=selected.authorityEpoch || detail.room.revision<selected.revision) throw HostedRoomScopeChanged()
            val after=HostedRoomProtocol.capabilities(socket.roomCapabilities(scope.owner.profile),scope)
            sameAuthority(before,after);requireMethod(after,"groups.state");detail
        }}
        verify(scope,api,tokens);validate(scope,api,tokens);return result
    }
}
