package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

/** Android runtime checks for the pure room foundation; no activities, network or saved credentials. */
class HostedRoomProjectionAndroidTest {
    private val scope=HostedRoomScope(ManagementTarget("d".repeat(64),"https://hermes.test/dashboard/","default","fixture · owner"),"e".repeat(64))
    private fun room(id:String="room-1",name:String="Fixture room")=buildJsonObject {
        put("room_id",id);put("name",name);put("members",JsonArray(emptyList()));put("authority_gateway_id","gateway-1")
        put("authority_epoch",1);put("revision",0);put("created_at",1.25);put("updated_at",2.75)
    }
    @Test fun decimalRoomTimesAndOpaqueFilteredCursorSurviveAndroidJson() {
        val json=buildJsonObject {put("rooms",JsonArray(listOf(room())));put("next_offset",731)}
        val result=HostedRoomProtocol.page(Json.parseToJsonElement(json.toString()).jsonObject,scope,200)
        assertEquals(731,result.nextOffset);assertEquals(1.25,result.rooms.single().createdAtSeconds,0.0);assertEquals(scope,result.scope)
    }
    @Test fun mergedFirstPageIsNotMistakenForAnInvalidSingleStorePage() {
        val json=buildJsonObject {put("rooms",JsonArray((0..599).map {room("r-$it")}));put("next_offset",100)}
        val result=HostedRoomProtocol.page(json,scope,0);assertEquals(600,result.rooms.size);assertEquals(100,result.nextOffset)
    }
    @Test fun supplementaryUnicodeAndUtf8NotesKeepTheirDistinctBoundsOnAndroid() {
        val name=String(Character.toChars(0x1F33F)).repeat(200);val notes="界".repeat(1365)
        fun detail(value:String)=buildJsonObject {put("room",room(name=name));put("context",buildJsonObject {put("purpose","");put("notes",value);put("routing","everyone");put("lead_member_id","")})}
        val result=HostedRoomProtocol.detail(detail(notes),scope,"room-1");assertEquals(name,result.room.name);assertEquals(notes,result.context!!.notes)
        try {HostedRoomProtocol.detail(detail(notes+"界"),scope,"room-1");fail("Oversized UTF8 notes accepted")} catch(_:HostedRoomsUnsupported) {}
    }
}
