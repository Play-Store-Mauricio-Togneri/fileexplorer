#!/usr/bin/env bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

APP_ID=$(jq -r '.client[0].client_info.mobilesdk_app_id' app/google-services.json)
APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"

echo "Building release APK..."
./gradlew assembleRelease

echo "Uploading to Firebase App Distribution..."
firebase appdistribution:distribute "$APK_PATH" --app "$APP_ID" --groups "Testers"

echo "Distribution complete!"
