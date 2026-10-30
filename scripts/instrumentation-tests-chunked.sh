#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

# Runs the whole instrumentation suite as instrumentation-tests.sh's four chunks, cold-booting the
# emulator before each one so its memory never grows past what a single chunk needs. A failing chunk
# does not stop the run: the remaining chunks still run, and the end lists every chunk that failed.
#
# Usage: ./scripts/instrumentation-tests-chunked.sh
# A running Pixel_7_API_36 emulator is shut down first; the last chunk's emulator is left running.
#
# Every chunk's Gradle run clears the previous chunk's test report, so each chunk's HTML report and
# JUnit XML are copied to app/build/reports/instrumentation-chunks/<chunk>/ as it finishes.

AVD=Pixel_7_API_36
CHUNKS=4 # SHARD_COUNT in instrumentation-tests.sh
REPORT=app/build/reports/androidTests/connected
RESULTS=app/build/outputs/androidTest-results/connected
SAVED=app/build/reports/instrumentation-chunks
EXIT_TIMEOUT=120
BOOT_TIMEOUT=300

# Shuts down the running emulator, if any, and cold-boots a fresh one that logs to $1.
restart_emulator() {
    local log="$1" deadline pid

    adb -e emu kill &> /dev/null || true
    # The new emulator needs the AVD's lock, which the old one keeps until its process has exited.
    deadline=$((SECONDS + EXIT_TIMEOUT))
    while pgrep -f -- "-avd $AVD( |$)" > /dev/null; do
        if ((SECONDS > deadline)); then
            echo "The running $AVD emulator did not shut down." >&2
            return 1
        fi
        sleep 1
    done

    # -no-snapshot: boot from scratch, and don't spend the next shutdown saving a snapshot.
    emulator -avd "$AVD" -no-snapshot &> "$log" &
    pid=$!
    deadline=$((SECONDS + BOOT_TIMEOUT))
    until [[ "$(adb -e shell getprop sys.boot_completed 2> /dev/null)" == "1" ]]; do
        if ! kill -0 "$pid" 2> /dev/null || ((SECONDS > deadline)); then
            echo "$AVD did not boot; see $log" >&2
            return 1
        fi
        sleep 2
    done

    # A cold boot can stop at the lock screen, which keeps the tests' activities from resuming.
    adb -e shell input keyevent KEYCODE_WAKEUP
    adb -e shell wm dismiss-keyguard
}

rm -rf "$SAVED"
FAILED=()

for ((CHUNK = 1; CHUNK <= CHUNKS; CHUNK++)); do
    mkdir -p "$SAVED/$CHUNK"
    echo "==> Cold-booting $AVD for chunk $CHUNK of $CHUNKS"
    if ! restart_emulator "$SAVED/$CHUNK/emulator.log"; then
        FAILED+=("$CHUNK")
        continue
    fi

    # Cleared here too, so a chunk that fails before its tests run can't keep the previous report.
    rm -rf "$REPORT" "$RESULTS"
    if ! ./scripts/instrumentation-tests.sh "$CHUNK"; then
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
