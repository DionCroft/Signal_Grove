package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.util.Collections

data class McpCatalogEnvironment(val name:String,val prompt:String,val required:Boolean)
class McpCatalogEntry private constructor(private val snapshot:String) {
    private val row=Json.parseToJsonElement(snapshot).jsonObject
    val name=row.text("name")
    val description=row.text("description")
    val installed=flag("installed")
    val enabled=flag("enabled")
    val needsInstall=flag("needs_install")
    val transport=row.text("transport")
    val auth=row.text("auth_type")
    val environment:List<McpCatalogEnvironment> = Collections.unmodifiableList((row["required_env"] as? JsonArray ?: error("Missing environment schema.")).also {require(it.size<=32)}.map {
        val spec=it.jsonObject;val name=spec.text("name");val prompt=spec.text("prompt")
        require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")) && prompt.length<=2048)
        McpCatalogEnvironment(name,prompt,spec["required"]?.jsonPrimitive?.booleanOrNull ?: error("Missing requirement."))
    })
    val details:String=buildList {
        for(key in listOf("source","transport","auth_type","command","url","install_url","install_ref","post_install")) row.text(key).takeIf {it.isNotBlank()}?.let {add("$key: $it")}
        for(key in listOf("args","bootstrap","default_enabled")) (row[key] as? JsonArray)?.let {values->add("$key:\n"+values.joinToString("\n") {it.jsonPrimitive.content})}
    }.joinToString("\n\n")
    // Background CLI installation cannot reliably consume the supplied non-secret values.
    val supported get()=transport in setOf("http","sse","stdio") && (!needsInstall || environment.isEmpty())
    private fun flag(key:String)=row[key]?.jsonPrimitive?.booleanOrNull ?: error("Missing catalog state.")
    fun same(other:McpCatalogEntry)=row==other.row
    override fun toString()="McpCatalogEntry($name)"
    companion object {
        fun inventory(j:JsonObject):List<McpCatalogEntry> {
            val entries=j["entries"] as? JsonArray ?: error("Missing catalog.");require(entries.size<=500)
            return entries.map {value ->
                val text=value.toString();require(text.length<=65536)
                McpCatalogEntry(text).also {entry ->require(McpProtocol.addressable(entry.name));require(entry.description.length<=8192);require(entry.environment.map {it.name}.distinct().size==entry.environment.size)}
            }.also {require(it.map {entry->entry.name}.distinct().size==it.size)}
        }
    }
}
class McpCatalogInstall(val entry:McpCatalogEntry,environment:Map<String,String>,val enable:Boolean) {
    private val values=environment.toMap()
    init {
        require(entry.supported && !entry.installed)
        require(!entry.needsInstall || enable)
        require(values.keys.all {key->entry.environment.any {it.name==key}})
        require(entry.environment.filter {it.required}.all {values[it.name]?.isNotBlank()==true})
        require(values.values.all {it.length<=8192 && '\u0000' !in it})
    }
    fun body(profile:String)=buildJsonObject {put("name",entry.name);put("profile",profile);put("enable",enable);put("env",buildJsonObject {values.forEach {(key,value)->put(key,value)}})}
    fun summary()=buildJsonObject {put("enable",enable);put("background",entry.needsInstall);put("env_keys",JsonArray(values.keys.sorted().map(::JsonPrimitive)))}.toString()
    fun acknowledge(j:JsonObject):String {
        require(j["ok"]?.jsonPrimitive?.booleanOrNull==true && j.text("name")==entry.name)
        val background=j["background"]?.jsonPrimitive?.booleanOrNull ?: error("Missing installation mode.")
        require(background==entry.needsInstall)
        if(background) require(j.text("action").isNotBlank() && j.text("action").length<=256 && j.text("action").none(Char::isISOControl))
        return buildJsonObject {put("accepted",true);put("background",background);put("enable",enable)}.toString()
    }
    override fun toString()="McpCatalogInstall(redacted)"
}
class McpCatalogChanged:Exception()
