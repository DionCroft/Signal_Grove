package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.security.MessageDigest

/** Profile roots are hashed before entering native state; full host paths are never retained. */
class PluginHome private constructor(private val rootHash:String) {
    internal fun identity()=rootHash
    fun directory(value:JsonElement?):PluginDirectoryTarget? {
        val raw=(value as? JsonPrimitive)?.takeIf {it.isString}?.content ?: return null
        val normalized=normalize(raw) ?: return null
        for(depth in 1..2) {
            var cut=normalized.length
            repeat(depth) {cut=normalized.lastIndexOf('/',cut-1)}
            if(cut<0) continue
            val prefix=normalized.substring(0,cut)
            if(hash(prefix)!=rootHash) continue
            val name=normalized.substring(cut+1)
            if(name!=name.trim() || name.length>512 || !name.split('/').all(::part)) return null
            return PluginDirectoryTarget(name,hash(normalized),rootHash)
        }
        return null
    }
    override fun toString()="PluginHome(redacted)"
    companion object {
        private fun hash(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}
        private fun part(s:String)=s.isNotEmpty() && s.length<=256 && s!="." && !s.contains("..") && s.none {it.isISOControl() || it in ":\\/"}
        private fun normalize(raw:String):String? {
            if(raw.isBlank() || raw.length>8192 || raw.any(Char::isISOControl) || raw.codePoints().anyMatch {it in 0xD800..0xDFFF}) return null
            val drive=raw.length>=3 && raw[0].uppercaseChar() in 'A'..'Z' && raw[1]==':' && (raw[2]=='/' || raw[2].code==92)
            val windows=drive || raw.startsWith("\\\\")
            if(!windows && raw.contains('\\')) return null
            val normalized=(if(windows) raw.replace('\\','/') else raw).trimEnd('/').let {if(it.isEmpty()) "/" else if(drive && it.length==2) "$it/" else it}
            if(normalized=="/" || drive && normalized.length==3) return normalized
            if(normalized.startsWith("///") || !normalized.startsWith('/') && !Regex("^[A-Za-z]:/.*").matches(normalized)) return null
            val parts=normalized.removePrefix("//").removePrefix("/").split('/')
            if(parts.any {it=="." || it==".." || it.isEmpty()}) return null
            return normalized
        }
        fun project(value:JsonElement?):PluginHome? {
            val raw=(value as? JsonPrimitive)?.takeIf {it.isString}?.content ?: return null
            val normalized=normalize(raw) ?: return null
            val root=normalized.trimEnd('/')+"/plugins"
            return PluginHome(hash(root))
        }
    }
}
/** Only a relative, explicitly reviewed identifier survives projection. */
class PluginDirectoryTarget internal constructor(val name:String,private val locationHash:String,private val homeHash:String) {
    fun same(other:PluginDirectoryTarget?)=other!=null && name==other.name && locationHash==other.locationHash
    fun belongsTo(home:PluginHome?)=home!=null && homeHash==home.identity()
    override fun toString()="PluginDirectoryTarget(redacted)"
}
class PluginRemovalChanged(val inventory:GatewayPluginInventory?=null):Exception()
class PluginRemovalReadbackPending:Exception()
class PluginRemovalUncertain:Exception()
class PluginRemovalRequest(val expected:ManagedPlugin) {
    init {require(supported(expected))}
    val directory get()=expected.directory!!
    fun params(profile:String)=buildJsonObject {GatewayCronInventory.requireProfile(profile);put("profile",profile);put("action","remove");put("name",directory.name)}
    fun summary()=buildJsonObject {put("install_name",directory.name);put("plugin_key",expected.key);put("version",expected.version)}
    fun same(row:ManagedPlugin)=expected.key==row.key && expected.name==row.name && expected.version==row.version && expected.description==row.description && expected.source==row.source && expected.status==row.status && expected.portable==row.portable && expected.desktopHalf==row.desktopHalf && expected.catalog==row.catalog && expected.tier==row.tier && expected.installedSha==row.installedSha && expected.catalogSha==row.catalogSha && expected.pinnedSha==row.pinnedSha && directory.same(row.directory)
    fun acknowledge(j:JsonObject)=buildJsonObject {
        require(j["ok"]==JsonPrimitive(true) && j["name"]==JsonPrimitive(directory.name))
        put("reported_remove",true)
        put("memory_provider_cleared",j["cleared_memory_provider"]?.takeIf {it==JsonPrimitive(true) || it==JsonPrimitive(false)} ?: JsonNull)
    }
    fun observed(inventory:GatewayPluginInventory)=inventory.plugins.none {row->
        row.directory?.name==directory.name && row.source in setOf("user","git") || row.key==expected.key && row.source !in setOf("bundled","entrypoint")
    }
    override fun toString()="PluginRemovalRequest(redacted)"
    companion object {
        fun supported(row:ManagedPlugin)=row.source in setOf("user","git") && row.directory!=null
        fun collision(row:ManagedPlugin,inventory:GatewayPluginInventory)=row.directory?.name?.let {target->inventory.plugins.any {other->other.key!=row.key && (other.key==target || other.name==target || other.directory?.name==target)}} ?: true
    }
}
