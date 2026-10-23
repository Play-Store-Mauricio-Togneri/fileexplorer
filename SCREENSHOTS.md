# Capturing app screenshots

What was learned producing the website's screenshots and then the Play Store listing's (October
2026), written for whoever produces the next set. It records what exists, how it works, every
problem hit on the way and how it was solved, and the content decisions the owner made. The Play
Store set reuses the website's capture, so its own section only adds what differs.

## What exists

The repository holds a working capture tool and its output (first committed in `f873168`; the
`drives` screen was added after):

| Path                                                                                      | What it is                                                                        |
|-------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| `app/src/androidTest/java/com/mauriciotogneri/fileexplorer/website/WebsiteScreenshots.kt` | Instrumentation class that renders 8 screens with made-up data, per language      |
| `scripts/website-screenshots.sh`                                                          | Runs the class once per theme on the emulator, pulls the PNGs, converts them      |
| `app/src/androidTest/assets/website/`                                                     | Fixtures: 10 photos (`photos/`) and `Summer Days at Mount Shasta.pdf`             |
| `website/images/<code>/<screen>-<theme>.webp`                                             | Output: 20 languages × 8 screens × light/dark = 320 images, 600px wide            |
| `scripts/play-screenshots.sh`                                                             | Runs the same class for the Play Store set, then lays the captures out            |
| `scripts/play_screenshots.py`, `scripts/play_screenshots.html`                            | Captions, fonts and layout; renders each Play screenshot with headless Chrome     |
| `build/play-screenshots/<Play code>/<slot>-<screen>.png`                                  | Play output, not committed: 20 languages × 8 slots = 160 images, 1080×1920        |
| `scripts/play_upload.py`                                                                  | Replaces the listing's phone screenshots with that output, every language at once |

The plan was to delete the tool in a later commit. If it is gone, restore it from the commit just
before the one that deleted it, which has the latest version. The search covers every branch:
release branches reach `main` as one squashed commit, so `main` never sees the tool being added or
deleted, and the branch that held it (`release/2.8.0`) has to still exist. The Play scripts depend
on the same class, so they go and come back with it.

```bash
deleted=$(git log --all -1 --format=%h --diff-filter=D -- scripts/website-screenshots.sh)
git checkout "$deleted^" -- app/src/androidTest/java/com/mauriciotogneri/fileexplorer/website \
    scripts/website-screenshots.sh app/src/androidTest/assets/website \
    scripts/play-screenshots.sh scripts/play_screenshots.py scripts/play_screenshots.html \
    scripts/play_upload.py
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

## Play Store screenshots

The listing's phone screenshots (October 2026): 8 per language, a caption over the app's screen,
in all 20 languages.

### How they're made

- **Running it.** Start the `Pixel_7_API_36` emulator, then run
  `./scripts/play-screenshots.sh en ar bn ca de el es fr hi id it ja nl pt ro ru tr ur vi zh` (the
  website's codes; English alone when none is given). It checks every language has captions before
  anything else, then installs the app, sets demo mode, runs every screen of the class in light
  mode and `home` in dark, pulls the full-size captures into `build/play-screenshots/raw/<code>/`,
  and renders them. All 20 languages took about 10 minutes to build and capture and 4 more to
  render.
- **Captions or layout only.** Re-render from the captures already pulled, about 1.5 seconds an
  image: `python3 scripts/play_screenshots.py build/play-screenshots/raw build/play-screenshots en de`.
- **Rendering.** `play_screenshots.py` fills `play_screenshots.html` once per slot and headless
  Chrome renders it (`--headless=new`, a 1080×1920 window, `--virtual-time-budget` so the web fonts
  arrive) into a PNG without transparency. A browser rather than Pillow, so Arabic, Urdu, Hindi and
  Bengali are shaped correctly and captions wrap and shrink with CSS and a few lines of script.
- **Layout.** A 320px caption band, then the capture scaled to 688×1530 with rounded corners and a
  shadow, 60px above the bottom edge. The caption is Bricolage Grotesque 800 at 80px, centred,
  balanced over two lines at most; a longer one shrinks 2px at a time, down to 48px.
- **Output.** `build/` is ignored by git, so neither the captures nor the 160 images are committed.

### Uploading them

`scripts/play_upload.py` replaces every listing language's phone screenshots with its folder's
eight, in slot order, as one change through Google Play's publishing API. The listing has exactly
the 20 languages the folders are named after, with no regional variants. It needs nothing beyond
`requests`, `PyJWT` and `cryptography`.

- **Rehearse first:** `python3 scripts/play_upload.py --key ~/keys/play-upload.json`. Play stages
  every upload in a draft change and checks it, then the draft is thrown away, so nothing changes.
  It proves the key, the permissions and the files.
- **Publish:** add `--commit`. Google reviews the listing change before it shows on Play. While
  other changes are in review, such as an app update, publishing fails and changes nothing: Play's
  default would cancel that review and resubmit everything together, so the script asks for an
  error instead (`changesInReviewBehavior=ERROR_IF_IN_REVIEW`). Run it again once the review is
  over.
- **The key** is a service account's JSON key and stays outside the repository; the script refuses
  one inside it. One-time setup:
  1. In Google Cloud, in any project, enable the Google Play Android Developer API.
  2. Create a service account there (it needs no Cloud roles), then add a JSON key to it and save
     the file outside the repository.
  3. In Play Console, Users and permissions, invite the service account's email address and give it
     access to File Explorer with permission to manage the store presence.

  A newly invited account can take a while before the API accepts it; an error that says the caller
  lacks permission right after setup usually means waiting.

### Play's rules

Checked against Play Console Help in October 2026 (`support.google.com/googleplay/android-developer`
answers 9866151, 9898842 and 3125566):

- JPEG or 24-bit PNG, 320–3840px a side, the long side at most twice the short; up to 8 per device
  type. At least four 9:16 screenshots of 1080×1920 or more qualify for Play's larger placements.
- A tagline takes no more than 20% of the image. No "best", "#1", "top" or "new", no prices or
  promotions, no calls to action such as "Download now", no awards. Device imagery, such as a phone
  frame, is advised against and can limit promotion.
- A language without screenshots of its own shows the default language's (English), not those of
  its nearest regional variant.

### Problems hit, and the fixes

- **Captures too tall for Play.** 1080×2400 breaks the 2:1 limit, so each one sits scaled down on a
  1080×1920 canvas.
- **Caption font covers Latin only.** Bricolage Grotesque has Latin, extended Latin and Vietnamese.
  Greek and Russian use Noto Sans; Arabic, Urdu, Hindi, Bengali, Japanese and Chinese use their own
  Noto face. Each goes first in the font stack, so it also draws "SD" and "USB" and a caption never
  mixes two typefaces.
- **Marks above and below the letters collided.** Line height is 1.4 for Arabic, 1.35 for Hindi and
  Bengali, 1.25 for Japanese and Chinese, 1.9 for Urdu, and 1.08 for the rest.
- **Urdu ran off the band.** Nastaliq's letters rise and fall far past their line: at 80px they hit
  the top edge and nearly touched the screen. Urdu starts at 60px.
- **Japanese broke inside words.** `word-break: auto-phrase` breaks between phrases.
- **A keyboard notice in the captures.** Switching the app's language makes the keyboard show a
  "keyboard language changed" message at the bottom, and it lingers after the search and PDF tests
  type. It landed on the Hindi and Turkish `select` captures and on some light `home` ones, which no
  slot uses. Look at the bottom of every capture, and recapture an affected screen on its own, with
  no typing test before it: `-e tests_regex '.*#select\\[(hi|tr)\\]'` with demo mode and light mode
  set as the script sets them.
- **A missing translation failed after a full capture.** The script now runs
  `play_screenshots.py --check` with the requested languages first.

### Content decisions the owner made

- **The goal is turning listing visitors into installs**, so each of the first three slots shows one
  benefit. The order was the owner's call over a storage-first proposal, from the analytics: 80% of
  users have an SD card, and twice as many use the light theme as the dark one.

  | Slot | Screen     | Theme | English caption                     |
  |------|------------|-------|-------------------------------------|
  | 1    | `drives`   | light | Phone, SD card and USB in one place |
  | 2    | `analyzer` | light | See what's filling your storage     |
  | 3    | `search`   | light | Find what you need, fast            |
  | 4    | `select`   | light | Manage your files with ease         |
  | 5    | `pdf`      | light | Open files without another app      |
  | 6    | `actions`  | light | Everything you need, one tap away   |
  | 7    | `settings` | light | Tailor the app to how you work      |
  | 8    | `home`     | dark  | Light or dark, your choice          |

- **Only screens the class already captures.** A video player or unzip screen would have needed new
  tests and fixtures.
- **Captions rejected on the way:** "Move, copy and zip many at once" and others built on "many" or
  "bulk"; "No ads. Works offline." for slot 7; "Easy on the eyes at night" for slot 8, because it
  suggests the theme switches by itself.
- **Look:** manila `#ebc68c` with espresso captions `#2b1d0e`; slot 8 walnut `#27211c` with a manila
  caption. No phone frame, per Play's advice. A folder tab drawn behind each screen, after the app's
  icon, was tried and removed.
- **All 20 languages, replaced at once** rather than tested against the old set in a store listing
  experiment. The captions were translated without a native speaker's review. They follow the app's
  own formal or informal address and its words for SD card and light/dark, except Russian "память",
  French "appli", Catalan "app" and Chinese "U盘", which differ from the app's strings. Urdu captions
  use Nastaliq, which the app's plainer Urdu text doesn't.
- **Play language codes:** `en-US`, `ar`, `bn-BD`, `ca`, `de-DE`, `el-GR`, `es-ES`, `fr-FR`,
  `hi-IN`, `id`, `it-IT`, `ja-JP`, `nl-NL`, `pt-BR`, `ro`, `ru-RU`, `tr-TR`, `ur`, `vi`, `zh-CN`:
  exactly the listing's languages, with no regional variants. A regional listing added later
  (`es-419`, `pt-PT`, `zh-TW`, ...) shows English until it gets screenshots of its own.

### Not done

- **Tablets:** the only emulator is a phone, so tablet screenshots would need a tablet emulator and
  a check that the screens lay out well there.
