package org.hermesmobile.app.data

import java.security.MessageDigest

/** Automatic labels share Hermes' single-line rules but never alter the submitted message. */
object NewSessionTitle {
    private fun clean(value:String)=buildString {
        var space=false
        value.codePoints().forEach {point->
            when {
                Character.isWhitespace(point) || Character.isSpaceChar(point)->space=isNotEmpty()
                Character.isISOControl(point) || point in 0xD800..0xDFFF || point in 0x200B..0x200F || point in 0x2028..0x202E || point in 0x2060..0x2069 || point==0xFEFF || point==0xFFFC || point in 0xFFF9..0xFFFB->Unit
                else->{if(space) append(' ');space=false;appendCodePoint(point)}
            }
        }
    }
    fun create(message:String,sessionId:String,fallback:String):String {
        require(sessionId.isNotBlank())
        val label=clean(message).ifBlank {clean(fallback).also {require(it.isNotBlank())}}
        val base=if(label.codePointCount(0,label.length)>60) label.substring(0,label.offsetByCodePoints(0,60)) else label
        // Stable per client-chosen session, unlike a retry counter or the opening text alone.
        val suffix=MessageDigest.getInstance("SHA-256").digest(sessionId.toByteArray(Charsets.UTF_8)).take(8).joinToString("") {"%02x".format(it)}
        return "$base · $suffix"
    }
}
