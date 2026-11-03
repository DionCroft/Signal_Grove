package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HostAuxModelTest {
    private val target=ManagementTarget("a".repeat(64),"https://fixture.example/dashboard/","worker","password · fixture")
    private val home=PluginHome.project(JsonPrimitive("/private-aux-home"))
    private fun obj(s:String)=Json.parseToJsonElement(s).jsonObject
    private val aux=obj("""{"main":{"provider":"fixture","model":"old"},"tasks":[{"task":"voice_chat","provider":"auto","model":"","reasoning_effort":"none"},{"task":"plugin.task","provider":"fixture","model":"old","plugin":"fixture-plugin","inherit_from":"voice_chat","effective":{"provider":"fixture","model":"old"}}]}""")
    private val catalog=obj("""{"provider":"fixture","model":"old","providers":[{"slug":"fixture","name":"Fixture provider","authenticated":true,"models":["old","new","blocked"],"unavailable_models":["blocked"]}]}""")
    private fun inventory(a:JsonObject=aux,c:JsonObject=catalog)=HostModelInventory.parse(a,c,target,home)
    private fun request(task:String="voice_chat",effort:HostAuxEffort=HostAuxEffort.Keep)=HostAuxModelRequest.assign(inventory(),task,HostModelRoute("fixture","new"),effort)
    private fun saved(task:String="voice_chat")=obj("""{"ok":true,"scope":"auxiliary","provider":"fixture","model":"new","tasks":["$task"],"api_key":"private-key","base_url":"private-url"}""")
    private fun warning()=obj("""{"ok":false,"scope":"auxiliary","provider":"fixture","model":"new","confirm_required":true,"confirm_message":"Price and data-training terms"}""")
    @Test fun keepInheritDisabledAndEveryCanonicalLevelHaveDistinctWireMeaning() {
        for(effort in HostAuxEffort.entries) {val body=request(effort=effort).body("worker");assertEquals(JsonPrimitive("auxiliary"),body["scope"]);assertEquals(JsonPrimitive("worker"),body["profile"]);assertEquals(JsonPrimitive("voice_chat"),body["task"]);assertEquals(JsonPrimitive(false),body["confirm_expensive_model"]);assertEquals(setOf("scope","profile","task","provider","model","confirm_expensive_model")+if(effort==HostAuxEffort.Keep) emptySet() else setOf("reasoning_effort"),body.keys);if(effort!=HostAuxEffort.Keep) assertEquals(effort.wire?.let(::JsonPrimitive) ?: JsonNull,body["reasoning_effort"])}
        for(profile in listOf("all","other","hermes",".hermes","")) assertTrue(runCatching {request().body(profile)}.isFailure)
    }
    @Test fun perSlotAutoAndGlobalResetAreDifferentOperations() {
        val auto=HostAuxModelRequest.assign(inventory(),"voice_chat",HostModelRoute("auto",""),HostAuxEffort.Inherit);assertEquals(JsonNull,auto.body("worker")["reasoning_effort"]);assertEquals(JsonPrimitive("voice_chat"),auto.body("worker")["task"])
        val reset=HostAuxModelRequest.reset(inventory());assertEquals(JsonPrimitive("__reset__"),reset.body("worker")["task"]);assertFalse(reset.body("worker").containsKey("reasoning_effort"));assertEquals(listOf("voice_chat","plugin.task"),reset.targets);assertTrue(reset.reply(obj("""{"ok":true,"scope":"auxiliary","reset":true}""")) is HostAuxModelReply.Saved);assertTrue(runCatching {reset.reply(saved())}.isFailure)
    }
    @Test fun modelChoicesRespectReportedRestrictionsWhileExistingRouteCanKeepReasoningOnly() {
        for(route in listOf(HostModelRoute("fixture","blocked"),HostModelRoute("unknown","new"),HostModelRoute("fixture"," new"))) assertTrue(runCatching {HostAuxModelRequest.assign(inventory(),"voice_chat",route)}.isFailure)
        val row=inventory().auxiliary.single {it.task=="plugin.task"};val preserved=HostAuxModelRequest.assign(inventory(),row.task,row.route,HostAuxEffort.High,true);assertEquals(JsonPrimitive("old"),preserved.body("worker")["model"])
        assertTrue(runCatching {HostAuxModelRequest.assign(inventory(),row.task,HostModelRoute("fixture","new"),keepRoute=true)}.isFailure)
        assertTrue(runCatching {HostAuxModelRequest.assign(inventory(),"",row.route,keepRoute=true)}.isFailure)
    }
    @Test fun unknownCaseChangedOrReservedTaskCannotSilentlyTargetAnotherSlot() {
        for(task in listOf("missing","Voice_Chat","__reset__"," voice_chat","voice_chat\n")) assertTrue(runCatching {request(task)}.isFailure)
        assertTrue(runCatching {(request().targets as MutableList).clear()}.isFailure)
    }
    @Test fun preflightDetectsAllVisibleTaskChangesIncludingNewPluginsAndInheritedEffectiveRoutes() {
        val req=request();assertTrue(req.same(inventory()));val tasks=aux["tasks"]!!.jsonArray
        for(changed in listOf(JsonArray(tasks+obj("""{"task":"new.plugin","provider":"auto","model":""}""")),JsonArray(tasks.map {JsonObject(it.jsonObject+mapOf("reasoning_effort" to JsonPrimitive("high")))}),JsonArray(tasks.map {if(it.jsonObject.containsKey("inherit_from")) JsonObject(it.jsonObject+mapOf("effective" to obj("""{"provider":"other","model":"other"}"""))) else it}))) assertFalse(req.same(inventory(JsonObject(aux+mapOf("tasks" to changed)))))
    }
    @Test fun typedKnownWarningRequiresSeparateBoundApprovalAndNeverPersistsText() {
        val req=request();val report=req.reply(warning()) as HostAuxModelReply.Warning;assertEquals(JsonPrimitive(false),report.summary["reported_assigned"]);assertFalse(report.summary.toString().contains("training"));assertEquals(JsonPrimitive(false),req.body("worker")["confirm_expensive_model"]);val approved=req.approve(report.warning);assertEquals(JsonPrimitive(true),approved.body("worker")["confirm_expensive_model"]);assertTrue(runCatching {request().approve(report.warning)}.isFailure);assertTrue(runCatching {approved.reply(warning())}.isFailure)
        for(pair in listOf("ok" to JsonPrimitive("false"),"scope" to JsonPrimitive("main"),"provider" to JsonPrimitive("other"),"confirm_required" to JsonPrimitive("true"),"confirm_message" to JsonPrimitive("x".repeat(12001)))) assertTrue(runCatching {req.reply(JsonObject(warning()+pair))}.isFailure)
    }
    @Test fun acknowledgementsRequireCorrectScopeExactRouteAndUniqueTypedAffectedTasks() {
        val req=request();val saved=req.reply(saved()) as HostAuxModelReply.Saved;assertEquals(listOf("voice_chat"),saved.tasks);assertFalse(saved.summary.toString().contains("private"));assertTrue(runCatching {(saved.tasks as MutableList).clear()}.isFailure)
        for(pair in listOf("ok" to JsonPrimitive("true"),"scope" to JsonPrimitive("main"),"reset" to JsonPrimitive(true),"provider" to JsonPrimitive("other"),"model" to JsonPrimitive("other"),"tasks" to buildJsonArray {add("voice_chat");add("voice_chat")},"tasks" to buildJsonArray {add("plugin.task")},"tasks" to buildJsonArray {add(1)})) assertTrue(runCatching {req.reply(JsonObject(saved()+pair))}.isFailure)
    }
    @Test fun bulkAckCanReportNewlyRegisteredSlotsButResetDoesNotInventAnExactAffectedList() {
        val req=request("");val reply=req.reply(JsonObject(saved()+mapOf("tasks" to buildJsonArray {add("voice_chat");add("plugin.task");add("later.plugin")}))) as HostAuxModelReply.Saved;assertEquals(3,reply.tasks.size);assertEquals(JsonPrimitive(true),reply.summary["bulk"])
        val reset=HostAuxModelRequest.reset(inventory());val ack=reset.reply(obj("""{"ok":true,"scope":"auxiliary","reset":true,"private":"secret"}""")) as HostAuxModelReply.Saved;assertTrue(ack.tasks.isEmpty());assertFalse(ack.summary.containsKey("reported_tasks"));assertFalse(ack.summary.toString().contains("secret"))
    }
    @Test fun explicitReasoningAckMustMatchNullOrCanonicalChoice() {
        for(effort in HostAuxEffort.entries.filter {it!=HostAuxEffort.Keep}) {val req=request(effort=effort);assertTrue(runCatching {req.reply(saved())}.isFailure);assertTrue(req.reply(JsonObject(saved()+mapOf("reasoning_effort" to (effort.wire?.let(::JsonPrimitive) ?: JsonNull)))) is HostAuxModelReply.Saved)}
    }
}
