package org.hermesmobile.app.local

import java.io.File
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalHistoryOrganizationTest {
    @get:Rule val temp = TemporaryFolder()
    private val model = "a".repeat(64)
    private fun fixture(): Triple<LocalHistoryOrganization, File, Pair<LocalConversationStore, LocalConversation>> {
        val history = LocalConversationStore(temp.newFolder())
        val chat = LocalConversation(modelSha256 = model, updatedAt = 7,
            exchanges = listOf(LocalExchange("Garden", "Peaceful reply", LocalReplyState.COMPLETE)))
        history.saveNew(chat)
        val root = temp.newFolder()
        return Triple(LocalHistoryOrganization(root, history), root, history to chat)
    }
    private fun denied(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} }
    private fun unavailable(block: () -> Unit) { try { block(); fail("Expected corrupt-index refusal") } catch (_: LocalHistoryOrganizationException) {} }

    @Test fun missingMetadataMeansDefaultsWithoutCreatingAnIndexOrLoadingAWeight() {
        val (organization, root, pair) = fixture()
        assertTrue(organization.entries().isEmpty())
        assertNull(organization.update(pair.second.id, model, null, LocalHistoryPreferences()))
        assertTrue(root.listFiles()!!.isEmpty()); assertEquals(pair.second, pair.first.load(pair.second.id))
    }
    @Test fun unicodeTitlePinAndArchiveSurviveReopenWithoutChangingTranscriptOrBranchIdentity() {
        val (organization, root, pair) = fixture(); val (history, chat) = pair
        val baseline = history.createBranch(chat, 0, LocalBranchKind.FORK, 8).origin!!.sourceSha256
        val prefs = LocalHistoryPreferences("花园 🌱 "+"🌳".repeat(72), pinned = true, archived = true)
        val saved = organization.update(chat.id, model, null, prefs)
        assertEquals(saved, LocalHistoryOrganization(root, history).entries()[chat.id])
        assertEquals(chat, history.load(chat.id)); assertEquals(7, history.load(chat.id).updatedAt)
        assertEquals(baseline, history.createBranch(chat, 0, LocalBranchKind.FORK, 9).origin!!.sourceSha256)
        assertEquals(3, history.list().size)
    }
    @Test fun staleReviewFromAnotherStoreCannotOverwriteANewerTitleOrFlags() {
        val (organization, root, pair) = fixture(); val (history, chat) = pair
        val earlier = organization.update(chat.id, model, null, LocalHistoryPreferences("First"))
        val newer = LocalHistoryOrganization(root, history).update(chat.id, model, earlier, LocalHistoryPreferences("Second", pinned = true))
        val bytes = File(root, "index.json").readBytes().toList()
        denied { organization.update(chat.id, model, earlier, LocalHistoryPreferences("Stale", archived = true)) }
        assertEquals(bytes, File(root, "index.json").readBytes().toList()); assertEquals(newer, organization.entries()[chat.id])
    }
    @Test fun wrongModelOrMissingConversationCannotAcquireMetadata() {
        val (organization, root, pair) = fixture(); val (history, chat) = pair
        denied { organization.update(chat.id, "b".repeat(64), null, LocalHistoryPreferences("Wrong")) }
        assertTrue(root.listFiles()!!.isEmpty())
        val saved = organization.update(chat.id, model, null, LocalHistoryPreferences("Bound"))
        val bytes = File(root, "index.json").readBytes().toList()
        history.save(chat.copy(modelSha256 = "b".repeat(64)))
        denied { organization.update(chat.id, "b".repeat(64), saved, LocalHistoryPreferences("Changed")) }
        assertEquals(bytes, File(root, "index.json").readBytes().toList())
        history.delete(chat.id)
        try { organization.update(chat.id, model, saved, LocalHistoryPreferences("Missing")); fail("Expected missing-chat refusal") }
        catch (_: LocalHistoryException) {}
        assertEquals(bytes, File(root, "index.json").readBytes().toList())
    }
    @Test fun invalidTitlesAndPathLikeIdentifiersCannotPublishOrChangeMetadata() {
        val (organization, root, pair) = fixture()
        for (title in listOf("", " ", " leading", "trailing ", "a\nb", "a\u0000b", "a\u2028b", "a\u2029b", "x".repeat(81), "你".repeat(81), "\uD800", "\uDC00")) {
            denied { LocalHistoryPreferences(title) }
        }
        assertEquals("🌳".repeat(80), LocalHistoryPreferences("🌳".repeat(80)).title)
        for (id in listOf("../outside", "00000000-0000-0000-0000-00000000000Z", pair.second.id.uppercase()+"/..")) {
            denied { organization.update(id, model, null, LocalHistoryPreferences("Name")) }
        }
        assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun unknownSchemaExtensionsAndWrongFlagTypesArePreservedAndRefused() {
        val (organization, root, pair) = fixture(); val chat = pair.second
        organization.update(chat.id, model, null, LocalHistoryPreferences("Name", pinned = true))
        val file = File(root, "index.json"); val good = file.readText()
        val bad = listOf(good.replace("\"schema\":1", "\"schema\":2"), good.replace("\"schema\":1", "\"schema\":\"1\""),
            good.replace("\"pinned\":true", "\"pinned\":\"true\""), good.dropLast(1)+",\"future\":1}",
            good.replace("\"title\":\"Name\"", "\"title\":\"Name\",\"future\":false"), good.replace(model, "../model"))
        for (text in bad) {
            file.writeText(text); unavailable { organization.entries() }
            unavailable { organization.update(chat.id, model, null, LocalHistoryPreferences("Other")) }
            assertEquals(text, file.readText()); assertEquals(chat, pair.first.load(chat.id))
        }
    }
    @Test fun oversizedMalformedUtf8AndNonFileIndexesAreNotReplaced() {
        val (organization, root, pair) = fixture(); val file = File(root, "index.json")
        for (bytes in listOf(ByteArray(65537) {32}, byteArrayOf(0xc3.toByte(), 0x28), "{broken".toByteArray())) {
            file.writeBytes(bytes); unavailable { organization.entries() }
            unavailable { organization.update(pair.second.id, model, null, LocalHistoryPreferences("Other")) }
            assertArrayEquals(bytes, file.readBytes())
        }
        assertTrue(file.delete()); assertTrue(file.mkdir())
        unavailable { organization.entries() }; assertTrue(file.isDirectory)
    }
    @Test fun cancellationBeforeAtomicPublicationKeepsThePreviousIndexAndAllHistory() {
        val (organization, root, pair) = fixture(); val chat = pair.second
        val saved = organization.update(chat.id, model, null, LocalHistoryPreferences("Before"))
        val file = File(root, "index.json"); val bytes = file.readBytes().toList()
        val oldPartial = File(root, "index.previous.part").apply {writeText("old interrupted bytes")}
        var cancelled = false
        try { organization.update(chat.id, model, saved, LocalHistoryPreferences("After", archived = true)) {
            if (root.listFiles()!!.any { it.extension == "part" && it != oldPartial }) {cancelled = true; throw CancellationException("stop")}
        }; fail("Expected cancellation") } catch (_: CancellationException) {}
        assertTrue(cancelled); assertEquals(bytes, file.readBytes().toList()); assertEquals("old interrupted bytes", oldPartial.readText())
        assertEquals(setOf("index.json", oldPartial.name), root.listFiles()!!.map {it.name}.toSet())
        assertEquals(chat, pair.first.load(chat.id)); assertEquals(saved, organization.entries()[chat.id])
    }
    @Test fun cancelledReadPropagatesWithoutDroppingExistingMetadata() {
        val (organization, root, pair) = fixture(); organization.update(pair.second.id, model, null, LocalHistoryPreferences("Saved"))
        val file = File(root, "index.json"); val before = file.readBytes().toList(); var calls = 0
        try { organization.entries {if (++calls == 2) throw CancellationException("stop")}; fail("Expected cancellation") }
        catch (_: CancellationException) {}
        assertEquals(2, calls); assertEquals(before, file.readBytes().toList())
    }
    @Test fun explicitResetWorksAfterDeletionWithoutDeletingAnotherConversationOrReplayingAnything() {
        val (organization, root, pair) = fixture(); val (history, chat) = pair
        val child = history.createBranch(chat, 0, LocalBranchKind.FORK, 8)
        val saved = organization.update(chat.id, model, null, LocalHistoryPreferences("Archived parent", archived = true))!!
        history.delete(chat.id); assertEquals(saved, organization.entries()[chat.id])
        organization.reset(chat.id, saved)
        assertTrue(LocalHistoryOrganization(root, history).entries().isEmpty()); assertEquals(child, history.load(child.id))
        denied { organization.reset(chat.id, saved) }; assertEquals(listOf(child.id), history.list().map {it.id})
    }
    @Test fun fullOrphanIndexNeedsExplicitCleanupBeforeAnotherAssociation() {
        val (organization, root, pair) = fixture(); val file = File(root, "index.json")
        val ids = List(50) {UUID.randomUUID().toString()}
        file.writeText(buildJsonObject {put("schema",1);putJsonObject("entries") {ids.forEach {id ->putJsonObject(id) {
            put("model",model);put("title","Older saved title");put("pinned",false);put("archived",true)
        }}}}.toString())
        val bytes = file.readBytes().toList()
        denied { organization.update(pair.second.id, model, null, LocalHistoryPreferences("New title")) }
        assertEquals(bytes, file.readBytes().toList()); assertEquals(50, organization.entries().size)
        organization.reset(ids.first(), organization.entries().getValue(ids.first()))
        organization.update(pair.second.id, model, null, LocalHistoryPreferences("New title"))
        assertEquals(50, organization.entries().size); assertEquals(pair.second, pair.first.load(pair.second.id))
    }
}
