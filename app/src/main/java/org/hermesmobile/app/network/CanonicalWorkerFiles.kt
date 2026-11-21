package org.hermesmobile.app.network

import java.util.UUID
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.CanonicalSessionSnapshot

internal class AttachedWorkerRefused(val code: String, val requestSubmitted: Boolean) :
    Exception("The registered project read was refused.")

internal class AttachedWorkerProject(
    val projectId: String, val device: String, val root: String, val instance: String?
) {
    override fun toString() = "AttachedWorkerProject(redacted)"
}

internal class AttachedWorkerBinding(val project: AttachedWorkerProject, val token: String) {
    override fun toString() = "AttachedWorkerBinding(redacted)"
}

internal class AttachedWorkerEntry(val name: String, val path: String, val directory: Boolean, val token: String) {
    override fun toString() = "AttachedWorkerEntry(redacted)"
}

internal class AttachedWorkerListing(
    val path: String, val entries: List<AttachedWorkerEntry>, val truncated: Boolean, val excluded: Long
) {
    override fun toString() = "AttachedWorkerListing(redacted)"
}

internal class AttachedWorkerPreview(val path: String, val text: String?, val truncated: Boolean) {
    override fun toString() = "AttachedWorkerPreview(redacted)"
}

internal object AttachedWorkerProtocol {
    const val Describe = "signal_grove.workspaces.describe_attachment_v1"
    const val Read = "signal_grove.workspaces.read_attachment_v1"
    private val codes = setOf("INVALID", "DENIED", "CHANGED", "EXPIRED", "NOT_FOUND", "BUSY", "UNAVAILABLE",
        "UNSUPPORTED", "TOO_LARGE", "DUPLICATE", "IDENTITY_FAILURE")
    private val hex = Regex("[a-f0-9]{64}")
    private val uuid = Regex("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")
    private val deviceId = Regex("[A-Z0-9][A-Z0-9_-]{0,63}")
    private val projectId = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,79}")
    private val drive = Regex("[A-Za-z]:")
    private val base = setOf("schema", "worker_instance", "project_id", "relative_path", "truncated")

    fun params(snapshot: CanonicalSessionSnapshot, request: JsonObject? = null) = buildJsonObject {
        put("attached_session_id", snapshot.owner.sessionId)
        put("expected_revision", snapshot.revision)
        put("expected_generation", snapshot.executionGeneration)
        request?.let { put("request", it) }
    }

    fun request(project: AttachedWorkerProject, operation: String, binding: AttachedWorkerBinding? = null,
                entry: AttachedWorkerEntry? = null) = buildJsonObject {
        put("request_id", UUID.randomUUID().toString().replace("-", ""))
        put("worker_instance", project.instance ?: throw GatewayProtocolException())
        put("operation", operation)
        binding?.let {
            put("binding_token", it.token)
            put("relative_path", entry?.path.orEmpty())
            put("entry_token", entry?.let { row -> JsonPrimitive(row.token) } ?: JsonNull)
        }
    }

    fun describe(envelope: JsonObject): AttachedWorkerProject {
        val row = payload(envelope)
        expect(row.keys == setOf("schema", "device", "state", "worker_instance", "failure_code", "project_ids", "operations", "root"))
        expect(row.int("schema") == 1L)
        val projects = row.array("project_ids")
        val project = string(projects.singleOrNull(), 80).also { expect(projectId.matches(it)) }
        val device = row.text("device", 64).also { expect(deviceId.matches(it)) }
        val root = row.text("root", 8192).also(::validateRoot)
        val state = row.text("state", 12)
        val operations = row.array("operations")
        val instance = if (state == "ready") {
            expect(row["failure_code"] == JsonNull)
            expect(operations == JsonArray(listOf("prepare", "list", "preview").map(::JsonPrimitive)))
            row.text("worker_instance", 36).also { expect(uuid.matches(it)) }
        } else {
            expect(state in setOf("starting", "held", "closed") && operations.isEmpty() && row["worker_instance"] == JsonNull)
            val code = row["failure_code"]
            if (code != JsonNull) throw AttachedWorkerRefused(string(code, 24).also { expect(it in codes) }, false)
            null
        }
        return AttachedWorkerProject(project, device, root, instance)
    }

    fun prepare(envelope: JsonObject, request: JsonObject, project: AttachedWorkerProject): AttachedWorkerBinding {
        val row = readResult(envelope, request)
        expect(row.keys == setOf("schema", "project_id", "worker", "root", "binding_token", "expires_in_seconds"))
        val worker = row.obj("worker")
        expect(worker.keys == setOf("device", "instance_id"))
        expect(worker.text("device", 64) == project.device && worker.text("instance_id", 36) == project.instance)
        expect(row.int("schema") == 1L && row.text("project_id", 80) == project.projectId)
        expect(row.text("root", 8192) == project.root && row.int("expires_in_seconds") in 1L..300L)
        return AttachedWorkerBinding(project, row.text("binding_token", 64).also { expect(hex.matches(it)) })
    }

    fun list(envelope: JsonObject, request: JsonObject, binding: AttachedWorkerBinding): AttachedWorkerListing {
        val row = readResult(envelope, request)
        expect(row.keys == base + setOf("entries", "excluded_entries"))
        val parent = location(row, request, binding)
        val children = row.array("entries")
        expect(children.size <= 200)
        val entries = children.map { element ->
            val entry = element as? JsonObject ?: throw GatewayProtocolException()
            expect(entry.keys == setOf("name", "relative_path", "kind", "byte_size", "entry_token"))
            val name = entry.text("name", 1024).also(::validatePart)
            val path = entry.text("relative_path", 4096)
            expect(path == if (parent.isEmpty()) name else "$parent/$name")
            val kind = entry.text("kind", 10)
            expect(kind == "directory" || kind == "file")
            if (kind == "directory") expect(entry["byte_size"] == JsonNull) else expect(entry.int("byte_size") >= 0)
            AttachedWorkerEntry(name, path, kind == "directory", entry.text("entry_token", 64).also { expect(hex.matches(it)) })
        }
        expect(entries.map { it.path }.toSet().size == entries.size)
        val excluded = row.int("excluded_entries")
        expect(excluded in 0L..1000L)
        return AttachedWorkerListing(parent, entries, row.flag("truncated"), excluded)
    }

    fun preview(envelope: JsonObject, request: JsonObject, binding: AttachedWorkerBinding): AttachedWorkerPreview {
        val row = readResult(envelope, request)
        expect(row.keys == base + setOf("byte_size", "preview_bytes", "preview_sha256", "binary", "text"))
        val path = location(row, request, binding)
        val size = row.int("byte_size")
        val bytes = row.int("preview_bytes")
        val truncated = row.flag("truncated")
        expect(size in 0L..67108864L && bytes in 0L..minOf(size, 32768L) && truncated == (bytes < size))
        expect(hex.matches(row.text("preview_sha256", 64)))
        val text = if (row.flag("binary")) {
            expect(row["text"] == JsonNull)
            null
        } else {
            val value = (row["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw GatewayProtocolException()
            expect(value.toByteArray(Charsets.UTF_8).size <= bytes && clean(value, allowLineBreaks = true))
            value
        }
        return AttachedWorkerPreview(path, text, truncated)
    }

    private fun readResult(envelope: JsonObject, request: JsonObject): JsonObject {
        val payload = payload(envelope)
        expect(payload.keys == setOf("schema", "request_id", "worker_instance", "status", "result"))
        expect(payload.int("schema") == 1L && payload["status"] == JsonPrimitive("ok"))
        expect(payload["request_id"] == request["request_id"] && payload["worker_instance"] == request["worker_instance"])
        return payload.obj("result")
    }

    private fun location(row: JsonObject, request: JsonObject, binding: AttachedWorkerBinding): String {
        expect(row.int("schema") == 1L && row.text("project_id", 80) == binding.project.projectId)
        expect(row["relative_path"] == request["relative_path"] && row["worker_instance"] == request["worker_instance"])
        return row["relative_path"]!!.jsonPrimitive.content
    }

    private fun payload(envelope: JsonObject): JsonObject {
        expect(envelope.int("schema") == 1L)
        when (envelope["state"]) {
            JsonPrimitive("ok") -> expect(envelope.keys == setOf("schema", "state", "payload"))
            JsonPrimitive("error") -> {
                expect(envelope.keys == setOf("schema", "state", "code", "request_submitted"))
                val code = envelope.text("code", 24).also { expect(it in codes) }
                throw AttachedWorkerRefused(code, envelope.flag("request_submitted"))
            }
            else -> throw GatewayProtocolException()
        }
        return envelope.obj("payload")
    }

    private fun validateRoot(root: String) {
        val parts = root.split('\\')
        if (root.startsWith("\\\\")) {
            expect(parts.size >= 5)
            parts.drop(2).forEach(::validatePart)
        } else {
            expect(parts.size >= 3 && drive.matches(parts.first()))
            parts.drop(1).forEach(::validatePart)
        }
    }

    private fun validatePart(value: String) {
        expect(value.isNotEmpty() && value != "." && value != ".." && value.last() !in " ." &&
            value.none { it in "/\\:<>\"|?*" })
    }

    private fun clean(value: String, allowLineBreaks: Boolean = false) =
        value.codePoints().noneMatch { code ->
            code in 0xD800..0xDFFF || (Character.isISOControl(code) && !(allowLineBreaks && code in listOf(9, 10, 13)))
        }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject ?: throw GatewayProtocolException()
    private fun JsonObject.array(key: String) = this[key] as? JsonArray ?: throw GatewayProtocolException()
    private fun JsonObject.text(key: String, max: Int) = string(this[key], max)
    private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
        ?: throw GatewayProtocolException()
    private fun JsonObject.flag(key: String) = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
        ?: throw GatewayProtocolException()
    private fun string(value: JsonElement?, max: Int) = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?.also { expect(it.isNotBlank() && it.length <= max && clean(it)) } ?: throw GatewayProtocolException()
    private fun expect(condition: Boolean) { if (!condition) throw GatewayProtocolException() }
}
