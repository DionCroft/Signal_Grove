package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun CanonicalProjectFilesDialog(view: CanonicalProjectFiles, controller: CanonicalViewerController) {
    val review = view as? CanonicalProjectFiles.Review
    AlertDialog(
        onDismissRequest = controller::closeProjectFiles,
        title = { Text(label(Words.ViewerProjectFiles)) },
        text = {
            LazyColumn(
                Modifier.heightIn(max = 400.dp).testTag("project-files-dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (view) {
                    CanonicalProjectFiles.Loading -> item {
                        QuietProgress()
                        Text(label(Words.Loading))
                    }
                    is CanonicalProjectFiles.Unavailable -> item {
                        Text(label(view.message), Modifier.testTag("project-files-unavailable"))
                    }
                    is CanonicalProjectFiles.Review -> item {
                        val project = view.project
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(label(Words.ViewerFilesReview))
                            Text(project.device)
                            Text(project.projectId)
                            Text(project.root, Modifier.testTag("project-files-reviewed-root"))
                            if (project.instance == null) Text(label(Words.ViewerFilesWorkerOffline))
                        }
                    }
                    is CanonicalProjectFiles.Folder -> {
                        val page = view.current
                        item {
                            Text(page.path.ifEmpty { view.binding.project.root }, style = MaterialTheme.typography.titleSmall)
                            if (view.pages.size > 1) ProjectFilesBack(controller)
                            if (page.truncated || page.excluded > 0) Text(label(Words.ViewerFilesListingLimited))
                            if (page.entries.isEmpty()) Text(label(Words.ViewerFilesEmpty))
                        }
                        items(page.entries, key = { it.path }) { entry ->
                            TextButton(
                                onClick = { controller.openProjectEntry(view, entry) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("project-files-entry")
                            ) {
                                Column {
                                    Text(entry.name)
                                    Text(label(if (entry.directory) Words.ViewerFilesDirectory else Words.ViewerFilesFile),
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    is CanonicalProjectFiles.Preview -> {
                        item {
                            ProjectFilesBack(controller)
                            Text(view.preview.path, style = MaterialTheme.typography.titleSmall)
                            if (view.preview.truncated) Text(label(Words.ViewerFilesPreviewLimited))
                        }
                        item {
                            val text = view.preview.text
                            Text(
                                when {
                                    text == null -> label(Words.ViewerFilesBinary)
                                    text.isEmpty() -> label(Words.ViewerFilesEmptyText)
                                    else -> text
                                },
                                Modifier.testTag("project-files-preview"),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        dismissButton = if (review?.project?.instance != null) {
            {
                TextButton(onClick = { controller.openProjectFiles(review) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("project-files-confirm")) {
                    Text(label(Words.ViewerFilesOpenRoot))
                }
            }
        } else null,
        confirmButton = {
            TextButton(controller::closeProjectFiles, Modifier.heightIn(min = 48.dp).testTag("project-files-close")) {
                Text(label(Words.Close))
            }
        }
    )
}

@Composable
private fun ProjectFilesBack(controller: CanonicalViewerController) {
    TextButton(controller::backInProjectFiles, Modifier.heightIn(min = 48.dp).testTag("project-files-back")) {
        Text(label(Words.ViewerFilesBack))
    }
}
