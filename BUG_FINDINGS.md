# Bug Findings

## Medium

### [c/api-or-library-misuse/media-viewer/seek-enabled-for-unseekable-media] Seeking in a file the player cannot seek restarts it from 0:00

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/mediaviewer/MediaViewerScreen.kt:440`. Related:
  - `MediaViewerScreen.kt:488`
  - `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/mediaviewer/MediaViewerViewModel.kt:219`
  - `app/src/main/java/com/mauriciotogneri/fileexplorer/data/source/ExoMediaPlayback.kt:116`
  - Media3 1.11.1 `ProgressiveMediaPeriod.java:589-590`
- **Severity:** Medium
- **Confidence:** High
- **Likelihood:** Low. This happens only in the in-app fallback player, which runs when no installed app opens the file. It also needs one of the containers listed below.
- **Defect:** The seek bar is enabled whenever the length is known and above zero. Nothing asks the player whether the file can be seeked. ExoPlayer turns every seek into an unseekable file into a seek to 0. So dragging the thumb restarts playback from the beginning. The label briefly shows the target time, then snaps back to 0:00.
- **Trigger:** Open any of these in the media viewer, then drag the seek bar:
  - an AMR or ADTS AAC file short enough to be fully buffered;
  - a Matroska/WebM file with a duration but no cue index;
  - an FLV file without a keyframe index;
  - an AVI file without an index;
  - a raw AC-3, E-AC-3 or AC-4 file.

  The player uses the default extractors, which leave constant-bitrate seeking off.
- **Evidence / verification:**
  - Traced from the screen to the player: screen `seekable` (`durationMs != null && durationMs > 0`) → `MediaViewerViewModel.seekTo` → `ExoMediaPlayback.seekTo` → `exoPlayer.seekTo`.
  - Read in the Media3 1.11.1 sources jar:
    - `ExoPlayerImpl.seekTo` and `ExoPlayerImplInternal.seekToInternal` do not check seekability.
    - `ProgressiveMediaPeriod.seekToUs` sets `positionUs = seekMap.isSeekable() ? positionUs : 0`.
    - `onLoadCompleted` publishes a duration while keeping the unseekable map.
    - The AMR and ADTS extractors emit `SeekMap.Unseekable` when constant-bitrate seeking is off, and `DefaultExtractorsFactory` defaults that to off.
  - No app source references the player's seekability.
  - Separate refutation pass: looked for any seekability guard in the player and for buffer-seek fallbacks. None prevents the reset. The unit fake honours every seek, so no test covers this.
- **Suggested fix:** Expose the player's `isCurrentMediaItemSeekable` (the timeline window's seekability) through the playback interface. Enable the seek bar only when it is true. Optionally turn on constant-bitrate seeking in the extractors factory so AMR and ADTS become seekable.

## Low

### [a/logic-errors/pdf-viewer/page-indicator-uses-screen-middle] PDF page indicator shows the wrong page when pages are shorter than half the screen

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/pdfviewer/PdfViewerScreen.kt:607`. Related: `PdfViewerScreen.kt:570`, `:364`, and the scroll-to-page helper around `:419-446`.
- **Severity:** Low
- **Confidence:** High
- **Likelihood:** Medium. Landscape documents and slide decks are common, and on a portrait phone they always hit this whenever the in-app PDF viewer is used.
- **Defect:** The indicator reports the page under the vertical middle of the screen. When a page's height at screen width is below about half the viewport, that point is never inside page 1 or the last page:
  - At the top of the document the indicator shows "2 / N".
  - At the end it never shows "N".
  - Go-to-page and in-document links put the target page at the top, so the indicator reads the next page: "Go to page 4" shows "5 / N".
- **Trigger:** In the in-app viewer on a portrait phone, open a landscape A4/Letter PDF or a 16:9 slide deck.
- **Evidence / verification:** Worked example: 1080 px wide, 1841 px document area, 8 dp = 21 px, A4 landscape page height 763 px.
  - The middle maps to item offset 920 − 21 = 899.
  - Page 1 spans [0, 784) and page 2 spans [784, 1568), so the indicator shows "2".
  - The offset conventions were confirmed in the Compose foundation 1.12.1 sources: `viewportStartOffset = -beforeContentPadding`. The zoom transform is the identity at 1x.
  - The independent refutation agent re-derived the same arithmetic.
  - The tests use square 200×200 pt pages, which never take this path.
- **Suggested fix:** Pick the current page with rules that let page 1 and page N win:
  - the first fully visible page, or page 1 when the list is at its start;
  - the last page when the list cannot scroll further;
  - otherwise the page covering the most viewport.

  Make the indicator agree with the page a navigation event placed at the top.

### [a/logic-errors/pdf-viewer/page-indicator-covers-last-page] The page indicator chip permanently hides the bottom centre of the last page at 1x

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/pdfviewer/PdfViewerScreen.kt:264`. Related: `PdfViewerScreen.kt:364`.
- **Severity:** Low
- **Confidence:** High
- **Likelihood:** Medium. It happens on every document whose last page has content near the bottom centre, such as a page-number footer.
- **Defect:**
  - The chip is always shown and opaque, sits 16 dp above the bottom, and is about 36 dp tall.
  - The list's bottom content padding is only 8 dp, and at 1x the zoom state allows no vertical pan.
  - So the bottom centre of the last page, from 8 to 44 dp above its edge, can never be uncovered without zooming in.
- **Trigger:** Scroll to the end of any document at 1x zoom.
- **Evidence / verification:**
  - Layout traced: the chip is aligned to the bottom centre with 16 dp padding, and `labelLarge` gives a 20 sp line plus 8 dp padding on each side.
  - With `contentPadding = PaddingValues(vertical = 8.dp)` and `maxOffsetY = (scale − 1) * centerY = 0` at 1x, nothing can scroll the last page higher.
  - Refutation pass: the chip has no fade or scroll-dependent visibility, and no extra bottom inset reaches the list. Zooming in is the only workaround.
- **Suggested fix:** Give the list bottom content padding at least as tall as the chip plus its margin. Alternatively, hide or fade the chip while the user is at the end of the document or not scrolling.

### [a/error-handling/feedback/http-error-reported-as-success] Feedback the server rejected is reported as sent and the message is discarded

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/FeedbackActivity.kt:157`. Related: `FeedbackActivity.kt:97`, `:158-162`.
- **Severity:** Low
- **Confidence:** Medium
- **Likelihood:** Low. It needs the feedback endpoint to answer with an error status without storing the message, such as a removed deployment, rate limiting or a front-end 5xx.
- **Defect:**
  - The submit now closes the HTTP response unread and always takes the success path. It records `feedback_submit_success`, finishes the screen and discards the typed text.
  - When the endpoint rejects the request before storing it, the user is told the feedback was sent, and their text is lost.
  - Analytics also counts a success, so an outage of the endpoint is invisible.
  - The baseline checked `response.isSuccessful` and kept the screen and text open on failure.
- **Trigger:** Submit feedback while the endpoint answers 404, 429 or 5xx without running the script.
- **Evidence / verification:**
  - `execute().close()` is followed unconditionally by `trackFeedbackSubmitSuccess()` and `onSuccess()`, which is `finish()`.
  - The client is a plain `OkHttpClient()` with no interceptor. OkHttp does not throw on non-2xx responses.
  - The change is deliberate:
    - A code comment says the script can answer with an error status after storing the message.
    - The instrumentation test `feedbackScreen_serverErrorStatus_reportsSuccess` locks in "500 means success".
  - The trade-off covers statuses returned after the message was stored, but also swallows those returned before anything was stored.
  - The independent refutation pass found no other signal or retry.
- **Suggested fix:** Keep treating the status codes the script itself returns after storing as success. Treat statuses that can only come from in front of the script as failures: 404, 408, 429, 5xx from the front end, or anything that is not a script response. Alternatively, have the script return a marker in the body and check for it, so a missing marker counts as an error and keeps the text.

### [a/state-and-lifecycle/media-viewer/autoplay-after-process-death] After process death, returning to a paused media viewer starts playing from 0:00

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/mediaviewer/MediaViewerViewModel.kt:169`. Related: `MediaViewerViewModel.kt:170`, `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/MediaViewerActivity.kt:25`.
- **Severity:** Low
- **Confidence:** High
- **Likelihood:** Low. It needs the system to kill the backgrounded process while the viewer is on the back stack, and the user to return to it.
- **Defect:**
  - Leaving the viewer pauses playback.
  - When the process is killed and the user returns from Recents, the activity is recreated from its intent with a fresh view model. Its `init` opens the file and calls `play()` unconditionally.
  - So media the user left paused starts playing aloud from the beginning. The position is lost, and the opened, file-opened and recent-file tracking fire again.
  - This contradicts the view model's own contract that nothing resumes playback but the user.
- **Trigger:** Play media → Home (playback pauses) → the process is killed (low memory, or "Don't keep activities") → return to the app.
- **Evidence / verification:**
  - The activity never reads `savedInstanceState`.
  - The app's main sources have no `SavedStateHandle`.
  - `onStart` returns early because `stopped` is false in a new instance.
  - The independent refutation pass confirmed nothing restores the paused state.
- **Suggested fix:**
  - Persist the position and the playing or paused state in a `SavedStateHandle`.
  - On restore, seek to the saved position and only call `play()` if the user had not paused. A process-death restore should start paused.
  - Skip the "opened" tracking when the state was restored.

### [a/resource-management/media-viewer/surface-holder-retains-activity] The player keeps a destroyed Activity alive after the video view leaves the screen

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/mediaviewer/MediaViewerScreen.kt:353`. Related: `MediaViewerScreen.kt:341`; Media3 ui-compose `PlayerSurface.kt:142-178`; `ExoPlayerImpl.java:219` and `3571-3576`.
- **Severity:** Low
- **Confidence:** High
- **Likelihood:** Low. It needs a video that was showing to fail or lose its video track, followed by a rotation while the error screen is up.
- **Defect:**
  - Media3's `PlayerSurface` clears the video view only when the player changes or becomes null, never when it leaves composition.
  - When the content leaves the ready-with-video state, `ContentFrame` is disposed, but the retained view model's ExoPlayer keeps the old `SurfaceView`'s holder.
  - After a rotation that holder pins the destroyed Activity and its view tree until the viewer closes.
- **Trigger:** A video plays, then fails mid-playback (or its video track is deselected), then the device rotates while the error screen shows.
- **Evidence / verification:**
  - Sources read:
    - `PlayerSurface`'s `onDispose` only removes a listener.
    - `ExoPlayerImpl.surfaceDestroyed` clears the output but keeps the `surfaceHolder` field.
    - Only set, clear or release drops that field.
  - The app never calls `clearVideoSurface*`.
  - Once in the error state, the content never returns to Ready, so no new view replaces the holder.
  - The independent refutation pass confirmed this. The leak is bounded to one Activity and ends when the view model is cleared.
- **Suggested fix:** When the video frame leaves composition, call `player.clearVideoSurface()` from a `DisposableEffect` in the video frame, or from the view model when the content leaves ready-with-video.

### [a/state-and-lifecycle/media-viewer/immersive-mode-not-reapplied] Fullscreen video shows the system bars again after returning from the background on Android 7–10

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/mediaviewer/MediaViewerScreen.kt:667`.
- **Severity:** Low
- **Confidence:** Medium
- **Likelihood:** Low. It needs API 24–29, fullscreen video in the fallback player, and leaving and returning.
- **Defect:**
  - Immersive mode is applied once, in a disposable effect keyed on `enabled`, `window` and `view`.
  - Below API 30, androidx.core implements `hide()` with legacy system-UI visibility flags. The platform clears those flags when the user navigates away, and documents that apps must re-apply them on resume or focus.
  - The effect's keys do not change when the activity merely stops and starts, so nothing re-applies the flags.
  - The status and navigation bars come back over the video while the screen still believes it is fullscreen.
- **Trigger:** API 24–29: enter fullscreen video → Home (or screen off) → return.
- **Evidence / verification:**
  - androidx.core 1.19.1 `WindowInsetsControllerCompat` Impl20 (`hide` ORs `SYSTEM_UI_FLAG_FULLSCREEN | SYSTEM_UI_FLAG_HIDE_NAVIGATION`) has no resume or focus listener.
  - The app has no `onWindowFocusChanged` or `onResume` override.
  - `onStop` only pauses playback and keeps fullscreen on.
  - The independent refutation pass did not refute it.
  - Not run on a device: the flag-clearing step rests on platform documentation.
- **Suggested fix:** Re-apply `hide(systemBars())` on `ON_RESUME`, or when the window regains focus, while fullscreen is enabled. For example, use a lifecycle event effect inside the immersive-mode helper.

### [a/concurrency/swipe-actions/action-cancelled-by-competing-animation] Tapping a revealed swipe action can be silently dropped

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/components/SwipeableFileListItem.kt:134`. Related: `SwipeableFileListItem.kt:157-180`, `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderScreen.kt:163-176`.
- **Severity:** Low
- **Confidence:** High
- **Likelihood:** Low. It needs a second gesture, either a flick of the list or a drag on another row, within the roughly 350 ms close animation.
- **Defect:**
  - The action button launches `offsetX.animateTo(0f)`, then `onRevealedChange(false)`, then `onSwipeAction(action)`.
  - If the folder screen takes the open row away during that animation, the row's new `LaunchedEffect(isRevealed)` starts its own `animateTo(0f)`. Triggers are a scroll start, an overlay appearing, or another row starting a drag.
  - Animatable's mutator cancels the button's in-flight animation with a `CancellationException`, which ends the coroutine.
  - So Rename or Delete is never invoked. Only the swipe-tapped analytics event fires.
- **Trigger:** Swipe a row open, tap Rename or Delete, and immediately flick the list or start swiping another row.
- **Evidence / verification:**
  - Compose animation-core 1.12.1 `Animatable.animateTo` documents that an interrupted animation throws `CancellationException` and cancels the caller's subsequent work.
  - `InternalMutatorMutex` cancels the previous mutator's job.
  - The baseline had neither the `isRevealed` effect nor the scroll, overlay or other-row closers.
  - The independent refutation pass confirmed this.
- **Suggested fix:** Run the action independently of the close animation. One way is to call `onSwipeAction(action)` and `onRevealedChange(false)` before or regardless of the animation, for example in a `try/finally` or by launching the animation separately. Another is to make the `isRevealed` effect skip when the row is already animating closed.

### [a/error-handling/pdf-viewer/link-opened-tracked-before-success] "Link opened" analytics fire even when the link could not be opened

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/pdfviewer/PdfViewerViewModel.kt:399`. Related: `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/pdfviewer/PdfViewerScreen.kt:158`.
- **Severity:** Low
- **Confidence:** High
- **Likelihood:** Low. It needs a web or mail link on a device with no app accepting a browsable intent for it.
- **Defect:**
  - The view model records `pdf_viewer_link_opened` (`external`) before it emits the event that opens the link.
  - When `IntentUtil.openExternalLink` returns false, the screen shows the "Unable to open this link" toast. The event has already counted the link as opened, and no failure event exists.
  - This departs from the app's open-file convention, which tracks `file_opened` only after a successful launch and has a separate `file_open_failed`.
- **Trigger:** Tap a `mailto:` or `https:` link in a PDF on a device with no handler for it.
- **Evidence / verification:**
  - Traced from the tap to the toast: `onPageTapped` → `trackPdfViewerLinkOpened` → `OpenExternalLink` event → `openExternalLink` returns false → toast.
  - The independent refutation pass found no compensating event and no tracker documentation that defines the event as a tap.
- **Suggested fix:** Have the screen report the outcome after `openExternalLink` returns. Track the link as opened only when it returns true, and add a failure event, or a result parameter, when it returns false.

### [a/state-and-lifecycle/pdf-viewer/one-shot-events-dropped-during-recreation] PDF viewer drops its finish and toast events if they fire while the activity is being recreated

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/pdfviewer/PdfViewerViewModel.kt:127`. Related: `PdfViewerViewModel.kt:541`, `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/pdfviewer/PdfViewerScreen.kt:145`.
- **Severity:** Low
- **Confidence:** Medium
- **Likelihood:** Low. The event must be emitted in the short gap while a rotation recreates the activity.
- **Defect:**
  - Events go through `MutableSharedFlow()` with no replay or buffer. The only collector is a `LaunchedEffect` tied to the composition.
  - The activity declares no `configChanges`, so a rotation disposes the collector until the new composition starts.
  - An event emitted in that gap is discarded:
    - `Finish` after a confirmed delete, which leaves the viewer open on a file that no longer exists;
    - a delete-failure toast;
    - a scroll-to-match or link event.
- **Trigger:** Confirm Delete in the PDF viewer, or submit a search, and rotate the device before the operation completes.
- **Evidence / verification:**
  - With zero subscribers, `emit` on a `MutableSharedFlow(replay = 0, extraBufferCapacity = 0)` returns immediately and drops the value.
  - `onDeleteConfirmed` emits `Finish` only after the delete completes on IO.
  - Separate refutation: checked for a retained collector or a state-based fallback, and found none.
  - The baseline image viewer uses the same pattern, but this viewer is new code.
- **Suggested fix:** Deliver one-shot events through a buffered `Channel` received with `receiveAsFlow()`, or model `Finish` as state, for example a `finished` flag in the UI state that the screen observes. Then an event emitted during recreation is delivered to the new collector.

### [a/boundary-and-encoding-cases/localization/mixed-digit-scripts-in-page-range] Bengali and Arabic go-to-page texts mix Latin and native digits

- **Location:** `app/src/main/res/values-bn/strings.xml:475`. Related:
  - `app/src/main/res/values-bn/strings.xml:476`
  - `app/src/main/res/values-ar/strings.xml:547-548`
  - `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/pdfviewer/PdfViewerScreen.kt:782`, `:785`
- **Severity:** Low
- **Confidence:** Medium
- **Likelihood:** Low. It needs a Bengali, or non-Maghreb Arabic, device locale and the in-app PDF viewer's go-to-page dialog.
- **Defect:** The hint and the invalid-number message keep a literal ASCII "1" next to `%d`. `stringResource(id, pageCount)` formats `%d` with the resources locale, which writes Bengali or Arabic-Indic digits. The result is a range that mixes two numeral systems:
  - Bengali: `(1–১২)`
  - Arabic: `من 1 إلى ١٢`
- **Trigger:** Device language Bengali or Arabic (for example ar-EG or ar-SA): open a PDF in the in-app viewer, tap the page indicator, and type an out-of-range number.
- **Evidence / verification:**
  - Compose `stringResource` → `Resources.getString(id, args)` → `String.format(configuration locale, …)`.
  - A JVM check with CLDR data printed `(1–১২)` for `bn` and `1 إلى ١٢` for `ar`, and Latin digits for `hi`.
  - No baseline bn or ar string mixes an ASCII digit with a placeholder.
  - Not checked on an Android device. Android's ICU follows the same CLDR default numbering systems.
- **Suggested fix:** Pass the lower bound as an argument too, for example `(%1$d–%2$d)` with `1, pageCount`, in every locale, so both numbers are formatted the same way.

### [b/resource-and-configuration-parity/media-viewer/controls-ignore-display-cutout] Media viewer controls can sit under a display cutout in landscape

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/mediaviewer/MediaViewerScreen.kt:241`. Related: `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/MediaViewerActivity.kt:27`.
- **Severity:** Low
- **Confidence:** Medium
- **Likelihood:** Low. It needs a device whose cutout lands at the bottom corner in landscape, with the player not in fullscreen.
- **Defect:**
  - The activity draws edge to edge, cutout areas included.
  - The non-fullscreen control bar is passed to the Scaffold `bottomBar` slot padded only by `WindowInsets.navigationBars`. Material3's Scaffold applies no insets to a custom bottom bar.
  - With only 12–24 dp of side padding, the mute button, time label or seek-track start can be drawn partly under a corner camera cutout.
  - Every other bar changed in this release includes the cutout in its insets: the fullscreen overlay in the same file, `PickerBottomBar`, and the PDF viewer's `BottomAppBar`.
- **Trigger:** A phone with a corner punch-hole, rotated so the hole is at a bottom corner, playing media outside fullscreen.
- **Evidence / verification:**
  - Code read at the cited lines.
  - Material3 `Scaffold` passes `contentWindowInsets` only to the content slot, and the bottom bar receives none.
  - Separate refutation: checked for a parent inset or a Scaffold default reaching this bar, and found none.
  - Not run on a device: whether the controls actually overlap depends on the cutout's position.
- **Suggested fix:** Pad the bar with `WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(Horizontal + Bottom)`, as the fullscreen overlay and `PickerBottomBar` already do.

## Summary

Findings by severity: Critical 0, High 0, Medium 1, Low 11.

Findings by confidence: High 7, Medium 5, Low 0.

| Severity | High | Medium | Low |
| --- | --- | --- | --- |
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 1 | 0 | 0 |
| Low | 6 | 5 | 0 |

By Likelihood: High (0 findings), Medium (2 findings), Low (10 findings).
