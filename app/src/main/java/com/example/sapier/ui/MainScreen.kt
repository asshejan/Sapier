package com.example.sapier.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sapier.data.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onProcessImage: () -> Unit,
    onUpdateConfig: (com.example.sapier.data.AppConfig) -> Unit,
    onClearStatus: () -> Unit,
    onGetSummary: () -> Unit,
    onClearData: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sapier") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Process") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Config") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Results") }
                )
            }
            
            // Tab content
            when (selectedTab) {
                0 -> ProcessTab(
                    uiState = uiState,
                    onProcessImage = onProcessImage,
                    onClearStatus = onClearStatus
                )
                1 -> ConfigTab(
                    config = uiState.config,
                    isConfigValid = uiState.isConfigValid,
                    onUpdateConfig = onUpdateConfig
                )
                2 -> ResultsTab(
                    receipts = uiState.receipts,
                    personDetections = uiState.personDetections,
                    onGetSummary = onGetSummary,
                    onClearData = onClearData
                )
            }
        }
    }
}

@Composable
fun ProcessTab(
    uiState: MainUiState,
    onProcessImage: () -> Unit,
    onClearStatus: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Status display
        when (val status = uiState.processingStatus) {
            is com.example.sapier.data.ProcessingStatus.Idle -> {
                Text(
                    text = "Ready to process images",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            is com.example.sapier.data.ProcessingStatus.Processing -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Processing image...")
            }
            is com.example.sapier.data.ProcessingStatus.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Success!",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(status.message)
                    }
                }
            }
            is com.example.sapier.data.ProcessingStatus.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(status.message)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Process button
        Button(
            onClick = onProcessImage,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.isConfigValid && uiState.processingStatus is com.example.sapier.data.ProcessingStatus.Idle
        ) {
            Text("Process Image from Gallery")
        }
        
        if (uiState.processingStatus !is com.example.sapier.data.ProcessingStatus.Idle) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onClearStatus,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Status")
            }
        }
        
        if (!uiState.isConfigValid) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Configuration Required",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text("Please configure the app settings in the Config tab before processing images.")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigTab(
    config: com.example.sapier.data.AppConfig,
    isConfigValid: Boolean,
    onUpdateConfig: (com.example.sapier.data.AppConfig) -> Unit
) {
    var localConfig by remember { mutableStateOf(config) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Configuration",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Telegram Configuration
        Text(
            text = "Telegram Bot",
            style = MaterialTheme.typography.titleMedium
        )
        
        OutlinedTextField(
            value = localConfig.telegramBotToken,
            onValueChange = { 
                localConfig = localConfig.copy(telegramBotToken = it)
                onUpdateConfig(localConfig)
            },
            label = { Text("Bot Token") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = localConfig.telegramChatId,
            onValueChange = { 
                localConfig = localConfig.copy(telegramChatId = it)
                onUpdateConfig(localConfig)
            },
            label = { Text("Chat ID") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // OpenAI Configuration
        Text(
            text = "OpenAI API",
            style = MaterialTheme.typography.titleMedium
        )
        
        OutlinedTextField(
            value = localConfig.openaiApiKey,
            onValueChange = { 
                localConfig = localConfig.copy(openaiApiKey = it)
                onUpdateConfig(localConfig)
            },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Email Configuration
        Text(
            text = "Email Settings",
            style = MaterialTheme.typography.titleMedium
        )
        
        OutlinedTextField(
            value = localConfig.emailUsername,
            onValueChange = { 
                localConfig = localConfig.copy(emailUsername = it)
                onUpdateConfig(localConfig)
            },
            label = { Text("Email Username") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = localConfig.emailPassword,
            onValueChange = { 
                localConfig = localConfig.copy(emailPassword = it)
                onUpdateConfig(localConfig)
            },
            label = { Text("Email Password") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = localConfig.fatherEmail,
            onValueChange = { 
                localConfig = localConfig.copy(fatherEmail = it)
                onUpdateConfig(localConfig)
            },
            label = { Text("Father's Email") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Son's Name
        Text(
            text = "Family Settings",
            style = MaterialTheme.typography.titleMedium
        )
        
        OutlinedTextField(
            value = localConfig.sonName,
            onValueChange = { 
                localConfig = localConfig.copy(sonName = it)
                onUpdateConfig(localConfig)
            },
            label = { Text("Son's Name") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Configuration status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isConfigValid) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isConfigValid) "Configuration Valid" else "Configuration Incomplete",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (isConfigValid) 
                        "All required fields are filled. You can now process images." 
                    else 
                        "Please fill in all required fields to enable image processing."
                )
            }
        }
    }
}

@Composable
fun ResultsTab(
    receipts: List<com.example.sapier.data.Receipt>,
    personDetections: List<com.example.sapier.data.PersonDetectionResult>,
    onGetSummary: () -> Unit,
    onClearData: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Results",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Summary section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Summary",
                    style = MaterialTheme.typography.titleMedium
                )
                Text("Total Receipts: ${receipts.size}")
                Text("Total Person Detections: ${personDetections.size}")
                Text("Son Detections: ${personDetections.count { it.containsSon }}")
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(onClick = onGetSummary) {
                    Text("Get Daily Summary")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Receipts section
        if (receipts.isNotEmpty()) {
            Text(
                text = "Recent Receipts",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            receipts.take(5).forEach { receipt ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = receipt.store ?: "Unknown Store",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text("Total: $${String.format("%.2f", receipt.total)}")
                        Text("Items: ${receipt.items.size}")
                        Text("Date: ${receipt.timestamp}")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Clear data button
        OutlinedButton(
            onClick = onClearData,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Clear All Data")
        }
    }
}
