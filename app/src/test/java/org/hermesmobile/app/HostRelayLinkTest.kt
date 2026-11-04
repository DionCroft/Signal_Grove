package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HostRelayLinkTest {
    private val owner=ManagementTarget("a".repeat(64),"https://host.test/prefix/","worker","password · fixture")
    private fun inventory(target:ManagementTarget=owner,home:String="b".repeat(64),manager:HostRelayManager=HostRelayManager.Connector,bot:String="https://t.me/fixture",mode:Boolean=true):HostRelayInventory {
        val channels=HostChannelInventory(target,home,listOf(HostChannelRow("telegram","Telegram","","",false,true,false,"stopped",false,false,true,mode,"",null,emptyList())))
        return HostRelayInventory(HostRelayScope.from(channels),manager,"https://portal.test/agents",listOf(HostRelayPlatform("telegram",bot,emptyList())))
    }
    private fun request()=HostRelayLinkRequest(inventory(),"telegram")
    private fun raw()=buildJsonObject {put("platform","telegram");put("code","AB12CD");put("command","/link AB12CD");put("expires_at","2040-01-01T00:00:00Z");put("bot_url","https://t.me/fixture")}
    private fun record(issued:Boolean=true,status:String=if(issued) "pending" else "not_issued")=ManagementOperation("receipt-key",owner.resourceScope,owner.apiScope,owner.url,owner.profile,owner.actor,"RelayLinkStart","telegram",request().summary().toString(),"acknowledged",buildJsonObject {put("acknowledged",true);put("code_issued",issued);put("status",status)}.toString(),1)
    private fun fails(block:()->Unit)=assertTrue(runCatching(block).isFailure)
    @Test fun onlyConnectorAndKnownPlatformCanBeReviewed() {for(m in listOf(HostRelayManager.Portal,HostRelayManager.Unknown)) fails {HostRelayLinkRequest(inventory(manager=m),"telegram")};fails {HostRelayLinkRequest(inventory(),"other")}}
    @Test fun freshReadMustMatchOwnerHomeModeAndReportedBot() {val r=request();assertTrue(r.same(inventory()));for(i in listOf(inventory(target=owner.copy(actor="other")),inventory(target=owner.copy(apiScope="c".repeat(64))),inventory(home="c".repeat(64)),inventory(manager=HostRelayManager.Portal),inventory(bot="https://t.me/changed"),inventory(mode=false))) assertFalse(r.same(i))}
    @Test fun acknowledgementNeedsExactPlatformCodeAndPlainLinkCommandWithoutGenericOk() {
        request().acknowledge(raw())
        for(change in listOf(mapOf("platform" to JsonPrimitive("discord")),mapOf("code" to JsonPrimitive("bad/code")),mapOf("code" to JsonPrimitive("bad\ncode")),mapOf("code" to JsonPrimitive(12)),mapOf("command" to JsonPrimitive("/other AB12CD")))) fails {request().acknowledge(JsonObject(raw()+change))}
        for(key in listOf("platform","code","command")) fails {request().acknowledge(JsonObject(raw()-key))}
    }
    @Test fun localRetentionIsAtMostFifteenMinutesAndCanBeShorterThanServerExpiry() {val c=HostRelayCode.from(raw(),request(),1000);assertEquals(901000,c.expiresAt);val early=HostRelayCode.from(JsonObject(raw()+mapOf("expires_at" to JsonPrimitive("1970-01-01T00:00:10Z"))),request(),1000);assertEquals(10000,early.expiresAt)}
    @Test fun malformedOptionalExpiryAndBotDoNotEraseAcknowledgedCode() {val r=JsonObject(raw()+mapOf("expires_at" to JsonPrimitive(17),"bot_url" to buildJsonObject {put("bad",true)}));request().acknowledge(r);val c=HostRelayCode.from(r,request(),1000);assertEquals("AB12CD",c.code);assertNull(c.botUrl);assertEquals(901000,c.expiresAt)}
    @Test fun browserDestinationsMatchOnlyReviewedOfficialSharedBotFormats() {
        assertTrue(HostRelayLinkPolicy.bot("telegram","AB12CD","https://t.me/fixture"));assertTrue(HostRelayLinkPolicy.bot("discord","AB12CD","https://discord.com/users/12345"));assertTrue(HostRelayLinkPolicy.bot("whatsapp","AB12CD","https://wa.me/12345?text=%2Flink%20AB12CD"))
        for(url in listOf("https://evil.test/fixture","http://t.me/fixture","https://user:pass@t.me/fixture","https://t.me/fixture?token=AB12CD","https://t.me/fixture#secret","https://t.me:8443/fixture")) assertFalse(HostRelayLinkPolicy.bot("telegram","AB12CD",url))
        assertFalse(HostRelayLinkPolicy.bot("whatsapp","AB12CD","https://wa.me/12345?text=%2Flink%20OTHER"));assertFalse(HostRelayLinkPolicy.bot("whatsapp","AB12CD","https://wa.me/12345?text=%2Flink%20AB12CD&text=extra"));assertFalse(HostRelayLinkPolicy.bot("future","AB12CD","https://future.test/bot"))
    }
    @Test fun originalReceiptModesAndFlagsAreStrictlyTypedAndImmutable() {val b=HostRelayLinkBinding.parse(record(),owner);assertTrue(b.issued);assertEquals("pending",b.status);assertTrue(b.same(inventory()));assertFalse(b.same(inventory(mode=false)));fails {(b.modes as MutableMap).clear()};assertEquals(setOf("platform","home_id","relay_modes"),request().summary().keys)}
    @Test fun knownPreMintRefusalIsDistinctFromAnIssuedCode() {val b=HostRelayLinkBinding.parse(record(false),owner);assertFalse(b.issued);assertEquals("not_issued",b.status);for(s in listOf("pending","inactive","refused")) fails {HostRelayLinkBinding.parse(record(false,s),owner)}}
    @Test fun localAndReportedIssuedStatesRemainDistinctFromLinkSuccess() {for(s in listOf("unverified","inactive","refused")) {val b=HostRelayLinkBinding.parse(record(status=s),owner);assertTrue(b.issued);assertEquals(s,b.status)};fails {HostRelayLinkBinding.parse(record(status="linked"),owner)}}
    @Test fun foreignOwnerActionHomeOrMalformedFlagsCannotRecoverPrivateCode() {
        for(r in listOf(record().copy(apiScope="c".repeat(64)),record().copy(actor="other"),record().copy(profile="default"),record().copy(action="Other"),record().copy(state="unknown"),record().copy(resultSummary="{\"acknowledged\":true,\"code_issued\":\"true\",\"status\":\"pending\"}"))) fails {HostRelayLinkBinding.parse(r,owner)}
    }
    @Test fun privateCodeAndUrlsStayOutOfReceiptsAndObjectStrings() {val c=HostRelayCode.from(raw(),request(),1000);val b=HostRelayLinkBinding.parse(record(),owner);for(v in listOf(request().summary().toString(),record().resultSummary,c.toString(),b.toString())) for(secret in listOf("AB12CD","/link","t.me/fixture")) assertFalse(v.contains(secret))}
    @Test fun unsupportedOpaqueCodeCharactersCannotBecomeAPathOrCommand() {for(c in listOf("",".","..","bad/code","bad?code","bad code","code\n",String(charArrayOf(0xD800.toChar())),"x".repeat(257))) assertFalse(HostRelayLinkPolicy.code(c));for(c in listOf("AB12CD","ab_cd-123","ab.cd+1=2","用户一","1")) assertTrue(HostRelayLinkPolicy.code(c))}
}
