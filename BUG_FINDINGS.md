### [a/logic-errors/pdf-thumbnails/fit-inside-box-smaller-than-display-slot] PDF thumbnails are rendered smaller than the slot that displays them, so both PDF surfaces now upscale

**Location:**
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/PdfThumbnailFetcher.kt:57-62` (
primary)
Related:`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/components/FileListItem.kt:349,362` (
fixed`.size(120)` box, `ContentScale.Crop` into a `40.dp` slot);
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/iteminfo/ItemInfoScreen.kt:287,296-298` (
fixed `.size(400)` box, `ContentScale.Fit` into a `200.dp` slot);
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/ThumbnailDiskCache.kt:232-262` (the
`coverage()`/`covers()` invariant that motivated the change)

**Severity:** Low
**Confidence:** High

**Defect:** The fetcher changed from scaling a PDF page to the requested *width* to fitting it
*inside* the requested box:

```kotlin
val scale = minOf(
    options.thumbnailWidth().toFloat() / page.width,
    options.thumbnailHeight().toFloat() / page.height
)
```

Both PDF request sites ask for a **fixed pixel** box (`.size(120)` and `.size(400)`) while rendering
into a **dp-sized** slot that grows with screen density. Fitting a portrait page inside a square
pixel box makes the produced bitmap shorter on its long axis than the slot, so Compose upscales it
at draw time. The result is a visibly softer PDF thumbnail than the baseline produced, on every
device at 420 dpi or above.

For A4 (595x842) the produced bitmap changes as follows:

| Site                             | Box    | Baseline bitmap | HEAD bitmap |
|----------------------------------|--------|-----------------|-------------|
| `FileListItem` (folder list row) | 120 px | 120x170         | 84x120      |
| `ItemInfoScreen` (preview)       | 400 px | 400x566         | 282x400     |

Resulting draw-time scale factor (values above 1.0 are upscaling):

| Density          | Row slot | Baseline | HEAD      | Info slot | Baseline | HEAD      |
|------------------|----------|----------|-----------|-----------|----------|-----------|
| xhdpi (2.0x)     | 80 px    | 0.67x    | 0.95x     | 400 px    | 0.71x    | 1.00x     |
| 420 dpi (2.625x) | 105 px   | 0.88x    | **1.25x** | 525 px    | 0.93x    | **1.31x** |
| xxhdpi (3.0x)    | 120 px   | 1.00x    | **1.43x** | 600 px    | 1.06x    | **1.50x** |
| 560 dpi (3.5x)   | 140 px   | 1.17x    | **1.67x** | 700 px    | 1.24x    | **1.75x** |
| xxxhdpi (4.0x)   | 160 px   | 1.33x    | **1.90x** | 800 px    | 1.41x    | **2.00x** |

The `ItemInfoScreen` preview is the more visible of the two: it is a 200 dp tall element, and it
moves from essentially 1:1 to a 1.5x upscale on a typical xxhdpi phone.

**Trigger:** Open a folder containing any portrait PDF on a device at 420 dpi or above (most current
phones), or open Item Info for one. No malformed input or unusual state is needed — every portrait
PDF is affected.

**Evidence / verification:**

- Baseline formula:
  `git show d0b63a1:app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/PdfThumbnailFetcher.kt` →
  `val scale = targetWidth.toFloat() / page.width`. HEAD is the `minOf(...)` shown above. *
  *Introduced.**
- Both request sites are unchanged in the range. `git show d0b63a1:.../FileListItem.kt` lines
  238/251 are byte-identical `.size(120)` and `contentScale = ContentScale.Crop` to HEAD lines
  349/362. Only the fetcher changed, so the regression is attributable to it alone.
- `.size(120)` is pixels and density-independent: Coil's `ImageRequest.size(@Px size: Int)`. The dp
  slot grows with density while the box does not.
- Coil never upscales to fill the request. `AsyncImagePainter` sets `Precision.INEXACT` when
  precision is not explicitly `EXACT`, and `BitmapFactoryDecoder` applies
  `scale = scale.coerceAtMost(1.0)` under `allowInexactSize` — the decoder only downsamples. The
  bitmap reaching `SubcomposeAsyncImageContent` really is 84x120, and `ContentScale.Crop`/`Fit`
  performs the upscale at draw time.
- Neither cache layer masks it: `ThumbnailDiskCache.covers()` only accepts a *larger* stored entry,
  it never fabricates a bigger bitmap; the memory cache keys on `thumbnailCacheKey` plus size.
- Refutation attempt 1 — *"the `ThumbnailDiskCache` coverage invariant requires fit-inside, so this
  is a necessary fix, not a regression."* Partly true but does not refute the finding. The KDoc at
  `ThumbnailDiskCache.kt:232-243` does state fit-inside as the invariant, and the record is
  `max(boxWidth, boxHeight)`. But the hazard it guards against needs a **non-square** request box,
  and every PDF request site today resolves to a square one (`.size(120)`, `.size(400)`, and both
  home sections whose thumbnail `Box` is `fillMaxWidth().aspectRatio(1f)`). With a square box,
  width-only scaling of a portrait page *over*-delivers (120x170, longest side 170 >= the recorded
  120) and can never under-cover. So the change is defensive alignment with an invariant whose
  violation is not currently reachable, while the softness it costs is paid on every render today.
- Refutation attempt 2 — *"the home sections regressed too, so this is uniform."* False, and it
  sharpens the finding: `RecentFilesSection.kt:141` and `FavoritesSection.kt:143` use
  `contentScale = if (isPdf) ContentScale.Fit else ContentScale.Crop` against a square box, so HEAD
  lands at exactly 1:1 there. Those two sites are *improved* by the change. Only the two
  fixed-pixel-box sites regressed.
- Two points genuinely favour the change and are why this is Low rather than higher: the **video**
  fetcher already fit-inside at baseline (`git show d0b63a1:.../VideoThumbnailFetcher.kt` →
  `getScaledFrameAtTime(w, h)`), so video thumbnails in the same row were already Crop-upscaled and
  PDF is now consistent with them; and `ThumbnailDiskCache` is new in this range, so the invariant
  it aligns with is new too.
- Remaining assumption: perceptibility. The scale factors are computed, not measured, and no
  on-device render was performed (no emulator available).

**Suggested fix:** Do not revert the fetcher — width-only scaling would reintroduce the asymmetry
the coverage invariant is written against. Fix the two request sites so the box matches what is
actually drawn: derive the requested pixel size from the slot's dp value and the current density (
`with(LocalDensity.current) { 40.dp.roundToPx() }` for the row, `200.dp.roundToPx()` for the
preview) rather than hardcoding 120 and 400. That removes the upscale at every density and leaves
the fit-inside invariant intact. As a narrower alternative for the row alone, giving `FileListItem`
the same `if (file.isPdf) ContentScale.Fit` treatment the two home sections already use would
letterbox instead of upscaling, at the cost of a smaller-looking thumbnail.

---

### [a/resource-management/startup-routing/activity-captured-by-storages-lambda] A destroyed
`MainActivity` is retained by the abandoned startup-folder resolution

**Location:**`app/src/main/java/com/mauriciotogneri/fileexplorer/activities/MainActivity.kt:41-46` (
primary)
Related: `app/src/main/java/com/mauriciotogneri/fileexplorer/util/StartupFolderResolver.kt:56-62`

**Severity:** Low
**Confidence:** Medium

**Defect:** The `storages` lambda handed to `StartupFolderResolver` reads `applicationContext`:

```kotlin
private val startupFolderResolver by lazy {
    StartupFolderResolver(
        scope = lifecycleScope,
        storages = { StorageRepository(AndroidStorageSource(applicationContext)).getStorages() }
    )
}
```

`applicationContext` is `ContextWrapper.getApplicationContext()`, an *instance* method, so the
lambda's synthetic class captures `this$0 = MainActivity` in order to call it. The lambda therefore
holds the **Activity**, which is exactly what writing `applicationContext` was meant to avoid.
`StartupFolderResolver` stores it in a field for the life of the resolver.

This matters because the class is explicitly designed around the resolution coroutine outliving its
caller. Its own KDoc states that `resolution.cancel()` cannot stop the work — "a stat of a wedged
volume is an uninterruptible syscall... the abandoned coroutine finishes on its own thread and its
result is discarded". During that window the running IO-dispatcher thread's stack is a GC root
reaching the continuation → the resolver → the `storages` field → the lambda → the destroyed
`MainActivity`, its window, and its whole Compose tree.

**Trigger:** A startup folder is configured; its volume is spinning up or wedged so the stat blocks
past the 2 s `withTimeoutOrNull`; the user then rotates the device or leaves the app, destroying the
Activity. The Activity stays reachable for as long as the syscall blocks.

**Evidence / verification:**

- **Introduced.** `git cat-file -e d0b63a1:.../util/StartupFolderResolver.kt` → absent. Baseline
  `MainActivity.kt` is 48 lines with no resolver, no `lifecycleScope`, and no startup routing.
- The capture is unambiguous: `applicationContext` has no local, import, or Compose-local shadow
  anywhere in `MainActivity.kt`, and the `by lazy` initializer is itself a lambda over the same
  outer `this`, so nothing pre-resolves the context.
- Refutation attempt 1 — *"it is a self-referential cycle (Activity → resolver → lambda → Activity),
  which GC collects fine."* Fails. The cycle is collectable only with no external root, and during
  the stall the running IO thread's stack is exactly that root. The chain holds wherever the wedge
  occurs: if the stall is inside `StartupDestinationResolver.resolve(path, storages())` rather than
  inside `storages()`, the resolver is still the continuation's receiver.
- Refutation attempt 2 — *"something else already holds the Activity strongly, so the capture is
  incidental."* Fails, and this is what makes the capture load-bearing. `scope` is`lifecycleScope` →
  `LifecycleCoroutineScopeImpl` → `LifecycleRegistry`, which holds its owner via a `WeakReference`.
  The separate `lifecycleScope.launch { openStartupFolder(path) }` coroutine does capture the
  Activity strongly, but it completes at the 2 s timeout and is unlinked from its parent job. After
  that instant the lambda capture is the sole strong reference.
- Refutation attempt 3 — *"`lifecycleScope` cancellation frees it."* Fails by construction.
  `resolution.cancel()` is already called on every path (`StartupFolderResolver.kt:59`);
  cancellation is cooperative and the uninterruptible stall is the premise the timeout exists for.
- Refutation attempt 4 — *"the retention is transient and bounded, so it is not a defect."* Weak. It
  is bounded only by the syscall, which is the unbounded case the class was written for. One blocked
  IO thread is a cost the design knowingly accepts; dragging a destroyed Activity and its Compose
  tree along is not, and nothing in the design chose it.
- Remaining assumptions (why Confidence is Medium, not High): the mechanism is certain, but the
  retention window's length and how often a volume actually wedges past 2 s were not measured, and
  no heap dump or LeakCanary run was performed.

**Suggested fix:** Resolve the context once, outside the lambda, so only the `Application` is
captured — hoist a `val appContext = applicationContext` (or take the `Application` as a constructor
parameter) and reference that inside the lambda. `AndroidStorageSource` only needs a `Context`, so
nothing else moves and there is no behavioral change.