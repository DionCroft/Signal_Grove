package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class RetiredAuxiliarySlotTest {
    private val owner=ManagementTarget("a".repeat(64),"https://fixture.example/prefix/","worker","password · fixture")
    private fun inventory(retired:Boolean):HostModelInventory {
        val main=buildJsonObject {put("provider","fixture");put("model","main")}
        val tasks=listOf("vision","plugin.task")+if(retired) listOf("skills_hub") else emptyList()
        val aux=buildJsonObject {put("main",main);put("tasks",buildJsonArray {tasks.forEach {task->add(buildJsonObject {put("task",task);put("provider","auto");put("model","")})}})}
        val catalog=buildJsonObject {main.forEach {(key,value)->put(key,value)};put("providers",JsonArray(emptyList()))}
        return HostModelInventory.parse(aux,catalog,owner,PluginHome.project(JsonPrimitive("/srv/profiles/worker")))
    }
    @Test fun hostRetirementInvalidatesFrozenSingleBulkAndResetReviews() {
        val before=inventory(true);val after=inventory(false);val auto=HostModelRoute("auto","")
        val single=HostAuxModelRequest.assign(before,"skills_hub",auto)
        for(request in listOf(single,HostAuxModelRequest.assign(before,"",auto),HostAuxModelRequest.reset(before))) assertFalse(request.same(after))
        assertThrows(IllegalArgumentException::class.java) {single.rebase(after)}
        assertThrows(IllegalArgumentException::class.java) {HostAuxModelRequest.assign(after,"skills_hub",auto)}
    }
    @Test fun remainingAdvertisedAndPluginRoutesStillSupportReviewedBulkAcknowledgementAndReadback() {
        val after=inventory(false);assertFalse(after.auxiliary.any {it.task=="skills_hub"});assertTrue(after.auxiliary.any {it.task=="plugin.task"})
        val request=HostAuxModelRequest.assign(after,"",HostModelRoute("auto",""))
        val ack=buildJsonObject {put("ok",true);put("scope","auxiliary");put("provider","auto");put("model","");put("tasks",buildJsonArray {add("vision");add("plugin.task")})}
        val reply=request.reply(ack) as HostAuxModelReply.Saved
        assertEquals(listOf("vision","plugin.task"),reply.tasks);assertTrue(request.observed(after,reply))
        assertEquals(JsonPrimitive("worker"),request.body("worker")["profile"])
    }
}
