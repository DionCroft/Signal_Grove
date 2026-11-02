package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.LocalDate

/** Deliberately excludes connections, credentials, conversations and server job definitions. */
object TaskTransfer {
    const val MAX_BYTES=2_000_000
    const val MAX_TASKS=5000
    fun validate(rows: List<LocalTask>) {
        require(rows.size<=MAX_TASKS && rows.map {it.id}.distinct().size==rows.size) {"Invalid task count or duplicate IDs."}
        rows.forEach {
            require(it.id.matches(Regex("[A-Za-z0-9_-]{1,120}")) && it.title.isNotBlank() && it.title.length<=500 && it.notes.length<=32000 && it.priority in 0..2) {"Invalid task fields."}
            require(it.dueDate.isEmpty() || runCatching {LocalDate.parse(it.dueDate)}.isSuccess) {"Invalid task date."}
        }
    }
    fun encode(rows: List<LocalTask>): ByteArray {
        validate(rows)
        val bytes=buildJsonObject {
            put("format","hermes-mobile-tasks");put("version",1)
            put("tasks",buildJsonArray {rows.forEach {task ->add(buildJsonObject {
                put("id",task.id);put("title",task.title);put("notes",task.notes);put("dueDate",task.dueDate);put("priority",task.priority);put("completed",task.completed)
            })}})
        }.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size<=MAX_BYTES) {"Task export exceeds the file size limit."};return bytes
    }
    fun decode(bytes: ByteArray): List<LocalTask> {
        require(bytes.size<=MAX_BYTES) {"Task import exceeds the file size limit."}
        val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        // A task file needs only three container levels. Reject deeply nested hostile JSON before parsing.
        var depth=0;var quoted=false;var escaped=false
        text.forEach {c ->
            if(quoted) {if(escaped) escaped=false else if(c=='\\') escaped=true else if(c=='"') quoted=false}
            else when(c) {'"'->quoted=true;'[','{'->{depth++;require(depth<=8) {"Task file nesting is too deep."}};']','}'->depth--}
        }
        val root=Json.parseToJsonElement(text).jsonObject
        require(root.keys==setOf("format","version","tasks") && root.text("format")=="hermes-mobile-tasks" && root["version"]?.jsonPrimitive?.intOrNull==1) {"Unsupported task file."}
        val array=root["tasks"]!!.jsonArray;require(array.size<=MAX_TASKS)
        return array.map {element ->
            val j=element.jsonObject
            require(j.keys==setOf("id","title","notes","dueDate","priority","completed"))
            fun string(key: String): String {val value=j[key]!!.jsonPrimitive;require(value.isString);return value.content}
            require(!j["priority"]!!.jsonPrimitive.isString && !j["completed"]!!.jsonPrimitive.isString)
            LocalTask(string("id"),string("title"),string("dueDate"),j["priority"]!!.jsonPrimitive.int,j["completed"]!!.jsonPrimitive.boolean,string("notes"))
        }.also(::validate)
    }
}
