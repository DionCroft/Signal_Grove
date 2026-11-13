package org.hermesmobile.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class UiFixtureDiagnosticsTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication

    @Test fun readyMessagesOnHomeDoNotProveChatIsRenderedAndSnapshotsExcludePrivateValues()=runBlocking {
        compose.waitUntil(10000) {!vm.connecting.value}
        compose.runOnUiThread {vm.setDemo(true)}
        compose.waitUntil(10000) {!vm.connecting.value && vm.preferences.value.demo}
        val previousAppearance=vm.preferences.value
        compose.runOnUiThread {vm.updatePreferences(vm.preferences.value.copy(chinese=true,pinyin=true))}
        compose.waitUntil(5000) {vm.preferences.value.chinese && vm.preferences.value.pinyin}
        compose.onNodeWithTag("nav-home").performClick();compose.onNodeWithTag("nav-home").assertIsSelected()
        val canary="private-fixture-canary-${UUID.randomUUID()}"
        val c=Conversation("key-$canary","demo","api-$canary",title="title-$canary",transport="demo")
        val messages=(0..15).map {ChatMessage("message-$it-$canary",c.key,if(it==15) "assistant" else "user","content-$canary",timestamp=it.toLong())}
        try {
            app.database.dao().save(c);app.database.dao().saveMessages(messages)
            compose.runOnUiThread {vm.select(c.key);vm.changeDraft("draft-$canary")}
            // These are the original scroll test's readiness predicates. They
            // can all hold on Home; no longer wait is an answer to that state.
            compose.waitUntil(5000) {vm.messages.value.size==16 && vm.selectedConversation.value?.key==c.key}
            compose.waitForIdle();compose.onNodeWithTag("chat-messages").assertDoesNotExist()
            val folder=File(compose.activity.getExternalFilesDir(null),"qa")
            val beforeFiles=folder.listFiles().orEmpty().map {it.name}.toSet()
            val original=AssertionError("original-$canary")
            val wrapped=retainUiFixtureFailure(compose,compose.activity,vm,original,
                FixtureFailureKind.ControlledCounterexample,c.key,16,1)
            assertSame(original,wrapped.cause)
            val encoded=wrapped.message!!.substringAfter("safe fixture snapshot=")
            assertFalse(encoded.contains(canary));assertFalse(wrapped.message!!.contains("original-$canary"))
            val home=JSONObject(encoded)
            assertFalse(home.optBoolean("snapshotUnavailable"));assertFalse(home.getBoolean("atomic"))
            assertTrue(home.getBoolean("selectionMatchesFixture"));assertTrue(home.getBoolean("selectedConversationMatchesFixture"))
            assertTrue(home.getBoolean("messageCountMatchesFixture"));assertEquals(16,home.getInt("messageCount"))
            assertEquals(1,home.getJSONObject("selectedNavigation").getInt("Home"))
            assertEquals(0,home.getJSONObject("selectedNavigation").getInt("Chat"))
            assertEquals(0,home.getInt("chatMessageLists"));assertEquals(0,home.getInt("runtimePickers"))
            assertTrue(home.getBoolean("ruleActivityIsResumed"));assertEquals("RESUMED",home.getString("activityLifecycle"))
            val retained=folder.listFiles().orEmpty().filter {it.name !in beforeFiles && it.name.startsWith("ui-fixture-counterexample-") && it.readText()==encoded}
            assertEquals(1,retained.size);assertTrue(retained.single().length()<=16384)
            // Now actually render Chat using its stable route tag and observe
            // the opposite result, with exactly the same selected data.
            compose.onNodeWithTag("nav-chat").performClick()
            compose.waitUntil(5000) {compose.onAllNodesWithTag("chat-messages").fetchSemanticsNodes().size==1}
            val chat=captureUiFixtureSnapshot(compose,compose.activity,vm,c.key,16)
            assertEquals(1,chat.getJSONObject("selectedNavigation").getInt("Chat"))
            assertEquals(0,chat.getJSONObject("selectedNavigation").getInt("Home"))
            assertEquals(1,chat.getInt("chatMessageLists"));assertTrue(chat.getBoolean("messageCountMatchesFixture"))
            assertFalse(chat.toString().contains(canary));assertTrue(chat.getBoolean("chinese"));assertTrue(chat.getBoolean("pinyin"))
            fun verifyLeaves(value:Any) {
                when(value) {
                    is JSONObject -> value.keys().forEach {verifyLeaves(value.get(it))}
                    is Boolean,is Int -> Unit
                    is String -> assertTrue(value in setOf("DESTROYED","INITIALIZED","CREATED","STARTED","RESUMED"))
                    else -> fail("Unexpected diagnostic value type")
                }
            }
            verifyLeaves(home);verifyLeaves(chat)
        } finally {
            compose.runOnUiThread {vm.newChat()};app.database.dao().deleteConversation(c.key)
            compose.runOnUiThread {vm.updatePreferences(vm.preferences.value.copy(chinese=previousAppearance.chinese,pinyin=previousAppearance.pinyin))}
            compose.waitUntil(5000) {vm.preferences.value.chinese==previousAppearance.chinese && vm.preferences.value.pinyin==previousAppearance.pinyin}
        }
    }
}
