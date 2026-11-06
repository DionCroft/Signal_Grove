package org.hermesmobile.app

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.ui.*
import org.hermesmobile.app.voice.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated presentation fixture: no microphone, speech engine, store reset or real network calls. */
class VoiceControlsTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val prefs=AppPreferences(chinese=false,pinyin=false,reducedMotion=true)
    private var reads=0;private var microphones=0;private var cancels=0;private var refreshes=0
    private var localOnly=true;private var networkVoices=false;private var settingsOpened=0
    private fun render(
        state:DictationSession=DictationSession(),block:Words?=null,error:Words?=null,
        speaking:Boolean=false,busy:Boolean=false,bilingual:Boolean=false,largeText:Boolean=false,appChrome:Boolean=false,
        speechSettingsAvailable:Boolean=true
    ) {
        val p=if(bilingual) prefs.copy(chinese=true,pinyin=true) else prefs
        compose.runOnUiThread {compose.activity.setContent {
            HermesAppTheme(p) {
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides if(largeText) Density(density.density,1.8f) else density) {
                    var reading by remember {mutableStateOf(false)}
                    var session by remember {mutableStateOf(state)}
                    val controls: @Composable ()->Unit = {
                        VoiceControls(p,reading,{reading=it;session=session.cancel(clearFailure=true)},session,"Typed message",{},
                            state.mode!=RecognitionMode.UNAVAILABLE,busy,speaking,block,error,
                            {session=session.configure(session.mode,it)},{localOnly=it},{networkVoices=it},{},
                            {microphones++},{cancels++},{},{reads++},{refreshes++},{},{settingsOpened++;speechSettingsAvailable})
                    }
                    if(appChrome) Scaffold(
                        topBar={Column(Modifier.statusBarsPadding().padding(horizontal=20.dp,vertical=8.dp)) {Text("Signal Grove",style=MaterialTheme.typography.titleLarge);Text(Words.Demo.localized(p),style=MaterialTheme.typography.labelMedium)}},
                        bottomBar={Box(Modifier.fillMaxWidth().navigationBarsPadding().height(80.dp))}
                    ) {padding->Box(Modifier.fillMaxSize().padding(padding)) {controls()}} else controls()
                }
            }
        }}
        compose.waitForIdle()
    }
    @Test fun readAloudRemainsReachableWhenOfflineDictationIsUnavailable() {
        render(DictationSession(failure=RecognitionFailure.CLIENT))
        compose.onNodeWithTag("voice-microphone").assertIsNotEnabled()
        compose.onNodeWithTag("voice-recognition-unavailable").assertExists()
        compose.onNodeWithTag("voice-read-tab").assertIsDisplayed().performClick()
        compose.onNodeWithTag("voice-dictation-error").assertDoesNotExist()
        compose.onNodeWithTag("voice-read-reply").assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(1,reads);assertEquals(0,microphones);assertTrue(localOnly);assertFalse(networkVoices)
    }
    @Test fun absentReplyHasAnExplicitReadAloudReason() {
        render(block=Words.SpeechReplyRequired)
        compose.onNodeWithTag("voice-read-tab").performClick()
        compose.onNodeWithTag("voice-read-reply").assertIsNotEnabled()
        compose.onNodeWithText(Words.SpeechReplyRequired.en).assertExists()
        assertEquals(0,reads)
    }
    @Test fun missingEngineOffersSetupWithoutUsingMicrophoneOrChangingPrivacy() {
        render(block=Words.SpeechEngineUnavailable,bilingual=true,largeText=true)
        compose.onNodeWithTag("voice-read-tab").performClick()
        compose.onNodeWithTag("voice-read-reply").assertIsNotEnabled()
        compose.onNodeWithTag("voice-speech-settings").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("voice-speech-setup-info").assertExists()
        compose.onNodeWithText(Words.SpeechSetupInfo.zh,substring=true).assertExists()
        assertEquals(1,settingsOpened);assertEquals(0,reads);assertEquals(0,microphones);assertTrue(localOnly);assertFalse(networkVoices)
    }
    @Test fun unavailableSettingsHasAManualRecoveryAndRefreshKeepsPrivacy() {
        render(block=Words.SpeechVoiceUnavailable,speechSettingsAvailable=false)
        compose.onNodeWithTag("voice-read-tab").performClick()
        compose.onNodeWithTag("voice-speech-settings").performScrollTo().performClick()
        compose.onNodeWithTag("voice-speech-settings-error").assertTextEquals(Words.SpeechSettingsUnavailable.en)
        compose.onNodeWithTag("voice-read-reply").assertIsNotEnabled()
        compose.onNodeWithTag("voice-refresh").performScrollTo().performClick()
        compose.onNodeWithTag("voice-speech-settings-error").assertDoesNotExist()
        assertEquals(1,settingsOpened);assertEquals(1,refreshes);assertEquals(0,reads);assertTrue(localOnly);assertFalse(networkVoices)
    }
    @Test fun missingOfflineVoiceDoesNotEnableOnlineVoices() {
        render(block=Words.SpeechVoiceUnavailable)
        compose.onNodeWithTag("voice-read-tab").performClick()
        compose.onNodeWithTag("voice-read-reply").assertIsNotEnabled()
        compose.onNodeWithTag("voice-network-voices").performScrollTo().assertIsOff()
        compose.onNodeWithTag("voice-read-reason").assertExists();assertFalse(networkVoices)
    }
    @Test fun stopRemainsAvailableWhileTheConversationIsBusy() {
        render(speaking=true,busy=true,block=Words.SpeechChatBusy)
        compose.onNodeWithTag("voice-read-tab").performClick()
        compose.onNodeWithTag("voice-read-reply").assertIsEnabled().performClick()
        compose.onNodeWithText(Words.StopSpeech.en).assertExists();assertEquals(1,reads)
        compose.onNodeWithTag("voice-speech-settings").assertIsNotEnabled();assertEquals(0,settingsOpened)
    }
    @Test fun processingDisablesMicrophoneButKeepsCancelAvailable() {
        render(DictationSession(RecognitionMode.ON_DEVICE).begin().let {it.processing(it.generation)})
        compose.onNodeWithTag("voice-microphone").assertIsNotEnabled()
        compose.onNodeWithTag("voice-cancel").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1,cancels);assertEquals(0,microphones)
    }
    @Test fun audioFailureDoesNotTellTheOwnerToGrantPermission() {
        render(DictationSession(RecognitionMode.ON_DEVICE,failure=RecognitionFailure.AUDIO))
        compose.onNodeWithTag("voice-dictation-error").performScrollTo().assertTextEquals(Words.SpeechAudioError.en)
        compose.onNodeWithText(Words.SpeechPermission.en).assertDoesNotExist()
        compose.onNodeWithTag("voice-refresh").performScrollTo().performClick();assertEquals(1,refreshes)
    }
    @Test fun primaryReadActionFitsTheInitialViewportWithAppChromeAndLargeBilingualText() {
        render(bilingual=true,largeText=true,appChrome=true)
        compose.onNodeWithTag("voice-read-tab").assertIsDisplayed().performClick()
        val action=compose.onNodeWithTag("voice-read-reply").assertIsDisplayed().assertIsEnabled()
        val bounds=action.getUnclippedBoundsInRoot()
        val viewport=compose.onNodeWithTag("voice-controls").getUnclippedBoundsInRoot()
        assertTrue("The entire primary read action must fit before scrolling",bounds.top>=viewport.top && bounds.bottom<=viewport.bottom && bounds.left>=viewport.left && bounds.right<=viewport.right)
        compose.onNodeWithTag("voice-screen").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle,Words.Voice.localized(prefs.copy(chinese=true,pinyin=true))))
        action.performClick();assertEquals(1,reads);assertEquals(0,microphones)
    }
    @Test fun bilingualLargeTextKeepsBothSectionsAndTheReadActionReachable() {
        render(bilingual=true,largeText=true)
        compose.onNodeWithTag("voice-read-tab").assertIsDisplayed().performClick()
        compose.onNodeWithTag("voice-read-reply").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithText(Words.ReadAloudInfo.zh,substring=true).assertExists()
        compose.onNodeWithTag("voice-dictation-tab").assertIsDisplayed().performClick()
        compose.onNodeWithTag("voice-local-only").performScrollTo().assertIsOn()
        assertEquals(1,reads);assertTrue(localOnly);assertFalse(networkVoices)
    }
}
