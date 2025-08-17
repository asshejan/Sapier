# Sapier - Smart Image Processing App

Sapier is an Android application that processes images to detect receipts and people, then automatically sends the results to appropriate destinations.

## Features

### Image Processing
- **Receipt Detection**: Uses ML Kit Text Recognition to detect and parse receipt information
- **Person Detection**: Uses ML Kit Face Detection to identify people in photos
- **Background Processing**: All image processing runs in the background without blocking the UI

### Automatic Actions
- **Receipt Summary**: Sends daily receipt summaries to Telegram
- **Photo Sharing**: Automatically sends photos of detected people to father via email and Telegram

## How It Works

1. **Configuration**: Set up your Telegram bot token, chat ID, email credentials, and son's name in the Config tab
2. **Image Processing**: 
   - **Single Image**: Click "Process Single Image" to analyze one image
   - **Multiple Images**: Click "Process Multiple Images (Find All Receipts)" to scan through all your photos and find receipts
3. **Results**: View processed receipts and person detections in the Results tab

## Enhanced Features

### Improved Receipt Detection
- **Comprehensive Keyword Matching**: Detects receipts using 30+ keywords and patterns
- **Smart Pattern Recognition**: Identifies receipts even with minimal text
- **Multiple Format Support**: Works with JPG, PNG, WebP, HEIC, and other image formats
- **Robust Parsing**: Extracts store names, totals, dates, and individual items

### Batch Processing
- **Multiple Image Selection**: Process all your photos at once to find receipts
- **Error Handling**: Continues processing even if some images fail
- **Progress Tracking**: Shows processing status and results summary

## Technical Details

### Fixed Issues
- **UI Freezing**: Resolved by moving all image processing to background threads using coroutines
- **Memory Issues**: Added image resizing to prevent out-of-memory errors
- **Dependency Conflicts**: Resolved by using compatible ML Kit and HTTP client libraries

### Architecture
- **MVVM Pattern**: Uses ViewModel and StateFlow for reactive UI updates
- **Coroutines**: Background processing with proper error handling
- **ML Kit Integration**: Text recognition and face detection for image analysis
- **Repository Pattern**: Clean data management with local storage

### Dependencies
- ML Kit for text recognition and face detection
- OkHttp for API calls
- Jetpack Compose for modern UI
- Coroutines for asynchronous operations

## Setup Instructions

1. Configure the app settings in the Config tab:
   - Telegram Bot Token
   - Telegram Chat ID
   - Email credentials
   - Son's name
   - Father's email

2. Ensure all required fields are filled (indicated by green "Configuration Valid" status)

3. Use the Process tab to select and analyze images

## Troubleshooting

- **App stops responding**: This issue has been fixed by implementing proper background processing
- **Memory errors**: Images are automatically resized to prevent memory issues
- **Processing fails**: Check that your configuration is valid and you have internet connectivity

## Development

The app is built with:
- Kotlin
- Jetpack Compose
- ML Kit
- Coroutines
- Modern Android development practices
