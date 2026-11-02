package org.hermesmobile.app.data

import kotlinx.serialization.json.*

data class ManagedSkill(val name:String,val description:String,val enabled:Boolean?,val locked:Boolean?,val provenance:String)
enum class SkillAction { Toggle, Lock }
data class SkillRequest(val expected:ManagedSkill,val action:SkillAction) {
    val value:Boolean get()=!(if(action==SkillAction.Toggle) expected.enabled else expected.locked)!!
    init {require(SkillProtocol.name(expected.name));require((if(action==SkillAction.Toggle) expected.enabled else expected.locked)!=null);require(action!=SkillAction.Toggle || !expected.enabled!! || expected.name !in SkillProtocol.essential)}
    val key get()=if(action==SkillAction.Toggle) "enabled" else "locked"
    val receiptAction get()=if(action==SkillAction.Toggle) "SkillToggle" else "SkillLock"
    fun body(profile:String)=buildJsonObject {put("name",expected.name);put(key,value);put("profile",profile)}
    fun summary()=buildJsonObject {put(key,value)}.toString()
    fun acknowledge(j:JsonObject):String {
        require(j["ok"]==JsonPrimitive(true) && j.text("name")==expected.name && j[key]==JsonPrimitive(value))
        return summary()
    }
    fun observed(rows:List<ManagedSkill>)=rows.singleOrNull {it.name==expected.name}?.let {if(action==SkillAction.Toggle) it.enabled==value else it.locked==value}==true
}

/** Plain public projections. No host paths, arbitrary errors or skill content in the action ledger. */
object SkillProtocol {
    val essential:Set<String> = java.util.Collections.unmodifiableSet(setOf("hermes-agent"))
    fun profile(value:String) {require(value.isNotBlank() && value.length<=128 && value==value.trim() && value.lowercase() !in setOf("all","current") && value.none(Char::isISOControl))}
    fun name(value:String)=value.isNotBlank() && value.length<=256 && value==value.trim() && value.none(Char::isISOControl)
    private fun field(j:JsonObject,key:String,max:Int,required:Boolean=false):String {
        val item=j[key];require(item==null || item==JsonNull || item is JsonPrimitive && item.isString)
        val value=(item as? JsonPrimitive)?.contentOrNull.orEmpty();require(value.length<=max && (!required || name(value)))
        return value
    }
    private fun flag(j:JsonObject,key:String):Boolean? {
        val v=j[key];require(v==null || v==JsonNull || v==JsonPrimitive(true) || v==JsonPrimitive(false));return (v as? JsonPrimitive)?.booleanOrNull
    }
    fun inventory(array:JsonArray):List<ManagedSkill> {
        require(array.size<=1000)
        return array.map {element->val j=element.jsonObject;ManagedSkill(field(j,"name",256,true),field(j,"description",8192),flag(j,"enabled"),flag(j,"locked"),field(j,"provenance",128))}
            .also {require(it.map(ManagedSkill::name).distinct().size==it.size)}
    }
    fun content(j:JsonObject,name:String):String {require(j.text("name")==name && j["content"] is JsonPrimitive && j["content"]!!.jsonPrimitive.isString);return j.text("content").also {require(it.length<=131072)}}
}
