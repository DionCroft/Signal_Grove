package org.hermesmobile.app

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
    @Test fun fourPalettesHaveAaTextContrast() {
        HermesTheme.entries.forEach { t ->
            fun ratio(a:androidx.compose.ui.graphics.Color,b:androidx.compose.ui.graphics.Color): Double {
                fun luminance(c:androidx.compose.ui.graphics.Color):Double {
                    fun channel(v:Float)=if(v<=.04045f) v/12.92 else Math.pow((v+.055)/1.055,2.4)
                    return .2126*channel(c.red)+.7152*channel(c.green)+.0722*channel(c.blue)
                }
                val x=luminance(a);val y=luminance(b);return (maxOf(x,y)+.05)/(minOf(x,y)+.05)
            }
            assertTrue(t.title,ratio(t.ink,t.surface)>=4.5);assertTrue(t.title,ratio(t.ink,t.background)>=4.5)
            assertTrue(t.title,ratio(androidx.compose.ui.graphics.Color.White,t.button)>=4.5)
        }
    }
}
