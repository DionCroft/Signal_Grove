package org.hermesmobile.app

import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Presentation-only: no credential changes, microphone, inference or model switching. */
class ModelStatusUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private var refreshes=0;private var choices=0
    private fun payload(status:String="ready",second:Boolean=false)=buildJsonObject {
        put("object","signalgrove.model_runtime");put("schema_version",1);put("observed_at",1000);put("status",status)
        put("models",buildJsonArray {
            add(buildJsonObject {put("model","serving-model");put("provider","spark-fixture");put("state","ready");put("verified",true)})
            if(second) add(buildJsonObject {put("model","another-serving-model");put("provider","spark-other");put("state","ready");put("verified",true)})
        })
    }
    private fun render(supported:Boolean=true,failed:Boolean=false,loading:Boolean=false,large:Boolean=false,malformed:Boolean=false,ambiguous:Boolean=false) {
        val p=AppPreferences(chinese=large,pinyin=large,reducedMotion=true)
        val runtime=if(malformed) JsonObject(payload()+mapOf("status" to JsonPrimitive("unavailable"))) else payload(if(ambiguous) "ambiguous" else "ready",ambiguous)
        val state=DiscoveryState(loading=if(loading) setOf(DiscoveryResource.RuntimeModels) else emptySet(),results=mapOf(
            DiscoveryResource.RuntimeModels to runtime,
            DiscoveryResource.Models to buildJsonObject {put("model","saved-default");put("provider","saved-provider");put("providers",buildJsonArray {})}
        ),errors=if(failed) mapOf(DiscoveryResource.RuntimeModels to "fixture failure") else emptyMap(),fetchedAt=mapOf(DiscoveryResource.RuntimeModels to 1000,DiscoveryResource.Models to 1000))
        val caps=HermesCapabilities("hermes-agent",buildJsonObject {put("model_runtime_status_v1",supported)},buildJsonObject {
            put("model_options",buildJsonObject {});put("model_runtime_status",buildJsonObject {put("method","GET");put("path","/api/model/runtime-status")})
        })
        compose.runOnUiThread {compose.activity.setContent {HermesAppTheme(p) {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides if(large) Density(density.density,1.8f) else density) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ModelStatusCard(state,caps,"connected",true,RuntimeChoice("requested-model","request-provider"),
                        ConversationModelStatus(RuntimeChoice("requested-model"),RuntimeChoice("last-reported-model","last-provider"),123000,true),
                        2000,{refreshes++},{choices++},choiceEnabled=!loading)
                }
            }
        }}}
        compose.waitForIdle()
    }
    @Test fun chatStatusOpensFromTheExistingHeaderAndShowsBothModels() {
        val vm=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
        compose.waitUntil(15000) {!vm.connecting.value}
        compose.runOnUiThread {vm.setDemo(true)}
        compose.waitUntil(15000) {!vm.connecting.value && vm.preferences.value.demo}
        val before=vm.preferences.value
        try {
            compose.runOnUiThread {vm.updatePreferences(before.copy(chinese=true,pinyin=true))}
            compose.waitUntil(5000) {vm.preferences.value.chinese && vm.preferences.value.pinyin}
            compose.onAllNodesWithContentDescription(Words.Chat.localized(vm.preferences.value),useUnmergedTree=true).onLast().performClick()
            compose.onNodeWithTag("chat-model-status-open").assertIsDisplayed().performClick()
            compose.waitUntil(15000) {vm.discovery.value.results.containsKey(DiscoveryResource.RuntimeModels)}
            compose.onNodeWithTag("observed-model-demo").assertExists()
            val model=compose.onAllNodesWithText("demo-model").onFirst().assertIsDisplayed().fetchSemanticsNode()
            val viewport=compose.onNodeWithTag("model-status-dialog-viewport").fetchSemanticsNode().boundsInRoot
            assertTrue("The whole serving model must fit in the initial dialog viewport",
                model.boundsInRoot.height+1f>=model.size.height && model.boundsInRoot.top>=viewport.top-1f && model.boundsInRoot.bottom<=viewport.bottom+1f)
            compose.onNodeWithTag("model-request-selection").assertExists()
            compose.onNodeWithTag("model-configured-default").assertExists()
            compose.onNodeWithText(Words.Close.localized(vm.preferences.value)).performClick()
            compose.onNodeWithTag("chat-model-status-open").assertIsDisplayed()
        } finally {compose.runOnUiThread {vm.updatePreferences(before)}}
    }
    @Test fun servingDefaultRequestAndLastReportedAreSeparate() {
        render()
        compose.onNodeWithTag("observed-model-spark-fixture").assertExists()
        compose.onNodeWithTag("model-configured-default").assertTextEquals("saved-default")
        compose.onNodeWithTag("model-request-selection").assertTextEquals("requested-model")
        compose.onNodeWithTag("model-conversation-reported").assertTextEquals("last-reported-model")
        compose.onNodeWithText("hermes-agent").assertDoesNotExist()
        assertEquals(0,choices);assertEquals(0,refreshes)
    }
    @Test fun unsupportedServerDoesNotPretendItsSavedModelIsLoaded() {
        render(supported=false)
        compose.onNodeWithTag("model-runtime-unsupported").assertExists()
        compose.onNodeWithTag("runtime-model-observations").assertDoesNotExist()
        compose.onNodeWithTag("model-configured-default").assertTextEquals("saved-default")
    }
    @Test fun failedRefreshKeepsTheObservationClearlyCached() {
        render(failed=true)
        compose.onNodeWithTag("model-observation-cached").assertExists()
        compose.onNodeWithTag("observed-model-spark-fixture").assertExists()
        compose.onNodeWithTag("model-observed-at").assertExists()
        compose.onNodeWithTag("model-host-observed-at").assertExists()
    }
    @Test fun invalidObserverPayloadDoesNotRenderAServingModel() {
        render(malformed=true)
        compose.onNodeWithTag("model-runtime-state").assertTextEquals(Words.ModelObservationInvalid.en)
        compose.onNodeWithTag("observed-model-spark-fixture").assertDoesNotExist()
    }
    @Test fun refreshAndRequestChoiceAreRealIndependentActions() {
        render()
        compose.onNodeWithTag("model-status-refresh").performScrollTo().performClick()
        compose.onNodeWithTag("model-choose-request").performScrollTo().performClick()
        assertEquals(1,refreshes);assertEquals(1,choices)
    }
    @Test fun inFlightReadDisablesDuplicateRefresh() {
        render(loading=true)
        compose.onNodeWithTag("model-status-refresh").assertIsNotEnabled()
        compose.onNodeWithTag("model-choose-request").assertIsNotEnabled()
        compose.onNodeWithTag("model-choice-busy").assertExists()
        compose.onNodeWithTag("observed-model-spark-fixture").assertExists()
        assertEquals(0,refreshes)
    }
    @Test fun largeBilingualStatusKeepsFullValuesAndChoiceReachable() {
        render(large=true)
        compose.onNodeWithTag("model-configured-default").performScrollTo().assertIsDisplayed().assertTextEquals("saved-default")
        compose.onNodeWithTag("model-conversation-reported").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("model-choose-request").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText(Words.ModelStatusInfo.zh,substring=true).assertExists();assertEquals(1,choices)
    }
    @Test fun multipleReadyEndpointsDoNotAutomaticallyChooseAModel() {
        render(ambiguous=true)
        compose.onNodeWithTag("model-runtime-state").assertTextEquals(Words.ModelMultipleReady.en)
        compose.onNodeWithTag("observed-model-spark-fixture").assertExists();compose.onNodeWithTag("observed-model-spark-other").assertExists()
        assertEquals(0,choices)
    }
}
