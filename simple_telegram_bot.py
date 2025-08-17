#!/usr/bin/env python3
"""
Simple Telegram Bot Example
This is a basic bot that responds to messages.
"""

import os
import sys
import time
import requests
import json
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

class SimpleTelegramBot:
    def __init__(self):
        self.bot_token = os.getenv('TELEGRAM_BOT_TOKEN')
        self.api_base_url = f"https://api.telegram.org/bot{self.bot_token}"
        self.last_update_id = 0
        
        if not self.bot_token or self.bot_token == "YOUR_BOT_TOKEN_HERE":
            print("❌ Please set your TELEGRAM_BOT_TOKEN in the .env file")
            sys.exit(1)
    
    def send_message(self, chat_id, text):
        """Send a message to a chat"""
        url = f"{self.api_base_url}/sendMessage"
        data = {
            'chat_id': chat_id,
            'text': text
        }
        
        try:
            response = requests.post(url, data=data, timeout=10)
            return response.json()
        except requests.exceptions.RequestException as e:
            print(f"Failed to send message: {e}")
            return None
    
    def get_updates(self):
        """Get updates from Telegram"""
        url = f"{self.api_base_url}/getUpdates"
        params = {
            'offset': self.last_update_id + 1,
            'timeout': 10
        }
        
        try:
            response = requests.get(url, params=params, timeout=15)
            if response.status_code == 200:
                return response.json()
            else:
                print(f"Failed to get updates: {response.status_code}")
                return None
        except requests.exceptions.RequestException as e:
            print(f"Failed to get updates: {e}")
            return None
    
    def handle_message(self, message):
        """Handle incoming messages"""
        chat_id = message['chat']['id']
        text = message.get('text', '')
        user_name = message['from'].get('first_name', 'User')
        
        print(f"📩 Message from {user_name}: {text}")
        
        # Simple responses
        if text.lower() in ['/start', '/hello']:
            response = f"Hello {user_name}! 👋 I'm your bot. How can I help you?"
        elif text.lower() == '/help':
            response = """🤖 Available commands:
/start - Start the bot
/hello - Say hello
/help - Show this help
/time - Get current time
/ping - Test bot response
/chatid - Get your chat ID
/id - Get your chat ID (alias)
/myid - Get your chat ID (alias)"""
        elif text.lower() == '/time':
            response = f"🕐 Current time: {time.strftime('%Y-%m-%d %H:%M:%S')}"
        elif text.lower() == '/ping':
            response = "🏓 Pong! Bot is working."
        elif text.lower() in ['/chatid', '/id', '/myid']:
            response = f"🆔 Your Chat ID is: `{chat_id}`\n\n💡 You can copy this ID for bot configuration."
        else:
            response = f"You said: {text}\n\nTry /help for available commands."
        
        # Send response
        result = self.send_message(chat_id, response)
        if result and result.get('ok'):
            print(f"✅ Sent response to {user_name}")
        else:
            print(f"❌ Failed to send response: {result}")
    
    def run(self):
        """Main bot loop"""
        print("🤖 Starting Telegram Bot...")
        print("Press Ctrl+C to stop")
        print("-" * 40)
        
        try:
            while True:
                updates = self.get_updates()
                
                if updates and updates.get('ok'):
                    for update in updates['result']:
                        self.last_update_id = update['update_id']
                        
                        if 'message' in update:
                            self.handle_message(update['message'])
                
                time.sleep(1)  # Small delay to avoid spam
                
        except KeyboardInterrupt:
            print("\n🛑 Bot stopped by user")
        except Exception as e:
            print(f"❌ Bot error: {e}")

def main():
    """Main function"""
    bot = SimpleTelegramBot()
    bot.run()

if __name__ == "__main__":
    main()
