#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

# Runs the whole instrumentation suite as instrumentation-tests-chunk.sh's six chunks, cold-booting
# the emulator before each one so its memory never grows past what a single chunk needs. A failing
# chunk does not stop the run: the remaining chunks still run, and the end lists every chunk that
# failed.
#
# Usage: ./scripts/instrumentation-tests.sh
# A running Pixel_7_API_36 emulator is shut down first; the last chunk's emulator is left running.
#
# Every chunk's Gradle run clears the previous chunk's test report, so each chunk's HTML report and
# JUnit XML are copied to app/build/reports/instrumentation-chunks/<chunk>/ as it finishes.

AVD=Pixel_7_API_36
CHUNKS=6 # SHARD_COUNT in instrumentation-tests-chunk.sh
REPORT=app/build/reports/androidTests/connected
RESULTS=app/build/outputs/androidTest-results/connected
SAVED=app/build/reports/instrumentation-chunks

rm -rf "$SAVED"
FAILED=()

for ((CHUNK = 1; CHUNK <= CHUNKS; CHUNK++)); do
    mkdir -p "$SAVED/$CHUNK"
    echo "==> Cold-booting $AVD for chunk $CHUNK of $CHUNKS"
    if ! ./scripts/boot-emulator.sh "$AVD" "$SAVED/$CHUNK/emulator.log"; then
        FAILED+=("$CHUNK")
        continue
    fi

    # Cleared here too, so a chunk that fails before its tests run can't keep the previous report.
    rm -rf "$REPORT" "$RESULTS"
    if ! ./scripts/instrumentation-tests-chunk.sh "$CHUNK"; then
        FAILED+=("$CHUNK")
    fi
    if [[ -d "$REPORT" ]]; then
        cp -r "$REPORT" "$SAVED/$CHUNK/report"
    fi
    if [[ -d "$RESULTS" ]]; then
        cp -r "$RESULTS" "$SAVED/$CHUNK/results"
    fi
done

if ((${#FAILED[@]} > 0)); then
    echo "==> Failed chunks: ${FAILED[*]} (reports in $SAVED)" >&2
    exit 1
fi
echo "==> All $CHUNKS chunks passed (reports in $SAVED)"
