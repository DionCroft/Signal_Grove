package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class GatewayCronControlsTest {
    private fun row(enabled:Boolean=true,id:String="job-fixture",name:String="Job fixture",model:String="model-fixture")=buildJsonObject {put("job_id",id);put("name",name);put("enabled",enabled);put("state",if(enabled) "scheduled" else "paused");put("schedule","Every hour");put("model",model);put("prompt_preview","private-preview-fixture");put("deliver","local")}
    private fun inventory(rows:List<JsonObject>)=GatewayCronInventory.parse(buildJsonObject {put("success",true);put("scoped","worker");put("count",rows.size);put("jobs",JsonArray(rows))},"worker")
    @Test fun allThreeActionsHaveExactProfileSelectorAndNoInventedRequestFields() {
        GatewayCronAction.entries.forEach {action ->
            val job=GatewayCronJob.parse(row(action!=GatewayCronAction.Resume));val request=GatewayCronRequest(job,action)
            assertEquals(buildJsonObject {put("action",action.wire);put("name","job-fixture");put("profile","worker")},request.params("worker"))
            assertFalse(request.summary().contains("private-preview-fixture"));assertFalse(request.summary().contains("model-fixture"))
            listOf("all","current","hermes",".hermes").forEach {assertTrue(runCatching {request.params(it)}.isFailure)}
        }
        assertTrue(runCatching {GatewayCronRequest(GatewayCronJob.parse(row(false)),GatewayCronAction.Pause)}.isFailure)
        assertTrue(runCatching {GatewayCronRequest(GatewayCronJob.parse(row()),GatewayCronAction.Resume)}.isFailure)
    }
    @Test fun acknowledgementsRejectWrongIdentityUntypedFlagsAndFalseSuccess() {
        val expected=GatewayCronJob.parse(row());val pause=GatewayCronRequest(expected,GatewayCronAction.Pause)
        val accepted=pause.acknowledge(buildJsonObject {put("success",true);put("job",row(false))})
        assertFalse(accepted.contains("private-preview-fixture"));assertTrue(pause.observed(inventory(listOf(row(false)))))
        listOf(buildJsonObject {put("success",false);put("job",row(false))},buildJsonObject {put("success",true);put("job",row(false,"other"))},buildJsonObject {put("success",true);put("job",JsonObject(row(false)+("enabled" to JsonPrimitive("false"))))}).forEach {assertTrue(runCatching {pause.acknowledge(it)}.isFailure)}
        val remove=GatewayCronRequest(expected,GatewayCronAction.Delete)
        assertTrue(runCatching {remove.acknowledge(buildJsonObject {put("success",true);put("removed_job",buildJsonObject {put("id","job-fixture");put("name","Other")})})}.isFailure)
        remove.acknowledge(buildJsonObject {put("success",true);put("removed_job",buildJsonObject {put("id","job-fixture");put("name","Job fixture")})})
        assertTrue(remove.observed(inventory(emptyList())))
    }
    @Test fun configurationChangesAndKnownNameCollisionsAreDetectedWithoutFreezingRunErrors() {
        val before=GatewayCronJob.parse(row())
        assertTrue(before.sameReview(GatewayCronJob.parse(JsonObject(row()+("last_error" to JsonPrimitive("new-private-error"))))))
        assertFalse(before.sameReview(GatewayCronJob.parse(row(model="changed"))))
        val collision=GatewayCronJob.parse(row(id="other-job",name="JOB-FIXTURE"))
        assertTrue(GatewayCronRequest.collision(before,listOf(before,collision)))
        assertFalse(GatewayCronRequest.collision(before,listOf(before)))
        assertTrue(runCatching {GatewayCronRequest(GatewayCronJob.parse(row(id="unknown")),GatewayCronAction.Delete)}.isFailure)
    }
    @Test fun reviewedUrlsHideQueriesAndAlwaysRemoveEmbeddedCredentials() {
        val job=GatewayCronJob.parse(JsonObject(row()+mapOf("base_url" to JsonPrimitive("https://user:private-password@provider.example/v1/?key=private-query"),"monitor_url" to JsonPrimitive("https://monitor.example/?key=monitor-private"))))
        assertEquals("https://provider.example/v1/",job.reviewUrl("base_url"));assertTrue(job.reviewUrl("base_url",true)!!.contains("private-query"))
        assertFalse(job.reviewUrl("base_url",true)!!.contains("private-password"));assertFalse(job.reviewUrl("monitor_url")!!.contains("monitor-private"));assertFalse(job.toString().contains("private"))
    }
    @Test fun missingAndEmptyToolAllowlistRemainDistinctAndMalformedFieldsAreRefused() {
        assertFalse(GatewayCronJob.parse(row()).reportsToolsets)
        val empty=GatewayCronJob.parse(JsonObject(row()+mapOf("enabled_toolsets" to JsonArray(emptyList()),"no_agent" to JsonPrimitive(false))))
        assertTrue(empty.reportsToolsets);assertEquals("",empty.fields()["enabled_toolsets"]);assertEquals("false",empty.fields()["no_agent"])
        listOf("skills" to JsonPrimitive("all"),"no_agent" to JsonPrimitive("false"),"enabled_toolsets" to buildJsonArray {add(1)},"monitor_script" to JsonPrimitive("x".repeat(8193))).forEach {(key,value)->assertTrue(runCatching {GatewayCronJob.parse(JsonObject(row()+mapOf(key to value)))}.isFailure)}
    }
}
