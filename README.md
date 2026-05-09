# File Explorer

* Menu
    * Use red badges in hamburger menu call the attention of users and make them click to discover
      the app
    * Storages
        * Storage 1
        * Storage 2
    ------------------
    * Locations
        * Downloads
        * Images
        * Videos
        * Audio
        * Documents
        * Screenshots?
        * DCIM?
    ------------------
    * Settings
        * Home screen
        * Theme color
        * Swipe configuration (left/right)
        * Dark/ligth theme
    * About
        * Version
        * Other apps
        * Privacy policy
        * Terms and conditions
    * Feedback

Are file names ellipised?

Implement analytics

Contextual menu

- Show hidden files

# Add sample files
/cle
Text & Office: .pdf, .docx, .xlsx, .pptx, .txt, and .csv.
E-books: .epub and sometimes .mobi or .azw3.
Installers: .apk (Android Package) and .apks or .xapk (bundled versions) (AndroWasm, 2026).
Obbb/Data: .obb (Opaque Binary Blob) used for large game assets in specific folders.
Archives: .zip, .rar, .7z, and .tar.gz.
Databases: .db, .db-journal, and .wal (commonly found in app-specific data folders) (Ji et al.,
2016).
Configuration: .xml, .json, and .properties.
Backups: .bak, .ab (Android Backup), or custom app-specific extension names (e.g., .crypt14 for
WhatsApp).
Web Files: .html, .css, and .js.
Fonts: .ttf and .otf (used by system-wide theme engines or design apps).
GPS Data: .gpx and .kml (used for hiking trails, fitness tracking, and Google Earth).

# Make file info a separate activity

Location: show copy button

Images (via ExifInterface - no extra dependencies)

- Dimensions (width × height pixels)
- Date taken
- ISO, aperture, focal length, exposure time
- Orientation/rotation
- Software?

Audio (via MediaMetadataRetriever - built-in)

- Duration (formatted as MM:SS)
- Artist
- Album
- Title
- Genre
- Year
- Bitrate (kbps)

Video (via MediaMetadataRetriever - built-in)

- Duration
- Resolution (width × height)
- Frame rate (FPS)
- Bitrate
- Rotation

PDF (via PdfRenderer - built-in)

- Page count (easy)

---

Things to avoid:

* Unreliable Transfers: File moves that fail halfway or don't provide clear error messages when a
  process stops.
* Slow Search: Taking several seconds to find a file in a large directory instead of providing
  instant results.
* Thumbnail Lag: Waiting for image or video thumbnails to generate while scrolling through large
  galleries.

---

# Check for

* Bugs
* Security vulnerabilities
* Different Android version problems
* Problems with screen size
* Problems in some devices
* Performance problems
* Accessibility problems 