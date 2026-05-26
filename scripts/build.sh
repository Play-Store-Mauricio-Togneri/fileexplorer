#!/usr/bin/env bash

set -e

cd "$(dirname "$0")/.."

echo "Cleaning project..."
./gradlew clean

echo "Building release AAB..."
./gradlew bundleRelease

AAB_PATH="app/build/outputs/bundle/release/app-release.aab"

if [[ -f "$AAB_PATH" ]]; then
    echo "Release AAB created: $AAB_PATH"
    ls -lh "$AAB_PATH"
else
    echo "Error: AAB file not found at $AAB_PATH"
    exit 1
fi
