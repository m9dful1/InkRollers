#!/bin/bash

# Debug script for viewing Ink Rollers app logs on connected device
# Usage: ./scripts/debug-device-logs.sh

echo "=== Ink Rollers Device Debug Logs ==="
echo "Make sure your device is connected via USB with USB debugging enabled"
echo ""

# Check if device is connected
echo "Checking connected devices..."
adb devices
echo ""

# Clear previous logs
echo "Clearing previous logs..."
adb logcat -c

echo "Starting live log monitoring..."
echo "Press Ctrl+C to stop"
echo ""
echo "=== LIVE LOGS ==="

# Monitor logs with multiple filters for comprehensive debugging
adb logcat -v time \
  *:E \
  MainActivity:D \
  MultiplayerManager:D \
  FirebaseAuth:D \
  FirebaseDatabase:D \
  FirebaseApp:D \
  com.spiritwisestudios.inkrollers:D \
  | grep -E "(MainActivity|MultiplayerManager|Firebase|InkRollers|PERMISSION|AUTH|ERROR)" 