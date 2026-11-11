package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.util.Collections

class HostProjectConversationsUnsupported:Exception("Unsupported project conversation metadata.")

data class HostProjectConversation(val id:String,val title:String,val source:String?,val model:String?,val messageCount:Long,val lastActive:Double,val repository:String?,val lane:String,val directory:String?) {
    override fun toString()="HostProjectConversation(redacted)"
}
data class HostProjectConversations(val owner:ManagementTarget,val homeId:String,val project:HostProjectInfo,val listed:Boolean,val reportedCount:Int,val rows:List<HostProjectConversation>,val observedAt:Long) {
    override fun toString()="HostProjectConversations(redacted)"
}
/** Navigation metadata only. Never retain preview/transcript, infer a worker or change the session cwd. */
object HostProjectConversationProtocol {
    const val SessionLimit=500
    private fun text(j:JsonObject,key:String,max:Int=512,optional:Boolean=false,multiline:Boolean=false):String? {
        val value=j[key];if(value==null || value==JsonNull) {require(optional);return null}
        require(value is JsonPrimitive && value.isString)
        return value.content.also {s->require(s.length<=max && s.none {it.isISOControl() && !(multiline && it in "\n\r\t")} && s.codePoints().noneMatch {it in 0xD800..0xDFFF})}
    }
    private fun id(j:JsonObject,key:String,max:Int=512)=checkNotNull(text(j,key,max)).also {require(it.isNotBlank())}
    private fun count(j:JsonObject,key:String):Int {
        val value=j[key] as JsonPrimitive;require(!value.isString)
        return checkNotNull(value.intOrNull).also {require(it in 0..SessionLimit)}
    }
    private fun flag(j:JsonObject,key:String):Boolean {val v=j[key] as JsonPrimitive;require(!v.isString);return checkNotNull(v.booleanOrNull)}
    private fun array(j:JsonObject,key:String,max:Int):JsonArray=(j[key] as JsonArray).also {require(it.size<=max)}
    fun parse(j:JsonObject,owner:ManagementTarget,home:String,project:HostProjectInfo,at:Long=System.currentTimeMillis()):HostProjectConversations=try {
        GatewayCronInventory.requireProfile(owner.profile);require(home.matches(Regex("[a-f0-9]{64}")) && HostProjectProtocol.validId(project.id))
        require(j.containsKey("project"));val value=j["project"]
        if(value==JsonNull) HostProjectConversations(owner,home,project,false,0,emptyList(),at)
        else {
            val node=value as JsonObject;require(id(node,"id")==project.id && text(node,"label",512,multiline=true)==project.name)
            require(!flag(node,"isAuto") && !flag(node,"isNoProject") && text(node,"path",4096,true)==project.primaryPath)
            val declared=count(node,"sessionCount")
            val ids=array(node,"sessionIds",SessionLimit).map {element->val v=element as JsonPrimitive;require(v.isString && HostProjectProtocol.validId(v.content));v.content}
            require(ids.size==declared && ids.distinct().size==ids.size)
            val rows=mutableListOf<HostProjectConversation>();val repos=mutableSetOf<String>();val lanes=mutableSetOf<String>()
            for(rawRepo in array(node,"repos",128)) {
                val repo=rawRepo as JsonObject;require(repos.add(id(repo,"id",8192)));val path=text(repo,"path",4096,true)
                val before=rows.size
                for(rawLane in array(repo,"groups",SessionLimit)) {
                    val lane=rawLane as JsonObject;require(lanes.add(id(lane,"id",8192)));val label=id(lane,"label",1024)
                    for(raw in array(lane,"sessions",SessionLimit)) {
                        require(rows.size<SessionLimit);val row=raw as JsonObject;val sid=id(row,"id")
                        require(sid in ids && text(row,"profile")==owner.profile)
                        val messages=row["message_count"] as JsonPrimitive;require(!messages.isString);val n=checkNotNull(messages.longOrNull);require(n>=0)
                        val last=row["last_active"] as JsonPrimitive;require(!last.isString);val time=checkNotNull(last.doubleOrNull);require(time.isFinite() && time>=0)
                        require(!flag(row,"archived"))
                        rows+=HostProjectConversation(sid,text(row,"title",512,true,true).orEmpty(),text(row,"source",128,true),text(row,"model",512,true),n,time,path,label,text(row,"cwd",4096,true))
                    }
                }
                require(rows.size-before==count(repo,"sessionCount"))
            }
            require(rows.map {it.id}.distinct().size==rows.size && rows.size<=declared)
            HostProjectConversations(owner,home,project,true,declared,Collections.unmodifiableList(rows),at)
        }
    } catch(_:Exception) {throw HostProjectConversationsUnsupported()}
}
