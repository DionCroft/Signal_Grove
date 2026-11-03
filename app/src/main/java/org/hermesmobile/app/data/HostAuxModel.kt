package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.util.Locale

enum class HostAuxEffort(val wire:String?) { Keep(null),Inherit(null),None("none"),Minimal("minimal"),Low("low"),Medium("medium"),High("high"),Xhigh("xhigh"),Max("max"),Ultra("ultra") }
class HostAuxModelChanged(val inventory:HostModelInventory?):Exception()
class HostAuxModelUncertain:Exception()
class HostAuxModelReadbackPending:Exception()
class HostAuxModelWarning internal constructor(val request:HostAuxModelRequest,val message:String) {override fun toString()="HostAuxModelWarning(redacted)"}
sealed class HostAuxModelReply {
    class Warning(val warning:HostAuxModelWarning,val summary:JsonObject):HostAuxModelReply()
    class Saved(val tasks:List<String>,val summary:JsonObject):HostAuxModelReply()
}
class HostAuxModelRequest private constructor(val expected:HostModelInventory,val task:String,val route:HostModelRoute,val effort:HostAuxEffort,val reset:Boolean,val keepRoute:Boolean,val warning:HostAuxModelWarning?) {
    val targets:List<String> = java.util.Collections.unmodifiableList(if(task.isEmpty() || reset) expected.auxiliary.map {it.task} else listOf(task))
    private val selected=expected.providers.singleOrNull {it.slug==route.provider}
    init {
        GatewayCronInventory.requireProfile(expected.owner.profile)
        require(targets.isNotEmpty() && targets.size<=512 && targets.all(::validTask))
        if(reset) require(task=="__reset__" && route==HostModelRoute("","") && effort==HostAuxEffort.Keep && !keepRoute && warning==null)
        else {
            require(task.isEmpty() || expected.auxiliary.any {it.task==task})
            require(validId(route.provider,256) && (route.model.isEmpty() || validId(route.model,1024)))
            if(keepRoute) require(task.isNotEmpty() && expected.auxiliary.single {it.task==task}.route==route)
            else require(route==HostModelRoute("auto","") || selected?.let {HostMainModelRequest.eligible(it,route.model)}==true)
        }
    }
    fun approve(report:HostAuxModelWarning):HostAuxModelRequest {require(report.request===this && warning==null && !reset);return HostAuxModelRequest(expected,task,route,effort,false,keepRoute,report)}
    fun rebase(current:HostModelInventory)=HostAuxModelRequest(current,task,route,effort,reset,keepRoute,null)
    fun body(profile:String)=buildJsonObject {
        GatewayCronInventory.requireProfile(profile);require(profile==expected.owner.profile)
        put("scope","auxiliary");put("profile",profile);put("task",task);put("provider",route.provider);put("model",route.model);put("confirm_expensive_model",warning!=null)
        if(effort!=HostAuxEffort.Keep) put("reasoning_effort",effort.wire?.let(::JsonPrimitive) ?: JsonNull)
    }
    fun summary()=buildJsonObject {
        put("reset",reset);put("bulk",task.isEmpty() || reset);put("requested_tasks",buildJsonArray {targets.forEach {add(it)}})
        put("requested_provider",route.provider);put("requested_model",route.model);put("reasoning_choice",effort.name);put("selection_warning_approved",warning!=null)
    }
    fun same(current:HostModelInventory):Boolean {
        if(current.owner!=expected.owner || current.homeId!=expected.homeId || current.main!=expected.main || current.auxiliary!=expected.auxiliary) return false
        if(reset || keepRoute || route==HostModelRoute("auto","")) return true
        val row=current.providers.singleOrNull {it.slug==route.provider} ?: return false
        val old=selected ?: return false
        return old.name==row.name && old.source==row.source && old.authenticated==row.authenticated && old.current==row.current && old.userDefined==row.userDefined && old.totalModels==row.totalModels && old.warningPresent==row.warningPresent && old.models==row.models && old.unavailableModels==row.unavailableModels && old.entitlementPending==row.entitlementPending
    }
    fun reply(raw:JsonObject):HostAuxModelReply {
        require(raw["scope"]==JsonPrimitive("auxiliary"))
        if(raw["ok"]==JsonPrimitive(false)) {
            require(!reset && route.model.isNotEmpty() && warning==null && raw["provider"]==JsonPrimitive(route.provider) && raw["model"]==JsonPrimitive(route.model) && raw["confirm_required"]==JsonPrimitive(true))
            val v=raw["confirm_message"];require(v is JsonPrimitive && v.isString && v.content.isNotBlank() && v.content.length<=12000 && v.content.none {it.isISOControl() && it !in "\r\n\t"})
            return HostAuxModelReply.Warning(HostAuxModelWarning(this,v.content),buildJsonObject {summary().forEach {(k,value)->put(k,value)};put("reported_assigned",false);put("selection_warning_required",true)})
        }
        require(raw["ok"]==JsonPrimitive(true))
        val tasks=if(reset) {require(raw["reset"]==JsonPrimitive(true));emptyList()} else {
            require(raw["reset"]==null || raw["reset"]==JsonPrimitive(false))
            require(raw["provider"]==JsonPrimitive(route.provider) && raw["model"]==JsonPrimitive(route.model))
            val list=raw["tasks"];require(list is JsonArray && list.isNotEmpty() && list.size<=512)
            val names=list.map {require(it is JsonPrimitive && it.isString && validTask(it.content));it.content}
            require(names.distinct().size==names.size && (task.isEmpty() || names==listOf(task)))
            if(effort!=HostAuxEffort.Keep) require(raw["reasoning_effort"]==(effort.wire?.let(::JsonPrimitive) ?: JsonNull))
            names
        }
        return HostAuxModelReply.Saved(java.util.Collections.unmodifiableList(tasks),buildJsonObject {summary().forEach {(k,value)->put(k,value)};put("reported_assigned",true);put("reported_reset",reset);if(!reset) put("reported_tasks",buildJsonArray {tasks.forEach {add(it)}})})
    }
    fun observed(current:HostModelInventory,reply:HostAuxModelReply.Saved):Boolean {
        if(current.homeId!=expected.homeId) return false
        if(reset) return current.auxiliary.isNotEmpty() && current.auxiliary.all {it.route==HostModelRoute("auto","") && !it.endpointPresent}
        return reply.tasks.all {name->current.auxiliary.singleOrNull {it.task==name}?.let {it.route==route && when(effort) {HostAuxEffort.Keep->true;HostAuxEffort.Inherit->it.reasoning==null;else->it.reasoning==effort.wire}}==true}
    }
    override fun toString()="HostAuxModelRequest(redacted)"
    companion object {
        private fun validId(v:String,max:Int)=v.isNotBlank() && v.length<=max && v==v.trim() && v.none(Char::isISOControl)
        fun validTask(v:String)=validId(v,256) && v==v.lowercase(Locale.ROOT) && v!="__reset__"
        fun assign(expected:HostModelInventory,task:String,route:HostModelRoute,effort:HostAuxEffort=HostAuxEffort.Keep,keepRoute:Boolean=false)=HostAuxModelRequest(expected,task,route,effort,false,keepRoute,null)
        fun reset(expected:HostModelInventory)=HostAuxModelRequest(expected,"__reset__",HostModelRoute("",""),HostAuxEffort.Keep,true,false,null)
    }
}
