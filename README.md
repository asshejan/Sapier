# Sapier - Smart Photo Analysis & Receipt Management

Sapier is an Android application that automatically analyzes photos to detect receipts and people, then sends relevant information to configured destinations like Telegram and email.

## 🚀 New Automatic Features

### 🤖 Automatic Google Photos Processing

Sapier now supports **direct access to Google Photos** with automatic processing capabilities:

#### 1. **Auto-Scan All Photos for Receipts**
- Automatically scans all photos in your Google Photos library
- Uses AI to detect receipt images
- Extracts receipt information (store, total, items, date)
- Sends receipt summaries directly to Telegram
- No manual photo selection required!

#### 2. **Auto-Send All Son Album Photos**
- Automatically finds all photos in your "Son" album on Google Photos
- Sends all photos directly to configured destinations
- Perfect for keeping family updated with son's activities
- Works with any album named "Son"

#### 3. **Process All Recent Photos (Complete Analysis)**
- Comprehensive processing of all recent photos
- Detects both receipts AND son photos in one operation
- Sends receipts to Telegram
- Sends son photos to both email and Telegram
- Most efficient way to process large photo collections

## 🔧 Setup Instructions

### 1. **Google Photos Integration**
1. Open the app and go to the **Config** tab
2. Check "Use Google Photos"
3. Click "Sign In to Google Photos" in the **Process** tab
4. Grant necessary permissions to access your photos

### 2. **Configuration**
Fill in the required fields in the **Config** tab:

- **Telegram Settings**: Bot token and chat ID
- **Email Settings**: Username, password, and father's email
- **Family Settings**: Son's name
- **Google Photos Options**:
  - ✅ Auto-process receipts from Google Photos
  - ✅ Auto-send son photos

### 3. **Automatic Processing**
Once configured, you can use the new automatic buttons:

- 🔍 **Auto-Scan All Photos for Receipts** - Processes all photos to find receipts
- 👦 **Auto-Send All Son Album Photos** - Sends all photos from Son album
- 📸 **Process All Recent Photos** - Complete analysis of all photos

## 📱 How It Works

### Receipt Detection
- Uses Google ML Kit for text recognition
- Analyzes photo content for receipt patterns
- Extracts store name, total amount, items, and date
- Sends formatted summaries to Telegram

### Son Photo Detection
- Uses ML Kit face detection
- Automatically identifies photos containing people
- Sends photos to father via email
- Also shares via Telegram for immediate access

### Google Photos Integration
- Direct API access to your Google Photos library
- No need to download or manually select photos
- Processes photos in batches to avoid rate limiting
- Maintains privacy and security

## 🎯 Use Cases

### For Receipt Management
- **Business Expense Tracking**: Automatically capture all receipt photos
- **Personal Finance**: Keep track of daily spending
- **Tax Preparation**: Organized receipt collection throughout the year

### For Family Photo Sharing
- **Parental Updates**: Automatic sharing of son's activities
- **Family Communication**: Keep everyone in the loop
- **Memory Preservation**: Organized photo sharing without manual effort

## 🔒 Privacy & Security

- **Local Processing**: Photo analysis happens on your device
- **Secure API**: Uses official Google Photos API with OAuth2
- **No Data Storage**: Photos are processed but not stored by the app
- **User Control**: You control what gets shared and where

## 📋 Requirements

- Android 7.0+ (API level 24)
- Google account with Google Photos
- Internet connection for API calls
- Telegram bot (for notifications)
- Email account (for photo sharing)

## 🚀 Getting Started

1. **Install the app** from your preferred source
2. **Configure settings** in the Config tab
3. **Sign in to Google Photos** using the Process tab
4. **Enable automatic features** in configuration
5. **Start processing** with the automatic buttons

## 💡 Tips for Best Results

- **Album Naming**: Ensure your "Son" album is exactly named "Son" in Google Photos
- **Photo Quality**: Higher resolution photos work better for receipt detection
- **Regular Processing**: Run automatic scans regularly for best results
- **Network**: Ensure stable internet connection for API calls

## �� Automatic Workflow

1. **Sign in to Google Photos** once
2. **Configure your destinations** (Telegram, email)
3. **Click automatic processing buttons** as needed
4. **Receive results** automatically via configured channels

No more manual photo selection or processing - Sapier handles everything automatically! 🎉

## 📞 Support

For issues or questions:
- Check the app's status messages
- Ensure all configuration fields are filled
- Verify Google Photos permissions
- Check internet connectivity

---

**Sapier** - Making photo management intelligent and automatic! 📸✨
