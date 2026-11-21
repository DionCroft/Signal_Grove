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

@Composable
internal fun HostCronRunOnceDialog(vm: ManagementViewModel, review: CronRunOnceReview, busy: Boolean, pending: Boolean) {
    val scope = review.scope
    LaunchedEffect(review) {
        delay((review.expiresAt - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        vm.expireHostCronRunOnce(review)
    }
    AlertDialog(
        onDismissRequest = vm::closeHostCronRunOnce,
        title = { Text(label(Words.HostCronRunOnceReview)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp).testTag("host-cron-run-once-review"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(label(Words.HostCronRunOnceEffect))
                    Text(review.owner.url)
                    Text(review.owner.profile)
                    Text(review.owner.actor)
                    Text(review.job)
                }
                scope.project?.let { project -> item { runOnceScopeField(Words.HostCronRunOnceProject, project) } }
                item { runOnceScopeField(Words.HostCronRunOnceWorkspace, scope.workspace) }
                scope.branch?.let { branch -> item { runOnceScopeField(Words.HostCronRunOnceBranch, branch) } }
                item { runOnceScopeField(Words.HostCronRunOnceWorker, scope.worker + "\n" + scope.workerInstance) }
                item { Text(label(if (scope.writes) Words.HostCronRunOnceWrites else Words.HostCronRunOnceReads)) }
                item { runOnceScopeField(Words.HostCronRunOnceTask, scope.task) }
                if (pending) item { Text(label(Words.ManagementOutcomePending)) }
                item {
                    TextButton({ vm.decideHostCronRunOnce(review, CronRunOnceDecision.Approve) },
                        enabled = !busy && !pending,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("host-cron-run-once-approve")) {
                        Text(label(Words.HostCronRunOnceApprove))
                    }
                    TextButton({ vm.decideHostCronRunOnce(review, CronRunOnceDecision.Defer) }, enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("host-cron-run-once-defer")) {
                        Text(label(Words.HostCronRunOnceDefer))
                    }
                    TextButton({ vm.decideHostCronRunOnce(review, CronRunOnceDecision.Deny) }, enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("host-cron-run-once-deny")) {
                        Text(label(Words.HostCronRunOnceDeny))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(vm::closeHostCronRunOnce, Modifier.heightIn(min = 48.dp)) { Text(label(Words.Close)) }
        })
}

@Composable
private fun runOnceScopeField(word: Words, value: String) {
    Column {
        Text(label(word), style = MaterialTheme.typography.labelLarge)
        SelectionContainer { Text(value) }
    }
}
