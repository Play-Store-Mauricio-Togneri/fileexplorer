# Plan 3: give files with no recognised extension a real type before opening them

Source: `todo.md` row 3 (Tier 3–4). Code read on `release/2.8.0` (`versionName` 2.8.0) and compared
with `release/2.6.0` and `release/2.7.0`. This is a plan only. Nothing has been implemented, built
or run.

Paths are relative to `app/src/main/java/com/mauriciotogneri/fileexplorer/` unless they start with
`app/`, `.claude/` or `scripts/`.

---

## 1. Problem

When someone taps a file whose name has no extension, or an extension Android doesn't know
(`photo`, `README`, `download`, `report.dat`), the app asks Android to open it as "any type". Android
then lists every app on the phone that can open *some* kind of file: galleries, music players, PDF
readers, browsers, maps, and so on. Most of them can't open this particular file. The user has to
guess, often guesses wrong, and gets an error in someone else's app. "Open with" behaves the same.

The app never looks inside the file, so a JPEG called `photo` gets the same treatment as random
binary data. A few bytes at the start of the file are usually enough to tell what it is.

Goal: when the name gives no type, read the start of the file and recognise the common formats.
Send that type to Android, so the picker shows only apps that fit, and the app's own viewers and
"extract" dialog get the files they can handle. Fall back to "any type" only when the contents
don't match anything. Then measure how much of the "any type" share goes away.

---

## 2. Verified cause

| # | Claim in todo row 3 | Verdict | Evidence (`release/2.8.0` unless noted) |
|---|---------------------|---------|------------------------------------------|
| 1 | The type comes only from the file name (`data/util/MimeTypeUtil.kt:10-19`) | **Confirmed**, lines exact | `getMimeType` asks `URLConnection.guessContentTypeFromName`, then `MimeTypeMap.getMimeTypeFromExtension`, else returns `*/*`; any exception also gives `*/*` (10-19). The type is fixed when the folder is listed: `FileItem.from` stores it (`data/model/FileItem.kt:80`), called per entry by `FileRepository.listFiles` on `Dispatchers.IO` (`data/repository/FileRepository.kt:157-180`, call at 175). The open path reuses that stored value (`util/IntentUtil.kt:156`). Its `ifEmpty` refill only fires for an empty type, which only a favorited directory that later became a file has (`util/StoredEntryDestination.kt:37-43,60`). |
| 2 | Anything unknown is sent as "any type" (`util/IntentUtil.kt:167-181`) | **Confirmed, lines drifted a little, and it reaches further than "open"** | Open: type at 156, intent built at 167-171, resolve and launch at 173-184. The same `*/*` also goes to: "Open with" (type 218, intent 229-233, chooser 236); single-file share (83, `ACTION_SEND` at 85-89); the in-app viewers' `file_opened` events and their share buttons, because each viewer rebuilds its item by name (`ui/screens/textviewer/TextViewerViewModel.kt:79,114`, `ui/screens/imageviewer/ImageViewerViewModel.kt:76,85-91`, `ui/screens/pdfviewer/PdfViewerViewModel.kt:151,258-264`, `ui/screens/mediaviewer/MediaViewerViewModel.kt:189-195`). Multi-file share is `*/*` always, whatever the files are (`IntentUtil.kt:116`); that's a separate issue. |
| 3 | So the system picker lists nearly every app | **Confirmed. This is a platform rule, not repo code** | An intent typed `*/*` matches every activity whose intent filter names *any* MIME type (Android's `IntentFilter` matching rule for a wildcard intent type). The manifest's `<queries>` entry for `ACTION_VIEW` + `*/*` (`app/src/main/AndroidManifest.xml:22-26`) makes all of those apps visible to this app. With several matches, `resolveActivity` returns the system resolver, so `startActivity` shows the disambiguation picker (`IntentUtil.kt:173-181`). The KDoc at `IntentUtil.kt:338-344` already records one result of this: a wildcard type resolving to the NFC tag viewer. |
| 4 | The file's contents are never checked | **Confirmed** | Nothing on the open, open-with or share paths reads the file before choosing the type. No code in `app/src/main` calls `guessContentTypeFromStream` or checks magic bytes. The only content reads happen after routing: zip4j in the extract dialog (`util/UncompressHandler.kt:57-80`), the viewers, and the thumbnail fetchers. |
| 5 | Unknown: why the share rose. The open and type code is the same in 2.6, 2.7 and 2.8 | **Code part confirmed. The cause can't be found from code** | `getMimeType` is identical on all three branches (`git show release/2.6.0:…/MimeTypeUtil.kt`), and so is `FileItem.from`'s type line. Between versions `IntentUtil.kt` changed only in rating-prompt hooks and the open-with availability report (`git diff release/2.7.0 HEAD -- …/IntentUtil.kt`), never the type lines. One event-side change: 2.7 added `file_opened` from the new PDF and media viewers (`git grep trackFileOpened release/2.6.0` has no `PdfViewerViewModel`/`MediaViewerViewModel`). That adds typed opens, so it should *lower* the `*/*` share, not raise it. **Hypothesis, not verified:** row 2 notes that 2.6 still logged Play pre-launch robots on Test Lab. If the robots mostly opened typed files, dropping them in 2.7 would raise the `*/*` share among the remaining users. |

Further findings that shape the plan (not in the todo):

| # | Finding | Evidence | Why it matters |
|---|---------|----------|----------------|
| 6 | Every open runs on the main thread, synchronously | `IntentUtil.openFile` returns its `OpenFileResult` directly to click lambdas: `ui/screens/folder/FolderScreen.kt:463`, `ui/screens/search/SearchScreen.kt:262`, `ui/screens/analyzercategory/AnalyzerCategoryScreen.kt:264`, `ui/screens/home/HomeScreen.kt:581` (via `openRecentFile`/`openFavorite`, 293/314, 528-572). `ItemInfoScreen.kt:146` calls it from a `LaunchedEffect` collector. "Open with" is the same at `FolderScreen.kt:558`, `SearchScreen.kt:343`, `HomeScreen.kt:376,448`, `AnalyzerCategoryScreen.kt:291` | AGENTS.md: file I/O never on the main thread. Reading the file's first bytes means the open has to suspend, so the call sites change |
| 7 | No `content://` input exists | No `DocumentFile`, `OPEN_DOCUMENT*`, `DocumentsContract` or SAF code in `app/src/main`. Every open starts from `FileItem.path`, a `java.io.File` path. SD cards and USB drives are reached as `/storage/…` paths. The app only *creates* `content://` URIs, through the stock androidx `FileProvider`, to hand files to other apps (`IntentUtil.kt:482-488`, `AndroidManifest.xml:137-145`) | The sniffer reads a `File`. No `ContentResolver` path is needed. Slow volumes (SD card, a USB drive spinning up) still need a timeout |
| 8 | The stock `FileProvider` gives other apps `application/octet-stream` for an extensionless file | androidx `FileProvider.getType` uses only the extension and returns `application/octet-stream` when that fails (library behaviour) | An app that ignores the intent's type and asks the provider still sees a generic type. The untyped fallback `openWithFallback` (`IntentUtil.kt:329-336`) also resolves to that. See Risks |
| 9 | Icons, thumbnails, search type filters and the bottom-sheet events are name-based too | `FileItem` predicates (`FileItem.kt:28-50`); thumbnail gate `data/util/ThumbnailDiskCache.kt:192`; fetchers `VideoThumbnailFetcher.kt:96`, `PdfThumbnailFetcher.kt:98`, `ApkThumbnailFetcher.kt:167`, `AudioThumbnailFetcher.kt:74`, `EpubThumbnailFetcher.kt:152`; bottom sheet `ui/components/FileActionsBottomSheet.kt:62-144`; analyzer `data/repository/AnalyzerRepository.kt:190` | An extensionless JPEG keeps a generic icon and no thumbnail. That's not the "any type" problem, and fixing it means reading files while listing (see Approach A2). Out of scope here |
| 10 | The metric | `file_opened` (`data/util/AnalyticsTracker.kt:206-214`): `extension`, `mime_type`, `source`. `file_open_failed` (237-252) carries the same params plus `reason`, and its KDoc requires the two to stay parallel. P7 in `.claude/skills/analyze-analytics/SKILL.md:258` = `file_opened` with `mime_type` `*/*` ÷ all opens, plus how many have `extension` `unknown` | The new param must go on both events. `extension` is an allowlist (`data/util/FileExtensionUtil.kt:36-43`), so `unknown` means no extension *or* an extension the app has never heard of |
| 11 | `application/octet-stream` is as unhelpful as `*/*` | It's the platform map's answer for generic extensions such as `.bin` (assumption about the platform table; check once on the emulator) | Treat it like "no type": sniff it, and keep it if nothing matches |
| 12 | Existing tests for MIME detection and opening | Unit: `MimeTypeUtilGetMimeTypeTest.kt:19-23` (pins `*/*` for an extensionless path; `MimeTypeMap` throws on the JVM, so the catch is what answers), `MimeTypeUtilTest`, `MimeTypeUtilByExtensionTest`, `MimeTypeUtilIsTextTest`, `MimeTypeUtilIsViewableImageTest`, `MimeTypeUtilIsViewableMediaTest`, `StoredEntryDestinationResolverTest`, `IntentUtilReviewPromptFailureTest`, `IntentUtilOpenWithReviewPromptTest`, `HomeScreenReviewPromptFailureTest`, `AnalyticsTrackerReviewHookTest`. Instrumentation: `integration/FileOpenRoutingTest.kt`, `util/IntentUtilOpenFileTest.kt`, `util/IntentUtilOpenFileWithTest.kt`, `util/IntentUtilReviewPromptTest.kt`, `util/IntentUtilShareTest.kt`, `ui/screens/iteminfo/ItemInfoScreenEventsTest.kt` | No test covers content-based typing, because none exists. Several tests call the open functions directly and must adapt once those functions suspend |

---

## 3. Approach

### D1. When and where the file is read

| # | Option | Cost | Benefit |
|---|--------|------|---------|
| 1 | **A1, at action time (recommended).** Read the start of the file only when the user opens it or uses "Open with", and only when the name gave no type. `openFile`/`openFileWith` become `suspend`, read on `Dispatchers.IO` with a timeout, and return to the main thread to launch | ~10 call sites wrap the call in a coroutine (Step 5). The tests that call these functions directly adapt | Zero cost for named files and for listing. One 4 KB read per tap on an unknown file. Covers every place a file is opened from: folder, search, recents, favorites, item info, analyzer |
| 2 | A2, at listing time, inside `FileItem.from` | One extra open + read per unknown file on every listing, search hit and analyzer page. Folders full of extensionless files (app caches, `.git/objects`) pay the most and are opened the least | Tiny diff. Icons, thumbnails and type filters improve too |
| 3 | A3, a background pass after listing (stream sniffed types into the folder's state, the way child counts already stream) | A background read for every unknown file on every folder visit; per-screen work for five sources; a tap before the pass finishes still gets `*/*` | Synchronous call sites stay as they are; icons improve in the folder view only |

Recommendation: **A1.** The problem is the open, and A1 reads only the one file the user chose. A2
would slow down listing for everyone to fix something only opens need. Icons and thumbnails can
follow later as their own change (Open question 8).

A1 keeps named files exactly as they are today. The resolver returns their type without
suspending, and the call sites use `launch(start = CoroutineStart.UNDISPATCHED)`, so a known-type
open still happens synchronously inside the click.

### D2. How to recognise formats: library, platform helper or own table

| # | Option | Verdict |
|---|--------|---------|
| 1 | `URLConnection.guessContentTypeFromStream` (JDK/libcore) | **No.** It knows only a handful of formats: Java class, HTML/XML, GIF, X bitmaps, PNG, some JPEGs, `.snd`, RIFF. Per the OpenJDK source Android's libcore is based on (worth a one-minute check), it calls any RIFF file `audio/x-wav`, WebP and AVI included, and calls Office 97 files `image/vnd.fpx`. It has no PDF, ZIP, MP4, WebP, MP3 or text detection. It also needs a stream that supports mark/reset |
| 2 | Apache Tika core | **No.** About 1,500 types, but the jar is large, it parses an XML magic database on first use, and needs R8 keep rules. Overkill for ~15 formats |
| 3 | Platform decoders (`BitmapFactory` with `inJustDecodeBounds` gives `outMimeType`; `MediaMetadataRetriever` gives a media type) | **No.** Images or media only, nothing for PDF/ZIP/text, heavier, `MediaMetadataRetriever` runs native extractors, and none of it runs in JVM unit tests |
| 4 | **A small hand-written table (recommended)** | ~150 lines of pure Kotlin, unit-testable on the JVM with byte arrays. Covers exactly the formats the app routes. Its outputs are a fixed set of constants, which matters for analytics (D8) |

### D3. Formats and exact signatures

The rule for inclusion: the app itself can show or route the format (images, PDF, audio/video, text,
the ZIP family), and its signature is unambiguous at a fixed offset. Offsets are 0-based; `"…"` is
ASCII.

| # | Format | Signature | Sent as | Why |
|---|--------|-----------|---------|-----|
| 1 | JPEG | `FF D8 FF` at 0 | `image/jpeg` | Todo. Three bytes cover JFIF (`E0`), EXIF (`E1`), raw (`DB`) and the rest; text can't start with `FF` |
| 2 | PNG | `89 50 4E 47 0D 0A 1A 0A` at 0 | `image/png` | Todo |
| 3 | GIF | `"GIF87a"` or `"GIF89a"` at 0 | `image/gif` | Cheap; common for web images saved without a name; the image viewer plays GIFs |
| 4 | WebP | `"RIFF"` at 0 and `"WEBP"` at 8 | `image/webp` | The usual format for web images saved from a browser |
| 5 | WAV / AVI | `"RIFF"` at 0 and `"WAVE"` / `"AVI "` at 8 | `audio/x-wav` / `video/x-msvideo` | Same 12-byte check as WebP, so free; both are in the media viewer's lists (`MimeTypeUtil.kt:140,168`) |
| 6 | MP4 family (ISO-BMFF) | `"ftyp"` at 4; big-endian box size at 0 between 16 and 4096; major brand at 8; compatible brands from 16 to the box end | by brand, see the table below | Todo (MP4). The brand table is required, not optional: HEIC photos, AVIF and M4A share the `ftyp` box, so `ftyp` → `video/mp4` alone would send phone photos to video players |
| 7 | Matroska / WebM | `1A 45 DF A3` at 0; DocType element `42 82` within the first 64 bytes | `video/webm` if the DocType is `"webm"`, else `video/x-matroska` | Common download format; the media viewer plays both |
| 8 | Ogg | `"OggS"` at 0 and version byte `00` at 4. The first packet starts at `27 + byte[26]` | `video/ogg` if that packet starts `80 "theora"` or `"fishead"`, else `audio/ogg` (Vorbis, Opus, FLAC-in-Ogg) | Messenger voice notes; four bytes plus a version check |
| 9 | FLAC | `"fLaC"` at 0 | `audio/flac` | Four bytes; the media viewer plays it |
| 10 | MP3 with ID3v2 tag | `"ID3"` at 0; byte 3 is 2, 3 or 4; byte 4 ≠ `FF`; bytes 6-9 each < `80` | `audio/mpeg`, or `audio/flac` if `"fLaC"` follows the tag inside the 4 KB prefix (tag length = 10 + syncsafe size, + 10 more if flag `10` is set) | Most distributed MP3s carry an ID3v2 tag |
| 11 | PDF | `"%PDF-"` at 0, or at the start of a line within the first 1,024 bytes | `application/pdf` | Todo. PDF readers accept a header anywhere in the first 1 KB. Requiring a line start avoids matching a text file that only mentions `%PDF-` |
| 12 | ZIP | `"PK" 03 04` at 0 | refined from the archive's directory, see D4 | Todo |
| 13 | Plain text | the heuristic in D5, run only after everything above fails | `text/plain` | Todo. Covers `README`, `LICENSE`, `Makefile`, logs and source files whose extension the platform doesn't know |

ISO-BMFF brands. The major brand decides; compatible brands are read only for the generic image
brands:

| # | Major brand | Sent as |
|---|-------------|---------|
| 1 | `avif`, `avis` | `image/avif` |
| 2 | `heic`, `heix`, `heim`, `heis` | `image/heic` |
| 3 | `hevc`, `hevx`, `hevm`, `hevs` | `image/heic-sequence` |
| 4 | `mif1`, `miaf`, `msf1` | `image/avif` if a compatible brand is `avif`/`avis`; `image/heic` if one is in row 2; else `image/heif` (`image/heif-sequence` for `msf1`) |
| 5 | `M4A `, `M4B `, `M4P `, `F4A `, `F4B ` | `audio/mp4` |
| 6 | `qt  ` | `video/quicktime` |
| 7 | starts with `3g2` | `video/3gpp2` |
| 8 | starts with `3g` (other) | `video/3gpp` |
| 9 | `isom`, `iso2`–`iso9`, `mp41`, `mp42`, `mp71`, `avc1`, `dash`, `mmp4`, `M4V `, `M4VH`, `M4VP`, `f4v `, `MSNV`, `XAVC` | `video/mp4` (an audio-only `dash` file goes to video players too, which play it) |
| 10 | anything else (Canon CR3 `crx `, unknown brands) | no match. Never guess `video/mp4` from an unknown brand: CR3 lists `isom` among its compatible brands |

Every output above is in the app's own viewable sets (`MimeTypeUtil.kt:74-177`), so a sniffed
file that no installed app handles still reaches the in-app viewer.

**Considered and left out**, each a one-line table addition later if the data asks for it:

| # | Format | Why not now |
|---|--------|-------------|
| 1 | BMP (`"BM"`) | A two-byte ASCII signature matches ordinary text; rare without an extension |
| 2 | MP3 without ID3, AAC ADTS | Frame-sync detection (`FF Ex`/`FF F1`) needs bitrate tables and a second-frame check to avoid false matches on binary data |
| 3 | RAR, 7z, gzip, xz, tar | The app can't extract them, so it only gains a narrower picker; rare without an extension |
| 4 | SQLite (`"SQLite format 3\0"`) | Very common *without* an extension in app data folders, but nobody opens those, and few apps open databases |
| 5 | Office 97 (`D0 CF 11 E0 A1 B1 1A E1`) | Telling `.doc`/`.xls`/`.ppt`/`.msg` apart means parsing the compound-file directory |
| 6 | TIFF and camera RAW (`II*\0`, `MM\0*`) | TIFF's signature is shared by most RAW formats; rare without an extension |
| 7 | SVG, HTML, XML | Reported as `text/plain`, which is safe: text editors open them. Choosing `text/html` would send them to browsers; that's a product choice to make later |
| 8 | UTF-16 text | Its NUL bytes fail the text test, and the in-app text viewer decodes only UTF-8 (`data/util/TextFilePreview.kt:78`) |
| 9 | MPEG-TS, MIDI, old QuickTime without `ftyp` | Rare; the weaker signatures risk false matches |

### D4. ZIP, which is also APK, DOCX, XLSX, PPTX, JAR and EPUB

What the app does with each type matters. `application/zip` goes to the in-app extract dialog
(`IntentUtil.kt:152-154`). The APK type goes to the install flow (148-150). Anything else goes to
other apps. Today an extensionless DOCX or APK at least shows Word or the package installer *among*
every app. Sending every ZIP as `application/zip` would make those files *worse*: they would only
reach the extract dialog.

| # | Option | Verdict |
|---|--------|---------|
| 1 | Z1: every ZIP → `application/zip` | Simple, but a regression for extensionless DOCX/EPUB/APK, as above |
| 2 | Z2: look only at the first entry's local header (inside the 4 KB prefix) | EPUB/ODF are reliable (their spec requires an uncompressed `mimetype` entry first). OOXML can't tell DOCX from XLSX from the first entry, and APK has no fixed first entry |
| 3 | **Z3: read the archive's central directory (recommended)** | Robust whatever the entry order. The app already does it: `ZipMetadataExtractor.kt:9-35` uses `java.util.zip.ZipFile`, and the extract dialog reads the same headers with zip4j on every `.zip` tap, so this adds no new kind of cost |

Z3 rules. Classification over the entry names is a pure function, so it's unit-testable:

| # | Entries | Sent as |
|---|---------|---------|
| 1 | a `mimetype` entry whose content (read ≤ 100 bytes, trimmed) is exactly `application/epub+zip` or `application/vnd.oasis.opendocument.{text,spreadsheet,presentation,graphics}` | that constant, taken from the table, never the bytes read (D8) |
| 2 | `[Content_Types].xml` plus an entry starting `word/` / `xl/` / `ppt/` | the matching `application/vnd.openxmlformats-officedocument.*` type (the same strings as `MimeTypeUtil.kt:350-352`) |
| 3 | `AndroidManifest.xml` plus `classes.dex` or `resources.arsc` | `application/vnd.android.package-archive`, so it goes to the install flow, which always ends in the system's confirmation (Open question 2) |
| 4 | anything else (plain ZIP, JAR, AAR, XAPK) or a directory that can't be read (truncated download, corrupt) | `application/zip`, so the extract dialog opens, and that dialog already reports an unreadable archive (`UncompressHandler.kt:68-72`) |

Use `java.util.zip.ZipFile` and the existing `isUnreadableZip` (`data/util/ZipErrors.kt:23`) to
tell expected failures from real ones. Since targetSdk 34, `ZipFile` throws `ZipException` for
entry names containing `..`; that is caught and gives row 4. Stop iterating names after 2,000
entries; the rows above are decided long before that in real files.

### D5. Plain-text heuristic

Run on the same 4 KB prefix, only after every signature failed:

1. Empty prefix: no match (an empty file stays `*/*`; there is nothing to judge).
2. Skip a UTF-8 BOM (`EF BB BF`).
3. Any `00` byte: not text. This alone rejects nearly all binary formats, UTF-16 and UTF-32.
4. Decode with a strict UTF-8 `CharsetDecoder` (`REPORT` on malformed or unmappable input). Pass
   `endOfInput = reachedEof`, so a multi-byte character cut off by the 4 KB cap counts as fine, but
   one cut off by the real end of the file counts as malformed. Malformed: not text.
5. Count control characters: U+0001–U+001F except TAB, LF, CR, FF and ESC (for ANSI-coloured
   logs), plus U+007F and U+0080–U+009F. More than 1% of the decoded characters: not text.
6. Otherwise `text/plain`.

| # | Option | Verdict |
|---|--------|---------|
| 1 | **T1: strict UTF-8 as above (recommended)** | A false positive needs 4 KB of binary data with no NUL that is also valid UTF-8, which compressed or encrypted data essentially never is. A false negative (Latin-1 or Windows-1252 text with accents) just stays `*/*`, which is today's behaviour. It also matches the in-app viewer, which decodes UTF-8 only |
| 2 | T2: also accept legacy 8-bit text when ≥ 95% of bytes are printable | Catches old Windows text, but those files would show replacement characters in the in-app viewer, and the threshold admits more binary data |
| 3 | T3: no text detection | Leaves out `README`/`LICENSE`/logs, probably the most common extensionless files people tap |

### D6. How many bytes, and how to read them safely

- **4,096 bytes** (one page). That covers the PDF 1 KB window, the ISO-BMFF brands, the EBML
  DocType, the Ogg codec ID, the ID3 skip and a fair text sample. Reading 64 bytes costs the same
  as 4 KB; opening the file is the expensive part.
- Read only when `file.isFile` (excludes directories, FIFOs and device nodes, which could block on
  open) and its length is > 0. `minSdk` is 24, so use a read loop rather than `readNBytes`
  (API 33).
- `FileNotFoundException`, other `IOException`s and `SecurityException` mean "unreadable", with no
  non-fatal report. They're environmental (a removed SD card, permissions), and the open that
  follows reports its own failures. Any other exception → `ErrorReporter.warning(e.scrubbed(),
  "sniff_mime_type")`, then "unreadable". It never throws into the open.
- **Timeout 300 ms** (Open question 5). Use the `StartupFolderResolver` pattern
  (`util/StartupFolderResolver.kt:49-62`): run the read with `scope.async(dispatcher)` in a scope
  the caller doesn't own, then wait with `withTimeoutOrNull(timeoutMs) { deferred.await() }`. A
  blocking read can't be cancelled, so a timeout around the read itself would bound nothing. A
  timeout gives today's `*/*`, and the user is never kept waiting on a slow SD card or a USB drive
  spinning up. The stuck read holds one IO thread until it returns. It only holds a `File`, never a
  `Context`.
- Cancellation: if the screen goes away mid-read, the coroutine's scope (`rememberCoroutineScope`)
  is cancelled and nothing is launched.

### D7. Where the sniffed type is used

| # | Consumer | Gets the sniffed type? | Why |
|---|----------|------------------------|-----|
| 1 | Open (`openFile`) | **Yes** | The measured problem. It also feeds the routing: `isApk`, `isZip`, `isText`, `isViewableImage`, `isPdf`, `isViewableAudio`/`Video` all read `mimeType`, so a sniffed file reaches the right in-app fallback |
| 2 | Open with (`openFileWith`) | **Yes** (Open question 3) | Apps that register for `*/*` still match a specific type, so file managers and hex editors stay in the list as an escape hatch. A wrong guess therefore narrows the list without hiding the any-file apps |
| 3 | In-app viewers' `file_opened` and their share button | **Yes**, the viewer models resolve too | Otherwise every in-app open of a sniffed file would still be counted as `*/*`, which would distort P7. As a side effect, the viewers' share sends the right type with no change to `shareFiles` |
| 4 | Share from lists (single file) | **Optional, Step 7** (Open question 1) | Not in the metric; ~10 more call sites. Multi-file share stays `*/*` regardless |
| 5 | Icons, thumbnails, search type filter, bottom-sheet events, recents' stored type | **No** | They would need listing-time reads (A2). Recents keep storing the name type (`data/repository/RecentFilesRepository.kt:87`), and a reopen is resolved again |

### D8. Analytics

- Add one param, **`type_source`**, to both `file_opened` and `file_open_failed` (their KDoc
  requires them to stay parallel). Values, a fixed vocabulary:
  - `extension`: the name gave the type (including `application/octet-stream` with no match).
  - `content`: the type came from the file's first bytes.
  - `none`: neither; the file went out as `*/*`.
  - `unread`: a read was needed but failed or timed out, so the name's type went out.
- `mime_type` keeps meaning "the type sent". A sniffed value must be one of the sniffer's
  constants, never text read from the file. That's why ZIP's `mimetype` entry is matched against an
  allowlist rather than echoed. Combined with the existing `extension` allowlist, no event can carry
  anything from inside the user's file (AGENTS.md: params describe a file, never identify it).
- Telemetry stays fire-and-forget: the param is computed from values already at hand and adds no
  failure path.
- `installApk` and the viewers don't hold the resolver's answer, so they *derive* `type_source`:
  if the name's type equals the sent type, it's `extension` (or `none` for `*/*`); otherwise
  `content`. They never report `unread`, because by then the file has been read successfully.

### D9. Caching

**No cache.** The read happens once per user action, never per recomposition. It costs about as
much as the `stat` and canonical-path calls the open already makes. A cache keyed on
path + size + modified time would add invalidation risk (FAT/exFAT SD cards store modified time to
2 seconds) for a saving nobody would notice. The viewers resolve again on their own IO dispatcher.
That's one more 4 KB read of a file they are about to read anyway. A cache becomes necessary only
if listing-time sniffing (A2) is ever added for icons.

---

## 4. Step-by-step changes

### Step 1. `data/util/MimeTypeSniffer.kt` (new, pure Kotlin, no Android imports)

```kotlin
object MimeTypeSniffer {
    const val PREFIX_BYTES = 4096

    /** The type [prefix] proves, or null. Only ever returns a member of [TYPES]. */
    fun sniff(prefix: ByteArray, length: Int, reachedEof: Boolean): String?

    /** Refines an archive [sniff] called `application/zip`, from its entry names (D4). */
    fun zipType(entryNames: Sequence<String>, mimetypeEntry: String?): String

    /** Every value the two functions above can return: the analytics vocabulary for sniffed types. */
    val TYPES: Set<String>
}
```

- The check order follows D3: fixed-offset signatures first, then PDF's 1 KB window, then text.
- Byte reads are bounds-checked against `length`, so a short file can never index past its
  data.
- KDoc states why it exists (todo row 3), the inclusion rule from D3, and the "constants only"
  rule from D8.

### Step 2. `data/util/MimeTypeResolver.kt` (new, the I/O around the sniffer)

```kotlin
enum class TypeSource(val param: String) { EXTENSION("extension"), CONTENT("content"), NONE("none"), UNREAD("unread") }

data class ResolvedType(val mimeType: String, val source: TypeSource)

class MimeTypeResolver(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMs: Long = TIMEOUT_MS,
    private val sniff: suspend (File) -> SniffOutcome = { readAndSniff(it) }
) {
    /** Returns at once, without I/O, when [nameType] is specific; otherwise reads within [timeoutMs]. */
    suspend fun resolve(path: String, nameType: String): ResolvedType

    companion object {
        const val TIMEOUT_MS = 300L
        fun needsContent(nameType: String) = nameType == "*/*" || nameType == "application/octet-stream"
        /** Blocking, no timeout: for callers already on an I/O dispatcher (the viewer models). */
        fun resolveNow(item: FileItem): FileItem
        /** type_source for a value the resolver did not produce here (D8). */
        fun sourceOf(path: String, sentType: String): TypeSource
        internal fun readAndSniff(file: File): SniffOutcome   // D6 read rules + ZIP refinement (D4)
    }
}

sealed interface SniffOutcome {
    data class Match(val mimeType: String) : SniffOutcome
    data object NoMatch : SniffOutcome
    data object Unreadable : SniffOutcome
}

/** [this] unchanged when [type] is already its type, so equality-based tests and state stay stable. */
internal fun FileItem.withMimeType(type: String): FileItem = if (type == mimeType) this else copy(mimeType = type)
```

- The shape mirrors `StartupFolderResolver` (injected scope, dispatcher, timeout and work), so the
  timeout is testable in virtual time the same way (`StartupFolderResolverTest.kt:94-108`).
- `resolve` maps outcomes: `Match` → (type, `CONTENT`). `NoMatch` → (nameType, `NONE` if
  `*/*`, else `EXTENSION`). `Unreadable` or timeout → (nameType, `UNREAD`).
- Takes the name type as a parameter rather than calling `MimeTypeUtil.getMimeType` itself, so
  the class runs on the JVM (`MimeTypeMap` throws there; see `MimeTypeUtilGetMimeTypeTest.kt:9-17`).

### Step 3. `data/util/AnalyticsTracker.kt`

- `trackFileOpened(extension, mimeType, source, typeSource: TypeSource)` and
  `trackFileOpenFailed(extension, mimeType, source, reason, typeSource: TypeSource)` add
  `"type_source" to typeSource.param`. Taking the enum, not a `String`, keeps the vocabulary
  closed at compile time.
- Extend both KDocs: what each value means (D8), and that `content` rows' `mime_type` comes from
  `MimeTypeSniffer.TYPES`.

### Step 4. `util/IntentUtil.kt`

- Add `private val mimeTypeResolver = MimeTypeResolver(scope)`. Reuse the existing process
  scope at line 54; the awaiting coroutine belongs to the caller, so the timeout bounds the wait.
- `openFile` becomes `suspend`. Its first lines:
  ```kotlin
  val nameType = file.mimeType.ifEmpty { MimeTypeUtil.getMimeType(File(file.path)) }
  val resolved = mimeTypeResolver.resolve(file.path, nameType)
  val typed = file.withMimeType(resolved.mimeType)
  if (typed.isApk) return openApkFile(context, typed, source)
  if (typed.isZip) return OpenFileResult.RequiresUncompress(typed)
  ```
  The rest of the body (156-214) is unchanged except that it uses `typed` and `resolved.mimeType`,
  and returns `typed` in every `Requires*` result. The `ifEmpty` refill now runs before
  `isApk`/`isZip`, which closes the gap `StoredEntryDestination.kt:37-43` describes. That gap is
  harmless today because the resolver already refills.
- `openFileWith` becomes `suspend` and gets the same three lines. `reportHandlerAvailability`
  receives the typed intent as today.
- Private `trackFileOpened`/`trackFileOpenFailed` (307-327) take the `TypeSource`; every call
  passes `resolved.source`.
- `installApk` (536-540): pass `MimeTypeResolver.sourceOf(file.path, APK_TYPE)`. Its item can
  arrive from a view model after the permission grant, so the resolver's answer isn't at hand.
- KDoc at 338-344: the wildcard case now covers only files whose contents matched nothing.

### Step 5. Call sites: open and open-with run in a coroutine

In each screen, add `val scope = rememberCoroutineScope()` (HomeScreen has one at 101), and wrap the
existing call and its unchanged `when`:

```kotlin
scope.launch(start = CoroutineStart.UNDISPATCHED) {
    when (val result = IntentUtil.openFile(context, file, "folder")) { /* unchanged */ }
}
```

`UNDISPATCHED` runs the block synchronously until its first suspension. A named file never
suspends, so it opens inside the click exactly as today. Only an unknown file hops to IO.

| # | File | Lines |
|---|------|-------|
| 1 | `ui/screens/folder/FolderScreen.kt` | 463 (open), 558 (open with) |
| 2 | `ui/screens/search/SearchScreen.kt` | 262, 343 |
| 3 | `ui/screens/analyzercategory/AnalyzerCategoryScreen.kt` | 264, 291 |
| 4 | `ui/screens/home/HomeScreen.kt` | `openRecentFile` (528), `openFavorite` (551) and `openFileItem` (574) become `suspend`; their click callers at 293 and 314 are wrapped; open with at 376 and 448 |
| 5 | `ui/screens/iteminfo/ItemInfoScreen.kt` | 146 already runs inside a `LaunchedEffect` collector, so no change beyond compiling against the suspending function |

This keeps the existing pattern of calling `IntentUtil` from composables. Moving the open into the
view models would be a refactor this change doesn't need.

### Step 6. Viewer models use the resolved type

All four already build their item on `ioDispatcher`. Wrap that item with
`MimeTypeResolver.resolveNow(...)` and pass `MimeTypeResolver.sourceOf(filePath, item.mimeType)`
to `trackFileOpened`:

| # | File | Lines |
|---|------|-------|
| 1 | `ui/screens/textviewer/TextViewerViewModel.kt` | 79 (state item), 114 (event) |
| 2 | `ui/screens/imageviewer/ImageViewerViewModel.kt` | 76 (state item), 85-91 (fallback item + event) |
| 3 | `ui/screens/pdfviewer/PdfViewerViewModel.kt` | 151 (state item), 258-264 (fallback item + event) |
| 4 | `ui/screens/mediaviewer/MediaViewerViewModel.kt` | 189-195 (event). Optional: `fileType()` (199-203) tags playback errors in Crashlytics as audio for an extensionless video; it can use the resolved type too |

The items the delete paths build (`ImageViewerViewModel.kt:126`, `PdfViewerViewModel.kt:533`,
`TextViewerViewModel.kt:137`) don't need a type; leave them alone.

### Step 7 (optional, Open question 1). Single-file share from lists

`shareFiles` becomes `suspend` and resolves the type for a single file (83). Its call sites are
wrapped the same way: `FolderScreen.kt:232` (already in a collector), `:555`, `SearchScreen.kt:350`,
`HomeScreen.kt:394,461`, `AnalyzerCategoryScreen.kt:222,298`. The viewer shares
(`PdfViewerScreen.kt:221`, `ImageViewerScreen.kt:126`, `TextViewerScreen.kt:121`) already get the
right type from Step 6 and need only the wrapping. Skip this step and list shares of unknown files
stay `*/*`, as today.

### Step 8. Docs

- `AGENTS.md`, "Analytics & Crash Reporting": add one rule. "A `mime_type` detected from a file's
  contents is a constant from `MimeTypeSniffer.TYPES`, never text read from the file; contents are
  read only when the user acts on a file whose name gives no type, never while listing."
- `.claude/skills/analyze-analytics/SKILL.md:258` (P7): keep the rate's definition (the skill
  requires stable definitions) and add a breakdown: by `type_source` from the release that adds it,
  and `content` rows by `mime_type` (Open question 7).
- Optional, owner only: register `type_source` as a custom dimension in the Firebase console if it
  should show in the GA4 UI. BigQuery has every param without it.

---

## 5. Tests

### Unit tests (JVM, `app/src/test/`, JUnit 4 + mockk)

**`data/util/MimeTypeSnifferTest.kt` (new).** Helpers `hex("FF D8 FF")` and `ascii("…")`. Each row
asserts the exact return value:

| # | Fixture (hex unless quoted) | Expect |
|---|-----------------------------|--------|
| 1 | `FF D8 FF E0 00 10 "JFIF" 00 01` | `image/jpeg` |
| 2 | `FF D8 FF E1 00 18 "Exif" 00 00` and `FF D8 FF DB 00 43 00` | `image/jpeg` |
| 3 | `FF D8 00 00` | null |
| 4 | `89 50 4E 47 0D 0A 1A 0A 00 00 00 0D "IHDR"` | `image/png` |
| 5 | the PNG signature cut to 7 bytes | null |
| 6 | `"GIF89a" 01 00 01 00 80 00 00` and `"GIF87a" 01 00 01 00 80 00 00` | `image/gif` |
| 7 | `"GIF88a" 01 00` | null (its NUL also rules out text) |
| 8 | `"RIFF" 24 00 00 00 "WEBP" "VP8 "` | `image/webp` |
| 9 | `"RIFF" 24 08 00 00 "WAVE" "fmt "` / `"RIFF" 00 10 00 00 "AVI " "LIST"` | `audio/x-wav` / `video/x-msvideo` |
| 10 | `"RIFF" 04 00 00 00 "ABCD"` | null |
| 11 | `00 00 00 18 "ftyp" "isom" 00 00 02 00 "isom" "mp41"` | `video/mp4` |
| 12 | `00 00 00 1C "ftyp" "M4A " 00 00 00 00 "M4A " "mp42" "isom"` | `audio/mp4` |
| 13 | major `"qt  "` / `"3gp4"` / `"3g2a"` (24-byte boxes) | `video/quicktime` / `video/3gpp` / `video/3gpp2` |
| 14 | `00 00 00 18 "ftyp" "heic" 00 00 00 00 "mif1" "heic"` | `image/heic` |
| 15 | major `"mif1"`, compatible `"mif1" "heic"` | `image/heic` |
| 16 | major `"avif"`; and major `"mif1"` with compatible `"avif"` | `image/avif` |
| 17 | major `"mif1"`, compatible `"mif1"` only | `image/heif` |
| 18 | major `"crx "`, compatible `"crx " "isom"` (Canon CR3) | null, not `video/mp4` |
| 19 | box size `00 00 00 04` before `"ftyp"`; and `"ftyp"` at offset 0 | null |
| 20 | `1A 45 DF A3 9F 42 86 81 01 42 F7 81 01 42 F2 81 04 42 F3 81 08 42 82 84 "webm"` | `video/webm` |
| 21 | the same with `42 82 88 "matroska"`; and EBML magic with no DocType | `video/x-matroska` |
| 22 | `"OggS" 00 02` + 20 bytes (granule, serial, sequence, CRC) + `01 1E` + `01 "vorbis"` | `audio/ogg` |
| 23 | the same page with `"OpusHead"` / `80 "theora"` at 28 | `audio/ogg` / `video/ogg` |
| 24 | `"OggS" 01 …` (bad version) | null |
| 25 | `"fLaC" 00 00 00 22` | `audio/flac` |
| 26 | `"ID3" 04 00 00 00 00 00 0A` + 10 tag bytes containing `00` | `audio/mpeg` |
| 27 | the same tag followed by `"fLaC"` | `audio/flac` |
| 28 | `"ID3" 03 00 00 80 00 00 00` (size byte ≥ `80`) | null |
| 29 | `"%PDF-1.7" 0A "%" E2 E3 CF D3 0A` | `application/pdf` |
| 30 | `0D 0A "%PDF-1.4" 0A` | `application/pdf` |
| 31 | `"see %PDF-1.4 header" 0A` | `text/plain`, not PDF |
| 32 | 1,100 × `"a"` + `0A "%PDF-1.4"` | `text/plain` |
| 33 | `50 4B 03 04 14 00 00 00 08 00` + the rest of a 30-byte header + `"a.txt"` | `application/zip` |
| 34 | `50 4B 00` | null |
| 35 | `"hello world" 0A`; `"README" 0D 0A 09 "tabbed"`; `1B "[31mERROR" 1B "[0m" 0A` | `text/plain` |
| 36 | UTF-8 `"naïve café — 日本語 🙂"` | `text/plain` |
| 37 | `EF BB BF "hi"` | `text/plain` |
| 38 | `"he" 00 "lo"` | null |
| 39 | Latin-1 `"caf" E9 " au lait"` | null |
| 40 | 4,095 × `"a"` + `E6`: with `reachedEof = false`; with `reachedEof = true` | `text/plain`; null |
| 41 | 20 × `01 02 03 04` + `"abc"` | null |
| 42 | empty array | null |
| 43 | every fixture above | each non-null result ∈ `MimeTypeSniffer.TYPES` (analytics vocabulary guard) |

`zipType` cases in the same file:

| # | Entry names, `mimetype` content | Expect |
|---|---------------------------------|--------|
| 1 | `mimetype`, `META-INF/container.xml`, `OEBPS/content.opf`; `"application/epub+zip"` | `application/epub+zip` |
| 2 | each ODF `mimetype` value, including one with a trailing `0A` | that ODF type |
| 3 | `mimetype` = `"text/x-made-up"`, and a 200-character value | `application/zip` (never echoed) |
| 4 | `[Content_Types].xml`, `_rels/.rels`, `word/document.xml` / `xl/workbook.xml` / `ppt/presentation.xml` | DOCX / XLSX / PPTX type |
| 5 | `[Content_Types].xml` alone | `application/zip` |
| 6 | `AndroidManifest.xml`, `classes.dex`, `resources.arsc`, `META-INF/MANIFEST.MF` | `application/vnd.android.package-archive` |
| 7 | `AndroidManifest.xml`, `classes.jar`, `R.txt` (an AAR) | `application/zip` |
| 8 | `META-INF/MANIFEST.MF`, `com/example/Main.class` (a JAR); `a.txt`, `b/c.png` | `application/zip` |

**`data/util/MimeTypeResolverTest.kt` (new).** `TemporaryFolder` and `runTest`, following
`StartupFolderResolverTest`. Real files are written with the fixtures above:

| # | Case | Expect |
|---|------|--------|
| 1 | path `/nonexistent/photo.png`, name type `image/png`, sniff lambda that counts calls | (`image/png`, `EXTENSION`) and 0 calls: named files are never read |
| 2 | file `photo` with JPEG bytes, `*/*` | (`image/jpeg`, `CONTENT`) |
| 3 | file `README` with text, `*/*` | (`text/plain`, `CONTENT`) |
| 4 | file `blob`: 4 KB of bytes 0..255 repeated, `*/*` | (`*/*`, `NONE`) |
| 5 | empty file, `*/*` | (`*/*`, `NONE`) |
| 6 | missing path; a directory path | (`*/*`, `UNREAD`), and no `ErrorReporter.warning` |
| 7 | `data.bin` with PDF bytes, `application/octet-stream`; the same name with random bytes | (`application/pdf`, `CONTENT`); (`application/octet-stream`, `EXTENSION`) |
| 8 | ZIPs written with `java.util.zip.ZipOutputStream`: `book` (EPUB, `mimetype` STORED first), `letter` (DOCX-shaped), `app` (APK-shaped), `archive` (plain), `broken` (first 64 bytes of a real ZIP) | EPUB / DOCX / APK / `application/zip` / `application/zip` |
| 9 | 10 MB of `"a"` | `text/plain`, and `readAndSniff` reads at most `PREFIX_BYTES` (assert through a reader seam or the returned prefix length) |
| 10 | sniff = `{ delay(TIMEOUT_MS * 5); Match("image/png") }` | (`*/*`, `UNREAD`) and `testScheduler.currentTime == TIMEOUT_MS`: the bound is on the wait |
| 11 | the timeout case after `advanceUntilIdle()` | no `ErrorReporter.warning` (a timeout isn't a fault) |
| 12 | sniff throws `IllegalStateException` | (`*/*`, `UNREAD`); `warning` called once, with a scrubbed throwable |
| 13 | `sourceOf("/x/README", "text/plain")`, `sourceOf("/x/README", "*/*")`, `sourceOf("/x/a.txt", "text/plain")` | `CONTENT`, `NONE`, `EXTENSION`. The last relies on the JVM's own name table knowing `txt`, as the viewer tests already do for `jpg` |

**Updated unit tests:**

- `AnalyticsTrackerReviewHookTest.kt:43`: new `trackFileOpenFailed` signature.
- `ImageViewerViewModelTest.kt:66`, `PdfViewerTestRule.kt:53`, `PdfViewerViewModelTest.kt:70,91`,
  `PdfViewerViewModelErrorReportingTest.kt:127`, `MediaViewerTestRule.kt:42`,
  `MediaViewerViewModelTest.kt:63,105`, `TextViewerViewModelDeleteTest.kt:73`: add the fourth
  matcher. Named-file verifications assert `TypeSource.EXTENSION`.
- New case in `ImageViewerViewModelTest`: a temp file `photo` with PNG bytes, via
  `createViewModel(filePath = …)` (292). `onImageLoaded()` → verify
  `trackFileOpened("unknown", "image/png", testSource, TypeSource.CONTENT)`, and
  `state.file?.mimeType == "image/png"`.
- `HomeScreenReviewPromptFailureTest.kt:50,62`: `openRecentFile`/`openFavorite` now suspend → wrap
  in `runTest`.
- Step 7 only: `IntentUtilReviewPromptFailureTest.kt:99-121` → `runTest`.
- `MimeTypeUtilGetMimeTypeTest` stays as it is: `getMimeType` still answers by name.

### Instrumentation tests (`app/src/androidTest/`, run on `Pixel_7_API_36`, no skip conditions)

None of the new cases depends on which apps the emulator has installed. Either they refuse every
launch, so the in-app fallback is what's asserted, or they read the chooser's inner intent, which
carries the type whatever the device holds.

- **`util/IntentUtilOpenFileTest.kt`.** Its `RecordingContext` (62-76) also records `intent.type`.
  The `openFile` helper (78-89) becomes
  `runOnMainSync { result = runBlocking { IntentUtil.openFile(…) } }`. A named file doesn't suspend.
  An unknown one resumes on `runBlocking`'s loop on the main thread, so Toast still has a Looper.
  New cases use extensionless files in `cacheDir`, the item's `mimeType` `*/*`, and
  `cancelledTypes = setOf(<sniffed type>, null)` (typed launch and untyped fallback both refused):
  - `photo`, a real JPEG written with `Bitmap.compress` → `RequiresImageViewer` whose item has
    `image/jpeg`; no recorded launch has type `*/*`.
  - `report`, from `DocumentFixtures.createPdf(name = "report")` → `RequiresPdfViewer`.
  - `clip`, from `DocumentFixtures.copyAsset(…, "sample_video.mp4", dir, "clip")` →
    `RequiresMediaViewer`.
  - `notes`, written with `"hello world\n"` → `RequiresTextViewer`.
  
  With today's code every one of these launches `*/*`, which the context doesn't refuse, so it
  returns `Handled` and the test fails. The tests measure the fix.
- **`util/IntentUtilOpenFileWithTest.kt`.** Same `runBlocking` change at 79-87, and its
  `RecordingContext` (69-76, today it keeps only actions) keeps the launched intents too. New case:
  an extensionless JPEG with `*/*` → the recorded chooser's `EXTRA_INTENT` has type `image/jpeg`.
- **`integration/FileOpenRoutingTest.kt`** (real `FolderScreen`):
  - `tapExtensionlessZip_showsUncompressDialog`: `FileFixtures.createZip(testDir, "archive", …)`,
    then wait for `R.string.action_uncompress`, as at 126-136.
  - `tapExtensionlessJpeg_neverOpensAsAnyType`, and the same for `report` (PDF), `clip` (MP4) and
    `notes` (text): tap, then `composeTestRule.waitUntil { Intents.getIntents().any { it.data == uri } }`
    (the open now finishes after an IO hop). Assert that every recorded `ACTION_VIEW` for that URI
    has a type in {sniffed type, null}. Null is the untyped fallback when nothing handles the typed
    intent. Device-independent, and red today, since today's type is `*/*`.
- **Adapt for the suspending signatures:** `util/IntentUtilReviewPromptTest.kt:170-199` (passes
  `IntentUtil::openFile` and `::openFileWith` to a helper, which becomes a suspend function type);
  `util/IntentUtilShareTest.kt:52` (Step 7 only).
- **Re-run unchanged:** `ItemInfoScreenEventsTest`, `ItemInfoScreenTest`, `SearchScreenTest`,
  `AnalyzerCategoryScreenTest`, `ActivityNavigationTest`, `HomeSearchLaunchTest`, and the viewer
  screen tests (share buttons). They open named files, which take the synchronous path, but they
  exercise the rewritten call sites.

No test reads `MimeTypeSniffer` or `MimeTypeResolver` inside an `assume`
(`scripts/check_tests.py:777-810`), and no new instrumentation test is pure-JVM
(`check_instrumentation_tests_need_a_device`): each renders a screen or uses `Intent`/`Bitmap`.

---

## 6. Verification

1. Narrow first, then the AGENTS.md trio:
   ```bash
   ./gradlew -w --console=plain -I gradle/agent-quiet.init.gradle testDebugUnitTest --tests '*MimeType*'
   ./gradlew -w --console=plain -I gradle/agent-quiet.init.gradle testDebugUnitTest
   ./gradlew -w --console=plain lintDebug
   ./scripts/check-tests.sh
   ```
2. Instrumentation, emulator already running, handed to the `instrumentation-runner` subagent one
   class at a time. A comma list of methods runs only the first; cold-boot between groups if memory
   is tight: `IntentUtilOpenFileTest`, `IntentUtilOpenFileWithTest`, `FileOpenRoutingTest`,
   `IntentUtilReviewPromptTest`, then the re-run list in §5.
3. Manual, on `Pixel_7_API_36`, debug build. Debug builds log every event to logcat
   (`AnalyticsTracker.kt:58-61`), so `type_source` can be checked there:
   ```bash
   adb shell mkdir -p /sdcard/Download/sniff
   adb push app/src/androidTest/assets/sample_video.mp4 /sdcard/Download/sniff/clip
   adb push app/src/androidTest/assets/sample_audio.mp3 /sdcard/Download/sniff/song
   adb push app/src/androidTest/assets/pdf_links.pdf    /sdcard/Download/sniff/report
   adb push app/src/androidTest/assets/small_anim.gif   /sdcard/Download/sniff/anim
   adb push app/src/androidTest/assets/pdf_links.pdf    /sdcard/Download/sniff/report.bin
   adb push <any .jpg> /sdcard/Download/sniff/photo
   adb push <any .zip> /sdcard/Download/sniff/archive
   adb push <any .docx> /sdcard/Download/sniff/letter
   adb push <any .epub> /sdcard/Download/sniff/book
   adb push <any .heic> /sdcard/Download/sniff/phone
   adb shell 'printf "hello\nworld\n" > /sdcard/Download/sniff/README'
   adb shell 'head -c 4096 /dev/urandom > /sdcard/Download/sniff/random'
   adb shell 'touch /sdcard/Download/sniff/empty'
   adb shell 'printf "not an image" > /sdcard/Download/sniff/fake.jpg'
   adb logcat -s Analytics
   ```
   Tap each one from the folder, then again from search, recents, a favorite, item info and the
   analyzer's "Other" category. Expect:
   - The picker offers only fitting apps, or opens directly.
   - `archive` opens the extract dialog.
   - `random` and `empty` behave as today, with `type_source=none`.
   - `fake.jpg` is still treated as a JPEG: the name wins, and nothing is read.
   - `report.bin` opens as a PDF.
   - "Open with" lists fitting apps plus any-file apps.
   - No visible delay on any tap.
   - Each `file_opened` line shows the expected `mime_type` and `type_source`.
4. If a real device with an SD card or USB drive is at hand, repeat a few taps there. That's the
   slow-volume case the timeout exists for.
5. After release, using the BigQuery export (`analyze-analytics`):
   - **Before shipping**, run P7's existing split on the current export: of the `*/*` opens, how
     many have `extension` `unknown`, and which allowlisted extensions (`dat`, `bin`, `tmp`, `part`
     and so on) make up the rest, by `source`. That sets the expected ceiling.
   - **After**: P7 for the new `app_version` against 2.8. The new version's `file_opened` split by
     `type_source`. `content` rows by `mime_type`. Expect `none` to remain: unknown formats and
     binaries still go out as `*/*`. `unread` should be close to 0; if it isn't, the timeout or the
     read rules need a look.
   - Read `file_open_failed` with `type_source` `content` by `reason` alongside. A sniffed type
     that no app on a device accepts now ends in the in-app viewer or "no app" instead of a
     useless picker. That's honest, but it raises this rate a little for those types.
   - Success: the `*/*` share falls by about the `content` share, with no matching rise in
     `content` failures.

---

## 7. Risks and edge cases

| # | Risk | Handling |
|---|------|----------|
| 1 | Main-thread I/O | Reads happen only on `Dispatchers.IO` inside `resolve`. Named files do no I/O at all. `UNDISPATCHED` keeps their open synchronous |
| 2 | A stalled volume (SD card, USB drive spinning up) | 300 ms timeout, then today's `*/*` (`unread`). The blocked read holds one IO thread until the kernel returns; it holds only a `File` |
| 3 | Double tap while an unknown file is being read | Typical reads take a few ms, so two opens are unlikely; worst case is two pickers, as a fast double tap can already cause. Add a guard only if testing shows it |
| 4 | The screen closes mid-read | Its coroutine scope is cancelled and nothing launches |
| 5 | Wrong guess narrows the picker | Only strong signatures are used, and the text test is strict. Apps that register for `*/*` still appear in "Open with" |
| 6 | Text misses | Latin-1/Windows-1252 and UTF-16 text stay `*/*`, which is today's behaviour |
| 7 | ZIP family | APK goes to the install flow, which the system confirms (Open question 2). A DOCX whose directory can't be read falls to the extract dialog. Opening the directory of a huge archive costs memory in proportion to its entry count; the extract dialog already pays that on every `.zip` tap |
| 8 | Untrusted input | Fixed-offset reads with bounds checks; `java.util.zip` for the directory; every exception caught. Since targetSdk 34 `ZipFile` throws for `..` entry names, which is caught (→ `application/zip`), and extraction keeps its own per-entry path check (`FileRepository.kt:1508-1511`, `ZipSlipException`) |
| 9 | Other apps asking the provider for the type still get `application/octet-stream` (finding 8) | Most viewers use the intent's type. A follow-up could subclass `FileProvider` and override `getType` to sniff (it runs on binder threads, off main). Not in this change |
| 10 | The metric moves without users being better off | Read `file_open_failed` (`content`) next to P7, as in §6.5 |
| 11 | `mime_type` gets new values | All of them come from `MimeTypeSniffer.TYPES` and are guarded by test row 43. `extension` is unchanged |
| 12 | The extension always wins | A misnamed file (`photo.png` that is a JPEG) behaves as today. This is deliberate: no read on the common path, no surprises for named files |
| 13 | Inconsistency left in place | An extensionless JPEG opens as an image but keeps a generic icon and no thumbnail. The bottom sheet's "Extract" item still keys off the name, so it's absent for an extensionless ZIP even though a tap opens the dialog. List shares stay `*/*` unless Step 7 is taken |
| 14 | Named-file behaviour changes | None intended. Existing equality assertions such as `IntentUtilOpenFileTest.kt:180` hold because `withMimeType` returns the same item when the type is unchanged |
| 15 | Rating prompt | Unchanged. Success and failure are still reported once per action, and a sniffed open that ends in `no_handler` blocks the ask through `trackFileOpenFailed`, as AGENTS.md requires |

Unrelated observations, not to fix in this change: `StoredEntryDestinationResolver.resolve` stats
the file on the main thread (`util/StoredEntryDestination.kt:48-50`). `FileProvider.getUriForFile`
canonicalises the path on the main thread for every open and share (`IntentUtil.kt:482-488`).
Multi-file share is always `*/*`, even for a selection of JPEGs (`IntentUtil.kt:116`).

---

## 8. Open questions for the owner

1. **Share in this change?** Recommendation: no. Ship open, open-with and the viewers first (the
   viewers' share improves as a side effect), and do list shares (Step 7, ~10 more call sites)
   later.
2. **An extensionless APK:** install flow, as for `.apk` (recommended; today it at least offers the
   installer in the picker), or treat it as a plain ZIP and open the extract dialog?
3. **"Open with":** use the sniffed type (recommended; any-file apps still appear), or keep "any
   type" there as a deliberate escape hatch? Keeping it leaves part of P7 unchanged.
4. **`application/octet-stream` names** (such as `.bin`): sniff them too (recommended), or only
   true `*/*`?
5. **Timeout:** 300 ms OK, or shorter or longer?
6. **Release:** into `release/2.8.0` (in progress), or the next version?
7. **Docs:** OK to add the `type_source` breakdown to the skill's P7 and the one rule to
   `AGENTS.md`?
8. **Text:** strict UTF-8 only (recommended), or also accept old 8-bit text (T2)?
9. **Later, not now:** do you want extensionless images to get real icons and thumbnails? That
   needs listing-time reads with a cache, as its own change.
