# Bug Findings

## Medium

### [a/null-and-numeric-hazards/pdf-viewer/page-aspect-clamp-int-overflow] A very wide page overflows the height clamp and crashes the viewer

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/PdfViewerSupport.kt:126`
    - Related:
      `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/pdfviewer/PdfViewerViewModel.kt:226`
      and
      `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/pdfviewer/PdfViewerScreen.kt:507`
- **Severity:** Medium
- **Confidence:** Medium
- **Likelihood:** Low. It needs a crafted or broken PDF with an absurdly wide page, and the in-app
  viewer only opens when no installed app handles PDFs.
- **Defect:** `layoutPageSize` computes `size.width * MAX_PAGE_ASPECT` in `Int`.
    - When the product wraps negative, `coerceAtMost` returns a negative page height.
    - `PdfPage` then passes `pageSize.width.toFloat() / pageSize.height` to `Modifier.aspectRatio`,
      whose `AspectRatioElement` runs `requirePrecondition(aspectRatio > 0)`.
    - The result is an `IllegalArgumentException` during composition on the main thread, which
      crashes the app.
    - For widths where the product wraps to a small positive number instead, the page is silently
      cut to that height. For example, a width of 85,899,346 gives a height of 4.
- **Trigger:** the product wraps in bands, so there are two ways to get a negative result.
    - A page width in 42,949,673–85,899,345 points, e.g. MediaBox `[0 0 50000000 100]`, which gives
      `50_000_000 * 50 = -1_794_967_296`.
    - Any absurdly large width that the native double-to-int conversion saturates to
      `Int.MAX_VALUE`, which gives `-50`. Saturation is ARM64 behaviour and formally undefined in
      C++.
- **Evidence / verification:**
    - Traced: `openDocument` → `pageSizeOrFallback`, which checks `width > 0 && height > 0` before
      `layoutPageSize`, not after → `Loaded(pageSizes)` → `PdfPage` → `aspectRatio`. Nothing in
      between sanitizes the size, and `produceState`'s initial value is only a cache lookup.
    - `requirePrecondition(aspectRatio > 0)` confirmed in Compose `foundation-layout` 1.12.0
      `AspectRatio.kt:79`.
    - The platform doesn't clamp the width:
        - MediaProvider `pdfClient/page.cc` `Page::Width()` returns `FPDF_GetPageWidth` truncated to
          `int`.
        - `PdfRendererPreV` and the API 35 `PdfRenderer` store it unchanged.
        - The classic API ≤34 JNI was not read.
    - Independent refutation pass: confirmed.
    - Not reproduced on a device.
- **Suggested fix:** compute the cap in `Long` and saturate it, e.g.
  `(size.width.toLong() * MAX_PAGE_ASPECT).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()`. Add unit
  tests for a width in the negative band and for `Int.MAX_VALUE`.

### [c/api-or-library-misuse/media-viewer/exceeds-capabilities-tracks-rejected] Files whose only tracks exceed the device's advertised decoder capabilities are refused without trying

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/source/ExoMediaPlayback.kt:51`
- **Severity:** Medium
- **Confidence:** Medium
- **Likelihood:** Low. It needs a file with no fully supported audio or video track. The fallback
  viewer, though, is only reached for files no installed app could open, which leans toward unusual
  formats.
- **Defect:** `onTracksChanged` stops the player and reports `NoPlayableTrackException` when
  `!tracks.isTypeSupported(C.TRACK_TYPE_AUDIO) && !tracks.isTypeSupported(C.TRACK_TYPE_VIDEO)`.
    - The one-argument `Tracks.isTypeSupported(type)` means `allowExceedsCapabilities = false`. So a
      track that `MediaCodec{Audio,Video}Renderer` marks `FORMAT_EXCEEDS_CAPABILITIES` counts as
      unsupported.
    - `DefaultTrackSelector` defaults to `exceedRendererCapabilitiesIfNecessary = true`, so
      ExoPlayer would select and try such a track, and it often plays.
    - The viewer shows "Unable to play this file" instead.
- **Trigger:** two examples:
    - A video-only clip, such as a muted screen recording, whose resolution, profile or level
      exceeds the decoder's advertised limits.
    - A video with such a track whose audio has no decoder on the device, such as DTS or AC-3 in an
      MKV. That audio is `FORMAT_UNSUPPORTED_SUBTYPE`, so the video track's support alone decides.
- **Evidence / verification:** checked against Media3 1.11.1 sources.
    - `Tracks.java:311-313` delegates with `false`.
    - `MediaCodecVideoRenderer.java:823` and `MediaCodecAudioRenderer.java:403` return
      `FORMAT_EXCEEDS_CAPABILITIES` when a decoder exists but fails `isFormatSupported`.
    - `DefaultTrackSelector.java:1805` sets `exceedRendererCapabilitiesIfNecessary = true`.
      Eligibility at `:3964` honours it.
    - Independent refutation pass: confirmed. Nothing in the player refuses such tracks up front. A
      real decoder failure would arrive as `DECODER_INIT_FAILED` or
      `DECODING_FORMAT_EXCEEDS_CAPABILITIES`, which `MediaErrors.kt` already classifies as expected.
    - Remaining uncertainty: how often an exceeds-capabilities track actually decodes on a given
      device.
- **Suggested fix:** use `tracks.isTypeSupported(type, /* allowExceedsCapabilities = */ true)` for
  both checks. Let ExoPlayer's own decoder errors, already treated as expected, handle the tracks
  that really fail.

## Low

### [b/contract-mismatches/media-viewer/unknown-duration-shown-as-zero] An unknown duration is shown as
`00:00` and never refreshed

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/source/ExoMediaPlayback.kt:35`
    - Related:
      `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/mediaviewer/MediaViewerScreen.kt:361-366`
      and
      `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/mediaviewer/MediaViewerViewModel.kt:111`
- **Severity:** Low
- **Confidence:** High
- **Likelihood:** Low. It only affects files ExoPlayer cannot time up front. The most realistic case
  is WebM without a Duration element, which is typical of browser `MediaRecorder` output. ADTS
  `.aac` is another.
- **Defect:** `exoPlayer.duration.coerceAtLeast(0)` turns `C.TIME_UNSET` into 0, and the UI can't
  tell "unknown" from "zero length".
    - The time text reads, for example, `01:23 / 00:00`.
    - At the end, `onEnded` sets `positionMs = durationMs`, which snaps the shown position back to
      `00:00`.
    - Seeking is dead: the slider is disabled and `seekTo` coerces to `[0, 0]`.
    - The duration is only read in the `STATE_READY` callback. So when ExoPlayer later learns the
      duration, because loading finished and the timeline was refreshed, the UI doesn't pick it up
      until another READY transition.
- **Trigger:** play a WebM with no Duration element, or an ADTS AAC file, in the in-app viewer.
- **Evidence / verification:** Media3 1.11.1:
    - `AdtsExtractor.java:294` emits `SeekMap.Unseekable(C.TIME_UNSET)`, because constant-bitrate
      seeking is off by default.
    - `MatroskaExtractor` starts with `durationUs = C.TIME_UNSET` (`:481`) and emits
      `Unseekable(durationUs)`.
    - `ProgressiveMediaPeriod.setSeekMap` (`:969`) takes that duration. Only `onLoadCompleted` fills
      it in later, via `onSourceInfoRefreshed`.
    - Independent refutation pass: confirmed. Nothing in `MediaViewerScreen` hides the text when the
      duration is 0.
- **Suggested fix:** carry "unknown" explicitly, as a nullable or `C.TIME_UNSET`. Show only the
  position (or `--:--`) while it is unknown, skip the `positionMs = durationMs` snap in that case,
  and refresh the duration from `onTimelineChanged`.

### [a/state-and-lifecycle/media-viewer/process-death-restarts-and-autoplays] After process death the player restarts from zero and plays without being asked

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/mediaviewer/MediaViewerViewModel.kt:130`
    - Related: the class KDoc at `MediaViewerViewModel.kt:58-60`
- **Severity:** Low
- **Confidence:** High
- **Likelihood:** Low. It needs the system to kill the backgrounded process, or "Don't keep
  activities" to be on, while the viewer is the top activity.
- **Defect:** the ViewModel's `init` always calls `playback.open(...)` and then `playback.play()`,
  and nothing records the position or play state across process death (no `SavedStateHandle`).
    - The class contract says "onStop pauses it, and nothing resumes it but the user".
    - When the activity is rebuilt, a new ViewModel starts the file from 0 and plays it on its own.
    - The user loses their position, and audio or video starts unprompted.
- **Trigger:**
    1. Pause a long video or audio file part-way through.
    2. Background the app.
    3. Let the system kill the process, or use "Don't keep activities".
    4. Return to the task.
- **Evidence / verification:**
    - Traced: `MediaViewerActivity.onCreate` →
      `viewModel(factory = MediaViewerViewModel.Factory(...))` → `init { open; play() }`.
    - `onCreate` never reads `savedInstanceState`.
    - The only `rememberSaveable` in the screen is `controlsShown`.
    - The manifest entry has no `configChanges`.
    - Rotation is handled correctly, because the ViewModel survives and `ON_STOP` is skipped while
      `isChangingConfigurations`.
    - Independent refutation pass: confirmed.
- **Suggested fix:** keep the position and the "user paused" flag in a `SavedStateHandle`. On
  restore, seek to the saved position and skip the automatic `play()`.

### [a/state-and-lifecycle/pdf-viewer/zoom-offset-not-reclamped-on-resize] Zoom pan offsets are not re-clamped when the viewer shrinks, leaving an empty strip

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/pdfviewer/PdfZoomState.kt:44`
    - Related: `PdfZoomState.kt:47-48` and
      `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/pdfviewer/PdfViewerScreen.kt:322`
- **Severity:** Low
- **Confidence:** Medium
- **Likelihood:** Low. It needs the document zoomed in with the pan near its vertical bound when the
  first search is submitted, and the search to find nothing.
- **Defect:** `maxOffsetX` and `maxOffsetY` are derived from `viewport`, and `onSizeChanged` only
  assigns `viewport`.
    - `offsetX` and `offsetY` keep their old values even when they now exceed the smaller bounds.
    - `graphicsLayer` reads the raw offsets, so the scaled list is translated past the viewport
      edge. An empty strip of about `(scale - 1) * Δh / 2` shows (about 72 dp at 4× for a 48 dp row)
      until the next pan or zoom re-clamps it.
- **Trigger:**
    1. Zoom in, with the top of the document aligned.
    2. Submit a search. `submitSearch` sets `searchedQuery` at once, which adds `SearchStatusRow`
       under the search bar and shrinks the content box.

    - If a match is found, the first `ScrollToPage` calls `alignTop()` and hides the gap. With no
      match, it stays.
    - A multi-window resize that doesn't relaunch the activity can also trigger it.
- **Evidence / verification:**
    - Traced: every offset writer (`pan`, `zoomTo`, `alignTop`, `placeAt`, `centerOn`) clamps at
      write time. The `viewport` setter doesn't, and no effect is keyed on the viewport.
    - Opening search alone doesn't change the height, and neither does the IME: there is no
      `imePadding` and Scaffold's default insets exclude it.
    - Independent refutation pass: confirmed, with this refined trigger.
    - Not reproduced on a device.
- **Suggested fix:** re-clamp `offsetX` and `offsetY` to the new bounds whenever `viewport` changes,
  for example in a custom setter or in the `onSizeChanged` callback.

### [a/error-handling/pdf-viewer/unopenable-encryption-loops-password-prompt] A malformed standard-encryption dictionary gets a password prompt that can never succeed

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/pdfviewer/PdfViewerViewModel.kt:234`
- **Severity:** Low
- **Confidence:** Medium
- **Likelihood:** Low. Such files are rare, and most other readers reject them too.
- **Defect:** in FULL mode, every `SecurityException` becomes
  `PasswordRequired(wrongPassword = password != null)`.
    - pdfium reports `FPDF_ERR_PASSWORD` when `CPDF_SecurityHandler::LoadDict` fails.
    - The platform maps `FPDF_ERR_PASSWORD` to `REQUIRES_PASSWORD`, and so to `SecurityException`.
    - So a `/Filter /Standard` document whose Encrypt dictionary pdfium can't load fails the same
      way for every password. The user is told "wrong password" forever, and Cancel is the only way
      out.
- **Trigger:** open, in FULL mode, a PDF with `/Filter /Standard` whose Encrypt dictionary pdfium
  rejects. Examples: `V>=4` with `StmF != StrF`, a missing `/CF` or named crypt filter, or an
  invalid key length.
    - FULL mode means API 35+, or API 31–34 with S extension 13.
    - Certificate-encrypted (`/Adobe.PubSec`) files are *not* affected: they map to
      `FPDF_ERR_SECURITY` → `IOException` → the normal load error.
- **Evidence / verification:** source-traced, no real file tested.
    - pdfium `cpdf_parser.cpp` `SetEncryptHandler`: a failed `OnInit` → `PASSWORD_ERROR`.
    - `cpdfsdk_helpers.cpp` `ProcessParseError`: `PASSWORD_ERROR` → `FPDF_ERR_PASSWORD`.
    - MediaProvider `pdfClient/document.cc` `Document::Load`: only `FPDF_ERR_PASSWORD` →
      `REQUIRES_PASSWORD`.
    - `PdfProcessor.create`: `REQUIRES_PASSWORD` → `SecurityException`.
    - Refutation attempt: the API can't tell this apart from a truly wrong password, so the defect
      is in the retry policy rather than the classification.
- **Suggested fix:** cap the prompt. After a few failed attempts, show the "can't open this file"
  state instead of asking again.

## Summary

By severity: Medium (3 findings), Low (4 findings)

By confidence: High (2 findings), Medium (5 findings), Low (0 findings)

| Severity | High | Medium | Low |
|----------|------|--------|-----|
| Medium   | 0    | 3      | 0   |
| Low      | 2    | 2      | 0   |

By Likelihood: High (0 findings), Medium (1 finding), Low (6 findings)
