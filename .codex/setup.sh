#!/usr/bin/env bash
set -euo pipefail

# Install JDK
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk unzip curl

# Android SDK installation directory
ANDROID_SDK_ROOT="$HOME/android-sdk"
mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"

# Download and extract Android command line tools
cd /tmp
curl -L -o commandlinetools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
unzip -q commandlinetools.zip -d "$ANDROID_SDK_ROOT/cmdline-tools"
# Move into expected location
mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"

# Install required SDK packages
yes | "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_SDK_ROOT" --licenses
"$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_SDK_ROOT" \
    "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# Create local.properties pointing to the SDK
cat > local.properties <<EOL
sdk.dir=$ANDROID_SDK_ROOT
EOL

# Pre-download Gradle dependencies
./gradlew --no-daemon assembleDebug
