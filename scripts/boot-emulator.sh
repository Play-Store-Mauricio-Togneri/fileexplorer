#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

# Cold-boots an emulator and waits until tests can run on it: booted, settled, awake and past the
# lock screen. A running emulator is shut down first. The new one keeps running after this script
# ends; stop it with `adb -e emu kill`.
#
# Usage: ./scripts/boot-emulator.sh [AVD] [LOG]
# AVD defaults to Pixel_7_API_36, the project's emulator. LOG, where the emulator writes its output,
# defaults to app/build/emulator.log.

AVD="${1:-Pixel_7_API_36}"
LOG="${2:-app/build/emulator.log}"
EXIT_TIMEOUT=120
BOOT_TIMEOUT=300
# Seconds to wait after boot completes: the system keeps compiling apps in the background for a
# while, and tests started meanwhile failed with their activities never reaching the foreground.
SETTLE_TIME=60

adb -e emu kill &> /dev/null || true
# The new emulator needs the AVD's lock, which the old one keeps until its process has exited.
deadline=$((SECONDS + EXIT_TIMEOUT))
while pgrep -f -- "-avd $AVD( |$)" > /dev/null; do
    if ((SECONDS > deadline)); then
        echo "The running $AVD emulator did not shut down." >&2
        exit 1
    fi
    sleep 1
done

mkdir -p "$(dirname "$LOG")"
# -no-snapshot: boot from scratch, and don't spend the next shutdown saving a snapshot.
# nohup: the emulator outlives this script.
nohup emulator -avd "$AVD" -no-snapshot &> "$LOG" &
pid=$!
deadline=$((SECONDS + BOOT_TIMEOUT))
until [[ "$(adb -e shell getprop sys.boot_completed 2> /dev/null)" == "1" ]]; do
    if ! kill -0 "$pid" 2> /dev/null || ((SECONDS > deadline)); then
        echo "$AVD did not boot; see $LOG" >&2
        exit 1
    fi
    sleep 2
done
sleep "$SETTLE_TIME"

# A cold boot can stop at the lock screen, which keeps the tests' activities from resuming.
adb -e shell input keyevent KEYCODE_WAKEUP
adb -e shell wm dismiss-keyguard
