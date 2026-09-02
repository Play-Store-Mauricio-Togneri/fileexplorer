# Coil 2.7.0 → 3.6.1 migration plan

Status: **implemented**. Kept as the record of why each change is what it is. Five things the plan
got wrong or under-specified were corrected during implementation and are marked **[corrected]**
below.

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
2. Thumbnail cache keys must never stat the file on the main thread (the `addLastModifiedToFileCacheKey`
   comment in `AppImageLoader.build` — the stat happens inside the list's measure pass and can ANR
   on congested storage).
3. Native thumbnail extraction must stay capped at 4 concurrent
   (the `fetcherDispatcher` comment in `AppImageLoader.build` — otherwise
   `MediaMetadataRetriever.finalize() timed out`).

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

`RealImageLoader` calls `addServiceLoaderComponents` for every `ImageLoader` it builds, so the
decoders those artifacts carry land in loaders that never asked for them.

**Consequence if missed:** the thumbnails loader — which registers *no* animated decoder on purpose
— silently acquires `AnimatedImageDecoder` (API 28+) or `GifDecoder`. A grid of GIFs or animated
WebPs then does exactly what `AppImageLoader.thumbnails`' KDoc says it must not: buffers every
encoded file plus per-frame software bitmaps on-heap for every visible and prefetched row. This is
the OOM the design exists to prevent.

**[corrected] Fix: turn discovery off.** `ImageLoader.Builder` does have the switch, on both
loaders:

```kotlin
.serviceLoaderEnabled(false)
```

`coil3.ImageLoadersKt.serviceLoaderEnabled(ImageLoader.Builder, Boolean)` is public and unannotated
in 3.6.1, and `RealImageLoaderKt.addServiceLoaderComponents` reads it before doing anything:

```
0: invokestatic coil3/ImageLoadersKt.getServiceLoaderEnabled
4: ifeq 27          // skips addFetcherFactories and addDecoderFactories
```

The thumbnails loader then simply has no animated decoder, exactly as under Coil 2, and a GIF is
decoded by the platform decoders Coil registers by default. Both loaders already name every decoder
they use (`SvgDecoder` on each, `AnimatedImageDecoder`/`GifDecoder` on the viewer), so discovery has
nothing to contribute and its absence costs nothing.

**What this replaces, and why that approach was wrong.** The original plan asserted 3.6.1 had no
opt-out and worked around it by registering static decoders on the thumbnails loader ahead of the
discovered ones — `StaticImageDecoder` from API 29 plus `BitmapFactoryDecoder`, since the former
declines the `Buffer`-backed sources all five fetchers return. That does produce static thumbnails,
but it is both unnecessary and worse:

- The claim it rested on was false. `serviceLoaderEnabled` exists; the grep that "proved" otherwise
  was mine and it was wrong.
- **It doubled the decode budget.** Coil's own Android defaults build **one** `Semaphore` and pass
  that instance to both factories (`RealImageLoader_androidKt`: semaphore at offset 231,
  `StaticImageDecoder$Factory(Semaphore)` at 250, `BitmapFactoryDecoder$Factory(Semaphore, …)` at
  271). Each no-arg constructor instead allocates its **own** `Semaphore(4)`, so registering both
  by hand raised the thumbnails loader's ceiling from 4 concurrent bitmap decodes to 8 — on the one
  loader whose entire purpose is staying narrow.
- It left the reflective ServiceLoader surface open under R8 for no benefit (§7).

**Trade-off, accepted:** a future Coil artifact whose decoder or fetcher arrives only by discovery
(`coil-video`, say) will not be picked up by drop-in; it has to be registered explicitly on the
loader that wants it. That is the intent — both loaders are deliberately closed sets.

**Guarded by** `AppImageLoaderGifTest.thumbnails_gif_isStatic` (a GIF file, decoded from a
file-backed source) and `thumbnails_gifFromThumbnailFetcher_isStatic` (an EPUB whose cover is a
GIF, so the bytes arrive as an in-memory buffer). The second is the one that fails first if
discovery ever comes back: `AnimatedImageDecoder.Factory.create` tests only
`isApplicable(BufferedSource)` — a byte sniff — and takes a buffer without needing an
`ImageDecoder.Source`, where the platform's `ImageDecoder` path declines it.

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
| — | `coil3.decode.StaticImageDecoder` (new, API 29+ — a Coil default; this app registers neither) |

### Unchanged (confirmed, not assumed)

- `DiskCache`: `openSnapshot`, `openEditor`, `remove`, `clear`, `fileSystem`, `directory`, and
  `Snapshot.data` / `.metadata` / `.close()`, `Editor.data` / `.metadata` / `.commit()` / `.abort()`.
  `Snapshot` now extends `AutoCloseable`, which is what `ImageSource` accepts. **`ThumbnailDiskCache`
  needs no logic changes at all** — imports only.
- `ImageRequest.Builder`: `data`, `size(Int)`, `memoryCacheKey(String)`. `crossfade(Boolean)` keeps
  its name and signature but is now an extension function — **[corrected]** every call site needs
  `import coil3.request.crossfade`, or it fails with `Unresolved reference 'crossfade'` plus a
  cascading `Cannot infer type for type parameter 'T'` on the whole builder chain.
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

New imports: `coil3.ImageLoader`, `coil3.disk.DiskCache`, `coil3.gif.AnimatedImageDecoder`,
`coil3.gif.GifDecoder`, `coil3.memory.MemoryCache`, `coil3.request.addLastModifiedToFileCacheKey`,
`coil3.serviceLoaderEnabled`, `coil3.svg.SvgDecoder`, `okio.Path.Companion.toOkioPath`.

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
        .serviceLoaderEnabled(false)                                   // see §2.2
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
        // No animated decoder: thumbnails render a static first frame. Sufficient only because
        // base() turned ServiceLoader discovery off — see §2.2. What decodes them is the pair Coil
        // registers by default, which share one lock and so stay within a single decode budget.
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

Also update the KDoc on `thumbnails()`: it explains the absence of an animated decoder, which under
Coil 3 is sufficient only in combination with `serviceLoaderEnabled(false)`. Say so, or the next
reader deletes one of the two halves. The memory-cache comment saying Coil keys entries "by file
path only" needs the same treatment — the key is now the file's `file://` Uri (§4.9).

### 4.3 The five fetchers

`VideoThumbnailFetcher`, `PdfThumbnailFetcher`, `ApkThumbnailFetcher`, `AudioThumbnailFetcher`,
`EpubThumbnailFetcher`. Identical shape of change in each:

```kotlin
// imports: coil.* → coil3.*, plus coil3.Uri
import coil3.Uri
import coil3.fetch.SourceFetchResult

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

One substantive sentence to add while there: on API 29+ Coil 3 prefers `StaticImageDecoder` over
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
keep their signatures — but **[corrected]** `crossfade` is an extension function now, so all five
files also need `import coil3.request.crossfade`.

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

### 4.9 Tests (8 changed, 1 added)

| File | Work |
|---|---|
| `AppImageLoaderGifTest` (androidTest) | **Real work.** `coil.drawable.ScaleDrawable` → `coil3.size.ScaleDrawable`; `SuccessResult.drawable` → `.image.asDrawable(context.resources)`. Its `classify()` helper is what proves §2.2 held — it must keep returning `"static"` for thumbnails and `"animated"` for the viewer |
| `AppImageLoaderCacheKeyTest` (androidTest) | **[corrected] Not imports only.** `loaderKey_isPathOnly` asserted `file.absolutePath`; under Coil 3 the `File` is mapped to a `file://` Uri before keying, `FileUriKeyer` returns null while `addLastModifiedToFileCacheKey` is false, and `UriKeyer` answers with `Uri.toString()` — so the key is now `file:` + the path. Assertion updated to match. The invariant the test exists to pin (no timestamp in the key) is unchanged |
| `ThumbnailDiskCacheTest` (androidTest) | Imports; `SourceResult` → `SourceFetchResult`. Its hand-built `Options(context =, size =, diskCachePolicy =)` uses named arguments and all three names survive. **[corrected]** It also builds a `DiskCache` of its own, so it needs the same `.directory(…toOkioPath())` change as `AppImageLoader` |
| `ThumbnailDiskCacheWiringTest` (androidTest) | Imports |
| `ThumbnailFetcherRobustnessTest` (androidTest) | Imports. **This is the test that proves §2.1** — it drives real fetches through the loader, so it fails loudly if the fetchers are still `Factory<File>` |
| `ItemInfoMetadataTest` (androidTest) | Imports |
| `FileRepositoryTest` (unit) | Imports only — `mockk<DiskCache>` and `verify { diskCache.remove(key) }` are unaffected |
| `ImageErrorsTest` (unit) | One comment referencing `coil.decode.GifDecoder`; assertions unchanged |

**Add two tests.** **[corrected]** `AppImageLoaderGifTest` asserted only that the *viewer*
animates — nothing anywhere asserted that a thumbnail does **not**, so §2.2 could have regressed
silently. Both were added:

- `thumbnails_gif_isStatic` — a GIF through Coil's own file fetcher, so the source is file-backed.
- `thumbnails_gifFromThumbnailFetcher_isStatic` — an EPUB built in-test holding `OEBPS/cover.gif`,
  so `EpubThumbnailFetcher` hands the GIF over as an in-memory buffer. This is the shape an
  animated decoder claims by sniffing bytes, so it is the case that fails first if
  `serviceLoaderEnabled(false)` is ever dropped.

**[corrected] And one unit test**, `FileUriTest`, for the new `Uri.toFileOrNull()`. `CLAUDE.md`
requires a unit test for a new utility, and this one is the gate that decides whether *any* of the
five fetchers is constructed: tighten it by accident and every media and document thumbnail becomes
a generic icon with nothing failing. It runs on the JVM — `coil3.Uri` is pure Kotlin with a public
structured constructor, the same one `FileMapper` uses — and covers the `file://` round trip
(including a path holding `#`, `?`, `%`, a space and non-ASCII, none of which are percent-encoded
on either leg), `content://`, `android.resource://`, a scheme-less Uri, and a `file:` Uri with no
path.

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

- **Disk cache reset (benign, and certain — not a maybe).** Same directory, same journal magic
  (`libcore.io.DiskLruCache`) and format version, but the **app version** in the journal header
  changed: Coil 2's `RealDiskCache` constructs `DiskLruCache(…, appVersion = 1, valueCount = 2)`,
  Coil 3's uses `appVersion = 3`. `readJournal()` therefore throws on the existing header and
  `initialize()` deletes the directory. Every cached video frame, PDF page, APK icon, album art and
  EPUB cover is re-extracted once on the first launch after the update — the cost
  `ThumbnailDiskCache`'s own KDoc says the cache exists to avoid, paid once. No code needed; do
  **not** rename the directory, which would orphan the old one instead of letting Coil clear it.
  Worth a release-note line so a first-launch burst of re-extraction is not read as a regression.
- **[corrected] R8 and ServiceLoader — no longer applicable.** The original text said no keep rule
  was needed because "Coil 3 ships consumer rules in its AAR". That was wrong twice over: only
  `coil-core.aar` ships a `proguard.txt` (`-dontwarn coil3.PlatformContext` and a
  `GenericViewTarget` keep, neither relevant), while `coil-gif` and `coil-svg` — the two artifacts
  that actually ship `META-INF/services` entries — ship none. Had discovery stayed on, R8 renaming
  or shrinking the two `internal` `*ServiceLoaderTarget` classes would have thrown
  `ServiceConfigurationError` in the release build only. §2.2's `serviceLoaderEnabled(false)`
  removes the question: `ServiceLoader.load` is never reached. Still confirm SVG and animated GIF
  on a **release** build (`./scripts/build.sh`) — that remains the one failure mode a debug build
  cannot show.
- **Kotlin/Compose compatibility.** Kotlin 2.4.10 and Compose BOM 2026.08.00. `coil-compose` 3.6.1
  is a Compose Multiplatform artifact; a Compose runtime mismatch would surface as a build failure,
  not a runtime one, so it is low-risk but worth watching in step 1.
- **Not covered here:** any change to what the app caches, which sizes it requests, or the
  `ThumbnailFileType` / eviction contract. This is a like-for-like upgrade.

## 8. Rollback

The change is confined to the files listed in §4 plus two build files, with no persisted state
change other than the self-healing cache directory in §7. Reverting the commit is sufficient; the
Coil 3 cache journal is discarded by Coil 2 the same way, costing one more thumbnail re-extraction.
