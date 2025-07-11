#!/bin/bash

# Script to generate placeholder campaign audio files
# This creates simple test audio files for development purposes
# In production, these should be replaced with proper audio content

echo "Generating placeholder campaign audio files..."

# Create raw directory if it doesn't exist
mkdir -p app/src/main/res/raw

# Function to create a simple test audio file
create_test_audio() {
    local filename=$1
    local duration=$2
    local frequency=$3
    
    echo "Creating $filename (${duration}s, ${frequency}Hz)..."
    
    # Use sox to generate a simple sine wave (if available)
    if command -v sox &> /dev/null; then
        sox -n -r 44100 -c 1 "app/src/main/res/raw/$filename" synth $duration sine $frequency fade t 0.1 $duration 0.1
    else
        # Create a dummy file if sox is not available
        echo "Sox not found, creating dummy file: $filename"
        touch "app/src/main/res/raw/$filename"
        echo "# Placeholder audio file for $filename" > "app/src/main/res/raw/$filename"
        echo "# Duration: ${duration}s" >> "app/src/main/res/raw/$filename"
        echo "# Frequency: ${frequency}Hz" >> "app/src/main/res/raw/$filename"
        echo "# Replace with actual audio content" >> "app/src/main/res/raw/$filename"
    fi
}

# Generate campaign-specific audio files
create_test_audio "color_shift.wav" 0.4 800
create_test_audio "robot_conversion.wav" 1.2 600
create_test_audio "security_device_activate.wav" 0.6 1200
create_test_audio "security_device_deactivate.wav" 0.6 400
create_test_audio "hardened_paint_dissolve.wav" 1.0 300
create_test_audio "level_complete.wav" 2.5 440
create_test_audio "campaign_bg.wav" 180 220

echo "Campaign audio files generated!"
echo ""
echo "Note: These are placeholder files for development."
echo "Replace with actual audio content before release."
echo ""
echo "Files created:"
ls -la app/src/main/res/raw/campaign_* app/src/main/res/raw/color_shift.wav app/src/main/res/raw/robot_conversion.wav app/src/main/res/raw/security_device_*.wav app/src/main/res/raw/hardened_paint_dissolve.wav app/src/main/res/raw/level_complete.wav 2>/dev/null || echo "Some files may not have been created (sox not available)" 