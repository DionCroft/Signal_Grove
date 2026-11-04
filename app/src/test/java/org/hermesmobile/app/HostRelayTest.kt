package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HostRelayTest {
    private val owner=ManagementTarget("a".repeat(64),"https://host.test/prefix/","worker","password · fixture")
    private fun scope(fronted:Boolean=true,home:String="home",target:ManagementTarget=owner,exclusive:Boolean=true):HostRelayScope {
        val row=HostChannelRow("telegram","Telegram","","",false,true,false,"stopped",false,true,fronted,exclusive,"",null,emptyList())
        return HostRelayScope.from(HostChannelInventory(target,home,listOf(row)))
    }
    private fun account(id:JsonElement=JsonPrimitive("private-id"))=buildJsonObject {put("platformUserId",id);put("displayName","Private 名称");put("handle","@private");put("secret","discard-me")}
    private fun raw(accounts:JsonArray=buildJsonArray {add(account())},manager:JsonElement=JsonPrimitive("portal"),url:String="https://portal.test/operator/agents")=buildJsonObject {put("managed_by",manager);put("portal_url",url);put("platforms",buildJsonArray {add(buildJsonObject {put("platform","telegram");put("accounts",accounts);put("bot_url",JsonNull)})});put("secret","discard-raw")}
    private fun fails(block:()->Unit)=assertTrue(runCatching(block).isFailure)
    @Test fun launchHomeNeedsPositiveRelayMetadata() {assertTrue(runCatching {scope(false)}.exceptionOrNull() is HostRelayScopeUnavailable)}
    @Test fun scopeBindsCredentialActorProfileHomeAndMode() {
        val s=scope();assertTrue(s.same(scope()));assertFalse(s.same(scope(home="other")));assertFalse(s.same(scope(target=owner.copy(actor="other"))));assertFalse(s.same(scope(target=owner.copy(apiScope="b".repeat(64)))));assertFalse(s.same(scope(exclusive=false)))
    }
    @Test fun privateProjectionIsBoundedAndRedacted() {
        val inventory=HostRelayInventory.parse(raw(),scope());val p=inventory.platforms.single();val a=p.accounts.single();assertEquals("private-id",a.id);assertEquals("Private 名称",a.name);assertEquals("@private",a.handle);assertEquals(1,inventory.accountCount)
        for(v in listOf(inventory.toString(),p.toString(),a.toString(),inventory.scope.toString())) for(secret in listOf("private-id","Private","@private","discard-raw","portal.test")) assertFalse(v.contains(secret))
        fails {(p.accounts as MutableList).clear()};fails {(inventory.platforms as MutableList).clear()};fails {(inventory.scope.modes as MutableMap).clear()}
    }
    @Test fun duplicateAccountsAndPlatformsAreRefusedWithoutTruncation() {
        fails {HostRelayInventory.parse(raw(buildJsonArray {add(account());add(account())}),scope())}
        val r=raw();fails {HostRelayInventory.parse(JsonObject(r+mapOf("platforms" to JsonArray(r["platforms"]!!.jsonArray.toList()+r["platforms"]!!.jsonArray.toList()))),scope())}
    }
    @Test fun typedIdsAndCollectionsAreMandatory() {
        for(id in listOf(JsonNull,JsonPrimitive(123),JsonPrimitive(""),JsonPrimitive("bad\nidentity"),JsonPrimitive("x".repeat(513)))) fails {HostRelayInventory.parse(raw(buildJsonArray {add(account(id))}),scope())}
        fails {HostRelayInventory.parse(JsonObject(raw()+mapOf("platforms" to JsonPrimitive("[]"))),scope())}
    }
    @Test fun unprovenOrMissingPlatformSetsAreRefused() {
        val r=raw();fails {HostRelayInventory.parse(JsonObject(r+mapOf("platforms" to JsonArray(emptyList()))),scope())}
        val p=r["platforms"]!!.jsonArray.single().jsonObject;fails {HostRelayInventory.parse(JsonObject(r+mapOf("platforms" to buildJsonArray {add(JsonObject(p+mapOf("platform" to JsonPrimitive("discord"))))})),scope())}
    }
    @Test fun unknownAndMissingAuthorityRemainReadOnly() {
        for(manager in listOf(JsonNull,JsonPrimitive("unknown"),JsonPrimitive("future-manager"))) assertEquals(HostRelayManager.Unknown,HostRelayInventory.parse(raw(manager=manager),scope()).manager)
        fails {HostRelayInventory.parse(raw(manager=JsonPrimitive(1)),scope())}
    }
    @Test fun accountCountLimitsAndPrivateSizeRefuseWholeReport() {
        fails {HostRelayInventory.parse(raw(JsonArray((0..512).map {account(JsonPrimitive("id-$it"))})),scope())}
        fails {HostRelayInventory.parse(raw(JsonArray((0..510).map {JsonObject(account(JsonPrimitive("id-$it"))+mapOf("displayName" to JsonPrimitive("a".repeat(512)),"handle" to JsonPrimitive("b".repeat(512))))})),scope())}
    }
    @Test fun portalRequiresSameScopeAuthorityAndDestination() {
        val s=scope();val expected=HostRelayInventory.parse(raw(),s);assertTrue(expected.samePortal(HostRelayInventory.parse(raw(),s)));assertFalse(expected.samePortal(HostRelayInventory.parse(raw(manager=JsonPrimitive("connector")),s)));assertFalse(expected.samePortal(HostRelayInventory.parse(raw(url="https://changed.test/agents"),s)));assertFalse(expected.samePortal(HostRelayInventory.parse(raw(),scope(home="other"))))
    }
    @Test fun onlySafeHttpsPortalDestinationsCanOpen() {
        assertTrue(HostRelayPolicy.portal("https://portal.test/prefix/agents"));assertTrue(HostRelayPolicy.portal("https://portal.test:8443/agents"))
        for(url in listOf("http://portal.test/agents","javascript:alert(1)","https://user:pass@portal.test/agents","https://portal.test/agents?code=secret","https://portal.test/agents#secret","https://portal.test:0/agents","https://portal.test:65536/agents","https://portal.test/\n")) assertFalse(url,HostRelayPolicy.portal(url))
    }
}
