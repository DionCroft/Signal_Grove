package org.hermesmobile.app.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.hermesmobile.app.data.CronRunOnceDecision
import org.hermesmobile.app.data.CronRunOnceReview
import org.hermesmobile.app.data.CronRunOnceScope
import org.hermesmobile.app.data.ManagementTarget

@Composable
internal fun HostCronRunOnceDialog(vm: ManagementViewModel, review: CronRunOnceReview, busy: Boolean, pending: Boolean) {
    SchedulerApprovalDialog(review.owner, review.job, review.scope, review.expiresAt,
        Words.HostCronRunOnceReview, Words.HostCronRunOnceEffect, "host-cron-run-once",
        busy, pending, denyBlocked = false, close = vm::closeSchedulerReview,
        expire = { vm.expireHostCronRunOnce(review) }, decide = { vm.decideHostCronRunOnce(review, it) })
}

@Composable
internal fun SchedulerApprovalDialog(
    owner: ManagementTarget, job: String, scope: CronRunOnceScope, expiresAt: Long,
    title: Words, explanation: Words, tag: String, busy: Boolean, pending: Boolean, denyBlocked: Boolean,
    close: () -> Unit, expire: () -> Unit, decide: (CronRunOnceDecision) -> Unit,
    detail: @Composable () -> Unit = {}
) {
    val onExpire by rememberUpdatedState(expire)
    LaunchedEffect(expiresAt) {
        delay((expiresAt - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        onExpire()
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(label(title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp).testTag("$tag-review"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(label(explanation))
                    Text(owner.url)
                    Text(owner.profile)
                    Text(owner.actor)
                    Text(job)
                    detail()
                }
                scope.project?.let { project -> item { runOnceScopeField(Words.HostCronRunOnceProject, project) } }
                item { runOnceScopeField(Words.HostCronRunOnceWorkspace, scope.workspace) }
                scope.branch?.let { branch -> item { runOnceScopeField(Words.HostCronRunOnceBranch, branch) } }
                item { runOnceScopeField(Words.HostCronRunOnceWorker, scope.worker + "\n" + scope.workerInstance) }
                item { Text(label(if (scope.writes) Words.HostCronRunOnceWrites else Words.HostCronRunOnceReads)) }
                item { runOnceScopeField(Words.HostCronRunOnceTask, scope.task) }
                if (pending) item { Text(label(Words.ManagementOutcomePending)) }
                item {
                    TextButton({ decide(CronRunOnceDecision.Approve) },
                        enabled = !busy && !pending,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-approve")) {
                        Text(label(Words.HostCronRunOnceApprove))
                    }
                    TextButton({ decide(CronRunOnceDecision.Defer) }, enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-defer")) {
                        Text(label(Words.HostCronRunOnceDefer))
                    }
                    TextButton({ decide(CronRunOnceDecision.Deny) }, enabled = !busy && !denyBlocked,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-deny")) {
                        Text(label(Words.HostCronRunOnceDeny))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(close, Modifier.heightIn(min = 48.dp)) { Text(label(Words.Close)) }
        })
}

@Composable
private fun runOnceScopeField(word: Words, value: String) {
    Column {
        Text(label(word), style = MaterialTheme.typography.labelLarge)
        SelectionContainer { Text(value) }
    }
}
