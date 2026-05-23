# Automated Screenshot Generation

This document describes how to generate Play Store screenshots automatically across all 20 supported
languages and both themes (light/dark).

## Overview

**Goal:** Generate 8 screenshots × 20 languages × 2 themes = **320 screenshots**

**Approach:** Compose UI Tests + Bash Orchestration

- Instrumentation tests navigate the app to specific screens/states and capture screenshots
- A bash script loops through locales and themes, configuring the emulator and running tests
- Screenshots are collected into an organized folder structure

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Bash Script                              │
│  (loops through locales/themes, configures emulator, runs tests)│
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Instrumentation Tests                         │
│  (ScreenshotTest.kt - navigates app, captures screenshots)      │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Screenshot Files                           │
│  screenshots/{locale}/{theme}/01_home.png                       │
└─────────────────────────────────────────────────────────────────┘
```

## Components to Implement

### 1. Screenshot Test Class

**Location:** `app/src/androidTest/java/com/mauriciotogneri/fileexplorer/screenshots/ScreenshotTest.kt`

A single test class with one `@Test` method per screenshot. Each test:

1. Launches the app
2. Navigates to the target screen
3. Sets up the required state (selections, dialogs, etc.)
4. Waits for UI to settle
5. Captures and saves the screenshot

```kotlin
@RunWith(AndroidJUnit4::class)
@LargeTest
class ScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var screenshotHelper: ScreenshotHelper

    @Before
    fun setup() {
        screenshotHelper = ScreenshotHelper(
            device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()),
            context = InstrumentationRegistry.getInstrumentation().targetContext
        )
    }

    @Test
    fun screenshot01_home() {
        // Wait for home screen to load
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Recent").assertIsDisplayed()
        
        screenshotHelper.capture("01_home")
    }

    @Test
    fun screenshot02_folderView() {
        // Navigate to Documents folder
        composeTestRule.onNodeWithText("Documents").performClick()
        composeTestRule.waitForIdle()
        
        screenshotHelper.capture("02_folder_view")
    }

    @Test
    fun screenshot03_fileSelected() {
        // Navigate and select files
        composeTestRule.onNodeWithText("Documents").performClick()
        composeTestRule.waitForIdle()
        
        // Long press to select first file
        composeTestRule.onAllNodesWithTag("file_list_item")[0].performLongClick()
        composeTestRule.waitForIdle()
        
        screenshotHelper.capture("03_file_selected")
    }

    // ... more screenshot tests
}
```

### 2. Screenshot Helper Class

**Location:** `app/src/androidTest/java/com/mauriciotogneri/fileexplorer/screenshots/ScreenshotHelper.kt`

Handles the actual screenshot capture and file saving:

```kotlin
class ScreenshotHelper(
    private val device: UiDevice,
    private val context: Context
) {
    fun capture(name: String) {
        // Get locale and theme from test arguments (passed by bash script)
        val arguments = InstrumentationRegistry.getArguments()
        val locale = arguments.getString("locale", "en")
        val theme = arguments.getString("theme", "light")

        // Create directory structure
        val screenshotDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "screenshots/$locale/$theme"
        )
        screenshotDir.mkdirs()

        // Capture screenshot
        val file = File(screenshotDir, "$name.png")
        device.takeScreenshot(file)
    }
}
```

### 3. Bash Orchestration Script

**Location:** `scripts/capture_screenshots.sh`

```bash
#!/bin/bash

set -e

# Configuration
PACKAGE="com.mauriciotogneri.fileexplorer"
TEST_CLASS="com.mauriciotogneri.fileexplorer.screenshots.ScreenshotTest"
OUTPUT_DIR="./screenshots"

# All supported locales (from CLAUDE.md)
LOCALES=(
    "en-US"   # English (default)
    "ar-SA"   # Arabic (RTL)
    "bn-BD"   # Bengali
    "ca-ES"   # Catalan
    "de-DE"   # German
    "el-GR"   # Greek
    "es-ES"   # Spanish
    "fr-FR"   # French
    "hi-IN"   # Hindi
    "in-ID"   # Indonesian
    "it-IT"   # Italian
    "ja-JP"   # Japanese
    "nl-NL"   # Dutch
    "pt-BR"   # Portuguese
    "ro-RO"   # Romanian
    "ru-RU"   # Russian
    "tr-TR"   # Turkish
    "ur-PK"   # Urdu (RTL)
    "vi-VN"   # Vietnamese
    "zh-CN"   # Chinese (Simplified)
)

THEMES=("light" "dark")

# Ensure output directory exists
mkdir -p "$OUTPUT_DIR"

# Build the test APK
echo "Building test APK..."
./gradlew assembleDebug assembleDebugAndroidTest

# Function to change device locale
change_locale() {
    local locale=$1
    local language="${locale%%-*}"
    local country="${locale##*-}"
    
    echo "Setting locale to $locale..."
    adb shell "setprop persist.sys.locale $language-$country; setprop ctl.restart zygote"
    sleep 5  # Wait for device to restart
}

# Function to change device theme
change_theme() {
    local theme=$1
    
    echo "Setting theme to $theme..."
    if [ "$theme" == "dark" ]; then
        adb shell "cmd uimode night yes"
    else
        adb shell "cmd uimode night no"
    fi
    sleep 2
}

# Function to run screenshot tests
run_tests() {
    local locale=$1
    local theme=$2
    
    echo "Running screenshot tests for $locale / $theme..."
    adb shell am instrument -w \
        -e class "$TEST_CLASS" \
        -e locale "$locale" \
        -e theme "$theme" \
        "$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner"
}

# Function to pull screenshots from device
pull_screenshots() {
    local locale=$1
    local theme=$2
    
    echo "Pulling screenshots for $locale / $theme..."
    mkdir -p "$OUTPUT_DIR/$locale/$theme"
    adb pull "/sdcard/Pictures/screenshots/$locale/$theme/." "$OUTPUT_DIR/$locale/$theme/"
}

# Main loop
for locale in "${LOCALES[@]}"; do
    change_locale "$locale"
    
    for theme in "${THEMES[@]}"; do
        change_theme "$theme"
        run_tests "$locale" "$theme"
        pull_screenshots "$locale" "$theme"
    done
done

echo "Screenshots saved to $OUTPUT_DIR"
echo "Total: $(find "$OUTPUT_DIR" -name "*.png" | wc -l) screenshots"
```

## Output Structure

```
screenshots/
├── en-US/
│   ├── light/
│   │   ├── 01_home.png
│   │   ├── 02_folder_view.png
│   │   ├── 03_file_selected.png
│   │   └── ...
│   └── dark/
│       ├── 01_home.png
│       └── ...
├── ar-SA/
│   ├── light/
│   └── dark/
├── de-DE/
│   ├── light/
│   └── dark/
└── ... (20 locales total)
```

## Implementation Steps

### Phase 1: Setup (Prerequisites)

1. [ ] Add UiAutomator dependency to `build.gradle.kts`:
   ```kotlin
   androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
   ```

2. [ ] Add storage permission for saving screenshots (test manifest or runtime):
   ```xml
   <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
   ```

3. [ ] Create the `scripts/` directory

### Phase 2: Screenshot Helper

4. [ ] Create `ScreenshotHelper.kt` with:
   - Screenshot capture using `UiDevice.takeScreenshot()`
   - Directory creation logic
   - Locale/theme parameter reading from instrumentation arguments

### Phase 3: Screenshot Tests

5. [ ] Create `ScreenshotTest.kt` with:
   - One `@Test` method per screenshot
   - Navigation logic for each screen
   - State setup (selections, dialogs, etc.)
   - Screenshot capture calls

6. [ ] Define the 8 screenshots (provide descriptions when ready):
   - `screenshot01_???` - [description]
   - `screenshot02_???` - [description]
   - `screenshot03_???` - [description]
   - `screenshot04_???` - [description]
   - `screenshot05_???` - [description]
   - `screenshot06_???` - [description]
   - `screenshot07_???` - [description]
   - `screenshot08_???` - [description]

### Phase 4: Bash Script

7. [ ] Create `scripts/capture_screenshots.sh`
8. [ ] Make it executable: `chmod +x scripts/capture_screenshots.sh`
9. [ ] Test with a single locale/theme first

### Phase 5: Testing & Refinement

10. [ ] Run for English/light only to verify navigation works
11. [ ] Run for one RTL language (Arabic) to verify RTL handling
12. [ ] Full run across all locales/themes
13. [ ] Review and adjust timing/waits as needed

## Running the Screenshots

```bash
# Ensure emulator is running
emulator -avd <your_avd_name> &

# Wait for device to boot
adb wait-for-device

# Run the screenshot script
./scripts/capture_screenshots.sh
```

## Estimated Time

- Per screenshot: ~5 seconds (navigation + capture)
- Per locale/theme combination: ~40 seconds (8 screenshots)
- Per locale: ~80 seconds (2 themes)
- Total: ~27 minutes (20 locales × 80 seconds)

## Troubleshooting

### Locale not changing
Some emulators require a reboot for locale changes. The script includes a zygote restart, but if
issues persist, try:
```bash
adb shell "settings put system system_locales $locale"
adb reboot
```

### Screenshots blank or timing issues
Increase wait times in tests:
```kotlin
composeTestRule.waitForIdle()
Thread.sleep(500)  // Additional wait if needed
```

### Permission denied when saving
Ensure the test APK has storage permissions. For API 30+, may need to use app-specific storage
or grant MANAGE_EXTERNAL_STORAGE.

## Adding New Screenshots

1. Add a new `@Test` method in `ScreenshotTest.kt`
2. Implement navigation to the target screen/state
3. Call `screenshotHelper.capture("XX_description")`
4. Re-run the script

## Notes

- The emulator should have sample files/folders already set up for realistic screenshots
- RTL languages (Arabic, Urdu) will automatically mirror the UI if `android:supportsRtl="true"` is
  set in the manifest (it is)
- Screenshots are numbered (01, 02, etc.) to maintain consistent ordering across locales
