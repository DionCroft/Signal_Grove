package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.security.MessageDigest

/** A transient review, never a byte pin or a persisted source/credential hash. */
class HubInstallRequest(val preview:HubPreview,val scan:HubScan) {
    private val frozen=review(preview,scan)
    init {require(supported(preview,scan))}
    val skill get()=preview.skill
    val name get()=scan.name
    fun same(preview:HubPreview,scan:HubScan)=supported(preview,scan) && frozen==review(preview,scan)
    fun body(profile:String):JsonObject {SkillProtocol.profile(profile);return buildJsonObject {put("identifier",skill.canonicalId);put("profile",profile)}}
    fun summary()=buildJsonObject {put("background",true)}.toString()
    fun acknowledge(j:JsonObject):String {
        val pid=j["pid"] as? JsonPrimitive
        require(j["ok"]==JsonPrimitive(true) && j["name"]==JsonPrimitive(actionName(skill.canonicalId)) && pid!=null && !pid.isString && (pid.longOrNull ?: 0)>0)
        return buildJsonObject {put("accepted",true);put("background",true)}.toString()
    }
    override fun toString()="HubInstallRequest(redacted)"
    companion object {
        fun supported(p:HubPreview,s:HubScan):Boolean {
            val identifier=p.skill.canonicalId;val lower=identifier.trim('/').lowercase()
            return p.skill.addressable && '/' in identifier && lower.startsWith("nousresearch/hermes-agent/skills/").not() &&
                lower.startsWith("skills-sh/nousresearch/hermes-agent/").not() && p.content.isNotBlank() && p.files.isNotEmpty() &&
                s.identifier==identifier && s.policy=="allow" && SkillProtocol.name(s.name)
        }
        fun actionName(identifier:String,verb:String="install"):String {
            require(verb in setOf("install","uninstall"))
            val slug=identifier.lowercase().replace(Regex("[^a-z0-9]+"),"-").trim('-').take(48).ifBlank {"skill"}
            val digest=MessageDigest.getInstance("SHA-1").digest(identifier.toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}.take(8)
            return "skills-$verb-$slug-$digest"
        }
        private fun review(p:HubPreview,s:HubScan)=buildJsonObject {
            val k=p.skill
            put("metadata",buildJsonObject {put("id",k.canonicalId);put("name",k.name);put("source",k.source);put("trust",k.trust);put("repo",k.repo);put("description",k.description);put("category",k.category);put("installed",k.installed?.let(::JsonPrimitive) ?: JsonNull);put("tags",JsonArray(k.tags.map(::JsonPrimitive)))})
            put("content",p.content);put("files",JsonArray(p.files.map(::JsonPrimitive)))
            put("scan",buildJsonObject {
                put("id",s.identifier);put("name",s.name);put("source",s.source);put("trust",s.trust);put("verdict",s.verdict);put("policy",s.policy);put("reason",s.reason)
                put("tier1_passed",s.tier1Passed?.let(::JsonPrimitive) ?: JsonNull);put("incomplete",JsonArray(s.incomplete.map(::JsonPrimitive)))
                fun finding(f:HubFinding)=buildJsonObject {put("severity",f.severity);put("category",f.category);put("file",f.file);put("line",f.line?.let(::JsonPrimitive) ?: JsonNull);put("description",f.description);put("sensitive",f.sensitive)}
                put("findings",JsonArray(s.findings.map(::finding)));put("tier1",JsonArray(s.tier1.map(::finding)))
            })
        }
    }
}
