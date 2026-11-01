package org.hermesmobile.app.ui

import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import org.hermesmobile.app.data.AppPreferences

enum class HermesTheme(val title: String, val background: Color, val surface: Color, val ink: Color, val accent: Color, val button: Color, val radius: Int) {
    LAVENDER("Lavender Original",Color(0xFFF2E8FA),Color(0xFFFBF8FE),Color(0xFF252033),Color(0xFFA98AC9),Color(0xFF63467F),26),
    JADE("Jade Terminal",Color(0xFFE6ECDD),Color(0xFFF5F7EF),Color(0xFF153B2E),Color(0xFF8BB69C),Color(0xFF335E49),12),
    SOLAR("Solar Archive",Color(0xFFF1E8D8),Color(0xFFFAF5E9),Color(0xFF274B37),Color(0xFFC9AA7D),Color(0xFF4E6039),5),
    MINT("Mint Circuit",Color(0xFFE1EFEB),Color(0xFFF5FAF7),Color(0xFF184738),Color(0xFF93BFAE),Color(0xFF326750),20);
    companion object { fun from(name: String)=entries.firstOrNull { it.name==name } ?: LAVENDER }
}
val LocalHermesTheme=staticCompositionLocalOf { HermesTheme.LAVENDER }
val LocalReducedMotion=staticCompositionLocalOf { false }
@Composable fun HermesAppTheme(p: AppPreferences, content: @Composable () -> Unit) {
    val t=HermesTheme.from(p.theme)
    val systemMotion=Settings.Global.getFloat(LocalContext.current.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val default=Typography()
    val display=if(t==HermesTheme.LAVENDER || t==HermesTheme.MINT) FontFamily.SansSerif else FontFamily.Monospace
    val typography=default.copy(headlineLarge=default.headlineLarge.copy(fontFamily=display),headlineMedium=default.headlineMedium.copy(fontFamily=display),titleLarge=default.titleLarge.copy(fontFamily=display))
    CompositionLocalProvider(LocalPreferences provides p,LocalHermesTheme provides t,LocalReducedMotion provides (p.reducedMotion || systemMotion)) {
        MaterialTheme(colorScheme=lightColorScheme(primary=t.button,onPrimary=Color.White,primaryContainer=t.accent.copy(alpha=.28f),onPrimaryContainer=t.ink,
            background=t.background,onBackground=t.ink,surface=t.surface,onSurface=t.ink,surfaceVariant=t.background,onSurfaceVariant=t.ink.copy(alpha=.8f),
            outline=t.ink.copy(alpha=.35f),outlineVariant=t.accent.copy(alpha=.6f),secondary=t.button,onSecondary=Color.White,
            secondaryContainer=t.accent.copy(alpha=.25f),onSecondaryContainer=t.ink),typography=typography,
            shapes=Shapes(small=RoundedCornerShape(t.radius/2),medium=RoundedCornerShape(t.radius),large=RoundedCornerShape(t.radius+4)),content=content)
    }
}
@Composable fun HermesCard(modifier: Modifier=Modifier, content: @Composable ColumnScope.()->Unit) {
    val t=LocalHermesTheme.current
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(t.radius.dp),color=t.surface,
        border=BorderStroke(if(t==HermesTheme.SOLAR) 1.5.dp else 1.dp,if(t==HermesTheme.LAVENDER) t.accent.copy(alpha=.25f) else t.ink.copy(alpha=.2f))) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
@Composable fun Word(w: Words, modifier: Modifier=Modifier, heading: Boolean=false) {
    val p=LocalPreferences.current
    Column(modifier,verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(w.en,style=if(heading) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium)
        if(p.chinese) {
            Text(w.zh,style=MaterialTheme.typography.bodyMedium)
            if(p.pinyin) Text(w.pin,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable fun QuietProgress() {
    if(LocalReducedMotion.current) Text(label(Words.Loading)) else LinearProgressIndicator(Modifier.fillMaxWidth())
}
@Preview(showBackground=true) @Composable fun LavenderPreview() { PreviewTheme(HermesTheme.LAVENDER) }
@Preview(showBackground=true) @Composable fun JadePreview() { PreviewTheme(HermesTheme.JADE) }
@Preview(showBackground=true) @Composable fun SolarPreview() { PreviewTheme(HermesTheme.SOLAR) }
@Preview(showBackground=true) @Composable fun MintPreview() { PreviewTheme(HermesTheme.MINT) }
@Composable private fun PreviewTheme(t: HermesTheme) { HermesAppTheme(AppPreferences(theme=t.name)) { Surface { HermesCard(Modifier.padding(16.dp)) { Word(Words.Greeting,heading=true); Text(label(Words.Assistant)); Button({}) { Text(label(Words.NewChat)) } } } } }
