package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.*

@Composable fun HostSkillsDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    var review by remember(s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedSkillAction?>(null)}
    val receiptRows by vm.receipts.collectAsState();val receipts=vm.visibleReceipts(s,receiptRows)
    val pending=receipts.any {it.resourceScope==vm.resourceScope(s) && it.state in setOf("sending","unknown")}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostSkills))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("host-skills-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(s.url);Text(s.profile);Text(label(Words.SkillBoundary));TextButton(vm::loadHostSkills,enabled=!s.busy) {Text(label(Words.Refresh))}}
            if(s.busy) item {QuietProgress()}
            if(pending) item {Text(label(Words.ManagementOutcomePending))}
            if(s.skills.loaded && s.skills.rows.isEmpty()) item {Text(label(Words.EmptyInventory))}
            s.notice?.let {item {Text(label(it))}}
            items(s.skills.rows,key={it.name}) {skill ->Column(Modifier.testTag("host-skill-${skill.name}")) {
                Text(skill.name,style=MaterialTheme.typography.titleMedium);Text(skill.description)
                if(skill.provenance.isNotBlank()) Text(skill.provenance,style=MaterialTheme.typography.bodySmall)
                Text(label(when(skill.enabled) {true->Words.Enabled;false->Words.Disabled;null->Words.Unknown}))
                Text(label(when(skill.locked) {true->Words.SkillLocked;false->Words.SkillUnlocked;null->Words.SkillLockUnknown}))
                TextButton({vm.loadSkillContent(skill)},enabled=!s.busy,modifier=Modifier.testTag("skill-content-${skill.name}")) {Text(label(Words.SkillContent))}
                TextButton({review=vm.prepareSkill(skill,SkillAction.Toggle)},enabled=!s.busy && !pending && skill.enabled!=null && !(skill.enabled && skill.name in SkillProtocol.essential),modifier=Modifier.testTag("skill-toggle-${skill.name}")) {Text(label(if(skill.enabled==true) Words.SkillDisable else Words.SkillEnable))}
                TextButton({review=vm.prepareSkill(skill,SkillAction.Lock)},enabled=!s.busy && !pending && skill.locked!=null,modifier=Modifier.testTag("skill-lock-${skill.name}")) {Text(label(if(skill.locked==true) Words.SkillUnlock else Words.SkillLock))}
                if(skill.enabled==true && skill.name in SkillProtocol.essential) Text(label(Words.SkillEssential),style=MaterialTheme.typography.bodySmall)
                if(skill.enabled==null || skill.locked==null) Text(label(Words.SkillUnsupported),style=MaterialTheme.typography.bodySmall)
            }}
            s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}}
        }
    },confirmButton={TextButton(close,Modifier.testTag("host-skills-close")) {Text(label(Words.Close))}})
    s.skills.content?.let {content ->AlertDialog(onDismissRequest=vm::closeSkillContent,title={Text(s.skills.contentName)},text={
        LazyColumn(Modifier.heightIn(max=480.dp).testTag("skill-content-view")) {item {Text(content)}}
    },confirmButton={TextButton(vm::closeSkillContent,Modifier.testTag("skill-content-close")) {Text(label(Words.Close))}})}
    review?.let {frozen ->AlertDialog(onDismissRequest={review=null},title={Text(label(if(frozen.request.action==SkillAction.Toggle) Words.SkillToggle else Words.SkillLockControl))},text={
        LazyColumn(Modifier.heightIn(max=440.dp).testTag("skill-action-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor);Text(frozen.request.expected.name)}
            item {Text(label(if(frozen.request.action==SkillAction.Toggle) {if(frozen.request.value) Words.SkillEnable else Words.SkillDisable} else {if(frozen.request.value) Words.SkillLock else Words.SkillUnlock}));Text(label(if(frozen.request.action==SkillAction.Toggle) Words.SkillToggleEffect else Words.SkillLockEffect))}
        }
    },confirmButton={TextButton({vm.performSkill(frozen);review=null},enabled=!s.busy,modifier=Modifier.testTag("skill-action-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({review=null}) {Text(label(Words.Cancel))}})}
}
