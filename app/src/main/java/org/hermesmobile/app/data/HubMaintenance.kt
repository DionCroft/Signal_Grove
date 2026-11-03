package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.util.Collections

class HubInstalledEntry(val name:String,entries:Map<String,HubInstalled>) {
    private val snapshot=entries.toMap()
    val identifiers:List<String> = Collections.unmodifiableList(snapshot.keys.sorted())
    val trust=snapshot.values.map {it.trust}.distinct().singleOrNull().orEmpty()
    val verdict=snapshot.values.map {it.verdict}.distinct().singleOrNull().orEmpty()
    val addressable=SkillProtocol.name(name) && name !in setOf(".","..") && !name.startsWith('-') && name.none {it=='/' || it=='\\' || it==':' } && snapshot.values.distinct().size==1
    fun same(other:HubInstalledEntry)=name==other.name && snapshot==other.snapshot
    fun displayIdentifier(identifier:String,query:Boolean)=HubSkill("","","",identifier,"","",emptyList(),"",null).displayIdentifier(query)
    override fun toString()="HubInstalledEntry(redacted)"
}
class HubInstalledInventory private constructor(val entries:List<HubInstalledEntry>) {
    fun same(other:HubInstalledInventory)=entries.size==other.entries.size && entries.all {row->other.entries.singleOrNull {it.name==row.name}?.let(row::same)==true}
    override fun toString()="HubInstalledInventory(redacted)"
    companion object {
        fun from(sources:HubSources):HubInstalledInventory {
            val map=sources.installed ?: throw SkillHubUnsupported()
            val grouped=map.entries.groupBy {it.value.name}.map {(name,rows)->HubInstalledEntry(name,rows.associate {it.key to it.value})}.sortedBy {it.name}
            return HubInstalledInventory(Collections.unmodifiableList(grouped))
        }
    }
}
enum class HubMaintenanceAction { Remove, UpdateAll }
class HubMaintenanceRequest(val action:HubMaintenanceAction,val inventory:HubInstalledInventory,val name:String="") {
    val selected=if(action==HubMaintenanceAction.Remove) inventory.entries.singleOrNull {it.name==name} else null
    init {require(inventory.entries.isNotEmpty());require(if(action==HubMaintenanceAction.Remove) selected?.addressable==true else name.isEmpty())}
    val receiptAction get()=if(action==HubMaintenanceAction.Remove) "HubRemove" else "HubUpdateAll"
    val endpoint get()=if(action==HubMaintenanceAction.Remove) "uninstall" else "update"
    fun same(fresh:HubInstalledInventory)=if(action==HubMaintenanceAction.UpdateAll) inventory.same(fresh) else fresh.entries.singleOrNull {it.name==name}?.let {selected!!.same(it)}==true
    fun body(profile:String)=buildJsonObject {SkillProtocol.profile(profile);put("profile",profile);if(action==HubMaintenanceAction.Remove) put("name",name)}
    fun summary()=buildJsonObject {put("background",true);put("all_skills",action==HubMaintenanceAction.UpdateAll);put("reviewed_count",if(action==HubMaintenanceAction.Remove) 1 else inventory.entries.size)}.toString()
    fun acknowledge(j:JsonObject):String {
        val expected=if(action==HubMaintenanceAction.Remove) HubInstallRequest.actionName(name,"uninstall") else "skills-update"
        val pid=j["pid"] as? JsonPrimitive
        require(j["ok"]==JsonPrimitive(true) && j["name"]==JsonPrimitive(expected) && pid!=null && !pid.isString && (pid.longOrNull ?: 0)>0)
        return buildJsonObject {put("accepted",true);put("background",true)}.toString()
    }
    override fun toString()="HubMaintenanceRequest(redacted)"
}
