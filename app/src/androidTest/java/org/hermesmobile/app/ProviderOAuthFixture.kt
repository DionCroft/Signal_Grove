package org.hermesmobile.app
import kotlinx.serialization.json.*
object ProviderOAuthFixture {
    const val provider="fixture-oauth"
    const val session="fixture_session_1234567"
    const val code="ABCD-EFGH"
    const val url="https://provider.example/device?user_code=ABCD-EFGH"
    fun catalog(changed:Boolean=false)=buildJsonObject {put("providers",buildJsonArray {
        add(buildJsonObject {put("id",provider);put("name",if(changed) "Changed" else "Fixture provider");put("flow","device_code");put("disconnectable",true);put("status",buildJsonObject {put("logged_in",false);put("token_preview","private-preview");put("source","private-path")});put("cli_command","private-command")})
        add(buildJsonObject {put("id","external");put("name","External CLI");put("flow","external");put("disconnectable",false);put("status",buildJsonObject {put("logged_in",true)})})
    })}
    fun start()=buildJsonObject {put("session_id",session);put("flow","device_code");put("user_code",code);put("verification_url",url);put("expires_in",900);put("poll_interval",5)}
    fun poll(status:String="pending",id:String=session)=buildJsonObject {put("session_id",id);put("status",status);put("account_email","private-email@example.com");put("error_message","private-error");put("handoff",buildJsonArray {add("private-path")})}
}
