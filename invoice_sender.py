#!/usr/bin/env python3
"""
Invoice to Telegram Sender
Send invoice details to Telegram with a single click.
"""

import os
import sys
import requests
import json
from datetime import datetime
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

class InvoiceSender:
    def __init__(self):
        self.bot_token = os.getenv('TELEGRAM_BOT_TOKEN')
        self.chat_id = os.getenv('TELEGRAM_ADMIN_USER_ID')
        self.api_base_url = f"https://api.telegram.org/bot{self.bot_token}"
        
        if not self.bot_token:
            print("❌ TELEGRAM_BOT_TOKEN not found in .env file")
            sys.exit(1)
            
        if not self.chat_id:
            print("❌ TELEGRAM_ADMIN_USER_ID not found in .env file")
            sys.exit(1)
    
    def format_invoice(self, invoice_data):
        """Format invoice data for Telegram message"""
        message = "🧾 INVOICE DETAILS\n"
        message += "=" * 40 + "\n\n"
        
        # Basic invoice info
        message += f"📄 Invoice Number: {invoice_data.get('invoice_number', 'N/A')}\n"
        message += f"📅 Invoice Date: {invoice_data.get('date', datetime.now().strftime('%Y-%m-%d'))}\n"
        message += f"⏰ Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n"
        message += "-" * 40 + "\n\n"
        
        # Customer info
        if 'customer' in invoice_data:
            customer = invoice_data['customer']
            message += "👤 CUSTOMER INFORMATION\n"
            message += f"• Name: {customer.get('name', 'N/A')}\n"
            message += f"• Email: {customer.get('email', 'N/A')}\n"
            message += f"• Phone: {customer.get('phone', 'N/A')}\n"
            if customer.get('address'):
                message += f"• Address: {customer.get('address')}\n"
            message += "\n"
        
        # Items section
        total = 0
        if 'items' in invoice_data and invoice_data['items']:
            message += "🛍️ INVOICE ITEMS\n"
            message += "-" * 40 + "\n"
            
            for i, item in enumerate(invoice_data['items'], 1):
                name = item.get('name', 'Item')
                qty = item.get('quantity', 1)
                price = item.get('price', 0)
                subtotal = qty * price
                total += subtotal
                
                message += f"{i}. {name}\n"
                message += f"   Quantity: {qty}\n"
                message += f"   Unit Price: ${price:.2f}\n"
                message += f"   Subtotal: ${subtotal:.2f}\n"
                message += "\n"
        
        # Calculation breakdown
        message += "💰 PAYMENT SUMMARY\n"
        message += "-" * 40 + "\n"
        
        # Calculate tax if needed (you can add tax logic here)
        subtotal_amount = total
        tax_amount = 0  # Add tax calculation if needed
        total_amount = subtotal_amount + tax_amount
        
        message += f"Subtotal: ${subtotal_amount:.2f}\n"
        if tax_amount > 0:
            message += f"Tax: ${tax_amount:.2f}\n"
        message += f"\n💵 TOTAL AMOUNT: ${total_amount:.2f}\n"
        message += "=" * 40 + "\n\n"
        
        # Payment info
        if 'payment' in invoice_data:
            payment = invoice_data['payment']
            message += "💳 PAYMENT INFORMATION\n"
            message += f"• Method: {payment.get('method', 'N/A')}\n"
            message += f"• Status: {payment.get('status', 'Pending')}\n"
            message += "\n"
        
        # Notes
        if 'notes' in invoice_data and invoice_data['notes']:
            message += "📝 ADDITIONAL NOTES\n"
            message += f"{invoice_data['notes']}\n\n"
        
        # Footer
        message += "━" * 40 + "\n"
        message += f"📱 Sent via Telegram Bot\n"
        message += f"🕐 {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n"
        message += "━" * 40
        
        return message
    
    def send_invoice(self, invoice_data):
        """Send invoice to Telegram"""
        try:
            formatted_message = self.format_invoice(invoice_data)
            
            url = f"{self.api_base_url}/sendMessage"
            data = {
                'chat_id': self.chat_id,
                'text': formatted_message,
                'parse_mode': 'Markdown'
            }
            
            response = requests.post(url, data=data, timeout=10)
            
            if response.status_code == 200:
                result = response.json()
                if result.get('ok'):
                    print("✅ Invoice sent to Telegram successfully!")
                    return True
                else:
                    print(f"❌ Failed to send invoice: {result}")
                    return False
            else:
                print(f"❌ HTTP Error: {response.status_code}")
                print(response.text)
                return False
                
        except Exception as e:
            print(f"❌ Error sending invoice: {e}")
            return False
    
    def send_sample_invoice(self):
        """Send a sample invoice for testing"""
        sample_invoice = {
            "invoice_number": "INV-2025-001",
            "date": "2025-08-16",
            "customer": {
                "name": "Abu Sayeam",
                "email": "sayeama4@gmail.com",
                "phone": "+1234567890",
                "address": "123 Main St, City, Country"
            },
            "items": [
                {
                    "name": "Web Development Service",
                    "quantity": 1,
                    "price": 500.00
                },
                {
                    "name": "Domain Registration",
                    "quantity": 1,
                    "price": 15.00
                },
                {
                    "name": "Hosting (1 Year)",
                    "quantity": 1,
                    "price": 120.00
                }
            ],
            "payment": {
                "method": "Bank Transfer",
                "status": "Paid"
            },
            "notes": "Thank you for your business! Invoice generated automatically."
        }
        
        return self.send_invoice(sample_invoice)

def main():
    """Main function"""
    print("🧾 Invoice to Telegram Sender")
    print("=" * 30)
    
    sender = InvoiceSender()
    
    # Send sample invoice
    print("Sending sample invoice...")
    success = sender.send_sample_invoice()
    
    if success:
        print("🎉 Sample invoice sent! Check your Telegram.")
    else:
        print("❌ Failed to send invoice.")

if __name__ == "__main__":
    main()
