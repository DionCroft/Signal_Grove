package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class ProviderOAuthTest {
    private val target=ManagementTarget("a".repeat(64),"https://hermes.example/prefix/","worker","password · fixture")
    private val home="b".repeat(64)
    private fun start()=buildJsonObject {put("session_id","fixture_session_1234567");put("flow","device_code");put("user_code","ABCD-EFGH");put("verification_url","https://provider.example/device?user_code=ABCD-EFGH");put("expires_in",1800);put("poll_interval",5)}
    private fun binding()=ProviderOAuthBinding.parse(ManagementOperation("key",target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"ProviderOAuthStart","fixture-oauth","{}","acknowledged",ProviderOAuthProtocol.start(start(),home).toString(),1),target)
    @Test fun catalogDropsPrivateFieldsAndKeepsDynamicProviders() {
        val j=Json.parseToJsonElement("""{"providers":[{"id":"plugin-oauth","name":"Plugin","flow":"device_code","disconnectable":true,"cli_command":"private","status":{"logged_in":true,"token_preview":"private","source":"private"}},{"id":"external","name":"External","flow":"external","disconnectable":false,"status":{"logged_in":false}}]}""").jsonObject
        val inventory=ProviderOAuthInventory.parse(j,target,home);assertTrue(inventory.rows.first().canStart);assertFalse(inventory.rows.last().canStart);assertFalse(inventory.rows.toString().contains("private"));assertTrue(runCatching {(inventory.rows as MutableList).clear()}.isFailure)
    }
    @Test fun catalogRejectsMissingTypedFlags() {
        for(raw in listOf("""{"providers":[{"id":"p","name":"P","flow":"device_code","disconnectable":"true","status":{"logged_in":true}}]}""","""{"providers":[{"id":"p","name":"P","flow":"device_code","disconnectable":true,"status":{}}]}""")) assertTrue(runCatching {ProviderOAuthInventory.parse(Json.parseToJsonElement(raw).jsonObject,target,home)}.isFailure)
    }
    @Test fun duplicateProviderSelectorsCannotBeReviewed() {val row=buildJsonObject {put("id","p");put("name","P");put("flow","device_code");put("disconnectable",true);put("status",buildJsonObject {put("logged_in",false)})};assertTrue(runCatching {ProviderOAuthInventory.parse(buildJsonObject {put("providers",JsonArray(listOf(row,row)))},target,home)}.isFailure)}
    @Test fun mandatoryStartAckDoesNotDependOnDisplaySecrets() {
        val j=JsonObject(start()-setOf("user_code","verification_url","expires_in"));val ack=ProviderOAuthProtocol.start(j,home);assertEquals("pending",ack.text("status"));assertFalse(ack.toString().contains("ABCD"));assertTrue(runCatching {ProviderDeviceCode.parse(j,1000)}.isFailure)
    }
    @Test fun deviceCodeExpiresAtEarlierOfHostLifetimeAndFifteenMinutes() {assertEquals(901000L,ProviderDeviceCode.parse(start(),1000).expiresAt);assertEquals(6000L,ProviderDeviceCode.parse(JsonObject(start()+mapOf("expires_in" to JsonPrimitive(5))),1000).expiresAt);assertFalse(ProviderDeviceCode.parse(start(),1000).toString().contains("ABCD"))}
    @Test fun unsafeBrowserDestinationsNeverBecomeUsableCodes() {
        for(url in listOf("http://provider.example/device","https://user:pass@provider.example/device","https://provider.example/#fragment","https://provider.example/?access_token=secret","javascript:alert(1)","https://provider.example/\\evil")) assertTrue(url,runCatching {ProviderDeviceCode.parse(JsonObject(start()+mapOf("verification_url" to JsonPrimitive(url))),1)}.isFailure)
    }
    @Test fun formattedDeviceCodesAllowSpacesButRejectControls() {assertEquals("ABCD EFGH",ProviderDeviceCode.parse(JsonObject(start()+mapOf("user_code" to JsonPrimitive("ABCD EFGH"))),1).code);assertTrue(runCatching {ProviderDeviceCode.parse(JsonObject(start()+mapOf("user_code" to JsonPrimitive("ABCD\nEFGH"))),1)}.isFailure)}
    @Test fun browserReviewDropsQueryWhilePreservingOneTimeLink() {val code=ProviderDeviceCode.parse(start(),1);assertEquals("https://provider.example/device",code.destination());assertTrue(code.url.contains("user_code="))}
    @Test fun receiptBindingRefusesRotatedActorOrDifferentApiProfileHost() {
        val receipt=binding().receipt
        for(other in listOf(target.copy(actor="password · other"),target.copy(apiScope="c".repeat(64)),target.copy(profile="other"),target.copy(url="https://other.example/"))) assertTrue(runCatching {ProviderOAuthBinding.parse(receipt,other)}.isFailure)
        assertFalse(receipt.toString().contains("ABCD"))
    }
    @Test fun statusMustMatchOwnedSessionAndRecognizedState() {
        val binding=binding()
        for(j in listOf(buildJsonObject {put("session_id","other_session_1234567");put("status","approved")},buildJsonObject {put("session_id",binding.sessionId);put("status","completed")})) assertTrue(runCatching {ProviderOAuthProtocol.observation(j,binding)}.isFailure)
        val summary=ProviderOAuthProtocol.observation(buildJsonObject {put("session_id",binding.sessionId);put("status","approved");put("account_email","private@example.com");put("error_message","private")},binding)
        assertEquals(home,summary.text("home_id"));assertFalse(summary.toString().contains("private"))
    }
    @Test fun cancelRequiresExactTypedAckAndKeepsKnownNotFoundDistinct() {
        val b=binding();assertEquals(JsonPrimitive(true),ProviderOAuthProtocol.cancellation(buildJsonObject {put("ok",true);put("session_id",b.sessionId)},b)["cancel_acknowledged"])
        assertEquals(JsonPrimitive(true),ProviderOAuthProtocol.cancellation(buildJsonObject {put("ok",false);put("message","session not found")},b)["session_not_found"])
        for(j in listOf(buildJsonObject {put("ok","true");put("session_id",b.sessionId)},buildJsonObject {put("ok",true);put("session_id","other_session_1234567")})) assertTrue(runCatching {ProviderOAuthProtocol.cancellation(j,b)}.isFailure)
    }
    @Test fun disconnectMustEchoReviewedProvider() {assertTrue(runCatching {ProviderOAuthProtocol.disconnect(buildJsonObject {put("ok",true);put("provider","other")},"fixture-oauth")}.isFailure)}
    @Test fun reservedAndPathSelectorsAreRejected() {for(id in listOf("sessions","..","p/other","p?token=x"," p")) assertFalse(ProviderOAuthProtocol.providerId(id));assertTrue(ProviderOAuthProtocol.providerId("plugin.fixture-oauth"))}
}
