# Changelog

## 2.7.0 — 2026-10-15

- Added a built-in PDF viewer for PDFs no other app can open, with zoom, go to page, text search,
  links and support for password-protected files.
- Added a built-in audio and video player for files no other app can play, with a seek bar, full
  screen, mute and album art.
- You can now share files straight from the storage analyzer's file lists.
- Moved Feedback from the side menu into the About screen.
- Improved file lists by dimming hidden files and folders so they stand out from your own files.
- Improved the file details screen to show locations like "Internal storage › Download" instead of a
  raw path.
- Simplified the storage analyzer chart to a single ring that shows how full the drive is.
- Reorganized the file action menus, with Delete last and shown in red.
- Improved swipe actions so only one row stays open at a time, and it closes when you scroll or open
  a menu.
- Fixed the storage permission button doing nothing on Android 10 and older after access had been
  denied; it now opens Settings.

## 2.6.0 — 2026-09-25

- Added a storage analyzer, opened from the side menu, that shows how much space images, videos,
  audio, documents, other files and the system take up on each storage.
- You can now open an analyzer category to see its files from largest to smallest, then delete them,
  open them, go to their folder or view their details.
- Improved USB drive support: they now show as "USB Drive" with their own icon instead of as an SD
  card.
- Improved the home screen so it updates by itself when you insert or remove an SD card or USB
  drive.
- Improved copying, moving and compressing so a file that can't be read is skipped and reported,
  instead of failing the whole operation.
- Improved delete errors to say why an item couldn't be deleted, such as read-only storage or a file
  in use.
- Improved home screen loading: location cards now appear right away with their last known sizes.
- Fixed favorites and recent files on an SD card or USB drive being lost for good when the card was
  removed.
- Fixed deleting several items stopping at the first one that failed.
- Fixed the app freezing while copying, compressing or extracting large files.
- Fixed the file list appearing to jump to the bottom after changing the sort order.
- Fixed retrying a password-protected archive reusing the wrong password you had just entered.

## 2.5.0 — 2026-08-29

- Improved sharing a very large selection: you're now asked to pick fewer files, and your selection
  is kept.
- Added a startup screen setting, so the app can open straight into a folder of your choice instead
  of the home screen.
- Added settings for what the second line of each file and folder shows: item count, size, date
  modified or nothing.
- Added customizable swipe actions: choose whether swiping left or right renames, deletes, moves,
  copies, shows info or does nothing.
- You can now reorder the home screen sections (Recent, Favorites, Locations, Storage) by dragging
  them.
- Raised the minimum Android version to 7.0.

## 2.4.0 — 2026-08-01

- Improved the Settings screen by grouping its options into sections.
- Improved thumbnail loading by keeping video, PDF, app, album art and e-book thumbnails between
  sessions.
- Improved home screen loading by no longer measuring folder sizes again on every visit.
- Improved copying, moving and extracting to tell you when storage runs out instead of showing a
  generic error.
- Improved compressing to tell you when storage is full instead of showing a generic error.

## 2.3.0 — 2026-07-03

- Added favorites: star files and folders from the file list, search results or recent files to keep
  them on the home screen, and clear them all from Settings.

## 2.2.0 — 2026-06-25

- Added search filters: find files, folders or both, include hidden items, and limit results to
  images, videos, audio, documents or other files.
- Added an "Open folder" action to search results.
- Added a built-in image viewer with pinch and double-tap zoom for images no other app can open.
- Added a built-in text viewer for text, code and data files no other app can open.

## 2.1.0 — 2026-06-06

- Improved storage names: "Internal Storage" and "SD Card" now appear in your language, and multiple
  SD cards are numbered correctly.
- Added a "Restricted" label for folders the app isn't allowed to read, such as Android/data,
  instead of showing them as empty.
- Improved how fast folders open by loading subfolder item counts in the background.

## 2.0.0 — 2026-04-30

- Rebuilt the app from scratch with a modern Material design and a light, dark or system theme.
- Added a home screen with your recent files, shortcuts to common folders.
- You can browse folders with a tappable folder path and sort them by name, size or date.
- You can select several items at once to copy, move, rename, delete or share them.
- You can follow long operations with a progress indicator and cancel them.
- You can compress files into ZIP archives and extract archives, including password-protected ones.
- You can install APK files.
- Added file search and a details screen with thumbnails and information.
- Added support for 20 languages, including Arabic and Urdu with right-to-left layout.
