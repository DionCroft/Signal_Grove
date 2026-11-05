package org.hermesmobile.app.voice

enum class DictationPhase { IDLE, LISTENING, PROCESSING }
enum class RecognitionFailure {
    PERMISSION, NETWORK, NO_MATCH, BUSY, AUDIO, LANGUAGE_UNSUPPORTED, LANGUAGE_UNAVAILABLE, SERVICE, CLIENT;

    companion object {
        // Android SpeechRecognizer's public error constants; no provider messages or audio are retained.
        fun fromAndroid(code:Int)=when(code) {
            9->PERMISSION
            1,2,4->NETWORK
            6,7->NO_MATCH
            8,10->BUSY
            3->AUDIO
            12->LANGUAGE_UNSUPPORTED
            13->LANGUAGE_UNAVAILABLE
            11->SERVICE
            else->CLIENT
        }
    }
}

/** Each new/cancelled attempt invalidates callbacks and permission results from older attempts. */
data class DictationSession(
    val mode:RecognitionMode=RecognitionMode.UNAVAILABLE,
    val language:String="en-GB",
    val phase:DictationPhase=DictationPhase.IDLE,
    val generation:Long=0,
    val failure:RecognitionFailure?=null
) {
    fun configure(mode:RecognitionMode,language:String)=
        if(this.mode==mode && this.language==language) this
        else copy(mode=mode,language=language,phase=DictationPhase.IDLE,generation=generation+1,failure=null)
    fun begin()=if(mode==RecognitionMode.UNAVAILABLE || phase!=DictationPhase.IDLE) this
        else copy(phase=DictationPhase.LISTENING,generation=generation+1,failure=null)
    fun accepts(token:Long)=generation==token && phase!=DictationPhase.IDLE
    fun processing(token:Long)=if(accepts(token)) copy(phase=DictationPhase.PROCESSING) else this
    fun fail(token:Long,reason:RecognitionFailure)=if(accepts(token)) copy(phase=DictationPhase.IDLE,failure=reason) else this
    fun finish(token:Long)=if(accepts(token)) copy(phase=DictationPhase.IDLE,failure=null) else this
    fun cancel(clearFailure:Boolean=false)=copy(phase=DictationPhase.IDLE,generation=generation+1,failure=if(clearFailure) null else failure)
}
