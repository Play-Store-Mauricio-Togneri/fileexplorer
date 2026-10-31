# Plan 2: help Android 11+ users who come back from Settings without All Files Access

Source: `todo.md` row 2 (Tier 3). Code read on `release/2.8.0` (`versionName` 2.8.0) and compared
with `release/2.6.0` (2.6.3) and `release/2.7.0` (2.7.3). This is a plan only. Nothing has been
implemented, built or run.

Paths are relative to `app/src/main/java/com/mauriciotogneri/fileexplorer/` unless they start with
`app/`, `.claude/` or `scripts/`.

---

## 1. Problem

On Android 11 and later the app can't browse anything until the user turns on "All files access"
for it in system Settings. The permission screen's only button takes the user there. Many of those
trips end with the user back in the app without having turned the switch on. When that happens the
app shows exactly the screen it showed before: the same title, sentence and button. A user who
didn't understand what to do on the Settings page learns nothing new, and the easy next step is to
give up.

Goal: after a return without access, say in one line what to turn on, and measure whether that
line helps.

---

## 2. Verified cause

| # | Claim in todo row 2 | Verdict | Evidence (`release/2.8.0` unless noted) |
|---|---------------------|---------|------------------------------------------|
| 1 | The button opens this app's All Files Access page (`util/IntentUtil.kt:589-603`) | **Confirmed**, lines exact | `openAllFilesAccessSettings` (589-603, KDoc 582-587) sends `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` with a `package:` URI. It's called from `PermissionScreen.kt:116-118` when the SDK is 30 or higher. When that page is missing it falls back to the all-apps list `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION` (605-617), then to the App info page (627-641), then to the toast `permission_settings_unavailable` (643-647). The function returns `Unit`, so the screen never learns which page opened, or that none did. |
| 2 | On return the user sees the same screen and text, with no extra explanation (`ui/screens/permission/PermissionScreen.kt:156-182`) | **Confirmed** | Title at 156-161, message at 165-172, button at 176-182. The resume handler (93-110) only logs `permission_returned_without_granting` (102). Nothing it does reaches `PermissionScreenContent`, whose only input besides the click is `permanentlyDenied` (126). |
| 3 | The extra help is only shown on Android 10 and below (`:82-85`) | **Confirmed, with a nuance** | 82-85 set `permanentlyDenied` after the runtime dialog, and that dialog only runs below Android 11 (122-124). The flag swaps the message to `permission_denied_message` and the button to `apk_permission_settings` (166-168, 178-180). It appears only after a *permanent* denial, not after every pre-11 return. It was added in 2.7 (`git diff release/2.6.0 release/2.7.0` on this file). |
| 4 | Likely: part of the pooled rate is Play pre-launch robots on 2.6, which still collected analytics on Firebase Test Lab (2.7 stopped, `release/2.7.0` `data/util/AnalyticsTracker.kt:31`) | **Code part confirmed. The data share can't be checked from code** | `release/2.7.0` `AnalyticsTracker.kt:31` calls `isTelemetryCollectionEnabled`, which includes `DeviceInfo.isTestLab` (`data/util/TelemetryCollection.kt:16-19`). `release/2.6.0` `AnalyticsTracker.kt:31` reads `!(BuildConfig.DEBUG \|\| DeviceInfo.isEmulator())`, with no Test Lab check, so 2.6 logged from physical pre-launch devices. Robots tapping the button and pressing back is plausible. Only the data can say how much of the rate they make up. |
| 5 | The screen is unchanged in 2.8 | **Confirmed** | `git diff release/2.7.0 release/2.8.0 -- …/PermissionScreen.kt` is empty. The All Files Access fallback chain is also unchanged. 2.8 only added the rating-ask block to the toast fallback (`IntentUtil.kt:644-645`). |

Further findings that shape the plan (not in the todo):

| # | Finding | Evidence | Why it matters |
|---|---------|----------|----------------|
| 6 | A return is missed when the activity is rebuilt during the trip | `hasNavigatedToSettings` and `isFirstResume` use plain `remember` (`PermissionScreen.kt:62-63`), and the return check sits in the not-first-resume branch (95-104). Android may kill the process while Settings is in front (common on low-memory phones). A theme, language or font-size change also rebuilds the activity, because `MainActivity` handles only `orientation\|screenSize\|screenLayout\|keyboardHidden` itself (`app/src/main/AndroidManifest.xml:55`). In both cases the flag is lost and the first resume is skipped | Those returns are never counted, and as written they could never show a hint either. Rotation is fine, because it doesn't rebuild the activity |
| 7 | The toast fallback records a trip that never happened | Line 117 sets `hasNavigatedToSettings = true` *before* line 118 runs the intent chain, which can end in a toast with nothing opened | The next unrelated resume (screen off and on) logs a false return. With a hint added, it would also show the hint. This is rare: it needs a device with no Settings page at all |
| 8 | What the metric is built from | `AnalyticsTracker.kt:844-849` `permission_grant_button_tapped` {`is_android_11_or_above`}; 851-853 `permission_returned_without_granting` (no params); 863-865 `permission_screen_resumed`. **No event marks a grant on return on Android 11+**: the screen just calls `onPermissionGranted()` (106-108). The pre-11 App info route (119-121) sets the same flag, so the return event mixes both routes | Today the only rate is returns ÷ taps. To mean "Android 11+" it has to be filtered by `os_version`, not only by the tap's param. `.claude/skills/analyze-analytics/SKILL.md` row U10 defines it per grant tap |
| 9 | Existing tests | androidTest: `ui/screens/permission/PermissionScreenTest.kt` (content only: renders in every theme mode, plus the permanently-denied variant), `PermissionScreenActionsTest.kt` (the tap fires the per-app intent, stubbed with Espresso-Intents), `util/IntentUtilAllFilesAccessTest.kt` (fallback order), `integration/NavGraphTest.kt` (a fake permission checker plus CREATED→RESUMED drives a grant on return), `util/AndroidPermissionCheckerTest.kt`. Unit: `util/IntentUtilReviewPromptFailureTest.kt` (a failed App info launch blocks the rating ask). **No test covers a return without access** | `NavGraphTest.grantPermissionAndResume` is a working recipe for driving a return on the API 36 emulator |
| 10 | The theme defines no secondary or tertiary colours | `ui/theme/Theme.kt:39-79` sets `primary*`, `surface*`, `surfaceContainer{Low,,High}`, `onSurface*`, `outlineVariant`, `error*`. Anything else, such as `secondaryContainer`, falls back to Material's purple baseline | The hint may use only roles the theme defines |
| 11 | The screen's column doesn't scroll | `PermissionScreen.kt:144-147`: a centred `Column` with no `verticalScroll` | Rough estimate: the content is about 325dp tall today. A landscape phone has about 320-370dp to spare, so another line can squeeze the button out of view at large font sizes or in landscape |

---

## 3. Approach

### D1. When the hint appears

| # | Option | Gain | Cost |
|---|--------|------|------|
| 1 | **Only after a return without access** (the todo's recommendation) | The first visit stays as it is. The hint reaches exactly the users who failed once. A first trip (no hint) and a retry (hint) can be compared in the same release | Needs a small saved state (D2) |
| 2 | Always on Android 11+ | No state, and the first trip gets help too | Extra text for everyone, including the majority who succeed. No same-release comparison, so the only test left is old version against new |

**Recommendation: 1.**

### D2. Detecting and holding "came back without access"

**Detection.** Arm a trip only when the grant button actually opened a Settings page. Change
`IntentUtil` to report which page opened, or none. On **every** resume, including the first resume
of a rebuilt screen, check for an armed trip:

- No armed trip: not a return. Do nothing beyond today's checks.
- Armed trip, access granted: disarm, log the grant, navigate (as today).
- Armed trip, no access: disarm, log the return, show the hint.

The first resume after a rebuild can only see an armed trip if the trip was saved before the
rebuild, because the button can't be tapped before the screen first resumes. That condition is
therefore exactly "this return landed in a rebuilt screen", and it's reported as `recreated` (D6).

**Where the state lives.**

| # | Option | Gain | Cost |
|---|--------|------|------|
| 1 | Two more `rememberSaveable` values inline in the composable, next to `permanentlyDenied` | Smallest diff | The decision logic stays in a composable and can only be tested on the device, where the SDK is fixed at 36. The rule "no hint below Android 11" would go untested, and AGENTS.md wants other-API behaviour unit-tested with the SDK level passed in |
| 2 | **A small plain state holder, `SettingsReturnState`, in the permission package** (the same shape as `ui/screens/pdfviewer/PdfZoomState.kt`). It holds Compose state, has a `Saver`, and is held with `rememberSaveable(saver = …)` | The decisions and analytics calls are JVM-testable with `mockkObject(AnalyticsTracker)`, including below Android 11 through an `sdkInt` argument. The state survives a rebuild and process death | One new file, about 50 lines, plus its unit test |
| 3 | A new `PermissionViewModel` with `SavedStateHandle` | Matches "business logic in ViewModels" | It would be the first `SavedStateHandle` in the codebase, needs a factory, and means moving the runtime-dialog launcher and the permanently-denied logic too. That refactor goes well beyond this problem |

**Recommendation: 2.** It moves only the trip and hint logic. The runtime-dialog and
permanently-denied code stays as it is.

**What survives.** Rotation (it never rebuilds the activity), a theme, language or font change while
in Settings, and process death while in Settings. A cold start doesn't survive: if the user swipes
the app away and opens it later, the screen starts fresh. Persisting the hint in DataStore is
possible (open question 4) but adds a preference and a read. It isn't recommended for this release:
`hint_shown` (D6) will show first how often people retry at all.

**Reset.** Once shown, the hint stays for the life of the screen. It isn't cleared when the user taps
again: the screen is hidden while they're away, and clearing it would make it blink off and on with
every failed retry. It goes away when access is granted (the screen leaves the back stack) or on a
cold start. The armed trip is cleared by every return.

### D3. Wording: quote the system label or describe it

The switch's label isn't fixed. The per-app page on Pixel/AOSP reads "Allow access to manage all
files". The permission controller's Files page uses "Allow management of all files". OEM skins may
differ again. The system also shows the label in the user's language, using Google's or the OEM's
translation, which our 19 translations wouldn't match word for word. A quoted label that doesn't
match exactly is worse than a description, because users scan for the exact words. (Assumption:
the OEM labels are from memory and haven't been checked on Samsung or Xiaomi. The manual check in
section 6 records them.)

| # | Option | Gain | Cost |
|---|--------|------|------|
| 1 | Quote "Allow access to manage all files" | Exact match on Pixel in English | Likely mismatches on other OEMs and in other languages |
| 2 | **Describe the control and the goal, reusing the label's key words without quotes** | Right everywhere, and still echoes the English label | Slightly less literal on Pixel |

**Recommendation: 2.** English text:

> Access not granted yet. Turn on the switch that allows File Explorer to manage all files, then
> come back here.

It names the control (the only switch on the per-app page), echoes "allow / manage all files", and
says to come back, since the Settings page doesn't return by itself. "File Explorer" is written
literally, the way `permission_settings_unavailable` already does it. `app_name` is "File Explorer"
in all 20 locales.

### D4. Different text for the fallback pages?

| # | Page that opened | Hint |
|---|------------------|------|
| 1 | This app's All files access page (normal case) | New string `permission_all_files_hint` |
| 2 | The all-apps list (per-app page missing) | **The same new string.** The user taps File Explorer in the list and reaches the same switch. A list-specific string would mean 20 more translations for a path no data yet shows is used |
| 3 | App info (both All files access pages missing, Android 11+) | The **existing** `permission_settings_unavailable`, "Allow File Explorer to access all files manually in Settings.", because App info has no single switch to point at |
| 4 | Nothing opened (toast) | No trip is armed and no hint is shown. The existing toast already explains |
| 5 | Below Android 11, App info after a permanent denial | No hint. The existing denied message already is that help |

**Recommendation: as in the table.** The `settings_page` param (D6) then shows whether the list
path is common enough to deserve its own wording later.

### D5. Placement and style

- **Placement:** between the message and the button, so it's read just before tapping ("next to
  the button"). The 48dp gap becomes about 16dp, then the hint, then about 32dp.
- **Style options:** (1) a plain centred text line in `onSurface`; (2) **a callout like the text
  viewer's `TruncationBanner`** (`ui/screens/textviewer/TextViewerScreen.kt:205-226`): a
  `surfaceContainerHigh` fill with rounded `MaterialTheme.shapes.medium` corners, an
  `Icons.Outlined.Info` icon tinted `onSurfaceVariant`, and the text in `bodyMedium`. **Recommend
  2.** It has an in-app precedent, and it reads as a different kind of text from the sentence the
  user already passed over once. Use only colours the theme defines, never `secondaryContainer`
  (finding 10). In the light palette the fill (#F0F0F0) sits close to the surface (#FBFBFB), so the
  icon does most of the work there. The `frontend-design` skill settles the final spacing and
  emphasis.
- **Accessibility:** mark the callout as a polite live region so TalkBack announces it when it
  appears on return. The icon is decorative (`contentDescription = null`). Whether the
  announcement actually fires when the node first appears needs a check on a device (section 6).
- **Scrolling:** make the column scroll vertically (finding 11), so the button can't be squeezed out
  in landscape or at large font sizes.

### D6. Analytics

Extend the events so the hint's effect can be measured. Every value describes the trip and nothing
identifies the user or a file.

| # | Event | Change | Params |
|---|-------|--------|--------|
| 1 | `permission_grant_button_tapped` | add a param | `is_android_11_or_above` (as today), **`hint_shown`** |
| 2 | `permission_returned_without_granting` | add params | **`settings_page`** (`app` / `list` / `app_details`), **`hint_shown`**, **`recreated`** |
| 3 | **`permission_granted_on_return`** | new event | `settings_page`, `hint_shown`, `recreated` |

- `hint_shown` means the hint was on screen when the user left for this trip, so `true` marks a
  retry after a failed return.
- `recreated` means the return landed in a rebuilt screen (finding 6). These returns are counted for
  the first time in this release, so leave them out when comparing with older versions. It also
  shows how often the process dies while Settings is in front.
- Neither event calls `ReviewPrompt`: this isn't a counted action and isn't a failure.
- The names are within Firebase's limits (event names ≤40 characters, ≤25 params). The saved
  BigQuery query takes every event except `screen_view`, so the new event and params arrive with no
  query change. BigQuery needs no custom-dimension registration.
- Alternative: change no analytics and judge only by returns ÷ taps. That's cheaper, but it can't
  separate the hint's effect from changes in the version and device mix. **Recommendation: extend.**

### D7. Judging the result

The export is aggregated (no users, no dates), so these are event ratios, not per-user
conversions.

| # | Rate | Definition | Versions |
|---|------|------------|----------|
| 1 | Return-without rate (comparable across versions) | `permission_returned_without_granting` ÷ `permission_grant_button_tapped`{`is_android_11_or_above`=true}, both with `os_version` ≥ 11. On the new release count only `recreated`=false | Baseline 2.7–2.8 against the release with the hint. **Never include 2.6** |
| 2 | First-trip success | `permission_granted_on_return`{`hint_shown`=false} ÷ (that + `permission_returned_without_granting`{`hint_shown`=false}) | New release only |
| 3 | Retry success: the hint's effect | `permission_granted_on_return`{`hint_shown`=true} ÷ (that + `permission_returned_without_granting`{`hint_shown`=true}) | New release only |
| 4 | Retry rate | `permission_grant_button_tapped`{`hint_shown`=true} ÷ `permission_returned_without_granting` (`os_version` ≥ 11) | New release only |
| 5 | Fallback share | Rows 2 and 3 split by `settings_page` | New release only |

- **Test Lab noise:** leave out every `app_version` below 2.7. 2.6.x collected on pre-launch
  devices, while 2.7 and later never do (finding 4), so the new release's data is clean by
  construction. If the raw BigQuery table is ever used instead of the saved query, filtering by
  device model would also work, but the version filter is enough.
- **Android version:** filter `os_version` ≥ 11. The pre-11 App info route shares the return event.
- **Sample size:** follow the analytics skill: at least 100 events in each denominator. Rate 3's
  denominator is a subset of returns, so it may take weeks of the 30-day window to fill.
- **Suggested reading:** the hint works if rate 3 is clearly above zero (for example, at least a
  third of retries end in a grant) and rate 1 drops below the 2.7–2.8 baseline. The thresholds are
  the owner's call.

---

## 4. Step-by-step changes

1. **`util/IntentUtil.kt`**: report which Settings page opened.
   - Add an enum near the other result types at the top of the file (as with `OpenFileResult`,
     line 36):
     ```kotlin
     /** The Settings page the permission screen's grant button opened. */
     enum class SettingsPage(val analyticsValue: String) {
         APP("app"),                 // this app's All files access page
         LIST("list"),               // the all-apps All files access list
         APP_DETAILS("app_details")  // this app's App info page
     }
     ```
   - `openAllFilesAccessSettings(context): SettingsPage?` returns `APP` on success, otherwise
     whatever the list fallback returns. `openAllFilesAccessList` returns `LIST`, or `APP_DETAILS`
     when `openAppDetailsSettings` opened, or `null` after the toast. `openAppDetailsSettings(context):
     Boolean` returns whether a page opened, the way `openPlayStore` already does. Error reporting,
     the toast and the `ReviewPrompt.onActionFailed()` call stay unchanged. Update the KDoc to say
     what's returned.
   - Why: the screen needs the page to pick the hint (D4) and to log `settings_page`, and needs
     `null` to avoid arming a trip that never happened (finding 7).

2. **`data/util/AnalyticsTracker.kt`** (permission section, lines 842-869):
   - `trackPermissionGrantButtonTapped(isAndroid11OrAbove: Boolean, hintShown: Boolean)` adds
     `"hint_shown" to hintShown.toString()`.
   - `trackPermissionReturnedWithoutGranting(settingsPage: SettingsPage, hintShown: Boolean,
     recreated: Boolean)` sends `settings_page`, `hint_shown` and `recreated`.
   - New `trackPermissionGrantedOnReturn(settingsPage, hintShown, recreated)` sends event
     `permission_granted_on_return` with the same params.
   - Add KDoc to all three explaining each param and that the pre-11 App info route also fires the
     return events. The analytics skill reads this KDoc as its event catalogue. Values stay plain
     strings through the existing `trackEvent`, which already catches every failure, so nothing
     throws into the screen.

3. **New `ui/screens/permission/SettingsReturnState.kt`**: the state holder from D2, about this
   shape:
   ```kotlin
   @Stable
   internal class SettingsReturnState(awayOn: SettingsPage? = null, hintFrom: SettingsPage? = null) {
       /** The page the grant button opened that the user hasn't come back from yet. */
       var awayOn by mutableStateOf(awayOn)
           private set
       /** The page the user last came back from without access; non-null shows the hint. */
       var hintFrom by mutableStateOf(hintFrom)
           private set
       val hintShown: Boolean get() = hintFrom != null

       fun onSettingsOpened(page: SettingsPage?) { if (page != null) awayOn = page }

       fun onResumed(hasPermission: Boolean, recreated: Boolean, sdkInt: Int) {
           val page = awayOn ?: return
           awayOn = null
           if (hasPermission) {
               AnalyticsTracker.trackPermissionGrantedOnReturn(page, hintShown, recreated)
           } else {
               AnalyticsTracker.trackPermissionReturnedWithoutGranting(page, hintShown, recreated)
               if (hintFor(page, sdkInt) != null) hintFrom = page
           }
       }

       @StringRes fun hint(sdkInt: Int): Int? = hintFrom?.let { hintFor(it, sdkInt) }

       companion object {
           // Saved by name, the way FolderScreen saves its sort mode; an unknown name restores as null.
           val Saver = listSaver<SettingsReturnState, String>(
               save = { listOf(it.awayOn?.name.orEmpty(), it.hintFrom?.name.orEmpty()) },
               restore = { SettingsReturnState(pageNamed(it[0]), pageNamed(it[1])) }
           )

           private fun pageNamed(name: String) = SettingsPage.entries.firstOrNull { it.name == name }
       }
   }

   @StringRes
   internal fun hintFor(page: SettingsPage, sdkInt: Int): Int? = when {
       sdkInt < Build.VERSION_CODES.R -> null   // below 11 the denied message is the help
       page == SettingsPage.APP_DETAILS -> R.string.permission_settings_unavailable
       else -> R.string.permission_all_files_hint
   }
   ```
   It saves the page, not a string resource ID, because resource IDs aren't stable across builds.
   Add a KDoc on why the state is saved (process death and rebuilds while in Settings).

4. **`ui/screens/permission/PermissionScreen.kt`**. **Use the `frontend-design` skill for this
   step** (owner's rule for any UI change).
   - Replace `hasNavigatedToSettings` (line 62) with
     `val settingsReturn = rememberSaveable(saver = SettingsReturnState.Saver) { SettingsReturnState() }`.
     Keep `isFirstResume` as it is.
   - Resume block (93-110): read `val recreated = isFirstResume`, keep the existing
     first-resume skip for `trackPermissionScreenResumed()`, read the permission once, call
     `settingsReturn.onResumed(hasPermission, recreated, Build.VERSION.SDK_INT)` on **every**
     resume, then `if (hasPermission) onPermissionGranted()`. Add a comment that the first resume is
     checked too, because a trip saved before a rebuild ends there.
   - Click (113-125): `trackPermissionGrantButtonTapped(isAndroid11OrAbove, settingsReturn.hintShown)`.
     On 11+: `settingsReturn.onSettingsOpened(IntentUtil.openAllFilesAccessSettings(context))`. On
     the permanently-denied path:
     `settingsReturn.onSettingsOpened(SettingsPage.APP_DETAILS.takeIf { IntentUtil.openAppDetailsSettings(context) })`.
     The runtime-dialog path is unchanged.
   - `PermissionScreenContent(onGrantClick, permanentlyDenied = false, @StringRes settingsHint: Int? = null)`.
     The default keeps the existing callers and tests compiling. Pass
     `settingsHint = settingsReturn.hint(Build.VERSION.SDK_INT)`.
   - Content (144-183): add `verticalScroll(rememberScrollState())` to the column. When
     `settingsHint != null`, place a private `SettingsHint` callout between the message and the
     button (D5: `surfaceContainerHigh`, rounded, `Icons.Outlined.Info` in `onSurfaceVariant`, text
     in `bodyMedium`, polite live region). Spacing is inline dp, as in the rest of this file. The
     project has no `dimens.xml`.

5. **`app/src/main/res/values*/strings.xml`**: one new string, `permission_all_files_hint`, placed
   after `permission_denied_message` in each file. Apostrophes are shown escaped, as they must
   appear in the XML. The translations are drafts and should get a native read if possible.

   | # | Folder | Text |
   |---|--------|------|
   | 1 | `values` | Access not granted yet. Turn on the switch that allows File Explorer to manage all files, then come back here. |
   | 2 | `values-ar` | لم يُمنح الوصول بعد. فعّل المفتاح الذي يسمح لـ File Explorer بإدارة جميع الملفات، ثم عُد إلى هنا. |
   | 3 | `values-bn` | অ্যাক্সেস এখনও দেওয়া হয়নি। যে সুইচটি File Explorer-কে সমস্ত ফাইল পরিচালনা করার অনুমতি দেয় সেটি চালু করুন, তারপর এখানে ফিরে আসুন। |
   | 4 | `values-ca` | Encara no s\'ha concedit l\'accés. Activa l\'interruptor que permet a File Explorer gestionar tots els fitxers i després torna aquí. |
   | 5 | `values-de` | Der Zugriff wurde noch nicht gewährt. Aktivieren Sie den Schalter, mit dem File Explorer alle Dateien verwalten darf, und kehren Sie dann hierher zurück. |
   | 6 | `values-el` | Η πρόσβαση δεν έχει παραχωρηθεί ακόμα. Ενεργοποιήστε τον διακόπτη που επιτρέπει στο File Explorer να διαχειρίζεται όλα τα αρχεία και, στη συνέχεια, επιστρέψτε εδώ. |
   | 7 | `values-es` | Aún no se ha concedido el acceso. Activa el interruptor que permite a File Explorer gestionar todos los archivos y vuelve aquí. |
   | 8 | `values-fr` | L\'accès n\'a pas encore été accordé. Activez l\'option qui autorise File Explorer à gérer tous les fichiers, puis revenez ici. |
   | 9 | `values-hi` | पहुँच अभी तक नहीं दी गई है। वह स्विच चालू करें जो File Explorer को सभी फ़ाइलें प्रबंधित करने की अनुमति देता है, फिर यहाँ वापस आएँ। |
   | 10 | `values-in` | Akses belum diberikan. Aktifkan sakelar yang mengizinkan File Explorer mengelola semua file, lalu kembali ke sini. |
   | 11 | `values-it` | L\'accesso non è ancora stato concesso. Attiva l\'interruttore che consente a File Explorer di gestire tutti i file, poi torna qui. |
   | 12 | `values-ja` | アクセスはまだ許可されていません。File Explorer にすべてのファイルの管理を許可するスイッチをオンにしてから、ここに戻ってください。 |
   | 13 | `values-nl` | Toegang is nog niet verleend. Zet de schakelaar aan waarmee File Explorer alle bestanden mag beheren en kom daarna hier terug. |
   | 14 | `values-pt` | O acesso ainda não foi concedido. Ative a opção que permite ao File Explorer gerenciar todos os arquivos e depois volte aqui. |
   | 15 | `values-ro` | Accesul nu a fost încă acordat. Activați comutatorul care permite aplicației File Explorer să gestioneze toate fișierele, apoi reveniți aici. |
   | 16 | `values-ru` | Доступ ещё не предоставлен. Включите переключатель, который разрешает File Explorer управлять всеми файлами, и вернитесь сюда. |
   | 17 | `values-tr` | Erişim henüz verilmedi. File Explorer uygulamasının tüm dosyaları yönetmesine izin veren anahtarı açın, ardından buraya geri dönün. |
   | 18 | `values-ur` | رسائی ابھی تک نہیں دی گئی۔ وہ سوئچ آن کریں جو File Explorer کو تمام فائلوں کا انتظام کرنے کی اجازت دیتا ہے، پھر یہاں واپس آئیں۔ |
   | 19 | `values-vi` | Quyền truy cập chưa được cấp. Hãy bật công tắc cho phép File Explorer quản lý tất cả tệp, rồi quay lại đây. |
   | 20 | `values-zh` | 尚未授予访问权限。请打开允许 File Explorer 管理所有文件的开关，然后返回此处。 |

   The tone of address follows each file's existing permission strings: formal in de, fr, el, ro,
   ru and tr; informal in es, ca and it; Brazilian Portuguese in pt. No placeholders and no plurals.
   `LocalizationParityTest` enforces that all 20 files have the string.

6. **Tests**: see section 5.

7. **After the release ships (optional, the owner's call):** update row U10 in
   `.claude/skills/analyze-analytics/SKILL.md` to read `permission_granted_on_return` and the
   `settings_page` / `hint_shown` / `recreated` params, and add rates 1-5 from D7, so the next
   report computes them the same way every time.

Left alone on purpose: feature-discovery badges (nothing new to point at), `ReviewPrompt`, Remote
Config, `AGENTS.md` (no new rule), the runtime-dialog and permanently-denied logic, `MainActivity`
and `NavGraph`.

---

## 5. Tests

### Unit tests (JVM, `app/src/test/`)

1. **New `app/src/test/java/com/mauriciotogneri/fileexplorer/ui/screens/permission/SettingsReturnStateTest.kt`**
   (JUnit 4, backticked names, `mockkObject(AnalyticsTracker)` as in `HomeViewModelTest`):
   - `a resume with no trip to Settings is not a return`: no analytics call and no hint.
   - `a grant button that opened nothing arms no trip`: after `onSettingsOpened(null)`, a resume
     logs nothing.
   - `coming back without access shows the hint and reports the page`: APP at SDK 36 logs
     `trackPermissionReturnedWithoutGranting(APP, hintShown = false, recreated = false)`, and
     `hint(36) == R.string.permission_all_files_hint`.
   - `a second return without access reports the hint as shown`: the second event has
     `hintShown = true`.
   - `coming back with access reports the grant and shows no hint`.
   - `a return into a rebuilt screen is reported as recreated`.
   - `the trip ends with the return`: a second resume after a return isn't a return.
   - `the all-apps list gets the same hint as the app page`.
   - `the App info fallback on Android 11+ points to Settings`: gives
     `R.string.permission_settings_unavailable`.
   - `below Android 11 a return adds no hint`: SDK 29 with APP_DETAILS still logs the return with
     `hintShown = false`, but `hint(29)` is null.
   - `the trip and the hint survive a save and restore`: a round trip through `Saver` with a
     `SaverScope` that accepts every value. Covers each page and `null`.
2. **`app/src/test/java/com/mauriciotogneri/fileexplorer/util/IntentUtilReviewPromptFailureTest.kt`**:
   in `an app details screen that cannot open blocks the rating ask`, add
   `assertFalse(IntentUtil.openAppDetailsSettings(context))`.
3. `LocalizationParityTest` and `AnalyticsTrackerReviewHookTest` need no edits but must stay green.
   The first proves all 20 strings exist. The second proves no new failure hook was added.

### Instrumentation and Compose UI tests (`app/src/androidTest/`, all run the Android 11+ path on `Pixel_7_API_36`, no skip conditions)

4. **`util/IntentUtilAllFilesAccessTest.kt`**: each of the four cases also asserts the returned
   page: `APP`, `LIST`, `APP_DETAILS`, `null`. The helper returns the result as well as the
   recording context.
5. **`ui/screens/permission/PermissionScreenTest.kt`**:
   - In `permissionScreen_rendersEveryElement_inEveryThemeMode`, also assert that the hint text is
     absent by default (`assertCountEquals(0)`).
   - New `permissionScreen_settingsHint_rendersAboveTheButton_inEveryThemeMode`: renders
     `PermissionScreenContent(onGrantClick = {}, settingsHint = R.string.permission_all_files_hint)`,
     re-themes in place as the existing test does, and asserts that the hint, title, message and an
     enabled button are displayed. It also asserts that the hint's bottom bound is above the
     button's top bound, which pins "next to the button".
   - New `permissionScreen_settingsHint_keepsTheButtonReachableInAShortWindow`: renders the content
     with the hint inside a `Box(Modifier.size(640.dp, 320.dp))`, then
     `onNodeWithText(grant).performScrollTo()`, `assertIsDisplayed()` and
     `assertHeightIsAtLeast(ButtonDefaults.MinHeight)`. This fails if the column can't scroll and
     the button gets squeezed.
6. **`ui/screens/permission/PermissionScreenActionsTest.kt`** (it already stubs every intent with
   Espresso-Intents, so Settings never opens). Add a helper that renders the internal
   `PermissionScreen(onPermissionGranted, permissionChecker = fake)` and drives a return with
   `scenario.moveToState(CREATED)` and then `RESUMED`, the way `NavGraphTest` does:
   - `returnWithoutAccess_showsTheHint`: tap, return with the fake still saying no, then the hint
     (`getString(R.string.permission_all_files_hint)`) and the button are displayed.
   - `returnWithAccess_grants_andNeverShowsTheHint`: the fake flips to yes before RESUMED, then
     `onPermissionGranted` is called and the hint count is 0.
   - `resumeWithoutATrip_showsNoHint`: no tap, CREATED→RESUMED, hint count 0.
   - `returnIntoARebuiltScreen_stillShowsTheHint`: uses `StateRestorationTester` with tap, then
     `emulateSavedInstanceStateRestore()`, then the hint is displayed. The restored screen's first
     resume ends the saved trip.
   - Update the class KDoc. It says the resume re-check is out of scope, but the fake checker now
     covers it. Its reference to `PermissionScreen.kt:96-100` for the pre-R arm is stale: that arm
     is at 122-124 today and will move again.
7. **`theme/ThemeRenderingTest.kt`**: in `assertSchemeIsReadable`, add the callout's text-on-fill
   pair (for example `onSurface/surfaceContainerHigh` or `onSurfaceVariant/surfaceContainerHigh`,
   whichever the final design uses) at 4.5:1, for both palettes.
8. `integration/NavGraphTest.kt` needs no edit and must stay green: a grant on return still
   navigates home and pops the permission screen.

All new tests read text through `getString(R.string…)` and render the production composables, as
`scripts/check-tests.sh` requires.

---

## 6. Verification

1. The three per-change commands from `AGENTS.md`:
   ```bash
   ./gradlew -w --console=plain -I gradle/agent-quiet.init.gradle testDebugUnitTest
   ./gradlew -w --console=plain lintDebug
   ./scripts/check-tests.sh
   ```
2. Run `delta-review` on the changed files (owner's workflow).
3. Instrumentation, only with `Pixel_7_API_36` already running, handed to the
   `instrumentation-runner` subagent. Run one class per run, because a comma list runs only the
   first test, and check free memory first:
   ```bash
   ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mauriciotogneri.fileexplorer.ui.screens.permission.PermissionScreenActionsTest
   ```
   Repeat for `PermissionScreenTest`, `util.IntentUtilAllFilesAccessTest`, `theme.ThemeRenderingTest`
   and `integration.NavGraphTest`.
4. Light and dark check on the emulator. Revoke access with
   `adb shell appops set --uid com.mauriciotogneri.fileexplorer MANAGE_EXTERNAL_STORAGE default`
   (the system restarts the app on a revoke), open the app, tap the button, press back in Settings
   without switching. Confirm the hint is readable and the callout visible in LIGHT, DARK and
   SYSTEM. Also check Arabic (right-to-left, icon on the right), German (longest text), the largest
   font size, and landscape (the button stays reachable by scrolling).
5. Manual check on a real Android 11+ phone, ideally one Pixel and one Samsung:
   - Return without switching: the hint appears. Tap again, switch on, return: the home screen
     opens.
   - Process death: turn on Developer options › "Don't keep activities", tap the button, press back.
     The hint must still appear. Turn the option off afterwards.
   - With TalkBack on, the hint is announced on return. If it isn't, adjust the live region.
   - Write down the exact switch label on each device, to confirm D3.
   - `adb logcat -s Analytics` on the debug build shows each event with its params. Debug builds
     echo every event to the log and send nothing to Firebase.
6. After release: let the release collect for the 30-day window, then run the analytics skill and
   compute rates 1-5 from D7 on `app_version` ≥ 2.7 and `os_version` ≥ 11, with the 100-event floor.

---

## 7. Risks and edge cases

- **Overflow:** without the scroll change, the extra line can hide the button in landscape or at
  large font sizes. Covered by step 4 and test 5.
- **Label mismatch:** the description in D3 can still miss on some OEM skins. The `settings_page`
  param doesn't reveal that. The manual label check is the only signal.
- **Switch blocked by an admin** (work profile or managed device): the hint can't help, but it isn't
  wrong either.
- **Split-screen or freeform windows:** the app stays resumed while Settings is open, so no return is
  detected and the hint never shows. A grant there is only noticed on the next resume. This
  behaviour already exists and is unchanged.
- **Per-app intent resolving to an OEM list screen:** it's logged as `app` even though the user saw
  a list. The hint text still fits.
- **Metric discontinuity:** the new release counts returns after a rebuild (`recreated`=true) and
  stops counting the false returns from the toast path (finding 7). Compare with older versions only
  on `recreated`=false rows. `hint_shown`, `settings_page` and the grant event don't exist before
  this release.
- **Granting doesn't restart the app; revoking does.** This is AOSP behaviour as best I recall it,
  not checked against source, so verify on the devices. If an OEM does restart on a grant, the return lands in a rebuilt
  screen, is logged with `recreated`=true, and still navigates home.
- **Saved by enum name:** renaming a `SettingsPage` constant later silently drops a trip saved at
  that moment. That's harmless: no hint for that one return.
- **Telemetry:** every new call goes through `trackEvent`'s try/catch, so a broken reporter can't
  break the screen. No names or paths are logged.
- **Translations** are drafts. A wrong word is likely to be a near-synonym rather than an error,
  but a native read is worth it for the RTL languages.

---

## 8. Open questions for the owner

1. Which release carries this: 2.8.0 (the branch still says 2.8.0 and the todo calls 2.8
   unchanged) or the next one? That release's number goes into the `recreated` KDoc and the D7
   comparison.
2. Wording: approve the described form ("Access not granted yet. Turn on the switch that allows File
   Explorer to manage all files, then come back here."), or quote the Pixel label instead?
3. Style: a callout like the text viewer's banner (recommended), or a plain text line?
4. Should the hint survive a cold restart, stored in DataStore? Recommended: not now. Decide after
   rate 4 shows how often people retry.
5. Keep the `recreated` param (recommended, for like-for-like comparisons and a process-death
   signal), or drop it to keep the events smaller?
6. Update the analytics skill's U10 row after release (step 7)?
7. The scroll fix for the screen's column is included because the hint makes overflow likely. Agree
   to include it in this change?
8. Can the 19 translations get a native-speaker review, or ship as drafted?
