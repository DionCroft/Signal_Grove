package org.hermesmobile.app

import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class ManagementInstanceTest {
    private val owner=ManagementTarget("a".repeat(64),"https://fixture.example/dashboard/","private-old-profile","password · private-actor")
    private val other=owner.copy(profile="worker",apiScope="b".repeat(64))
    private fun receipt(action:String="RelayLinkStart",state:String="unknown")=ManagementOperation("opaque-key",owner.resourceScope,owner.apiScope,owner.url,owner.profile,owner.actor,action,"private-target","private-request",state,"private-result",123456789)
    private fun project(row:ManagementOperation,target:ManagementTarget=other)=ManagementReceiptVisibility.project(listOf(row),target.apiScope,target.resourceScope,target.url)
    @Test fun crossProfileInstanceWorkIsVisibleWithoutPrivateMetadata() {
        for(action in listOf("RelayLinkStart","RelayUnlink","CustomEndpointProbe",HostKanbanStatusChange.ReceiptAction)) for(state in listOf("sending","unknown")) {
            val row=project(receipt(action,state)).single()
            assertEquals(ManagementReceiptVisibility.SharedInstancePending,row.action);assertEquals(state,row.state);assertEquals("opaque-key",row.key)
            assertEquals(owner.url,row.url);assertEquals("",row.profile);assertEquals("",row.resourceScope);assertEquals("",row.apiScope);assertEquals(0L,row.createdAt)
            assertFalse(row.toString().contains("private-"));assertFalse(row.toString().contains(owner.resourceScope));assertFalse(row.toString().contains(owner.apiScope))
        }
    }
    @Test fun sharingInferenceCredentialDoesNotExposeAnotherProfilesRelayDetails() {
        val row=project(receipt(),other.copy(apiScope=owner.apiScope)).single()
        assertEquals(ManagementReceiptVisibility.SharedInstancePending,row.action);assertEquals("",row.actor);assertFalse(row.toString().contains("private-"))
    }
    @Test fun projectionSurvivesCollectorAndProfileChangesButNotAnotherDashboard() {
        val row=project(receipt()).single()
        val again=project(row,other.copy(profile="another",apiScope="c".repeat(64))).single()
        assertEquals(row,again)
        for(url in listOf("https://other.example/dashboard/","https://fixture.example/other/","https://fixture.example:8443/dashboard/")) assertTrue(project(row,other.copy(url=url)).isEmpty())
        assertTrue(ManagementReceiptVisibility.project(listOf(row),other.apiScope,"",other.url).isEmpty())
    }
    @Test fun settledForeignProfileRelayHistoryNeverAppears() {
        for(state in listOf("acknowledged","confirmed","reviewed")) assertTrue(project(receipt(state=state)).isEmpty())
    }
    @Test fun ordinaryOtherProfileWorkRemainsIndependent() {
        for(state in listOf("sending","unknown")) assertTrue(project(receipt("Probe",state)).isEmpty())
    }
    @Test fun ownSameProfileRelayReceiptRetainsItsOriginalHistory() {
        val row=receipt();assertSame(row,project(row,owner).single())
    }
    @Test fun otherConnectionSameProfileRelayGetsInstanceReviewRatherThanProfileOnlyReview() {
        val row=project(receipt(),owner.copy(apiScope=other.apiScope)).single()
        assertEquals(ManagementReceiptVisibility.SharedInstancePending,row.action);assertEquals("",row.profile);assertEquals("",row.resourceScope)
    }
    @Test fun ordinarySameProfilePrivatePendingBehaviorIsPreserved() {
        val row=project(receipt("Probe"),owner.copy(apiScope=other.apiScope)).single()
        assertEquals(ManagementReceiptVisibility.SharedPending,row.action);assertEquals(owner.profile,row.profile);assertEquals(owner.resourceScope,row.resourceScope)
    }
    @Test fun pendingStatusBlocksEveryProfileOnTheSameEndpointWithoutExposingDetails() {
        val original=receipt(HostKanbanStatusChange.ReceiptAction)
        for(target in listOf(other,other.copy(apiScope=owner.apiScope),owner.copy(apiScope=other.apiScope))) {
            val shared=project(original,target).single()
            assertEquals(ManagementReceiptVisibility.SharedInstancePending,shared.action)
            assertEquals("",shared.actor);assertEquals("",shared.profile);assertEquals("",shared.requestSummary);assertEquals("",shared.resultSummary);assertEquals("",shared.target)
            assertFalse(shared.toString().contains("private-"));assertEquals("unknown",shared.state)
        }
        assertTrue(project(original,other.copy(url="https://different.example/dashboard/")).isEmpty())
    }
    @Test fun settledStatusHistoryBelongsToItsExactProfileAndCredentialScope() {
        val original=receipt(HostKanbanStatusChange.ReceiptAction,"confirmed")
        assertEquals(listOf(original),project(original,owner))
        assertTrue(project(original).isEmpty())
        assertTrue(project(original,owner.copy(apiScope=other.apiScope)).isEmpty())
        assertTrue(project(original,other.copy(apiScope=owner.apiScope)).isEmpty())
    }

}
