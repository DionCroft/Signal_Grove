package org.hermesmobile.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun HomeScreen(vm: HermesViewModel, go: (String)->Unit) {
    val allConversations by vm.conversations.collectAsStateWithLifecycle()
    val conversations=allConversations.filterNot {it.hidden}
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val t=LocalHermesTheme.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).widthIn(max=1100.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Surface(shape=PixelPanelShape,color=t.surface,border=BorderStroke(1.5.dp,t.ink.copy(alpha=.3f))) {
            Column(verticalArrangement=Arrangement.spacedBy(0.dp)) {
                PixelHomestead()
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(label(Words.OrbitalHomestead),style=MaterialTheme.typography.labelSmall,color=t.button)
                Word(Words.Greeting,heading=true)
                Text(label(Words.GreetingBody),style=MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    PixelPrimaryButton({vm.newChat();go("Chat")},enabled=!busy,modifier=Modifier.weight(1f,fill=false)) { Icon(Icons.Outlined.Add,null); Spacer(Modifier.width(8.dp));Text(Words.NewChat.en) }
                    IconButton({go("Voice")},Modifier.size(48.dp).testTag("home-voice-action")) { Icon(Icons.Outlined.MicNone,label(Words.Voice)) }
                }
            }
            }
        }
        BoxWithConstraints {
            if(maxWidth>=650.dp) Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                HermesCard(Modifier.weight(1f)) { Word(Words.LocalTasks); Text("${tasks.count { it.completed }} / ${tasks.size}",style=MaterialTheme.typography.displaySmall); Text(label(Words.Completed)); TextButton({go("Tasks")}) { Text(label(Words.Tasks)) } }
                HermesCard(Modifier.weight(1f)) { Word(Words.Jobs); Text(jobs.size.toString(),style=MaterialTheme.typography.displaySmall); TextButton({go("Automation")}) { Text(label(Words.Automation)) } }
            } else HermesCard { Word(Words.LocalTasks); Text("${tasks.count { it.completed }} / ${tasks.size} · ${label(Words.Completed)}"); TextButton({go("Tasks")}) { Text(label(Words.Tasks)) } }
        }
        Word(Words.Recent)
        if(conversations.isEmpty()) HermesCard { Text(label(Words.NoChats)) }
        conversations.take(5).forEach { c -> HermesCard {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(c.title.ifBlank { Words.NewChat.en },style=MaterialTheme.typography.titleMedium); Text(label(statusWord(c.status)),style=MaterialTheme.typography.labelSmall) }
                IconButton({vm.select(c.key);go("Chat")},enabled=!busy) { Icon(Icons.AutoMirrored.Outlined.ArrowForward,label(Words.Chat)) }
            }
        } }
        AppUpdateHint {go("Settings")}
        Text(label(Words.Assistant),style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(vertical=8.dp))
    }
}
