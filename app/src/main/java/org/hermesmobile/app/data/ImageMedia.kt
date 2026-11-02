package org.hermesmobile.app.data

class ImageMedia(val blobs:ImageBlobStore) {
    fun importSource(scope:String,conversation:String,source:String):ImageRef {
        if(source.startsWith("data:",ignoreCase=true)) {
            val header=source.substringBefore(',');val data=source.substringAfter(',',"")
            if(!header.startsWith("data:image/",ignoreCase=true) || !header.endsWith(";base64",ignoreCase=true) || data.length>11_000_000) return ImageRef(unavailable=true)
            val normalized=try {ImageCodec.normalize(java.util.Base64.getDecoder().decode(data))} catch(_:IllegalArgumentException) {return ImageRef(unavailable=true)}
            return blobs.write(scope,conversation,normalized.bytes,normalized.width,normalized.height)
        }
        return ImageContent.remoteUrl(source)?.let {ImageRef(url=it)} ?: ImageRef(unavailable=true)
    }
    fun content(scope:String,conversation:String,message:ChatMessage)=ImageContent.parts(message.content,ImageContent.refs(message.imagesJson)) {blobs.read(scope,conversation,it)}
}
