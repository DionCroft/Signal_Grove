package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

object PluginInstallFixture {
    val pin="b".repeat(40)
    fun catalog(changed:Boolean=false)=buildJsonObject {put("status","found");put("entry",buildJsonObject {put("name","fixture-plugin");put("repo",if(changed) "https://github.com/example/changed" else "https://github.com/example/fixture");put("sha","a".repeat(40));put("subdir","plugin");put("tier","community")})}
    fun inspection(changed:Boolean=false)=buildJsonObject {put("ok",true);put("agent",true);put("desktop",true);put("agent_name","fixture-agent");put("desktop_name","fixture-desktop");put("sha",if(changed) "c".repeat(40) else pin);put("warnings",buildJsonArray {add("Host inspection fixture warning")});put("insecure",false)}
    fun inventory(existing:Boolean=false)=buildJsonObject {put("plugins",buildJsonArray {if(existing) add(buildJsonObject {put("name","fixture-agent");put("key","tools/fixture-agent");put("version","1");put("description","Fixture");put("source","user");put("status","enabled");put("servers",JsonArray(emptyList()));put("tools",buildJsonArray {add("fixture_tool")})})});put("user_count",if(existing) 1 else 0);put("bundled_count",0)}
    fun request(catalog:Boolean=true,enable:Boolean=true):PluginInstallRequest {
        val source=PluginSource(if(catalog) PluginSourceKind.Catalog else PluginSourceKind.Repository,if(catalog) "fixture-plugin" else "https://git.example/fixture?key=private-install-source")
        val entry=if(catalog) PluginSourcesProtocol.lookup(PluginInstallFixture.catalog(),"fixture-plugin").entry else null
        return PluginInstallRequest(PluginSourcesProtocol.inspection(inspection(),source,entry),entry,enable)
    }
    fun acknowledgement(enable:Boolean=true)=buildJsonObject {
        put("ok",true);put("plugin_name","fixture-agent");put("enabled",enable);put("restart_required",true);put("gateway_reloaded",true)
        put("python_dependencies",buildJsonArray {add("fixture-package>=1");add("private-package @ https://user:pass@example/?key=private-install-dependency")});put("missing_env",buildJsonArray {add("FIXTURE_KEY")});put("after_install_path","/private-install-home/after-install.md");put("warnings",buildJsonArray {add("/private-warning-home")})
        put("activation",buildJsonObject {put("name","fixture-agent");put("key","tools/fixture-agent");put("activated_now",buildJsonObject {put("hooks",buildJsonArray {add("fixture_hook")})});put("deferred",buildJsonObject {put("tools",buildJsonArray {add("fixture_deferred_tool")})});put("live_now",buildJsonObject {put("mcp_servers",buildJsonArray {add(buildJsonObject {put("name","fixture-server");put("connected",false);put("tools",buildJsonArray {add("fixture_tool")});put("error","/private-error-home?key=private-install-error")})});put("skills",buildJsonArray {add(buildJsonObject {put("name","fixture:skill");put("description","private-skill-description")})})});put("missing_dependencies",buildJsonArray {add("private-extra @ https://example/?key=private-missing-dependency")});put("missing_in",buildJsonArray {add("backend")})})
    }
}
