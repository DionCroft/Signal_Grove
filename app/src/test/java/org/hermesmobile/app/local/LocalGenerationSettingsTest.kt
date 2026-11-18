package org.hermesmobile.app.local

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalGenerationSettingsTest {
    @get:Rule val temp = TemporaryFolder()
    private val settings = LocalGenerationSettings(1024, 2, 384,
        LocalRuntimePolicy("b".repeat(64), LocalSamplingPolicy.GREEDY_MODEL_METADATA_V1))
    private fun conversation() = LocalConversation(modelSha256 = "a".repeat(64), updatedAt = 1, exchanges = listOf(
        LocalExchange("Old", "Old reply", LocalReplyState.COMPLETE),
        LocalExchange("New", "New reply", LocalReplyState.COMPLETE, settings)))
    @Test fun mixedHistoryRetainsKnownSettingsAndDoesNotInventOlderOnes() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val chat = conversation(); store.save(chat)
        val before = File(root, "${chat.id}.json").readBytes(); assertTrue(String(before, Charsets.UTF_8).contains("\"schema\":3"))
        val reopened = LocalConversationStore(root).load(chat.id)
        assertEquals(chat, reopened); assertNull(reopened.exchanges.first().generation)
        assertEquals(settings, reopened.exchanges.last().generation); store.list(); store.recoverInterrupted()
        assertArrayEquals(before, File(root, "${chat.id}.json").readBytes())
    }
    @Test fun legacyAndEarlierBranchSchemaRemainReadableWithoutMigrationWrites() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val old = conversation().copy(exchanges = conversation().exchanges.take(1)); store.save(old)
        val branch = store.createBranch(old, 0, LocalBranchKind.FORK, 2)
        val before = root.listFiles()!!.associate { it.name to it.readText() }
        val next = LocalConversationStore(root); assertNull(next.load(old.id).exchanges.single().generation)
        assertNull(next.load(branch.id).exchanges.single().generation); next.list(); next.recoverInterrupted()
        assertEquals(before, root.listFiles()!!.associate { it.name to it.readText() })
    }
    @Test fun regeneratedChildKeepsNewSettingsAndEarlierRepliesKeepTheirOriginalOnes() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val parent = conversation(); store.save(parent)
        val before = File(root, "${parent.id}.json").readBytes(); val changed = settings.copy(contextTokens = 2048, threads = 3)
        val child = store.createBranch(parent, 1, LocalBranchKind.REGENERATE, 2, generation = changed)
        assertNull(child.exchanges.first().generation); assertEquals(changed, child.exchanges.last().generation)
        assertEquals(child, LocalConversationStore(root).load(child.id)); assertArrayEquals(before, File(root, "${parent.id}.json").readBytes())
        val fork = store.createBranch(parent, 1, LocalBranchKind.FORK, 3)
        assertEquals(parent.exchanges, fork.exchanges); assertEquals(settings, fork.exchanges.last().generation)
    }
    @Test fun interruptedRecoveryRetainsExactRequestProvenance() {
        val root = temp.newFolder(); val store = LocalConversationStore(root)
        val chat = conversation().copy(exchanges = conversation().exchanges.mapIndexed { i, row -> if (i == 1) row.copy(state = LocalReplyState.GENERATING) else row })
        store.save(chat); val restarted = LocalConversationStore(root); restarted.recoverInterrupted()
        assertEquals(chat.copy(exchanges = chat.exchanges.dropLast(1) + chat.exchanges.last().copy(state = LocalReplyState.INTERRUPTED)), restarted.load(chat.id))
    }
    @Test fun invalidSettingsCannotReplaceHistoryOrPublishAChild() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val chat = conversation(); store.save(chat)
        val before = File(root, "${chat.id}.json").readBytes()
        val invalid = listOf(settings.copy(contextTokens = 511), settings.copy(contextTokens = 4097), settings.copy(threads = 0),
            settings.copy(threads = 5), settings.copy(maxOutputTokens = 0), settings.copy(maxOutputTokens = 513),
            settings.copy(runtime = settings.runtime!!.copy(sourceSha256 = "../invalid")))
        invalid.forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { store.save(chat.copy(exchanges = chat.exchanges.map {it.copy(generation = bad)})) }
            assertThrows(IllegalArgumentException::class.java) { store.createBranch(chat, 1, LocalBranchKind.REGENERATE, 2, generation = bad) }
            assertArrayEquals(before, File(root, "${chat.id}.json").readBytes()); assertEquals(1, root.listFiles()!!.size)
        }
    }
    @Test fun malformedOrFutureSettingsArePreservedInsteadOfGuessedOrDropped() {
        val root = temp.newFolder(); val store = LocalConversationStore(root); val chat = conversation(); store.save(chat)
        val file = File(root, "${chat.id}.json"); val valid = file.readText()
        val invalid = listOf(valid.replace("\"context\":1024", "\"context\":\"1024\""), valid.replace("\"threads\":2", "\"threads\":5"),
            valid.replace("\"output\":384", "\"output\":513"), valid.replace("\"runtime\":{", "\"future\":true,\"runtime\":{"),
            valid.replace("GREEDY_MODEL_METADATA_V1", "FUTURE_POLICY"), valid.replace("b".repeat(64), "bad"))
        invalid.forEach { content ->
            assertNotEquals(valid, content); file.writeText(content)
            assertThrows(LocalHistoryException::class.java) { store.load(chat.id) }; assertEquals(content, file.readText())
        }
    }
    @Test fun unknownRuntimeIsExplicitAndNativeSourceIdentityComesFromTheBuild() {
        val root = temp.newFolder(); val store = LocalConversationStore(root)
        val chat = conversation().copy(exchanges = listOf(conversation().exchanges.last().copy(generation = settings.copy(runtime = null))))
        store.save(chat); assertNull(store.load(chat.id).exchanges.single().generation!!.runtime)
        val native = NativeLocalInference(temp.newFolder(), "unused-no-library-load")
        assertEquals(org.hermesmobile.app.BuildConfig.LOCAL_RUNTIME_SOURCE_SHA256, native.runtimePolicy.sourceSha256)
        native.runtimePolicy.validate(); assertEquals(LocalSamplingPolicy.GREEDY_MODEL_METADATA_V1, native.runtimePolicy.sampling)
    }
}