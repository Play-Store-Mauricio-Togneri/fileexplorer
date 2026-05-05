# File Explorer

## TODO

* Main
    * Content
        * Top
            * Breadcrumbs
            * Contextual menu
                * Select all
                * Sort by (same as Google Files)
                * New folder
        * Body
            * File list
                * Is file
                    * Thumbnail/Icon
                    * Name
                    * Size
                * Is folder
                    * Icon
                    * Name
                    * Number of items (e.g. 5 items)
                * Long press: select
                * Swipe left: Delete
                * Swipe right: Share
            * Floating button
                * Share
                * Open with
                * Move to
                * Copy to
                * Rename
                * Delete
* Menu
    * Use red badges in hamburger menu call the attention of users and make them click to discover
      the app
    * Settings
        * Home screen
        * Theme color
        * Swipe configuration (left/right)
        * Show hidden items
        * Dark/ligth theme
    * About
        * Version
        * Other apps
        * Privacy policy
        * Terms and conditions

Images (via ExifInterface - no extra dependencies)

- Dimensions (width × height pixels)
- Camera (make, model)
- Date taken
- GPS coordinates (requires ACCESS_MEDIA_LOCATION         
  permission on Android 10+)
- ISO, aperture, focal length, exposure time
- Orientation/rotation

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
- Author/title would require a third-party library

---

Things to avoid:

* Unreliable Transfers: File moves that fail halfway or don't provide clear error messages when a
  process stops.
* Slow Search: Taking several seconds to find a file in a large directory instead of providing
  instant results.
* Thumbnail Lag: Waiting for image or video thumbnails to generate while scrolling through large
  galleries.