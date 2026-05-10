#!/usr/bin/env bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

#command -v jq >/dev/null 2>&1 || { echo "Error: jq is required but not installed"; exit 1; }
#command -v firebase >/dev/null 2>&1 || { echo "Error: firebase CLI is required but not installed"; exit 1; }

#[ -f "app/google-services.json" ] || { echo "Error: app/google-services.json not found"; exit 1; }

APP_ID=$(jq -r '.client[0].client_info.mobilesdk_app_id' app/google-services.json)
APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"

echo "Building release APK..."
./gradlew assembleRelease

#[ -f "$APK_PATH" ] || { echo "Error: APK not found at $APK_PATH"; exit 1; }

echo "Uploading to Firebase App Distribution..."
firebase appdistribution:distribute "$APK_PATH" --app "$APP_ID" --groups "Testers"

echo "Distribution complete!"
