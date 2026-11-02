package org.hermesmobile.app.network

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

/** The mobile editor changes only these fields; all other server fields remain intact. */
object JobProtocol {
    const val MAX_PROMPT_LENGTH=5000
    val operations=setOf("create","update","pause","resume","run","delete")
    fun body(name: String,prompt: String,schedule: String,create: Boolean): JsonObject {
        require(name.isNotBlank() && name.length<=200) {"Enter a job name of at most 200 characters."}
        require(prompt.isNotBlank() && prompt.length<=MAX_PROMPT_LENGTH) {"Enter a prompt of at most 5000 characters."}
        require(schedule.length<=500 && (!create || schedule.isNotBlank())) {"Enter a schedule of at most 500 characters."}
        return buildJsonObject {
            put("name",name.trim());put("prompt",prompt)
            if(schedule.isNotBlank()) put("schedule",schedule.trim())
            if(create) {put("deliver","local");put("paused",true)}
        }
    }
    fun validate(operation: String,id: String,body: JsonObject) {
        require(operation in operations)
        if(operation!="create") OkHttpHermesApiClient.segment(id)
        if(operation in setOf("create","update")) {
            require(body.keys.all {it in setOf("name","prompt","schedule","deliver","paused")})
            JobProtocol.body(body.text("name"),body.text("prompt"),body.text("schedule"),operation=="create")
            if(operation=="create") require(body.text("deliver")=="local" && body["paused"]?.jsonPrimitive?.booleanOrNull==true)
            else require(body.keys.none {it in setOf("deliver","paused")})
        } else require(body.isEmpty())
    }
}
