package org.hermesmobile.app.data

import android.content.Intent
import android.net.Uri
import android.os.Build

data class SharedImages(val uris:List<Uri>,val caption:String="",val invalid:Boolean=false) {
    companion object {
        @Suppress("DEPRECATION")
        fun parse(intent:Intent):SharedImages? {
            if(intent.action !in setOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE) || intent.type?.startsWith("image/")!=true) return null
            return runCatching {
                val values=mutableListOf<Uri>();var excessive=false
                if(intent.action==Intent.ACTION_SEND_MULTIPLE) {
                    val streams=if(Build.VERSION.SDK_INT>=33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM,Uri::class.java) else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    excessive=streams.orEmpty().size>ImageContent.MAX_IMAGES
                    values+=streams.orEmpty().take(ImageContent.MAX_IMAGES+1)
                } else {
                    val stream=if(Build.VERSION.SDK_INT>=33) intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri::class.java) else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    stream?.let(values::add)
                }
                intent.clipData?.let {clip -> if(clip.itemCount>ImageContent.MAX_IMAGES) excessive=true;repeat(minOf(clip.itemCount,ImageContent.MAX_IMAGES+1)) {index -> clip.getItemAt(index).uri?.let(values::add)}}
                val distinct=values.distinct();val caption=intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
                SharedImages(distinct.take(ImageContent.MAX_IMAGES),caption.take(64000),excessive || distinct.isEmpty() || distinct.size>ImageContent.MAX_IMAGES || distinct.any {it.scheme!="content"} || caption.length>64000)
            }.getOrDefault(SharedImages(emptyList(),invalid=true))
        }
    }
}
