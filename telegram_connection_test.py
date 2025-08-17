#!/usr/bin/env python3
"""
Telegram Bot Connection Tester
This script helps diagnose Telegram bot connection issues.
"""

import os
import sys
import requests
import json
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

class TelegramConnectionTester:
    def __init__(self):
        self.bot_token = os.getenv('TELEGRAM_BOT_TOKEN')
        self.api_base_url = "https://api.telegram.org/bot"
        
    def test_connection(self):
        """Test all aspects of Telegram bot connection"""
        print("🔍 Telegram Bot Connection Diagnostics")
        print("=" * 50)
        
        # Test 1: Check if bot token is provided
        if not self.bot_token:
            print("❌ TELEGRAM_BOT_TOKEN not found in environment variables")
            print("💡 Solution: Set TELEGRAM_BOT_TOKEN in your .env file")
            return False
            
        if self.bot_token == "YOUR_BOT_TOKEN_HERE":
            print("❌ Bot token is still the template value")
            print("💡 Solution: Replace with actual token from @BotFather")
            return False
            
        print(f"✅ Bot token found: {self.bot_token[:10]}...")
        
        # Test 2: Check internet connectivity to Telegram
        try:
            response = requests.get("https://api.telegram.org", timeout=10)
            print("✅ Internet connection to Telegram API: OK")
        except requests.exceptions.RequestException as e:
            print(f"❌ Internet connection failed: {e}")
            print("💡 Solution: Check your internet connection or proxy settings")
            return False
            
        # Test 3: Test bot token validity
        try:
            url = f"{self.api_base_url}{self.bot_token}/getMe"
            response = requests.get(url, timeout=10)
            
            if response.status_code == 200:
                bot_info = response.json()
                if bot_info.get('ok'):
                    print(f"✅ Bot token is valid")
                    print(f"   Bot name: {bot_info['result']['first_name']}")
                    print(f"   Bot username: @{bot_info['result']['username']}")
                else:
                    print(f"❌ Bot token validation failed: {bot_info}")
                    return False
            elif response.status_code == 401:
                print("❌ Unauthorized: Bot token is invalid")
                print("💡 Solution: Check your bot token from @BotFather")
                return False
            else:
                print(f"❌ HTTP Error {response.status_code}: {response.text}")
                return False
                
        except requests.exceptions.RequestException as e:
            print(f"❌ Request failed: {e}")
            return False
            
        # Test 4: Check webhook status (if applicable)
        try:
            url = f"{self.api_base_url}{self.bot_token}/getWebhookInfo"
            response = requests.get(url, timeout=10)
            
            if response.status_code == 200:
                webhook_info = response.json()
                if webhook_info.get('ok'):
                    webhook_url = webhook_info['result'].get('url', '')
                    if webhook_url:
                        print(f"ℹ️  Webhook URL: {webhook_url}")
                        pending_updates = webhook_info['result'].get('pending_update_count', 0)
                        if pending_updates > 0:
                            print(f"⚠️  Pending updates: {pending_updates}")
                    else:
                        print("ℹ️  No webhook configured (using polling)")
                        
        except requests.exceptions.RequestException:
            print("⚠️  Could not check webhook status")
            
        # Test 5: Try to get updates
        try:
            url = f"{self.api_base_url}{self.bot_token}/getUpdates"
            response = requests.get(url, timeout=10)
            
            if response.status_code == 200:
                updates = response.json()
                if updates.get('ok'):
                    print(f"✅ Can retrieve updates: {len(updates['result'])} messages")
                else:
                    print(f"❌ Failed to get updates: {updates}")
                    
        except requests.exceptions.RequestException as e:
            print(f"❌ Failed to get updates: {e}")
            
        print("\n🎉 Connection test completed!")
        return True
        
    def create_env_template(self):
        """Create a .env template file"""
        env_content = """# Telegram Bot Configuration
TELEGRAM_BOT_TOKEN=YOUR_BOT_TOKEN_HERE

# Optional: Webhook configuration
TELEGRAM_WEBHOOK_URL=https://your-domain.com/webhook
TELEGRAM_WEBHOOK_SECRET=your_secret_here

# Optional: Admin user ID
TELEGRAM_ADMIN_USER_ID=your_user_id
"""
        
        if not os.path.exists('.env'):
            with open('.env', 'w') as f:
                f.write(env_content)
            print("📄 Created .env template file")
        else:
            print("📄 .env file already exists")

def main():
    """Main function"""
    print("Telegram Bot Connection Tester")
    print("===============================")
    
    tester = TelegramConnectionTester()
    
    # Create .env template if it doesn't exist
    tester.create_env_template()
    
    # Run connection test
    success = tester.test_connection()
    
    if not success:
        print("\n❌ Connection test failed!")
        print("\n📋 Troubleshooting steps:")
        print("1. Get a bot token from @BotFather on Telegram")
        print("2. Add TELEGRAM_BOT_TOKEN=your_token to your .env file")
        print("3. Check your internet connection")
        print("4. Verify the token is correct and not revoked")
        sys.exit(1)
    else:
        print("\n✅ All tests passed! Your Telegram bot connection is working.")

if __name__ == "__main__":
    main()
