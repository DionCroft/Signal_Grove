package org.hermesmobile.app

import kotlinx.serialization.json.*
object CredentialPoolFixture {
    const val provider="openrouter"
    const val secret="pool-fresh-credential-fixture"
    const val label="pool-fresh-label@example.test"
    const val preview="pool-private-preview-fixture"
    const val source="config:/private/pool-source-fixture.yaml"
    fun entry(index:Int,id:String,label:String,source:String,requests:Long=7,status:String="ok")=buildJsonObject {put("index",index);put("id",id);put("label",label);put("source",source);put("auth_type","api_key");put("priority",index-1);put("last_status",status);put("request_count",requests);put("has_refresh",false);put("token_preview",preview)}
    fun rows(change:String="")=listOf(entry(1,if(change=="order") "second-id" else "first-id",if(change=="label") "Changed label" else "Existing primary",if(change=="source") "env:CHANGED_POOL_KEY" else "env:FIXTURE_POOL_KEY",if(change=="counter") 99 else 7),entry(2,if(change=="order") "first-id" else "second-id","Existing secondary",source))
    fun inventory(provider:String=this.provider,rows:List<JsonObject> = rows())=buildJsonObject {put("providers",if(rows.isEmpty()) JsonArray(emptyList()) else buildJsonArray {add(buildJsonObject {put("provider",provider);put("entries",JsonArray(rows))})})}
    fun add(provider:String=this.provider)=inventory(provider,rows()+entry(3,"new-id",label,"manual"))
    fun remove(provider:String=this.provider)=inventory(provider,rows().take(1))
    fun ack(provider:String=this.provider,count:Int=3,cleanup:Boolean=false)=buildJsonObject {put("ok",true);put("provider",provider);put("count",count);if(cleanup) {put("cleaned",buildJsonArray {add("private-cleanup-file-fixture")});put("hints",buildJsonArray {add("private-cli-command-fixture")})}}
}
