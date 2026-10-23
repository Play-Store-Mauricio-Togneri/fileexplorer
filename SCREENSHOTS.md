# Capturing app screenshots

What was learned producing the website's screenshots (October 2026), written for whoever produces
the next set, such as the Play Store listing's. It records what exists, how it works, every problem
hit on the way and how it was solved, and the content decisions the owner made.

## What exists

The repository holds a working capture tool and its output (first committed in `f873168`; the
`drives` screen was added after):

| Path                                                                                      | What it is                                                                        |
|-------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| `app/src/androidTest/java/com/mauriciotogneri/fileexplorer/website/WebsiteScreenshots.kt` | Instrumentation class that renders 8 screens with made-up data, per language      |
| `scripts/website-screenshots.sh`                                                          | Runs the class once per theme on the emulator, pulls the PNGs, converts them      |
| `app/src/androidTest/assets/website/`                                                     | Fixtures: 10 photos (`photos/`) and `Summer Days at Mount Shasta.pdf`             |
| `website/images/<code>/<screen>-<theme>.webp`                                             | Output: 20 languages × 8 screens × light/dark = 320 images, 600px wide            |

The plan was to delete the tool in a later commit. If it is gone, restore it from the commit just
before the one that deleted it, which has the latest version. The search covers every branch:
release branches reach `main` as one squashed commit, so `main` never sees the tool being added or
deleted, and the branch that held it (`release/2.8.0`) has to still exist.

```bash
deleted=$(git log --all -1 --format=%h --diff-filter=D -- scripts/website-screenshots.sh)
git checkout "$deleted^" -- app/src/androidTest/java/com/mauriciotogneri/fileexplorer/website \
    scripts/website-screenshots.sh app/src/androidTest/assets/website
```

The eight screens: `home`, `drives` (the home screen with no favorites, so it opens on the
drives), `select` (folder in multi-select), `actions` (a file's action sheet), `search` (results
with filter chips), `pdf` (PDF viewer with a search highlighted), `analyzer` (storage analyzer
results) and `settings`.

## How the capture works

- **Real screens, made-up data.** Each test hosts a production composable (`HomeScreen`,
  `FolderScreen`, `SearchScreen`, `PdfViewerScreen`, `AnalyzerScreen`, `SettingsScreen`) in the
  compose test rule's `ComponentActivity`, with view models built by hand from fake sources: the
  existing `testutil` fakes (`InMemoryPreferencesSource`, `SeededRecentFiles`, `SeededFavorites`,
  `NoExternalChanges`) plus small ones in the class (storages, location sizes, an analyzer
  repository that returns one finished scan). No screen needed a production change.
- **Fixture files are real files.** They're copied from the test APK's assets into the app's
  external files directory (`website-fixtures/`), which a fake `StorageSource` presents as internal
  storage, SD card and USB drive. Thumbnails, folder listing, search and the PDF viewer then work
  unmodified. Photos are padded with zero bytes after the image data to look like camera-sized
  files, since decoders ignore trailing bytes. Each file's modified date comes from its name.
- **Language.** An outer JUnit rule switches the app through Android's per-app language
  (`LocaleManager.setApplicationLocales`, API 33+) before the activity starts, then waits until the
  process configuration carries it and asserts it did. View models, number and date formats and
  right-to-left layout then follow exactly as for a real user. The class is `Parameterized` over the
  site's 20 codes, mapped to Android tags (`id`, `pt-BR`, `zh-CN`, `en-US`). `@AfterClass` resets
  the app to the system language.
- **Theme.** The script flips the emulator with `adb shell cmd uimode night no|yes` between two runs
  of the class. `FileExplorerTheme` follows the system, and the class names each file `-light` or
  `-dark` from `isNightModeActive`.
- **Window.** Edge to edge, as the app's activities are, and in touch mode (see the problems
  section).
- **Capture.** `UiAutomation.takeScreenshot()` grabs the whole screen, status bar and gesture bar
  included, as a 1080×2400 PNG. It's written to
  `/sdcard/Android/data/com.mauriciotogneri.fileexplorer/files/website-screenshots/<code>/`, then
  pulled with `adb pull` and converted with Pillow (resize to 600px, WebP quality 82).
- **Status bar.** Android's demo mode, set by the script:
  `settings put global sysui_demo_allowed 1`, then broadcasts `enter`, `clock -e hhmm 1200`,
  `battery -e level 100 -e plugged false`, `network -e wifi show -e level 4 -e fully true`,
  `network -e mobile hide` and `notifications -e visible false`. `exit` is sent at the end.
- **Running it.** Start the `Pixel_7_API_36` emulator, then run `./scripts/website-screenshots.sh`.
  It builds and installs the app, creates the public folders the home screen's locations need, sets
  demo mode, runs both themes, converts the images, and restores dark mode, demo mode and the app's
  language. With seven screens (140 per theme), each theme took about 10 minutes and the whole run
  about 25; the eighth screen adds roughly a seventh to that. Pass a screen name to capture only
  that one, in every language and both themes (`./scripts/website-screenshots.sh drives` took about
  5 minutes).

## Problems hit, and the fixes

- **Running one screen across all languages.** `-e class …WebsiteScreenshots#home` runs 0 tests,
  because parameterized names are `home[en]`. Use a regex, quoted as a single string for the device
  shell:
  `adb shell "am instrument -w -e class com.mauriciotogneri.fileexplorer.website.WebsiteScreenshots -e tests_regex '.*#home\\[.*' com.mauriciotogneri.fileexplorer.test/androidx.test.runner.AndroidJUnitRunner"`.
  Passed unquoted, the regex was mangled and every test ran. The script's screen argument does
  this for you.
- **Analyzer stuck on the drive list.** With more than one drive, none is selected, so "Analyze"
  does nothing. Tap the internal storage row first.
- **Folder title showed the full path.** Pass the folder name both as the view model's
  `initialTitle` and as the screen's `title`.
- **A "Drag handle" tooltip over the action sheet.** The test's clicks aren't touches, so the window
  leaves touch mode and the sheet's handle takes focus. Call
  `Instrumentation.setInTouchMode(true)` before rendering.
- **Feature-discovery dots.** A fresh preferences source shows every badge. Make
  `dismissedBadgeVersion` return `Int.MAX_VALUE`.
- **Home screen too tall.** With the default section order, the drives fall below the fold on a
  914dp-tall screen. Lead with favorites, then storages, then locations, and turn recent files off.
- **Keyboard covering search results.** After typing, `performImeAction()` and then Espresso's
  `closeSoftKeyboard()`. The text cursor stays visible, which looks natural.
- **Status bar showed "3G" and a Wi-Fi warning mark.** `datatype none` didn't hide the mobile
  network type; `mobile hide` and `fully true` do.
- **Status bar stays in English.** The emulator is a Google Play image, with no root, so only the
  app's language can change, not the system's. The clock and icons stay in English left-to-right on
  every language, which the owner accepted. A fully translated phone would need a second, rootable
  emulator (a Google APIs image).
- **Missing location cards.** The home screen lists only locations whose public folder exists, so
  the script creates them with `adb shell mkdir -p`.
- **Folder names in the path bar stay English.** "Pictures" is the folder's real name on disk, as on
  every Android phone. Only the storage name ("Internal Storage") is translated.
- **The app stays in a language if the run dies.** A crash or killed process skips `@AfterClass`, so
  the script's exit trap also runs `adb shell cmd locale set-app-locales <package>`. With no
  `--locales` argument, it clears the setting.
- **The class joins the regular instrumentation suite while it exists** (160 extra cases). Don't run
  the full suite on a commit that contains it.
- **Images and timing.** Thumbnails decode outside what Compose's idle wait sees, so each capture
  waits an extra 1.5 seconds.
- **Website frame.** The site's phone frame in `website/styles.css` uses the screenshots' 1080×2400
  shape, with the drawn camera dot at 2.8% of the screen height (the status bar's middle). A
  different capture size means updating both.

## Content decisions the owner made

- **Captured once, not every release.** The owner accepts that screenshots drift as the app changes.
- **Varied, everyday photos rather than all mountains**, in public domain or CC0 only, so no credit
  line is needed. Licenses were checked through the Wikimedia Commons API (`extmetadata`), never
  from memory. People appear only from behind, because a free license doesn't waive the pictured
  person's rights on a promotional page. Each photo was checked as a small square crop, since that's
  how thumbnails show it. Photos were scaled to 1280px before committing.

  | Fixture | Wikimedia Commons file | License | Author |
    |---|---|---|---|
  | 2026-06-06 tulips.jpg | Big_red_tulips_with_small_hoverfly.jpg | CC0 | W.carter |
  | 2026-06-14 hike.jpg | Hiker_on_the_Fortynine_Palms_Trail_(50699662533).jpg | Public domain (NPS) | NPS / Emily Hassell |
  | 2026-06-21 lake.jpg | Lake_Minnewanka,_Canada.jpg | CC0 | Vlunyak |
  | 2026-06-28 new bike.jpg | VillyCustomsBeachCruiserBicycle.jpg | Public domain | Adelaidasofia |
  | 2026-07-05 dog.jpg | Dog_at_Nørre_Vorupør_Strand.jpg | CC0 | W.carter |
  | 2026-07-12 cat.jpg | Young_tabby_cat_keeping_watch.jpg | CC0 | W.carter |
  | 2026-07-19 breakfast.jpg | Full_English_breakfast_-_Bushy_Park,_UK.jpg | CC0 | Daderot |
  | 2026-07-26 beach.jpg | 2022_03_Nissi_beach_1.jpg | CC0 | Qasinka |
  | 2026-08-02 city walk.jpg | Carabobo_Street_in_Maracaibo.jpg | CC0 | Wilfredor |
  | 2026-08-09 sunset.jpg | Juan_Griego_Sunset.jpg | CC0 | Wilfredor |

- **File names dated, in a "Summer 2026" folder**, so the search screenshot searches "2026" and
  fills the screen with results. Every language shows the same file names.
- **The PDF** is the opening of John Muir's *Steep Trails*, chapter III, "Summer Days at Mount
  Shasta" (public domain worldwide; Muir died in 1914). It was typeset as two A5 pages with
  reportlab, from the Project Gutenberg text (ebook #326) without Gutenberg's header. The viewer
  searches "Shasta": 8 matches, counting the title.
- **Made-up figures:** 128 GB internal storage with 79.9 GB free, a 64 GB SD card, a 32 GB USB
  drive, phone-like location sizes, and analyzer categories totalling 48.1 GB used.
- **Clean English status bar** in every language: 12:00, full Wi-Fi and battery, no mobile signal,
  no notifications.
- **Site sections:** the website's last section, "Made for Everyone", shows `drives`, so it doesn't
  repeat the first section's screenshot. The owner may still choose a better one later.

## Notes for Play Store screenshots

Not yet done, and the Play rules below are from memory, so check them against the current Play
Console help before relying on them.

- **Format and size:** Play takes JPEG or 24-bit PNG (no transparency), each side 320–3840px, with
  the long side at most twice the short side. The captures are 1080×2400, a ratio of 2.22, so as far
  as I know Play would reject them unchanged. Either crop to 1080×2160 (exactly 2:1) or place them
  on a 9:16 canvas (1080×1920) with a frame and caption.
- **Keep the full-size PNGs.** The website's 600px WebP files are too small for Play. Re-run the
  capture, and change the script's conversion step to keep (or crop and compose) the full-resolution
  PNGs.
- **Languages:** Play listings take screenshots per language, but Play's language codes differ from
  the site's (for example `es-ES` or `es-419`, `pt-BR`, `zh-CN`, `id`). Map each code to what the
  listing uses.
- **Captions** in 20 languages would need translating. The website's feature titles (for example
  "Browse with Ease", "Find Anything Fast") already exist in all 20 home pages and could be reused.
- **Tablets:** the only emulator is a phone, so tablet screenshots would need a tablet emulator and
  a check that the screens lay out well there.
