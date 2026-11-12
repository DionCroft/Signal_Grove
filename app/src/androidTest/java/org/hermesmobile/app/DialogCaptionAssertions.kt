package org.hermesmobile.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidPath
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.junit.Assert.*

/** Assert rendered glyphs, including every translated line, fit their visible action. */
fun ComposeContentTestRule.assertDialogActionCaptionVisible(tag:String,outline:Outline?=null) {
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
    val clipRegion=outline?.let {shape->
        val path=android.graphics.Path()
        when(shape) {
            is Outline.Generic->path.set(shape.path.asAndroidPath())
            is Outline.Rectangle->path.addRect(shape.rect.left,shape.rect.top,shape.rect.right,shape.rect.bottom,android.graphics.Path.Direction.CW)
            is Outline.Rounded->{val r=shape.roundRect
                path.addRoundRect(android.graphics.RectF(r.left,r.top,r.right,r.bottom),floatArrayOf(
                    r.topLeftCornerRadius.x,r.topLeftCornerRadius.y,r.topRightCornerRadius.x,r.topRightCornerRadius.y,
                    r.bottomRightCornerRadius.x,r.bottomRightCornerRadius.y,r.bottomLeftCornerRadius.x,r.bottomLeftCornerRadius.y),android.graphics.Path.Direction.CW)
            }
        }
        android.graphics.Region().also {it.setPath(path,android.graphics.Region(-1,-1,ceil(button.width).toInt()+2,ceil(button.height).toInt()+2))}
    }
    // Default Material3 text buttons use CircleShape: bounds alone miss its clipping.
    val radius=minOf(button.width,button.height)/2f
    fun insideShape(x:Float,y:Float):Boolean {
        clipRegion?.let {region->
            val localX=(x-button.left).roundToInt();val localY=(y-button.top).roundToInt()
            // Match the existing one-pixel glyph/bounds tolerance at raster edges.
            return (-1..1).any {dx->(-1..1).any {dy->region.contains(localX+dx,localY+dy)}}
        }
        val closestX=x.coerceIn(button.left+radius,button.right-radius)
        val closestY=y.coerceIn(button.top+radius,button.bottom-radius)
        val dx=x-closestX;val dy=y-closestY
        return dx*dx+dy*dy<=(radius+1f)*(radius+1f)
    }
    for(i in value.indices) {
        if(value[i].isWhitespace() || Character.isLowSurrogate(value[i])) continue
        val glyph=layout.getBoundingBox(i)
        for(x in listOf(glyph.left+origin.x,glyph.right+origin.x)) for(y in listOf(glyph.top+origin.y,glyph.bottom+origin.y)) {
            assertTrue("Caption glyph "+i+" clipped by "+(if(outline==null) "rounded action shape" else "supplied action outline")+" in "+tag,insideShape(x,y))
        }
        assertTrue("Caption glyph "+i+" clipped in "+tag+": "+glyph+" at "+origin+"; visible="+visible+"; action="+button,
            glyph.left+origin.x>=left-1f && glyph.right+origin.x<=right+1f && glyph.top+origin.y>=top-1f && glyph.bottom+origin.y<=bottom+1f)
    }
}
