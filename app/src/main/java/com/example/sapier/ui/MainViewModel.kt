package com.example.sapier.ui
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sapier.data.*
import com.example.sapier.service.EmailService
import com.example.sapier.service.GooglePhotosService
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
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts

class MainViewModel(
    private val repository: Repository,
    private val telegramService: TelegramService,
    private val emailService: EmailService
) : ViewModel() {
    
    fun signInToGooglePhotos(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        Log.d("SapierApp", "Attempting to sign in to Google Photos...")
        
        // Initialize the service if needed
        if (googlePhotosService == null) {
            Log.d("SapierApp", "Initializing GooglePhotosService...")
            initGooglePhotosService(activity)
        }
        
        googlePhotosService?.let { service ->
            Log.d("SapierApp", "Getting sign-in intent from GooglePhotosService...")
            val signInIntent = service.getSignInIntent()
            Log.d("SapierApp", "Launching Google Sign-In...")
            launcher.launch(signInIntent)
        } ?: run {
            Log.e("SapierApp", "GooglePhotosService is null!")
            _uiState.update { it.copy(
                processingStatus = ProcessingStatus.Error("Failed to initialize Google Photos service")
            ) }
        }
    }
    
    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            val success = googlePhotosService?.handleSignInResult(data) ?: false
            if (success) {
                _uiState.update { it.copy(
                    processingStatus = ProcessingStatus.Info("Successfully signed in to Google Photos! You can now use the auto-scan features.")
                ) }
            } else {
                _uiState.update { it.copy(
                    processingStatus = ProcessingStatus.Error("Failed to sign in to Google Photos. Please try again.")
                ) }
            }
        }
    }
    
    fun updateProcessingStatus(message: String) {
        _uiState.update { it.copy(
            processingStatus = ProcessingStatus.Info(message)
        ) }
    }
    
    fun signOutFromGooglePhotos() {
        viewModelScope.launch {
            googlePhotosService?.signOut()
            _uiState.update { it.copy(
                processingStatus = ProcessingStatus.Info("Signed out from Google Photos")
            ) }
        }
    }
    
    private var googlePhotosService: GooglePhotosService? = null
    
    fun initGooglePhotosService(context: Context) {
        if (googlePhotosService == null) {
            googlePhotosService = GooglePhotosService(context)
            googlePhotosService?.initializeIfNeeded()
        }
        
        // Set access token from config if available
        val config = _uiState.value.config
        if (config.googlePhotosAccessToken.isNotEmpty()) {
            googlePhotosService?.setAccessToken(config.googlePhotosAccessToken)
            Log.d("SapierApp", "Google Photos access token set from config")
        }
    }
    
    fun refreshGooglePhotosAccessToken() {
        val config = _uiState.value.config
        if (config.googlePhotosAccessToken.isNotEmpty()) {
            googlePhotosService?.setAccessToken(config.googlePhotosAccessToken)
            Log.d("SapierApp", "Google Photos access token refreshed from config")
        }
    }
    
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
                android.util.Log.d("SapierApp", "Face detection result: found ${result.size} faces in image $imageUri")
                
                val personDetection = PersonDetectionResult(
                    imageUri = imageUri,
                    timestamp = LocalDateTime.now(),
                    containsSon = containsSon,
                    confidence = if (containsSon) 0.8f else 0.0f
                )
                
                repository.addPersonDetection(personDetection)
                personDetection
            } catch (e: Exception) {
                android.util.Log.e("SapierApp", "Error processing person detection: ${e.message}", e)
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
            
            // If Google Photos is enabled and we have a token, set it in the service
            if (newConfig.useGooglePhotos && newConfig.googlePhotosAccessToken.isNotEmpty()) {
                googlePhotosService?.setAccessToken(newConfig.googlePhotosAccessToken)
                Log.d("SapierApp", "Google Photos access token updated from config")
            }
        }
    }
    
    fun validateGooglePhotosConfig(): Boolean {
        val config = _uiState.value.config
        return config.useGooglePhotos && 
               config.googlePhotosAccessToken.isNotEmpty() &&
               googlePhotosService?.isSignedIn == true
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
                // Initialize Google Photos Service if needed
                if (config.useGooglePhotos) {
                    initGooglePhotosService(context)
                }
                
                // Find all images in "Son" album
                val sonImages = withContext(Dispatchers.IO) {
                    if (config.useGooglePhotos && googlePhotosService?.isSignedIn == true) {
                        // Use Google Photos API
                        android.util.Log.d("SapierApp", "Using Google Photos API to find Son album images")
                        googlePhotosService?.findAndGetImagesFromAlbum("Son") ?: emptyList()
                    } else {
                        // Use device storage
                        android.util.Log.d("SapierApp", "Using device storage to find Son album images")
                        findImagesInSonAlbum(context, selectedDate)
                    }
                }
                
                if (sonImages.isEmpty()) {
                    val dateMsg = selectedDate?.let { " for ${it.toLocalDate()}" } ?: ""
                    val sourceMsg = if (config.useGooglePhotos && googlePhotosService?.isSignedIn == true) {
                        "Google Photos"
                    } else {
                        "device storage"
                    }
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Error("No images found in 'Son' album in $sourceMsg$dateMsg. Please ensure you have an album named 'Son' with photos.")
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
    
    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            try {
                val result = googlePhotosService?.handleSignInResult(data)
                if (result == true) {
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Success("Successfully signed in to Google Photos")
                    ) }
                    
                    // If auto-processing is enabled, trigger the appropriate functions
                    val config = _uiState.value.config
                    if (config.autoSendReceipts) {
                        // Auto-process receipts
                        _uiState.update { it.copy(processingStatus = ProcessingStatus.Processing) }
                        withContext(Dispatchers.Main) {
                            // Use a delay to ensure UI updates before processing starts
                            kotlinx.coroutines.delay(500)
                            // Removed call to processAllReceiptsFromGooglePhotos
                        }
                    }
                    
                    if (config.autoSendSonPhotos) {
                        // Auto-send Son photos
                        _uiState.update { it.copy(processingStatus = ProcessingStatus.Processing) }
                        withContext(Dispatchers.Main) {
                            // Use a delay to ensure UI updates before processing starts
                            kotlinx.coroutines.delay(500)
                            autoSendSonPhotosFromGooglePhotos()
                        }
                    }
                } else {
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Error("Failed to sign in to Google Photos")
                    ) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    processingStatus = ProcessingStatus.Error("Error signing in to Google Photos: ${e.message}")
                ) }
            }
        }
    }
    
    private fun autoSendSonPhotosFromGooglePhotos() {
        viewModelScope.launch {
            try {
                val config = _uiState.value.config
                val sonImages = googlePhotosService?.findAndGetImagesFromAlbum("Son") ?: emptyList()
                
                if (sonImages.isNotEmpty()) {
                    var successCount = 0
                    var failCount = 0
                    
                    // Send all Son images via Telegram
                    for (imageUri in sonImages.take(5)) { // Limit to 5 images for auto-processing
                        try {
                            val photoSent = telegramService.sendPhoto(
                                imageUri,
                                "📸 Auto-shared photo of ${config.sonName} from Google Photos",
                                config.telegramBotToken,
                                config.telegramChatId
                            )
                            
                            if (photoSent) successCount++ else failCount++
                            kotlinx.coroutines.delay(500) // Avoid rate limiting
                            
                        } catch (e: Exception) {
                            failCount++
                        }
                    }
                    
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Success(
                            "Auto-shared $successCount photos of ${config.sonName} from Google Photos"
                        )
                    ) }
                } else {
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Info("No photos found in Son album on Google Photos")
                    ) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    processingStatus = ProcessingStatus.Error("Error auto-processing photos: ${e.message}")
                ) }
            }
        }
    }

    // New automatic processing methods
    fun autoScanAllPhotosForReceipts(context: Context) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(processingStatus = ProcessingStatus.Processing) }
                
                // Initialize Google Photos Service if needed
                initGooglePhotosService(context)
                
                // Check if signed in and has valid token
                if (googlePhotosService?.isSignedIn != true) {
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Error("Please sign in to Google Photos first")
                    ) }
                    return@launch
                }
                
                if (!googlePhotosService?.hasValidToken()!!) {
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Error("Please set your Google Photos access token in the Config tab")
                    ) }
                    return@launch
                }
                
                // Get all recent photos from Google Photos
                val allPhotos = googlePhotosService?.getAllRecentPhotos(100) ?: emptyList()
                
                if (allPhotos.isEmpty()) {
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Info("No photos found in Google Photos")
                    ) }
                    return@launch
                }
                
                var processedCount = 0
                var receiptCount = 0
                
                // Process all photos to find receipts
                for (imageUri in allPhotos) {
                    try {
                        processedCount++
                        
                        // Load the image
                        val bitmap = ImageUtils.loadBitmapFromUri(imageUri, context)
                        if (bitmap != null) {
                            // Recognize text in the image
                            val recognizedText = recognizeTextInImage(bitmap)
                            
                            // Check if this looks like a receipt
                            if (isReceipt(recognizedText)) {
                                receiptCount++
                                
                                // Extract receipt information
                                val receiptInfo = extractReceiptInfo(recognizedText)
                                
                                // Send receipt summary via Telegram
                                val config = _uiState.value.config
                                telegramService.sendMessage(
                                    "📝 Receipt Summary:\n${receiptInfo}",
                                    config.telegramBotToken,
                                    config.telegramChatId
                                )
                                
                                kotlinx.coroutines.delay(500) // Avoid rate limiting
                            }
                        }
                        
                        // Update progress every 10 photos
                        if (processedCount % 10 == 0) {
                            _uiState.update { it.copy(
                                processingStatus = ProcessingStatus.Processing
                            ) }
                        }
                        
                    } catch (e: Exception) {
                        // Continue with next photo if one fails
                        android.util.Log.e("SapierApp", "Error processing photo: ${e.message}")
                    }
                }
                
                _uiState.update { it.copy(
                    processingStatus = ProcessingStatus.Success(
                        "Auto-scanned $processedCount photos, found $receiptCount receipts"
                    )
                ) }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    processingStatus = ProcessingStatus.Error("Error auto-scanning receipts: ${e.message}")
                ) }
            }
        }
    }
    
    fun autoSendAllSonPhotos(context: Context) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(processingStatus = ProcessingStatus.Processing) }
                
                // Initialize Google Photos Service if needed
                initGooglePhotosService(context)
                
                if (googlePhotosService?.isSignedIn != true) {
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Error("Please sign in to Google Photos first")
                    ) }
                    return@launch
                }
                
                // Find all images in "Son" album
                val sonImages = googlePhotosService?.findAndGetImagesFromAlbum("Son") ?: emptyList()
                
                if (sonImages.isEmpty()) {
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Error("No images found in 'Son' album in Google Photos. Please ensure you have an album named 'Son' with photos.")
                    ) }
                    return@launch
                }
                
                var successCount = 0
                var failCount = 0
                
                // Send all Son images via Telegram
                for (imageUri in sonImages) {
                    try {
                        val config = _uiState.value.config
                        val photoSent = telegramService.sendPhoto(
                            imageUri,
                            "📸 Auto-shared photo of ${config.sonName} from Google Photos Son album",
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
                
                val resultMessage = if (successCount > 0) {
                    "Successfully auto-sent $successCount photos of ${_uiState.value.config.sonName} from Son album" +
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
                    processingStatus = ProcessingStatus.Error("Error auto-sending Son photos: ${e.message}")
                ) }
            }
        }
    }
    
    fun processAllRecentPhotos(context: Context) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(processingStatus = ProcessingStatus.Processing) }
                
                // Initialize Google Photos Service if needed
                initGooglePhotosService(context)
                
                if (googlePhotosService?.isSignedIn != true) {
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Error("Please sign in to Google Photos first")
                    ) }
                    return@launch
                }
                
                // Get all recent photos from Google Photos
                val allPhotos = googlePhotosService?.getAllRecentPhotos(100) ?: emptyList()
                
                if (allPhotos.isEmpty()) {
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Info("No photos found in Google Photos")
                    ) }
                    return@launch
                }
                
                var processedCount = 0
                var receiptCount = 0
                var sonPhotoCount = 0
                
                // Process all photos for both receipts and son detection
                for (imageUri in allPhotos) {
                    try {
                        processedCount++
                        
                        // Load the image
                        val bitmap = ImageUtils.loadBitmapFromUri(imageUri, context)
                        if (bitmap != null) {
                            // Process for receipts
                            val recognizedText = recognizeTextInImage(bitmap)
                            if (isReceipt(recognizedText)) {
                                receiptCount++
                                
                                val receiptInfo = extractReceiptInfo(recognizedText)
                                val config = _uiState.value.config
                                telegramService.sendMessage(
                                    "📝 Receipt Summary:\n${receiptInfo}",
                                    config.telegramBotToken,
                                    config.telegramChatId
                                )
                            }
                            
                            // Process for son detection (face detection)
                            val image = InputImage.fromBitmap(bitmap, 0)
                            val personDetection = processPersonDetection(image, imageUri)
                            
                            if (personDetection?.containsSon == true) {
                                sonPhotoCount++
                                
                                // Send son photo to father via email
                                val config = _uiState.value.config
                                emailService.sendPhotoToFather(
                                    imageUri,
                                    config.sonName,
                                    config.emailUsername,
                                    config.emailPassword,
                                    config.fatherEmail
                                )
                                
                                // Also send via Telegram
                                telegramService.sendPhoto(
                                    imageUri,
                                    "👦 Auto-detected photo of ${config.sonName}",
                                    config.telegramBotToken,
                                    config.telegramChatId
                                )
                            }
                        }
                        
                        // Update progress every 10 photos
                        if (processedCount % 10 == 0) {
                            _uiState.update { it.copy(
                                processingStatus = ProcessingStatus.Processing
                            ) }
                        }
                        
                        // Small delay to avoid rate limiting
                        kotlinx.coroutines.delay(300)
                        
                    } catch (e: Exception) {
                        // Continue with next photo if one fails
                        android.util.Log.e("SapierApp", "Error processing photo: ${e.message}")
                    }
                }
                
                _uiState.update { it.copy(
                    processingStatus = ProcessingStatus.Success(
                        "Processed $processedCount photos: Found $receiptCount receipts and $sonPhotoCount son photos"
                    )
                ) }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    processingStatus = ProcessingStatus.Error("Error processing all photos: ${e.message}")
                ) }
            }
        }
    }
    
    private fun extractReceiptInfo(text: String): String {
        val lines = text.split("\n")
        val receiptInfo = StringBuilder()
        
        // Try to extract store name (usually first few lines)
        val storeName = lines.take(3).joinToString(" ").trim()
        receiptInfo.append("Store: $storeName\n")
        
        // Try to extract date
        val datePattern = "\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}"
        val dateRegex = Regex(datePattern)
        val dateMatch = dateRegex.find(text)
        if (dateMatch != null) {
            receiptInfo.append("Date: ${dateMatch.value}\n")
        }
        
        // Try to extract total amount
        val totalPattern = "(?i)total\\s*[:\\$]?\\s*(\\d+[.,]\\d{2})"
        val totalRegex = Regex(totalPattern)
        val totalMatch = totalRegex.find(text)
        if (totalMatch != null) {
            receiptInfo.append("Total: $${totalMatch.groupValues[1]}\n")
        }
        
        // Add some items if found
        val itemLines = lines.filter { line ->
            line.contains(Regex("\\d+[.,]\\d{2}")) && 
            !line.contains("total", ignoreCase = true) &&
            !line.contains("subtotal", ignoreCase = true) &&
            !line.contains("tax", ignoreCase = true)
        }.take(5) // Take up to 5 items
        
        if (itemLines.isNotEmpty()) {
            receiptInfo.append("\nItems:\n")
            itemLines.forEach { item ->
                receiptInfo.append("- $item\n")
            }
        }
        
        return receiptInfo.toString()
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
                // Query all images and filter by album name
                val projection = arrayOf(
                    android.provider.MediaStore.Images.Media._ID,
                    android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                    android.provider.MediaStore.Images.Media.DATE_ADDED,
                    android.provider.MediaStore.Images.Media.DATE_TAKEN,
                    android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    android.provider.MediaStore.Images.Media.DATA
                )
                
                val cursor = context.contentResolver.query(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"
                )
                
                cursor?.use {
                    val idColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                    val bucketNameColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val dataColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA)
                    val dateAddedColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATE_ADDED)
                    
                    while (it.moveToNext()) {
                        val bucketName = it.getString(bucketNameColumn)
                        val filePath = it.getString(dataColumn)
                        val dateAdded = it.getLong(dateAddedColumn)
                        
                        // Check if image is in "Son" album by bucket name or file path
                        val isInSonAlbum = bucketName?.equals("Son", ignoreCase = true) == true ||
                                          filePath?.contains("/Son/", ignoreCase = true) == true ||
                                          filePath?.contains("/son/", ignoreCase = true) == true ||
                                          filePath?.contains("\\Son\\", ignoreCase = true) == true ||
                                          filePath?.contains("\\son\\", ignoreCase = true) == true ||
                                          filePath?.contains("Pictures/Son", ignoreCase = true) == true ||
                                          filePath?.contains("Pictures\\Son", ignoreCase = true) == true
                        
                        // Log all image paths for debugging
                        android.util.Log.d("SapierApp", "Checking image: $filePath, bucket: $bucketName, isInSonAlbum: $isInSonAlbum")
                        
                        if (isInSonAlbum) {
                            // Apply date filter if specified
                            if (selectedDate != null) {
                                val startOfDay = selectedDate.toLocalDate().atStartOfDay()
                                val endOfDay = selectedDate.toLocalDate().plusDays(1).atStartOfDay().minusNanos(1)
                                val startEpoch = startOfDay.toEpochSecond(java.time.ZoneOffset.UTC)
                                val endEpoch = endOfDay.toEpochSecond(java.time.ZoneOffset.UTC)
                                
                                if (dateAdded < startEpoch || dateAdded > endEpoch) {
                                    continue
                                }
                            }
                            
                            val id = it.getLong(idColumn)
                            val contentUri = android.content.ContentUris.withAppendedId(
                                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                            sonImages.add(contentUri)
                        }
                    }
                }
                
                if (sonImages.isEmpty()) {
                    android.util.Log.e("SapierApp", "No images found in 'Son' album. Please ensure you have an album named 'Son' with photos.")
                } else {
                    android.util.Log.d("SapierApp", "Found ${sonImages.size} images in Son album")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("SapierApp", "Error scanning Son album: ${e.message}", e)
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
    
    private suspend fun recognizeTextInImage(bitmap: Bitmap): String {
        return withContext(Dispatchers.IO) {
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val task = textRecognizer.process(image)
                val result = task.await()
                result.text
            } catch (e: Exception) {
                android.util.Log.e("SapierApp", "Error recognizing text: ${e.message}")
                ""
            }
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

    fun testGooglePhotosConnection(context: Context) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(processingStatus = ProcessingStatus.Processing) }
                
                // Initialize Google Photos Service if needed
                initGooglePhotosService(context)
                
                // Check if signed in to Google
                if (googlePhotosService?.isSignedIn != true) {
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Error(
                            "Google Photos not connected. Please:\n" +
                            "1. Click 'Sign In to Google Photos' first\n" +
                            "2. Set your access token in Config tab\n" +
                            "3. Try testing connection again"
                        )
                    ) }
                    return@launch
                }
                
                // Check if access token is set
                val config = _uiState.value.config
                if (config.googlePhotosAccessToken.isEmpty()) {
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Error(
                            "Access token not set. Please:\n" +
                            "1. Go to Config tab\n" +
                            "2. Check 'Use Google Photos'\n" +
                            "3. Enter your Google Photos access token\n" +
                            "4. Try testing connection again"
                        )
                    ) }
                    return@launch
                }
                
                // Test the connection by making a simple API call
                val connectionTest = withContext(Dispatchers.IO) {
                    try {
                        val client = okhttp3.OkHttpClient()
                        val request = okhttp3.Request.Builder()
                            .url("https://photoslibrary.googleapis.com/v1/albums?pageSize=1")
                            .addHeader("Authorization", "Bearer ${config.googlePhotosAccessToken}")
                            .build()
                        
                        val response = client.newCall(request).execute()
                        response.isSuccessful
                    } catch (e: Exception) {
                        Log.e("SapierApp", "Connection test failed: ${e.message}")
                        false
                    }
                }
                
                if (connectionTest) {
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Success(
                            "Google Photos connection successful! ✅\n" +
                            "Account: ${GoogleSignIn.getLastSignedInAccount(context)?.email ?: "Unknown"}\n" +
                            "You can now use the automatic features."
                        )
                    ) }
                } else {
                    _uiState.update { it.copy(
                        processingStatus = ProcessingStatus.Error(
                            "Google Photos connection failed. Please check:\n" +
                            "1. Your access token is correct\n" +
                            "2. You have the necessary permissions\n" +
                            "3. The Google Photos API is enabled\n" +
                            "4. Your token hasn't expired"
                        )
                    ) }
                }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    processingStatus = ProcessingStatus.Error("Error testing Google Photos: ${e.message}")
                ) }
            }
        }
    }
}

