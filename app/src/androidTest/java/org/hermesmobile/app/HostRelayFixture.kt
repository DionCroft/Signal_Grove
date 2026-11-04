package org.hermesmobile.app

import kotlinx.serialization.json.*

internal object HostRelayFixture {
    const val account="private-relay-account"
    const val name="Private relay 用户"
    const val handle="@private-relay-handle"
    const val portal="https://portal.example.test/operator/agents"
    fun channels(fronted:Boolean=true,exclusive:Boolean=true):JsonObject {
        val raw=HostChannelsFixture.catalog();val row=raw["platforms"]!!.jsonArray.single().jsonObject
        return JsonObject(raw+mapOf("platforms" to buildJsonArray {add(JsonObject(row+mapOf("relay_fronted" to JsonPrimitive(fronted),"relay_exclusive" to JsonPrimitive(exclusive))))}))
    }
    fun report(manager:String="portal",url:String=portal)=buildJsonObject {put("managed_by",manager);put("portal_url",url);put("private_extension","discarded-relay-private");put("platforms",buildJsonArray {add(buildJsonObject {put("platform",HostChannelsFixture.id);put("bot_url","https://t.me/fixture_bot");put("accounts",buildJsonArray {add(buildJsonObject {put("platformUserId",account);put("displayName",name);put("handle",handle);put("unknown_private","discarded-account-private")})})})})}
}
