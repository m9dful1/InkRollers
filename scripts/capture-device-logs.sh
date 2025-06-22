#!/bin/bash

# Script to capture Ink Rollers device logs to a file
# Usage: ./scripts/capture-device-logs.sh [duration_in_seconds]

DURATION=${1:-30}  # Default 30 seconds
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOG_FILE="device_logs_${TIMESTAMP}.txt"

echo "=== Capturing Ink Rollers Device Logs ==="
echo "Duration: ${DURATION} seconds"
echo "Output file: ${LOG_FILE}"
echo ""

# Check if device is connected
echo "Checking connected devices..."
adb devices
echo ""

# Clear previous logs
echo "Clearing previous logs..."
adb logcat -c

echo "Capturing logs for ${DURATION} seconds..."
echo "Launch your app now!"
echo ""

# Capture logs to file
timeout ${DURATION} adb logcat -v time \
  *:E \
  MainActivity:D \
  MultiplayerManager:D \
  FirebaseAuth:D \
  FirebaseDatabase:D \
  FirebaseApp:D \
  com.spiritwisestudios.inkrollers:D \
  > ${LOG_FILE}

echo ""
echo "Log capture complete!"
echo "Saved to: ${LOG_FILE}"
echo ""
echo "To view Firebase-specific errors:"
echo "grep -i firebase ${LOG_FILE}"
echo ""
echo "To view permission errors:"
echo "grep -i permission ${LOG_FILE}" 