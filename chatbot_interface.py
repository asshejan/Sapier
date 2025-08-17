#!/usr/bin/env python3
"""
Chatbot Interface for Sapier
Provides a conversational interface to interact with Sapier's photo sending capabilities.
"""

import os
import sys
import time
import re
from simple_face_finder import SimpleFaceFinder
from simple_telegram_bot import SimpleTelegramBot
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

class ChatbotInterface:
    def __init__(self):
        self.bot = SimpleTelegramBot()
        self.face_finder = SimpleFaceFinder()
        self.running = False
        
        # Greeting messages
        self.greetings = [
            "Hello! I'm your Sapier assistant. How can I help you today?",
            "Hi there! I'm ready to assist you with your photos. What would you like to do?",
            "Welcome to Sapier! I can help you send photos to Telegram. Just let me know what you need."
        ]
        
        # Help message
        self.help_message = """
🤖 I can help you with the following:

1. Send photos to Telegram:
   - "Send my son's pictures to Telegram"
   - "Send recent photos to Telegram"
   - "Send photos with faces to Telegram"

2. Other commands:
   - "help" - Show this help message
   - "exit" or "quit" - Exit the chatbot

Just type what you'd like me to do!
"""
    
    def start(self):
        """Start the chatbot interface"""
        self.running = True
        
        # Print welcome message
        print("\n" + "=" * 50)
        print("🤖 Sapier Chatbot Interface")
        print("=" * 50)
        print("Type 'help' for available commands or 'exit' to quit.")
        print("=" * 50 + "\n")
        
        # Print greeting
        import random
        print(f"🤖 {random.choice(self.greetings)}\n")
        
        # Main loop
        while self.running:
            try:
                # Get user input
                user_input = input("You: ").strip()
                
                # Process user input
                if user_input.lower() in ['exit', 'quit', 'bye', 'goodbye']:
                    print("\n🤖 Goodbye! Have a great day!")
                    self.running = False
                    break
                    
                # Process the input and get response
                response = self.process_input(user_input)
                
                # Print response
                print(f"\n🤖 {response}\n")
                
            except KeyboardInterrupt:
                print("\n\n🤖 Chatbot interrupted. Goodbye!")
                self.running = False
                break
            except Exception as e:
                print(f"\n❌ Error: {e}")
                continue
    
    def process_input(self, user_input):
        """Process user input and return appropriate response"""
        # Convert to lowercase for easier matching
        input_lower = user_input.lower()
        
        # Help command
        if input_lower in ['help', 'commands', '?']:
            return self.help_message
        
        # Send son's pictures to Telegram
        if re.search(r'send\s+my\s+sons?\s+pictures?\s+to\s+telegram', input_lower) or \
           re.search(r'send\s+sons?\s+pictures?\s+to\s+telegram', input_lower) or \
           re.search(r'send\s+my\s+sons?\s+photos?\s+to\s+telegram', input_lower):
            return self.send_son_photos()
        
        # Send recent photos
        if re.search(r'send\s+recent\s+photos?\s+to\s+telegram', input_lower) or \
           re.search(r'send\s+latest\s+photos?\s+to\s+telegram', input_lower):
            return self.send_recent_photos()
        
        # Send photos with faces
        if re.search(r'send\s+photos?\s+with\s+faces\s+to\s+telegram', input_lower) or \
           re.search(r'send\s+face\s+photos?\s+to\s+telegram', input_lower):
            return self.send_face_photos()
        
        # Default response for unrecognized input
        return "I'm not sure what you're asking. Type 'help' to see what I can do."
    
    def send_son_photos(self):
        """Send son's photos to Telegram"""
        try:
            print("🔍 Looking for son's photos...")
            
            # Use the face finder to find and send photos with the "son" search mode
            self.face_finder.find_and_send_face_images(max_images=10, search_mode="son")
            
            return "I've sent your son's photos to Telegram! Check your messages."
        except Exception as e:
            return f"Sorry, I couldn't send the photos: {str(e)}"
    
    def send_recent_photos(self):
        """Send recent photos to Telegram"""
        try:
            print("📸 Sending recent photos...")
            self.face_finder.send_recent_photos(max_images=5)
            return "I've sent 5 recent photos to Telegram! Check your messages."
        except Exception as e:
            return f"Sorry, I couldn't send the photos: {str(e)}"
    
    def send_face_photos(self):
        """Send photos with faces to Telegram"""
        try:
            print("👤 Looking for photos with faces...")
            self.face_finder.find_and_send_face_images(max_images=5, search_mode="faces")
            return "I've sent 5 photos with faces to Telegram! Check your messages."
        except Exception as e:
            return f"Sorry, I couldn't send the photos: {str(e)}"

def main():
    """Main function"""
    chatbot = ChatbotInterface()
    chatbot.start()

if __name__ == "__main__":
    main()