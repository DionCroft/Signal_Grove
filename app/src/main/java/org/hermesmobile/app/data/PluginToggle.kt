package org.hermesmobile.app.data

import kotlinx.serialization.json.*

class PluginToggleChanged(val inventory:GatewayPluginInventory):Exception()
class PluginToggleReadbackPending:Exception()
class PluginToggleOutcomeUncertain:Exception()
class PluginToggleRequest(val expected:ManagedPlugin,val enable:Boolean) {
    init {require(supported(expected) && enable!=(expected.status=="enabled"))}
    val receiptAction get()=if(enable) "PluginEnable" else "PluginDisable"
    fun same(row:ManagedPlugin)=expected.key==row.key && expected.name==row.name && expected.version==row.version && expected.description==row.description && expected.source==row.source && expected.status==row.status && expected.portable==row.portable && expected.desktopHalf==row.desktopHalf && expected.catalog==row.catalog && expected.tier==row.tier && expected.installedSha==row.installedSha && expected.catalogSha==row.catalogSha && expected.pinnedSha==row.pinnedSha
    fun params(profile:String)=buildJsonObject {GatewayCronInventory.requireProfile(profile);put("profile",profile);put("action","toggle");put("key",expected.key);put("enable",enable)}
    fun summary()=buildJsonObject {put("requested_enable",enable);put("previous_status",expected.status)}
    fun acknowledge(j:JsonObject) {require(j["ok"]==JsonPrimitive(true) && j["name"]==JsonPrimitive(expected.key))}
    fun observed(inventory:GatewayPluginInventory)=inventory.plugins.singleOrNull {it.key==expected.key}?.let {it.name==expected.name && it.source==expected.source && it.status==if(enable) "enabled" else "disabled"}==true
    override fun toString()="PluginToggleRequest(redacted)"
    companion object {
        fun supported(row:ManagedPlugin)=row.key!="observability/langfuse" && row.status in setOf("enabled","disabled","not enabled") && row.key.length<=512 && row.key.split('/').all {it.matches(Regex("[A-Za-z0-9_][A-Za-z0-9_.-]{0,255}")) && it!="." && it!=".."}
        fun collision(row:ManagedPlugin,inventory:GatewayPluginInventory)=inventory.plugins.any {it.key!=row.key && it.name==row.key}
    }
}
