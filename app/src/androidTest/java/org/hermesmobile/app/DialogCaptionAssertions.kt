package org.hermesmobile.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.*

/** Assert rendered glyphs, including every translated line, fit their visible action. */
fun ComposeContentTestRule.assertDialogActionCaptionVisible(tag:String) {
    waitForIdle()
    val button=onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    val text=onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult) and hasAnyAncestor(hasTestTag(tag)),useUnmergedTree=true)
    assertEquals("One caption layout is required for "+tag,1,text.fetchSemanticsNodes().size)
    val node=text[0].fetchSemanticsNode();val layouts=mutableListOf<TextLayoutResult>()
    text[0].performSemanticsAction(SemanticsActions.GetTextLayoutResult) {get->assertTrue(get(layouts))}
    val layout=layouts.single()
    // Paragraph width is its retained layout constraint, not the rendered glyph extent.
    // Check the actual final caption clip and shape below, including cached layouts.
    assertFalse("Caption height overflows: "+tag,layout.didOverflowHeight)
    for(line in 0 until layout.lineCount) assertFalse("Caption line ellipsized: "+tag,layout.isLineEllipsized(line))
    val value=layout.layoutInput.text.text;assertTrue(value.isNotBlank())
    val visible=node.boundsInRoot;val origin=node.positionInRoot
    val left=maxOf(button.left,visible.left);val top=maxOf(button.top,visible.top);val right=minOf(button.right,visible.right);val bottom=minOf(button.bottom,visible.bottom)
    // Default Material3 text buttons use CircleShape: bounds alone miss its clipping.
    val radius=minOf(button.width,button.height)/2f
    fun insideShape(x:Float,y:Float):Boolean {
        val closestX=x.coerceIn(button.left+radius,button.right-radius)
        val closestY=y.coerceIn(button.top+radius,button.bottom-radius)
        val dx=x-closestX;val dy=y-closestY
        return dx*dx+dy*dy<=(radius+1f)*(radius+1f)
    }
    for(i in value.indices) {
        if(value[i].isWhitespace() || Character.isLowSurrogate(value[i])) continue
        val glyph=layout.getBoundingBox(i)
        for(x in listOf(glyph.left+origin.x,glyph.right+origin.x)) for(y in listOf(glyph.top+origin.y,glyph.bottom+origin.y)) {
            assertTrue("Caption glyph "+i+" clipped by rounded action shape in "+tag,insideShape(x,y))
        }
        assertTrue("Caption glyph "+i+" clipped in "+tag+": "+glyph+" at "+origin+"; visible="+visible+"; action="+button,
            glyph.left+origin.x>=left-1f && glyph.right+origin.x<=right+1f && glyph.top+origin.y>=top-1f && glyph.bottom+origin.y<=bottom+1f)
    }
}
