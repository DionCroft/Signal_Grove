package org.hermesmobile.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.BuildConfig

@Composable fun SettingsScreen(vm: HermesViewModel,openServer: ()->Unit) {
    val p by vm.preferences.collectAsStateWithLifecycle()
    val runBusy by vm.busy.collectAsStateWithLifecycle()
    val jobBusy by vm.jobBusy.collectAsStateWithLifecycle()
    val controlBusy by vm.controlBusy.collectAsStateWithLifecycle()
    val busy=runBusy || jobBusy || controlBusy
    val connecting by vm.connecting.collectAsStateWithLifecycle()
    val capabilities by vm.capabilities.collectAsStateWithLifecycle()
    var url by remember(p.url) {mutableStateOf(p.url)}
    // Credentials are deliberately excluded from saved instance state.
    var token by remember {mutableStateOf("")}
    var http by remember(p.debugHttp) {mutableStateOf(p.debugHttp)}
    var clear by remember {mutableStateOf(false)}
    var licences by remember {mutableStateOf(false)}
    val uri=LocalUriHandler.current
    val context=LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Word(Words.Settings,heading=true)
        AppUpdateSettings(busy || connecting)
        HermesCard {
            Word(Words.Server)
            Toggle(Words.Demo,p.demo,!busy && !connecting) {vm.setDemo(it)}
            OutlinedTextField(url,{url=it},Modifier.fillMaxWidth(),label={Text(label(Words.ServerUrl))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),enabled=!busy && !connecting)
            OutlinedTextField(token,{token=it},Modifier.fillMaxWidth(),label={Text(label(Words.Token))},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),enabled=!busy && !connecting)
            Text(label(Words.TokenHint),style=MaterialTheme.typography.labelMedium)
            Text(label(Words.ServerHelp),style=MaterialTheme.typography.bodySmall)
            LocalNetworkSettings()
            if(BuildConfig.DEBUG) Toggle(Words.DebugHttp,http,!busy && !connecting) {http=it}
            Text(label(Words.RotationPolicy),style=MaterialTheme.typography.bodySmall)
            Button({vm.configure(url,token,http);token=""},enabled=!busy && !connecting && url.isNotBlank()) {Text(label(Words.Connect))}
            if(connecting) QuietProgress()
            capabilities?.let { c -> Text("${c.model} · Sessions: ${c.sessions} · Runs: ${c.runs}",style=MaterialTheme.typography.labelSmall) }
            TextButton(vm::forgetServer,enabled=!busy && !connecting) {Text(label(Words.Forget))}
        }
        ConnectionProfiles(vm)
        ManagementEntry(vm)
        TextButton(openServer) {Text(label(Words.ServerDetails))}
        TaskFiles(vm)
        BackgroundSettings(vm)
        Word(Words.Appearance)
        HermesTheme.entries.forEach { t ->
            Surface(Modifier.fillMaxWidth().clickable {vm.updatePreferences(p.copy(theme=t.name),p)},color=t.background,shape=PixelPanelShape,border=BorderStroke(if(p.theme==t.name) 2.dp else 1.dp,t.button)) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(t.title,color=t.ink,style=MaterialTheme.typography.titleLarge)
                    Text(label(Words.Preview),color=t.ink,style=MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(t.surface,t.accent,t.button).forEach { color -> Box(Modifier.size(28.dp).background(color,RoundedCornerShape(8.dp)).border(1.dp,t.ink.copy(alpha=.25f),RoundedCornerShape(8.dp))) } }
                }
            }
        }
        HermesCard {
            Word(Words.Language)
            Toggle(Words.Chinese,p.chinese) {vm.updatePreferences(p.copy(chinese=it),p)}
            Toggle(Words.Pinyin,p.pinyin,p.chinese) {vm.updatePreferences(p.copy(pinyin=it),p)}
        }
        HermesCard {Word(Words.Accessibility);Toggle(Words.ReducedMotion,p.reducedMotion) {vm.updatePreferences(p.copy(reducedMotion=it),p)}}
        HermesCard {
            Word(Words.About)
            Text(label(Words.AboutBody).replace("{version}",BuildConfig.VERSION_NAME),style=MaterialTheme.typography.bodySmall)
            Text(label(Words.Security),style=MaterialTheme.typography.bodySmall)
            TextButton({uri.openUri("https://github.com/DionCroft/Signal_Grove")}) {Text(label(Words.Github))}
            TextButton({licences=true}) {Text(label(Words.Licences))}
            TextButton({clear=true},enabled=!busy) {Text(label(Words.ClearChats))}
        }
    }
    if(clear) AlertDialog(onDismissRequest={clear=false},title={Text(label(Words.ClearChats))},text={Text(label(Words.ClearConfirm))},confirmButton={TextButton({vm.clearLocalChats();clear=false}) {Text(label(Words.Delete))}},dismissButton={TextButton({clear=false}) {Text(label(Words.Cancel))}})
    if(licences) AlertDialog(onDismissRequest={licences=false},title={Text(label(Words.Licences))},text={Text(remember {context.assets.open("NOTICE.txt").bufferedReader().use {it.readText()}},Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall)},confirmButton={TextButton({licences=false}) {Text(label(Words.Close))}})
}
@Composable private fun Toggle(word: Words, checked: Boolean, enabled: Boolean=true, change: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label(word),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        Switch(checked,change,enabled=enabled)
    }
}
