package org.hermesmobile.app.ui

import android.provider.Settings
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.view.Window
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import org.hermesmobile.app.data.AppPreferences

enum class HermesTheme(
    val title: String,
    val background: Color,
    val surface: Color,
    val ink: Color,
    val accent: Color,
    val button: Color,
    val radius: Int,
    val dark: Boolean = false
) {
    STARHAVEN("Starhaven Grove",Color(0xFFF4ECD9),Color(0xFFFFF9E9),Color(0xFF263F35),Color(0xFFC49A60),Color(0xFF365B45),4),
    LAVENDER("Lavender Original",Color(0xFFF2E8FA),Color(0xFFFBF8FE),Color(0xFF252033),Color(0xFFA98AC9),Color(0xFF63467F),26),
    JADE("Jade Terminal",Color(0xFFE6ECDD),Color(0xFFF5F7EF),Color(0xFF153B2E),Color(0xFF8BB69C),Color(0xFF335E49),12),
    SOLAR("Solar Archive",Color(0xFFF1E8D8),Color(0xFFFAF5E9),Color(0xFF274B37),Color(0xFFC9AA7D),Color(0xFF4E6039),5),
    MINT("Mint Circuit",Color(0xFFE1EFEB),Color(0xFFF5FAF7),Color(0xFF184738),Color(0xFF93BFAE),Color(0xFF326750),20),
    MOONLIT("Moonlit Grove", Color(0xFF10201B), Color(0xFF1B2E25), Color(0xFFE1F2E6), Color(0xFF86B88E), Color(0xFFB4E0BE), 4, dark = true),
    MIDNIGHT("Midnight Observatory", Color(0xFF15182A), Color(0xFF23283D), Color(0xFFEBEDFF), Color(0xFFA1A9E7), Color(0xFFC0C7FC), 4, dark = true),
    AMBER("Amber Terminal", Color(0xFF1C1811), Color(0xFF2B241A), Color(0xFFFFEBD1), Color(0xFFC99755), Color(0xFFF0C987), 4, dark = true);
    companion object { fun from(name: String)=entries.firstOrNull { it.name==name } ?: LAVENDER }
}
internal fun HermesTheme.colorScheme(): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val onButton = if (dark) background else Color.White
    return base.copy(
        primary = button, onPrimary = onButton,
        primaryContainer = accent.copy(alpha = .28f), onPrimaryContainer = ink,
        background = background, onBackground = ink,
        surface = surface, onSurface = ink,
        surfaceVariant = background, onSurfaceVariant = ink.copy(alpha = .8f),
        surfaceBright = surface, surfaceDim = background, surfaceContainerLowest = surface,
        surfaceContainerLow = lerp(surface, background, .2f), surfaceContainer = surface,
        surfaceContainerHigh = lerp(surface, background, .45f), surfaceContainerHighest = background, surfaceTint = button,
        tertiary = button, onTertiary = onButton,
        tertiaryContainer = accent.copy(alpha = .25f), onTertiaryContainer = ink,
        inverseSurface = ink, inverseOnSurface = surface, inversePrimary = if (dark) background else accent,
        outline = ink.copy(alpha = if (dark) .52f else .35f), outlineVariant = accent.copy(alpha = .6f),
        secondary = button, onSecondary = onButton,
        secondaryContainer = accent.copy(alpha = .25f), onSecondaryContainer = ink
    )
}

val LocalHermesTheme=staticCompositionLocalOf { HermesTheme.STARHAVEN }
val LocalReducedMotion=staticCompositionLocalOf { false }
@Composable fun HermesAppTheme(p: AppPreferences, window: Window? = null, content: @Composable () -> Unit) {
    val t=HermesTheme.from(p.theme)
    val colors = remember(t) { t.colorScheme() }
    if (window != null) {
        val insets = remember(window) { WindowCompat.getInsetsController(window, window.decorView) }
        SideEffect {
            insets.isAppearanceLightStatusBars = !t.dark
            insets.isAppearanceLightNavigationBars = !t.dark
        }
    }
    val resolver=LocalContext.current.contentResolver
    var systemMotion by remember(resolver) {mutableStateOf(Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f)}
    DisposableEffect(resolver) {
        val observer=object:ContentObserver(Handler(Looper.getMainLooper())) {override fun onChange(selfChange:Boolean) {systemMotion=Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f}}
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose {resolver.unregisterContentObserver(observer)}
    }
    val default=Typography()
    val display=FontFamily.Monospace
    val typography=default.copy(headlineLarge=default.headlineLarge.copy(fontFamily=display),headlineMedium=default.headlineMedium.copy(fontFamily=display),titleLarge=default.titleLarge.copy(fontFamily=display,fontWeight=FontWeight.Bold),labelLarge=default.labelLarge.copy(fontFamily=display,fontWeight=FontWeight.Bold))
    CompositionLocalProvider(LocalPreferences provides p,LocalHermesTheme provides t,LocalReducedMotion provides (p.reducedMotion || systemMotion)) {
        MaterialTheme(colorScheme=colors,typography=typography,
            shapes=Shapes(extraSmall=CutCornerShape(3.dp),small=CutCornerShape(4.dp),medium=CutCornerShape(6.dp),large=CutCornerShape(8.dp),extraLarge=CutCornerShape(10.dp)),content=content)
    }
}
@Composable fun HermesCard(modifier: Modifier=Modifier, content: @Composable ColumnScope.()->Unit) {
    val t=LocalHermesTheme.current
    Surface(modifier.fillMaxWidth().pixelPaper(t.ink),shape=PixelPanelShape,color=t.surface,
        border=BorderStroke(1.5.dp,t.ink.copy(alpha=.24f))) {
        Column(Modifier.pixelPaper(t.ink).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
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
    if(LocalReducedMotion.current) Text(label(Words.Loading)) else PixelProgress()
}
@Preview(showBackground=true) @Composable fun LavenderPreview() { PreviewTheme(HermesTheme.LAVENDER) }
@Preview(showBackground=true) @Composable fun JadePreview() { PreviewTheme(HermesTheme.JADE) }
@Preview(showBackground=true) @Composable fun SolarPreview() { PreviewTheme(HermesTheme.SOLAR) }
@Preview(showBackground=true) @Composable fun MintPreview() { PreviewTheme(HermesTheme.MINT) }
@Composable private fun PreviewTheme(t: HermesTheme) { HermesAppTheme(AppPreferences(theme=t.name)) { Surface { HermesCard(Modifier.padding(16.dp)) { Word(Words.Greeting,heading=true); Text(label(Words.Assistant)); Button({}) { Text(label(Words.NewChat)) } } } } }
