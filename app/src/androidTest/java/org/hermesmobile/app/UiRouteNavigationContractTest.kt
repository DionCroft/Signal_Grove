package org.hermesmobile.app

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Controlled input-boundary cases, separate from the real app keyboard transitions. */
class UiRouteNavigationContractTest {
    @get:Rule val compose=createComposeRule()
    @Test fun duplicateChatDescriptionsCannotRedirectTheTaggedRouteClick() {
        var routeClicks=0;var decoyClicks=0
        compose.setContent {
            var routeSelected by remember {mutableStateOf(false)}
            MaterialTheme {Column {
                Button({decoyClicks++},Modifier.semantics {contentDescription="Chat"}) {Text("First decoy")}
                Button({routeClicks++;routeSelected=true},Modifier.testTag("nav-chat").semantics {selected=routeSelected}) {Text("Route target")}
                Button({decoyClicks++},Modifier.semantics {contentDescription="Chat"}) {Text("Last decoy")}
            }}
        }
        compose.onAllNodesWithContentDescription("Chat").assertCountEquals(2)
        compose.openChatRouteOnce()
        compose.runOnIdle {assertEquals(1,routeClicks);assertEquals(0,decoyClicks)}
    }
    @Test fun anAcceptedClickWithoutRouteSelectionFailsWithoutASecondClick() {
        var routeClicks=0;var decoyClicks=0
        compose.setContent {MaterialTheme {Column {
            Button({routeClicks++},Modifier.testTag("nav-chat").semantics {selected=false}) {Text("Unchanged route")}
            Button({decoyClicks++},Modifier.semantics {contentDescription="Chat"}) {Text("Decoy")}
        }}}
        val failure=runCatching {compose.openChatRouteOnce()}.exceptionOrNull()
        assertTrue(failure is AssertionError)
        compose.runOnIdle {assertEquals(1,routeClicks);assertEquals(0,decoyClicks)}
    }

    @Test fun delayedEnabledHomeActionUsesItsTagAndNavigatesOnlyOnce() {
        var clicks=0;var decoyClicks=0
        val enabled=mutableStateOf(false)
        compose.setContent {
            var homeSelected by remember {mutableStateOf(false)}
            MaterialTheme {Column(Modifier.verticalScroll(rememberScrollState())) {
                Button({decoyClicks++}) {Text("New conversation")}
                Button({clicks++;homeSelected=true},enabled=enabled.value,modifier=Modifier.testTag("home-new-chat")) {Text("New conversation")}
                Text("Chat target",Modifier.testTag("nav-chat").semantics {selected=homeSelected})
            }}
        }
        compose.onNodeWithTag("home-new-chat").assertIsNotEnabled()
        compose.runOnUiThread {android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({enabled.value=true},250)}
        compose.openHomeNewChatOnce()
        compose.runOnIdle {assertEquals(1,clicks);assertEquals(0,decoyClicks)}
    }
    @Test fun aHomeCallbackWithoutNavigationFailsAfterOnlyOneClick() {
        var clicks=0
        compose.setContent {MaterialTheme {Column(Modifier.verticalScroll(rememberScrollState())) {
            Button({clicks++},Modifier.testTag("home-new-chat")) {Text("New conversation")}
            Text("Unselected",Modifier.testTag("nav-chat").semantics {selected=false})
        }}}
        val failure=runCatching {compose.openHomeNewChatOnce()}.exceptionOrNull()
        assertNotNull(failure)
        compose.runOnIdle {assertEquals(1,clicks)}
    }
    @Test fun aDisabledHomeActionNeverDispatchesInput() {
        var clicks=0
        compose.setContent {MaterialTheme {Column(Modifier.verticalScroll(rememberScrollState())) {
            Button({clicks++},enabled=false,modifier=Modifier.testTag("home-new-chat")) {Text("New conversation")}
            Text("Unselected",Modifier.testTag("nav-chat").semantics {selected=false})
        }}}
        compose.onNodeWithTag("home-new-chat").assertIsNotEnabled()
        assertNotNull(runCatching {compose.openHomeNewChatOnce()}.exceptionOrNull())
        compose.runOnIdle {assertEquals(0,clicks)}
    }
}
