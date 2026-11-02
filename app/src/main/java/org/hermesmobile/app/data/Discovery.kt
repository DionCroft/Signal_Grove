package org.hermesmobile.app.data

import kotlinx.serialization.json.*

enum class DiscoveryResource(val endpoint: String,val path: String) {
    Readiness("health_detailed","health/detailed"),
    Models("model_options","api/model/options"),
    Skills("skills","v1/skills"),
    Toolsets("toolsets","v1/toolsets")
}
data class DiscoveryState(
    val loading: Set<DiscoveryResource> = emptySet(),
    val results: Map<DiscoveryResource,JsonObject> = emptyMap(),
    val errors: Map<DiscoveryResource,String> = emptyMap(),
    val fetchedAt: Map<DiscoveryResource,Long> = emptyMap()
)
data class ModelProvider(val slug: String,val name: String,val authenticated: Boolean?,val models: List<String>,
    val warning: String,val capabilities: JsonObject,val pricing: JsonObject,val unavailableModels: Set<String> = emptySet())
data class ModelInventory(val model: String,val provider: String,val providers: List<ModelProvider>) {
    companion object {
        fun parse(payload: JsonObject)=ModelInventory(payload.text("model"),payload.text("provider"),payload.items("providers").take(500).mapNotNull {row ->
            val slug=row.text("slug");if(slug.isBlank()) return@mapNotNull null
            ModelProvider(slug,row.text("name").ifBlank {slug},(row["authenticated"] as? JsonPrimitive)?.booleanOrNull,
                (row["models"] as? JsonArray).orEmpty().mapNotNull {(it as? JsonPrimitive)?.contentOrNull}.filter {it.isNotBlank()}.distinct().take(20000),
                row.text("warning").take(1000),row["capabilities"] as? JsonObject ?: JsonObject(emptyMap()),row["pricing"] as? JsonObject ?: JsonObject(emptyMap()),(row["unavailable_models"] as? JsonArray).orEmpty().mapNotNull {(it as? JsonPrimitive)?.contentOrNull}.toSet())
        }.distinctBy {it.slug})
    }
}
/** HTTP success says the diagnostic request worked; readiness comes from its payload. */
fun readinessStatus(payload: JsonObject)=payload.text("status").ifBlank {(payload["readiness"] as? JsonObject)?.text("status").orEmpty()}.ifBlank {"unknown"}
