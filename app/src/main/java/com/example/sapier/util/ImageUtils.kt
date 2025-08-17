package com.example.sapier.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

object ImageUtils {
    
    /**
     * Load a bitmap from a URI with proper error handling
     */
    suspend fun loadBitmapFromUri(uri: Uri, context: Context): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                bitmap
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Resize bitmap to a maximum size to prevent memory issues
     */
    fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }
        
        val ratio = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Check if the image is a valid format for processing
     */
    fun isValidImageFormat(uri: Uri, context: Context): Boolean {
        return try {
            // Try to get the MIME type from content resolver
            val mimeType = context.contentResolver.getType(uri)
            mimeType?.startsWith("image/") == true
        } catch (e: Exception) {
            // Fallback to checking URI path
            val path = uri.toString().lowercase()
            path.endsWith(".jpg") || path.endsWith(".jpeg") || 
            path.endsWith(".png") || path.endsWith(".bmp") ||
            path.endsWith(".webp") || path.endsWith(".heic") ||
            path.endsWith(".heif")
        }
    }
}
