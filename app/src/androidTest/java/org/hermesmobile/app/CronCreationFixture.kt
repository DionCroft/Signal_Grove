package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.text

internal object CronCreationFixture {
    fun schema(paused:Boolean=true)=buildJsonObject {
        put("info",buildJsonObject {put("version","0.22.6")})
        put("paths",buildJsonObject {put("/api/cron/jobs",buildJsonObject {put("post",buildJsonObject {
            put("parameters",JsonArray(listOf(buildJsonObject {put("name","profile");put("in","query");put("schema",buildJsonObject {put("type","string")})})))
            put("requestBody",buildJsonObject {put("content",buildJsonObject {put("application/json",buildJsonObject {put("schema",buildJsonObject {put("\$ref","#/components/schemas/CronJobCreate")})})})})
        })})})
        put("components",buildJsonObject {put("schemas",buildJsonObject {put("CronJobCreate",buildJsonObject {
            put("required",JsonArray(listOf(JsonPrimitive("schedule"))))
            put("properties",buildJsonObject {
                for(key in listOf("name","schedule","prompt","deliver","model","provider","base_url","script","workdir","timezone","paused_reason")) put(key,buildJsonObject {put("type","string")})
                for(key in listOf("skills","context_from","enabled_toolsets")) put(key,buildJsonObject {put("type","array");put("items",buildJsonObject {put("type","string")})})
                for(key in listOf("no_agent","catch_up")) put(key,buildJsonObject {put("type","boolean")})
                for(key in listOf("repeat","grace_minutes")) put(key,buildJsonObject {put("type","integer")})
                if(paused) put("paused",buildJsonObject {put("type","boolean");put("default",false)})
            })
        })})})
    }
    fun job(body:JsonObject,active:Boolean=false)=buildJsonObject {
        put("id","created-job-fixture");put("profile","worker");put("profile_name","worker");put("name",body.text("name"));put("prompt",body.text("prompt"));put("deliver",body.text("deliver"))
        put("enabled",active);put("state",if(active) "scheduled" else "paused");put("next_run_at",JsonNull);put("created_at","2026-11-02T00:00:00+00:00")
        put("schedule",buildJsonObject {put("kind","cron");put("expr",body.text("schedule"));put("display",body.text("schedule"))});put("schedule_display",body.text("schedule"))
        put("repeat",buildJsonObject {put("times",body["repeat"] ?: JsonNull);put("completed",0)})
        put("no_agent",body["no_agent"] ?: JsonPrimitive(false));put("skills",body["skills"] ?: JsonArray(emptyList()))
        for(key in listOf("model","provider","base_url","script","workdir","timezone","paused_reason","context_from","enabled_toolsets","grace_minutes","catch_up")) put(key,body[key] ?: JsonNull)
    }
}
