#!/bin/bash

# Build and deploy SSID Logger to emulator

echo "🔨 Building APK..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "📱 Installing to emulator..."
    /Users/badlogic/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
    
    if [ $? -eq 0 ]; then
        echo "🚀 Launching app..."
        /Users/badlogic/Library/Android/sdk/platform-tools/adb shell am start -n at.mariozechner.ssid_logger/.MainActivity
        echo "✅ Done!"
    else
        echo "❌ Installation failed"
    fi
else
    echo "❌ Build failed"
fi