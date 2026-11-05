package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.util.Collections

class HostProjectsUnsupported:Exception("Unsupported project metadata.")
class HostProjectsScopeChanged:Exception("Project profile changed.")

data class HostProjectFolder(val path:String,val label:String?,val primary:Boolean,val addedAtSeconds:Long?) {
    override fun toString()="HostProjectFolder(redacted)"
}
data class HostProjectInfo(val id:String,val slug:String,val name:String,val description:String?,val icon:String?,val color:String?,val boardSlug:String?,val primaryPath:String?,val archived:Boolean,val createdAtSeconds:Long,val folders:List<HostProjectFolder>) {
    override fun toString()="HostProjectInfo(redacted)"
}
data class HostProjectSnapshot(val owner:ManagementTarget,val homeId:String,val projects:List<HostProjectInfo>,val activeId:String?,val observedAt:Long) {
    val activeProject get()=projects.singleOrNull {it.id==activeId}
    override fun toString()="HostProjectSnapshot(redacted)"
}
data class HostProjectDetail(val owner:ManagementTarget,val homeId:String,val project:HostProjectInfo,val observedAt:Long) {
    override fun toString()="HostProjectDetail(redacted)"
}

/** Bounded native projection, not new upstream limits. No raw JSON, home paths, links or disk I/O. */
object HostProjectProtocol {
    fun validId(value:String)=value.isNotBlank() && value.length<=512 && value.none(Char::isISOControl) && !value.codePoints().anyMatch {it in 0xD800..0xDFFF}
    private inline fun <T> decode(block:()->T):T=try {block()} catch(_:Exception) {throw HostProjectsUnsupported()}
    private fun string(j:JsonObject,key:String,max:Int,optional:Boolean=false,multiline:Boolean=false):String? {
        val v=j[key]
        if(v==null || v is JsonNull) {require(optional);return null}
        require(v is JsonPrimitive && v.isString)
        return v.content.also {s->require(s.length<=max && s.none {it.isISOControl() && !(multiline && it in "\n\r\t")} && !s.codePoints().anyMatch {it in 0xD800..0xDFFF})}
    }
    private fun bool(j:JsonObject,key:String):Boolean {
        val v=j[key] ?: return false
        require(v is JsonPrimitive && !v.isString && v.booleanOrNull!=null)
        return v.boolean
    }
    private fun seconds(j:JsonObject,key:String,optional:Boolean=false):Long? {
        val v=j[key]
        if(v==null || v is JsonNull) {require(optional);return null}
        require(v is JsonPrimitive && !v.isString)
        return v.longOrNull!!.also {require(it in 0..Long.MAX_VALUE/1000)}
    }
    private fun project(j:JsonObject):HostProjectInfo {
        val id=string(j,"id",512)!!.also {require(validId(it))}
        val slug=string(j,"slug",64)!!.also {require(it.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")))}
        val name=string(j,"name",512,multiline=true)!!.also {require(it.isNotBlank())}
        val rows=(j["folders"] ?: JsonArray(emptyList())) as JsonArray;require(rows.size<=128)
        val folders=rows.map {row->
            val f=row as JsonObject
            HostProjectFolder(string(f,"path",4096)!!.also {require(it.isNotBlank())},string(f,"label",512,true,true),bool(f,"is_primary"),seconds(f,"added_at",true))
        }
        require(folders.map {it.path}.distinct().size==folders.size)
        return HostProjectInfo(id,slug,name,string(j,"description",8192,true,true),string(j,"icon",512,true),string(j,"color",128,true),string(j,"board_slug",64,true),string(j,"primary_path",4096,true),bool(j,"archived"),seconds(j,"created_at")!!,Collections.unmodifiableList(folders))
    }
    fun inventory(j:JsonObject,owner:ManagementTarget,homeId:String,observedAt:Long=System.currentTimeMillis()):HostProjectSnapshot=decode {
        GatewayCronInventory.requireProfile(owner.profile);require(homeId.matches(Regex("[a-f0-9]{64}")))
        val rows=j["projects"] as JsonArray;require(rows.size<=500)
        val projects=rows.map {project(it as JsonObject)}
        require(projects.map {it.id}.distinct().size==projects.size && projects.map {it.slug}.distinct().size==projects.size)
        val active=string(j,"active_id",512,true)?.also {require(validId(it))}
        // A deleted/archived project's metadata pointer is not automatically cleared upstream.
        HostProjectSnapshot(owner,homeId,Collections.unmodifiableList(projects),active,observedAt)
    }
    fun resultProject(j:JsonObject):HostProjectInfo?=decode {
        if(j["project"]==null || j["project"] is JsonNull) null else project(j["project"] as JsonObject)
    }
    fun detail(j:JsonObject,owner:ManagementTarget,homeId:String,id:String,observedAt:Long=System.currentTimeMillis()):HostProjectDetail=decode {
        GatewayCronInventory.requireProfile(owner.profile);require(homeId.matches(Regex("[a-f0-9]{64}")) && validId(id))
        val p=project(j["project"] as JsonObject);require(p.id==id) // get_project can fall back from ID to slug.
        HostProjectDetail(owner,homeId,p,observedAt)
    }
}
