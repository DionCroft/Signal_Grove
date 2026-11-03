package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HostMainModelTest {
    private val target=ManagementTarget("a".repeat(64),"https://fixture.example/dashboard/","worker","password · fixture")
    private val home=PluginHome.project(JsonPrimitive("/private-main-home"))
    private fun obj(s:String)=Json.parseToJsonElement(s).jsonObject
    private val aux=obj("""{"main":{"provider":"fixture","model":"old-model"},"tasks":[]}""")
    private val catalog=obj("""{"provider":"fixture","model":"old-model","providers":[{"slug":"fixture","name":"Fixture provider","source":"user-config","authenticated":true,"is_current":true,"models":["old-model","new-model","blocked-model"],"unavailable_models":["blocked-model"]}]}""")
    private fun inventory(a:JsonObject=aux,c:JsonObject=catalog)=HostModelInventory.parse(a,c,target,home)
    private fun request()=HostMainModelRequest(inventory(),"fixture","new-model")
    private fun warning()=obj("""{"ok":false,"scope":"main","provider":"fixture","model":"new-model","confirm_required":true,"confirm_message":"Price and data-training terms require approval.\nFixture warning."}""")
    private fun saved()=obj("""{"ok":true,"scope":"main","provider":"normalized-provider","model":"normalized-model","base_url":"private-url","api_key":"private-key","gateway_tools":["private-tool"],"stale_aux":[{"task":"private-task","api_key":"private-key"}],"notice":"private-notice"}""")
    @Test fun exactBodyOmitsSecretsEndpointsReasoningTasksAndUnknownConcurrencyFields() {
        val request=request();val body=request.body("worker");assertEquals(setOf("scope","profile","provider","model","confirm_expensive_model"),body.keys);assertEquals(JsonPrimitive("main"),body["scope"]);assertEquals(JsonPrimitive(false),body["confirm_expensive_model"]);assertEquals(JsonPrimitive("worker"),body["profile"])
        for(profile in listOf("all","hermes",".hermes","other","")) assertTrue(runCatching {request.body(profile)}.isFailure)
        assertFalse(request.summary().toString().contains("api_key"));assertFalse(request.toString().contains("new-model"))
    }
    @Test fun knownWarningIsExactTypedAndNeverImplicitlyApprovesItsOwnOverride() {
        val request=request();val reply=request.reply(warning()) as HostMainModelReply.Warning;assertSame(request,reply.warning.request);assertEquals(JsonPrimitive(false),reply.summary["reported_assigned"]);assertEquals(JsonPrimitive(true),reply.summary["selection_warning_required"]);assertFalse(reply.summary.toString().contains("training terms"));assertFalse(reply.warning.toString().contains("training"));assertEquals(JsonPrimitive(false),request.body("worker")["confirm_expensive_model"])
        val approved=request.approve(reply.warning);assertEquals(JsonPrimitive(true),approved.body("worker")["confirm_expensive_model"]);assertTrue(runCatching {request().approve(reply.warning)}.isFailure);assertTrue(runCatching {approved.approve(reply.warning)}.isFailure);assertTrue(runCatching {approved.reply(warning())}.isFailure)
    }
    @Test fun malformedOrMismatchedNegativesCannotBeClassifiedAsKnownRefusal() {
        val request=request();val variants=listOf(JsonObject(warning()+mapOf("ok" to JsonPrimitive("false"))),JsonObject(warning()+mapOf("provider" to JsonPrimitive("other"))),JsonObject(warning()+mapOf("model" to JsonPrimitive("other"))),JsonObject(warning()+mapOf("scope" to JsonPrimitive("auxiliary"))),JsonObject(warning()+mapOf("confirm_required" to JsonPrimitive("true"))),JsonObject(warning()-"confirm_message"),JsonObject(warning()+mapOf("confirm_message" to JsonPrimitive("x".repeat(12001)))))
        for(raw in variants) assertTrue(runCatching {request.reply(raw)}.isFailure)
    }
    @Test fun normalizedAcknowledgementRetainsMandatorySuccessWithoutPrivateOptionalDetails() {
        val reply=request().reply(saved()) as HostMainModelReply.Saved;assertEquals(HostModelRoute("normalized-provider","normalized-model"),reply.route);assertEquals(JsonPrimitive(true),reply.summary["reported_assigned"]);assertEquals(JsonPrimitive(1),reply.summary["gateway_tools_count"]);assertEquals(JsonPrimitive(1),reply.summary["stale_aux_count"]);assertEquals("private-notice",reply.notice);assertFalse(reply.summary.toString().contains("private"));assertEquals(JsonPrimitive(true),reply.summary["notice_reported"])
        val bad=JsonObject(saved()+mapOf("gateway_tools" to JsonPrimitive("private-bad-tools"),"stale_aux" to JsonPrimitive("private-bad-aux"),"notice" to JsonArray(emptyList())))
        val retained=request().reply(bad) as HostMainModelReply.Saved;assertFalse(retained.summary.containsKey("gateway_tools_count"));assertNull(retained.notice);assertEquals(reply.route,retained.route)
    }
    @Test fun savedReplyRequiresTypedMainStatusAndBoundedReportedPair() {
        val badStatus=JsonObject(saved()+mapOf("ok" to JsonPrimitive("true")))
        val badScope=JsonObject(saved()+mapOf("scope" to JsonPrimitive("auxiliary")))
        val emptyModel=JsonObject(saved()+mapOf("model" to JsonPrimitive("")))
        val largeModel=JsonObject(saved()+mapOf("model" to JsonPrimitive("x".repeat(1025))))
        for(raw in listOf(badStatus,badScope,JsonObject(saved()-"provider"),emptyModel,largeModel)) assertTrue(runCatching {request().reply(raw)}.isFailure)
    }
    @Test fun freshPublicSelectionAndMainMetadataMustStillMatchBeforeSend() {
        val request=request();assertTrue(request.same(inventory()));val provider=catalog["providers"]!!.jsonArray.single().jsonObject
        val changed=JsonObject(catalog+mapOf("providers" to buildJsonArray {add(JsonObject(provider+mapOf("name" to JsonPrimitive("Changed"))))}));assertFalse(request.same(inventory(c=changed)))
        val restricted=JsonObject(catalog+mapOf("providers" to buildJsonArray {add(JsonObject(provider+mapOf("unavailable_models" to buildJsonArray {add("new-model")})))}));assertFalse(request.same(inventory(c=restricted)))
        val otherMain=HostModelInventory(target,home!!.identity(),HostModelRoute("fixture","other"),emptyList(),inventory().providers,false);assertFalse(request.same(otherMain))
        val privateOnly=JsonObject(catalog+mapOf("providers" to buildJsonArray {add(JsonObject(provider+mapOf("api_url" to JsonPrimitive("changed-private-url"),"api_key" to JsonPrimitive("changed-private-key"))))}));assertTrue(request.same(inventory(c=privateOnly)))
    }
    @Test fun accountRestrictionsUnknownAuthenticationAndUntrimmedIdsCannotBecomeAssignments() {
        val inventory=inventory();assertFalse(HostMainModelRequest.eligible(inventory.providers.single(),"blocked-model"));assertTrue(runCatching {HostMainModelRequest(inventory,"fixture","blocked-model")}.isFailure)
        for(field in listOf("authenticated","free_tier_pending")) {val row=catalog["providers"]!!.jsonArray.single().jsonObject;val changed=JsonObject(catalog+mapOf("providers" to buildJsonArray {add(JsonObject(row+mapOf(field to if(field=="authenticated") JsonNull else JsonPrimitive(true))))}));assertTrue(runCatching {HostMainModelRequest(inventory(c=changed),"fixture","new-model")}.isFailure)}
        assertFalse(HostMainModelRequest.eligible(inventory.providers.single()," new-model"));assertTrue(runCatching {HostMainModelRequest(inventory,"missing","new-model")}.isFailure)
    }
    @Test fun reportedRestrictionsAreBoundedTypedAndImmutable() {
        val state=inventory();assertEquals(setOf("blocked-model"),state.providers.single().unavailableModels);assertTrue(runCatching {(state.providers.single().unavailableModels as MutableSet).clear()}.isFailure)
        val row=catalog["providers"]!!.jsonArray.single().jsonObject
        for(value in listOf(JsonPrimitive("blocked-model"),buildJsonArray {add(1)},buildJsonArray {add("blocked-model");add("blocked-model")})) {val changed=JsonObject(catalog+mapOf("providers" to buildJsonArray {add(JsonObject(row+mapOf("unavailable_models" to value)))}));assertTrue(runCatching {inventory(c=changed)}.isFailure)}
    }
}
