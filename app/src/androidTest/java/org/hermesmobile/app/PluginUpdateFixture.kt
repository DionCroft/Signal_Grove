package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

object PluginUpdateFixture {
    const val home="/private-update-profile"
    const val key="manifest-key"
    const val install="install-folder"
    val pin="b".repeat(40)
    fun profiles(changed:Boolean=false)=buildJsonObject {put("profiles",buildJsonArray {add(buildJsonObject {put("name","worker");put("path",if(changed) "/changed-update-home" else home)})})}
    fun catalog(changed:Boolean=false,origin:Boolean=false)=buildJsonObject {put("status","found");put("entry",buildJsonObject {put("name","fixture-plugin");put("repo",if(origin) "https://github.com/example/changed" else "https://github.com/example/plugin");put("sha",if(changed) "c".repeat(40) else pin);put("subdir","plugin");put("tier","community")})}
    fun inventory(catalog:Boolean=false,after:Boolean=false,mode:String=""):JsonObject=buildJsonObject {
        val renamed=after && mode=="rename";val absent=after && mode=="readback";val collision=!after && mode=="collision"
        put("plugins",buildJsonArray {
            if(!absent) add(buildJsonObject {put("name",if(renamed) "Renamed manifest" else "Fixture manifest");put("key",if(renamed) "renamed-key" else key);put("version",if(after || mode=="changed") "2" else "1");put("source","git");put("status","enabled");put("install_dir","$home/plugins/"+(if(renamed) "renamed-folder" else install));put("servers",JsonArray(emptyList()));if(catalog) {put("catalog_name","fixture-plugin");put("catalog_sha",if(after && mode=="movedpin") "c".repeat(40) else pin);put("installed_sha",if(after) (if(mode=="tag") "d".repeat(40) else if(mode=="movedpin") "c".repeat(40) else pin) else "a".repeat(40));put("update_available",!after)}})
            if(collision) add(buildJsonObject {put("name",install);put("key","collision-key");put("version","1");put("source","bundled");put("status","enabled");put("servers",JsonArray(emptyList()))})
        });put("user_count",if(absent) 0 else 1);put("bundled_count",if(collision) 1 else 0)
    }
    fun request(catalog:Boolean=false)=PluginUpdateRequest(GatewayPluginInventory.parse(inventory(catalog),"worker",PluginHome.project(JsonPrimitive(home))).plugins.single())
    fun capability()=buildJsonObject {put("ok",false);put("name","fixture-plugin");put("consent_required",true);put("sha",pin);put("error","private-update-error");put("delta_lines",buildJsonArray {add("private-update-lines")});put("delta",buildJsonObject {put("tools",buildJsonArray {add("fixture_new_tool")});put("python_dependencies",buildJsonArray {add("private-package @ https://user:pass@example/?secret=private-update-reference")})})}
    fun scan(verdict:String="caution")=buildJsonObject {put("ok",false);put("name",install);put("scan_blocked",true);put("scan_verdict",verdict);put("error","private-update-error");put("scan_findings",buildJsonArray {add(buildJsonObject {put("pattern_id","fixture-rule");put("severity","high");put("category","execution");put("file","plugin.py");put("line",3);put("description","Fixture scanner finding")})})}
    fun accepted(mode:String="",catalog:Boolean=false)=buildJsonObject {
        put("ok",true);put("name",when(mode) {"badack"->"wrong-directory";"rename"->"renamed-folder";else->install});put("unchanged",false);if(catalog) put("sha",if(mode=="movedpin") "c".repeat(40) else pin);put("output","private-update-output");put("warnings",buildJsonArray {add("private-update-warning")});put("restart_required",true);put("activation",if(mode=="baddetails") JsonPrimitive("private-invalid-activation") else buildJsonObject {put("deferred",buildJsonObject {put("tools",buildJsonArray {add("fixture_deferred_tool")})})})
    }
}
