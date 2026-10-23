#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

# Captures the Play Store listing's phone screenshots on the running emulator: the WebsiteScreenshots
# instrumentation class renders the app's screens at full resolution, and play_screenshots.py lays
# each one out with its caption as build/play-screenshots/<play language>/<slot>-<screen>.png.
#
# Usage: ./scripts/play-screenshots.sh [language...]
# Languages are the website's codes (en, de, ar, ...) that play_screenshots.py has captions for;
# English alone when none is given.
#
# The status bar is Android's demo mode (12:00, full Wi-Fi and battery) for the whole run; the
# emulator's dark mode and demo mode are put back afterwards, and the app goes back to the system's
# language, even when the capture dies before it can reset that itself.

APP=com.mauriciotogneri.fileexplorer
RUNNER="$APP.test/androidx.test.runner.AndroidJUnitRunner"
CLASS="$APP.website.WebsiteScreenshots"
DEVICE_OUTPUT="/sdcard/Android/data/$APP/files/website-screenshots"
OUTPUT=build/play-screenshots
LANGUAGES=("${@:-en}")

# Parameterized tests are named like "home[en]", so languages are picked by pattern. The whole
# command goes to the device as one string, or the pattern's brackets do not survive the trip.
PATTERN="$(IFS='|'; echo "${LANGUAGES[*]}")"

# A language without captions fails here, not after minutes of capturing.
python3 scripts/play_screenshots.py --check "${LANGUAGES[@]}"

if [[ "$(adb devices | grep -c 'device$')" -ne 1 ]]; then
    echo "Start exactly one emulator first." >&2
    exit 1
fi

ORIGINAL_NIGHT_MODE="$(adb shell cmd uimode night | awk '{print $NF}')"

demo() {
    adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null
}

restore() {
    demo exit
    adb shell cmd uimode night "$ORIGINAL_NIGHT_MODE" > /dev/null
    # No locales: the app follows the system's language again.
    adb shell cmd locale set-app-locales "$APP"
}
trap restore EXIT

capture() {
    local night="$1" regex="$2"
    echo "==> Dark mode: $night"
    adb shell cmd uimode night "$night" > /dev/null
    sleep 2
    RESULT="$(adb shell "am instrument -w -e class $CLASS -e tests_regex '$regex' $RUNNER")"
    if ! grep -q '^OK (' <<< "$RESULT"; then
        echo "$RESULT" | grep -E 'Error in|FAILURES|Tests run|Exception' | head -40 >&2
        exit 1
    fi
    grep '^OK (' <<< "$RESULT"
}

./gradlew -w --console=plain installDebug installDebugAndroidTest

# The home screen lists only the locations whose folder exists.
adb shell mkdir -p /sdcard/Download /sdcard/DCIM /sdcard/Pictures/Screenshots /sdcard/Movies \
    /sdcard/Music /sdcard/Documents /sdcard/Podcasts
adb shell rm -rf "$DEVICE_OUTPUT"

adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put global sysui_demo_allowed 1
demo enter
demo clock -e hhmm 1200
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e fully true
demo network -e mobile hide
demo notifications -e visible false

# Every screen in light mode; only the home screen is shown in dark.
capture no ".*\\[($PATTERN)\\]"
capture yes ".*#home\\[($PATTERN)\\]"

rm -rf "$OUTPUT/raw"
mkdir -p "$OUTPUT/raw"
adb pull "$DEVICE_OUTPUT/." "$OUTPUT/raw" > /dev/null

python3 scripts/play_screenshots.py "$OUTPUT/raw" "$OUTPUT" "${LANGUAGES[@]}"
