package org.hermesmobile.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import org.hermesmobile.app.local.validWritingSource

/** System Share and selected-text entry, limited to one bounded plain-text draft. */
class PhoneTextReceiveActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val text = parseWritingShare(intent)
            val app = application as HermesApplication
            val token = text?.let(app.writingIntake::offer)
            setIntent(Intent())
            startActivity(Intent(this, LocalAiActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(LocalAiActivity.WRITING_TOKEN, token)
                .putExtra(LocalAiActivity.WRITING_REJECTED, token == null))
        }
        finish()
    }
}

internal fun parseWritingShare(intent: Intent): String? = runCatching {
    val key = when (intent.action) {
        Intent.ACTION_SEND -> Intent.EXTRA_TEXT
        Intent.ACTION_PROCESS_TEXT -> Intent.EXTRA_PROCESS_TEXT
        else -> return null
    }
    if (intent.type != "text/plain" || intent.data != null || intent.hasExtra(Intent.EXTRA_STREAM)) return null
    val clip = intent.clipData
    if (clip != null && (clip.itemCount != 1 || clip.getItemAt(0).let { it.uri != null || it.intent != null })) return null
    val value = intent.getCharSequenceExtra(key)
        ?: clip?.getItemAt(0)?.text?.takeIf { intent.action == Intent.ACTION_SEND }
    value?.takeIf { it.length <= org.hermesmobile.app.local.MAX_WRITING_SOURCE }
        ?.toString()?.takeIf(::validWritingSource)
}.getOrNull()
