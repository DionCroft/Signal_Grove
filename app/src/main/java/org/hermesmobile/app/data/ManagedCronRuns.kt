package org.hermesmobile.app.data

import kotlinx.serialization.json.*

class InvalidCronRunsReply:Exception()

/** Bounded display projection only; rows never grant chat, worker or mutation authority. */
class ManagedCronRun private constructor(
    val id:String?, val source:String?, val title:String?, val preview:String?,
    val startedAt:Double?, val lastActive:Double?, val active:Boolean?,
    val model:String?, val messageCount:Long?, val toolCallCount:Long?
) {
    val scriptOutput get()=source=="cron_output"
    val conversation get()=source in setOf("cron","local") && id!=null
    val hasPrivateText get()=listOf(id,title,preview,model).any {!it.isNullOrBlank()}
    override fun toString()="ManagedCronRun(redacted)"
    companion object {
        fun parse(row:JsonObject,profile:String):ManagedCronRun {
            require(row["profile"]==JsonPrimitive(profile)) {"Run owner profile is unverified."}
            fun text(key:String,max:Int,multiline:Boolean=false):String? {
                val value=row[key]?.takeUnless {it is JsonNull} ?: return null
                require(value is JsonPrimitive && value.isString && value.content.length<=max)
                require(value.content.none {it.isISOControl() && !(multiline && it in "\n\r\t")})
                return value.content.takeIf {it.isNotBlank()}
            }
            fun time(key:String):Double? {
                val value=row[key]?.takeUnless {it is JsonNull} ?: return null
                require(value is JsonPrimitive && !value.isString)
                val number=value.doubleOrNull ?: error("Invalid run time.")
                require(number.isFinite() && number>=0 && number<=253402300799.0)
                return number
            }
            fun count(key:String):Long? {
                val value=row[key]?.takeUnless {it is JsonNull} ?: return null
                require(value is JsonPrimitive && !value.isString)
                return (value.longOrNull ?: error("Invalid run count.")).also {require(it>=0)}
            }
            val active=row["is_active"]?.takeUnless {it is JsonNull}?.let {require(it==JsonPrimitive(true) || it==JsonPrimitive(false));it.jsonPrimitive.boolean}
            val id=text("id",512)
            return ManagedCronRun(id,text("source",64),text("title",1024),text("preview",8192,true),time("started_at"),time("last_active"),active,text("model",512),count("message_count"),count("tool_call_count"))
        }
    }
}

class ManagedCronRuns private constructor(val profile:String,val jobId:String,val limit:Int,val rows:List<ManagedCronRun>) {
    override fun toString()="ManagedCronRuns(${rows.size} records, redacted)"
    companion object {
        fun parse(value:JsonObject,profile:String,jobId:String,limit:Int=20):ManagedCronRuns=try {
            require(profile.isNotBlank() && profile.length<=128 && !profile.trim().equals("all",true) && profile.none(Char::isISOControl))
            require(jobId!="unknown" && jobId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}")) && limit in 1..100)
            require(value.toString().length<=262144)
            val reported=value["limit"] as? JsonPrimitive
            require(reported!=null && !reported.isString && reported.intOrNull==limit)
            val rows=value["runs"] as? JsonArray ?: error("Missing run records.")
            require(rows.size<=limit)
            val parsed=rows.map {ManagedCronRun.parse(it as? JsonObject ?: error("Invalid run row."),profile)}
            val ids=parsed.mapNotNull {it.id};require(ids.distinct().size==ids.size)
            ManagedCronRuns(profile,jobId,limit,java.util.Collections.unmodifiableList(parsed))
        } catch(e:Exception) {throw InvalidCronRunsReply()}
    }
}
