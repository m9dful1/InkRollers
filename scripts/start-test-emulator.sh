#!/bin/bash

# Firebase Emulator Startup Script for Integration Testing
# This script ensures a clean Firebase emulator environment for testing

set -e

echo "🔥 Starting Firebase Emulator for Integration Testing..."

# Check if Firebase CLI is installed
if ! command -v firebase &> /dev/null; then
    echo "❌ Firebase CLI not found. Please install it:"
    echo "   npm install -g firebase-tools"
    exit 1
fi

# Navigate to project root (assumes script is in /scripts directory)
cd "$(dirname "$0")/.."

# Kill any existing emulator processes
echo "🧹 Cleaning up existing emulator processes..."
pkill -f "firebase-database-emulator" || true
pkill -f "firebase" || true

# Wait for processes to clean up
sleep 2

# Clear emulator data to ensure clean state
echo "🗑️ Clearing emulator data..."
rm -rf .firebase/emulators-cache || true

# Start emulators in background
echo "🚀 Starting Firebase emulators..."
firebase emulators:start --only database,auth &

# Wait for emulators to be ready
echo "⏳ Waiting for emulators to start..."
sleep 10

# Test connectivity
echo "🔍 Testing emulator connectivity..."
curl -s http://127.0.0.1:9001/.json > /dev/null
if [ $? -eq 0 ]; then
    echo "✅ Firebase Database Emulator ready at http://127.0.0.1:9001"
else
    echo "❌ Failed to connect to Firebase Database Emulator"
    exit 1
fi

curl -s http://127.0.0.1:9099 > /dev/null
if [ $? -eq 0 ]; then
    echo "✅ Firebase Auth Emulator ready at http://127.0.0.1:9099"
else
    echo "❌ Failed to connect to Firebase Auth Emulator"
    exit 1
fi

echo "🎉 Emulators ready! View at http://127.0.0.1:4000"
echo "🧪 Run integration tests with: ./gradlew connectedAndroidTest"
echo "🛑 Stop emulators with: firebase emulators:stop" 