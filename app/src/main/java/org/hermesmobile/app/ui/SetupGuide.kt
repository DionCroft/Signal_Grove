package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Offline guidance only. No credential access, execution, connection writes or network calls. */
@Composable fun SetupGuide(savedApiUrl: String="",close:()->Unit) {
    var step by rememberSaveable {mutableIntStateOf(0)}
    var contents by rememberSaveable {mutableStateOf(false)}
    var newHost by rememberSaveable {mutableStateOf(false)}
    val pages=SetupGuideContent.pages
    val page=pages[step]
    val uri=LocalUriHandler.current
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().widthIn(max=640.dp),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp).testTag("setup-guide")) {
                Text(label(Words.SetupGuide),style=MaterialTheme.typography.titleLarge)
                LinearProgressIndicator(progress={ (step+1)/pages.size.toFloat() },modifier=Modifier.fillMaxWidth().padding(vertical=8.dp))
                Text(label(Words.SetupStepCount).replace("{step}",(step+1).toString()).replace("{total}",pages.size.toString()),style=MaterialTheme.typography.labelMedium)
                TextButton({contents=!contents},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("setup-guide-contents"),shape=MaterialTheme.shapes.small) {Text(label(Words.SetupContents))}
                if(contents) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("setup-guide-contents-list"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        pages.forEachIndexed {index,item ->
                            OutlinedButton({step=index;contents=false},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("setup-guide-step-$index"),shape=MaterialTheme.shapes.small) {Text(label(item.heading))}
                        }
                    }
                } else key(step) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("setup-guide-page"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Word(page.heading,heading=true)
                        if(step==0) {
                            Text(label(Words.SetupIntro))
                            FilterChip(selected=!newHost,onClick={newHost=false},label={Text(label(Words.SetupExisting))},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("setup-guide-existing"),shape=MaterialTheme.shapes.small)
                            FilterChip(selected=newHost,onClick={newHost=true},label={Text(label(Words.SetupNewLinux))},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("setup-guide-new"),shape=MaterialTheme.shapes.small)
                            Text(label(Words.SetupRoles))
                            SetupGuideContent.savedApi(savedApiUrl)?.let {address ->
                                Word(Words.SetupSavedApi,heading=true)
                                SelectionContainer {Text(address,Modifier.testTag("setup-guide-saved-api"),fontFamily=FontFamily.Monospace)}
                                Text(label(Words.SetupSavedAddressNote),style=MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(label(if(step==0 && newHost) Words.SetupNewHostBody else page.body))
                        if(step in 0..2 || step==4) Text(label(Words.SetupWhere),style=MaterialTheme.typography.bodySmall)
                        if(step==1) {Text(label(Words.SetupApiKeys));Word(Words.SetupEnvFile,heading=true)}
                        if(step==2 && !newHost) Text(label(Words.SetupNetworkExisting))
                        if(step==4) {
                            if(!newHost) Text(label(Words.SetupDashboardExisting))
                            if(newHost) {
                                Word(Words.SetupEnvFile,heading=true)
                                Text(label(Words.SetupDashboardLoginConfig))
                                SelectionContainer {Text(SetupGuideContent.dashboardConfig,Modifier.testTag("setup-guide-config"),style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace)}
                                Text(label(Words.SetupDashboardStart))
                                SelectionContainer {Text(SetupGuideContent.dashboardStart,Modifier.testTag("setup-guide-dashboard-start"),style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace)}
                            }
                            Text(label(Words.SetupDashboardGate))
                            Text(label(Words.SetupManagementSteps))
                        }
                        SetupGuideContent.example(step,newHost,savedApiUrl)?.let {example ->
                            SelectionContainer {Text(example,Modifier.testTag("setup-guide-example"),style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace)}
                        }
                        if(step==pages.lastIndex) {
                            Text(label(Words.SetupFinalCheck))
                            Word(Words.SetupTroubleshoot,heading=true)
                            Text(label(Words.SetupTroubleshootBody))
                        }
                        TextButton({uri.openUri(page.url)},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("setup-guide-docs"),shape=MaterialTheme.shapes.small) {Text(label(page.docs))}
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(close,Modifier.weight(1f).heightIn(min=48.dp).testTag("setup-guide-close"),shape=MaterialTheme.shapes.small) {Text(label(Words.Close))}
                    if(step>0 && !contents) TextButton({step--},Modifier.weight(1f).heightIn(min=48.dp).testTag("setup-guide-previous"),shape=MaterialTheme.shapes.small) {Text(label(Words.SetupPrevious))}
                }
                if(!contents) {
                    if(step<pages.lastIndex) Button({step++},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("setup-guide-next"),shape=MaterialTheme.shapes.small) {Text(label(Words.SetupNext))}
                    else Button(close,Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("setup-guide-finish"),shape=MaterialTheme.shapes.small) {Text(label(Words.SetupReady))}
                }
            }
        }
    }
}
