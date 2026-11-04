package org.hermesmobile.app

import kotlinx.serialization.json.*
import java.time.Instant

internal object HostRelayLinkFixture {
    const val platform="telegram"
    const val code="AB12CD"
    const val bot="https://t.me/fixture_bot"
    const val reason="private-relay-code-refusal"
    fun channels(mode:Boolean=true):JsonObject {val raw=HostRelayFixture.channels(exclusive=mode);val row=raw["platforms"]!!.jsonArray.single().jsonObject;return JsonObject(raw+mapOf("platforms" to buildJsonArray {add(JsonObject(row+mapOf("id" to JsonPrimitive(platform))))}))}
    fun report(manager:String="connector",botUrl:String=bot):JsonObject {val raw=HostRelayFixture.report(manager);val row=raw["platforms"]!!.jsonArray.single().jsonObject;return JsonObject(raw+mapOf("platforms" to buildJsonArray {add(JsonObject(row+mapOf("platform" to JsonPrimitive(platform),"bot_url" to JsonPrimitive(botUrl))))}))}
    fun issued(codeValue:String=code)=buildJsonObject {put("platform",platform);put("code",codeValue);put("command","/link "+codeValue);put("expires_at",Instant.now().plusSeconds(300).toString());put("bot_url",bot)}
    fun state(value:String="pending")=buildJsonObject {put("state",value);put("reason",reason)}
}
