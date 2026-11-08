package org.hermesmobile.app.data

import java.util.Collections
import kotlinx.serialization.json.*

internal class HostedRoomsUnsupported:Exception("Unsupported hosted-room response.")
internal data class HostedRoomScope(val owner:ManagementTarget,val homeId:String) {
    init {GatewayCronInventory.requireProfile(owner.profile);require(homeId.matches(Regex("[a-f0-9]{64}")))}
    override fun toString()="HostedRoomScope(redacted)"
}
internal data class HostedRoomMember(val id:String?,val profile:String?,val handle:String?,val displayName:String?,val targetKind:String?) {
    override fun toString()="HostedRoomMember(redacted)"
}
internal data class HostedRoom(val id:String,val name:String,val members:List<HostedRoomMember>,val authorityId:String,val authorityEpoch:Long,val revision:Long,val createdAtSeconds:Double,val updatedAtSeconds:Double,val disbandedAtSeconds:Double?,val latestSequence:Long?) {
    override fun toString()="HostedRoom(redacted)"
}
internal data class HostedRoomCapabilities(val scope:HostedRoomScope,val protocolVersion:Int,val driver:Boolean,val persistentProcess:Boolean,val authorityId:String,val methods:Set<String>,val features:Set<String>,val seatableProfiles:List<String>?) {
    // Advertisement is an observation, never a grant or proof of worker readiness.
    val hasReadMethods get()=methods.containsAll(setOf("groups.list","groups.state"))
    override fun toString()="HostedRoomCapabilities(redacted)"
}
internal data class HostedRoomPage(val scope:HostedRoomScope,val rooms:List<HostedRoom>,val offset:Int,val nextOffset:Int?,val observedAt:Long) {
    override fun toString()="HostedRoomPage(redacted)"
}
internal data class HostedRoomContext(val purpose:String,val notes:String,val routing:String,val leadMemberId:String) {
    override fun toString()="HostedRoomContext(redacted)"
}
internal data class HostedRoomDriverObservation(val running:Boolean,val working:Boolean,val blocked:Boolean) {
    override fun toString()="HostedRoomDriverObservation(redacted)"
}
internal data class HostedRoomDetail(val scope:HostedRoomScope,val room:HostedRoom,val context:HostedRoomContext?,val driver:HostedRoomDriverObservation?,val observedAt:Long) {
    override fun toString()="HostedRoomDetail(redacted)"
}

/** Pure bounded public-field projection. No network, credentials, raw targets, grants or control calls. */
internal object HostedRoomProtocol {
    fun id(value:String)=value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))
    private fun <T> frozen(values:List<T>):List<T> = Collections.unmodifiableList(values)
    private inline fun <T> decode(block:()->T):T=try {block()} catch(_:Exception) {throw HostedRoomsUnsupported()}
    private fun text(j:JsonObject,key:String,max:Int,optional:Boolean=false,multiline:Boolean=false):String? {
        val v=j[key];if(v==null || v is JsonNull) {require(optional);return null}
        require(v is JsonPrimitive && v.isString)
        return v.content.also {s->require(s.codePointCount(0,s.length)<=max && s.none {it.isISOControl() && !(multiline && it in "\n\r\t")} && !s.codePoints().anyMatch {it in 0xD800..0xDFFF})}
    }
    private fun integer(j:JsonObject,key:String,min:Long=0,optional:Boolean=false):Long? {
        val v=j[key];if(v==null || v is JsonNull) {require(optional);return null}
        require(v is JsonPrimitive && !v.isString);return v.longOrNull!!.also {require(it>=min)}
    }
    private fun time(j:JsonObject,key:String,optional:Boolean=false):Double? {
        val v=j[key];if(v==null || v is JsonNull) {require(optional);return null}
        require(v is JsonPrimitive && !v.isString)
        return v.doubleOrNull!!.also {require(it.isFinite() && it>=0 && it<=Long.MAX_VALUE/1000.0)}
    }
    private fun bool(j:JsonObject,key:String):Boolean {
        val v=j[key];require(v is JsonPrimitive && !v.isString);return checkNotNull(v.booleanOrNull)
    }
    private fun names(j:JsonObject,key:String,max:Int):List<String> {
        val values=j[key] as JsonArray;require(values.size<=max)
        val result=values.map {v->require(v is JsonPrimitive && v.isString);v.content.also {require(it.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")))}}
        require(result.distinct().size==result.size);return frozen(result)
    }
    private fun room(j:JsonObject):HostedRoom {
        val roomId=text(j,"room_id",128)!!.also {require(id(it))};val name=text(j,"name",200)!!.also {require(it.isNotBlank())}
        val rows=j["members"] as JsonArray;require(rows.size<=128)
        val members=rows.map {value->val m=value as JsonObject;val target=m["target"]
            require(target==null || target is JsonNull || target is JsonObject)
            HostedRoomMember(text(m,"member_id",128,true),text(m,"profile",128,true),text(m,"handle",128,true),text(m,"display_name",200,true),if(target is JsonObject) text(target,"kind",32,true) else null)
        }
        return HostedRoom(roomId,name,frozen(members),text(j,"authority_gateway_id",128)!!.also {require(id(it))},integer(j,"authority_epoch",1)!!,integer(j,"revision")!!,time(j,"created_at")!!,time(j,"updated_at")!!,time(j,"disbanded_at",true),integer(j,"latest_seq",optional=true))
    }
    fun capabilities(j:JsonObject,scope:HostedRoomScope):HostedRoomCapabilities=decode {
        val version=integer(j,"protocol_version",1)!!;require(version==2L)
        val methods=names(j,"methods",128);val features=names(j,"features",128)
        val seats=if(j["seatable_profiles"]==null || j["seatable_profiles"] is JsonNull) null else names(j,"seatable_profiles",500)
        HostedRoomCapabilities(scope,2,bool(j,"driver"),bool(j,"persistent_process"),text(j,"authority_gateway_id",128)!!.also {require(id(it))},Collections.unmodifiableSet(methods.toSet()),Collections.unmodifiableSet(features.toSet()),seats)
    }
    fun page(j:JsonObject,scope:HostedRoomScope,offset:Int,observedAt:Long=System.currentTimeMillis()):HostedRoomPage=decode {
        require(offset>=0 && observedAt>=0);val rows=j["rooms"] as JsonArray
        // The forwarding server merges up to three stores on page zero, independent of requested limit.
        require(rows.size<=(if(offset==0) 1500 else 500));val rooms=rows.map {room(it as JsonObject)}
        require(rooms.map {it.id}.distinct().size==rooms.size)
        val next=integer(j,"next_offset",optional=true)?.also {require(it>offset && it<=Int.MAX_VALUE)}?.toInt()
        // Actor-filtered offsets are opaque server continuation cursors, not offset+visible row count.
        HostedRoomPage(scope,frozen(rooms),offset,next,observedAt)
    }
    fun detail(j:JsonObject,scope:HostedRoomScope,requestedId:String,observedAt:Long=System.currentTimeMillis()):HostedRoomDetail=decode {
        require(id(requestedId) && observedAt>=0);val room=room(j["room"] as JsonObject);require(room.id==requestedId)
        val context=j["context"].let {v->if(v==null || v is JsonNull) null else {
            val c=v as JsonObject;val purpose=text(c,"purpose",500,multiline=true)!!;val notes=text(c,"notes",4096,multiline=true)!!;require(notes.toByteArray(Charsets.UTF_8).size<=4096)
            val routing=text(c,"routing",32)!!.also {require(it in setOf("everyone","lead","auto"))}
            HostedRoomContext(purpose,notes,routing,text(c,"lead_member_id",128)!!)
        }}
        val driver=j["driver_status"].let {v->if(v==null || v is JsonNull) null else (v as JsonObject).let {HostedRoomDriverObservation(bool(it,"running"),bool(it,"working"),bool(it,"blocked"))}}
        HostedRoomDetail(scope,room,context,driver,observedAt)
    }
}
