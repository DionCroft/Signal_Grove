package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable fun HostMainModelReview(review:ReviewedHostMainModel,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    var approved by remember(review) {mutableStateOf(false)};val request=review.request
    AlertDialog(onDismissRequest=close,title={Text(label(if(request.warning!=null) Words.HostMainWarningTitle else Words.HostMainReview))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("host-main-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor);Text(label(Words.HostModelsMain));Text(request.expected.main.provider);Text(request.expected.main.model);Text(label(Words.HostMainSet));Text(request.provider);Text(request.model)}
        item {Text(label(Words.HostMainEffect));Text(label(Words.HostMainConcurrency))}
        request.warning?.let {warning->item {Text(label(Words.HostMainWarningEffect));Text(warning.message,Modifier.testTag("host-main-warning-message"))}}
        item {Row {Checkbox(approved,{approved=it},modifier=Modifier.testTag("host-main-approve"));Text(label(if(request.warning==null) Words.HostMainApprove else Words.HostMainWarningApprove),Modifier.weight(1f))}}
    }},confirmButton={TextButton(confirm,enabled=enabled && approved,modifier=Modifier.testTag("host-main-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.testTag("host-main-cancel")) {Text(label(Words.Cancel))}})
}
