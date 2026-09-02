# Coil 2.7.0 → 3.6.1 migration plan

Status: **not started**. This document is the plan only; no code has been changed.

| | |
|---|---|
| From | `io.coil-kt:coil-compose / coil-gif / coil-svg` **2.7.0** |
| To | `io.coil-kt.coil3:coil-compose / coil-gif / coil-svg` **3.6.1** |
| Package | `coil.*` → `coil3.*` |
| Files touched | 11 production, 8 test, 2 build |
| Approach for the animated-decoder regression | **Static decode** — register a static decoder explicitly on the thumbnails loader (see §2.2) |

## How the claims in this document were verified

Everything below was checked against the real 3.6.1 artifacts, not from memory. The AARs were
downloaded from Maven Central, `classes.jar` extracted, and the API read with `javap`
(`-c` where bytecode ordering mattered):

```
io/coil-kt/coil3/coil-core-android/3.6.1/coil-core-android-3.6.1.aar
io/coil-kt/coil3/coil-compose-core-android/3.6.1/coil-compose-core-android-3.6.1.aar
io/coil-kt/coil3/coil-gif/3.6.1/coil-gif-3.6.1.aar
io/coil-kt/coil3/coil-svg-android/3.6.1/coil-svg-android-3.6.1.aar
```

Where a claim rests on runtime ordering rather than a signature, the evidence is quoted inline.

---

## 1. Why this is not a find-and-replace

Three behaviours this app deliberately engineered are **not** expressed in types, so the compiler
will not defend them. All three survive a mechanical rename and then fail at runtime:

1. Thumbnails must never animate (`AppImageLoader.kt:24-31` — animating them buffers each whole
   encoded file plus per-frame software bitmaps for every visible and prefetched row, and OOMs).
2. Thumbnail cache keys must never stat the file on the main thread (`AppImageLoader.kt:104-113` —
   the stat happens inside the list's measure pass and can ANR on congested storage).
3. Native thumbnail extraction must stay capped at 4 concurrent
   (`AppImageLoader.kt:89-95` — otherwise `MediaMetadataRetriever.finalize() timed out`).

Coil 3 breaks all three silently, plus a fourth that removes media thumbnails outright. Those four
are §2. The rest of the migration (§3 onwards) is mechanical.

---

## 2. The four runtime landmines

### 2.1 `Fetcher.Factory<File>` becomes dead code

**Coil 2** shipped `FileUriMapper`, mapping `file://` Uri → `java.io.File`, so `File` was the
canonical fetcher input and `Fetcher.Factory<File>` was the documented way to write a custom fetcher.

**Coil 3 reversed the direction.** `coil3.map.FileMapper` maps `java.io.File` → `coil3.Uri`:

```
public final class coil3.map.FileMapper implements coil3.map.Mapper<java.io.File, coil3.Uri>
```

and it is registered unconditionally by `coil3.RealImageLoader_jvmCommonKt.addJvmComponents`
(it is the first component in that method's bytecode). Mapping runs *before* fetcher resolution,
and `ComponentRegistry.map` applies **every** matching mapper in one forward pass rather than
stopping at the first, so a `File` handed to `.data(...)` is *always* a `coil3.Uri` by the time
fetchers are consulted.

**Consequence if missed:** all five custom factories compile, register, and are never called.
`FileUriFetcher` opens the raw `.mp4`/`.pdf`/`.apk`/`.mp3`/`.epub` bytes, `BitmapFactoryDecoder`
fails on them, and **every media and document thumbnail in the app disappears** — list rows, home
cards, favourites, recents, and the item-info preview. No crash, no log, just generic icons.

**Fix:** all five fetchers become `Fetcher.Factory<Uri>` guarded on `scheme == "file"`, rebuilding
the `File` from `coil3.Uri.filePath`. Call sites keep passing `File` and are unchanged.

**Why our factories still win over `FileUriFetcher`:** component order is user → ServiceLoader →
defaults. From `coil3.RealImageLoader.<init>`:

```
77: invokevirtual coil3/ComponentRegistry.newBuilder          // user components
84: invokestatic  coil3/RealImageLoaderKt.addServiceLoaderComponents
108: invokestatic coil3/RealImageLoaderKt.addCommonComponents  // FileUriFetcher, decoders
```

### 2.2 coil-gif and coil-svg inject decoders into *every* loader

Both artifacts ship a ServiceLoader entry:

```
coil-gif  META-INF/services/coil3.util.DecoderServiceLoaderTarget → coil3.gif.internal.GifDecoderServiceLoaderTarget
coil-svg  META-INF/services/coil3.util.DecoderServiceLoaderTarget → coil3.svg.internal.SvgDecoderServiceLoaderTarget
```

`RealImageLoader` calls `addServiceLoaderComponents` for every `ImageLoader` it builds, and
**3.6.1 has no opt-out** — there is no `serviceLoaderEnabled` on `ImageLoader.Builder`, and no
occurrence of that string anywhere in `coil-core`. (It exists in some Coil 3 docs; it is not in this
version's Android artifact.)

**Consequence if missed:** the thumbnails loader — which today registers *no* animated decoder on
purpose — silently acquires `AnimatedImageDecoder` (API 28+) or `GifDecoder`. A grid of GIFs or
animated WebPs then does exactly what the comment at `AppImageLoader.kt:24-31` says it must not:
buffers every encoded file plus per-frame software bitmaps on-heap for every visible and prefetched
row. This is the OOM the current design exists to prevent.

**Fix (the static-decode approach):** register a static decoder explicitly on the thumbnails
loader. User components are consulted before ServiceLoader components, so an explicitly registered
static decoder claims GIF and animated WebP first and decodes a single frame — precisely today's
behaviour.

Register **both** of these, in this order, after `SvgDecoder`:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    add(StaticImageDecoder.Factory())
}
add(BitmapFactoryDecoder.Factory())
```

`BitmapFactoryDecoder` is not redundant. `StaticImageDecoder.Factory.create` returns null when
`toImageDecoderSourceOrNull` cannot adapt the source:

```
 2: invokespecial isApplicable:(Lcoil3/request/Options;)Z
 5: ifne 10
 8: aconst_null ; areturn
16: invokestatic coil3/decode/StaticImageDecoderKt.toImageDecoderSourceOrNull
20: ifnonnull 26
24: aconst_null ; areturn
```

and it cannot adapt a `Buffer`-backed `ImageSource` — which is exactly what all five of our
fetchers return. Without the `BitmapFactoryDecoder` line, those results would fall through past
our block into the ServiceLoader decoders. In practice a JPEG/PNG thumbnail would not be claimed
by `GifDecoder` anyway, but relying on that is relying on a sniff we do not control.

This mirrors Coil's own Android defaults, which register `StaticImageDecoder` (API 28+) then
`BitmapFactoryDecoder` (`coil3.RealImageLoader_androidKt`, offsets 244 and 261).

**Trade-off, accepted:** the thumbnails loader now shadows any decoder a future Coil artifact
installs by ServiceLoader. That is the intent — the thumbnails loader is a deliberately narrow
pipeline — but it means adding e.g. `coil-video` later would require an explicit registration here
rather than working by drop-in.

**Parallelism is unchanged.** `BitmapFactoryDecoder.Factory()`'s no-arg constructor builds
`Semaphore(4)` (`iconst_4` in its bytecode), the same permit count Coil's own default factory uses,
so decode concurrency for thumbnails stays where it is. Note this is a *second* semaphore instance,
not a shared one — irrelevant here because the default factory is never reached on this loader, but
worth knowing if a third decoder is ever added.

**`SvgDecoder` is now doubly registered** (explicitly, and by coil-svg's ServiceLoader entry). Keep
the explicit registration: it is what makes the ordering above deterministic, and it documents that
SVG must be sniffed before the catch-all static decoders.

### 2.3 `addLastModifiedToFileCacheKey` default flipped

Coil 2 defaulted to `true`; Coil 3 defaults to `false`. The option survives as an extension
function on both builders:

```
coil3.request.ImageRequestsKt:
  addLastModifiedToFileCacheKey(coil3.request.ImageRequest$Builder, boolean)
  addLastModifiedToFileCacheKey(coil3.ImageLoader$Builder, boolean)
```

**Consequence if missed:** the *viewer* loader silently loses its stat. It relies on the Coil 2
default (`AppImageLoader.kt:110-112`: "Left enabled on the viewer below … not worth the risk of
showing a stale full-resolution image when a file is replaced at the same path"). After the
upgrade, replacing a file in place and reopening it shows the previous image.

**Fix:** `.addLastModifiedToFileCacheKey(true)` on the viewer, explicitly.

Keep `.addLastModifiedToFileCacheKey(false)` on the thumbnails loader even though it now matches
the default — the comment above it is the reason the app has an explicit `memoryCacheKey` on every
request, and deleting the call would strand that comment.

### 2.4 `fetcherDispatcher` → `fetcherCoroutineContext`

```
public final coil3.ImageLoader$Builder fetcherCoroutineContext(kotlin.coroutines.CoroutineContext);
public final coil3.ImageLoader$Builder decoderCoroutineContext(kotlin.coroutines.CoroutineContext);
```

`fetcherDispatcher` no longer exists, so this one *is* a compile error rather than a silent
regression — but the compile error is easy to "fix" by deleting the line. Deleting it uncaps
`MediaMetadataRetriever` and `PdfRenderer` back onto `Dispatchers.IO`'s 64 threads and brings back
the `MediaMetadataRetriever.finalize() timed out` crash. `CoroutineDispatcher` is a
`CoroutineContext`, so the existing `Dispatchers.IO.limitedParallelism(4)` value passes unchanged.

---

## 3. Complete API mapping

Verified signature by signature. Anything not listed here is unchanged.

### Builders

| Coil 2 | Coil 3.6.1 |
|---|---|
| `ImageLoader.Builder(context).fetcherDispatcher(d)` | `.fetcherCoroutineContext(d)` |
| `MemoryCache.Builder(context).maxSizePercent(0.15)` | `MemoryCache.Builder().maxSizePercent(context, 0.15)` |
| `DiskCache.Builder().directory(File)` | `.directory(okio.Path)` — `file.toOkioPath()` |
| `ImageLoader.Builder.addLastModifiedToFileCacheKey(b)` | same name, now `coil3.request` extension, default flipped |

### Fetchers and decoding

| Coil 2 | Coil 3.6.1 |
|---|---|
| `coil.fetch.SourceResult` | `coil3.fetch.SourceFetchResult` (same 3 params) |
| `coil.fetch.Fetcher.Factory<File>` | `coil3.fetch.Fetcher.Factory<Uri>` (see §2.1) |
| `ImageSource(buffer, options.context)` | `ImageSource(buffer, options.fileSystem)` |
| `ImageSource(path, fileSystem, key, closeable)` | same, `closeable` is now `AutoCloseable` |
| `coil.decode.ImageDecoderDecoder` | `coil3.gif.AnimatedImageDecoder` |
| `coil.decode.GifDecoder` | `coil3.gif.GifDecoder` |
| `coil.decode.SvgDecoder` | `coil3.svg.SvgDecoder` |
| `coil.decode.BitmapFactoryDecoder` | `coil3.decode.BitmapFactoryDecoder` |
| — | `coil3.decode.StaticImageDecoder` (new, API 28+) |

### Unchanged (confirmed, not assumed)

- `DiskCache`: `openSnapshot`, `openEditor`, `remove`, `clear`, `fileSystem`, `directory`, and
  `Snapshot.data` / `.metadata` / `.close()`, `Editor.data` / `.metadata` / `.commit()` / `.abort()`.
  `Snapshot` now extends `AutoCloseable`, which is what `ImageSource` accepts. **`ThumbnailDiskCache`
  needs no logic changes at all** — imports only.
- `ImageRequest.Builder`: `data`, `size(Int)`, `memoryCacheKey(String)`, `crossfade(Boolean)`.
- `coil3.size`: `Size`, `Dimension.Pixels.px`, `Precision`, `Scale`.
- `Options`: `context`, `size`, `diskCachePolicy`, plus a new `fileSystem`.
- `coil3.annotation.ExperimentalCoilApi`.
- Decoder failure message constants — checked byte-for-byte in 3.6.1:
  `"BitmapFactory returned a null bitmap. …"` and `"Failed to decode GIF."`. **`ImageErrors.kt`
  keeps working**; only its KDoc references need updating.

### Compose

| Coil 2 | Coil 3.6.1 |
|---|---|
| `coil.compose.SubcomposeAsyncImage` | `coil3.compose.SubcomposeAsyncImage` — same slots, same typed `State` params |
| `coil.compose.SubcomposeAsyncImageContent` | `coil3.compose.SubcomposeAsyncImageContent` |
| `AsyncImagePainter.state: State` | `state: StateFlow<State>` ⚠ |
| `SuccessResult.drawable: Drawable` | `SuccessResult.image: coil3.Image` — `.asDrawable(resources)` |

`ScaleDrawable` moved package: `coil.drawable.ScaleDrawable` → **`coil3.size.ScaleDrawable`**.

---

## 4. File-by-file work

### 4.1 Build files (2)

**`gradle/libs.versions.toml`**

```toml
coil = "3.6.1"

coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-gif     = { group = "io.coil-kt.coil3", name = "coil-gif",     version.ref = "coil" }
coil-svg     = { group = "io.coil-kt.coil3", name = "coil-svg",     version.ref = "coil" }
```

**`app/build.gradle.kts`** — the three `implementation(libs.coil.*)` lines are unchanged.

`okhttp` stays as it is. It is declared explicitly at 5.5.0 and used directly by `FeedbackActivity`;
Coil 3 simply no longer pulls it in, which changes nothing here because nothing in this app loads an
image over the network. No `coil-network-okhttp` dependency is needed.

### 4.2 `AppImageLoader.kt` — all four landmines

New imports: `coil3.ImageLoader`, `coil3.decode.BitmapFactoryDecoder`,
`coil3.decode.StaticImageDecoder`, `coil3.disk.DiskCache`, `coil3.gif.AnimatedImageDecoder`,
`coil3.gif.GifDecoder`, `coil3.memory.MemoryCache`,
`coil3.request.addLastModifiedToFileCacheKey`, `coil3.svg.SvgDecoder`,
`okio.Path.Companion.toOkioPath`.

```kotlin
val diskCache = DiskCache.Builder()
    .directory(context.cacheDir.resolve("image_cache").toOkioPath())   // was a java.io.File
    .maxSizeBytes(50L * 1024 * 1024)
    .build()

val thumbnailsMemoryCache = MemoryCache.Builder()                      // was Builder(context)
    .maxSizePercent(context, 0.15)                                     // context moved here
    .build()
val viewerMemoryCache = MemoryCache.Builder()
    .maxSizePercent(context, 0.05)
    .build()

val fetcherDispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_THUMBNAILS)

fun base(memoryCache: MemoryCache): ImageLoader.Builder =
    ImageLoader.Builder(context)
        .fetcherCoroutineContext(fetcherDispatcher)                    // was fetcherDispatcher
        .memoryCache { memoryCache }
        .diskCache { diskCache }

val thumbnails = base(thumbnailsMemoryCache)
    .addLastModifiedToFileCacheKey(false)
    .components {
        add(PdfThumbnailFetcher.Factory())
        add(VideoThumbnailFetcher.Factory())
        add(ApkThumbnailFetcher.Factory())
        add(AudioThumbnailFetcher.Factory())
        add(EpubThumbnailFetcher.Factory())
        add(SvgDecoder.Factory())
        // Registered explicitly so a GIF or animated WebP is claimed here rather than by the
        // animated decoder coil-gif installs into every loader through its ServiceLoader entry
        // (Coil 3.6.1 offers no way to switch that off). User components are consulted before
        // ServiceLoader ones, so this is what keeps a thumbnail a single static frame.
        // StaticImageDecoder declines Buffer-backed sources — every one of the fetchers above
        // returns one — so BitmapFactoryDecoder is registered as well, not as a duplicate.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(StaticImageDecoder.Factory())
        }
        add(BitmapFactoryDecoder.Factory())
    }
    .build()

val viewer = base(viewerMemoryCache)
    // Coil 3 no longer adds it by default; the viewer wants it (see the thumbnails comment).
    .addLastModifiedToFileCacheKey(true)
    .components {
        add(SvgDecoder.Factory())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(AnimatedImageDecoder.Factory())                        // was ImageDecoderDecoder
        } else {
            add(GifDecoder.Factory())
        }
    }
    .build()
```

Also update the KDoc on `thumbnails()` (line 24-31): it currently explains the absence of an
animated decoder. Under Coil 3 the absence is no longer sufficient, so the comment must say the
static decoder registration is what enforces it.

### 4.3 The five fetchers

`VideoThumbnailFetcher`, `PdfThumbnailFetcher`, `ApkThumbnailFetcher`, `AudioThumbnailFetcher`,
`EpubThumbnailFetcher`. Identical shape of change in each:

```kotlin
// imports: coil.* → coil3.*, plus coil3.Uri and coil3.pathSegments/filePath as needed
import coil3.Uri
import coil3.fetch.SourceFetchResult
import coil3.filePath

// return value
SourceFetchResult(                                  // was SourceResult
    source = ImageSource(buffer, options.fileSystem),  // was ImageSource(buffer, options.context)
    mimeType = MIME_TYPE,
    dataSource = DataSource.DISK
)

// factory
class Factory : Fetcher.Factory<Uri> {              // was Fetcher.Factory<File>
    override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
        val file = data.toFileOrNull() ?: return null
        if (!file.exists() || !file.canRead()) return null
        if (!MimeTypeUtil.isVideo(MimeTypeUtil.getMimeType(file))) return null
        return VideoThumbnailFetcher(file, options, imageLoader.diskCache)
    }
}
```

Add one shared helper next to the fetchers (they all need the same three lines, and a single
definition keeps the five factories agreeing on what counts as a local file):

```kotlin
/**
 * The local file [this] addresses, or null when it is not one. Coil 3 maps a `java.io.File` model
 * to a `file://` [Uri] before fetchers are consulted (`coil3.map.FileMapper`), so a fetcher that
 * wants the file back has to undo that. Anything else — `content://`, `android.resource://`, a
 * relative Uri with no path — is left to Coil's own fetchers.
 */
internal fun Uri.toFileOrNull(): File? {
    if (scheme != "file") return null
    return filePath?.let(::File)
}
```

`ApkThumbnailFetcher` reads `options.context.packageManager` and passes `file.absolutePath` to
`getPackageArchiveInfo`. Both survive: `Options.context` is `PlatformContext`, which is a
`typealias` for `android.content.Context` on Android. No change beyond taking the file from the
helper.

Everything else in these files (the extraction logic, the `ThumbnailDiskCache` wiring, the error
scrubbing, the `variesWithSize` flags) is untouched.

### 4.4 `ThumbnailDiskCache.kt`

Imports only — `coil.annotation.ExperimentalCoilApi` → `coil3.annotation.ExperimentalCoilApi`,
`coil.decode.*` → `coil3.decode.*`, `coil.disk.DiskCache` → `coil3.disk.DiskCache`,
`coil.fetch.SourceResult` → `coil3.fetch.SourceFetchResult`, `coil.request.Options` →
`coil3.request.Options`. The `read()` return type becomes `SourceFetchResult?`.

`ImageSource(snapshot.data, cache.fileSystem, key, snapshot)` compiles unchanged: `Snapshot` is now
`AutoCloseable`, which is what the overload takes.

Two KDoc references to fix: `[coil.fetch.HttpUriFetcher]` (that class no longer exists in
`coil-core` at all — Coil 3 moved network fetching to a separate artifact this app does not use, so
the sentence should say Coil never writes this cache itself here) and `[Fetcher][coil.fetch.Fetcher]`.

### 4.5 `ThumbnailSize.kt`

Imports only: `coil3.request.Options`, `coil3.size.Dimension`.

Note: `coil3.size` ships its own `pxOrElse`. The app's local `Dimension.pxOrElse` will shadow it at
these call sites. It already did the same in Coil 2, so this is not a migration issue — but it is a
candidate for a separate cleanup.

### 4.6 `FileRepository.kt`

One import: `coil3.disk.DiskCache`. Its `thumbnailDiskCache: () -> DiskCache?` parameter, the
`evictThumbnail` call, and every delete path are unchanged.

### 4.7 `ImageErrors.kt` / `FileErrors.kt` — documentation only

No logic change. Both message constants `isUndecodableImage` matches are byte-identical in 3.6.1
(verified against the compiled classes), and `isUnreadableFile(e) = e is IOException` still covers
`ImageDecoder.DecodeException`.

KDoc references to rewrite: `[coil.decode.BitmapFactoryDecoder]` → `[coil3.decode.BitmapFactoryDecoder]`,
`[coil.decode.GifDecoder]` → `[coil3.gif.GifDecoder]`, `[coil.decode.ImageDecoderDecoder]` →
`[coil3.gif.AnimatedImageDecoder]`, `[coil.decode.SvgDecoder]` → `[coil3.svg.SvgDecoder]`,
`[coil.decode.ImageSource]` → `[coil3.decode.ImageSource]`.

One substantive sentence to add while there: on API 28+ Coil 3 prefers `StaticImageDecoder` over
`BitmapFactoryDecoder` for *all* images, not just GIFs, so a corrupt JPEG in the viewer now arrives
as `ImageDecoder.DecodeException` rather than the `IllegalStateException` the null-bitmap phrase
matches. That path is already covered — `isUndecodableImage` and `isUnreadableFile` are applied
together at `ImageViewerViewModel.kt:105` — but the KDoc currently frames DecodeException as a
GIF/WebP-only case, and after this upgrade that is no longer true.

Also check `coil-svg`'s transitive `androidsvg-aar` is still runtime-scoped; the `ImageErrors` KDoc
explains that `SVGParseException` is unreferenceable for that reason, and the reasoning should be
re-confirmed rather than assumed after the artifact change.

### 4.8 Compose call sites (5)

`FileListItem.kt`, `RecentFilesSection.kt`, `FavoritesSection.kt`, `ItemInfoScreen.kt`,
`ImageViewerScreen.kt`.

Four of them are import-only (`coil.compose.*` → `coil3.compose.*`, `coil.request.ImageRequest` →
`coil3.request.ImageRequest`). `ImageRequest.Builder(context)`, `.data(File(...))`,
`.memoryCacheKey(...)`, `.size(px)`, `.crossfade(true)` and both `SubcomposeAsyncImage` overloads
keep their signatures.

**`ImageViewerScreen.kt:242` is the one real change.** `painter.state` is now a `StateFlow<State>`,
so the cast no longer compiles. The `error` slot already receives the `State.Error` as its
parameter, so the fix removes the indirection rather than adding a `collectAsState()`:

```kotlin
error = {
    val throwable = it.result.throwable
    LaunchedEffect(Unit) { onError(throwable) }
    ImageLoadError()
}
```

This also drops the now-unused `coil.compose.AsyncImagePainter` import.

`MAX_IMAGE_DIMENSION = 4096` needs no change: it happens to equal Coil 3's new default
`maxBitmapSize` cap, so the viewer's request is not clipped by it.

### 4.9 Tests (8)

| File | Work |
|---|---|
| `AppImageLoaderGifTest` (androidTest) | **Real work.** `coil.drawable.ScaleDrawable` → `coil3.size.ScaleDrawable`; `SuccessResult.drawable` → `.image.asDrawable(context.resources)`. Its `classify()` helper is what proves §2.2 held — it must keep returning `"static"` for thumbnails and `"animated"` for the viewer |
| `AppImageLoaderCacheKeyTest` (androidTest) | Imports only — it asserts on cache keys, never on the decoded result |
| `ThumbnailDiskCacheTest` (androidTest) | Imports; `SourceResult` → `SourceFetchResult`. Its hand-built `Options(context =, size =, diskCachePolicy =)` uses named arguments and all three names survive, so it needs no change |
| `ThumbnailDiskCacheWiringTest` (androidTest) | Imports |
| `ThumbnailFetcherRobustnessTest` (androidTest) | Imports. **This is the test that proves §2.1** — it drives real fetches through the loader, so it fails loudly if the fetchers are still `Factory<File>` |
| `ItemInfoMetadataTest` (androidTest) | Imports |
| `FileRepositoryTest` (unit) | Imports only — `mockk<DiskCache>` and `verify { diskCache.remove(key) }` are unaffected |
| `ImageErrorsTest` (unit) | One comment referencing `coil.decode.GifDecoder`; assertions unchanged |

**Add one test.** Nothing currently asserts that a *plain* GIF renders as a static frame through
the thumbnails loader specifically because of an explicit decoder rather than by the absence of one.
`AppImageLoaderGifTest` covers the outcome; extend it with a case that also passes a GIF whose
bytes arrive from a custom fetcher (a `Buffer`-backed source), which is the path §2.2 says
`StaticImageDecoder` declines. Without it, the `BitmapFactoryDecoder` line has no coverage.

---

## 5. Suggested order of work

Each step should compile before the next starts; the fetchers cannot compile until `AppImageLoader`
has been converted, and vice versa, so steps 2 and 3 land together.

1. Build files (§4.1).
2. + 3. `AppImageLoader` and the five fetchers, together (§4.2, §4.3).
4. `ThumbnailDiskCache`, `ThumbnailSize`, `FileRepository` (§4.4-4.6).
5. Compose call sites (§4.8).
6. KDoc in `ImageErrors` / `FileErrors` / `ThumbnailDiskCache` (§4.7).
7. Tests (§4.9).
8. Verify (§6).

## 6. Verification

Inline, after every step from 4 onwards:

```bash
./gradlew -w --console=plain -I gradle/agent-quiet.init.gradle testDebugUnitTest
./gradlew -w --console=plain lintDebug
./scripts/check-tests.sh
```

The unit suite proves almost nothing about this change — it exercises `FileRepositoryTest`,
`ThumbnailDiskCacheKeyTest`, `ThumbnailCacheKeyTest` and `ImageErrorsTest`, none of which touch a
live `ImageLoader`. **The instrumentation suite is the only real evidence**, and three tests carry
it:

| Test | Landmine it proves |
|---|---|
| `ThumbnailFetcherRobustnessTest` | §2.1 — custom fetchers are actually reached |
| `AppImageLoaderGifTest` | §2.2 — thumbnails static, viewer animated |
| `ThumbnailDiskCacheWiringTest` | §2.1 + the disk cache round-trip |

Run it through the `instrumentation-runner` subagent with an emulator up, per `CLAUDE.md`:

```bash
./gradlew connectedDebugAndroidTest
```

Manual checks the automated suite cannot make:

- A folder of videos, PDFs, APKs, MP3s and EPUBs shows real thumbnails (§2.1 has no unit-level tell).
- Scroll a large folder of animated GIFs — memory should stay flat (§2.2).
- Open a GIF full-screen — it animates (viewer keeps its animated decoder).
- Edit an image in place, reopen it in the viewer — the new content shows (§2.3).
- Both themes, since `ImageViewerScreen`'s error slot changed.

## 7. Risks and things this plan does not cover

- **Disk cache reset (benign).** Coil 3 writes a different `DiskLruCache` app version, so the
  existing `image_cache` journal is rejected and the directory rebuilt on first launch. Users lose
  their cached thumbnails once and they re-extract. No code needed; do **not** rename the directory,
  which would leave the old one orphaned instead.
- **R8 and ServiceLoader.** Coil 3's ServiceLoader lookup must survive minification. There are no
  Coil rules in `app/proguard-rules.pro` today because Coil 2 needed none; Coil 3 ships consumer
  rules in its AAR. Confirm on a **release** build (`./scripts/build.sh`) that SVG and animated GIF
  still work — this is the one failure mode debug builds cannot show.
- **Kotlin/Compose compatibility.** Kotlin 2.4.10 and Compose BOM 2026.08.00. `coil-compose` 3.6.1
  is a Compose Multiplatform artifact; a Compose runtime mismatch would surface as a build failure,
  not a runtime one, so it is low-risk but worth watching in step 1.
- **Not covered here:** any change to what the app caches, which sizes it requests, or the
  `ThumbnailFileType` / eviction contract. This is a like-for-like upgrade.

## 8. Rollback

The change is confined to the files listed in §4 plus two build files, with no persisted state
change other than the self-healing cache directory in §7. Reverting the commit is sufficient; the
Coil 3 cache journal is discarded by Coil 2 the same way, costing one more thumbnail re-extraction.
