package org.hermesmobile.app

import kotlinx.serialization.json.*

object GatewayCronFixture {
    fun job(enabled:Boolean=true,id:String="gateway-control-fixture",name:String="Control fixture",model:String="model-fixture")=buildJsonObject {
        put("job_id",id);put("name",name);put("enabled",enabled);put("state",if(enabled) "scheduled" else "paused");put("schedule","Every hour");put("model",model);put("prompt_preview","private-control-preview");put("deliver","local");put("script","fixture.py");put("base_url","https://provider.example/?key=private-provider-key");put("enabled_toolsets",JsonArray(emptyList()))
    }
    fun inventory(rows:List<JsonObject>,profile:String="worker")=buildJsonObject {put("success",true);put("scoped",profile);put("count",rows.size);put("jobs",JsonArray(rows));put("gateway_running",false)}
    fun ready()="""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}"""
    fun result(request:JsonObject,result:JsonObject)=buildJsonObject {put("jsonrpc","2.0");put("id",request["id"]!!);put("result",result)}.toString()
}
