#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CORE_DIR="$APP_DIR/rust/grape_core"
JNILIBS_DIR="$APP_DIR/app/mobile/app/src/main/jniLibs"

# Configure NDK path
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-/home/sugarcube/Android/Sdk/ndk/28.2.13676358}"

echo "Using ANDROID_NDK_HOME: $ANDROID_NDK_HOME"

if [ ! -d "$ANDROID_NDK_HOME" ]; then
    echo "Error: ANDROID_NDK_HOME directory does not exist at $ANDROID_NDK_HOME" >&2
    exit 1
fi

# Target architectures mapping
declare -A TARGETS=(
    ["arm64-v8a"]="aarch64-linux-android"
    ["armeabi-v7a"]="armv7-linux-androideabi"
    ["x86_64"]="x86_64-linux-android"
)

# Build each target
for abi in "${!TARGETS[@]}"; do
    rust_target="${TARGETS[$abi]}"
    echo "=================================================="
    echo "Building grape_core for ABI: $abi ($rust_target)"
    echo "=================================================="
    
    # Run cargo ndk compilation
    (
        cd "$CORE_DIR"
        cargo ndk -t "$abi" build --lib --release -j 1
    )
    
    # Create target directory for .so
    mkdir -p "$JNILIBS_DIR/$abi"
    
    # Locate built library
    # Cargo NDK places built products in target/<rust-target>/release/libgrape_core.so
    so_source="$APP_DIR/rust/target/$rust_target/release/libgrape_core.so"
    if [ ! -f "$so_source" ]; then
        # Check standard cargo target directory fallback if custom target dir isn't used
        so_source="$CORE_DIR/target/$rust_target/release/libgrape_core.so"
    fi
    
    if [ -f "$so_source" ]; then
        cp "$so_source" "$JNILIBS_DIR/$abi/libgrape_core.so"
        echo "Successfully copied libgrape_core.so for $abi"
    else
        echo "Error: Could not locate built libgrape_core.so at $so_source" >&2
        exit 1
    fi
done

echo "Android Rust compilation completed successfully."
