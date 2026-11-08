package org.hermesmobile.app.ui

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*

internal class HostedRoomAccess(val binding:HostedRoomScope,val generation:Long,val api:ManagementClient,val tokens:ManagementTokens) {
    fun same(other:HostedRoomAccess?)=other!=null && binding==other.binding && generation==other.generation && tokens===other.tokens
    override fun toString()="HostedRoomAccess(redacted)"
}
internal enum class HostedRoomBrowserPhase {Closed,Loading,Page,Detail,Held}
internal data class HostedRoomBrowserState(val phase:HostedRoomBrowserPhase=HostedRoomBrowserPhase.Closed,val listing:HostedRoomListing?=null,val detail:HostedRoomDetail?=null,val includeDisbanded:Boolean=false,val error:Words?=null) {
    val busy get()=phase==HostedRoomBrowserPhase.Loading
    override fun toString()="HostedRoomBrowserState("+phase+")"
}

/** One foreground metadata browser. No background polling, retries, controls or work admission. */
internal class HostedRoomBrowserController(private val scope:CoroutineScope,private val reader:HostedRoomReader,private val context:()->HostedRoomAccess?,private val guard:(Any,Boolean)->Unit) {
    private val mutable=MutableStateFlow(HostedRoomBrowserState())
    val state=mutable.asStateFlow()
    private var generation=0L
    private var work:Job?=null
    private var accepted:HostedRoomAccess?=null
    private fun current(id:Long,access:HostedRoomAccess)=id==generation && access.same(context())
    private fun requireCurrent(id:Long,access:HostedRoomAccess) {
        if(!current(id,access)) {
            if(id==generation) {close();mutable.value=mutable.value.copy(error=Words.ManagementReviewChanged)}
            throw CancellationException("Room context changed.")
        }
    }
    private fun error(e:Exception)=when(e) {
        is HostedRoomScopeChanged->Words.ManagementReviewChanged
        is HostedRoomsUnsupported->Words.RoomsUnsupported
        is HermesHttpException->if(e.code==401) Words.ManagementExpired else Words.RoomsReadHeld
        is GatewayRpcException->if(e.code==-32601) Words.RoomsUnsupported else Words.RoomsReadHeld
        else->Words.RoomsReadHeld
    }
    private fun launch(access:HostedRoomAccess,block:suspend (Long)->Unit) {
        work?.cancel();val id=++generation;accepted=access
        val lease=Any();guard(lease,true)
        work=scope.launch {
            try {requireCurrent(id,access);block(id)}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {if(current(id,access)) mutable.value=mutable.value.copy(phase=HostedRoomBrowserPhase.Held,detail=null,error=error(e))}
        }.also {it.invokeOnCompletion {guard(lease,false)}}
    }
    private fun access():HostedRoomAccess?=context().also {if(it==null) {close();mutable.value=mutable.value.copy(error=Words.ManagementVerify)}}
    fun reload(includeDisbanded:Boolean=mutable.value.includeDisbanded) {
        if(mutable.value.busy) return
        val access=access() ?: return
        val previous=mutable.value.listing?.takeIf {it.scope==access.binding && it.includeDisbanded==includeDisbanded && accepted?.same(access)==true}
        mutable.value=HostedRoomBrowserState(HostedRoomBrowserPhase.Loading,previous,includeDisbanded=includeDisbanded)
        launch(access) {id->
            val result=reader.page(access.binding,access.api,access.tokens,0,includeDisbanded);requireCurrent(id,access)
            if(result.scope!=access.binding || result.page.offset!=0 || result.includeDisbanded!=includeDisbanded) throw HostedRoomScopeChanged()
            mutable.value=HostedRoomBrowserState(HostedRoomBrowserPhase.Page,result,includeDisbanded=includeDisbanded)
        }
    }
    fun more() {
        if(mutable.value.busy) return
        val before=mutable.value;val listing=before.listing ?: return;val offset=listing.page.nextOffset ?: return
        val access=access() ?: return
        if(!access.same(accepted) || access.binding!=listing.scope) {close();mutable.value=mutable.value.copy(error=Words.ManagementReviewChanged);return}
        mutable.value=before.copy(phase=HostedRoomBrowserPhase.Loading,detail=null,error=null)
        launch(access) {id->
            val result=reader.page(access.binding,access.api,access.tokens,offset,listing.includeDisbanded);requireCurrent(id,access)
            if(result.scope!=access.binding || result.page.offset!=offset || result.includeDisbanded!=listing.includeDisbanded) throw HostedRoomScopeChanged()
            mutable.value=HostedRoomBrowserState(HostedRoomBrowserPhase.Page,result,includeDisbanded=listing.includeDisbanded)
        }
    }
    fun detail(selected:HostedRoom) {
        if(mutable.value.busy) return
        val before=mutable.value;val listing=before.listing ?: return
        val access=access() ?: return
        if(!access.same(accepted) || access.binding!=listing.scope) {
            close();mutable.value=mutable.value.copy(error=Words.ManagementReviewChanged);return
        }
        if(listing.page.rooms.none {it===selected}) {mutable.value=before.copy(error=Words.ManagementReviewChanged);return}
        if("groups.state" !in listing.capabilities.methods) {mutable.value=before.copy(error=Words.RoomsStateUnavailable);return}
        mutable.value=before.copy(phase=HostedRoomBrowserPhase.Loading,detail=null,error=null)
        launch(access) {id->
            val result=reader.detail(listing,selected,access.api,access.tokens);requireCurrent(id,access)
            if(result.scope!=access.binding || result.room.id!=selected.id || result.room.authorityId!=selected.authorityId || result.room.authorityEpoch!=selected.authorityEpoch || result.room.revision<selected.revision) throw HostedRoomScopeChanged()
            mutable.value=before.copy(phase=HostedRoomBrowserPhase.Detail,detail=result,error=null)
        }
    }
    fun back() {
        if(mutable.value.busy) return
        val access=access() ?: return
        if(!access.same(accepted)) {close();mutable.value=mutable.value.copy(error=Words.ManagementReviewChanged);return}
        mutable.value=mutable.value.copy(phase=HostedRoomBrowserPhase.Page,detail=null,error=null)
    }
    fun recheckContext() {if(accepted?.same(context())==false) close()}
    fun close() {generation++;work?.cancel();work=null;accepted=null;mutable.value=HostedRoomBrowserState()}
}
