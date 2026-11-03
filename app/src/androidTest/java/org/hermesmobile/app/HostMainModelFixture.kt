package org.hermesmobile.app

import kotlinx.serialization.json.*

object HostMainModelFixture {
    const val provider="fixture"
    const val old="old-model"
    const val next="new-model"
    const val warningText="Fixture selection warning: price and data-training terms need approval."
    fun auxiliary(model:String=old,provider:String=this.provider)=buildJsonObject {put("main",buildJsonObject {put("provider",provider);put("model",model)});put("tasks",JsonArray(emptyList()))}
    fun catalog(model:String=old,provider:String=this.provider,changed:Boolean=false,blocked:Boolean=false)=buildJsonObject {
        put("provider",provider);put("model",model);put("providers",buildJsonArray {add(buildJsonObject {put("slug",HostMainModelFixture.provider);put("name",if(changed) "Changed provider" else "Fixture provider");put("source","user-config");put("authenticated",true);put("is_current",provider==HostMainModelFixture.provider);put("models",buildJsonArray {add(old);add(next);add("blocked-model")});put("unavailable_models",buildJsonArray {add("blocked-model");if(blocked) add(next)});put("api_key","private-catalog-key");put("api_url","private-catalog-url")})})
    }
    fun saved(model:String=next,provider:String=this.provider,optional:Boolean=false)=buildJsonObject {put("ok",true);put("scope","main");put("provider",provider);put("model",model);put("base_url","private-saved-endpoint");put("gateway_tools",if(optional) JsonPrimitive("private-malformed-tools") else buildJsonArray {add("private-tool")});put("stale_aux",if(optional) JsonPrimitive("private-malformed-aux") else JsonArray(emptyList()));put("notice","Fixture post-assignment notice")}
    fun warning()=buildJsonObject {put("ok",false);put("scope","main");put("provider",provider);put("model",next);put("confirm_required",true);put("confirm_message",warningText)}
}
