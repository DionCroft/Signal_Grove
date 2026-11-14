package org.hermesmobile.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
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
            compose.openChatRouteOnce()
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

    @Test fun homePickerKeyboardDismissalUsesTheUniqueChatRoute()=runBlocking {
        compose.waitUntil(10000) {!vm.connecting.value}
        compose.runOnUiThread {vm.setDemo(true)}
        compose.waitUntil(10000) {!vm.connecting.value && vm.preferences.value.demo}
        val previous=vm.preferences.value
        val trace=JSONObject().put("version",2).put("atomic",false).put("routeTarget","nav-chat")
        val cases=org.json.JSONArray();trace.put("cases",cases)
        var stage="prepare"
        var completed=false
        try {
            compose.runOnUiThread {vm.updatePreferences(vm.preferences.value.copy(chinese=true,pinyin=true))}
            compose.waitUntil(5000) {vm.preferences.value.chinese && vm.preferences.value.pinyin}
            for(action in listOf("apply","cancel")) {
                val row=JSONObject().put("action",action);cases.put(row)
                val c=Conversation("transition-${UUID.randomUUID()}","demo","api-transition",title="Keyboard navigation fixture",transport="demo")
                val decoys=(0..3).map {Conversation("route-decoy-${UUID.randomUUID()}","demo","api-transition",title="Synthetic recent conversation",transport="demo")}
                try {
                    stage="prepare-$action"
                    compose.runOnUiThread {vm.newChat();vm.chooseRuntime(vm.profileScope.value,RuntimeChoice())}
                    compose.waitUntil(5000) {!vm.controlBusy.value && vm.requestedRuntime.value.isDefault}
                    app.database.dao().save(c);decoys.forEach {app.database.dao().save(it)}
                    compose.waitUntil(5000) {vm.conversations.value.count {it.key==c.key || decoys.any {d->d.key==it.key}}==5}
                    app.database.dao().saveMessages((0..15).map {ChatMessage("transition-$it",c.key,if(it==15) "assistant" else "user","Synthetic navigation note",timestamp=it.toLong())})
                    compose.onNodeWithTag("nav-home").performClick()
                    compose.onNodeWithTag("nav-home").assertIsSelected()
                    row.put("homeBeforePicker",captureUiFixtureSnapshot(compose,compose.activity,vm,c.key,16))
                    stage="open-picker-$action"
                    compose.onNodeWithTag("home-model-status").performScrollTo().performClick()
                    compose.onNodeWithTag("model-choose-request").performScrollTo().performClick()
                    compose.onNodeWithTag("runtime-picker-list").performScrollToNode(hasTestTag("runtime-manual"))
                    compose.onNodeWithTag("runtime-manual").performClick()
                    compose.onNodeWithTag("runtime-picker-list").performScrollToNode(hasTestTag("runtime-model"))
                    compose.onNodeWithTag("runtime-model").performTextReplacement("transition-model")
                    compose.onNodeWithTag("runtime-picker-list").performScrollToNode(hasTestTag("runtime-provider"))
                    compose.onNodeWithTag("runtime-provider").performClick().performTextReplacement("transition-provider")
                    stage="require-native-keyboard-$action"
                    compose.waitUntil(10000) {runCatching {focusedDialogViewport(requireKeyboard=true)!=null}.getOrDefault(false)}
                    row.put("nativeDialogKeyboardVerified",true)
                    row.put("pickerBeforeDismiss",captureUiFixtureSnapshot(compose,compose.activity,vm,c.key,16))
                    stage="dismiss-$action"
                    if(action=="apply") compose.onNodeWithTag("runtime-apply").performClick()
                    else compose.onNodeWithText(Words.Cancel.localized(vm.preferences.value)).performClick()
                    // A description can match offscreen Recent actions as well as navigation.
                    // Wait for the unique visible route target, click once and require selection.
                    stage="tagged-chat-click-$action"
                    compose.openChatRouteOnce()
                    compose.runOnUiThread {row.put("selectionAccepted",vm.select(c.key)!=null)}
                    stage="data-wait-$action"
                    compose.waitUntil(5000) {vm.messages.value.size==16 && vm.selectedConversation.value?.key==c.key}
                    compose.waitForIdle()
                    stage="message-lookup-$action"
                    try {compose.onNodeWithTag("chat-messages").fetchSemanticsNode()}
                    catch(original:AssertionError) {throw retainUiFixtureFailure(compose,compose.activity,vm,original,FixtureFailureKind.RuntimeMessages,c.key,16,1)}
                    val rendered=captureUiFixtureSnapshot(compose,compose.activity,vm,c.key,16)
                    row.put("afterRouteClick",rendered)
                    assertEquals(1,rendered.getJSONObject("selectedNavigation").getInt("Chat"))
                    assertEquals(1,rendered.getInt("chatMessageLists"))
                    compose.waitUntil(5000) {!vm.controlBusy.value}
                    row.put("runtimeChoiceExpected",if(action=="apply") vm.requestedRuntime.value.model=="transition-model" && vm.requestedRuntime.value.provider=="transition-provider" else vm.requestedRuntime.value.isDefault)
                    assertTrue(row.getBoolean("runtimeChoiceExpected"))
                    row.put("passed",true)
                } catch(error:Throwable) {
                    row.put("failedStage",stage)
                    row.put("failureState",runCatching {captureUiFixtureSnapshot(compose,compose.activity,vm,c.key,16)}.getOrElse {JSONObject().put("snapshotUnavailable",true)})
                    throw error
                } finally {
                    // Recovery is separate from the measured transition and
                    // never reruns an uncertain click or message submission.
                    runCatching {closeFocusedKeyboard()}
                    compose.runOnUiThread {vm.newChat();vm.chooseRuntime(vm.profileScope.value,RuntimeChoice())}
                    compose.waitUntil(5000) {!vm.controlBusy.value && vm.requestedRuntime.value.isDefault}
                    app.database.dao().deleteConversation(c.key);decoys.forEach {app.database.dao().deleteConversation(it.key)}
                }
            }
            completed=true
        } finally {
            trace.put("completed",completed).put("lastStage",stage)
            val retention=runCatching {
                val text=trace.toString();check(text.toByteArray(Charsets.UTF_8).size<=32768)
                val file=File(compose.activity.getExternalFilesDir(null),"qa/chat-route-transition-${UUID.randomUUID()}.json")
                file.parentFile!!.mkdirs();file.writeText(text)
            }
            if(completed) retention.getOrThrow()
            compose.runOnUiThread {vm.updatePreferences(vm.preferences.value.copy(chinese=previous.chinese,pinyin=previous.pinyin))}
            compose.waitUntil(5000) {vm.preferences.value.chinese==previous.chinese && vm.preferences.value.pinyin==previous.pinyin}
        }
    }

    @Test fun failureSnapshotPrecedesCallerCleanupAndIdentifiesTheForegroundLocalActivity() {
        compose.waitUntil(10000) {!vm.connecting.value}
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        fun resumedLocal():LocalAiActivity? {
            val result=java.util.concurrent.atomic.AtomicReference<LocalAiActivity?>()
            instrumentation.runOnMainSync {
                result.set(androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<LocalAiActivity>().singleOrNull())
            }
            return result.get()
        }
        var local:LocalAiActivity?=null
        val original=AssertionError("Controlled navigation observation")
        var retained:AssertionError?=null
        try {
            compose.runOnUiThread {compose.activity.startActivity(android.content.Intent(app,LocalAiActivity::class.java))}
            compose.waitUntil(10000) {resumedLocal()!=null};local=resumedLocal()!!
            compose.waitUntil(10000) {compose.onAllNodesWithTag("phone-draft").fetchSemanticsNodes().size==1}
            try {
                throw retainUiFixtureFailure(compose,compose.activity,vm,original,
                    FixtureFailureKind.ControlledCounterexample,null,0,0)
            } catch(captured:AssertionError) {retained=captured}
        } finally {
            // This cleanup happens after the snapshot; the assertion is retained,
            // never rerun or converted into a successful navigation action.
            instrumentation.runOnMainSync {local?.finish()}
        }
        val wrapped=checkNotNull(retained);assertSame(original,wrapped.cause)
        val observed=JSONObject(wrapped.message!!.substringAfter("safe fixture snapshot="))
        assertFalse(observed.optBoolean("snapshotUnavailable"));assertFalse(observed.getBoolean("ruleActivityIsResumed"))
        assertEquals(1,observed.getInt("resumedLocalAiActivityCount"));assertEquals(0,observed.getInt("resumedMainActivityCount"))
        assertEquals(1,observed.getInt("localPhoneDraftNodes"));assertEquals(0,observed.getJSONObject("navigationNodes").getInt("Chat"))
        val nativeWindows=observed.getJSONObject("nativeWindows")
        assertTrue(nativeWindows.getBoolean("readCompleted"))
        if(nativeWindows.getInt("reportedWindowCount")==0) {
            // Enabling native window reporting need not populate its first read.
            // An empty read is indeterminate, never proof of an absent IME.
            assertFalse(nativeWindows.getBoolean("available"))
            for(field in listOf("inputMethodCount","focusedApplicationCount","activeApplicationCount","inputMethodTopPx")) assertEquals(-1,nativeWindows.getInt(field))
        } else {
            assertTrue(nativeWindows.getBoolean("available"))
            assertTrue(nativeWindows.getInt("reportedWindowCount")>0)
            assertTrue(nativeWindows.getInt("focusedApplicationCount") in 0..nativeWindows.getInt("reportedWindowCount"))
        }
        compose.waitUntil(10000) {compose.activity.lifecycle.currentState==androidx.lifecycle.Lifecycle.State.RESUMED}
        val after=captureUiFixtureSnapshot(compose,compose.activity,vm)
        assertTrue(after.getBoolean("ruleActivityIsResumed"));assertEquals(0,after.getInt("resumedLocalAiActivityCount"))
    }
}
