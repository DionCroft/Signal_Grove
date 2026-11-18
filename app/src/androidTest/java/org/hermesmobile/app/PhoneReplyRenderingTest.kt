package org.hermesmobile.app

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.MetricAffectingSpan
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.Markwon
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.local.*
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real Android text spans and clipboard, synthetic content; no model, HTTP client or server. */
class PhoneReplyRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var originalClipboard: ClipData? = null
    private var clipboardChanged = false
    @Before fun rememberClipboard() { compose.runOnUiThread {
        originalClipboard = compose.activity.getSystemService(ClipboardManager::class.java).primaryClip
    } }
    @After fun restoreClipboard() {
        if (clipboardChanged) compose.runOnUiThread {
            val manager = compose.activity.getSystemService(ClipboardManager::class.java)
            originalClipboard?.let(manager::setPrimaryClip) ?: run {
                if (Build.VERSION.SDK_INT >= 28) manager.clearPrimaryClip()
                else manager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
    private fun show(text: String, state: LocalReplyState = LocalReplyState.COMPLETE, bilingual: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if(bilingual) 1.3f else density.fontScale)) {
                HermesAppTheme(AppPreferences(chinese = bilingual, pinyin = bilingual)) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        PhoneReply(text, state, 0)
                        androidx.compose.material3.OutlinedTextField("", {}, Modifier.testTag("reader-fixture-editor"))
                    }
                }
            }
        }
    }
    @Test fun markdownStylesTextButModelLinksCannotCreateClickableActions() {
        compose.runOnUiThread {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val input = "**Strong** [link](intent://example) ![image](https://example.invalid/never.png) `literal`"
            val normal = Markwon.create(context).toMarkdown(input)
            assertTrue(normal.getSpans(0, normal.length, ClickableSpan::class.java).isNotEmpty())
            val passive = phoneReplyMarkwon(context).toMarkdown(input)
            assertTrue(passive.toString().contains("Strong")); assertFalse(passive.toString().contains("**Strong**"))
            assertTrue(passive.getSpans(0, passive.length, MetricAffectingSpan::class.java).isNotEmpty())
            assertTrue(passive.getSpans(0, passive.length, ClickableSpan::class.java).isEmpty())
        }
    }
    @Test fun codeCopyWritesOnlyTheExplicitlyChosenLiteralBlock() {
        show("Advice\n\n```python\nprint('grove')\n```\n\nOther text")
        compose.runOnIdle {
            val pending = java.util.ArrayDeque<android.view.View>(); pending.add(compose.activity.window.decorView)
            var found = false
            while (pending.isNotEmpty()) {
                val view = pending.removeLast()
                if (view is android.widget.TextView && view.tag is Markwon) {
                    found = true; assertSame(android.view.textclassifier.TextClassifier.NO_OP, view.textClassifier)
                }
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) pending.add(view.getChildAt(i))
            }
            assertTrue("Expected the actual native reply TextView", found)
        }
        compose.onNodeWithTag("phone-code-open-0").performScrollTo().performClick()
        clipboardChanged = true
        compose.onNodeWithTag("phone-code-copy-0").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(ClipboardManager::class.java)
            assertEquals("print('grove')\n", clipboard.primaryClip!!.getItemAt(0).text.toString())
            assertTrue(clipboard.primaryClip!!.description.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
        }
        compose.onNodeWithTag("phone-code-close").assertIsDisplayed().performClick()
    }
    @Test fun longBilingualReplyHasACompleteBoundedReaderAndExactFullCopy() {
        val text = (1..1000).joinToString("\n") {"Garden line $it"}
        show(text, bilingual = true)
        compose.onNodeWithTag("phone-reply-expand-0").performScrollTo().performClick()
        val last = LocalReplyContent.chunks(text).lastIndex
        compose.onNodeWithTag("phone-full-reply").performScrollToNode(hasTestTag("phone-full-part-$last"))
        compose.onNodeWithTag("phone-full-part-$last").assertIsDisplayed().assertTextEquals(LocalReplyContent.chunks(text).last())
        clipboardChanged = true
        compose.onNodeWithTag("phone-full-copy").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(text, compose.activity.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString())
        }
        compose.onNodeWithTag("phone-full-close").assertIsDisplayed().performClick()
    }
    @Test fun streamingTextUsesABoundedNewestPartAndExposesNoPrematureCopyAction() {
        val text = "Earlier ".repeat(1000) + "Latest garden text"
        show(text, LocalReplyState.GENERATING)
        compose.onNodeWithText(LocalReplyContent.streamingTail(text)).assertExists()
        compose.onNodeWithText(Words.PhoneStreamTail.en).assertExists()
        compose.onNodeWithTag("phone-code-open-0").assertDoesNotExist()
        compose.onNodeWithTag("phone-reply-expand-0").assertDoesNotExist()
    }

    @Test fun copiedLongReplyRetainsExactTextAndTheKeyboardCanStillOpen() {
        val text = (1..1000).joinToString("\n") {"Garden line $it"}
        show(text)
        compose.onNodeWithTag("phone-reply-expand-0").performScrollTo().performClick()
        clipboardChanged = true
        compose.onNodeWithTag("phone-full-copy").assertIsDisplayed().performClick()
        compose.runOnIdle {
            val clip = compose.activity.getSystemService(ClipboardManager::class.java).primaryClip!!
            assertEquals(text, clip.getItemAt(0).text.toString())
            assertTrue(clip.description.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
        }
        compose.onNodeWithTag("phone-full-close").assertIsDisplayed().performClick()
        val initialBottom = java.util.concurrent.atomic.AtomicInteger()
        compose.runOnUiThread {
            val frame = android.graphics.Rect()
            compose.activity.window.decorView.getWindowVisibleDisplayFrame(frame)
            initialBottom.set(frame.bottom)
        }
        assertTrue(initialBottom.get() > 0)
        compose.onNodeWithTag("reader-fixture-editor").performScrollTo().assertIsNotFocused().performClick().assertIsFocused()
        compose.waitUntil(5000) {
            val frame = android.graphics.Rect()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                compose.activity.window.decorView.getWindowVisibleDisplayFrame(frame)
            }
            frame.bottom < initialBottom.get() * 0.8
        }
    }
}
