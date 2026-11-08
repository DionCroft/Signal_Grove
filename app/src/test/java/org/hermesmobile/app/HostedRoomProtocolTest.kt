package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HostedRoomProtocolTest {
    private val scope=HostedRoomScope(ManagementTarget("a".repeat(64),"https://hermes.test/prefix/dashboard/","default","fixture · owner"),"b".repeat(64))
    private fun room(id:String="room-1")=buildJsonObject {
        put("room_id",id);put("name","Fixture room");put("members",JsonArray(emptyList()));put("authority_gateway_id","gateway-1")
        put("authority_epoch",1);put("revision",0);put("created_at",1.25);put("updated_at",2.75)
    }
    private fun page(rows:List<JsonObject> = listOf(room()),next:JsonElement=JsonNull)=buildJsonObject {put("rooms",JsonArray(rows));put("next_offset",next)}
    private fun caps()=buildJsonObject {
        put("protocol_version",2);put("driver",false);put("persistent_process",true);put("authority_gateway_id","gateway-1")
        put("methods",JsonArray(listOf("groups.list","groups.state").map(::JsonPrimitive)));put("features",JsonArray(listOf(JsonPrimitive("room_identity"))))
    }
    private fun change(j:JsonObject,key:String,value:JsonElement)=JsonObject(j+mapOf(key to value))
    private fun detail(row:JsonObject=room(),context:JsonElement=JsonNull,driver:JsonElement=JsonNull)=buildJsonObject {put("room",row);put("context",context);put("driver_status",driver)}
    private fun context(notes:String="",routing:String="everyone")=buildJsonObject {put("purpose","");put("notes",notes);put("routing",routing);put("lead_member_id","")}
    private fun refused(block:()->Unit) {try {block();fail("Expected unsupported room response.")} catch(_:HostedRoomsUnsupported) {}}

    @Test fun missingDriverDoesNotEraseReadMethodsOrAssertReadiness() {
        val c=HostedRoomProtocol.capabilities(caps(),scope);assertFalse(c.driver);assertTrue(c.persistentProcess);assertTrue(c.hasReadMethods)
    }
    @Test fun anUnknownAdvertisedMethodCannotSupplyMissingReadMethods() {
        val c=HostedRoomProtocol.capabilities(change(caps(),"methods",JsonArray(listOf(JsonPrimitive("groups.future")))),scope)
        assertFalse(c.hasReadMethods);assertEquals(setOf("groups.future"),c.methods)
    }
    @Test fun futureProtocolVersionsAreHeldInsteadOfGuessed() {
        for(v in listOf(JsonPrimitive(1),JsonPrimitive(3),JsonPrimitive("2"),JsonPrimitive(true))) refused {HostedRoomProtocol.capabilities(change(caps(),"protocol_version",v),scope)}
    }
    @Test fun capabilityBooleansAreNotCoercedFromNumbersOrText() {
        for(v in listOf(JsonPrimitive(1),JsonPrimitive("false"),JsonNull)) refused {HostedRoomProtocol.capabilities(change(caps(),"driver",v),scope)}
    }
    @Test fun omittedSeatabilityDiffersFromAnExplicitEmptyRoster() {
        assertNull(HostedRoomProtocol.capabilities(caps(),scope).seatableProfiles)
        assertEquals(emptyList<String>(),HostedRoomProtocol.capabilities(change(caps(),"seatable_profiles",JsonArray(emptyList())),scope).seatableProfiles)
    }
    @Test fun duplicateAdvertisedMethodsAreRejected() {
        refused {HostedRoomProtocol.capabilities(change(caps(),"methods",JsonArray(List(2) {JsonPrimitive("groups.list")})),scope)}
    }
    @Test fun mergedFirstPagesCanContainMoreRowsThanOneStorePage() {
        val result=HostedRoomProtocol.page(page((0..599).map {room("room-$it")},JsonPrimitive(100)),scope,0,9)
        assertEquals(600,result.rooms.size);assertEquals(100,result.nextOffset);assertEquals(9L,result.observedAt)
    }
    @Test fun actorFilteredContinuationIsNotRecomputedFromVisibleRows() {
        val result=HostedRoomProtocol.page(page(listOf(room()),JsonPrimitive(731)),scope,200)
        assertEquals(731,result.nextOffset);assertEquals(200,result.offset)
    }
    @Test fun malformedOrNonAdvancingCursorsAreRefused() {
        for(v in listOf(JsonPrimitive(200),JsonPrimitive(-1),JsonPrimitive("731"),JsonPrimitive(true),JsonPrimitive(731.5),JsonPrimitive(Long.MAX_VALUE))) refused {HostedRoomProtocol.page(page(next=v),scope,200)}
    }
    @Test fun laterPagesCannotUseTheFirstPageMergeAllowance() {
        refused {HostedRoomProtocol.page(page((0..500).map {room("r-$it")}),scope,100)}
    }
    @Test fun firstPageBoundsAndDuplicateRoomIdentityAreEnforced() {
        refused {HostedRoomProtocol.page(page(List(1501) {room("r-$it")}),scope,0)}
        refused {HostedRoomProtocol.page(page(listOf(room(),room())),scope,0)}
    }
    @Test fun invalidRoomAndAuthorityIdentifiersAreRefused() {
        for(value in listOf("../room"," room","房间","r".repeat(129))) refused {HostedRoomProtocol.page(page(listOf(room(value))),scope,0)}
        refused {HostedRoomProtocol.page(page(listOf(change(room(),"authority_gateway_id",JsonPrimitive("../host")))),scope,0)}
    }
    @Test fun decimalTimestampsArePreservedWithoutIntegerCoercion() {
        val room=HostedRoomProtocol.page(page(),scope,0).rooms.single();assertEquals(1.25,room.createdAtSeconds,0.0);assertEquals(2.75,room.updatedAtSeconds,0.0)
    }
    @Test fun invalidTimestampTypesAndUnboundedValuesAreRefused() {
        for(v in listOf(JsonPrimitive("1.25"),JsonPrimitive(true),JsonPrimitive(-0.25),JsonPrimitive(Double.NaN),JsonPrimitive(1e100))) refused {HostedRoomProtocol.page(page(listOf(change(room(),"updated_at",v))),scope,0)}
    }
    @Test fun rawMemberTargetsAndGrantSecretsNeverEnterTheProjection() {
        val member=buildJsonObject {put("profile","legacy");put("target",buildJsonObject {put("kind","peer");put("grant","fixture-private-grant");put("target_url","https://private.invalid/")});put("private","fixture-private-body")}
        val r=HostedRoomProtocol.page(page(listOf(change(room(),"members",JsonArray(listOf(member))))),scope,0).rooms.single()
        assertEquals("legacy",r.members.single().profile);assertEquals("peer",r.members.single().targetKind);assertNull(r.members.single().id)
        assertFalse(r.toString().contains("private"));assertEquals("HostedRoomMember(redacted)",r.members.single().toString())
    }
    @Test fun contextUsesUtf8ByteBoundsForMultibyteNotes() {
        val accepted="界".repeat(1365);assertEquals(accepted,HostedRoomProtocol.detail(detail(context=context(accepted)),scope,"room-1").context!!.notes)
        refused {HostedRoomProtocol.detail(detail(context=context("界".repeat(1366))),scope,"room-1")}
    }
    @Test fun unicodeNameBoundsCountCodePointsAndRejectBrokenSurrogates() {
        val emoji=String(Character.toChars(0x1F33F));val name=emoji.repeat(200)
        assertEquals(name,HostedRoomProtocol.page(page(listOf(change(room(),"name",JsonPrimitive(name)))),scope,0).rooms.single().name)
        for(value in listOf(emoji.repeat(201),"broken"+0xD800.toChar())) refused {HostedRoomProtocol.page(page(listOf(change(room(),"name",JsonPrimitive(value)))),scope,0)}
    }
    @Test fun detailCannotSubstituteAnotherRoomForTheRequestedId() {
        refused {HostedRoomProtocol.detail(detail(room("other")),scope,"room-1")}
        assertEquals(scope,HostedRoomProtocol.detail(detail(),scope,"room-1").scope)
    }
    @Test fun unavailableContextAndDriverRemainUnknown() {
        val result=HostedRoomProtocol.detail(detail(),scope,"room-1");assertNull(result.context);assertNull(result.driver)
        val archived=HostedRoomProtocol.detail(detail(change(room(),"disbanded_at",JsonPrimitive(3.5))),scope,"room-1");assertEquals(3.5,archived.room.disbandedAtSeconds!!,0.0)
    }
    @Test fun unknownRoutingAndMalformedDriverObservationsAreRefused() {
        refused {HostedRoomProtocol.detail(detail(context=context(routing="future")),scope,"room-1")}
        refused {HostedRoomProtocol.detail(detail(driver=buildJsonObject {put("running","false");put("working",false);put("blocked",false)}),scope,"room-1")}
    }
    @Test fun memberCountAndAuthorityNumbersRemainBoundedAndTyped() {
        val member=buildJsonObject {put("profile","legacy")}
        refused {HostedRoomProtocol.page(page(listOf(change(room(),"members",JsonArray(List(129) {member})))),scope,0)}
        for(key in listOf("authority_epoch","revision","latest_seq")) refused {HostedRoomProtocol.page(page(listOf(change(room(),key,JsonPrimitive("1")))),scope,0)}
        refused {HostedRoomProtocol.page(page(listOf(change(room(),"authority_epoch",JsonPrimitive(0)))),scope,0)}
    }
    @Test fun crossProfileAndHomeBindingsStayExplicitAndCollectionsAreImmutable() {
        val other=HostedRoomScope(scope.owner.copy(profile="other"),"c".repeat(64));val a=HostedRoomProtocol.page(page(),scope,0);val b=HostedRoomProtocol.page(page(),other,0)
        assertNotEquals(a.scope,b.scope);assertEquals(scope,a.scope);assertEquals(other,b.scope)
        try {(a.rooms as MutableList).clear();fail("Mutable room page")} catch(_:UnsupportedOperationException) {}
        for(profile in listOf("all","ALL")) try {HostedRoomScope(scope.owner.copy(profile=profile),scope.homeId);fail("Unscoped profile")} catch(_:IllegalArgumentException) {}
    }
}
