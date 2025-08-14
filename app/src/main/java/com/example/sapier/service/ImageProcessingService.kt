package com.example.sapier.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.sapier.R
import com.example.sapier.data.PersonDetectionResult
import com.example.sapier.data.Receipt
import com.example.sapier.data.ReceiptItem
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDateTime
import java.util.*

class ImageProcessingService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "ImageProcessingChannel"
        
        fun startService(context: Context, imageUri: Uri) {
            val intent = Intent(context, ImageProcessingService::class.java).apply {
                putExtra("image_uri", imageUri.toString())
            }
            // Use startService for compatibility with API level 24
            context.startService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val imageUriString = intent?.getStringExtra("image_uri")
        if (imageUriString != null) {
            val imageUri = Uri.parse(imageUriString)
            // Start foreground service if API level supports it
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForeground(NOTIFICATION_ID, createNotification("Processing image..."))
            }
            
            serviceScope.launch {
                processImage(imageUri)
                stopSelf()
            }
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
    
    private suspend fun processImage(imageUri: Uri) {
        try {
            updateNotification("Analyzing image...")
            
            // Process image for both receipt and person detection
            val receiptResult = processReceipt(imageUri)
            val personResult = processPersonDetection(imageUri)
            
            // Send results to appropriate destinations
            if (receiptResult != null) {
                // TODO: Send receipt summary to Telegram
                updateNotification("Receipt processed successfully")
            }
            
            if (personResult?.containsSon == true) {
                // TODO: Send image to father via email/Telegram
                updateNotification("Son detected - sending to father")
            }
            
        } catch (e: Exception) {
            updateNotification("Error processing image: ${e.message}")
        }
    }
    
    private suspend fun processReceipt(imageUri: Uri): Receipt? {
        return withContext(Dispatchers.IO) {
            try {
                // For now, simulate receipt processing
                // In a real implementation, you would use ML Kit text recognition here
                val receiptData = analyzeReceiptWithLLM("Sample receipt text")
                if (receiptData != null) {
                    Receipt(
                        id = UUID.randomUUID().toString(),
                        imageUri = imageUri,
                        timestamp = LocalDateTime.now(),
                        items = receiptData.items,
                        total = receiptData.total,
                        store = receiptData.store,
                        date = receiptData.date
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private suspend fun processPersonDetection(imageUri: Uri): PersonDetectionResult? {
        return withContext(Dispatchers.IO) {
            try {
                // For now, simulate person detection
                // In a real implementation, you would use ML Kit face detection here
                val containsSon = true // Simulate detection
                
                PersonDetectionResult(
                    imageUri = imageUri,
                    timestamp = LocalDateTime.now(),
                    containsSon = containsSon,
                    confidence = if (containsSon) 0.8f else 0.0f
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private suspend fun analyzeReceiptWithLLM(text: String): ReceiptAnalysisResult? {
        // TODO: Implement OpenAI API call to analyze receipt text
        // For now, return a simple mock result
        return ReceiptAnalysisResult(
            items = listOf(ReceiptItem("Item", 1, 10.0)),
            total = 10.0,
            store = "Store",
            date = LocalDateTime.now().toString()
        )
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Image Processing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of image processing"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(message: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Sapier")
        .setContentText(message)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()
    
    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }
    
    data class ReceiptAnalysisResult(
        val items: List<ReceiptItem>,
        val total: Double,
        val store: String,
        val date: String
    )
}
