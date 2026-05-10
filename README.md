# File Explorer

* Menu
    * Use red badges in hamburger menu call the attention of users and make them click to discover
      the app
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

* Use black from material files

* Check libraries and dependencies

* Implement analytics

* Implement crashlytics

* Contextual menu: Show hidden files

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

* Search for TODOs
* Bugs
* Security vulnerabilities
* Different Android version problems
* Problems with screen size
* Problems in some devices
* Performance problems
* Unused or dead code
* Accessibility problems
* Localize app in all major languages and existing used languages in the app
* Is there hardcoded text or it's all localized?
* Run project inspections (Problems tab)