package com.example.sapier.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sapier.data.*
import com.example.sapier.service.EmailService
import com.example.sapier.service.ImageProcessingService
import com.example.sapier.service.TelegramService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class MainViewModel(
    private val repository: Repository,
    private val telegramService: TelegramService,
    private val emailService: EmailService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
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
                // Start background processing service
                ImageProcessingService.startService(context, imageUri)
                
                // For demo purposes, simulate processing
                // In a real app, the service would communicate back with results
                kotlinx.coroutines.delay(2000)
                
                // Simulate receipt detection
                val mockReceipt = Receipt(
                    id = java.util.UUID.randomUUID().toString(),
                    imageUri = imageUri,
                    timestamp = LocalDateTime.now(),
                    items = listOf(ReceiptItem("Sample Item", 1, 15.99)),
                    total = 15.99,
                    store = "Sample Store",
                    date = LocalDateTime.now().toString()
                )
                
                repository.addReceipt(mockReceipt)
                
                // Simulate person detection
                val mockPersonDetection = PersonDetectionResult(
                    imageUri = imageUri,
                    timestamp = LocalDateTime.now(),
                    containsSon = true,
                    confidence = 0.85f
                )
                
                repository.addPersonDetection(mockPersonDetection)
                
                // Update UI
                _uiState.update { it.copy(
                    receipts = repository.getReceipts(),
                    personDetections = repository.getPersonDetections(),
                    processingStatus = ProcessingStatus.Success("Image processed successfully")
                ) }
                
                // Send to appropriate destinations
                sendToDestinations(mockReceipt, mockPersonDetection)
                
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    processingStatus = ProcessingStatus.Error("Error processing image: ${e.message}")
                ) }
            }
        }
    }
    
    private suspend fun sendToDestinations(receipt: Receipt, personDetection: PersonDetectionResult) {
        val config = _uiState.value.config
        
        // Send receipt summary to Telegram if it's a receipt
        if (receipt.items.isNotEmpty()) {
            val receiptsForToday = repository.getReceiptsForDate(LocalDateTime.now())
            telegramService.sendReceiptSummary(
                receiptsForToday,
                config.telegramBotToken,
                config.telegramChatId
            )
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
}
