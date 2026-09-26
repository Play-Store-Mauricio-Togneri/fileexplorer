# Bug Findings

## Medium

### [a/null-and-numeric-hazards/pdf-viewer/page-aspect-clamp-int-overflow] A very wide page overflows the height clamp and crashes the viewer

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/PdfViewerSupport.kt:126`
  - Related: `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/pdfviewer/PdfViewerViewModel.kt:226` and `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/pdfviewer/PdfViewerScreen.kt:507`
- **Severity:** Medium
- **Confidence:** Medium
- **Likelihood:** Low. It needs a crafted or broken PDF with a page more than about 42.9 million points wide. The in-app viewer also only opens when no installed app handles PDFs.
- **Defect:** `layoutPageSize` computes `size.width * MAX_PAGE_ASPECT` in `Int`.
  - For any width above `Int.MAX_VALUE / 50` (42,949,672), the product wraps to a negative number, and `coerceAtMost` then returns that negative number as the page height.
  - `PdfPage` passes `pageSize.width.toFloat() / pageSize.height` to `Modifier.aspectRatio`, which runs `require(aspectRatio > 0)`.
  - The result is an `IllegalArgumentException` during composition on the main thread, which crashes the app.
- **Trigger:** open a PDF with a page whose MediaBox is, for example, `[0 0 50000000 100]`.
  - The width becomes 50,000,000 and the height 100.
  - `50_000_000 * 50` wraps to `-1_794_967_296`.
- **Evidence / verification:**
  - Traced path: `openDocument` → `pageSizeOrFallback`. That function checks `width > 0 && height > 0` only on the raw size, before `layoutPageSize`, so it doesn't catch the overflow. Then `Loaded(pageSizes)` → `PdfPage` → `aspectRatio`.
  - Wraparound confirmed by arithmetic.
  - `require(aspectRatio > 0)` confirmed in Compose `foundation-layout` 1.12.0 `AspectRatio.kt:76`.
  - Refutation attempt: `renderSize` does `coerceAtLeast(1)`, but it only runs later, at render time. `PdfPageSize` has no validation, and the unit tests only cover `(1, 1000)`, `(595, 842)` and `(164, 7200)`.
  - Remaining assumption: the platform renderer reports the MediaBox width without clamping it below 42.9M points. Not reproduced on a device.
- **Suggested fix:** compute the cap in `Long` and saturate it, e.g. `(size.width.toLong() * MAX_PAGE_ASPECT).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()`. Also add a unit test with a width above `Int.MAX_VALUE / 50`.

### [c/api-or-library-misuse/media-viewer/exceeds-capabilities-tracks-rejected] Files whose only tracks exceed the device's advertised decoder capabilities are refused without trying

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/data/source/ExoMediaPlayback.kt:51`
- **Severity:** Medium
- **Confidence:** Medium
- **Likelihood:** Low. It needs a file with no fully supported audio or video track. The fallback viewer, though, is only reached for files no installed app could open, which leans toward unusual formats.
- **Defect:** `onTracksChanged` stops the player and reports `NoPlayableTrackException` when `!tracks.isTypeSupported(C.TRACK_TYPE_AUDIO) && !tracks.isTypeSupported(C.TRACK_TYPE_VIDEO)`.
  - The one-argument `Tracks.isTypeSupported(type)` means `allowExceedsCapabilities = false` (Media3 1.11.1 `Tracks.java:311-313`). So a track that `MediaCodec{Audio,Video}Renderer` marks `FORMAT_EXCEEDS_CAPABILITIES` counts as unsupported.
  - `DefaultTrackSelector` defaults to `exceedRendererCapabilitiesIfNecessary = true`, so ExoPlayer would select and try such a track, and it often plays.
  - The viewer shows "Unable to play this file" instead.
- **Trigger:** two examples:
  - A video-only clip, such as a muted screen recording, whose resolution, profile or level exceeds the decoder's advertised limits.
  - A video with such a track whose audio is in a format with no decoder on the device, such as DTS or AC-3 in an MKV.
- **Evidence / verification:**
  - Checked against Media3 1.11.1 sources from the Gradle cache:
    - `Tracks.isTypeSupported(int)` delegates with `false`.
    - `MediaCodecAudioRenderer` and `MediaCodecVideoRenderer` return `FORMAT_EXCEEDS_CAPABILITIES` when no decoder's `isFormatSupported` passes.
    - `DefaultTrackSelector` initial value `exceedRendererCapabilitiesIfNecessary = true` at `DefaultTrackSelector.java:1805`.
  - Refutation attempt: a file with at least one fully supported track of either type passes the check, so common files are unaffected.
  - Remaining uncertainty: how often an exceeds-capabilities track actually decodes on a given device.
- **Suggested fix:** use `tracks.isTypeSupported(type, /* allowExceedsCapabilities = */ true)` for both checks. Let ExoPlayer's own decoder errors, which `isUnplayableMedia` already treats as expected, handle the tracks that really fail.

## Low

### [a/state-and-lifecycle/media-viewer/process-death-restarts-and-autoplays] After process death the player restarts from zero and plays without being asked

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/mediaviewer/MediaViewerViewModel.kt:130`
  - Related: the class KDoc at `MediaViewerViewModel.kt:58-60`
- **Severity:** Low
- **Confidence:** High
- **Likelihood:** Low. It needs the system to kill the backgrounded process while the viewer is the top activity, and the user to return to the task.
- **Defect:** the ViewModel's `init` always calls `playback.open(...)` and then `playback.play()`, and nothing records the position or play state across process death (no `SavedStateHandle`).
  - The class contract says "onStop pauses it, and nothing resumes it but the user".
  - When the activity is rebuilt from its intent after process death, a new ViewModel starts the file from 0 and plays it on its own.
  - The user loses their position, and audio or video starts unprompted.
- **Trigger:**
  1. Pause a long video or audio file part-way through.
  2. Background the app.
  3. Let the system kill the process, or use "Don't keep activities" or `adb shell am kill`.
  4. Return to the task.
- **Evidence / verification:**
  - Traced: `MediaViewerActivity.onCreate` → `viewModel(factory = MediaViewerViewModel.Factory(...))` → `init { open; play() }`.
  - Rotation is handled, because the ViewModel survives and `LifecycleEventEffect(ON_STOP)` skips `onStop` while `isChangingConfigurations`. Process death is not handled.
  - Refutation attempt: no saved-state path exists in the Activity, the Factory or the ViewModel.
- **Suggested fix:** keep the position and the "user paused" flag in a `SavedStateHandle`. On restore, seek to the saved position and skip the automatic `play()`.

### [b/contract-mismatches/media-viewer/unknown-duration-shown-as-zero] An unknown duration is shown as `00:00`

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/data/source/ExoMediaPlayback.kt:35`
  - Related: `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/mediaviewer/MediaViewerScreen.kt:362-364` and `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/mediaviewer/MediaViewerViewModel.kt:111`
- **Severity:** Low
- **Confidence:** Medium
- **Likelihood:** Low. It only affects files ExoPlayer cannot time, such as ADTS `.aac` or WebM/MKV without a Duration element.
- **Defect:** `exoPlayer.duration.coerceAtLeast(0)` turns `C.TIME_UNSET` into 0, and the UI can't tell "unknown" from "zero length".
  - The time text reads, for example, `01:23 / 00:00`.
  - At the end, `onEnded` sets `positionMs = durationMs`, which snaps the shown position back to `00:00`.
  - The slider is correctly disabled, but the duration shown is wrong.
- **Trigger:** play an ADTS AAC file, or any file with no container duration, in the in-app viewer.
- **Evidence / verification:**
  - Traced: `onPlaybackStateChanged(STATE_READY)` → `onReady(duration.coerceAtLeast(0))` → `durationMs = 0` → `MediaTimeFormatter.format(durationMs, durationMs)` renders `00:00`.
  - Refutation attempt: no crash, because `valueRange` uses `coerceAtLeast(1)` and `enabled = durationMs > 0`.
  - Remaining uncertainty: which containers leave the duration unset.
- **Suggested fix:** carry "unknown" explicitly, as a nullable or `C.TIME_UNSET`. Show only the position (or `--:--`) while it is unknown, skip the `positionMs = durationMs` snap in that case, and refresh the duration on `onTimelineChanged`.

### [a/state-and-lifecycle/pdf-viewer/zoom-offset-not-reclamped-on-resize] Zoom pan offsets are not re-clamped when the viewer shrinks, leaving an empty strip

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/pdfviewer/PdfZoomState.kt:44`
  - Related: `PdfZoomState.kt:47-48` and `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/pdfviewer/PdfViewerScreen.kt:322`
- **Severity:** Low
- **Confidence:** Medium
- **Likelihood:** Low. It needs the document zoomed in with the pan near its bound at the moment the viewer area shrinks.
- **Defect:** `maxOffsetX` and `maxOffsetY` are derived from `viewport`, and `onSizeChanged` only assigns `viewport`.
  - `offsetX` and `offsetY` keep their old values even when they now exceed the smaller bounds.
  - The scaled list is translated past the viewport edge, so an empty strip of about `(scale - 1) * Δh / 2` px shows until the next pan or zoom re-clamps it.
- **Trigger:**
  1. Zoom to about 4× with the top of the document aligned.
  2. Run a search that finds nothing, so the search status row appears and shrinks the content area by one row. Resizing a multi-window split also triggers it.
  - With matches, `scrollToPage` calls `alignTop()`, which hides the gap.
- **Evidence / verification:**
  - Traced: `pan`, `zoomTo`, `placeAt`, `centerOn` and `alignTop` are the only writers of the offsets, and all clamp at write time. The `viewport` setter doesn't clamp.
  - Refutation attempt: rotation rebuilds the `remember`ed state, so it doesn't trigger this. The IME doesn't either, because Scaffold's default insets exclude it.
  - Not reproduced on a device.
- **Suggested fix:** re-clamp `offsetX` and `offsetY` to the new bounds whenever `viewport` changes, for example in a custom setter or in the `onSizeChanged` callback.

## Summary

By severity: Medium (2 findings), Low (3 findings)

By confidence: High (1 finding), Medium (4 findings), Low (0 findings)

| Severity | High | Medium | Low |
|---|---|---|---|
| Medium | 0 | 2 | 0 |
| Low | 1 | 2 | 0 |

By Likelihood: High (0 findings), Medium (0 findings), Low (5 findings)
