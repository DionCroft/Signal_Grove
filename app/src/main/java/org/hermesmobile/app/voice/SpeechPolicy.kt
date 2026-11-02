package org.hermesmobile.app.voice

enum class RecognitionMode { ON_DEVICE, SYSTEM, UNAVAILABLE }
object SpeechPolicy {
    fun recognition(localOnly: Boolean,api: Int,localAvailable: Boolean,systemAvailable: Boolean): RecognitionMode =
        if(localOnly) {if(api>=31 && localAvailable) RecognitionMode.ON_DEVICE else RecognitionMode.UNAVAILABLE}
        else if(systemAvailable) RecognitionMode.SYSTEM else RecognitionMode.UNAVAILABLE
    fun plainText(markdown: String): String = markdown.replace(Regex("(?s)```.*?```")," ")
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)")) {it.groupValues[1]}
        .replace(Regex("[*`#>]"),"").trim()
    fun chunks(text: String,maximum: Int): List<String> {
        require(maximum>=2)
        val result=mutableListOf<String>();var start=0
        while(start<text.length) {
            var end=minOf(start+maximum,text.length)
            if(end<text.length && Character.isHighSurrogate(text[end-1]) && Character.isLowSurrogate(text[end])) end--
            if(end<text.length) {
                val boundary=text.lastIndexOfAny(charArrayOf(' ','\n'),end-1)
                if(boundary>start+maximum/2) end=boundary+1
            }
            result+=text.substring(start,end);start=end
        }
        return result
    }
}
