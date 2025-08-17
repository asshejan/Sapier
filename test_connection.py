#!/usr/bin/env python3
"""
Test Telegram Connection - Sapier Edition
Quick test to verify Telegram bot is working in Sapier directory.
"""

import os
import requests
from dotenv import load_dotenv

def test_telegram_connection():
    """Test Telegram bot connection"""
    print("🔗 Testing Telegram Connection in Sapier...")
    print("=" * 50)
    
    # Load environment variables from current directory
    load_dotenv()
    
    bot_token = os.getenv('TELEGRAM_BOT_TOKEN')
    chat_id = os.getenv('TELEGRAM_ADMIN_USER_ID')
    
    if not bot_token:
        print("❌ TELEGRAM_BOT_TOKEN not found in .env file")
        return False
    
    if not chat_id:
        print("❌ TELEGRAM_ADMIN_USER_ID not found in .env file")
        return False
    
    print(f"✅ Bot Token: {bot_token[:10]}...{bot_token[-10:]}")
    print(f"✅ Chat ID: {chat_id}")
    
    # Test bot info
    try:
        url = f"https://api.telegram.org/bot{bot_token}/getMe"
        response = requests.get(url, timeout=10)
        
        if response.status_code == 200:
            bot_info = response.json()
            if bot_info.get('ok'):
                bot_name = bot_info['result']['username']
                print(f"✅ Bot connected: @{bot_name}")
            else:
                print(f"❌ Bot API error: {bot_info}")
                return False
        else:
            print(f"❌ HTTP Error: {response.status_code}")
            return False
    
    except Exception as e:
        print(f"❌ Connection error: {e}")
        return False
    
    # Test sending a message
    try:
        url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
        data = {
            'chat_id': chat_id,
            'text': f'🎉 Sapier Photo Sender is ready!\n📸 Connected from: {os.getcwd()}\n🕐 Test time: {os.popen("date /t & time /t").read().strip()}'
        }
        
        response = requests.post(url, data=data, timeout=10)
        
        if response.status_code == 200:
            result = response.json()
            if result.get('ok'):
                print("✅ Test message sent successfully!")
                print("📱 Check your Telegram for the test message")
                return True
            else:
                print(f"❌ Message send error: {result}")
                return False
        else:
            print(f"❌ HTTP Error: {response.status_code}")
            return False
            
    except Exception as e:
        print(f"❌ Message send error: {e}")
        return False

if __name__ == "__main__":
    if test_telegram_connection():
        print("\n🎉 Sapier Telegram connection test PASSED!")
        print("✅ Ready to run the photo sender app!")
    else:
        print("\n❌ Connection test FAILED!")
        print("🔧 Check your .env file and try again")
