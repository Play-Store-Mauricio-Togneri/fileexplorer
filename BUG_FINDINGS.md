# Bug Findings

## Medium

### [b/resource-and-configuration-parity/privacy-policy/new-data-flows-undisclosed] The privacy policy no longer describes what the app and the website send

- **Location:** `website/analytics.js:10`
  - Related: `app/src/main/res/raw/privacy.md:7`, `:33-34` (and its 19 `raw-*/` translations); `website/privacy.html` and the 19 `website/*/privacy.html` copies; `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/RemoteConfig.kt:41-42`; `app/src/main/java/com/mauriciotogneri/fileexplorer/FileExplorerApplication.kt:26`
- **Severity:** Medium
- **Confidence:** Medium. The facts are certain; how much the gap matters legally (for example EU cookie consent) is a judgment call.
- **Likelihood:** High. Every visitor to fileexplorer.org is now measured, and every release install fetches Remote Config at startup.
- **Defect:** The branch turns on two new data flows but leaves the policy, unchanged since the baseline, describing the old ones:
  - **Website:** the measurement ID was empty at the baseline, so nothing was sent. It is now set, so every page, the privacy page included, loads Google's tag, sets its cookies and reports visits and events. The site's only privacy page is the app's policy, which says nothing about the website's own measurement.
  - **App:** Firebase Remote Config now fetches on every start. Each fetch sends the Firebase installation ID and token, country, language, time zone, app version and Analytics user properties. The policy's "Third-Party Services" list still names only Crashlytics and Analytics.
- **Trigger:** any visit to fileexplorer.org; any start of a release build.
- **Evidence / verification:**
  - `git diff --stat d7947cad HEAD` over `raw/`, `raw-*/` and every `privacy.html` is empty.
  - `git show d7947cad:website/analytics.js` has `const MEASUREMENT_ID = '';` plus an early return when it is empty.
  - The fetch payload was read from the pinned firebase-config 24.0.1 sources (`ConfigFetchHttpClient`, `ConfigFetchHandler`).
  - Refutation attempt: no decision not to disclose is recorded in `AGENTS.md`, `website/AGENTS.md`, the README or the plan and todo documents. In-App Review is an on-device call to the Play Store, so leaving it out of the policy is defensible.
  - The policy's "does not transmit any personal data" sentence was already loose at the baseline (Analytics), so only the new flows are counted here.
- **Suggested fix:** Add Remote Config to the policy's third-party list and add a section on the website's own analytics and cookies, in English and all 19 translations, then copy them to the website as `website/AGENTS.md` describes. Alternatively, empty the measurement ID again until the disclosure ships. Whether the site also needs a consent step is the owner's call.

### [a/state-and-lifecycle/folder-operations/leaving-before-progress-dialog-cancels-operation] Leaving the folder screen in the first half second silently stops a confirmed delete, move or copy part-way

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/util/DelayedProgress.kt:42-43`
  - Related: `ui/screens/folder/FolderViewModel.kt:623` (move/copy), `:1091` (delete); `ui/screens/folder/FolderScreen.kt:261`; `activities/FolderActivity.kt:185`, `:192`; `data/repository/FileRepository.kt:838`
- **Severity:** Medium
- **Confidence:** High
- **Likelihood:** Low. It needs a back press, the toolbar back arrow or an ancestor breadcrumb tap within 0.5 s of confirming, on an operation still running at that point.
- **Defect:**
  - The progress dialog now appears only after 500 ms. Until then nothing blocks back: the screen's own back handler is active only in selection mode, and confirming clears the selection.
  - Leaving pops the folder's navigation entry, which clears its ViewModel and cancels every operation running in that ViewModel's scope.
  - The delete, rename and copy walks check for cancellation item by item, so the work stops part-way.
  - The cancel message is sent to a screen that is already gone, so the user sees nothing.
- **Trigger:** Confirm a delete of a large folder, a move, or a copy, then press back within half a second.
- **Evidence / verification:**
  - Outcomes by operation:
    - **Delete:** what was already removed is gone and the rest stays.
    - **Move:** items already renamed or copied stay at the destination and the rest stays at the source.
    - **Copy:** leaves a partly filled folder at the destination with no warning; a user may take it as complete and delete the originals.
    - **Compress and extract:** roll back cleanly to "nothing happened", the same as the baseline's short pre-count window, so they are not part of this finding.
  - Library behavior was confirmed in navigation 2.10.2: a popped entry's ViewModel store is cleared when the transition ends, and these transitions are instant.
  - At the baseline:
    - small deletes ran a blocking walk that could not be cancelled;
    - large deletes, moves and copies showed the modal dialog from their first progress update, before the first file changed.
  - Refutation attempt: the analyzer category screen documents the same behavior as intended (its ViewModel KDoc and the test "leaving the listing mid-walk still drops what the walk removed"), so it is excluded here. Nothing documents it for the folder screen; the plan's "the screen stays usable" rule exists so that a second operation can start, not so that the screen can be left.
- **Suggested fix:**
  - While a folder operation's dialog is still pending, intercept system back, the toolbar back arrow and ancestor breadcrumb taps. Show the dialog at once instead of popping the screen; the press then lands on the modal dialog, as it would 500 ms later.
  - Navigating into a child folder can stay allowed.
  - Leave the analyzer screen as documented.

### [a/logic-errors/media-index-sync/android10-gone-file-scan-drops-prefixed-rows] On Android 10, a gallery update after renaming or deleting a file can remove other photos from the gallery, including the renamed one

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/data/source/AndroidMediaIndexSync.kt:158-161`
  - Related: `:185-197` (only gone folder rows are kept out of the scan, at `:192`), the comments at `:74-77` and `:180-183`
- **Severity:** Medium
- **Confidence:** Medium. The scanner behavior comes from recall of Android 10's `ModernMediaScanner` and matches the code's own comment at `:74-77`; no Android 10 device or source was available.
- **Likelihood:** Low. It is limited to Android 10 and needs a file name that is the start of other names, in practice a file with no extension.
- **Defect:**
  - For each operation the sync scans the created paths first and the removed (now missing) paths last, in one scanner call.
  - On Android 10, scanning a missing path deletes every gallery row whose path merely starts with it, case-insensitively and with no `/` boundary. The code guards against this for folders (`Trip` must not drop `Trip 2`) but still scans missing files.
  - So renaming `IMG_2024` to `IMG_2024.jpg` indexes the new file, then deletes that brand-new row.
  - Deleting or moving an extensionless `IMG` drops the rows of every `IMG_0001.jpg` beside it.
  - The files themselves are untouched; they are missing from galleries until the next full media scan.
- **Trigger:** Android 10. Either:
  - rename a file so the new name extends the old one (adding a missing extension, `clip.mp` → `clip.mp4`);
  - delete or move an extensionless file whose name prefixes its siblings' names.
- **Evidence / verification:**
  - A throwaway unit test in a disposable copy (API 29, rename `IMG_2024` → `IMG_2024.jpg` with the old row indexed) shows the scanner receiving `[.../IMG_2024.jpg, .../IMG_2024]` in that order. The existing test at `AndroidMediaIndexSyncTest.kt:470` pins the same created-then-gone order.
  - At the baseline, the rename deleted exactly the old row and then scanned the new path, so nothing else was touched.
  - Refutation attempt: the comment at `:180-183` assumes every name has an extension ("extension included"). Nothing in GALLERY_SYNC_PLAN.md accepts this case.
- **Suggested fix:**
  - On Android 10, scan an entry's missing paths before its created ones.
  - After scanning a missing file, rescan the existing siblings whose names start with its name.
  - Do this together with the next finding's per-root error handling, or a failing lookup would then also skip the created scans.

### [a/error-handling/media-index-sync/failed-removed-lookup-drops-created-scans] On Android 7–10, one failed gallery lookup drops the gallery update for the files an operation created

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/data/source/AndroidMediaIndexSync.kt:158-171`
  - Related: `:377` (no cursor is an error); `:128-129` (the entry is removed from the saved queue)
- **Severity:** Medium
- **Confidence:** High
- **Likelihood:** Low, since provider failures are rare. It becomes High on Android 10 if the provider rejects the undocumented `format` column the lookup now requests, which the owner's notes list as unchecked.
- **Defect:**
  - Created files are collected into a batch that is only sent to the scanner at 500 paths or at the end.
  - If looking up a removed root's rows throws (no cursor, a security error, a rejected column), the end is never reached.
  - The whole entry is then reported and dropped, with no retry.
  - So a renamed or moved file is never scanned, and with fewer than 500 created files none are.
- **Trigger:** Android 7–10. A rename or move, either of which reports both a created and a removed path, whose removed-path lookup fails.
- **Evidence / verification:**
  - A throwaway unit test in a disposable copy (created `DCIM/IMG_2024.jpg`, removed `DCIM/IMG_2024`, failing lookup) passed asserting that nothing was scanned and the saved queue was empty.
  - At the baseline, the rename's old-row delete swallowed its own errors and the new path was scanned regardless.
  - The two existing failing-entry tests use entries with removed paths only, so they do not cover this.
- **Suggested fix:**
  - Catch failures per removed root: report the error, keep the rows already collected and continue, rethrowing cancellation.
  - Do not fix it by sending the created batch first; that recreates the order behind the previous finding.

## Low

### [a/error-handling/review-prompt/visible-failures-do-not-block-ask] Some failures the user sees do not stop the rating request

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/iteminfo/ItemInfoViewModel.kt:255`
  - Related: same file `:259`; `activities/MainActivity.kt:172`; `ui/screens/picker/PickerViewModel.kt:120-121`; `ui/screens/pdfviewer/PdfViewerViewModel.kt:240`
- **Severity:** Low
- **Confidence:** High
- **Likelihood:** Low. It needs a user who already qualifies for the rating request and hits one of these failures in the same session. The Info and startup cases are the plausible ones.
- **Defect:** AGENTS.md requires every user-visible failure to block the rating request for the rest of the session, and similar paths do (a missing recent or favorite, a gone analyzer volume, a folder listing failure, a wrong ZIP password). These do not:
  - the Info screen's "could not load" state, for a file that is gone or whose details fail to read;
  - the "startup folder unavailable" toast;
  - the picker's "unable to load files" state;
  - a wrong PDF password.
  
  A later success can then trigger the rating request right after the failure.
- **Trigger:** Open Info on a stale search result or recent, or start the app with its startup folder on a removed SD card, then complete any counted action.
- **Evidence / verification:**
  - None of these paths calls the failure hook or sends a failure event that calls it, and the error reporter does not call it either.
  - The startup path's disarm only clears a pending request; it does not block future ones.
  - The picker case is practically unreachable, because its helpers catch their own errors.
  - The wrong PDF password is a policy call; the ZIP flow treats the same situation as a failure.
  - This was reported in an earlier audit on this branch; none of these files has changed since.
- **Suggested fix:** Call the failure hook in both Info error branches, in the picker's storage-load error branch, beside the startup-unavailable toast (inside its existing "started" check), and, if the owner agrees, on a wrong PDF password.

### [a/user-interface-state/review-prompt/delete-confirm-double-tap-counts-twice] A second tap on Delete in the search and home dialogs counts one delete twice toward the rating request

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/search/SearchViewModel.kt:330-343`
  - Related: `ui/screens/home/HomeViewModel.kt:671-719` (recents), `:816-866` (favorites); `ui/components/DeleteConfirmDialog.kt:66`; `ui/screens/folder/FolderViewModel.kt:1166-1179`
- **Severity:** Low
- **Confidence:** High
- **Likelihood:** Low. The window lasts milliseconds for a file, but seconds for a large folder deleted from search or favorites, which shows no progress dialog by design.
- **Defect:**
  - The confirm dialog stays open with Delete enabled until the delete finishes, because these handlers clear the item only afterwards.
  - A second tap starts a second delete of the same item.
  - The second delete finds the path already gone, which counts as success, so the success hook runs twice for one user action. AGENTS.md requires "once per user action".
  - Folder and analyzer deletes clear first and are not affected.
  - The same overcount can happen on the folder screen if a row still visible before the list reloads is deleted again (traced, much less likely).
- **Trigger:** Confirm deleting a large folder from search results or favorites, then tap Delete again before the dialog closes.
- **Evidence / verification:**
  - A path that is gone while its parent is readable is classified as already absent, and a delete with no failures is a success.
  - The duplicate delete and duplicate analytics event existed at the baseline; the double count toward the rating request is new with the hooks.
  - Reported in an earlier audit on this branch; unchanged since.
- **Suggested fix:**
  - Clear the pending item, or mark a delete in flight, before launching, as the folder screen does.
  - Optionally, do not count a delete whose every root was already absent, which also covers the folder-screen route.

### [a/repeated-and-interrupted-operations/review-prompt/ask-spent-when-cancelled-after-save] A rating request interrupted at the wrong moment uses up one of the two lifetime requests without being shown

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/util/ReviewPromptGate.kt:132`
  - Related: same file `:110`, `:116-120`; `data/repository/ReviewPromptRepository.kt:54`; `ui/components/ReviewPromptEffect.kt`
- **Severity:** Low
- **Confidence:** High
- **Likelihood:** Low. The window is the few to tens of milliseconds the record takes to write.
- **Defect:**
  - Before launching Play's flow, the gate saves "asked" (count +1, counters reset).
  - DataStore 1.3.0 finishes that write even if the caller is cancelled. The caller is cancelled if a sheet opens, the screen pauses or a failure happens during the write.
  - The gate then treats the request as never made and releases it. The next attempt reads the saved record, finds the counters at zero and settles without launching.
  - One of the two lifetime requests is spent, Play's flow never appears, and no "requested" event is logged.
- **Trigger:** The rating request fires, and the screen pauses or an overlay opens during the save.
- **Evidence / verification:**
  - In the pinned DataStore 1.3.0 source, the write runs in DataStore's own scope and only the wait for it is cancellable.
  - The storage helper catches only I/O errors.
  - Reported in an earlier audit on this branch; unchanged since.
- **Suggested fix:** Run the save without cancellation. If the caller was cancelled by then, restore the whole previous record (count, first-ask time and both counters), also without cancellation, and then rethrow.

### [c/api-or-library-misuse/media-index-sync/legacy-scanner-keeps-gone-playlists] On Android 7–9, deleted, moved or renamed playlist files stay listed in music apps

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/data/source/AndroidMediaIndexSync.kt:193`
  - Related: the design note at `:43-47` ("A row is never removed directly")
- **Severity:** Low
- **Confidence:** Medium. It relies on recall of the legacy `MediaScanner`; no source was available.
- **Likelihood:** Low. It needs Android 7–9 and a `.m3u`, `.pls` or `.wpl` file.
- **Defect:**
  - The new sync never deletes index rows. It rescans missing paths and relies on the scanner to drop their rows.
  - The legacy scanner deliberately keeps rows of missing playlist files ("do not delete missing playlists").
  - A deleted playlist therefore stays listed, and a moved or renamed one appears twice.
  - The baseline deleted the exact row.
- **Trigger:** Android 7–9; delete, move or rename a playlist file.
- **Evidence / verification:** The plan's "never remove a row" decision exists so the provider cannot delete a file that now sits at that path. It does not mention playlists, and assumes "a scan of a missing path drops its row". Android 10's scanner drops file playlists, so the problem is limited to 7–9.
- **Suggested fix:** Accept and document it, or on API 24–28 only, delete a missing playlist's exact row without deleting data, after re-checking that the path is still empty.

### [a/concurrency/swipe-analytics/overlapping-swipes-leave-open-row-untracked] Two overlapping swipes leave a row open on screen that swipe analytics no longer tracks

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/SwipeRevealTracker.kt:91-92`
  - Related: same file `:95-105`; `ui/screens/folder/FolderScreen.kt:506-512`; `ui/components/SwipeableFileListItem.kt:293-301`
- **Severity:** Low (analytics only; nothing visibly wrong)
- **Confidence:** Medium. The tracker half was reproduced by a unit test; the touch-handling half was traced in Compose 1.12.1 but not run.
- **Likelihood:** Low. It needs deliberate two-finger swipes on two rows.
- **Defect:** Sequence:
  1. Row A opens.
  2. Row B is released open during A's ~300 ms open animation. The tracker records A as closed by another row and holds B.
  3. A's animation ends and A claims the open row again. The tracker records B as closed and holds nothing.
  
  A is now open on screen but untracked: its later close is never sent, and a tap on its button sends a tap with no opening. The opened/closed pairing the analytics relies on breaks.
- **Trigger:** Swipe two rows open with two fingers, releasing the second during the first's animation.
- **Evidence / verification:**
  - A throwaway unit test replaying that sequence passed. It recorded "A opened, A closed (other row), B opened, B closed (other row)", and the following scroll close of A sent nothing.
  - Compose routes each pointer only to the row it hit, so both drags run independently.
- **Suggested fix:** Drive the tracker from changes to the screen's open row, or have a row report a close when it is closed by another row taking the open slot, instead of inferring from the order of "opened" reports.

### [c/api-or-library-misuse/swipe-analytics/open-duration-excludes-deep-sleep] Swipe analytics undercount how long a row stayed open when the phone slept meanwhile

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/SwipeRevealTracker.kt:52`
  - Related: same file `:103`, `:110`
- **Severity:** Low (analytics only)
- **Confidence:** High
- **Likelihood:** Low. The screen must go off while a row is open and less than 5 s of awake time must pass in total.
- **Defect:**
  - The "open for" bucket is timed with `System.nanoTime`, which on Android stops during deep sleep.
  - Locking the phone does not close the row, so a row opened, locked, then closed later is bucketed by awake time only. An open of hours can be reported as "1 to 5 s".
- **Trigger:** Open a row, lock the phone within a second or two, come back later and close it.
- **Evidence / verification:**
  - The Android `SystemClock` docs say `nanoTime` is "suitable for interval timing when the interval does not span device sleep".
  - The only lifecycle close runs when the screen is disposed, not on pause.
  - The code comment says this clock was chosen to keep JVM tests free of Android stubs, which is a testability choice, not a decision to undercount.
- **Suggested fix:** Inject `SystemClock.elapsedRealtimeNanos` in production and keep the injectable clock for tests.

### [b/resource-and-configuration-parity/media-viewer/playback-error-tagged-by-name-type] Playback errors for extensionless videos are filed as audio in crash reports

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/mediaviewer/MediaViewerViewModel.kt:203`
  - Related: same file `:164`, `:189-199`
- **Severity:** Low (diagnostics only)
- **Confidence:** High
- **Likelihood:** Low. It is reached only when no external app takes the type read from the file and the fallback finds none either.
- **Defect:**
  - The media viewer's "opened" analytics was updated to use the type read from the file's contents. The tag on its playback-error reports was not: it still comes from the name, which for an extensionless file says "audio".
  - Content detection is new on this branch, so extensionless videos can now reach the viewer and their errors are mis-grouped in Crashlytics.
- **Trigger:** An extensionless video opened in the in-app media viewer that hits an unexpected playback error.
- **Evidence / verification:** At the baseline an extensionless file could not reach the viewer, because its name type was "any".
- **Suggested fix:** Remember the type resolved for the "opened" event and use it for the error tag, falling back to the name's type.

### [a/resource-management/screenshot-scripts/temp-dir-leaked-on-early-exit] The website screenshot script leaves a temporary folder behind when it exits early

- **Location:** `scripts/website-screenshots.sh:26`
  - Related: same file `:39`, `:48-55`
- **Severity:** Low
- **Confidence:** High
- **Likelihood:** Low. The tool is run rarely, and it needs a run with no device or several devices, or a device that dies mid-run.
- **Defect:**
  - The temporary folder is created before the device check, but the cleanup trap is installed after it, so an early exit never removes it.
  - Separately, the cleanup runs with exit-on-error still on. If the device is gone, its first failing `adb` call ends it before the folder is removed.
- **Trigger:** Run the script with no emulator started, or with two devices connected.
- **Evidence / verification:**
  - The script is new on this branch.
  - The same defect was reported in an earlier audit on this branch and the script has not changed since.
  - The parallel Play screenshot script uses no such folder (its Python helper cleans up its own), so only this script is affected.
- **Suggested fix:** Create the folder just before installing the trap, and make the cleanup tolerate failing `adb` calls, removing the folder first.

## Summary

| # | Severity \ Confidence | High | Medium | Low |
|---|---|---|---|---|
| 1 | Medium | 2 | 2 | 0 |
| 2 | Low | 6 | 2 | 0 |

By severity: Medium (4 findings), Low (8 findings).

By confidence: High (8 findings), Medium (4 findings), Low (0 findings).

By Likelihood: High (1 finding), Medium (0 findings), Low (11 findings).
