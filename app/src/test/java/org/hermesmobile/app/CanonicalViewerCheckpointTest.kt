package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class CanonicalViewerCheckpointTest {
    private val f=CanonicalViewerFixtures
    private fun pointed()=f.receipt().let {it.copy(resultSummary=CanonicalViewerCheckpoint.updated(it,f.owner,f.cursor))}
    private fun read(row:ManagementOperation)=CanonicalViewerCheckpoint.read(row,f.target,f.home,"history")
    private fun result(row:ManagementOperation,key:String,value:JsonElement)=row.copy(resultSummary=JsonObject(Json.parseToJsonElement(row.resultSummary).jsonObject+(key to value)).toString())
    private fun request(row:ManagementOperation,key:String,value:JsonElement)=row.copy(requestSummary=JsonObject(Json.parseToJsonElement(row.requestSummary).jsonObject+(key to value)).toString())
    private fun applied(row:ManagementOperation,change:(JsonObject)->JsonObject):ManagementOperation {
        val j=Json.parseToJsonElement(row.resultSummary).jsonObject
        return result(row,"applied_view",change(j.getValue("applied_view").jsonObject))
    }
    private fun refuses(block:()->Unit) {assertTrue(runCatching(block).exceptionOrNull() is CanonicalReviewChanged)}

    @Test fun firstConfirmedOpeningHasNoAppliedCursorUntilTheViewerCommitsItsProjection() {
        val row=f.receipt();assertNull(read(row))
        assertFalse(row.resultSummary.contains("applied_view"))
        assertFalse(row.resultSummary.contains("Private fixture reply"))
    }
    @Test fun appliedCursorRoundTripsAllOwnerCoordinatesWithoutMessagesOrCredentials() {
        val row=pointed();assertEquals(CanonicalReplayCheckpoint(f.owner,f.cursor),read(row))
        val j=Json.parseToJsonElement(row.resultSummary).jsonObject
        assertEquals(Json.parseToJsonElement(f.receipt().resultSummary),JsonObject(j-"applied_view"))
        for(value in listOf("Private fixture reply","Private fixture title",f.tokens.access,f.tokens.refresh)) assertFalse(row.resultSummary.contains(value))
    }
    @Test fun anotherApiScopeUrlProfileOrActorCannotReadThisCheckpoint() {
        for(owner in listOf(f.target.copy(apiScope="a".repeat(64)),f.target.copy(url="https://other.test/"),f.target.copy(profile="worker"),f.target.copy(actor="fixture · another"))) {
            refuses {CanonicalViewerCheckpoint.read(pointed(),owner,f.home,"history")}
        }
        refuses {CanonicalViewerCheckpoint.read(pointed(),f.target,"a".repeat(64),"history")}
        refuses {CanonicalViewerCheckpoint.read(pointed(),f.target,f.home,"another-history")}
    }
    @Test fun unconfirmedForeignActionAndAlteredReceiptCoordinatesAreRejected() {
        val row=pointed()
        for(changed in listOf(row.copy(state="acknowledged"),row.copy(state="unknown"),row.copy(action="McpToggle"),
            row.copy(resourceScope="a".repeat(64)),row.copy(projectHome="a".repeat(64)),row.copy(target="other"),
            row.copy(apiScope="a".repeat(64)),row.copy(url="https://other.test/"),row.copy(profile="worker"),row.copy(actor="other"))) {
            refuses {read(changed)}
        }
    }
    @Test fun immutableReviewedRuntimeAndHomeMustStillBindTheAppliedOwner() {
        for((key,value) in listOf("instance_id" to JsonPrimitive("other"),"authority_epoch" to JsonPrimitive(8),"profile_home" to JsonPrimitive("a".repeat(64)))) {
            val row=request(pointed(),key,value);refuses {read(row)}
            refuses {CanonicalViewerCheckpoint.updated(row,f.owner,f.cursor)}
        }
    }
    @Test fun requestedResolvedAndAuthorityReceiptStampsCannotBeRebound() {
        for((key,value) in listOf("requested_session_id" to JsonPrimitive("other"),"resolved_session_id" to JsonPrimitive("other"),"authority_epoch" to JsonPrimitive(8))) {
            val row=result(pointed(),key,value);refuses {read(row)}
            refuses {CanonicalViewerCheckpoint.updated(row,f.owner,f.cursor)}
        }
    }
    @Test fun missingUnknownAndUnsupportedAppliedFieldsRequireExplicitFreshReview() {
        val row=pointed();val keys=Json.parseToJsonElement(row.resultSummary).jsonObject.getValue("applied_view").jsonObject.keys
        for(key in keys) refuses {read(applied(row) {JsonObject(it-key)})}
        refuses {read(applied(row) {JsonObject(it+("future_field" to JsonPrimitive(true)))})}
        refuses {read(applied(row) {JsonObject(it+("version" to JsonPrimitive(2)))})}
    }
    @Test fun appliedNumbersMustBeIntegralNonnegativeAndUnquoted() {
        for(key in listOf("version","authority_epoch","sequence")) for(value in listOf(JsonPrimitive(-1),JsonPrimitive(1.5),JsonPrimitive("1"),JsonPrimitive(true),JsonNull)) {
            refuses {read(applied(pointed()) {JsonObject(it+(key to value))})}
        }
    }
    @Test fun invalidOrForeignAppliedIdentityAndEpochAreRefused() {
        for((key,value) in listOf("profile_id" to "/other/home","instance_id" to "other","session_id" to "other",
            "replay_epoch" to "", "instance_id" to (55296.toChar().toString()),"replay_epoch" to "x".repeat(257))) {
            refuses {read(applied(pointed()) {JsonObject(it+(key to JsonPrimitive(value)))})}
        }
    }
    @Test fun repeatedOrAdvancingSameEpochCursorIsAllowedWithoutRollback() {
        val row=pointed()
        assertEquals(Json.parseToJsonElement(row.resultSummary),Json.parseToJsonElement(CanonicalViewerCheckpoint.updated(row,f.owner,f.cursor)))
        val next=row.copy(resultSummary=CanonicalViewerCheckpoint.updated(row,f.owner,CanonicalReplayCursor("ring",9)))
        assertEquals(9L,read(next)!!.cursor.sequence)
        refuses {CanonicalViewerCheckpoint.updated(next,f.owner,CanonicalReplayCursor("ring",8))}
        refuses {CanonicalViewerCheckpoint.updated(next,f.owner,CanonicalReplayCursor("replacement-ring",10))}
        refuses {CanonicalViewerCheckpoint.updated(next,f.owner.copy(instanceId="other"),CanonicalReplayCursor("ring",10))}
    }
    @Test fun malformedAndOversizedStoredJsonCannotSilentlyStartFresh() {
        val row=pointed()
        for(value in listOf("[","[]","x".repeat(65537))) {
            refuses {read(row.copy(resultSummary=value))}
            refuses {read(row.copy(requestSummary=value))}
        }
        refuses {read(result(row,"applied_view",JsonNull))}
    }
    @Test fun checkpointAndOwnerStringsNeverIncludePrivateCoordinates() {
        val point=read(pointed())!!
        for(value in listOf(f.target.url,f.owner.profileId,f.owner.sessionId,f.owner.instanceId,f.cursor.epoch)) {
            assertFalse(point.toString().contains(value));assertFalse(point.owner.toString().contains(value));assertFalse(point.cursor.toString().contains(value))
        }
    }
}
