package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*

internal object CanonicalViewerFixtures {
    val target=ManagementTarget("b".repeat(64),"https://hermes.test/dashboard/","default","fixture · owner")
    const val profile="/fixture/home"
    val home=checkNotNull(PluginHome.project(JsonPrimitive(profile))).identity()
    val binding=CanonicalRuntimeBinding(target,home,profile,"runtime",7)
    val owner=CanonicalFeedOwner(target,home,profile,"runtime","resolved-history",7)
    val cursor=CanonicalReplayCursor("ring",4)
    val tokens=ManagementTokens("viewer-access-fixture","viewer-refresh-fixture","fixture","owner",4102444800)
    fun inventory(scope:CanonicalSessionScope=CanonicalSessionScope.Live)=CanonicalSessionInventory(binding,
        listOf(CanonicalStoredSession("history","history","resolved-history","Private fixture title","cli","fixture-model",2,1.0,false,"fingerprint")),scope)
    fun snapshot()=CanonicalSessionSnapshot(owner,"history","reported-history","subscription",cursor,2,3,false,2,true)
    fun delivery(sequence:Long=4,events:List<CanonicalFeedEvent> = emptyList(),phase:CanonicalFeedPhase=CanonicalFeedPhase.Following)=
        CanonicalFeedDelivery(CanonicalFeedStatus(phase,CanonicalReplayCursor("ring",sequence),null,0),events)
    fun event(sequence:Long,type:String="message.delta",text:String="Private fixture reply")=CanonicalFeedEvent.parse(buildJsonObject {
        put("type",type);put("session_id",owner.sessionId);put("replay_epoch","ring");put("seq",sequence)
        put("payload",buildJsonObject {put("text",text)})
    },owner,cursor,65536)
    fun receipt()=ManagementOperation("viewer-receipt",target.resourceScope,target.apiScope,target.url,target.profile,target.actor,
        CanonicalResumeAction.Id,"history",inventory().review("history").summary(),"confirmed",snapshot().receiptSummary(),1,home)
}
