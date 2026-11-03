package org.hermesmobile.app

import kotlinx.serialization.json.*
object HostFallbackFixture {
    const val inline="fallback-existing-inline-fixture"
    const val fresh="fallback-fresh-inline-fixture"
    const val endpoint="https://fallback.test/private?key=endpoint-fixture"
    const val env="FALLBACK_PRIVATE_ENV"
    const val extra="fallback-private-extension-fixture"
    fun row(model:String="primary-model")=buildJsonObject {put("provider","custom:fixture");put("model",model);put("api_key",inline);put("key_env",env);put("base_url",endpoint);put("extension",buildJsonObject {put("data",extra)})}
    fun config(change:String="")=buildJsonObject {
        put("fallback_providers",buildJsonArray {add(if(change=="secret") JsonObject(row()+mapOf("api_key" to JsonPrimitive("rotated-inline-fixture"))) else row());add(buildJsonObject {put("provider","openrouter");put("model","secondary-model")});add(17)})
        put("fallback_model",buildJsonObject {put("provider","zen");put("model",if(change=="legacy") "changed-legacy" else "legacy-model");put("api_key_env","LEGACY_ENV")})
        put("unrelated",buildJsonObject {put("api_key","unrelated-private-fixture")})
    }
    fun ack()=buildJsonObject {put("ok",true)}
}
