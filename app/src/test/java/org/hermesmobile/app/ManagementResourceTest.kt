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
    @Test fun timingEditRequiresTheOriginalActorAndVerifiedHome() {
        val home = "c".repeat(64)
        val row = receipt().copy(action = CronTimingProtocol.Action, projectHome = home)
        fun projected(actor: String, selectedHome: String, record: ManagementOperation = row) =
            ManagementReceiptVisibility.project(listOf(record), owner.apiScope, owner.resourceScope, owner.url, selectedHome, actor)
        assertSame(row, projected(owner.actor, home).single())
        for (redacted in listOf(projected("password · other", home).single(), projected(owner.actor, "").single())) {
            assertEquals(ManagementReceiptVisibility.SharedCronTimingPending, redacted.action)
            assertTrue(redacted.apiScope.isEmpty())
            assertTrue(redacted.actor.isEmpty())
            assertTrue(redacted.target.isEmpty())
            assertTrue(redacted.requestSummary.isEmpty())
            assertTrue(redacted.resultSummary.isEmpty())
            assertEquals(ManagementReceiptVisibility.SharedCronTimingPending, projected(owner.actor, home, redacted).single().action)
        }
        assertTrue(projected("password · other", home, row.copy(state = "confirmed")).isEmpty())
    }

    @Test fun aRunOnceReceiptRequiresOriginalActorConnectionAndHome() {
        val home = "c".repeat(64)
        val row = receipt().copy(action = CronRunOnceProtocol.Action, projectHome = home)
        fun shown(connection: String, actor: String, selectedHome: String) = ManagementReceiptVisibility.project(
            listOf(row), connection, owner.resourceScope, owner.url, selectedHome, actor).single()
        assertSame(row, shown(owner.apiScope, owner.actor, home))
        for (other in listOf(shown("d".repeat(64), owner.actor, home), shown(owner.apiScope, "other", home),
            shown(owner.apiScope, owner.actor, "e".repeat(64)))) {
            assertEquals(ManagementReceiptVisibility.SharedCronRunOncePending, other.action)
            assertTrue(other.apiScope.isEmpty() && other.actor.isEmpty() && other.target.isEmpty())
            assertTrue(other.requestSummary.isEmpty() && other.resultSummary.isEmpty())
        }
    }

    @Test fun ownHistoryStaysIntactAndStaleRowsAreRedactedAfterSwitching() {
        val raw=receipt();assertSame(raw,ManagementReceiptVisibility.project(listOf(raw),owner.apiScope,owner.resourceScope).single())
        val stale=ManagementReceiptVisibility.project(listOf(raw),"b".repeat(64),owner.resourceScope)
        assertFalse(stale.single().toString().contains("private-"))
        assertFalse(ManagementReceiptVisibility.project(stale,owner.apiScope,owner.resourceScope).single().toString().contains("private-"))
    }
}
