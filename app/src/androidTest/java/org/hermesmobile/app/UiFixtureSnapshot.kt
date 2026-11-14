package org.hermesmobile.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.hermesmobile.app.ui.HermesViewModel
import org.hermesmobile.app.ui.Words
import org.hermesmobile.app.ui.localized
import org.json.JSONObject
import java.io.File
import java.util.UUID

// Test-only observations. Queries can advance Compose; this is deliberately
// labelled non-atomic and never used to decide that a failed action succeeded.
// Selectors and JSON fields are fixed. No text, URI, credential or ID is copied.
internal fun captureUiFixtureSnapshot(
    compose:AndroidComposeTestRule<*,MainActivity>,activity:MainActivity,vm:HermesViewModel,
    expectedConversationKey:String?=null,expectedMessageCount:Int?=null
):JSONObject {
    fun count(matcher:SemanticsMatcher)=runCatching {
        compose.onAllNodes(matcher,useUnmergedTree=true).fetchSemanticsNodes().size.coerceAtMost(1000)
    }.getOrDefault(-1)
    val nativeSampleAt=android.os.SystemClock.uptimeMillis()
    val nativeWindows=captureNativeFixtureWindowCounts()
    val preferences=vm.preferences.value
    val snapshot=JSONObject().apply {
        put("version",1);put("atomic",false)
        put("demo",preferences.demo);put("chinese",preferences.chinese);put("pinyin",preferences.pinyin)
        put("connecting",vm.connecting.value);put("busy",vm.busy.value);put("controlling",vm.controlBusy.value)
        put("selectionIsBlank",vm.selected.value.isBlank())
        put("expectedSelectionProvided",expectedConversationKey!=null)
        put("selectionMatchesFixture",expectedConversationKey!=null && vm.selected.value==expectedConversationKey)
        put("selectedConversationMatchesFixture",expectedConversationKey!=null && vm.selectedConversation.value?.key==expectedConversationKey)
        put("messageCount",vm.messages.value.size.coerceAtMost(1000))
        put("messageCountCapped",vm.messages.value.size>1000)
        put("expectedMessageCountProvided",expectedMessageCount!=null)
        put("expectedMessageCount",expectedMessageCount?.coerceIn(0,1000) ?: -1)
        put("messageCountMatchesFixture",expectedMessageCount!=null && vm.messages.value.size==expectedMessageCount)
    }
    compose.runOnUiThread {
        val resumed=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
        val insets=androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
        snapshot.put("activityLifecycle",activity.lifecycle.currentState.name)
        snapshot.put("activityFinishing",activity.isFinishing);snapshot.put("activityDestroyed",activity.isDestroyed)
        snapshot.put("activityHasWindowFocus",activity.hasWindowFocus())
        snapshot.put("resumedActivityCount",resumed.size.coerceAtMost(1000))
        snapshot.put("ruleActivityIsResumed",resumed.any {it===activity})
        snapshot.put("resumedMainActivityCount",resumed.count {it is MainActivity}.coerceAtMost(1000))
        snapshot.put("resumedLocalAiActivityCount",resumed.count {it is LocalAiActivity}.coerceAtMost(1000))
        snapshot.put("resumedOtherActivityCount",resumed.count {it !is MainActivity && it !is LocalAiActivity}.coerceAtMost(1000))
        snapshot.put("activityHasTextEditorFocus",activity.currentFocus?.onCheckIsTextEditor()==true)
        snapshot.put("imeBottomPx",insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom?.coerceIn(0,100000) ?: -1)
        snapshot.put("imeVisible",insets?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) ?: false)
    }
    val navigation=JSONObject();val selected=JSONObject()
    for(route in listOf("Home","Chat","Tasks","Automation","Voice","Settings")) {
        val matcher=hasTestTag("nav-${route.lowercase()}")
        navigation.put(route,count(matcher));selected.put(route,count(matcher and isSelected()))
    }
    snapshot.put("navigationNodes",navigation);snapshot.put("selectedNavigation",selected)
    snapshot.put("composeRoots",count(isRoot()))
    snapshot.put("chatMessageLists",count(hasTestTag("chat-messages")))
    snapshot.put("runtimePickers",count(hasTestTag("runtime-picker-list")))
    snapshot.put("emptyChatPrompts",count(hasText(Words.NoChats.localized(preferences))))
    snapshot.put("chatDescriptionNodes",count(hasContentDescription(Words.Chat.localized(preferences))))
    snapshot.put("demoApprovalNodes",count(hasText(Words.DemoApproval.localized(preferences))))
    snapshot.put("demoClarificationNodes",count(hasText(Words.DemoClarification.localized(preferences))))
    snapshot.put("homeNewConversationNodes",count(hasText("New conversation")))
    snapshot.put("settingsDescriptionNodes",count(hasContentDescription(Words.Settings.localized(preferences))))
    snapshot.put("settingsEnglishDescriptionNodes",count(hasContentDescription(Words.Settings.en,substring=true)))
    snapshot.put("settingsChineseDescriptionNodes",count(hasContentDescription(Words.Settings.zh,substring=true)))
    snapshot.put("settingsPinyinDescriptionNodes",count(hasContentDescription(Words.Settings.pin,substring=true)))
    snapshot.put("localPhoneDraftNodes",count(hasTestTag("phone-draft")))
    snapshot.put("phoneDraftReviewNodes",count(hasTestTag("phone-draft-review")))
    snapshot.put("setupGuideNodes",count(hasTestTag("setup-guide")))
    snapshot.put("nativeWindows",nativeWindows)
    val separation=(android.os.SystemClock.uptimeMillis()-nativeSampleAt).coerceAtLeast(0)
    snapshot.put("nativeWindowReadSeparationMs",separation.coerceAtMost(10000).toInt())
    snapshot.put("nativeWindowReadSeparationCapped",separation>10000)
    return snapshot
}

// Read window types/geometry only; never node text, package names, URI or IDs.
// No idle wait or input is added. The original failed interaction is never retried.
private fun captureNativeFixtureWindowCounts():JSONObject {
    val unavailable=JSONObject().put("available",false).put("readCompleted",false).put("reportedWindowCount",-1).put("inputMethodCount",-1)
        .put("focusedApplicationCount",-1).put("activeApplicationCount",-1).put("inputMethodTopPx",-1)
    return runCatching {
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val info=automation.serviceInfo;val before=info.flags
        try {
            info.flags=before or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo=info
            val windows=automation.windows;check(windows.size<=128)
            if(windows.isEmpty()) return@runCatching unavailable.put("readCompleted",true).put("reportedWindowCount",0)
            val applications=windows.filter {it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION}
            val inputMethods=windows.filter {it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD}
            val rectangles=inputMethods.map {window->android.graphics.Rect().also {window.getBoundsInScreen(it)}}.filter {!it.isEmpty}
            JSONObject().put("available",true).put("readCompleted",true).put("reportedWindowCount",windows.size).put("inputMethodCount",inputMethods.size)
                .put("focusedApplicationCount",applications.count {it.isFocused})
                .put("activeApplicationCount",applications.count {it.isActive})
                .put("inputMethodTopPx",rectangles.minOfOrNull {it.top}?.coerceIn(-100000,100000) ?: -1)
        } finally {info.flags=before;automation.serviceInfo=info}
    }.getOrElse {unavailable}
}

internal enum class FixtureFailureKind(val filePrefix:String,val label:String) {
    RuntimeMessages("runtime-message-list-failure","Message-list lookup failed"),
    PhoneDraftNavigation("phone-draft-navigation-failure","Phone draft navigation failed"),
    SetupGuideNavigation("setup-guide-navigation-failure","Setup guide navigation failed"),
    ControlledCounterexample("ui-fixture-counterexample","Controlled fixture assertion")
}

internal fun retainUiFixtureFailure(
    compose:AndroidComposeTestRule<*,MainActivity>,activity:MainActivity,vm:HermesViewModel,
    original:AssertionError,kind:FixtureFailureKind,expectedConversationKey:String?,
    expectedMessageCount:Int,positionRead:Int
):AssertionError {
    val snapshot=runCatching {captureUiFixtureSnapshot(compose,activity,vm,expectedConversationKey,expectedMessageCount)}
        .getOrElse {JSONObject().put("version",1).put("snapshotUnavailable",true)}
    snapshot.put("positionRead",positionRead.coerceIn(0,3))
    val diagnostic=snapshot.toString()
    runCatching {
        check(diagnostic.toByteArray(Charsets.UTF_8).size<=16384)
        val file=File(activity.getExternalFilesDir(null),"qa/${kind.filePrefix}-${UUID.randomUUID()}.json")
        file.parentFile!!.mkdirs();file.writeText(diagnostic)
    }
    return AssertionError("${kind.label}; safe fixture snapshot=$diagnostic",original)
}
