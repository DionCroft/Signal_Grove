package org.hermesmobile.app.data

import kotlinx.serialization.json.*

class HostMainModelChanged(val inventory:HostModelInventory?):Exception()
class HostMainModelUncertain:Exception()
class HostMainModelReadbackPending:Exception()
class HostMainModelWarning internal constructor(val request:HostMainModelRequest,val message:String) {
    override fun toString()="HostMainModelWarning(redacted)"
}
sealed class HostMainModelReply {
    class Warning(val warning:HostMainModelWarning,val summary:JsonObject):HostMainModelReply()
    class Saved(val route:HostModelRoute,val summary:JsonObject,val notice:String?):HostMainModelReply()
}
class HostMainModelRequest private constructor(val expected:HostModelInventory,val provider:String,val model:String,val warning:HostMainModelWarning?) {
    constructor(expected:HostModelInventory,provider:String,model:String):this(expected,provider,model,null)
    val selected=expected.providers.singleOrNull {it.slug==provider} ?: error("Provider unavailable")
    init {GatewayCronInventory.requireProfile(expected.owner.profile);require(eligible(selected,model))}
    fun approve(report:HostMainModelWarning):HostMainModelRequest {require(report.request===this && warning==null);return HostMainModelRequest(expected,provider,model,report)}
    fun body(profile:String)=buildJsonObject {GatewayCronInventory.requireProfile(profile);require(profile==expected.owner.profile);put("scope","main");put("profile",profile);put("provider",provider);put("model",model);put("confirm_expensive_model",warning!=null)}
    fun summary()=buildJsonObject {put("requested_provider",provider);put("requested_model",model);put("previous_provider",expected.main.provider);put("previous_model",expected.main.model);put("selection_warning_approved",warning!=null)}
    fun same(current:HostModelInventory):Boolean {
        if(current.owner!=expected.owner || current.homeId!=expected.homeId || current.main!=expected.main) return false
        val row=current.providers.singleOrNull {it.slug==provider} ?: return false
        // Public preflight only: endpoint/credential changes with the same metadata remain invisible; no CAS exists.
        return selected.name==row.name && selected.source==row.source && selected.authenticated==row.authenticated && selected.current==row.current && selected.userDefined==row.userDefined && selected.totalModels==row.totalModels && selected.warningPresent==row.warningPresent && selected.models==row.models && selected.unavailableModels==row.unavailableModels && selected.entitlementPending==row.entitlementPending
    }
    fun reply(raw:JsonObject):HostMainModelReply {
        fun text(name:String,max:Int,lines:Boolean=false):String {val v=raw[name];require(v is JsonPrimitive && v.isString && v.content.isNotBlank() && v.content.length<=max && v.content.none {it.isISOControl() && (!lines || it !in "\r\n\t")});return v.content}
        require(raw["scope"]==JsonPrimitive("main"))
        if(raw["ok"]==JsonPrimitive(false)) {
            require(warning==null && raw["provider"]==JsonPrimitive(provider) && raw["model"]==JsonPrimitive(model) && raw["confirm_required"]==JsonPrimitive(true))
            val report=HostMainModelWarning(this,text("confirm_message",12000,true))
            return HostMainModelReply.Warning(report,buildJsonObject {summary().forEach {(k,v)->put(k,v)};put("reported_assigned",false);put("selection_warning_required",true)})
        }
        require(raw["ok"]==JsonPrimitive(true))
        val route=HostModelRoute(text("provider",256),text("model",1024))
        fun count(name:String,objects:Boolean=false):Int?=runCatching {val a=raw[name] as? JsonArray ?: return@runCatching null;require(a.size<=512 && a.all {if(objects) it is JsonObject else it is JsonPrimitive && it.isString && it.content.length<=256 && it.content.none(Char::isISOControl)});a.size}.getOrNull()
        val notice=runCatching {text("notice",8192,true)}.getOrNull()
        val reportSummary=buildJsonObject {summary().forEach {(k,v)->put(k,v)};put("reported_assigned",true);put("reported_provider",route.provider);put("reported_model",route.model);count("gateway_tools")?.let {put("gateway_tools_count",it)};count("stale_aux",true)?.let {put("stale_aux_count",it)};put("notice_reported",notice!=null)}
        // Mandatory success can normalize the selected route; optional details cannot erase its ACK.
        return HostMainModelReply.Saved(route,reportSummary,notice)
    }
    override fun toString()="HostMainModelRequest(redacted)"
    companion object {fun eligible(provider:HostModelProvider,model:String)=provider.authenticated==true && provider.entitlementPending!=true && model in provider.models && model !in provider.unavailableModels && provider.slug.isNotBlank() && provider.slug.length<=256 && provider.slug==provider.slug.trim() && provider.slug.none(Char::isISOControl) && model.isNotBlank() && model.length<=1024 && model==model.trim() && model.none(Char::isISOControl)}
}
