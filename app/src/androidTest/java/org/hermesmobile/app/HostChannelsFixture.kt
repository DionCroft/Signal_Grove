package org.hermesmobile.app

import kotlinx.serialization.json.*

internal object HostChannelsFixture {
    const val id="Plugin.Relay:1"
    const val key="PLUGIN_BOT_TOKEN"
    const val listKey="PLUGIN_ALLOWED_USERS"
    const val secret="fresh-channel-fixture-private"
    const val stored="discarded-stored-channel-secret"
    const val users="private-user-one,private-user-two"
    const val message="private-channel-posture-message"
    fun field(key:String,password:Boolean,list:Boolean,present:Boolean,value:String?=null)=buildJsonObject {
        put("key",key);put("required",true);put("is_set",present);put("is_password",password);put("is_list",list);put("advanced",false);put("prompt",if(password) "Fixture bot token" else "Fixture allowed users");put("description","Declared fixture channel setting");put("help","Only the selected profile is changed.");put("redacted_value","discarded-preview-private");put("value",value?.let(::JsonPrimitive) ?: JsonNull)
    }
    fun catalog(enabled:Boolean=false,tokenSet:Boolean=true,allowSet:Boolean=true,allowValue:String=users,changed:Boolean=false)=buildJsonObject {
        put("env_path","discarded-private-env-path");put("gateway_start_command","discarded-private-host-command")
        put("platforms",buildJsonArray {add(buildJsonObject {
            put("id",id);put("name","Fixture channel");put("description","Fixture platform catalog entry");put("docs_url","https://docs.example.test/setup");put("enabled",enabled);put("configured",tokenSet);put("gateway_running",false);put("state",if(enabled) "pending_restart" else "disabled");put("disabled_by_relay",false);put("relay_fronted",false);put("relay_exclusive",false);put("ingress_url","https://private-callback.test/prefix/")
            put("home_channel",buildJsonObject {put("platform",id);put("chat_id","private-channel-chat");put("name","Private fixture destination");put("user_id","private-channel-user");put("scope_id","private-channel-scope");put("unknown_secret","discarded-private-home-extension")})
            put("env_vars",buildJsonArray {add(field(key,true,false,tokenSet,stored));add(JsonObject(field(listKey,false,true,allowSet,if(allowSet) allowValue else null)+if(changed) mapOf("required" to JsonPrimitive(false)) else emptyMap()))})
        })})
    }
    fun ack(hot:Boolean=false)=buildJsonObject {put("ok",true);put("platform",id);put("hot_served",hot)}
    fun posture(ok:Boolean=false)=buildJsonObject {put("ok",ok);put("state",if(ok) "connected" else "gateway_stopped");put("message",message);put("auth_posture",if(ok) "allowlist" else "deny_all")}
}
