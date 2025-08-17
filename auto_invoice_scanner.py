#!/usr/bin/env python3
"""
Automatic Invoice Photo Scanner
Automatically scans photos, detects invoices, extracts data, and sends to Telegram.
"""

import os
import sys
import cv2
import numpy as np
import pytesseract
import re
from datetime import datetime
from PIL import Image
import json
from pathlib import Path
from invoice_sender import InvoiceSender

class AutoInvoiceScanner:
    def __init__(self):
        self.sender = InvoiceSender()
        
        # Common photo folders in Windows
        self.photo_folders = [
            os.path.join(os.path.expanduser("~"), "Pictures"),
            os.path.join(os.path.expanduser("~"), "Documents"),
            os.path.join(os.path.expanduser("~"), "Desktop"),
            os.path.join(os.path.expanduser("~"), "Downloads"),
            os.path.join(os.path.expanduser("~"), "OneDrive", "Pictures"),
        ]
        
        # Supported image formats
        self.image_extensions = ['.jpg', '.jpeg', '.png', '.bmp', '.tiff', '.webp']
        
        # Invoice keywords to identify invoice images
        self.invoice_keywords = [
            'invoice', 'bill', 'receipt', 'total', 'amount', 'due', 'paid',
            'subtotal', 'tax', 'qty', 'quantity', 'price', 'cost', 'payment',
            'invoice#', 'inv#', 'receipt#', 'bill#', 'date', 'customer'
        ]
        
    def find_all_images(self):
        """Find all image files in common photo folders"""
        image_files = []
        
        for folder in self.photo_folders:
            if os.path.exists(folder):
                print(f"🔍 Scanning: {folder}")
                
                for root, dirs, files in os.walk(folder):
                    for file in files:
                        if any(file.lower().endswith(ext) for ext in self.image_extensions):
                            full_path = os.path.join(root, file)
                            image_files.append(full_path)
                            
        print(f"📸 Found {len(image_files)} images total")
        return image_files
    
    def extract_text_from_image(self, image_path):
        """Extract text from image using OCR"""
        try:
            # Read image
            image = cv2.imread(image_path)
            if image is None:
                return ""
                
            # Convert to grayscale
            gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
            
            # Apply image preprocessing to improve OCR
            # Denoise
            denoised = cv2.fastNlMeansDenoising(gray)
            
            # Threshold to get better contrast
            _, thresh = cv2.threshold(denoised, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
            
            # Extract text using Tesseract
            text = pytesseract.image_to_string(thresh, config='--psm 6')
            
            return text.strip()
            
        except Exception as e:
            print(f"⚠️  Error processing {image_path}: {e}")
            return ""
    
    def is_invoice_image(self, text):
        """Check if extracted text indicates this is an invoice"""
        if not text:
            return False
            
        text_lower = text.lower()
        
        # Count how many invoice keywords are found
        keyword_count = sum(1 for keyword in self.invoice_keywords if keyword in text_lower)
        
        # If we find multiple invoice-related keywords, it's likely an invoice
        return keyword_count >= 3
    
    def extract_invoice_data(self, text, image_path):
        """Extract structured invoice data from OCR text"""
        lines = text.split('\n')
        
        invoice_data = {
            "invoice_number": "AUTO-" + datetime.now().strftime("%Y%m%d-%H%M%S"),
            "date": datetime.now().strftime("%Y-%m-%d"),
            "customer": {
                "name": "Customer from Image",
                "email": "",
                "phone": "",
                "address": ""
            },
            "items": [],
            "payment": {
                "method": "Unknown",
                "status": "Detected from Image"
            },
            "notes": f"Automatically extracted from image: {os.path.basename(image_path)}"
        }
        
        # Try to extract invoice number
        for line in lines:
            # Look for invoice number patterns
            inv_match = re.search(r'inv(?:oice)?[#\s]*:?\s*([a-zA-Z0-9-]+)', line, re.IGNORECASE)
            if inv_match:
                invoice_data["invoice_number"] = inv_match.group(1)
                break
        
        # Try to extract date
        for line in lines:
            # Look for date patterns
            date_match = re.search(r'(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})', line)
            if date_match:
                try:
                    # Try to parse and reformat date
                    date_str = date_match.group(1)
                    invoice_data["date"] = date_str
                    break
                except:
                    pass
        
        # Try to extract customer name (usually near "bill to" or "customer")
        for i, line in enumerate(lines):
            if any(phrase in line.lower() for phrase in ['bill to', 'customer', 'client']):
                # Customer name might be in next few lines
                for j in range(i+1, min(i+4, len(lines))):
                    if lines[j].strip() and not any(char.isdigit() for char in lines[j]):
                        invoice_data["customer"]["name"] = lines[j].strip()
                        break
                break
        
        # Try to extract items and amounts
        total_amount = 0
        item_count = 1
        
        for line in lines:
            # Look for price patterns ($XX.XX or XX.XX)
            price_matches = re.findall(r'\$?(\d+\.?\d*)', line)
            if price_matches:
                # Look for items with prices
                if any(word in line.lower() for word in ['item', 'service', 'product', 'description']):
                    # Extract item name (text before the price)
                    price_pattern = r'\$?\d+\.?\d*'
                    item_name = re.sub(price_pattern, '', line).strip()
                    if item_name:
                        try:
                            price = float(price_matches[-1])  # Use last price found
                            invoice_data["items"].append({
                                "name": item_name,
                                "quantity": 1,
                                "price": price
                            })
                            total_amount += price
                        except:
                            pass
        
        # If no items found, create a generic item with total
        if not invoice_data["items"]:
            # Look for total amount
            for line in lines:
                if any(word in line.lower() for word in ['total', 'amount due', 'balance']):
                    total_matches = re.findall(r'\$?(\d+\.?\d+)', line)
                    if total_matches:
                        try:
                            total_amount = float(total_matches[-1])
                            invoice_data["items"].append({
                                "name": "Invoice Amount (from image)",
                                "quantity": 1,
                                "price": total_amount
                            })
                            break
                        except:
                            pass
        
        # If still no items, create a placeholder
        if not invoice_data["items"]:
            invoice_data["items"].append({
                "name": "Invoice detected in image (details unclear)",
                "quantity": 1,
                "price": 0.00
            })
        
        return invoice_data
    
    def process_images(self, max_images=10):
        """Process images and send invoices to Telegram"""
        print("🤖 Starting Automatic Invoice Scanner")
        print("=" * 50)
        
        # Find all images
        image_files = self.find_all_images()
        
        if not image_files:
            print("❌ No images found in common folders")
            return
        
        processed_count = 0
        invoice_count = 0
        
        print(f"\n📋 Processing up to {max_images} images...")
        print("⏳ This may take a few minutes...")
        
        for i, image_path in enumerate(image_files[:max_images]):
            try:
                print(f"\n📸 [{i+1}/{min(len(image_files), max_images)}] Processing: {os.path.basename(image_path)}")
                
                # Extract text from image
                text = self.extract_text_from_image(image_path)
                
                if not text:
                    print("   ⚠️  No text detected")
                    continue
                
                # Check if it's an invoice
                if self.is_invoice_image(text):
                    print("   ✅ Invoice detected!")
                    
                    # Extract invoice data
                    invoice_data = self.extract_invoice_data(text, image_path)
                    
                    # Send to Telegram
                    success = self.sender.send_invoice(invoice_data)
                    
                    if success:
                        invoice_count += 1
                        print(f"   📤 Sent invoice #{invoice_count} to Telegram")
                    else:
                        print("   ❌ Failed to send to Telegram")
                else:
                    print("   ℹ️  Not an invoice image")
                
                processed_count += 1
                
            except Exception as e:
                print(f"   ❌ Error: {e}")
                continue
        
        print(f"\n🎉 Scan Complete!")
        print(f"📊 Processed: {processed_count} images")
        print(f"🧾 Found: {invoice_count} invoices")
        print(f"📱 Sent to Telegram: {invoice_count} invoices")
    
    def scan_specific_folder(self, folder_path, max_images=20):
        """Scan a specific folder for invoices"""
        if not os.path.exists(folder_path):
            print(f"❌ Folder not found: {folder_path}")
            return
            
        print(f"🔍 Scanning specific folder: {folder_path}")
        
        image_files = []
        for root, dirs, files in os.walk(folder_path):
            for file in files:
                if any(file.lower().endswith(ext) for ext in self.image_extensions):
                    image_files.append(os.path.join(root, file))
        
        if not image_files:
            print("❌ No images found in the specified folder")
            return
        
        print(f"📸 Found {len(image_files)} images in folder")
        
        # Process images (reuse the same logic)
        processed_count = 0
        invoice_count = 0
        
        for i, image_path in enumerate(image_files[:max_images]):
            try:
                print(f"\n📸 [{i+1}/{min(len(image_files), max_images)}] Processing: {os.path.basename(image_path)}")
                
                text = self.extract_text_from_image(image_path)
                
                if not text:
                    print("   ⚠️  No text detected")
                    continue
                
                if self.is_invoice_image(text):
                    print("   ✅ Invoice detected!")
                    
                    invoice_data = self.extract_invoice_data(text, image_path)
                    success = self.sender.send_invoice(invoice_data)
                    
                    if success:
                        invoice_count += 1
                        print(f"   📤 Sent invoice #{invoice_count} to Telegram")
                    else:
                        print("   ❌ Failed to send to Telegram")
                else:
                    print("   ℹ️  Not an invoice image")
                
                processed_count += 1
                
            except Exception as e:
                print(f"   ❌ Error: {e}")
                continue
        
        print(f"\n🎉 Folder Scan Complete!")
        print(f"📊 Processed: {processed_count} images")
        print(f"🧾 Found: {invoice_count} invoices")
        print(f"📱 Sent to Telegram: {invoice_count} invoices")

def main():
    """Main function"""
    scanner = AutoInvoiceScanner()
    
    print("🤖 Automatic Invoice Photo Scanner")
    print("=" * 40)
    print()
    print("Choose an option:")
    print("1. Scan all common photo folders (max 10 images)")
    print("2. Scan specific folder")
    print("3. Quick scan (max 5 images)")
    print()
    
    try:
        choice = input("Enter choice (1-3): ").strip()
        
        if choice == "1":
            scanner.process_images(max_images=10)
        elif choice == "2":
            folder = input("Enter folder path: ").strip()
            scanner.scan_specific_folder(folder)
        elif choice == "3":
            scanner.process_images(max_images=5)
        else:
            print("❌ Invalid choice")
            
    except KeyboardInterrupt:
        print("\n🛑 Scan cancelled by user")
    except Exception as e:
        print(f"❌ Error: {e}")

if __name__ == "__main__":
    main()
