package com.example.sapier.service

import android.content.Context
import android.net.Uri
import com.example.sapier.data.Receipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TelegramService(private val context: Context) {
    
    private val client = OkHttpClient()
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    
    suspend fun sendReceiptSummary(receipts: List<Receipt>, botToken: String, chatId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val summary = generateReceiptSummary(receipts)
                val message = buildSummaryMessage(summary)
                
                val json = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", message)
                    put("parse_mode", "HTML")
                }
                
                val requestBody = json.toString().toRequestBody(mediaType)
                val request = Request.Builder()
                    .url("https://api.telegram.org/bot$botToken/sendMessage")
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }
    
    suspend fun sendPhoto(imageUri: Uri, caption: String, botToken: String, chatId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = getFileFromUri(imageUri)
                if (file == null) return@withContext false
                
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId)
                    .addFormDataPart("caption", caption)
                    .addFormDataPart("photo", file.name, file.asRequestBody("image/*".toMediaType()))
                    .build()
                
                val request = Request.Builder()
                    .url("https://api.telegram.org/bot$botToken/sendPhoto")
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }
    
    private fun generateReceiptSummary(receipts: List<Receipt>): ReceiptSummary {
        val totalSpent = receipts.sumOf { it.total }
        val storeCounts = receipts.groupBy { it.store ?: "Unknown Store" }
            .mapValues { it.value.size }
        val itemCounts = receipts.flatMap { it.items }
            .groupBy { it.name }
            .mapValues { it.value.sumOf { item -> item.quantity } }
        
        return ReceiptSummary(
            totalReceipts = receipts.size,
            totalSpent = totalSpent,
            stores = storeCounts,
            topItems = itemCounts.toList().sortedByDescending { it.second }.take(5).toMap(),
            date = LocalDateTime.now()
        )
    }
    
    private fun buildSummaryMessage(summary: ReceiptSummary): String {
        val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
        val date = summary.date.format(dateFormatter)
        
        val storesText = summary.stores.entries.joinToString("\n") { 
            "• ${it.key}: ${it.value} receipt(s)" 
        }
        
        val topItemsText = summary.topItems.entries.joinToString("\n") { 
            "• ${it.key}: ${it.value} item(s)" 
        }
        
        return """
            📊 <b>Daily Purchase Summary - $date</b>
            
            📋 <b>Overview:</b>
            • Total Receipts: ${summary.totalReceipts}
            • Total Spent: $${String.format("%.2f", summary.totalSpent)}
            
            🏪 <b>Stores Visited:</b>
            $storesText
            
            🛒 <b>Top Items:</b>
            $topItemsText
        """.trimIndent()
    }
    
    private fun getFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
            inputStream?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            null
        }
    }
    
    data class ReceiptSummary(
        val totalReceipts: Int,
        val totalSpent: Double,
        val stores: Map<String, Int>,
        val topItems: Map<String, Int>,
        val date: LocalDateTime
    )
}
