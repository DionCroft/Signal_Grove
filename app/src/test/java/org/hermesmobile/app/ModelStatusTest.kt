package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class ModelStatusTest {
    private fun payload(status:String="ready",state:String="ready",verified:JsonElement=JsonPrimitive(true))=buildJsonObject {
        put("object","signalgrove.model_runtime");put("schema_version",1);put("observed_at",123000);put("status",status)
        put("models",buildJsonArray {add(buildJsonObject {put("provider","spark-fixture");put("model","loaded-model");put("state",state);put("verified",verified)})})
    }
    private fun refuses(value:JsonObject) {assertTrue(runCatching {ModelRuntimeStatus.parse(value)}.isFailure)}
    @Test fun servingIdentityRequiresTheVersionedObserverRatherThanCatalogOrAlias() {
        val result=ModelRuntimeStatus.parse(payload());assertEquals("loaded-model",result.verifiedModels.single().model)
        refuses(buildJsonObject {put("model","configured-only");put("providers",buildJsonArray {})})
        refuses(buildJsonObject {put("model","hermes-agent")})
    }
    @Test fun inconsistentIdentityAndReadinessNeverBecomeVerified() {
        refuses(payload(verified=JsonPrimitive(false)));refuses(payload(state="unknown"));refuses(payload(verified=JsonPrimitive("true")))
        refuses(JsonObject(payload()+mapOf("schema_version" to JsonPrimitive("1"))))
        refuses(JsonObject(payload()+mapOf("observed_at" to JsonPrimitive(-1))))
    }
    @Test fun ambiguousAndUnverifiedRoutesAreKeptDistinctFromASingleReadyModel() {
        val first=payload()["models"]!!.jsonArray.first().jsonObject
        val second=JsonObject(first+mapOf("provider" to JsonPrimitive("spark-other"),"model" to JsonPrimitive("other-model")))
        val many=JsonObject(payload()+mapOf("status" to JsonPrimitive("ambiguous"),"models" to JsonArray(listOf(first,second))))
        assertEquals(2,ModelRuntimeStatus.parse(many).verifiedModels.size)
        refuses(JsonObject(many+mapOf("status" to JsonPrimitive("ready"))))
        val uncertain=JsonObject(second+mapOf("state" to JsonPrimitive("unsafe"),"verified" to JsonPrimitive(false)))
        assertEquals("unverified",ModelRuntimeStatus.parse(JsonObject(many+mapOf("status" to JsonPrimitive("unverified"),"models" to JsonArray(listOf(first,uncertain))))).status)
    }
    @Test fun unsupportedMalformedAndUnboundedObservationsDoNotInventModels() {
        refuses(JsonObject(payload()+mapOf("models" to JsonArray(List(9) {payload()["models"]!!.jsonArray.first()}))))
        val invalid=JsonObject(payload()["models"]!!.jsonArray.first().jsonObject+mapOf("model" to JsonPrimitive("bad\nmodel")))
        refuses(JsonObject(payload()+mapOf("models" to JsonArray(listOf(invalid)))))
        assertTrue(ModelRuntimeStatus.parse(JsonObject(payload()+mapOf("status" to JsonPrimitive("not_configured"),"models" to JsonArray(emptyList())))).verifiedModels.isEmpty())
    }
    @Test fun observerRequiresTheExactAdvertisedReadOnlyEndpointAndFeature() {
        val endpoint=buildJsonObject {put("method","GET");put("path","/api/model/runtime-status")}
        val features=buildJsonObject {put("model_runtime_status_v1",true)}
        assertTrue(HermesCapabilities(features=features,endpoints=buildJsonObject {put("model_runtime_status",endpoint)}).supportsDiscovery(DiscoveryResource.RuntimeModels))
        assertFalse(HermesCapabilities(endpoints=buildJsonObject {put("model_runtime_status",endpoint)}).supportsDiscovery(DiscoveryResource.RuntimeModels))
        for(flag in listOf<JsonElement>(JsonPrimitive("true"),buildJsonObject {})) assertFalse(HermesCapabilities(features=buildJsonObject {put("model_runtime_status_v1",flag)},endpoints=buildJsonObject {put("model_runtime_status",endpoint)}).supportsDiscovery(DiscoveryResource.RuntimeModels))
        for((field,value) in listOf("path" to "/arbitrary","method" to "POST")) assertFalse(HermesCapabilities(features=features,endpoints=buildJsonObject {put("model_runtime_status",JsonObject(endpoint+mapOf(field to JsonPrimitive(value))))}).supportsDiscovery(DiscoveryResource.RuntimeModels))
    }
    @Test fun freshnessCannotHideFailuresOldDataOrClockRollback() {
        assertFalse(modelStatusDue(1000,60999));assertTrue(modelStatusDue(1000,61000));assertTrue(modelStatusDue(1000,999));assertTrue(modelStatusDue(null,1))
        assertFalse(modelObservationCached(1000,2000,2000,false));assertTrue(modelObservationCached(1000,121000,121000,false))
        assertTrue(modelObservationCached(null,2000,2000,false));assertTrue(modelObservationCached(32001,2000,2000,false));assertFalse(modelObservationCached(32000,2000,2000,false))
        assertTrue(modelObservationCached(2000,2000,2000,true))
        assertFalse(modelStatusCached(1000,2000,false));assertTrue(modelStatusCached(1000,2000,true));assertTrue(modelStatusCached(1000,121000,false));assertTrue(modelStatusCached(1000,999,false))
    }
    @Test fun requestAndLastReportedModelStaySeparateAndConversationScoped() {
        val c=Conversation("local","scope","remote")
        val message=ChatMessage("m","local","assistant","Reply",123,"completed",telemetryJson="""{"runtime":{"model":"actual-model","provider":"actual-provider","model_lock":"accepted"}}""")
        val requested=RuntimeChoice("next-model","next-provider")
        val status=conversationModelStatus(requested,c,listOf(message),"scope")
        assertEquals("next-model",status.requested.model);assertEquals("actual-model",status.reported?.model);assertEquals(123L,status.reportedAt);assertTrue(status.lockReported)
        assertNull(conversationModelStatus(requested,c,listOf(message),"another-scope").reported)
        assertNull(conversationModelStatus(requested,c,listOf(message.copy(sessionKey="another-chat")),"scope").reported)
        assertNull(conversationModelStatus(requested,null,listOf(message),"scope").reported)
    }
    @Test fun defaultSelectionDoesNotClaimTheLastModelWillBeUsedAgain() {
        val c=Conversation("local","scope","remote")
        val message=ChatMessage("m","local","assistant","Reply",123,"completed",telemetryJson="""{"runtime":{"model":"last-model"}}""")
        val status=conversationModelStatus(RuntimeChoice(),c,listOf(message),"scope")
        assertTrue(status.requested.isDefault);assertEquals("last-model",status.reported?.model);assertFalse(status.lockReported)
    }
}
