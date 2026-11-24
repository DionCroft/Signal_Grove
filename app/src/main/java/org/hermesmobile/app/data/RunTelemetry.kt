package org.hermesmobile.app.data

import kotlinx.serialization.json.*

/** Only public accounting/runtime metadata, never reasoning content or arbitrary payloads. */
object RunTelemetry {
    private const val ObservedRuntimeGeneration = "observed_runtime_generation"
    internal const val RuntimeWithoutExecution = "runtime_without_execution"

    internal fun executionGeneration(frame: JsonObject): Long? = count(frame, "execution_generation")

    internal fun runtimeGeneration(stored: JsonObject): Long? = count(stored, ObservedRuntimeGeneration)

    internal fun hasRuntimeIdentity(runtime: JsonElement?): Boolean {
        val fields = runtime as? JsonObject ?: return false
        return fields.text("provider").isNotBlank() && fields.text("model").isNotBlank()
    }

    private val counts=setOf("input_tokens","output_tokens","total_tokens","cache_read_tokens","cache_write_tokens","reasoning_tokens")
    private fun count(j:JsonObject,key:String)=(j[key] as? JsonPrimitive)?.takeUnless {it.isString}?.longOrNull?.takeIf {it>=0}
    private fun id(j:JsonObject,key:String,max:Int=256)=(j[key] as? JsonPrimitive)?.takeIf {it.isString}?.content?.takeIf {it.isNotBlank() && it.length<=max && it.none(Char::isISOControl)}
    fun from(j:JsonObject):JsonObject=buildJsonObject {
        (j["usage"] as? JsonObject)?.let {raw ->
            val usage=buildJsonObject {
                counts.forEach {key -> count(raw,key)?.let {put(key,it)}}
                if(count(raw,"input_tokens")==null) count(raw,"prompt_tokens")?.let {put("input_tokens",it)}
                if(count(raw,"output_tokens")==null) count(raw,"completion_tokens")?.let {put("output_tokens",it)}
            }
            if(usage.isNotEmpty()) put("usage",usage)
        }
        (j["runtime"] as? JsonObject)?.let {raw ->
            val runtime=buildJsonObject {
                listOf("provider","model","route_source","model_lock").forEach {key -> id(raw,key)?.let {put(key,it)}}
                (raw["requested"] as? JsonObject)?.let {req ->
                    val requested=buildJsonObject {listOf("provider","model").forEach {key -> id(req,key)?.let {put(key,it)}}}
                    if(requested.isNotEmpty()) put("requested",requested)
                }
            }
            if(runtime.isNotEmpty()) put("runtime",runtime)
        }
    }
    fun parse(value: String): JsonObject = runCatching {
        val raw = Json.parseToJsonElement(value).jsonObject
        val fields = from(raw)
        val generation = runtimeGeneration(raw)
        val unverified = (raw[RuntimeWithoutExecution] as? JsonObject)?.let {
            from(JsonObject(mapOf("runtime" to it)))["runtime"]
        }
        buildJsonObject {
            fields.forEach { (key, field) -> put(key, field) }
            if (generation != null && fields.containsKey("runtime")) put(ObservedRuntimeGeneration, generation)
            if (unverified != null) put(RuntimeWithoutExecution, unverified)
        }
    }.getOrDefault(JsonObject(emptyMap()))

    fun merge(old: String, next: JsonObject): String = mergeObserved(old, next, null, null)

    /** Scope reported runtime to its explicit execution while retaining run-level usage. */
    internal fun mergeObserved(
        old: String,
        next: JsonObject,
        currentGeneration: Long?,
        observedGeneration: Long?,
    ): String {
        val before = parse(old)
        val after = if (currentGeneration != null && observedGeneration != null && observedGeneration < currentGeneration) {
            JsonObject(emptyMap())
        } else from(next)
        val previousRuntime = before["runtime"]?.takeIf { runtimeGeneration(before) == currentGeneration }
        val observedRuntime = after["runtime"]?.takeIf { observedGeneration == currentGeneration }
        val runtime = observedRuntime ?: previousRuntime
        // Some servers label controls and poll snapshots but omit the execution from runtime frames.
        val unverified = if (currentGeneration == null) null else {
            after["runtime"]?.takeIf { observedGeneration == null }
                ?: before[RuntimeWithoutExecution]
                ?: before["runtime"]?.takeIf { runtimeGeneration(before) == null }
        }
        val usage = (before["usage"] as? JsonObject).orEmpty() + (after["usage"] as? JsonObject).orEmpty()
        val merged = buildJsonObject {
            if (usage.isNotEmpty()) put("usage", JsonObject(usage))
            if (runtime != null) {
                put("runtime", runtime)
                currentGeneration?.let { put(ObservedRuntimeGeneration, it) }
            }
            if (unverified != null && !hasRuntimeIdentity(runtime)) put(RuntimeWithoutExecution, unverified)
        }
        return if (merged.isEmpty()) "" else merged.toString()
    }
}
