package org.hermesmobile.app.ui

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import kotlinx.serialization.json.*
import java.util.Locale

internal class CanonicalViewerAccess(
    val target:ManagementTarget,val home:String,val generation:Long,
    val api:ManagementClient,val tokens:ManagementTokens
) {
    fun same(other:CanonicalViewerAccess?) = other!=null && target==other.target && home==other.home &&
        generation==other.generation && tokens===other.tokens
    override fun toString()="CanonicalViewerAccess(redacted)"
}
internal enum class CanonicalViewerPhase { Closed, Loading, Inventory, Opening, Following, Held }
internal class CanonicalViewerReview(val approval:CanonicalResumeReview,val generation:Long) {
    override fun toString()="CanonicalViewerReview(redacted)"
}
internal class CanonicalActivity(val sequence:Long,val kind:Words,val text:String,val truncated:Boolean=false) {
    override fun toString()="CanonicalActivity(redacted)"
}
internal data class CanonicalViewerState(
    val phase:CanonicalViewerPhase=CanonicalViewerPhase.Closed,
    val scope:CanonicalSessionScope=CanonicalSessionScope.Live,
    val inventory:CanonicalSessionInventory?=null,val review:CanonicalViewerReview?=null,
    val title:String="",val sessionId:String="",val receiptKey:String="",
    val cursor:CanonicalReplayCursor?=null,val observed:Long=0,
    val activities:List<CanonicalActivity> = emptyList(),val heartbeatAt:Long?=null,val error:Words?=null,
    val workspace:CanonicalWorkspaceDirectory?=null,val workspaceObservedAt:Long?=null,
    val workspaceSnapshot:CanonicalSessionSnapshot?=null,val workspaceChanging:Boolean=false,val workspaceNotice:Words?=null
) {
    val busy get()=phase in setOf(CanonicalViewerPhase.Loading,CanonicalViewerPhase.Opening)
    override fun toString()="CanonicalViewerState("+phase+")"
}

/** Foreground viewer only. Never retries adoption/work or turns observations into execution status. */
internal class CanonicalViewerController(
    private val scope:CoroutineScope,
    private val repository:CanonicalViewerRepository,
    private val context:()->CanonicalViewerAccess?,
    private val guard:(Any,Boolean)->Unit,
    private val now:()->Long=System::currentTimeMillis,
    private val heartbeatDelay:Long=15_000,
    private val heartbeatDeadline:Long=20_000
) {
    val state=MutableStateFlow(CanonicalViewerState())
    private var generation=0L
    private var task:Job?=null
    private var workspaceTask:Job?=null
    private var connection:CanonicalViewerLink?=null
    private var acceptedContext:CanonicalViewerAccess?=null
    private fun current(id:Long,access:CanonicalViewerAccess) = id==generation && access.same(context())
    private fun requireCurrent(id:Long,access:CanonicalViewerAccess) {
        if(!current(id,access)) {
            if(id==generation) {close();state.value=state.value.copy(error=Words.ManagementReviewChanged)}
            throw CancellationException("Viewer scope changed.")
        }
    }
    private fun error(error:Throwable):Words=when(error) {
        is CanonicalReviewChanged,is CanonicalScopeChanged,is CanonicalApprovalUsed->Words.ManagementReviewChanged
        is CanonicalNotSupported->Words.ViewerUnsupported
        is ManagementOutcomePending->Words.ManagementOutcomePending
        is CanonicalAdoptionAcknowledged->Words.ViewerAdoptionKnown
        is HermesHttpException->if(error.code==401) Words.ManagementExpired else Words.ViewerHeld
        else->Words.ViewerHeld
    }
    private fun launch(id:Long,access:CanonicalViewerAccess,block:suspend ()->Unit) {
        val owner=Any();guard(owner,true)
        task=scope.launch {
            try {requireCurrent(id,access);block()}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                if(current(id,access)) state.value=state.value.copy(phase=CanonicalViewerPhase.Held,review=null,error=error(e))
            }
        }.also {it.invokeOnCompletion {guard(owner,false)}}
    }
    fun load(selected:CanonicalSessionScope) {
        if(state.value.busy || state.value.phase==CanonicalViewerPhase.Following) return
        close()
        val access=context() ?: run {state.value=CanonicalViewerState(error=Words.ManagementVerify);return}
        val id=generation;acceptedContext=access
        state.value=CanonicalViewerState(phase=CanonicalViewerPhase.Loading,scope=selected)
        launch(id,access) {
            val inventory=repository.prepare(access.target,access.home,access.api,access.tokens,selected)
            requireCurrent(id,access)
            state.value=state.value.copy(phase=CanonicalViewerPhase.Inventory,inventory=inventory)
        }
    }
    fun review(sessionId:String) {
        val value=state.value;val access=acceptedContext ?: return
        if(value.phase!=CanonicalViewerPhase.Inventory || !access.same(context())) return
        val inventory=value.inventory ?: return
        val approval=try {inventory.review(sessionId)} catch(_:CanonicalReviewChanged) {
            state.value=value.copy(review=null,error=Words.ManagementReviewChanged);return
        }
        state.value=value.copy(review=CanonicalViewerReview(approval,generation),error=null)
    }
    fun cancelReview() {state.value=state.value.copy(review=null)}
    fun reviewCurrent(review:CanonicalViewerReview) = state.value.review===review && review.generation==generation &&
        acceptedContext?.same(context())==true && state.value.phase==CanonicalViewerPhase.Inventory
    fun approve(review:CanonicalViewerReview,fresh:Boolean) {
        if(!reviewCurrent(review)) return
        val access=checkNotNull(acceptedContext);val id=generation
        // Remove this one-use UI approval before launching any asynchronous work.
        state.value=state.value.copy(phase=CanonicalViewerPhase.Opening,review=null,title=review.approval.row.title,
            sessionId=review.approval.row.sessionId,error=null)
        launch(id,access) {
            val previous=if(fresh) null else repository.lastApplied(access.target,access.home,review.approval.row.sessionId)
            requireCurrent(id,access)
            var link:CanonicalViewerLink?=null
            try {
                val attached=repository.adopt(review.approval,access.api,access.tokens,previous,requireHeartbeat=true)
                link=attached
                requireCurrent(id,access)
                connection=attached
                state.value=state.value.copy(receiptKey=attached.receipt.key,sessionId=attached.attachment.snapshot.owner.sessionId,
                    workspace=attached.attachment.snapshot.workspace,workspaceObservedAt=now(),workspaceSnapshot=attached.attachment.snapshot)
                beat(attached)
                requireCurrent(id,access)
                apply(id,access,attached,attached.attachment.initial)
                state.value=state.value.copy(phase=CanonicalViewerPhase.Following,heartbeatAt=now())
                coroutineScope {
                    launch {
                        while(isActive) {
                            delay(heartbeatDelay);requireCurrent(id,access);beat(attached);requireCurrent(id,access)
                            state.value=state.value.copy(heartbeatAt=now())
                        }
                    }
                    attached.observations().collect {delivery->apply(id,access,attached,delivery)}
                    throw GatewayUnavailable()
                }
            } finally {link?.close();if(connection===link) connection=null}
        }
    }
    private suspend fun beat(link:CanonicalViewerLink) {
        if(withTimeoutOrNull(heartbeatDeadline) {link.heartbeat();true}!=true) throw GatewayUnavailable()
    }
    private suspend fun apply(id:Long,access:CanonicalViewerAccess,link:CanonicalViewerLink,delivery:CanonicalFeedDelivery) {
        requireCurrent(id,access)
        if(delivery.status.phase!=CanonicalFeedPhase.Following) throw GatewayProtocolException()
        val before=state.value
        if(before.cursor?.let {it.epoch!=delivery.status.cursor.epoch || it.sequence>delivery.status.cursor.sequence}==true)
            throw GatewayProtocolException()
        val activities=delivery.events.map(::activity)
        require(delivery.events.size.toLong()<=Long.MAX_VALUE-before.observed)
        // "Applied" means committed to this viewer's projection, not merely queued by the socket.
        state.value=before.copy(cursor=delivery.status.cursor,observed=before.observed+activities.size,
            activities=(before.activities+activities).takeLast(40))
        withContext(NonCancellable) {repository.recordApplied(link.receipt.key,link.attachment.snapshot.owner,delivery.status.cursor)}
        requireCurrent(id,access)
    }
    private fun activity(event:CanonicalFeedEvent):CanonicalActivity {
        val type=event.type
        val privateType=type.lowercase(Locale.ROOT).split('.').any {it=="reasoning" || it=="thinking"}
        val statusKind=(event.payload["kind"] as? JsonPrimitive)?.takeIf {it.isString}?.content?.lowercase(Locale.ROOT)
        if(privateType || (type=="status.update" && statusKind in setOf("reasoning","thinking")))
            return CanonicalActivity(event.sequence,Words.ViewerPrivateActivity,"")
        val replies=setOf("message.delta","message.interim","message.complete")
        val publicSubagents=setOf("subagent.spawn_requested","subagent.start","subagent.progress","subagent.tool","subagent.complete")
        val kind=when {
            type in replies->Words.ViewerReplyActivity
            type.startsWith("tool.")->Words.ViewerToolActivity
            type in publicSubagents->Words.ViewerSubagentActivity
            type.startsWith("approval.") || type.startsWith("clarify.")->Words.ViewerDecisionActivity
            else->Words.ViewerOtherActivity
        }
        // Only schema-reviewed public fields become previews. Unknown/private payloads still
        // apply their sequence, with no generic text/content/message fallback.
        val fields=when(type) {
            "message.delta","message.interim","message.complete","status.update"->listOf("text")
            "tool.start","tool.complete","tool.generating","tool.progress","tool.output_risk"->listOf("name","tool_name")
            "subagent.spawn_requested","subagent.start","subagent.progress","subagent.tool","subagent.complete"->listOf("summary","tool_name")
            else->emptyList()
        }
        val raw=fields.firstNotNullOfOrNull {key->
            (event.payload[key] as? JsonPrimitive)?.takeIf {it.isString}?.content?.takeIf {it.isNotBlank()}
        }.orEmpty()
        val clean=raw.filter {it=='\n' || it=='\t' || !it.isISOControl()}
        val end=clean.offsetByCodePoints(0,minOf(512,clean.codePointCount(0,clean.length)))
        return CanonicalActivity(event.sequence,kind,clean.substring(0,end),end<clean.length)
    }
    fun workspaceCurrent(snapshot:CanonicalSessionSnapshot)=state.value.workspaceSnapshot===snapshot &&
        state.value.phase==CanonicalViewerPhase.Following && !state.value.workspaceChanging &&
        acceptedContext?.same(context())==true && CanonicalWorkspaceReview.eligible(snapshot)
    fun changeWorkspace(review:CanonicalWorkspaceReview) {
        if(!workspaceCurrent(review.expected)) return
        val access=checkNotNull(acceptedContext);val id=generation;val owner=Any()
        state.value=state.value.copy(workspaceChanging=true,workspaceNotice=null);guard(owner,true)
        workspaceTask=scope.launch {
            try {
                requireCurrent(id,access)
                repository.changeWorkspace(review,access.api,access.tokens)
                requireCurrent(id,access)
                // The old attachment is now stale. A fresh one needs a new explicit review.
                generation++;task?.cancel();task=null;connection?.close();connection=null;acceptedContext=null
                state.value=CanonicalViewerState(workspaceNotice=Words.WorkspaceAcknowledged)
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                if(current(id,access)) state.value=state.value.copy(workspaceChanging=false,
                    workspaceSnapshot=if(e is CanonicalWorkspaceAcknowledged) null else state.value.workspaceSnapshot,workspaceNotice=when(e) {
                        is CanonicalWorkspaceAcknowledged->Words.WorkspaceAcknowledged
                        is CanonicalReviewChanged,is CanonicalApprovalUsed,is CanonicalScopeChanged->Words.ManagementReviewChanged
                        is ManagementOutcomePending->Words.ManagementOutcomePending
                        else->Words.WorkspaceUncertain
                    })
            } finally {guard(owner,false)}
        }
    }
    fun close() {
        generation++;task?.cancel();task=null;workspaceTask?.cancel();workspaceTask=null;connection?.close();connection=null;acceptedContext=null
        state.value=CanonicalViewerState()
    }
}
