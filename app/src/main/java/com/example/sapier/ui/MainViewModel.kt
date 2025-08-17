package com.example.sapier.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sapier.data.*
import com.example.sapier.service.EmailService
import com.example.sapier.service.TelegramService
import com.example.sapier.util.ImageUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDateTime
import java.util.*

class MainViewModel(
    private val repository: Repository,
    private val telegramService: TelegramService,
    private val emailService: EmailService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    // ML Kit recognizers
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
    )
    
    init {
        viewModelScope.launch {
            repository.config.collect { config ->
                _uiState.update { it.copy(
                    config = config,
                    isConfigValid = repository.isConfigValid(config)
                ) }
            }
        }
    }
    
    fun processImage(imageUri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(processingStatus = ProcessingStatus.Processing) }
            
            try {
                // Validate image format
                if (!ImageUtils.isValidImageFormat(imageUri, context)) {
                    throw IllegalArgumentException("Invalid image format. Please select a valid image file.")
                }
                
                // Process image in background
                val results = withContext(Dispatchers.IO) {
                    processImageInBackground(imageUri, context)
                }
                
                // Update UI with results
                _uiState.update { it.copy(
                    receipts = repository.getReceipts(),
                    personDetections = repository.getPersonDetections(),
                    processingStatus = ProcessingStatus.Success(
                        buildSuccessMessage(results.receipt, results.personDetection)
                    )
                ) }
                
                // Send to destinations if needed
                results.receipt?.let { receipt ->
                    results.personDetection?.let { personDetection ->
                        sendToDestinations(receipt, personDetection)
                    }
                }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    processingStatus = ProcessingStatus.Error("Error processing image: ${e.message}")
                ) }
            }
        }
    }
    
    fun processMultipleImages(imageUris: List<Uri>, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(processingStatus = ProcessingStatus.Processing) }
            
            try {
                var processedCount = 0
                var receiptCount = 0
                var personCount = 0
                
                for (imageUri in imageUris) {
                    try {
                        // Validate image format
                        if (!ImageUtils.isValidImageFormat(imageUri, context)) {
                            continue // Skip invalid images
                        }
                        
                        // Process image in background
                        val results = withContext(Dispatchers.IO) {
                            processImageInBackground(imageUri, context)
                        }
                        
                        processedCount++
                        if (results.receipt != null) receiptCount++
                        if (results.personDetection?.containsSon == true) personCount++
                        
                        // Send to destinations if needed
                        results.receipt?.let { receipt ->
                            results.personDetection?.let { personDetection ->
                                sendToDestinations(receipt, personDetection)
                            }
                        }
                        
                    } catch (e: Exception) {
                        // Continue processing other images even if one fails
                        println("Error processing image ${imageUri}: ${e.message}")
                    }
                }
                
                // Update UI with batch results
                _uiState.update { it.copy(
                    receipts = repository.getReceipts(),
                    personDetections = repository.getPersonDetections(),
                    processingStatus = ProcessingStatus.Success(
                        "Processed $processedCount images. Found $receiptCount receipts and $personCount people."
                    )
                ) }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    processingStatus = ProcessingStatus.Error("Error processing images: ${e.message}")
                ) }
            }
        }
    }
    
    private fun buildSuccessMessage(receipt: Receipt?, personDetection: PersonDetectionResult?): String {
        val messages = mutableListOf<String>()
        
        if (receipt != null) {
            messages.add("Receipt detected: ${receipt.store ?: "Unknown store"} - $${String.format("%.2f", receipt.total)}")
        }
        
        if (personDetection?.containsSon == true) {
            messages.add("Person detected - sending to father")
        }
        
        return if (messages.isNotEmpty()) {
            messages.joinToString(". ")
        } else {
            "Image processed but no receipts or people detected"
        }
    }
    
    private suspend fun processImageInBackground(imageUri: Uri, context: Context): ProcessingResults {
        // Load image
        val bitmap = loadImageFromUri(imageUri, context)
        
        // Resize bitmap if too large to prevent memory issues
        val resizedBitmap = if (bitmap.width > 1024 || bitmap.height > 1024) {
            ImageUtils.resizeBitmap(bitmap, 1024)
        } else {
            bitmap
        }
        
        val image = InputImage.fromBitmap(resizedBitmap, 0)
        
        // Process for text (receipt detection)
        val receipt = processReceiptText(image, imageUri)
        
        // Process for faces (person detection)
        val personDetection = processPersonDetection(image, imageUri)
        
        return ProcessingResults(receipt, personDetection)
    }
    
    private suspend fun loadImageFromUri(uri: Uri, context: Context): Bitmap {
        return withContext(Dispatchers.IO) {
            val bitmap = ImageUtils.loadBitmapFromUri(uri, context)
            bitmap ?: throw IllegalStateException("Failed to load image")
        }
    }
    
    private suspend fun processReceiptText(image: InputImage, imageUri: Uri): Receipt? {
        return withContext(Dispatchers.IO) {
            try {
                val task = textRecognizer.process(image)
                val result = task.await()
                val text = result.text
                
                // Debug: Print extracted text for troubleshooting
                println("Extracted text from image: ${text.take(200)}...")
                
                // Enhanced receipt detection logic
                if (isReceipt(text)) {
                    val receiptData = parseReceiptText(text)
                    val receipt = Receipt(
                        id = UUID.randomUUID().toString(),
                        imageUri = imageUri,
                        timestamp = LocalDateTime.now(),
                        items = receiptData.items,
                        total = receiptData.total,
                        store = receiptData.store,
                        date = receiptData.date
                    )
                    
                    repository.addReceipt(receipt)
                    println("Receipt detected: ${receipt.store} - $${receipt.total}")
                    receipt
                } else {
                    println("No receipt detected in image")
                    null
                }
            } catch (e: Exception) {
                println("Error processing receipt text: ${e.message}")
                null
            }
        }
    }
    
    private suspend fun processPersonDetection(image: InputImage, imageUri: Uri): PersonDetectionResult? {
        return withContext(Dispatchers.IO) {
            try {
                val task = faceDetector.process(image)
                val result = task.await()
                val containsSon = result.size > 0 // Simple detection - if faces found, assume son
                
                val personDetection = PersonDetectionResult(
                    imageUri = imageUri,
                    timestamp = LocalDateTime.now(),
                    containsSon = containsSon,
                    confidence = if (containsSon) 0.8f else 0.0f
                )
                
                repository.addPersonDetection(personDetection)
                personDetection
            } catch (e: Exception) {
                println("Error processing person detection: ${e.message}")
                null
            }
        }
    }
    
    private fun isReceipt(text: String): Boolean {
        val receiptKeywords = listOf(
            "total", "subtotal", "tax", "receipt", "invoice", "amount", "price",
            "item", "quantity", "store", "shop", "market", "grocery", "bill",
            "payment", "due", "balance", "cash", "card", "credit", "debit",
            "change", "discount", "sale", "purchase", "transaction", "order",
            "customer", "cashier", "register", "pos", "terminal", "checkout",
            "summary", "details", "line", "product", "service", "charge",
            "fee", "cost", "expense", "spend", "buy", "purchase", "sold",
            "date", "time", "location", "address", "phone", "email"
        )
        
        val lowerText = text.lowercase()
        val keywordMatches = receiptKeywords.count { keyword -> lowerText.contains(keyword) }
        
        // Consider it a receipt if it has at least 3 receipt-related keywords
        // or if it contains common receipt patterns
        val hasReceiptPatterns = lowerText.contains("total") && 
                                (lowerText.contains("$") || lowerText.contains("amount") || lowerText.contains("price"))
        
        return keywordMatches >= 3 || hasReceiptPatterns
    }
    
    private fun parseReceiptText(text: String): ReceiptAnalysisResult {
        val lines = text.split("\n")
        val items = mutableListOf<ReceiptItem>()
        var total = 0.0
        var subtotal = 0.0
        var tax = 0.0
        var store = "Unknown Store"
        var date = LocalDateTime.now().toString()
        
        // Extract store name (usually the first non-empty line that's not a header)
        for (line in lines.take(10)) {
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty() && 
                !trimmedLine.lowercase().contains("receipt") &&
                !trimmedLine.lowercase().contains("invoice") &&
                !trimmedLine.lowercase().contains("total") &&
                !trimmedLine.lowercase().contains("tax") &&
                !trimmedLine.lowercase().contains("subtotal") &&
                !trimmedLine.lowercase().contains("date") &&
                !trimmedLine.lowercase().contains("time") &&
                store == "Unknown Store") {
                store = trimmedLine
                break
            }
        }
        
        // Extract financial information
        for (line in lines) {
            val trimmedLine = line.trim()
            val lowerLine = trimmedLine.lowercase()
            
            // Look for total
            if (lowerLine.contains("total") && !lowerLine.contains("subtotal")) {
                val totalMatch = Regex("\\$?([0-9]+[.,]?[0-9]*)").find(trimmedLine)
                totalMatch?.let {
                    val totalStr = it.groupValues[1].replace(",", "")
                    total = totalStr.toDoubleOrNull() ?: 0.0
                }
            }
            
            // Look for subtotal
            if (lowerLine.contains("subtotal")) {
                val subtotalMatch = Regex("\\$?([0-9]+[.,]?[0-9]*)").find(trimmedLine)
                subtotalMatch?.let {
                    val subtotalStr = it.groupValues[1].replace(",", "")
                    subtotal = subtotalStr.toDoubleOrNull() ?: 0.0
                }
            }
            
            // Look for tax
            if (lowerLine.contains("tax")) {
                val taxMatch = Regex("\\$?([0-9]+[.,]?[0-9]*)").find(trimmedLine)
                taxMatch?.let {
                    val taxStr = it.groupValues[1].replace(",", "")
                    tax = taxStr.toDoubleOrNull() ?: 0.0
                }
            }
            
            // Look for date
            if (lowerLine.contains("date") || lowerLine.contains("time")) {
                val dateMatch = Regex("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})").find(trimmedLine)
                dateMatch?.let {
                    date = it.groupValues[1]
                }
            }
            
            // Try to extract items (lines with prices)
            val itemMatch = Regex("(.+?)\\s+\\$?([0-9]+[.,]?[0-9]*)").find(trimmedLine)
            if (itemMatch != null && !lowerLine.contains("total") && !lowerLine.contains("tax") && !lowerLine.contains("subtotal")) {
                val itemName = itemMatch.groupValues[1].trim()
                val itemPrice = itemMatch.groupValues[2].replace(",", "").toDoubleOrNull() ?: 0.0
                
                if (itemName.isNotEmpty() && itemPrice > 0 && itemName.length > 2) {
                    items.add(ReceiptItem(itemName, 1, itemPrice))
                }
            }
        }
        
        // If no total found, use subtotal or sum of items
        if (total == 0.0) {
            total = if (subtotal > 0) subtotal else items.sumOf { it.price * it.quantity }
        }
        
        // If no items found, create a summary item
        if (items.isEmpty() && total > 0) {
            items.add(ReceiptItem("Receipt Total", 1, total))
        }
        
        return ReceiptAnalysisResult(
            items = items,
            total = total,
            store = store,
            date = date
        )
    }
    
    private suspend fun sendToDestinations(receipt: Receipt, personDetection: PersonDetectionResult) {
        val config = _uiState.value.config
        
        // Send individual receipt details to Telegram immediately
        if (receipt.items.isNotEmpty()) {
            val receiptSent = telegramService.sendIndividualReceipt(
                receipt,
                config.telegramBotToken,
                config.telegramChatId
            )
            
            if (receiptSent) {
                println("Receipt details sent to Telegram successfully")
            } else {
                println("Failed to send receipt details to Telegram")
            }
            
            // Also send daily summary if there are multiple receipts
            val receiptsForToday = repository.getReceiptsForDate(LocalDateTime.now())
            if (receiptsForToday.size > 1) {
                telegramService.sendReceiptSummary(
                    receiptsForToday,
                    config.telegramBotToken,
                    config.telegramChatId
                )
            }
        }
        
        // Send photo to father if son is detected
        if (personDetection.containsSon) {
            emailService.sendPhotoToFather(
                personDetection.imageUri,
                config.sonName,
                config.emailUsername,
                config.emailPassword,
                config.fatherEmail
            )
            
            // Also send via Telegram
            telegramService.sendPhoto(
                personDetection.imageUri,
                "New photo of ${config.sonName}",
                config.telegramBotToken,
                config.telegramChatId
            )
        }
    }
    
    fun updateConfig(newConfig: AppConfig) {
        viewModelScope.launch {
            repository.updateConfig(newConfig)
        }
    }
    
    fun clearProcessingStatus() {
        _uiState.update { it.copy(processingStatus = ProcessingStatus.Idle) }
    }
    
    fun getDailySummary(): String {
        val receipts = repository.getReceiptsForDate(LocalDateTime.now())
        val totalSpent = receipts.sumOf { it.total }
        val totalReceipts = receipts.size
        
        return "Today's Summary:\n" +
               "Total Receipts: $totalReceipts\n" +
               "Total Spent: $${String.format("%.2f", totalSpent)}"
    }
    
    fun clearAllData() {
        repository.clearAllData()
        _uiState.update { it.copy(
            receipts = emptyList(),
            personDetections = emptyList()
        ) }
    }
    
    fun testTelegramConnection() {
        viewModelScope.launch {
            val config = _uiState.value.config
            if (config.telegramBotToken.isNotEmpty() && config.telegramChatId.isNotEmpty()) {
                try {
                    val testMessage = "🧪 <b>Test Message</b>\n\nSapier app is connected and ready to send receipt details!"
                    val json = org.json.JSONObject().apply {
                        put("chat_id", config.telegramChatId)
                        put("text", testMessage)
                        put("parse_mode", "HTML")
                    }
                    
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val requestBody = json.toString().toRequestBody(mediaType)
                    val request = okhttp3.Request.Builder()
                        .url("https://api.telegram.org/bot${config.telegramBotToken}/sendMessage")
                        .post(requestBody)
                        .build()
                    
                    val client = okhttp3.OkHttpClient()
                    val response = client.newCall(request).execute()
                    
                    if (response.isSuccessful) {
                        _uiState.update { it.copy(
                            processingStatus = ProcessingStatus.Success("Telegram connection test successful!")
                        ) }
                    } else {
                        _uiState.update { it.copy(
                            processingStatus = ProcessingStatus.Error("Telegram connection test failed. Check your bot token and chat ID.")
                        ) }
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Error("Telegram connection test failed: ${e.message}")
                    ) }
                }
            } else {
                _uiState.update { it.copy(
                    processingStatus = ProcessingStatus.Error("Please configure Telegram bot token and chat ID first.")
                ) }
            }
        }
    }
    
    fun sendSonPhoto(context: Context, selectedDate: LocalDateTime? = null) {
        viewModelScope.launch {
            val config = _uiState.value.config
            if (config.telegramBotToken.isEmpty() || config.telegramChatId.isEmpty()) {
                _uiState.update { it.copy(
                    processingStatus = ProcessingStatus.Error("Please configure Telegram settings first.")
                ) }
                return@launch
            }
            
            _uiState.update { it.copy(processingStatus = ProcessingStatus.Processing) }
            
            try {
                // Find all images in "Son" album
                val sonImages = withContext(Dispatchers.IO) {
                    findImagesInSonAlbum(context, selectedDate)
                }
                
                if (sonImages.isEmpty()) {
                    val dateMsg = selectedDate?.let { " for ${it.toLocalDate()}" } ?: ""
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Error("No images found in 'Son' album$dateMsg. Please create a 'Son' album and add photos to it.")
                    ) }
                    return@launch
                }
                
                var successCount = 0
                var failCount = 0
                
                // Send all Son images via Telegram
                for (imageUri in sonImages) {
                    try {
                        val dateMsg = selectedDate?.let { " from ${it.toLocalDate()}" } ?: ""
                        val photoSent = telegramService.sendPhoto(
                            imageUri,
                            "📸 Photo of ${config.sonName}$dateMsg from Son album",
                            config.telegramBotToken,
                            config.telegramChatId
                        )
                        
                        if (photoSent) {
                            successCount++
                        } else {
                            failCount++
                        }
                        
                        // Small delay to avoid rate limiting
                        kotlinx.coroutines.delay(500)
                        
                    } catch (e: Exception) {
                        failCount++
                        println("Error sending image ${imageUri}: ${e.message}")
                    }
                }
                
                val dateMsg = selectedDate?.let { " for ${it.toLocalDate()}" } ?: ""
                val resultMessage = if (successCount > 0) {
                    "Successfully sent $successCount photos of ${config.sonName}$dateMsg from Son album" +
                    if (failCount > 0) " ($failCount failed)" else ""
                } else {
                    "Failed to send any photos. Check your Telegram configuration."
                }
                
                _uiState.update { it.copy(
                    processingStatus = if (successCount > 0) 
                        ProcessingStatus.Success(resultMessage)
                    else 
                        ProcessingStatus.Error(resultMessage)
                ) }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    processingStatus = ProcessingStatus.Error("Error scanning Son album: ${e.message}")
                ) }
            }
        }
    }
    
    fun sendSaraPhoto(context: Context, selectedDate: LocalDateTime? = null) {
        viewModelScope.launch {
            val config = _uiState.value.config
            if (config.telegramBotToken.isEmpty() || config.telegramChatId.isEmpty()) {
                _uiState.update { it.copy(
                    processingStatus = ProcessingStatus.Error("Please configure Telegram settings first.")
                ) }
                return@launch
            }
            
            _uiState.update { it.copy(processingStatus = ProcessingStatus.Processing) }
            
            try {
                // Find all images in "Sara" album
                val saraImages = withContext(Dispatchers.IO) {
                    findImagesInSaraAlbum(context, selectedDate)
                }
                
                if (saraImages.isEmpty()) {
                    val dateMsg = selectedDate?.let { " for ${it.toLocalDate()}" } ?: ""
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Error("No images found in 'Sara' album$dateMsg. Please create a 'Sara' album and add photos to it.")
                    ) }
                    return@launch
                }
                
                var successCount = 0
                var failCount = 0
                
                // Send all Sara images via Telegram
                for (imageUri in saraImages) {
                    try {
                        val dateMsg = selectedDate?.let { " from ${it.toLocalDate()}" } ?: ""
                        val photoSent = telegramService.sendPhoto(
                            imageUri,
                            "📸 Photo of Sara$dateMsg from Sara album",
                            config.telegramBotToken,
                            config.telegramChatId
                        )
                        
                        if (photoSent) {
                            successCount++
                        } else {
                            failCount++
                        }
                        
                        // Small delay to avoid rate limiting
                        kotlinx.coroutines.delay(500)
                        
                    } catch (e: Exception) {
                        failCount++
                        println("Error sending image ${imageUri}: ${e.message}")
                    }
                }
                
                val dateMsg = selectedDate?.let { " for ${it.toLocalDate()}" } ?: ""
                val resultMessage = if (successCount > 0) {
                    "Successfully sent $successCount photos of Sara$dateMsg from Sara album" +
                    if (failCount > 0) " ($failCount failed)" else ""
                } else {
                    "Failed to send any photos. Check your Telegram configuration."
                }
                
                _uiState.update { it.copy(
                    processingStatus = if (successCount > 0) 
                        ProcessingStatus.Success(resultMessage)
                    else 
                        ProcessingStatus.Error(resultMessage)
                ) }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    processingStatus = ProcessingStatus.Error("Error scanning Sara album: ${e.message}")
                ) }
            }
        }
    }
    
    private suspend fun findImagesInSonAlbum(context: Context, selectedDate: LocalDateTime? = null): List<Uri> {
        return withContext(Dispatchers.IO) {
            val sonImages = mutableListOf<Uri>()
            
            try {
                // First, try to find the "Son" album
                val albumId = findAlbumByName(context, "Son")
                
                if (albumId != null) {
                    // Query images from the specific album
                    val projection = arrayOf(
                        android.provider.MediaStore.Images.Media._ID,
                        android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                        android.provider.MediaStore.Images.Media.DATE_ADDED,
                        android.provider.MediaStore.Images.Media.DATE_TAKEN
                    )
                    
                    // Build selection criteria
                    val selection = if (selectedDate != null) {
                        val startOfDay = selectedDate.toLocalDate().atStartOfDay()
                        val endOfDay = selectedDate.toLocalDate().plusDays(1).atStartOfDay().minusNanos(1)
                        val startEpoch = startOfDay.toEpochSecond(java.time.ZoneOffset.UTC)
                        val endEpoch = endOfDay.toEpochSecond(java.time.ZoneOffset.UTC)
                        
                        "${android.provider.MediaStore.Images.Media.BUCKET_ID} = ? AND ${android.provider.MediaStore.Images.Media.DATE_ADDED} BETWEEN ? AND ?"
                    } else {
                        "${android.provider.MediaStore.Images.Media.BUCKET_ID} = ?"
                    }
                    
                    val selectionArgs = if (selectedDate != null) {
                        val startOfDay = selectedDate.toLocalDate().atStartOfDay()
                        val endOfDay = selectedDate.toLocalDate().plusDays(1).atStartOfDay().minusNanos(1)
                        val startEpoch = startOfDay.toEpochSecond(java.time.ZoneOffset.UTC)
                        val endEpoch = endOfDay.toEpochSecond(java.time.ZoneOffset.UTC)
                        
                        arrayOf(albumId.toString(), startEpoch.toString(), endEpoch.toString())
                    } else {
                        arrayOf(albumId.toString())
                    }
                    
                    val cursor = context.contentResolver.query(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"
                    )
                    
                    cursor?.use {
                        val idColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                        
                        while (it.moveToNext()) {
                            val id = it.getLong(idColumn)
                            val contentUri = android.content.ContentUris.withAppendedId(
                                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                            sonImages.add(contentUri)
                        }
                    }
                } else {
                    println("Album 'Son' not found. Please create an album named 'Son' and add photos to it.")
                }
                
            } catch (e: Exception) {
                println("Error scanning Son album: ${e.message}")
            }
            
            sonImages
        }
    }
    
    private suspend fun findImagesInSaraAlbum(context: Context, selectedDate: LocalDateTime? = null): List<Uri> {
        return withContext(Dispatchers.IO) {
            val saraImages = mutableListOf<Uri>()
            
            try {
                // First, try to find the "Sara" album
                val albumId = findAlbumByName(context, "Sara")
                
                if (albumId != null) {
                    // Query images from the specific album
                    val projection = arrayOf(
                        android.provider.MediaStore.Images.Media._ID,
                        android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                        android.provider.MediaStore.Images.Media.DATE_ADDED,
                        android.provider.MediaStore.Images.Media.DATE_TAKEN
                    )
                    
                    // Build selection criteria
                    val selection = if (selectedDate != null) {
                        val startOfDay = selectedDate.toLocalDate().atStartOfDay()
                        val endOfDay = selectedDate.toLocalDate().plusDays(1).atStartOfDay().minusNanos(1)
                        val startEpoch = startOfDay.toEpochSecond(java.time.ZoneOffset.UTC)
                        val endEpoch = endOfDay.toEpochSecond(java.time.ZoneOffset.UTC)
                        
                        "${android.provider.MediaStore.Images.Media.BUCKET_ID} = ? AND ${android.provider.MediaStore.Images.Media.DATE_ADDED} BETWEEN ? AND ?"
                    } else {
                        "${android.provider.MediaStore.Images.Media.BUCKET_ID} = ?"
                    }
                    
                    val selectionArgs = if (selectedDate != null) {
                        val startOfDay = selectedDate.toLocalDate().atStartOfDay()
                        val endOfDay = selectedDate.toLocalDate().plusDays(1).atStartOfDay().minusNanos(1)
                        val startEpoch = startOfDay.toEpochSecond(java.time.ZoneOffset.UTC)
                        val endEpoch = endOfDay.toEpochSecond(java.time.ZoneOffset.UTC)
                        
                        arrayOf(albumId.toString(), startEpoch.toString(), endEpoch.toString())
                    } else {
                        arrayOf(albumId.toString())
                    }
                    
                    val cursor = context.contentResolver.query(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"
                    )
                    
                    cursor?.use {
                        val idColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                        
                        while (it.moveToNext()) {
                            val id = it.getLong(idColumn)
                            val contentUri = android.content.ContentUris.withAppendedId(
                                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                            saraImages.add(contentUri)
                        }
                    }
                } else {
                    println("Album 'Sara' not found. Please create an album named 'Sara' and add photos to it.")
                }
                
            } catch (e: Exception) {
                println("Error scanning Sara album: ${e.message}")
            }
            
            saraImages
        }
    }
    
    private suspend fun findAlbumByName(context: Context, albumName: String): Long? {
        return withContext(Dispatchers.IO) {
            try {
                val projection = arrayOf(
                    android.provider.MediaStore.Images.Media.BUCKET_ID,
                    android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                )
                
                val cursor = context.contentResolver.query(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    null
                )
                
                cursor?.use {
                    val bucketIdColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.BUCKET_ID)
                    val bucketNameColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    
                    while (it.moveToNext()) {
                        val bucketName = it.getString(bucketNameColumn)
                        if (bucketName?.equals(albumName, ignoreCase = true) == true) {
                            return@withContext it.getLong(bucketIdColumn)
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error finding album: ${e.message}")
            }
            null
        }
    }
    
    data class ProcessingResults(
        val receipt: Receipt?,
        val personDetection: PersonDetectionResult?
    )
    
    data class ReceiptAnalysisResult(
        val items: List<ReceiptItem>,
        val total: Double,
        val store: String,
        val date: String
    )
}

