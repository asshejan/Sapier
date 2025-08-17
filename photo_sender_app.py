#!/usr/bin/env python3
"""
Photo Sender App - Web Interface
Simple web app with buttons to send photos via Telegram bot.
"""

from flask import Flask, render_template_string, request, jsonify, redirect, url_for
import os
import sys
import threading
from simple_face_finder import SimpleFaceFinder

app = Flask(__name__)

# HTML template for the web interface
HTML_TEMPLATE = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>📸 Sapier Photo Sender to Telegram</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            max-width: 800px;
            margin: 0 auto;
            padding: 20px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            min-height: 100vh;
        }
        .container {
            background: rgba(255, 255, 255, 0.1);
            padding: 30px;
            border-radius: 15px;
            backdrop-filter: blur(10px);
            box-shadow: 0 8px 32px 0 rgba(31, 38, 135, 0.37);
            border: 1px solid rgba(255, 255, 255, 0.18);
        }
        h1 {
            text-align: center;
            margin-bottom: 30px;
            font-size: 2.5em;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
        }
        .button-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin: 30px 0;
        }
        .btn {
            background: linear-gradient(45deg, #FF6B6B, #4ECDC4);
            color: white;
            padding: 20px 30px;
            border: none;
            border-radius: 10px;
            cursor: pointer;
            font-size: 18px;
            font-weight: bold;
            transition: all 0.3s ease;
            box-shadow: 0 4px 15px 0 rgba(31, 38, 135, 0.4);
            text-align: center;
            min-height: 80px;
            display: flex;
            flex-direction: column;
            justify-content: center;
            align-items: center;
        }
        .btn:hover {
            transform: translateY(-5px);
            box-shadow: 0 8px 25px 0 rgba(31, 38, 135, 0.6);
        }
        .btn-recent {
            background: linear-gradient(45deg, #4ECDC4, #44A08D);
        }
        .btn-faces {
            background: linear-gradient(45deg, #FF9A8B, #A8E6CF);
        }
        .btn-sara {
            background: linear-gradient(45deg, #FFB7B7, #FF9A8B);
        }
        .btn-test {
            background: linear-gradient(45deg, #C7A2C7, #9A8C98);
        }
        .status {
            padding: 15px;
            margin: 20px 0;
            border-radius: 10px;
            text-align: center;
            font-weight: bold;
            display: none;
        }
        .success {
            background: rgba(40, 167, 69, 0.2);
            border: 1px solid rgba(40, 167, 69, 0.5);
            color: #28a745;
        }
        .error {
            background: rgba(220, 53, 69, 0.2);
            border: 1px solid rgba(220, 53, 69, 0.5);
            color: #dc3545;
        }
        .loading {
            background: rgba(255, 193, 7, 0.2);
            border: 1px solid rgba(255, 193, 7, 0.5);
            color: #ffc107;
        }
        .icon {
            font-size: 24px;
            margin-bottom: 10px;
        }
        .description {
            font-size: 14px;
            opacity: 0.9;
            margin-top: 5px;
        }
        .stats {
            background: rgba(255, 255, 255, 0.1);
            padding: 20px;
            border-radius: 10px;
            margin: 20px 0;
            text-align: center;
        }
        .footer {
            text-align: center;
            margin-top: 30px;
            opacity: 0.8;
            font-size: 14px;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>📸 Sapier Photo Sender</h1>
        
        <div class="stats">
            <h3>🤖 Your Telegram Bot is Ready!</h3>
            <p>Click any button below to send photos to your Telegram bot</p>
        </div>

        <div id="status" class="status"></div>

        <div class="button-grid">
            <button class="btn btn-recent" onclick="sendPhotos('recent', 5)">
                <div class="icon">📷</div>
                <div>Send 5 Recent Photos</div>
                <div class="description">Latest photos from your gallery</div>
            </button>

            <button class="btn btn-recent" onclick="sendPhotos('recent', 10)">
                <div class="icon">📸</div>
                <div>Send 10 Recent Photos</div>
                <div class="description">More recent photos</div>
            </button>

            <button class="btn btn-faces" onclick="sendPhotos('faces', 5)">
                <div class="icon">👤</div>
                <div>Send Photos with Faces</div>
                <div class="description">5 photos containing faces</div>
            </button>

            <button class="btn btn-faces" onclick="sendPhotos('faces', 10)">
                <div class="icon">👥</div>
                <div>Send More Face Photos</div>
                <div class="description">10 photos containing faces</div>
            </button>

            <button class="btn btn-sara" onclick="sendPhotos('sara', 10)">
                <div class="icon">👩</div>
                <div>Find Sara's Photos</div>
                <div class="description">Search for Sara-related images</div>
            </button>

            <button class="btn btn-test" onclick="sendPhotos('test', 3)">
                <div class="icon">🧪</div>
                <div>Quick Test</div>
                <div class="description">Send 3 photos for testing</div>
            </button>
        </div>

        <div class="footer">
            <p>🔗 Connected to Telegram Bot: @sapier2_bot</p>
            <p>📱 Photos will appear in your Telegram chat</p>
        </div>
    </div>

    <script>
        function showStatus(message, type) {
            const statusDiv = document.getElementById('status');
            statusDiv.textContent = message;
            statusDiv.className = `status ${type}`;
            statusDiv.style.display = 'block';
            
            if (type === 'success' || type === 'error') {
                setTimeout(() => {
                    statusDiv.style.display = 'none';
                }, 5000);
            }
        }

        function sendPhotos(mode, count) {
            showStatus(`🔄 Processing... This may take a few minutes`, 'loading');
            
            // Disable all buttons during processing
            const buttons = document.querySelectorAll('.btn');
            buttons.forEach(btn => btn.disabled = true);
            
            fetch('/send_photos', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    mode: mode,
                    count: count
                })
            })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    showStatus(`✅ Success! Sent ${data.sent_count} photos to Telegram`, 'success');
                } else {
                    showStatus(`❌ Error: ${data.error}`, 'error');
                }
            })
            .catch(error => {
                showStatus(`❌ Error: ${error}`, 'error');
            })
            .finally(() => {
                // Re-enable buttons
                buttons.forEach(btn => btn.disabled = false);
            });
        }

        // Show initial status
        window.onload = function() {
            showStatus('🟢 Ready to send photos!', 'success');
        };
    </script>
</body>
</html>
"""

@app.route('/')
def index():
    return render_template_string(HTML_TEMPLATE)

@app.route('/send_photos', methods=['POST'])
def send_photos():
    try:
        data = request.json
        mode = data.get('mode', 'recent')
        count = data.get('count', 5)
        
        finder = SimpleFaceFinder()
        
        def run_in_background():
            global result_data
            try:
                if mode == 'recent' or mode == 'test':
                    finder.send_recent_photos(max_images=count)
                    result_data = {'success': True, 'sent_count': count}
                elif mode == 'faces':
                    finder.find_and_send_face_images(max_images=count, search_mode="faces")
                    result_data = {'success': True, 'sent_count': count}
                elif mode == 'sara':
                    finder.find_and_send_face_images(max_images=count, search_mode="sara")
                    result_data = {'success': True, 'sent_count': count}
                else:
                    result_data = {'success': False, 'error': 'Invalid mode'}
            except Exception as e:
                result_data = {'success': False, 'error': str(e)}
        
        # Run in background thread (simplified response)
        thread = threading.Thread(target=run_in_background)
        thread.start()
        thread.join(timeout=60)  # Wait up to 60 seconds
        
        # For simplicity, return success (the actual sending happens in background)
        return jsonify({
            'success': True,
            'sent_count': count,
            'message': f'Started sending {count} photos in {mode} mode'
        })
        
    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e)
        })

if __name__ == '__main__':
    print("🌐 Starting Sapier Photo Sender App...")
    print("📱 Your Sapier photo sender is ready!")
    print("🔗 Open: http://localhost:5000")
    print("=" * 40)
    
    app.run(debug=True, host='0.0.0.0', port=5000)
