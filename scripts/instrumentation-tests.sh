#!/usr/bin/env bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

#./scripts/instrumentation-tests.sh 1 (also does the fresh build)
# RESTART EMULATOR
#./scripts/instrumentation-tests.sh 2
# RESTART EMULATOR
#./scripts/instrumentation-tests.sh 3
# RESTART EMULATOR
#./scripts/instrumentation-tests.sh 4

# The instrumentation suite runs for ~40 minutes and the emulator's memory grows with it, until the
# host can run out and kill it mid-run. Passing a chunk number (1-4) runs a quarter of the suite;
# restart the emulator between chunks so each starts from a fresh process. No argument runs it all.
SHARD_COUNT=4
SHARD="$1"

if [[ -n "$SHARD" ]] && ! [[ "$SHARD" =~ ^[1-9][0-9]*$ && "$SHARD" -le "$SHARD_COUNT" ]]; then
    echo "Usage: $0 [chunk 1-$SHARD_COUNT]" >&2
    exit 1
fi

# Structural guards first: they are instant and catch the class of test that would
# otherwise run green while asserting nothing (see scripts/check-tests.sh).
"$SCRIPT_DIR/check-tests.sh"

# No chunk number: run the whole suite in one go.
if [[ -z "$SHARD" ]]; then
    ./gradlew -w connectedDebugAndroidTest --rerun-tasks
else
    echo "==> Instrumentation chunk $SHARD of $SHARD_COUNT"
    # The first chunk rebuilds everything; the rest force only the test task to run again
    # (--rerun), reusing that build.
    RERUN="--rerun"
    if [[ "$SHARD" -eq 1 ]]; then
        RERUN="--rerun-tasks"
    fi
    ./gradlew -w connectedDebugAndroidTest "$RERUN" \
        -Pandroid.testInstrumentationRunnerArguments.numShards="$SHARD_COUNT" \
        -Pandroid.testInstrumentationRunnerArguments.shardIndex="$((SHARD - 1))"
fi
