package org.hermesmobile.app.data

import kotlinx.serialization.json.*

/** Local applied-view cursors only. No transcript, prompt, credentials or execution grant. */
internal object CanonicalViewerCheckpoint {
    private fun text(j:JsonObject,key:String,max:Int=8192):String =
        (j[key] as? JsonPrimitive)?.takeIf {it.isString}?.content
            ?.takeIf {it.isNotBlank() && it.length<=max && it.none(Char::isISOControl) &&
                it.codePoints().noneMatch {c->c in 0xD800..0xDFFF}} ?: throw CanonicalReviewChanged()
    private fun number(j:JsonObject,key:String):Long =
        (j[key] as? JsonPrimitive)?.takeIf {!it.isString}?.longOrNull?.takeIf {it>=0} ?: throw CanonicalReviewChanged()
    private fun summary(row:ManagementOperation):JsonObject {
        if(row.resultSummary.length>65536) throw CanonicalReviewChanged()
        return try {Json.parseToJsonElement(row.resultSummary).jsonObject} catch(_:Exception) {throw CanonicalReviewChanged()}
    }
    private fun request(row:ManagementOperation):JsonObject {
        if(row.requestSummary.length>65536) throw CanonicalReviewChanged()
        return try {Json.parseToJsonElement(row.requestSummary).jsonObject} catch(_:Exception) {throw CanonicalReviewChanged()}
    }
    private fun bindRequest(row:ManagementOperation,owner:CanonicalFeedOwner) {
        val j=request(row)
        if(text(j,"instance_id",512)!=owner.instanceId || number(j,"authority_epoch")!=owner.authorityEpoch ||
            text(j,"profile_home",64)!=owner.homeId) throw CanonicalReviewChanged()
    }
    private fun verify(row:ManagementOperation,target:ManagementTarget,home:String,requested:String) {
        if(row.action!=CanonicalResumeAction.Id || row.state!="confirmed" || row.apiScope!=target.apiScope ||
            row.url!=target.url || row.profile!=target.profile || row.actor!=target.actor ||
            row.resourceScope!=target.resourceScope || row.projectHome!=home || row.target!=requested)
            throw CanonicalReviewChanged()
    }
    fun read(row:ManagementOperation,target:ManagementTarget,home:String,requested:String):CanonicalReplayCheckpoint? {
        verify(row,target,home,requested)
        val result=summary(row);val raw=result["applied_view"] ?: return null
        val j=raw as? JsonObject ?: throw CanonicalReviewChanged()
        if(j.keys!=setOf("version","profile_id","instance_id","session_id","authority_epoch","replay_epoch","sequence") ||
            number(j,"version")!=1L) throw CanonicalReviewChanged()
        val profile=text(j,"profile_id")
        if(PluginHome.project(JsonPrimitive(profile))?.identity()!=home) throw CanonicalReviewChanged()
        val owner=CanonicalFeedOwner(target,home,profile,text(j,"instance_id",512),text(j,"session_id"),number(j,"authority_epoch"))
        if(text(result,"resolved_session_id")!=owner.sessionId || text(result,"requested_session_id")!=requested ||
            number(result,"authority_epoch")!=owner.authorityEpoch) throw CanonicalReviewChanged()
        bindRequest(row,owner)
        return CanonicalReplayCheckpoint(owner,CanonicalReplayCursor(text(j,"replay_epoch",256),number(j,"sequence")))
    }
    fun updated(row:ManagementOperation,owner:CanonicalFeedOwner,cursor:CanonicalReplayCursor):String {
        verify(row,owner.target,owner.homeId,row.target)
        val result=summary(row)
        if(PluginHome.project(JsonPrimitive(owner.profileId))?.identity()!=owner.homeId ||
            text(result,"resolved_session_id")!=owner.sessionId || text(result,"requested_session_id")!=row.target ||
            number(result,"authority_epoch")!=owner.authorityEpoch) throw CanonicalReviewChanged()
        // The reviewed runtime binding is in the immutable request summary.
        bindRequest(row,owner)
        read(row,owner.target,owner.homeId,row.target)?.let {previous->
            if(previous.owner!=owner || previous.cursor.epoch!=cursor.epoch || previous.cursor.sequence>cursor.sequence)
                throw CanonicalReviewChanged()
        }
        val applied=buildJsonObject {
            put("version",1);put("profile_id",owner.profileId);put("instance_id",owner.instanceId)
            put("session_id",owner.sessionId);put("authority_epoch",owner.authorityEpoch)
            put("replay_epoch",cursor.epoch);put("sequence",cursor.sequence)
        }
        text(applied,"replay_epoch",256);text(applied,"instance_id",512);text(applied,"session_id")
        return JsonObject(result+("applied_view" to applied)).toString()
    }
}
