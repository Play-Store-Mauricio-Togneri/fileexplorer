# Changelog

## v2.2.0 → v2.2.2

_Since commit `4e6dbeb` (2026-06-08), versionCode 220 → 222._

### New features

**Favorites** — Mark any file or folder as a favorite and reach it instantly.

- Dedicated **Favorites carousel** on the home screen (between Recents and Locations); auto-hides when empty. Cards show a thumbnail or type icon with the filename.
- Add/remove favorites from folder list rows, the folder overflow menu (for the current folder), the recent-files sheet, and search results. Favorited items show a star marker.
- Favorite actions sheet: **Open with**, **Share**, **Open folder**, **Remove from favorites**, **Delete**, **Info** (Open with/Share hidden for folders).
- **Clear favorites** option in Settings (with confirmation dialog).
- Favorites are unbounded, ordered most-recent-first, and persisted across launches; entries pointing at deleted/unmounted files are pruned automatically.

**Search filters** — A new filter bar under the search field with three controls:

- **Kind**: Files / Folders / Any. Folder results are now tappable and open directly.
- **Type** (multi-select): Images, Audio, Videos, Documents, Other — defaults to "All types"; disabled when scoped to Folders.
- **Hidden items**: show/hide, seeded from the global preference but applied per-search without overwriting the setting.
- Search results also support favoriting and an **Open folder** action.

### Improvements

- **Folder browsing reworked** to open in a standalone screen (own back-stack/breadcrumbs); open/close animations suppressed so navigation looks seamless and unchanged to the user.
- **Selection mode** in the swipeable list is cleaner: entering selection closes any open swipe row, disables the swipe gesture and inline rename/delete buttons, and makes a tap toggle selection.
- More precise **MediaStore indexing** after copy/move/delete (uses the exact created/removed paths).

### Bug fixes

- **Rename no longer silently overwrites** an existing file — including hidden dotfiles the collision dialog never showed — on Android 8+.
- **SQLite metadata preview no longer deletes user database files.** The previous read-only probe could trigger Android's default corruption handler, which deletes the `.db` plus its `-journal/-wal/-shm` files. (Data-loss fix.)
- **Accurate delete/move failure reporting:**
  - Delete progress no longer over-counts failures; a folder/symlink that can't be removed is reported as a distinct "structural" error with its own message instead of skewing the count.
  - A move where originals can't be deleted after a successful copy is now reported as failed ("Copied, but some originals could not be deleted") and won't tell MediaStore the source is gone.
- **Image viewer** loading spinner no longer stretches to fill the screen.
- **Folder list now reliably refreshes** when you return to it after navigating into and back out of subfolders.
- **Recent files** no longer suffer a lost-update race when items are added/removed quickly; stale (deleted) recents and favorites are pruned on load.
- **Corruption recovery**: preferences, locations cache, recent files, and favorites stores now reset cleanly instead of crashing if the on-disk data is corrupted; theme/sort fall back to defaults on read errors.
- **"All files access" settings** gracefully falls back to the app details page (and a toast) instead of crashing when the system settings screen is unavailable.
