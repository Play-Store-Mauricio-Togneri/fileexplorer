#!/usr/bin/env bash

set -e

./gradlew clean testDebugUnitTest --rerun-tasks

./gradlew connectedDebugAndroidTest --rerun-tasks