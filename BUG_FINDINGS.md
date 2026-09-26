## Low

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