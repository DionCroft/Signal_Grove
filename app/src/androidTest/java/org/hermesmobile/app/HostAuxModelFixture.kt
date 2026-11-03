package org.hermesmobile.app

import kotlinx.serialization.json.*

object HostAuxModelFixture {
    const val task="title_generation"
    fun auxiliary(model:String=HostMainModelFixture.old,effort:String?="low",reset:Boolean=false,changed:Boolean=false,extra:Boolean=false)=buildJsonObject {
        put("main",buildJsonObject {put("provider",HostMainModelFixture.provider);put("model",HostMainModelFixture.old)})
        put("tasks",buildJsonArray {
            add(buildJsonObject {put("task",task);put("provider",if(reset) "auto" else HostMainModelFixture.provider);put("model",if(reset) "" else model);put("base_url",if(reset) "" else "private-slot-url");put("reasoning_effort",(if(changed) "high" else if(reset) null else effort)?.let(::JsonPrimitive) ?: JsonNull)})
            add(buildJsonObject {put("task","voice_chat");put("provider","auto");put("model","");put("reasoning_effort","none")})
            add(buildJsonObject {put("task","plugin.task");put("provider","auto");put("model","");put("plugin","fixture-plugin");put("label","Fixture inherited task");put("inherit_from",task);put("effective",buildJsonObject {put("provider",if(reset) "auto" else HostMainModelFixture.provider);put("model",if(reset) "" else model)})})
            if(extra) add(buildJsonObject {put("task","later.plugin");put("provider",HostMainModelFixture.provider);put("model",model);put("reasoning_effort",effort?.let(::JsonPrimitive) ?: JsonNull)})
        })
    }
    fun warning()=JsonObject(HostMainModelFixture.warning()+mapOf("scope" to JsonPrimitive("auxiliary")))
    fun saved(tasks:List<String> = listOf(task),effort:JsonElement?=null)=buildJsonObject {
        put("ok",true);put("scope","auxiliary");put("provider",HostMainModelFixture.provider);put("model",HostMainModelFixture.next);put("tasks",buildJsonArray {tasks.forEach {add(it)}});if(effort!=null) put("reasoning_effort",effort);put("api_key","private-saved-key");put("base_url","private-saved-url")
    }
}
