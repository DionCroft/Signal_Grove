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
internal data class CanonicalViewerSelection(val target:ManagementTarget,val home:String,val contextGeneration:Long,val sessionId:String) {override fun toString()="CanonicalViewerSelection(redacted)"}
internal enum class CanonicalViewerPhase { Closed, Loading, Inventory, Opening, Following, Held }
internal class CanonicalViewerReview(val approval:CanonicalResumeReview,val generation:Long) {
    override fun toString()="CanonicalViewerReview(redacted)"
}
internal class CanonicalActivity(val sequence:Long,val kind:Words,val text:String,val truncated:Boolean=false) {
    override fun toString()="CanonicalActivity(redacted)"
}
internal sealed interface HermesContextRead {
    data object Loading : HermesContextRead
    data class Report(val stats: HermesContextStats, val observedAt: Long) : HermesContextRead
    data class Unavailable(val message: Words) : HermesContextRead
}

internal sealed interface CanonicalProjectFiles {
    data object Loading : CanonicalProjectFiles
    class Review(val project: AttachedWorkerProject) : CanonicalProjectFiles
    class Folder(val binding: AttachedWorkerBinding, val pages: List<AttachedWorkerListing>) : CanonicalProjectFiles {
        val current get() = pages.last()
    }
    class Preview(val folder: Folder, val preview: AttachedWorkerPreview) : CanonicalProjectFiles
    class Unavailable(val message: Words) : CanonicalProjectFiles
}

internal data class CanonicalViewerState(
    val phase:CanonicalViewerPhase=CanonicalViewerPhase.Closed,
    val scope:CanonicalSessionScope=CanonicalSessionScope.Live,
    val inventory:CanonicalSessionInventory?=null,val review:CanonicalViewerReview?=null,
    val title:String="",val sessionId:String="",val receiptKey:String="",
    val cursor:CanonicalReplayCursor?=null,val observed:Long=0,
    val activities:List<CanonicalActivity> = emptyList(),val heartbeatAt:Long?=null,val error:Words?=null,
    val workspace:CanonicalWorkspaceDirectory?=null,val workspaceObservedAt:Long?=null,
    val workspaceSnapshot:CanonicalSessionSnapshot?=null,val workspaceChanging:Boolean=false,val workspaceNotice:Words?=null,
    val hermesContext: HermesContextRead? = null,
    val projectFiles: CanonicalProjectFiles? = null, val projectReading: Boolean = false
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
    private var contextTask: Job? = null
    private var projectTask: Job? = null
    private var projectReadGeneration = 0L
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
        is CanonicalProfileTransportUnavailable->Words.ViewerProfileTransportUnavailable
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
    fun load(selected:CanonicalSessionScope)=load(selected,null)
    internal fun loadSelection(selection:CanonicalViewerSelection)=load(CanonicalSessionScope.Stored,selection)
    private fun load(selected:CanonicalSessionScope,selection:CanonicalViewerSelection?) {
        if(state.value.busy || state.value.phase==CanonicalViewerPhase.Following) return
        close()
        val access=context() ?: run {state.value=CanonicalViewerState(error=Words.ManagementVerify);return}
        if(selection!=null && (selection.target!=access.target || selection.home!=access.home || selection.contextGeneration!=access.generation)) {
            state.value=CanonicalViewerState(error=Words.ManagementReviewChanged);return
        }
        val id=generation;acceptedContext=access
        state.value=CanonicalViewerState(phase=CanonicalViewerPhase.Loading,scope=selected)
        launch(id,access) {
            val inventory=repository.prepare(access.target,access.home,access.api,access.tokens,selected)
            requireCurrent(id,access)
            state.value=state.value.copy(phase=CanonicalViewerPhase.Inventory,inventory=inventory)
            if(selection!=null) {
                if(inventory.rows.none {it.sessionId==selection.sessionId}) state.value=state.value.copy(error=Words.ProjectConversationNotListed)
                else review(selection.sessionId)
            }
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
                            delay(heartbeatDelay)
                            requireCurrent(id,access)
                            // The native socket serializes RPCs. Do not start a heartbeat timeout
                            // while a bounded worker read is already checking that same scope.
                            if (projectTask?.isActive == true) continue
                            beat(attached)
                            requireCurrent(id,access)
                            state.value=state.value.copy(heartbeatAt=now())
                        }
                    }
                    attached.observations().collect {delivery->apply(id,access,attached,delivery)}
                    throw GatewayUnavailable()
                }
            } finally {
                link?.close()
                if (connection === link) {
                    connection = null
                    clearContextRead()
                    clearProjectRead()
                }
            }
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
    fun readContext() {
        val access = acceptedContext ?: return
        val link = connection ?: return
        val id = generation
        fun currentViewer() = current(id, access) && connection === link &&
            state.value.phase == CanonicalViewerPhase.Following && !state.value.workspaceChanging
        if (!currentViewer() || state.value.hermesContext == HermesContextRead.Loading) return
        state.update { it.copy(hermesContext = HermesContextRead.Loading) }
        contextTask = scope.launch {
            try {
                requireCurrent(id, access)
                val report = repository.readContext(link.attachment.snapshot.owner, access.api, access.tokens)
                currentCoroutineContext().ensureActive()
                requireCurrent(id, access)
                if (currentViewer()) state.update { it.copy(hermesContext = HermesContextRead.Report(report, now())) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                requireCurrent(id, access)
                if (currentViewer()) {
                    val message = when {
                        error is GatewayRpcException && error.code == -32601 -> Words.ViewerContextUnsupported
                        error is CanonicalNotSupported -> Words.ViewerContextUnsupported
                        error is CanonicalScopeChanged || error is CanonicalReviewChanged -> Words.ManagementReviewChanged
                        error is HermesHttpException && error.code == 401 -> Words.ManagementExpired
                        else -> Words.ViewerContextUnavailable
                    }
                    state.update { it.copy(hermesContext = HermesContextRead.Unavailable(message)) }
                }
            }
        }
    }

    private fun clearContextRead() {
        contextTask?.cancel()
        contextTask = null
        state.update { it.copy(hermesContext = null) }
    }

    fun reviewProjectFiles() {
        if (state.value.projectFiles != null) return
        readProjectFiles { link -> CanonicalProjectFiles.Review(link.describeWorker()) }
    }

    fun openProjectFiles(review: CanonicalProjectFiles.Review) {
        if (state.value.projectFiles !== review || review.project.instance == null) return
        readProjectFiles { link ->
            val binding = link.prepareWorker(review.project)
            ensureCurrentProjectRead()
            val root = link.listWorker(binding, null)
            CanonicalProjectFiles.Folder(binding, listOf(root))
        }
    }

    fun openProjectEntry(folder: CanonicalProjectFiles.Folder, entry: AttachedWorkerEntry) {
        if (state.value.projectFiles !== folder || folder.current.entries.none { it === entry }) return
        readProjectFiles { link ->
            if (entry.directory) {
                CanonicalProjectFiles.Folder(folder.binding, folder.pages + link.listWorker(folder.binding, entry))
            } else {
                CanonicalProjectFiles.Preview(folder, link.previewWorker(folder.binding, entry))
            }
        }
    }

    fun backInProjectFiles() {
        when (val view = state.value.projectFiles) {
            is CanonicalProjectFiles.Preview -> state.update { it.copy(projectFiles = view.folder) }
            is CanonicalProjectFiles.Folder -> {
                if (view.pages.size > 1) state.update {
                    it.copy(projectFiles = CanonicalProjectFiles.Folder(view.binding, view.pages.dropLast(1)))
                }
            }
            else -> Unit
        }
    }

    fun closeProjectFiles() {
        projectReadGeneration++
        state.update { it.copy(projectFiles = null) }
    }

    private fun readProjectFiles(block: suspend ProjectRead.(CanonicalViewerLink) -> CanonicalProjectFiles) {
        val link = connection ?: return
        val access = acceptedContext ?: return
        val viewer = generation
        if (!current(viewer, access) || state.value.phase != CanonicalViewerPhase.Following ||
            state.value.workspaceChanging || projectTask?.isActive == true) return
        val read = ++projectReadGeneration
        fun holdsScope() = read == projectReadGeneration && current(viewer, access) && connection === link &&
            state.value.phase == CanonicalViewerPhase.Following && !state.value.workspaceChanging
        state.update { it.copy(projectFiles = CanonicalProjectFiles.Loading, projectReading = true) }
        projectTask = scope.launch {
            val request = ProjectRead(::holdsScope)
            try {
                request.ensureCurrentProjectRead()
                val result = request.block(link)
                request.ensureCurrentProjectRead()
                state.update { it.copy(projectFiles = result) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (holdsScope()) state.update {
                    it.copy(projectFiles = CanonicalProjectFiles.Unavailable(projectReadError(error)))
                }
            } finally {
                if (generation == viewer && connection === link) state.update { it.copy(projectReading = false) }
            }
        }
    }

    private class ProjectRead(private val holdsScope: () -> Boolean) {
        fun ensureCurrentProjectRead() {
            if (!holdsScope()) throw CancellationException("Project browser closed.")
        }
    }

    private fun projectReadError(error: Exception): Words = when (error) {
        is CanonicalNotSupported -> Words.ViewerFilesUnsupported
        is CanonicalReviewChanged, is CanonicalScopeChanged -> Words.ManagementReviewChanged
        is HermesHttpException -> if (error.code == 401) Words.ManagementExpired else Words.ViewerFilesUnavailable
        is AttachedWorkerRefused -> when (error.code) {
            "DENIED" -> Words.ViewerFilesNotAttached
            "CHANGED" -> Words.ViewerFilesChanged
            "EXPIRED" -> Words.ViewerFilesExpired
            "TOO_LARGE" -> Words.ViewerFileTooLarge
            else -> Words.ViewerFilesUnavailable
        }
        else -> Words.ViewerFilesUnavailable
    }

    private fun clearProjectRead() {
        closeProjectFiles()
        projectTask?.cancel()
        projectTask = null
        state.update { it.copy(projectReading = false) }
    }

    fun workspaceCurrent(snapshot:CanonicalSessionSnapshot)=state.value.workspaceSnapshot===snapshot &&
        state.value.phase==CanonicalViewerPhase.Following && !state.value.workspaceChanging &&
        acceptedContext?.same(context())==true && projectTask?.isActive != true && CanonicalWorkspaceReview.eligible(snapshot)
    fun changeWorkspace(review:CanonicalWorkspaceReview) {
        if(!workspaceCurrent(review.expected)) return
        val access=checkNotNull(acceptedContext);val id=generation;val owner=Any()
        clearContextRead()
        closeProjectFiles()
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
                        is CanonicalWorkspaceRefused->when(e.refusal) {
                            CanonicalWorkspaceRefusal.Busy->Words.WorkspaceBusy
                            CanonicalWorkspaceRefusal.Compressing->Words.WorkspaceCompressing
                            CanonicalWorkspaceRefusal.Permission->Words.WorkspacePermission
                        }
                        else->Words.WorkspaceUncertain
                    })
            } finally {guard(owner,false)}
        }
    }
    fun close() {
        generation++;task?.cancel();task=null;workspaceTask?.cancel();workspaceTask=null;connection?.close();connection=null;acceptedContext=null
        clearContextRead()
        clearProjectRead()
        state.value=CanonicalViewerState()
    }
}
