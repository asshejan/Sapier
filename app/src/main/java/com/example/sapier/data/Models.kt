package com.example.sapier.data

import android.net.Uri
import java.time.LocalDateTime

// Receipt data model
data class Receipt(
    val id: String,
    val imageUri: Uri,
    val timestamp: LocalDateTime,
    val items: List<ReceiptItem>,
    val total: Double,
    val store: String?,
    val date: String?
)

data class ReceiptItem(
    val name: String,
    val quantity: Int,
    val price: Double
)

// Person detection result
data class PersonDetectionResult(
    val imageUri: Uri,
    val timestamp: LocalDateTime,
    val containsSon: Boolean,
    val confidence: Float
)

// App configuration
data class AppConfig(
    val telegramBotToken: String = "",
    val telegramChatId: String = "",
    val openaiApiKey: String = "",
    val emailUsername: String = "",
    val emailPassword: String = "",
    val fatherEmail: String = "",
    val sonName: String = ""
)

// Processing status
sealed class ProcessingStatus {
    object Idle : ProcessingStatus()
    object Processing : ProcessingStatus()
    data class Success(val message: String) : ProcessingStatus()
    data class Error(val message: String) : ProcessingStatus()
}

// UI state
data class MainUiState(
    val receipts: List<Receipt> = emptyList(),
    val personDetections: List<PersonDetectionResult> = emptyList(),
    val processingStatus: ProcessingStatus = ProcessingStatus.Idle,
    val config: AppConfig = AppConfig(),
    val isConfigValid: Boolean = false
)
