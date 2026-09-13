#!/usr/bin/env bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Structural guards first: they are instant and catch the class of test that would
# otherwise run green while asserting nothing (see scripts/check-tests.sh).
"$SCRIPT_DIR/check-tests.sh"

./gradlew clean testDebugUnitTest --rerun-tasks

./gradlew connectedDebugAndroidTest --rerun-tasks
