package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

object PluginRemovalFixture {
    const val home="/private-removal-profile"
    const val key="manifest-key"
    const val install="install-folder"
    fun profiles(changed:Boolean=false)=buildJsonObject {put("profiles",buildJsonArray {add(buildJsonObject {put("name","worker");put("path",if(changed) "/changed-removal-home" else home)})})}
    fun inventory(mode:String="before"):JsonObject=buildJsonObject {
        val bundled=mode=="fallback";val count=if(mode=="absent") 0 else if(mode=="collision") 2 else 1
        put("plugins",buildJsonArray {
            if(count>0) add(buildJsonObject {put("name","Fixture manifest");put("key",key);put("source",if(bundled) "bundled" else "git");put("version",if(mode=="changed") "2" else "1");put("status","enabled");put("has_desktop_half",true);put("install_dir",if(bundled) "/bundled/fixture" else if(mode=="outside") "/outside/plugins/$install" else "$home/plugins/$install");put("servers",JsonArray(emptyList()))})
            if(mode=="collision") add(buildJsonObject {put("name",install);put("key","bundled-collision");put("version","1");put("source","bundled");put("status","enabled");put("servers",JsonArray(emptyList()))})
        });put("bundled_count",if(bundled || mode=="collision") 1 else 0);put("user_count",if(bundled) 0 else count-(if(mode=="collision") 1 else 0))
    }
    fun request()=PluginRemovalRequest(GatewayPluginInventory.parse(inventory(),"worker",PluginHome.project(JsonPrimitive(home))).plugins.single())
}
