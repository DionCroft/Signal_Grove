package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HostRelayUnlinkTest {
    private val owner=ManagementTarget("a".repeat(64),"https://host.test/prefix/","worker","password · fixture")
    private val account=HostRelayAccount("Private.User:1@platform","Private 用户","@private-user")
    private fun inventory(target:ManagementTarget=owner,home:String="b".repeat(64),manager:HostRelayManager=HostRelayManager.Connector,bot:String="https://t.me/fixture",accounts:List<HostRelayAccount> = listOf(account),mode:Boolean=true):HostRelayInventory {
        val channels=HostChannelInventory(target,home,listOf(HostChannelRow("telegram","Telegram","","",false,true,false,"stopped",false,false,true,mode,"",null,emptyList())))
        return HostRelayInventory(HostRelayScope.from(channels),manager,"https://portal.test/agents",listOf(HostRelayPlatform("telegram",bot,accounts)))
    }
    private fun request()=HostRelayUnlinkRequest(inventory(),"telegram",account)
    private fun record(state:String="unknown")=ManagementOperation("private-receipt-key",owner.resourceScope,owner.apiScope,owner.url,owner.profile,owner.actor,"RelayUnlink","telegram",request().summary().toString(),state,"",1)
    private fun fails(block:()->Unit)=assertTrue(runCatching(block).isFailure)
    @Test fun onlyConnectorAuthorityAndExactReportedAccountCanBeReviewed() {
        for(manager in listOf(HostRelayManager.Portal,HostRelayManager.Unknown)) fails {HostRelayUnlinkRequest(inventory(manager=manager),"telegram",account)}
        fails {HostRelayUnlinkRequest(inventory(),"telegram",HostRelayAccount(account.id,"different","@private-user"))};fails {HostRelayUnlinkRequest(inventory(),"missing",account)}
    }
    @Test fun preflightBindsOriginalOwnerHomeModeAuthorityBotAndAccountMetadata() {
        val r=request();assertTrue(r.same(inventory()))
        for(changed in listOf(inventory(target=owner.copy(actor="other")),inventory(target=owner.copy(apiScope="c".repeat(64))),inventory(home="c".repeat(64)),inventory(mode=false),inventory(manager=HostRelayManager.Portal),inventory(bot="https://t.me/changed"),inventory(accounts=emptyList()),inventory(accounts=listOf(HostRelayAccount(account.id,"changed",account.handle))))) assertFalse(r.same(changed))
        assertTrue(r.same(inventory(accounts=listOf(account,HostRelayAccount("new-other","Other","")))))
    }
    @Test fun laterAbsenceIsSeparateFromTheKnownAcknowledgement() {
        val r=request();assertEquals(true,r.observedAbsent(inventory(accounts=emptyList())));assertEquals(false,r.observedAbsent(inventory()));assertNull(r.observedAbsent(inventory(manager=HostRelayManager.Portal)));assertNull(r.observedAbsent(inventory(bot="https://t.me/changed")))
    }
    @Test fun onlyTypedUnlinkedTrueAcknowledgesTheRouteWithoutInventedEcho() {
        request().acknowledge(buildJsonObject {put("unlinked",true)})
        for(raw in listOf(buildJsonObject {put("ok",true)},buildJsonObject {put("unlinked","true")},buildJsonObject {put("unlinked",false)},buildJsonObject {put("unlinked",1)})) fails {request().acknowledge(raw)}
    }
    @Test fun literalSingleSegmentIdsKeepUnicodeDotsColonsAndAtSigns() {
        for(id in listOf("-12345","User.Name:1@platform","用户一")) assertTrue(HostRelayUnlinkRequest.addressable(id))
        for(id in listOf("",".",".."," x","x ","x/y","x\\y","x?y","x#y","x\ny","x".repeat(513))) assertFalse(id,HostRelayUnlinkRequest.addressable(id))
    }
    @Test fun receiptSummariesContainNoPrivateAccountOrBotDataOrValueHashes() {
        val r=request();assertEquals(setOf("platform","home_id","account_count"),r.summary().keys)
        for(secret in listOf(account.id,account.name,account.handle,"t.me","portal.test")) assertFalse(r.summary().toString().contains(secret))
        assertFalse(r.toString().contains(account.id))
    }
    @Test fun privateBindingChecksOriginalResourceActorCredentialProfileAndHome() {
        val original=record();val b=HostRelayUnlinkBinding.parse(original,owner);assertEquals("b".repeat(64),b.homeId)
        for(changed in listOf(original.copy(apiScope="c".repeat(64)),original.copy(actor="other"),original.copy(profile="default"),original.copy(url="https://other.test/"),original.copy(action="Other"),original.copy(target="discord"),original.copy(resourceScope="wrong"),original.copy(state="confirmed"))) fails {HostRelayUnlinkBinding.parse(changed,owner)}
        assertFalse(b.toString().contains("private-receipt"))
    }
    @Test fun privateBindingsRequireTheExactTypedSummaryAndAllowKnownHistoricalStates() {
        for(state in listOf("sending","unknown","acknowledged","reviewed")) HostRelayUnlinkBinding.parse(record(state),owner)
        val r=record();val j=Json.parseToJsonElement(r.requestSummary).jsonObject
        for(change in listOf(j+mapOf("account_count" to JsonPrimitive("1")),j+mapOf("home_id" to JsonPrimitive("not-a-fingerprint")),j+mapOf("account_id" to JsonPrimitive(account.id)),j-"platform")) fails {HostRelayUnlinkBinding.parse(r.copy(requestSummary=JsonObject(change).toString()),owner)}
    }
}
