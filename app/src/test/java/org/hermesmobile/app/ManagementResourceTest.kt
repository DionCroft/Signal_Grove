package org.hermesmobile.app

import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class ManagementResourceTest {
    private val owner=ManagementTarget("a".repeat(64),"https://fixture.example/dashboard/","worker","password · private-user")
    private fun receipt(state:String="unknown")=ManagementOperation("opaque-key",owner.resourceScope,owner.apiScope,owner.url,owner.profile,owner.actor,"CustomEndpointSave","private-target","private-request",state,"private-result",123456789)
    @Test fun managementResourceDoesNotChangeWithInferenceCredentialOrNativeAccount() {
        val changed=owner.copy(apiScope="b".repeat(64),actor="password · second")
        assertEquals(owner.resourceScope,changed.resourceScope);assertNotEquals(owner,changed)
    }
    @Test fun exactDashboardPrefixPortAndProfileRemainIndependent() {
        for(changed in listOf(owner.copy(url="https://other.example/dashboard/"),owner.copy(url="https://fixture.example/other/"),owner.copy(url="https://fixture.example:8443/dashboard/"),owner.copy(profile="other"))) assertNotEquals(owner.resourceScope,changed.resourceScope)
    }
    @Test fun foreignPendingProjectionHasNoPrivateReceiptMetadata() {
        for(state in listOf("sending","unknown")) {
            val raw=receipt(state);val shown=ManagementReceiptVisibility.project(listOf(raw),"b".repeat(64),owner.resourceScope).single()
            assertEquals(raw.key,shown.key);assertEquals(state,shown.state);assertEquals(owner.resourceScope,shown.resourceScope)
            assertEquals(owner.url,shown.url);assertEquals(owner.profile,shown.profile);assertEquals(ManagementReceiptVisibility.SharedPending,shown.action)
            assertEquals("",shown.apiScope);assertEquals("",shown.actor);assertEquals("",shown.target);assertEquals("",shown.requestSummary);assertEquals("",shown.resultSummary);assertEquals(0L,shown.createdAt)
            assertFalse(shown.toString().contains("private-"));assertFalse(shown.toString().contains(owner.apiScope))
        }
    }
    @Test fun completedForeignHistoryAndOtherResourcesAreNotProjected() {
        val rows=listOf("acknowledged","confirmed","reviewed").map(::receipt)+receipt().copy(resourceScope=owner.copy(profile="other").resourceScope)
        assertTrue(ManagementReceiptVisibility.project(rows,"b".repeat(64),owner.resourceScope).isEmpty())
        assertTrue(ManagementReceiptVisibility.project(listOf(receipt()),owner.apiScope,"").isEmpty())
    }
    @Test fun ownHistoryStaysIntactAndStaleRowsAreRedactedAfterSwitching() {
        val raw=receipt();assertSame(raw,ManagementReceiptVisibility.project(listOf(raw),owner.apiScope,owner.resourceScope).single())
        val stale=ManagementReceiptVisibility.project(listOf(raw),"b".repeat(64),owner.resourceScope)
        assertFalse(stale.single().toString().contains("private-"))
        assertFalse(ManagementReceiptVisibility.project(stale,owner.apiScope,owner.resourceScope).single().toString().contains("private-"))
    }
}
