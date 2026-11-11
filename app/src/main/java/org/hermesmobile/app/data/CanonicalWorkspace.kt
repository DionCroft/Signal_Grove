package org.hermesmobile.app.data

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.*

/** A private attachment-time observation, never a worker binding or a permission grant. */
internal sealed class CanonicalWorkspaceDirectory {
    data object NotReported:CanonicalWorkspaceDirectory()
    data object Unavailable:CanonicalWorkspaceDirectory()
    class Reported internal constructor(val path:String):CanonicalWorkspaceDirectory() {
        override fun equals(other:Any?)=other is Reported && path==other.path
        override fun hashCode()=path.hashCode()
        override fun toString()="CanonicalWorkspaceDirectory.Reported(redacted)"
    }
    override fun toString()="CanonicalWorkspaceDirectory(redacted)"
    companion object {
        fun parse(value:JsonElement?):CanonicalWorkspaceDirectory {
            if(value==null || value==JsonNull) return NotReported
            val text=(value as? JsonPrimitive)?.takeIf {it.isString}?.content ?: return Unavailable
            return if(validText(text)) Reported(text) else Unavailable
        }
        fun validText(text:String)=text.toByteArray(Charsets.UTF_8).size<=8192 && text.none(Char::isISOControl) && text.codePoints().noneMatch {it in 0xD800..0xDFFF}
        fun absolute(text:String):Boolean {
            if(!validText(text) || text.isBlank()) return false
            val drive=text.length>=3 && (text[0] in 'A'..'Z' || text[0] in 'a'..'z') &&
                text[1]==':' && (text[2]=='/' || text[2]=='\\')
            val unc=text.length>2 && text[0]=='\\' && text[1]=='\\' &&
                text.substring(2).split('\\').filter {it.isNotEmpty()}.size>=2
            return text.startsWith("/") || drive || unc
        }
    }
}

/** Preflight only: no transport, filesystem access, database write or automatic retry. */
internal class CanonicalWorkspaceReview private constructor(
    val expected:CanonicalSessionSnapshot,val requestedDirectory:String,val requestId:String
) {
    private val used=AtomicBoolean(false)
    fun consume(current:CanonicalSessionSnapshot):JsonObject {
        if(!used.compareAndSet(false,true)) throw CanonicalApprovalUsed()
        if(current.owner!=expected.owner || current.revision!=expected.revision ||
            current.executionGeneration!=expected.executionGeneration || current.workspace!=expected.workspace || current.reportedRunning)
            throw CanonicalReviewChanged()
        return buildJsonObject {
            put("session_id",expected.owner.sessionId);put("request_id",requestId)
            put("expected_revision",expected.revision);put("expected_generation",expected.executionGeneration)
            put("operation","workspace");put("payload",buildJsonObject {put("cwd",requestedDirectory)})
        }
    }
    override fun toString()="CanonicalWorkspaceReview(redacted)"
    companion object {
        fun create(snapshot:CanonicalSessionSnapshot,directory:String):CanonicalWorkspaceReview {
            val reported=snapshot.workspace as? CanonicalWorkspaceDirectory.Reported ?: throw CanonicalReviewChanged()
            require(!snapshot.reportedRunning && snapshot.revision in 0 until Long.MAX_VALUE && snapshot.executionGeneration in 0 until Long.MAX_VALUE)
            require((reported.path.isEmpty() || CanonicalWorkspaceDirectory.absolute(reported.path)) && CanonicalWorkspaceDirectory.absolute(directory))
            require(reported.path!=directory)
            return CanonicalWorkspaceReview(snapshot,directory,UUID.randomUUID().toString())
        }
    }
}
