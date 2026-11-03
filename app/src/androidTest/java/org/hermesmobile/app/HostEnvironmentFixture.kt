package org.hermesmobile.app

import kotlinx.serialization.json.*

object HostEnvironmentFixture {
    const val key="FIXTURE_API_KEY"
    const val secret="private-credential-replacement"
    const val mask="private-credential-mask"
    fun inventory(present:Boolean=true,changed:Boolean=false,newKey:Boolean=false)=buildJsonObject {
        fun row(set:Boolean)=buildJsonObject {put("is_set",set);put("is_password",true);put("description",if(changed) "Changed metadata" else "Fixture credential");put("category","provider");put("custom",false);put("channel_managed",false);put("provider","fixture-provider");put("provider_label","Fixture provider");put("provider_primary",true);put("tools",buildJsonArray {add("fixture_tool")});put("redacted_value",mask);put("value","private-unexpected-old-value");put("url","https://private.example/?secret=private-url")}
        put(key,row(present));if(newKey) put("NEW_FIXTURE_KEY",row(true))
    }
    fun accepted(key:String,remove:Boolean=false,malformed:Boolean=false)=buildJsonObject {put("ok",true);put("key",key);if(remove) {put("found",true);put("removed",false);put("pool_pruned",buildJsonArray {add("fixture-provider")});put("config_scrubbed",buildJsonArray {add("private-mirror-path")})} else put("config_updates",if(malformed) JsonPrimitive("private-malformed-path") else buildJsonArray {add("private-mirror-path")})}
}
