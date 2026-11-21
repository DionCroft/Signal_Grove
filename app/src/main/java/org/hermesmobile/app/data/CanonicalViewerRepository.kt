package org.hermesmobile.app.data

import java.io.Closeable
import kotlinx.coroutines.flow.Flow
import org.hermesmobile.app.network.*

internal interface CanonicalViewerLink:Closeable {
    val attachment:CanonicalGatewayChannel.Attachment
    val receipt:ManagementOperation
    fun observations():Flow<CanonicalFeedDelivery>
    suspend fun heartbeat()
    suspend fun describeWorker(): AttachedWorkerProject = throw CanonicalNotSupported()
    suspend fun prepareWorker(project: AttachedWorkerProject): AttachedWorkerBinding = throw CanonicalNotSupported()
    suspend fun listWorker(binding: AttachedWorkerBinding, entry: AttachedWorkerEntry?): AttachedWorkerListing =
        throw CanonicalNotSupported()
    suspend fun previewWorker(binding: AttachedWorkerBinding, entry: AttachedWorkerEntry): AttachedWorkerPreview =
        throw CanonicalNotSupported()
}
internal interface CanonicalViewerRepository {
    suspend fun prepare(target:ManagementTarget,homeId:String,api:ManagementClient,tokens:ManagementTokens,
                        scope:CanonicalSessionScope=CanonicalSessionScope.Stored):CanonicalSessionInventory
    suspend fun adopt(review:CanonicalResumeReview,api:ManagementClient,tokens:ManagementTokens,
                      previous:CanonicalReplayCheckpoint?=null,requireHeartbeat:Boolean=false):CanonicalViewerLink
    suspend fun changeWorkspace(review:CanonicalWorkspaceReview,api:ManagementClient,tokens:ManagementTokens):CanonicalWorkspaceAcknowledgement =
        throw CanonicalNotSupported()
    suspend fun readContext(owner: CanonicalFeedOwner, api: ManagementClient, tokens: ManagementTokens): HermesContextStats
    suspend fun lastApplied(target:ManagementTarget,homeId:String,requested:String):CanonicalReplayCheckpoint?
    suspend fun recordApplied(key:String,owner:CanonicalFeedOwner,cursor:CanonicalReplayCursor)
}
