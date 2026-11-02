package org.hermesmobile.app.data

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Scoped immutable image blobs. Database rows hold only bounded metadata/references. */
class ImageBlobStore(private val directory:File) {
    private val lock=locks.getOrPut(directory.absolutePath) {Any()}
    private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
    private fun partition(scope:String,conversation:String):File {
        require(scope.isNotBlank() && conversation.isNotBlank())
        require(!java.nio.file.Files.isSymbolicLink(directory.toPath()))
        return File(directory,digest((scope+"\u0000"+conversation).toByteArray(Charsets.UTF_8))).also {require(!java.nio.file.Files.isSymbolicLink(it.toPath()))}
    }
    fun write(scope:String,conversation:String,bytes:ByteArray,width:Int,height:Int):ImageRef=synchronized(lock) {
        require(bytes.size in 1..ImageContent.MAX_IMAGE_BYTES && width in 1..2048 && height in 1..2048)
        val ref=ImageRef(digest(bytes),"image/jpeg",width,height)
        val destination=File(partition(scope,conversation),ref.hash+".image")
        if(destination.exists()) {require(read(scope,conversation,ref).contentEquals(bytes));return@synchronized ref}
        check(destination.parentFile!!.isDirectory || destination.parentFile!!.mkdirs())
        val temporary=File.createTempFile("image-",".tmp",destination.parentFile)
        try {
            FileOutputStream(temporary).use {it.write(bytes);it.fd.sync()}
            check(temporary.renameTo(destination)) {"Cannot save the private image."}
        } finally {temporary.delete()}
        ref
    }
    fun read(scope:String,conversation:String,ref:ImageRef):ByteArray=synchronized(lock) {
        require(ref.hash.matches(Regex("[a-f0-9]{64}")))
        val file=File(partition(scope,conversation),ref.hash+".image")
        require(file.isFile && file.length() in 1..ImageContent.MAX_IMAGE_BYTES.toLong()) {"The private image is missing or too large."}
        val bytes=file.inputStream().use {input ->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) {val count=input.read(buffer);if(count<0) break;require(output.size()+count<=ImageContent.MAX_IMAGE_BYTES);output.write(buffer,0,count)}
            output.toByteArray()
        }
        require(bytes.size<=ImageContent.MAX_IMAGE_BYTES && digest(bytes)==ref.hash) {"The private image failed its integrity check."}
        bytes
    }
    fun copy(scope:String,from:String,to:String,ref:ImageRef):ImageRef=write(scope,to,read(scope,from,ref),ref.width,ref.height)
    fun deleteConversation(scope:String,conversation:String)=synchronized(lock) {
        val folder=partition(scope,conversation)
        if(folder.exists()) {
            val children=folder.listFiles() ?: error("Cannot inspect private images.")
            require(children.all {it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath())})
            children.forEach {check(it.delete())};check(folder.delete())
        }
    }
    fun clearAll()=synchronized(lock) {
        if(!directory.exists()) return@synchronized
        require(!java.nio.file.Files.isSymbolicLink(directory.toPath()))
        val folders=directory.listFiles() ?: error("Cannot inspect private images.")
        require(folders.all {it.isDirectory && it.name.matches(Regex("[a-f0-9]{64}")) && !java.nio.file.Files.isSymbolicLink(it.toPath())})
        folders.forEach {folder ->
            val files=folder.listFiles() ?: error("Cannot inspect private images.")
            require(files.all {it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath())})
            files.forEach {check(it.delete())};check(folder.delete())
        }
    }
    companion object {private val locks=java.util.concurrent.ConcurrentHashMap<String,Any>()}
}
