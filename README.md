# Sapier - Smart Image Processing Android App

Sapier is an Android application that automatically processes images to:
1. Extract and analyze receipt information for daily purchase summaries
2. Detect when your son appears in photos and automatically share them with your father

## Features

### 📊 Receipt Processing
- **Automatic Text Recognition**: Uses ML Kit to extract text from receipt images
- **LLM Analysis**: Leverages OpenAI API to parse and structure receipt data
- **Daily Summaries**: Generates comprehensive daily purchase summaries
- **Telegram Integration**: Sends formatted summaries directly to your Telegram

### 👨‍👦 Person Detection & Sharing
- **Face Detection**: Uses ML Kit face detection to identify people in photos
- **Automatic Sharing**: When your son is detected, photos are automatically sent to your father
- **Multiple Channels**: Supports both email and Telegram for photo sharing
- **Privacy Focused**: Only processes images when explicitly requested

### 🔧 Configuration Management
- **Secure Storage**: All API keys and credentials stored securely using DataStore
- **Easy Setup**: Simple configuration interface for all required services
- **Validation**: Real-time validation of configuration completeness

## Setup Instructions

### Prerequisites
- Android Studio Arctic Fox or later
- Android device/emulator running API level 24 or higher
- Active internet connection

### 1. Telegram Bot Setup
1. Create a new bot using [@BotFather](https://t.me/botfather) on Telegram
2. Get your bot token
3. Start a chat with your bot and get your chat ID
4. Add these to the app configuration

### 2. OpenAI API Setup
1. Sign up for OpenAI API access at [platform.openai.com](https://platform.openai.com)
2. Generate an API key
3. Add the API key to the app configuration

### 3. Email Setup (for Gmail)
1. Enable 2-factor authentication on your Gmail account
2. Generate an App Password (not your regular password)
3. Use your Gmail address and the app password in the configuration

### 4. App Configuration
1. Open the app and go to the "Config" tab
2. Fill in all required fields:
   - **Telegram Bot Token**: Your bot token from BotFather
   - **Telegram Chat ID**: Your chat ID with the bot
   - **OpenAI API Key**: Your OpenAI API key
   - **Email Username**: Your Gmail address
   - **Email Password**: Your Gmail app password
   - **Father's Email**: Your father's email address
   - **Son's Name**: Your son's name for photo captions

## Usage

### Processing Images
1. Ensure all configuration is complete (green "Configuration Valid" status)
2. Go to the "Process" tab
3. Tap "Process Image from Gallery"
4. Select an image from your gallery
5. The app will automatically:
   - Analyze the image for receipt data
   - Check for person detection
   - Send appropriate summaries/photos to configured destinations

### Viewing Results
- **Results Tab**: View processed receipts and detection results
- **Daily Summary**: Get a comprehensive summary of today's purchases
- **Clear Data**: Reset all stored data if needed

## Technical Architecture

### Core Components
- **Repository**: Manages data persistence and configuration
- **ImageProcessingService**: Background service for image analysis
- **TelegramService**: Handles Telegram bot communication
- **EmailService**: Manages email sending functionality
- **MainViewModel**: Coordinates between UI and business logic

### Technologies Used
- **Jetpack Compose**: Modern Android UI framework
- **ML Kit**: Google's ML services for text and face detection
- **OpenAI API**: LLM for receipt analysis
- **Telegram Bot API**: For sending messages and photos
- **JavaMail**: For email functionality
- **DataStore**: Secure configuration storage
- **Coroutines**: Asynchronous programming
- **ViewModel**: UI state management

## Security Considerations

- All API keys are stored securely using Android DataStore
- Images are processed locally when possible
- No data is stored on external servers (except for API calls)
- Email passwords use app-specific passwords, not account passwords

## Permissions Required

- **Camera**: For taking photos (optional)
- **Storage**: For accessing gallery images
- **Internet**: For API calls to OpenAI, Telegram, and email services
- **Foreground Service**: For background image processing

## Troubleshooting

### Common Issues

1. **Configuration Not Valid**
   - Ensure all fields are filled in the Config tab
   - Check that API keys are correct and active

2. **Telegram Messages Not Sending**
   - Verify bot token is correct
   - Ensure you've started a chat with your bot
   - Check that chat ID is correct

3. **Email Not Sending**
   - Use app password, not regular Gmail password
   - Enable 2-factor authentication on Gmail
   - Check internet connection

4. **Image Processing Fails**
   - Ensure image is clear and well-lit
   - Check that image contains readable text (for receipts)
   - Verify all permissions are granted

### Getting Help

If you encounter issues:
1. Check the app's status messages
2. Verify all configuration is correct
3. Ensure all permissions are granted
4. Check your internet connection

## Development

### Building from Source
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Build and run on device/emulator

### Customization
- Modify `ImageProcessingService.kt` for custom image processing logic
- Update `TelegramService.kt` for different message formats
- Customize UI in `MainScreen.kt`

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
