#!/usr/bin/env bash

set -e

./gradlew clean testDebugUnitTest 
./gradlew testDebugUnitTest --rerun-tasks