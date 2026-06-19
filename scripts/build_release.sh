#!/usr/bin/env bash
set -euo pipefail

# Find script directory and project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Extract version from latest_version.json using standard grep/cut
VERSION=$(grep -o '"version": "[^"]*' "$PROJECT_ROOT/latest_version.json" | cut -d'"' -f4)

echo "=================================================="
echo "Starting Release Build for Grape v$VERSION"
echo "=================================================="

# 1. Build Android Rust native libraries
echo "Step 1: Compiling Rust Core native libraries via NDK..."
"$PROJECT_ROOT/scripts/build_android.sh"

# 2. Build Android release APK
echo "Step 2: Building Release Android APK via Gradle..."
cd "$PROJECT_ROOT/app/mobile"
./gradlew clean assembleRelease

# 3. Verify built APK output
APK_PATH="$PROJECT_ROOT/app/mobile/app/build/outputs/apk/release/app-release.apk"
DIST_DIR="$PROJECT_ROOT/dist"
mkdir -p "$DIST_DIR"

if [ -f "$APK_PATH" ]; then
    echo "=================================================="
    echo "SUCCESS: Release APK compiled successfully!"
    echo "Location: $APK_PATH"
    echo "Size: $(du -sh "$APK_PATH" | cut -f1)"
    echo "Copying to dist folder..."
    cp "$APK_PATH" "$DIST_DIR/Grape_${VERSION}.apk"
    echo "Output saved to: $DIST_DIR/Grape_${VERSION}.apk"
    echo "=================================================="
else
    echo "Error: Release APK not found at expected location: $APK_PATH" >&2
    exit 1
fi
