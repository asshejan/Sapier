package com.example.sapier.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.util.*

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sapier_config")

class Repository(private val context: Context) {
    
    private val receipts = mutableListOf<Receipt>()
    private val personDetections = mutableListOf<PersonDetectionResult>()
    
    // Configuration keys
    private object PreferencesKeys {
        val TELEGRAM_BOT_TOKEN = stringPreferencesKey("telegram_bot_token")
        val TELEGRAM_CHAT_ID = stringPreferencesKey("telegram_chat_id")
        val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        val EMAIL_USERNAME = stringPreferencesKey("email_username")
        val EMAIL_PASSWORD = stringPreferencesKey("email_password")
        val FATHER_EMAIL = stringPreferencesKey("father_email")
        val SON_NAME = stringPreferencesKey("son_name")
        val USE_GOOGLE_PHOTOS = booleanPreferencesKey("use_google_photos")
        val GOOGLE_PHOTOS_ACCESS_TOKEN = stringPreferencesKey("google_photos_access_token")
        val AUTO_SEND_RECEIPTS = booleanPreferencesKey("auto_send_receipts")
        val AUTO_SEND_SON_PHOTOS = booleanPreferencesKey("auto_send_son_photos")
    }
    
    // Receipt operations
    suspend fun addReceipt(receipt: Receipt) {
        receipts.add(receipt)
    }
    
    fun getReceipts(): List<Receipt> = receipts.toList()
    
    fun getReceiptsForDate(date: LocalDateTime): List<Receipt> {
        return receipts.filter { 
            it.timestamp.toLocalDate() == date.toLocalDate() 
        }
    }
    
    // Person detection operations
    suspend fun addPersonDetection(result: PersonDetectionResult) {
        personDetections.add(result)
    }
    
    fun getPersonDetections(): List<PersonDetectionResult> = personDetections.toList()
    
    fun getPersonDetectionsForDate(date: LocalDateTime): List<PersonDetectionResult> {
        return personDetections.filter { 
            it.timestamp.toLocalDate() == date.toLocalDate() 
        }
    }
    
    // Configuration operations
    val config: Flow<AppConfig> = context.dataStore.data.map { preferences ->
        AppConfig(
            telegramBotToken = preferences[PreferencesKeys.TELEGRAM_BOT_TOKEN] ?: "",
            telegramChatId = preferences[PreferencesKeys.TELEGRAM_CHAT_ID] ?: "",
            openaiApiKey = preferences[PreferencesKeys.OPENAI_API_KEY] ?: "",
            emailUsername = preferences[PreferencesKeys.EMAIL_USERNAME] ?: "",
            emailPassword = preferences[PreferencesKeys.EMAIL_PASSWORD] ?: "",
            fatherEmail = preferences[PreferencesKeys.FATHER_EMAIL] ?: "",
            sonName = preferences[PreferencesKeys.SON_NAME] ?: "",
            useGooglePhotos = preferences[PreferencesKeys.USE_GOOGLE_PHOTOS] ?: false,
            googlePhotosAccessToken = preferences[PreferencesKeys.GOOGLE_PHOTOS_ACCESS_TOKEN] ?: "",
            autoSendReceipts = preferences[PreferencesKeys.AUTO_SEND_RECEIPTS] ?: false,
            autoSendSonPhotos = preferences[PreferencesKeys.AUTO_SEND_SON_PHOTOS] ?: false
        )
    }
    
    suspend fun updateConfig(config: AppConfig) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TELEGRAM_BOT_TOKEN] = config.telegramBotToken
            preferences[PreferencesKeys.TELEGRAM_CHAT_ID] = config.telegramChatId
            preferences[PreferencesKeys.OPENAI_API_KEY] = config.openaiApiKey
            preferences[PreferencesKeys.EMAIL_USERNAME] = config.emailUsername
            preferences[PreferencesKeys.EMAIL_PASSWORD] = config.emailPassword
            preferences[PreferencesKeys.FATHER_EMAIL] = config.fatherEmail
            preferences[PreferencesKeys.SON_NAME] = config.sonName
            preferences[PreferencesKeys.USE_GOOGLE_PHOTOS] = config.useGooglePhotos
            preferences[PreferencesKeys.GOOGLE_PHOTOS_ACCESS_TOKEN] = config.googlePhotosAccessToken
            preferences[PreferencesKeys.AUTO_SEND_RECEIPTS] = config.autoSendReceipts
            preferences[PreferencesKeys.AUTO_SEND_SON_PHOTOS] = config.autoSendSonPhotos
        }
    }
    
    fun isConfigValid(config: AppConfig): Boolean {
        // Basic validation - at least Telegram bot token and chat ID are required
        val hasBasicConfig = config.telegramBotToken.isNotBlank() && config.telegramChatId.isNotBlank()
        
        // For Google Photos features, we need at least basic config
        if (config.useGooglePhotos) {
            return hasBasicConfig && config.googlePhotosAccessToken.isNotBlank()
        }
        
        // For other features, require all config fields
        return hasBasicConfig &&
               config.openaiApiKey.isNotBlank() &&
               config.emailUsername.isNotBlank() &&
               config.emailPassword.isNotBlank() &&
               config.fatherEmail.isNotBlank() &&
               config.sonName.isNotBlank()
    }
    
    // Clear data
    fun clearAllData() {
        receipts.clear()
        personDetections.clear()
    }
}
