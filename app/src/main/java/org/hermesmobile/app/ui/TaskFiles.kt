package org.hermesmobile.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import org.hermesmobile.app.data.*
import java.io.ByteArrayOutputStream

@Composable fun TaskFiles(vm: HermesViewModel) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var pendingExport by remember {mutableStateOf<ByteArray?>(null)}
    var pendingImport by remember {mutableStateOf<List<LocalTask>?>(null)}
    var working by remember {mutableStateOf(false)}
    var result by remember {mutableStateOf<Words?>(null)}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri ->
        val bytes=pendingExport;pendingExport=null
        if(uri!=null) scope.launch {
            working=true
            try {require(bytes!=null);withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri,"wt")?.use {it.write(bytes)} ?: error("No output")};result=Words.TaskFileDone}
            catch(e:CancellationException) {throw e} catch(_:Exception) {result=Words.TaskFileError} finally {working=false}
        }
    }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
        if(uri!=null) scope.launch {
            working=true;result=null
            try {pendingImport=withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use {input ->
                    val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true) {val count=input.read(buffer);if(count<0) break;require(out.size()+count<=TaskTransfer.MAX_BYTES);out.write(buffer,0,count)}
                    TaskTransfer.decode(out.toByteArray())
                } ?: error("No input")
            }} catch(e:CancellationException) {throw e} catch(_:Exception) {result=Words.TaskFileError} finally {working=false}
        }
    }
    HermesCard {
        Word(Words.TaskFiles);Text(label(Words.TaskFilesInfo))
        TextButton({scope.launch {
            working=true;result=null
            try {pendingExport=withContext(Dispatchers.IO) {vm.taskBackup()};export.launch("hermes-tasks.json")}
            catch(e:CancellationException) {throw e} catch(_:Exception) {result=Words.TaskFileError} finally {working=false}
        }},enabled=!working) {Text(label(Words.ExportTasks))}
        TextButton({import.launch(arrayOf("application/json","text/plain"))},enabled=!working) {Text(label(Words.ImportTasks))}
        if(working) QuietProgress()
        result?.let {Text(label(it))}
    }
    pendingImport?.let {rows -> AlertDialog(onDismissRequest={pendingImport=null},title={Text(label(Words.ReviewImport))},text={Text(label(Words.ImportInfo).replace("{count}",rows.size.toString()))},confirmButton={TextButton({
        pendingImport=null;working=true
        scope.launch {try {vm.importTasks(rows);result=Words.TaskFileDone} catch(e:CancellationException) {throw e} catch(_:Exception) {result=Words.TaskFileError} finally {working=false}}
    },enabled=!working) {Text(label(Words.ConfirmChange))}},dismissButton={TextButton({pendingImport=null}) {Text(label(Words.Cancel))}})}
}
