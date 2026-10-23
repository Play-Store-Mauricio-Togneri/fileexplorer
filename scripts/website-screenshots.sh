#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

# Captures the website's screenshots on the running emulator: the WebsiteScreenshots instrumentation
# class renders seven of the app's screens in all 20 languages, once in light mode and once in dark,
# and this converts each into website/images/<language>/<screen>-<theme>.webp, 600px wide.
#
# The status bar is Android's demo mode (12:00, full Wi-Fi and battery) for the whole run; the
# emulator's dark mode and demo mode are put back afterwards, and the app goes back to the system's
# language, even when the capture dies before it can reset that itself.

APP=com.mauriciotogneri.fileexplorer
RUNNER="$APP.test/androidx.test.runner.AndroidJUnitRunner"
CLASS="$APP.website.WebsiteScreenshots"
DEVICE_OUTPUT="/sdcard/Android/data/$APP/files/website-screenshots"
LOCAL_OUTPUT="$(mktemp -d)"
WIDTH=600

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
    rm -rf "$LOCAL_OUTPUT"
}
trap restore EXIT

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

for night in no yes; do
    echo "==> Dark mode: $night"
    adb shell cmd uimode night "$night" > /dev/null
    sleep 2
    RESULT="$(adb shell am instrument -w -e class "$CLASS" "$RUNNER")"
    if ! grep -q '^OK (' <<< "$RESULT"; then
        echo "$RESULT" | grep -E 'Error in|FAILURES|Tests run|Exception' | head -40 >&2
        exit 1
    fi
    grep '^OK (' <<< "$RESULT"
done

adb pull "$DEVICE_OUTPUT" "$LOCAL_OUTPUT" > /dev/null

python3 - "$LOCAL_OUTPUT/website-screenshots" website/images "$WIDTH" <<'EOF'
import pathlib
import sys

from PIL import Image

source, target, width = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), int(sys.argv[3])
count = 0
for png in sorted(source.glob("*/*.png")):
    with Image.open(png) as image:
        height = round(image.height * width / image.width)
        resized = image.convert("RGB").resize((width, height), Image.LANCZOS)
    webp = target / png.parent.name / (png.stem + ".webp")
    webp.parent.mkdir(parents=True, exist_ok=True)
    resized.save(webp, "WEBP", quality=82, method=6)
    count += 1
print(f"Wrote {count} images to {target}")
EOF
