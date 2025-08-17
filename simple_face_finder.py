#!/usr/bin/env python3
"""
Simple Face Finder - OpenCV Face Detection
Finds images containing faces and sends them via Telegram bot.
Uses OpenCV's built-in face detection instead of face recognition.
"""

import os
import sys
import cv2
import numpy as np
import requests
from datetime import datetime
from dotenv import load_dotenv
import time

# Load environment variables
load_dotenv()

class SimpleFaceFinder:
    def __init__(self):
        self.bot_token = os.getenv('TELEGRAM_BOT_TOKEN')
        self.chat_id = os.getenv('TELEGRAM_ADMIN_USER_ID')
        self.api_base_url = f"https://api.telegram.org/bot{self.bot_token}"
        
        if not self.bot_token or not self.chat_id:
            print("❌ Telegram configuration missing in .env file")
            sys.exit(1)
        
        # Common photo folders
        self.photo_folders = [
            os.path.join(os.path.expanduser("~"), "Pictures"),
            os.path.join(os.path.expanduser("~"), "Documents"),
            os.path.join(os.path.expanduser("~"), "Desktop"),
            os.path.join(os.path.expanduser("~"), "Downloads"),
            os.path.join(os.path.expanduser("~"), "OneDrive", "Pictures"),
        ]
        
        # Supported image formats
        self.image_extensions = ['.jpg', '.jpeg', '.png', '.bmp', '.tiff', '.webp']
        
        # Initialize OpenCV face detector
        self.face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')
        
    def find_all_images(self):
        """Find all image files in photo folders"""
        image_files = []
        
        for folder in self.photo_folders:
            if os.path.exists(folder):
                print(f"🔍 Scanning folder: {folder}")
                
                for root, dirs, files in os.walk(folder):
                    for file in files:
                        if any(file.lower().endswith(ext) for ext in self.image_extensions):
                            full_path = os.path.join(root, file)
                            image_files.append(full_path)
        
        print(f"📸 Found {len(image_files)} total images")
        return image_files
    
    def has_faces(self, image_path):
        """Check if image contains faces using OpenCV"""
        try:
            # Read the image
            img = cv2.imread(image_path)
            if img is None:
                return False
            
            # Convert to grayscale
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
            
            # Detect faces
            faces = self.face_cascade.detectMultiScale(
                gray,
                scaleFactor=1.1,
                minNeighbors=5,
                minSize=(30, 30)
            )
            
            return len(faces) > 0
            
        except Exception as e:
            print(f"   ⚠️  Error processing {os.path.basename(image_path)}: {e}")
            return False
    
    def is_sara_related(self, image_path):
        """Check if image filename suggests it might be Sara (simple heuristic)"""
        filename = os.path.basename(image_path).lower()
        sara_keywords = ['sara', 'sarah', 'person', 'people', 'friend', 'photo']
        
        # If filename contains sara-related keywords AND has faces, it's likely Sara
        has_sara_keyword = any(keyword in filename for keyword in sara_keywords[:2])  # Only sara/sarah
        has_general_keyword = any(keyword in filename for keyword in sara_keywords[2:])  # Other keywords
        
        return has_sara_keyword or (has_general_keyword and self.has_faces(image_path))
        
    def is_son_related(self, image_path):
        """Check if image filename suggests it might be related to son (simple heuristic)"""
        filename = os.path.basename(image_path).lower()
        son_keywords = ['son', 'boy', 'kid', 'child', 'children', 'family']
        
        # If filename contains son-related keywords AND has faces, it's likely son-related
        has_son_keyword = any(keyword in filename for keyword in son_keywords[:2])  # Only son/boy
        has_general_keyword = any(keyword in filename for keyword in son_keywords[2:])  # Other keywords
        
        # Check if the image is in a folder that might contain son's photos
        folder_path = os.path.dirname(image_path).lower()
        son_folders = ['son', 'family', 'children', 'kids']
        in_son_folder = any(folder in folder_path for folder in son_folders)
        
        return has_son_keyword or in_son_folder or (has_general_keyword and self.has_faces(image_path))
    
    def send_image_to_telegram(self, image_path, caption=""):
        """Send image to Telegram"""
        try:
            url = f"{self.api_base_url}/sendPhoto"
            
            with open(image_path, 'rb') as photo:
                files = {'photo': photo}
                data = {
                    'chat_id': self.chat_id,
                    'caption': caption
                }
                
                response = requests.post(url, files=files, data=data, timeout=30)
            
            if response.status_code == 200:
                result = response.json()
                if result.get('ok'):
                    return True
                else:
                    print(f"   ❌ Telegram API error: {result}")
                    return False
            else:
                print(f"   ❌ HTTP Error: {response.status_code}")
                return False
                
        except Exception as e:
            print(f"   ❌ Error sending image: {e}")
            return False
    
    def find_and_send_face_images(self, max_images=10, search_mode="faces"):
        """Find images with faces and send them"""
        print("👤 Simple Face Detection System")
        print("=" * 50)
        
        # Find all images
        all_images = self.find_all_images()
        
        if not all_images:
            print("❌ No images found in gallery")
            return
        
        # Sort by modification time (newest first)
        all_images.sort(key=lambda x: os.path.getmtime(x), reverse=True)
        
        print(f"\n🔍 Scanning images for {search_mode}...")
        print(f"⏳ This may take a few minutes...")
        print(f"🎯 Will send maximum {max_images} images")
        print("-" * 50)
        
        found_images = []
        processed_count = 0
        
        for i, image_path in enumerate(all_images):
            try:
                filename = os.path.basename(image_path)
                print(f"📸 [{i+1}/{len(all_images)}] Checking: {filename}")
                
                # Check based on search mode
                if search_mode == "faces":
                    found = self.has_faces(image_path)
                elif search_mode == "sara":
                    found = self.is_sara_related(image_path) and self.has_faces(image_path)
                elif search_mode == "son":
                    found = self.is_son_related(image_path) and self.has_faces(image_path)
                else:
                    found = self.has_faces(image_path)
                
                if found:
                    print(f"   ✅ Match found!")
                    found_images.append(image_path)
                    
                    # Stop if we've found enough images
                    if len(found_images) >= max_images:
                        print(f"   🎯 Reached maximum of {max_images} images")
                        break
                else:
                    print(f"   ❌ No match")
                
                processed_count += 1
                
                # Small delay to prevent overloading
                time.sleep(0.1)
                
            except Exception as e:
                print(f"   ❌ Error: {e}")
                continue
        
        # Send found images to Telegram
        if not found_images:
            print(f"\n😔 No matching images found in {processed_count} images checked")
            return
        
        print(f"\n🎉 Found {len(found_images)} matching images!")
        print("📤 Sending to Telegram...")
        print("-" * 50)
        
        sent_count = 0
        for i, image_path in enumerate(found_images):
            try:
                filename = os.path.basename(image_path)
                print(f"📤 [{i+1}/{len(found_images)}] Sending: {filename}")
                
                # Create caption
                caption = f"Photo {i+1}/{len(found_images)}\n📸 {filename}\n🕐 {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}"
                
                # Send image
                if self.send_image_to_telegram(image_path, caption):
                    sent_count += 1
                    print(f"   ✅ Sent successfully")
                    
                    # Delay between sends to avoid rate limiting
                    time.sleep(2)
                else:
                    print(f"   ❌ Failed to send")
                
            except Exception as e:
                print(f"   ❌ Error: {e}")
                continue
        
        print(f"\n🎉 Process Complete!")
        print(f"📊 Images scanned: {processed_count}")
        print(f"📸 Matching images found: {len(found_images)}")
        print(f"📤 Successfully sent: {sent_count}")
        print(f"📱 Check your Telegram for the photos!")
    
    def send_recent_photos(self, max_images=10):
        """Send recent photos regardless of content"""
        print("📷 Recent Photos Sender")
        print("=" * 50)
        
        all_images = self.find_all_images()
        
        if not all_images:
            print("❌ No images found")
            return
        
        # Sort by modification time (newest first)
        all_images.sort(key=lambda x: os.path.getmtime(x), reverse=True)
        
        print(f"📤 Sending {min(max_images, len(all_images))} most recent photos...")
        print("-" * 50)
        
        sent_count = 0
        for i, image_path in enumerate(all_images[:max_images]):
            try:
                filename = os.path.basename(image_path)
                print(f"📤 [{i+1}/{min(max_images, len(all_images))}] Sending: {filename}")
                
                caption = f"Recent photo {i+1}/{min(max_images, len(all_images))}\n📸 {filename}\n🕐 {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}"
                
                if self.send_image_to_telegram(image_path, caption):
                    sent_count += 1
                    print(f"   ✅ Sent successfully")
                    time.sleep(2)
                else:
                    print(f"   ❌ Failed to send")
                
            except Exception as e:
                print(f"   ❌ Error: {e}")
                continue
        
        print(f"\n🎉 Sent {sent_count} recent photos to Telegram!")

def main():
    """Main function"""
    finder = SimpleFaceFinder()
    
    print("Choose an option:")
    print("1. Send photos with faces (max 10)")
    print("2. Send Sara-related photos (max 10)")
    print("3. Send recent photos (max 10)")
    print("4. Send photos with faces (max 5)")
    print("5. Send recent photos (max 5)")
    print("6. Send Son-related photos (max 10)")
    print()
    
    try:
        choice = input("Enter choice (1-6): ").strip()
        
        if choice == "1":
            finder.find_and_send_face_images(max_images=10, search_mode="faces")
        elif choice == "2":
            finder.find_and_send_face_images(max_images=10, search_mode="sara")
        elif choice == "3":
            finder.send_recent_photos(max_images=10)
        elif choice == "4":
            finder.find_and_send_face_images(max_images=5, search_mode="faces")
        elif choice == "5":
            finder.send_recent_photos(max_images=5)
        elif choice == "6":
            finder.find_and_send_face_images(max_images=10, search_mode="son")
        else:
            print("❌ Invalid choice")
            
    except KeyboardInterrupt:
        print("\n🛑 Process cancelled by user")
    except Exception as e:
        print(f"❌ Error: {e}")

if __name__ == "__main__":
    main()
