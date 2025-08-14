package com.example.sapier.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

object ImageUtils {
    
    /**
     * Creates a temporary file from a URI
     */
    fun createTempFileFromUri(context: Context, uri: Uri, prefix: String = "image"): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
            
            inputStream?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            
            file
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Gets file extension from URI
     */
    fun getFileExtension(context: Context, uri: Uri): String? {
        return context.contentResolver.getType(uri)?.split("/")?.lastOrNull()
    }
    
    /**
     * Checks if the image is likely a receipt based on file name or content type
     */
    fun isLikelyReceipt(fileName: String): Boolean {
        val receiptKeywords = listOf("receipt", "bill", "invoice", "purchase", "transaction")
        val lowerFileName = fileName.lowercase()
        
        return receiptKeywords.any { keyword ->
            lowerFileName.contains(keyword)
        }
    }
    
    /**
     * Generates a unique filename
     */
    fun generateUniqueFileName(prefix: String, extension: String): String {
        return "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$extension"
    }
    
    /**
     * Cleans up temporary files
     */
    fun cleanupTempFiles(context: Context) {
        try {
            val cacheDir = context.cacheDir
            val files = cacheDir.listFiles { file ->
                file.name.startsWith("temp_") || 
                file.name.startsWith("image_") || 
                file.name.startsWith("email_")
            }
            
            files?.forEach { file ->
                if (file.exists() && file.canWrite()) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
