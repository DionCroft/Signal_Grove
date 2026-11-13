package org.hermesmobile.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** Observe control readiness, issue one click, then require the route postcondition. */
fun ComposeContentTestRule.openChatRouteOnce() {
    waitUntil(5000) {
        val targets=onAllNodesWithTag("nav-chat").fetchSemanticsNodes()
        targets.size==1 && targets.single().boundsInRoot.let {it.width>0f && it.height>0f}
    }
    onNodeWithTag("nav-chat").assertIsDisplayed().assertHasClickAction().performClick()
    onNodeWithTag("nav-chat").assertIsSelected()
}
