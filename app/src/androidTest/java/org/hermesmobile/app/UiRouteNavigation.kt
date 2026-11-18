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


/** Wait for the actual enabled Home action; never retry a click to obtain navigation. */
fun ComposeContentTestRule.openHomeNewChatOnce() {
    val action=onNodeWithTag("home-new-chat")
    action.performScrollTo()
    var previous:androidx.compose.ui.geometry.Rect?=null
    var stableAt=android.os.SystemClock.uptimeMillis()
    waitUntil(5000) {
        val target=onAllNodesWithTag("home-new-chat").fetchSemanticsNodes().singleOrNull()
        val bounds=target?.boundsInRoot
        val enabled=target!=null && !target.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        val now=android.os.SystemClock.uptimeMillis()
        if(!enabled || bounds==null || bounds.width<=0f || bounds.height<=0f || bounds!=previous) {
            previous=bounds;stableAt=now;false
        } else now-stableAt>=100
    }
    action.assertIsDisplayed().assertIsEnabled().assertHasClickAction().performClick()
    waitUntil(5000) {
        onAllNodesWithTag("nav-chat").fetchSemanticsNodes().singleOrNull()
            ?.config?.let {it.contains(androidx.compose.ui.semantics.SemanticsProperties.Selected) && it[androidx.compose.ui.semantics.SemanticsProperties.Selected]}==true
    }
    onNodeWithTag("nav-chat").assertIsSelected()
}
