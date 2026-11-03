package org.hermesmobile.app

import kotlinx.serialization.json.*

object SkillHubFixture {
    fun entry(claw:Boolean=false)=buildJsonObject {put("name",if(claw) "Claw fixture" else "Official fixture");put("description","Preview fixture metadata");put("source",if(claw) "clawhub" else "official");put("identifier",if(claw) "fixture-slug" else "official/general/fixture");put("trust_level",if(claw) "community" else "builtin");put("repo","https://repo.example/fixture");put("tags",buildJsonArray {add("fixture")});if(!claw) {put("installed",false);put("category","general")}}
    fun sources()=buildJsonObject {
        put("sources",buildJsonArray {add(buildJsonObject {put("id","github");put("label","GitHub fixture");put("searchable",true);put("rate_limited",true)});add(buildJsonObject {put("id","hermes-index");put("label","Index fixture");put("available",true);put("searchable",true)})})
        put("featured",buildJsonArray {add(entry(true))});put("installed",buildJsonObject {put("clawhub/fixture-slug",buildJsonObject {put("name","fixture-slug");put("trust_level","community");put("scan_verdict","caution");put("install_path","/private-install-home")})})
    }
    fun preview(claw:Boolean=false,changed:Boolean=false)=JsonObject(entry(claw)+mapOf("source" to JsonPrimitive(if(changed) "unexpected-source" else if(claw) "clawhub" else "official"),"skill_md" to JsonPrimitive("# Fixture instruction text"),"files" to buildJsonArray {add("SKILL.md");add("scripts/fixture.py")}))
    fun scan()=buildJsonObject {
        put("identifier","clawhub/fixture-slug");put("name","Claw fixture");put("source","clawhub/fixture-slug");put("trust_level","community");put("verdict","caution");put("policy","ask");put("policy_reason","Requires host confirmation")
        put("findings",buildJsonArray {add(buildJsonObject {put("severity","medium");put("category","fixture");put("file","SKILL.md");put("line",12);put("description","Core finding fixture")})})
        put("tier1",buildJsonObject {put("passed",false);put("incomplete_checks",buildJsonArray {add("license")});put("findings",buildJsonArray {add(buildJsonObject {put("check","pii");put("severity","info");put("file","/private-scan-home/SKILL.md");put("line",0);put("message","private-tier1-message");put("secrets_class",true)})})})
    }
}
