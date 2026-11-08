package org.hermesmobile.app.data

import java.io.Closeable
import kotlinx.coroutines.flow.Flow
import org.hermesmobile.app.network.*

internal interface CanonicalViewerLink:Closeable {
    val attachment:CanonicalGatewayChannel.Attachment
    val receipt:ManagementOperation
    fun observations():Flow<CanonicalFeedDelivery>
    suspend fun heartbeat()
}
internal interface CanonicalViewerRepository {
    suspend fun prepare(target:ManagementTarget,homeId:String,api:ManagementClient,tokens:ManagementTokens,
                        scope:CanonicalSessionScope=CanonicalSessionScope.Stored):CanonicalSessionInventory
    suspend fun adopt(review:CanonicalResumeReview,api:ManagementClient,tokens:ManagementTokens,
                      previous:CanonicalReplayCheckpoint?=null,requireHeartbeat:Boolean=false):CanonicalViewerLink
    suspend fun lastApplied(target:ManagementTarget,homeId:String,requested:String):CanonicalReplayCheckpoint?
    suspend fun recordApplied(key:String,owner:CanonicalFeedOwner,cursor:CanonicalReplayCursor)
}
