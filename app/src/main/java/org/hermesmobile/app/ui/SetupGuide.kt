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

/** Offline guidance only. No credentials, commands, connection writes or network calls. */
@Composable fun SetupGuide(close:()->Unit) {
    var step by rememberSaveable {mutableIntStateOf(0)}
    val uri=LocalUriHandler.current
    val headings=listOf(Words.SetupHost,Words.SetupApi,Words.SetupNetwork,Words.SetupPhone,Words.SetupDashboard,Words.SetupVerify)
    val bodies=listOf(Words.SetupHostBody,Words.SetupApiBody,Words.SetupNetworkBody,Words.SetupPhoneBody,Words.SetupDashboardBody,Words.SetupVerifyBody)
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().widthIn(max=640.dp),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp).testTag("setup-guide")) {
                Text(label(Words.SetupGuide),style=MaterialTheme.typography.titleLarge)
                LinearProgressIndicator(progress={ (step+1)/6f },modifier=Modifier.fillMaxWidth().padding(vertical=12.dp))
                key(step) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("setup-guide-page"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Word(headings[step],heading=true)
                        if(step==0) Text(label(Words.SetupIntro))
                        Text(label(bodies[step]))
                        val example=when(step) {
                            1 -> "API_SERVER_ENABLED=true\nAPI_SERVER_HOST=127.0.0.1\nAPI_SERVER_PORT=8642\nAPI_SERVER_KEY=<your-private-strong-key>"
                            2 -> "tailscale serve status\nsudo tailscale serve --bg --https=443 http://127.0.0.1:8642"
                            3 -> "https://your-host.your-tailnet.ts.net/"
                            4 -> "HERMES_DASHBOARD_PUBLIC_URL=https://your-host.your-tailnet.ts.net:8443\n\nsudo tailscale serve --bg --https=8443 http://127.0.0.1:9119"
                            else -> null
                        }
                        if(example!=null) SelectionContainer {Text(example,Modifier.testTag("setup-guide-example"),style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace)}
                        if(step==5) {Word(Words.SetupTroubleshoot,heading=true);Text(label(Words.SetupTroubleshootBody))}
                        val link=when(step) {
                            0 -> Words.SetupInstallDocs to "https://hermes-agent.nousresearch.com/docs/getting-started/installation"
                            2 -> Words.SetupTailscaleDocs to "https://tailscale.com/docs/features/tailscale-serve"
                            4 -> Words.SetupDashboardDocs to "https://hermes-agent.nousresearch.com/docs/user-guide/features/web-dashboard"
                            else -> Words.SetupApiDocs to "https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server"
                        }
                        TextButton({uri.openUri(link.second)},Modifier.testTag("setup-guide-docs")) {Text(label(link.first))}
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(close,Modifier.testTag("setup-guide-close")) {Text(label(Words.Close))}
                    if(step>0) TextButton({step--},Modifier.testTag("setup-guide-previous")) {Text(label(Words.SetupPrevious))}
                }
                if(step<headings.lastIndex) Button({step++},Modifier.fillMaxWidth().testTag("setup-guide-next")) {Text(label(Words.SetupNext))}
            }
        }
    }
}
