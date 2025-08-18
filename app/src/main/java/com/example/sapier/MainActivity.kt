package com.example.sapier
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.sapier.data.AppConfig
import com.example.sapier.data.Repository
import com.example.sapier.service.EmailService
import com.example.sapier.service.GooglePhotosService
import com.example.sapier.service.TelegramService
import com.example.sapier.ui.MainScreen
import com.example.sapier.ui.MainViewModel
import com.example.sapier.ui.theme.SapierTheme

class MainActivity : ComponentActivity() {
    private lateinit var repository: Repository
    private lateinit var telegramService: TelegramService
    private lateinit var emailService: EmailService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize services
        repository = Repository(this)
        telegramService = TelegramService(this)
        emailService = EmailService(this)
        
        // Create ViewModel with custom factory
        val viewModel: MainViewModel by viewModels {
            object : ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(repository, telegramService, emailService) as T
                }
            }
        }
        
        setContent {
            SapierTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    
                    // Image picker launcher for single image
                    val imagePickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        uri?.let { imageUri ->
                            // Process image in background
                            viewModel.processImage(imageUri, this@MainActivity)
                        }
                    }
                    
                    // Multiple images picker launcher
                    val multipleImagesPickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetMultipleContents()
                    ) { uris: List<Uri>? ->
                        uris?.let { imageUris ->
                            // Process multiple images
                            viewModel.processMultipleImages(imageUris, this@MainActivity)
                        }
                    }
                    
                    // Google Sign-In launcher
                    val googleSignInLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == RESULT_OK) {
                            val data = result.data
                            viewModel.handleGoogleSignInResult(data)
                        } else {
                            // Handle sign-in failure
                            viewModel.updateProcessingStatus("Google Sign-In failed or was cancelled")
                        }
                    }
                    
                    MainScreen(
                        uiState = uiState,
                        onProcessImage = {
                            // Launch image picker directly
                            imagePickerLauncher.launch("image/*")
                        },
                        onProcessMultipleImages = {
                            // Launch multiple images picker
                            multipleImagesPickerLauncher.launch("image/*")
                        },
                        onTestTelegram = {
                            // Test Telegram connection
                            viewModel.testTelegramConnection()
                        },
                        onSendSonPhoto = {
                            // Send Son's photos from album
                            viewModel.sendSonPhoto(this@MainActivity)
                        },
                        onSendSaraPhoto = {
                            // Send Sara's photos from album
                            viewModel.sendSaraPhoto(this@MainActivity)
                        },
                        onSignInToGooglePhotos = {
                            // Sign in to Google Photos using the launcher
                            viewModel.signInToGooglePhotos(this@MainActivity, googleSignInLauncher)
                        },
                        onUpdateConfig = { config ->
                            viewModel.updateConfig(config)
                        },
                        onClearStatus = {
                            viewModel.clearProcessingStatus()
                        },
                        onGetSummary = {
                            // Show summary in a dialog or toast
                            val summary = viewModel.getDailySummary()
                            // You could show this in a dialog or snackbar
                        },
                        onClearData = {
                            viewModel.clearAllData()
                        },
                        onAutoScanReceipts = {
                            // Auto-scan all photos for receipts
                            viewModel.autoScanAllPhotosForReceipts(this@MainActivity)
                        },
                        onAutoSendSonPhotos = {
                            // Auto-send all Son album photos
                            viewModel.autoSendAllSonPhotos(this@MainActivity)
                        },
                        onProcessAllRecentPhotos = {
                            // Process all recent photos for receipts and son detection
                            viewModel.processAllRecentPhotos(this@MainActivity)
                        },
                        onTestGooglePhotosConnection = {
                            // Test Google Photos connection
                            viewModel.testGooglePhotosConnection(this@MainActivity)
                        }
                    )
                }
            }
        }
    }
}
