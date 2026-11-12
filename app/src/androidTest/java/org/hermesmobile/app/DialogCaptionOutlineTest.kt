package org.hermesmobile.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.hermesmobile.app.ui.HermesAppTheme
import org.hermesmobile.app.data.AppPreferences

/** Actual rendered shape counterexamples; no server, owner data or pointer injection. */
class DialogCaptionOutlineTest {
    @get:Rule val compose=createComposeRule()
    private var outlineFactory:((Size)->Outline)?=null
    private fun render(cut:Int) {
        compose.setContent {
            HermesAppTheme(AppPreferences(reducedMotion=true)) {
                val shape=CutCornerShape(cut.dp);val density=LocalDensity.current;val direction=LocalLayoutDirection.current
                SideEffect {outlineFactory={size->shape.createOutline(size,direction,density)}}
                TextButton({},Modifier.width(300.dp).height(120.dp).testTag("caption-action"),
                    shape=shape,contentPadding=PaddingValues(4.dp)) {
                    Text("SIGNAL",Modifier.weight(1f).align(Alignment.Top),fontSize=24.sp)
                }
            }
        }
        compose.waitForIdle()
    }
    private fun outline():Outline {
        val bounds=compose.onNodeWithTag("caption-action").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        return checkNotNull(outlineFactory)(Size(bounds.width,bounds.height))
    }
    @Test fun cutCornerCaptionUsesItsRenderedOutlineInsteadOfACapsuleAssumption() {
        render(4)
        val error=assertThrows(AssertionError::class.java) {compose.assertDialogActionCaptionVisible("caption-action")}
        assertTrue(error.message.orEmpty().contains("rounded action shape"))
        compose.assertDialogActionCaptionVisible("caption-action",outline())
    }
    @Test fun actualCutCornerGlyphClippingIsStillRefused() {
        render(40)
        val error=assertThrows(AssertionError::class.java) {compose.assertDialogActionCaptionVisible("caption-action",outline())}
        assertTrue(error.message.orEmpty().contains("supplied action outline"))
    }
}
