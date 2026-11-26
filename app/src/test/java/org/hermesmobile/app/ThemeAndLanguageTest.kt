package org.hermesmobile.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Test

class ThemeAndLanguageTest {
    @Test fun allStringsHaveIndependentChineseAndAccentedPinyin() {
        Words.entries.forEach {w -> assertTrue(w.en.isNotBlank());assertTrue(w.zh.isNotBlank());assertTrue(w.pin.isNotBlank())}
        assertEquals("Home",Words.Home.localized(AppPreferences(chinese=false,pinyin=true)))
        assertEquals("Home\n首页",Words.Home.localized(AppPreferences(chinese=true,pinyin=false)))
        assertEquals("Home\n首页\nShǒuyè",Words.Home.localized(AppPreferences(chinese=true,pinyin=true)))
    }
    @Test fun allPalettesHaveAaTextContrast() {
        for (theme in HermesTheme.entries) {
            val colors = theme.colorScheme()
            fun readable(role: String, foreground: Color, background: Color) {
                val a = foreground.compositeOver(background).luminance()
                val b = background.luminance()
                val contrast = (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
                assertTrue("${theme.title} $role: $contrast", contrast >= 4.5f)
            }
            readable("surface text", colors.onSurface, colors.surface)
            readable("background text", colors.onBackground, colors.background)
            readable("primary button", colors.onPrimary, colors.primary)
            if (theme.dark) {
                readable("subdued text", colors.onSurfaceVariant, colors.surface)
                readable("accent text", colors.primary, colors.surface)
                readable("error text", colors.error, colors.surface)
                readable("selection", colors.onSecondaryContainer, colors.secondaryContainer.compositeOver(colors.surface))
                readable("inverted action", colors.inversePrimary, colors.inverseSurface)
                assertTrue(theme.title, colors.background.luminance() < .04f && colors.surface.luminance() < .06f)
            }
        }
        assertEquals(3, HermesTheme.entries.count { it.dark })
        assertEquals(HermesTheme.STARHAVEN, HermesTheme.from(AppPreferences().theme))
    }
}
