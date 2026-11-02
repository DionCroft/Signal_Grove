package org.hermesmobile.app.data

import android.graphics.*
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

data class NormalizedImage(val bytes:ByteArray,val width:Int,val height:Int)
object ImageCodec {
    fun readBounded(input:InputStream):ByteArray {
        val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true) {val count=input.read(buffer);if(count<0) break;require(output.size()+count<=ImageContent.MAX_SOURCE_BYTES) {"Choose an image smaller than 8 MB."};output.write(buffer,0,count)}
        return output.toByteArray()
    }
    fun normalize(bytes:ByteArray):NormalizedImage {
        require(bytes.size in 1..ImageContent.MAX_SOURCE_BYTES)
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0 && bounds.outWidth.toLong()*bounds.outHeight<=100_000_000) {"This image cannot be decoded within mobile limits."}
        var sample=1;while(bounds.outWidth/sample>1536 || bounds.outHeight/sample>1536) sample*=2
        val decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply {inSampleSize=sample}) ?: error("This image format is unavailable.")
        val orientation=runCatching {ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix=Matrix().apply {when(orientation) {
            ExifInterface.ORIENTATION_ROTATE_90->postRotate(90f);ExifInterface.ORIENTATION_ROTATE_180->postRotate(180f);ExifInterface.ORIENTATION_ROTATE_270->postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL->postScale(-1f,1f);ExifInterface.ORIENTATION_FLIP_VERTICAL->postScale(1f,-1f)
            ExifInterface.ORIENTATION_TRANSPOSE->{postRotate(90f);postScale(-1f,1f)};ExifInterface.ORIENTATION_TRANSVERSE->{postRotate(270f);postScale(-1f,1f)}
        }}
        val oriented=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true)
        val opaque=Bitmap.createBitmap(oriented.width,oriented.height,Bitmap.Config.ARGB_8888)
        Canvas(opaque).apply {drawColor(Color.WHITE);drawBitmap(oriented,0f,0f,null)}
        return try {
            var output=ByteArrayOutputStream();check(opaque.compress(Bitmap.CompressFormat.JPEG,85,output))
            if(output.size()>ImageContent.MAX_IMAGE_BYTES) {output=ByteArrayOutputStream();check(opaque.compress(Bitmap.CompressFormat.JPEG,60,output))}
            require(output.size()<=ImageContent.MAX_IMAGE_BYTES) {"This image is too large after resizing."}
            NormalizedImage(output.toByteArray(),opaque.width,opaque.height)
        } finally {opaque.recycle();if(oriented!==decoded) oriented.recycle();decoded.recycle()}
    }
}
