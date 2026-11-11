package org.hermesmobile.app

import java.util.concurrent.Executors
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class CanonicalWorkspaceTest {
    private val f=CanonicalViewerFixtures
    private val old="/fixture/private/project"
    private fun snapshot(owner:CanonicalFeedOwner=f.owner,revision:Long=2,generation:Long=3,running:Boolean=false,
        directory:CanonicalWorkspaceDirectory=CanonicalWorkspaceDirectory.Reported(old))=
        CanonicalSessionSnapshot(owner,"requested-alias","reported-alias","subscription",f.cursor,revision,generation,running,2,true,directory)
    private fun review()=CanonicalWorkspaceReview.create(snapshot(),"/fixture/new project")
    private fun refuses(block:()->Unit) {assertTrue(runCatching(block).isFailure)}

    @Test fun optionalDirectoryDistinguishesMissingEmptyAndMalformed() {
        assertSame(CanonicalWorkspaceDirectory.NotReported,CanonicalWorkspaceDirectory.parse(null))
        assertSame(CanonicalWorkspaceDirectory.NotReported,CanonicalWorkspaceDirectory.parse(JsonNull))
        assertEquals("",(CanonicalWorkspaceDirectory.parse(JsonPrimitive("")) as CanonicalWorkspaceDirectory.Reported).path)
        for(value in listOf(JsonPrimitive(true),JsonPrimitive(1),buildJsonObject {},JsonArray(emptyList()),
            JsonPrimitive("/bad\npath"),JsonPrimitive("a".repeat(8193)),JsonPrimitive("\uD800")))
            assertSame(CanonicalWorkspaceDirectory.Unavailable,CanonicalWorkspaceDirectory.parse(value))
    }
    @Test fun absolutePathsPreserveExactUnicodeAndPlatformSpelling() {
        for(path in listOf("/","/fixture/项目 🌳", "C:\\Projects\\Signal Grove", "z:/projects", "\\\\server\\share", "/a/../b")) {
            assertTrue(path,CanonicalWorkspaceDirectory.absolute(path))
            assertEquals(path,(CanonicalWorkspaceDirectory.parse(JsonPrimitive(path)) as CanonicalWorkspaceDirectory.Reported).path)
        }
        for(path in listOf("", " ","relative/path","C:relative","\\single-root","\\\\server","/bad\tpath","/"+"界".repeat(2731)))
            assertFalse(path,CanonicalWorkspaceDirectory.absolute(path))
    }
    @Test fun reviewUsesResolvedOwnerAndExactCompareAndSwapWithoutInferringAuthority() {
        val r=review();val j=r.consume(snapshot())
        assertEquals(setOf("session_id","request_id","expected_revision","expected_generation","operation","payload"),j.keys)
        assertEquals(JsonPrimitive(f.owner.sessionId),j["session_id"])
        assertEquals(JsonPrimitive(2),j["expected_revision"]);assertEquals(JsonPrimitive(3),j["expected_generation"])
        assertEquals(JsonPrimitive("workspace"),j["operation"])
        assertEquals(buildJsonObject {put("cwd","/fixture/new project")},j["payload"])
        assertTrue(checkNotNull(j["request_id"]).jsonPrimitive.content.isNotBlank())
        assertFalse(r.toString().contains("fixture"));assertFalse(snapshot().receiptSummary().contains(old))
        assertFalse(snapshot().toString().contains(old));assertFalse(snapshot().workspace.toString().contains(old))
        assertTrue(runCatching {r.consume(snapshot())}.exceptionOrNull() is CanonicalApprovalUsed)
    }
    @Test fun actorProfileHomeApiRuntimeEpochAndResolvedSessionAreAllReviewFences() {
        val o=f.owner
        val owners=listOf(o.copy(target=o.target.copy(actor="other")),o.copy(target=o.target.copy(apiScope="a".repeat(64))),
            o.copy(target=o.target.copy(url="https://other.test/")),o.copy(target=o.target.copy(profile="other")),
            o.copy(homeId="a".repeat(64)),o.copy(profileId="/other"),o.copy(instanceId="other"),o.copy(authorityEpoch=8),o.copy(sessionId="other"))
        for(owner in owners) {
            val r=review()
            assertTrue(runCatching {r.consume(snapshot(owner=owner))}.exceptionOrNull() is CanonicalReviewChanged)
            assertTrue(runCatching {r.consume(snapshot())}.exceptionOrNull() is CanonicalApprovalUsed)
        }
    }
    @Test fun revisionGenerationRunningAndDirectoryChangesBurnTheReview() {
        val changed=listOf(snapshot(revision=3),snapshot(generation=4),snapshot(running=true),
            snapshot(directory=CanonicalWorkspaceDirectory.Reported("/replacement")),snapshot(directory=CanonicalWorkspaceDirectory.NotReported))
        for(s in changed) {
            val r=review();assertTrue(runCatching {r.consume(s)}.exceptionOrNull() is CanonicalReviewChanged)
            assertTrue(runCatching {r.consume(snapshot())}.exceptionOrNull() is CanonicalApprovalUsed)
        }
    }
    @Test fun invalidOrUnobservedStateCannotCreateReview() {
        for(s in listOf(snapshot(running=true),snapshot(revision=-1),snapshot(revision=Long.MAX_VALUE),snapshot(generation=-1),
            snapshot(generation=Long.MAX_VALUE),snapshot(directory=CanonicalWorkspaceDirectory.Unavailable),
            snapshot(directory=CanonicalWorkspaceDirectory.NotReported),snapshot(directory=CanonicalWorkspaceDirectory.Reported("relative"))))
            refuses {CanonicalWorkspaceReview.create(s,"/new")}
        for(path in listOf(old,"relative","/bad\npath")) refuses {CanonicalWorkspaceReview.create(snapshot(),path)}
        CanonicalWorkspaceReview.create(snapshot(directory=CanonicalWorkspaceDirectory.Reported("")),"/new")
    }
    @Test fun concurrentConsumersCannotProduceTwoRequests() {
        val pool=Executors.newFixedThreadPool(4)
        try {
            val r=review();val results=(1..8).map {pool.submit<Boolean> {runCatching {r.consume(snapshot())}.isSuccess}}
            assertEquals(1,results.count {it.get()})
        } finally {pool.shutdownNow()}
    }
    @Test fun malformedOptionalDirectoryDoesNotBreakHistoryOrRetainOtherPrivateInfo() {
        for(cwd in listOf<JsonElement>(JsonPrimitive(old),JsonNull,JsonPrimitive(9),JsonPrimitive("/bad\npath"))) {
            val raw=buildJsonObject {
                put("session_id",f.owner.sessionId);put("stored_session_id","reported");put("subscription_id","subscription")
                put("messages",JsonArray(emptyList()));put("messages_omitted",true);put("message_count",2);put("running",false)
                put("authority_epoch",7);put("revision",2);put("execution_generation",3);put("replay_epoch","ring");put("last_sequence",4)
                put("pending",JsonArray(emptyList()));put("prompts",JsonArray(emptyList()))
                put("info",buildJsonObject {put("desktop_protocol","hermes-gateway-v1");put("profile_id",f.profile);put("cwd",cwd)
                    put("system_prompt","private-sentinel");put("api_key","private-sentinel")})
            }
            val s=CanonicalSessionProtocol.snapshot(raw,f.binding,"requested")
            assertEquals(f.owner,s.owner);assertEquals(CanonicalWorkspaceDirectory.parse(cwd),s.workspace)
            assertFalse(s.receiptSummary().contains(old));assertFalse(s.receiptSummary().contains("private-sentinel"))
        }
    }
}
