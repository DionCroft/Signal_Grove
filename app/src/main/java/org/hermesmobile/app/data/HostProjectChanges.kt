package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.security.MessageDigest

/** Only these stored-project mutations are offered; no arbitrary gateway RPC is exposed. */
enum class HostProjectAction(val rpc:String) {
    Create("projects.create"), Update("projects.update"), AddFolder("projects.add_folder"),
    RemoveFolder("projects.remove_folder"), SetPrimary("projects.set_primary"),
    Archive("projects.archive"), Restore("projects.archive"), Delete("projects.delete"),
    SetActive("projects.set_active"), ClearActive("projects.set_active");
    val receiptAction get()="Project"+name
    companion object {fun receipt(value:String)=entries.any {it.receiptAction==value}}
}

class HostProjectChangeInvalid:Exception("Invalid project change.")
class HostProjectChangeChanged:Exception("Reviewed project metadata changed.")
class HostProjectChangeUncertain:Exception("Project change outcome is unknown.")
class HostProjectChangeReadbackPending:Exception("Project configuration needs another read.")

private fun projectHash(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}

data class HostProjectCreateDraft(val name:String,val slug:String?=null,val description:String?=null,val icon:String?=null,val color:String?=null,val boardSlug:String?=null,val folders:List<String> = emptyList(),val primaryPath:String?=null) {
    override fun toString()="HostProjectCreateDraft(redacted)"
}

data class HostProjectAcknowledgement(val project:HostProjectInfo?,val inventory:HostProjectSnapshot?,val activeId:String?,val effectReported:Boolean,val pathMappingUnverified:Boolean=false) {
    override fun toString()="HostProjectAcknowledgement(redacted)"
}

/** Private immutable review; durable summaries deliberately omit names, paths and input values. */
class HostProjectChange private constructor(val expected:HostProjectSnapshot,val action:HostProjectAction,val id:String?,val values:JsonObject) {
    val selected get()=expected.projects.singleOrNull {it.id==id}
    val receiptTarget get()=id?.let(::projectHash).orEmpty()
    override fun toString()="HostProjectChange(redacted)"
    fun params(profile:String):JsonObject {
        GatewayCronInventory.requireProfile(profile);require(profile==expected.owner.profile)
        return JsonObject(values+mapOf("profile" to JsonPrimitive(profile)))
    }
    fun same(current:HostProjectSnapshot)=expected.owner==current.owner && expected.homeId==current.homeId && expected.projects==current.projects && expected.activeId==current.activeId && (id==null || !collision(current,id))
    fun summary()=buildJsonObject {
        put("action",action.name);put("project_id_hash",receiptTarget)
        put("fields",JsonArray(values.keys.filter {it !in setOf("id","use","restore")}.sorted().map(::JsonPrimitive)))
        if(action==HostProjectAction.Create) {put("folder_count",(values["folders"] as? JsonArray)?.size ?: 0);put("explicit_primary",values.containsKey("primary_path"))}
    }
    fun acknowledgementSummary(ack:HostProjectAcknowledgement)=JsonObject(summary()+buildJsonObject {
        put("host_acknowledged",true);put("requested_effect_reported",ack.effectReported);put("path_mapping_unverified",ack.pathMappingUnverified);put("configuration_observed",false)
        put("canonical_id_hash",(ack.project?.id ?: id)?.let(::projectHash).orEmpty())
    })
    private fun field(project:HostProjectInfo,key:String):String?=when(key) {
        "name"->project.name;"description"->project.description;"icon"->project.icon;"color"->project.color;"board_slug"->project.boardSlug;else->null
    }
    private fun displayFieldsMatch(project:HostProjectInfo):Boolean=values.keys.filter {it in editKeys}.all {key->
        val value=values[key]!!.jsonPrimitive.content
        field(project,key)==if(key in setOf("icon","color","board_slug") && (action!=HostProjectAction.Create || key=="board_slug")) value.ifEmpty {null} else value
    }
    fun acknowledge(result:JsonObject):HostProjectAcknowledgement {
        val owner=expected.owner;val home=expected.homeId
        return when(action) {
            HostProjectAction.Archive,HostProjectAction.Restore,HostProjectAction.Delete->{
                val inventory=HostProjectProtocol.inventory(result,owner,home)
                val row=inventory.projects.singleOrNull {it.id==id}
                val reported=if(action==HostProjectAction.Delete) row==null else row!=null && row.archived==(action==HostProjectAction.Archive)
                HostProjectAcknowledgement(null,inventory,null,reported)
            }
            HostProjectAction.SetActive,HostProjectAction.ClearActive->{
                require(result.containsKey("active_id"))
                val value=result["active_id"]
                val active=if(value is JsonNull) null else (value as JsonPrimitive).also {require(it.isString && HostProjectProtocol.validId(it.content))}.content
                HostProjectAcknowledgement(null,null,active,active==id)
            }
            else->{
                val project=if(action==HostProjectAction.Create) HostProjectProtocol.resultProject(result) else HostProjectProtocol.detail(result,owner,home,id!!).project
                if(project==null) return HostProjectAcknowledgement(null,null,null,false)
                if(action==HostProjectAction.Create) require(expected.projects.none {it.id==project.id})
                val path=values["path"]?.jsonPrimitive?.content
                var normalized=false
                val reported=when(action) {
                    HostProjectAction.Create->{
                        val requested=(values["folders"] as? JsonArray).orEmpty().map {it.jsonPrimitive.content}+listOfNotNull(values["primary_path"]?.jsonPrimitive?.content)
                        normalized=requested.any {input->project.folders.none {it.path==input}}
                        displayFieldsMatch(project) && !project.archived && if(requested.isEmpty()) project.folders.isEmpty() && project.primaryPath==null else project.folders.isNotEmpty() && project.folders.size<=requested.distinct().size && project.primaryPath!=null && project.folders.any {it.path==project.primaryPath && it.primary}
                    }
                    HostProjectAction.Update->displayFieldsMatch(project)
                    HostProjectAction.AddFolder->{
                        val before=selected!!
                        val folder=project.folders.singleOrNull {it.path==path} ?: project.folders.filter {after->before.folders.none {it.path==after.path}}.singleOrNull()
                        normalized=folder!=null && folder.path!=path
                        folder!=null && (values["label"]==null || folder.label==values["label"]!!.jsonPrimitive.content) && (values["is_primary"]!=JsonPrimitive(true) && before.folders.any {it.primary} || folder.primary && project.primaryPath==folder.path)
                    }
                    HostProjectAction.RemoveFolder->project.folders.none {it.path==path} && project.primaryPath!=path
                    HostProjectAction.SetPrimary->project.primaryPath==path && project.folders.any {it.path==path && it.primary}
                    else->false
                }
                HostProjectAcknowledgement(project,null,null,reported,normalized)
            }
        }
    }
    fun observed(ack:HostProjectAcknowledgement,after:HostProjectSnapshot):Boolean {
        if(after.owner!=expected.owner || after.homeId!=expected.homeId || !ack.effectReported) return false
        return when(action) {
            HostProjectAction.SetActive->after.activeId==id && after.projects.any {it.id==id}
            HostProjectAction.ClearActive->after.activeId==null
            HostProjectAction.Delete->after.projects.none {it.id==id}
            HostProjectAction.Archive,HostProjectAction.Restore->after.projects.singleOrNull {it.id==id}==ack.inventory?.projects?.singleOrNull {it.id==id}
            else->ack.project!=null && after.projects.singleOrNull {it.id==ack.project.id}==ack.project
        }
    }
    companion object {
        val editKeys=setOf("name","description","icon","color","board_slug")
        fun collision(snapshot:HostProjectSnapshot,id:String)=snapshot.projects.any {it.id!=id && it.slug==id.lowercase()}
        private inline fun build(block:()->HostProjectChange)=try {block()} catch(_:Exception) {throw HostProjectChangeInvalid()}
        private fun text(value:String,max:Int,multiline:Boolean=false):String=value.also {s->require(s.length<=max && s.none {it.isISOControl() && !(multiline && it in "\n\r\t")} && !s.codePoints().anyMatch {it in 0xD800..0xDFFF})}
        private fun name(value:String)=text(value.trim(),512,true).also {require(it.isNotBlank())}
        private fun slug(value:String,blank:Boolean=false)=text(value.trim().lowercase(),64).also {require(blank && it.isEmpty() || it.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")))}
        private fun hostPath(value:String)=text(value,4096).also {require(it.isNotBlank())}
        private fun selected(snapshot:HostProjectSnapshot,id:String):HostProjectInfo {
            require(HostProjectProtocol.validId(id) && !collision(snapshot,id))
            return snapshot.projects.single {it.id==id}
        }
        fun create(snapshot:HostProjectSnapshot,draft:HostProjectCreateDraft)=build {
            val folders=draft.folders.map(::hostPath).distinct();val primary=draft.primaryPath?.let(::hostPath)
            require((folders+listOfNotNull(primary)).distinct().size<=128)
            val values=buildJsonObject {
                put("name",name(draft.name));put("use",false)
                draft.slug?.let {put("slug",slug(it))};draft.description?.let {put("description",text(it,8192,true))}
                draft.icon?.let {put("icon",text(it,512))};draft.color?.let {put("color",text(it,128))};draft.boardSlug?.let {put("board_slug",slug(it,true))}
                if(folders.isNotEmpty()) put("folders",JsonArray(folders.map(::JsonPrimitive)))
                primary?.let {put("primary_path",it)}
            }
            HostProjectChange(snapshot,HostProjectAction.Create,null,values)
        }
        fun update(snapshot:HostProjectSnapshot,id:String,edits:Map<String,String>)=build {
            selected(snapshot,id);require(edits.isNotEmpty() && edits.keys.all {it in editKeys})
            val values=buildJsonObject {put("id",id);edits.forEach {(key,value)->put(key,when(key) {"name"->name(value);"description"->text(value,8192,true);"icon"->text(value,512);"color"->text(value,128);else->slug(value,true)})}}
            HostProjectChange(snapshot,HostProjectAction.Update,id,values)
        }
        fun addFolder(snapshot:HostProjectSnapshot,id:String,path:String,label:String?=null,primary:Boolean=false)=build {
            val row=selected(snapshot,id);require(row.folders.size<128 || row.folders.any {it.path==path})
            HostProjectChange(snapshot,HostProjectAction.AddFolder,id,buildJsonObject {put("id",id);put("path",hostPath(path));put("is_primary",primary);label?.let {put("label",text(it,512,true))}})
        }
        fun folder(snapshot:HostProjectSnapshot,id:String,path:String,primary:Boolean)=build {
            val row=selected(snapshot,id);require(row.folders.any {it.path==path})
            HostProjectChange(snapshot,if(primary) HostProjectAction.SetPrimary else HostProjectAction.RemoveFolder,id,buildJsonObject {put("id",id);put("path",hostPath(path))})
        }
        fun action(snapshot:HostProjectSnapshot,id:String?,action:HostProjectAction)=build {
            require(action in setOf(HostProjectAction.Archive,HostProjectAction.Restore,HostProjectAction.Delete,HostProjectAction.SetActive,HostProjectAction.ClearActive))
            if(action==HostProjectAction.ClearActive) require(id==null) else selected(snapshot,requireNotNull(id))
            HostProjectChange(snapshot,action,id,buildJsonObject {
                put("id",id?.let(::JsonPrimitive) ?: JsonNull)
                if(action in setOf(HostProjectAction.Archive,HostProjectAction.Restore)) put("restore",action==HostProjectAction.Restore)
            })
        }
    }
}