package org.hermesmobile.app.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ErrorBanner(message:String,onDismiss:()->Unit) {
    var expanded by remember(message) {mutableStateOf(false)}
    Surface(color=MaterialTheme.colorScheme.errorContainer,modifier=Modifier.testTag("error-banner")) {
        Row(Modifier.fillMaxWidth().padding(start=16.dp,end=4.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(message,Modifier.weight(1f).testTag("error-banner-summary"),maxLines=2,overflow=TextOverflow.Ellipsis,
                color=MaterialTheme.colorScheme.onErrorContainer,style=MaterialTheme.typography.bodyMedium)
            IconButton({expanded=true},Modifier.size(48.dp).testTag("error-details-open")) {
                Icon(Icons.Outlined.Info,label(Words.ErrorDetails),tint=MaterialTheme.colorScheme.onErrorContainer)
            }
            IconButton(onDismiss,Modifier.size(48.dp).testTag("error-banner-dismiss")) {
                Icon(Icons.Outlined.Close,label(Words.Dismiss),tint=MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
    if(expanded) AlertDialog(
        onDismissRequest={expanded=false},
        title={Text(label(Words.ErrorDetails))},
        text={Text(message,Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()).testTag("error-details-message"))},
        confirmButton={TextButton({expanded=false},Modifier.heightIn(min=48.dp).testTag("error-details-close")) {Text(label(Words.Close))}}
    )
}
