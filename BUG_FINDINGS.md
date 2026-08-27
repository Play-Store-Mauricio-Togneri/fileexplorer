### [a/state-and-lifecycle/folder-navigation/breadcrumb-depth-exceeds-back-stack] Ancestor breadcrumb on a startup folder empties the nav back stack and freezes the screen

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/FolderActivity.kt:166`
  (related: `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/MainActivity.kt:95`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/util/StartupDestinationResolver.kt:60`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/components/BreadcrumbPathParser.kt:30`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderScreen.kt:114`)
- **Severity:** Medium
- **Confidence:** Medium
- **Defect:** `onNavigateToFolder` assumes the breadcrumb trail and the `NavController` back stack
  have the same depth — it computes `levelsBack = currentSegments.size - targetSegments.size` and
  pops that many times. `MainActivity.openStartupFolder()` is the first caller to pass a `rootPath`
  that is an **ancestor** of `path`, so the trail is trimmed to the storage root and renders
  clickable ancestors that were never pushed. Tapping one pops more entries than exist. The tap is a
  visual no-op, but the back stack is silently emptied, which drops the surviving entry's
  `maxLifecycle` to `CREATED`; `FolderScreen` collects its state with `collectAsStateWithLifecycle`,
  so collection stops and the folder freezes at its last-rendered content — later deletes, renames,
  sort changes and refreshes never appear.
- **Trigger:** Settings → Startup screen → "Specific folder" → pick any folder **below** a storage
  root (e.g. `/storage/emulated/0/Download`). Cold start. Tap the "Internal storage" breadcrumb.
- **Evidence / verification:** `BreadcrumbPathParser.parsePath("/storage/emulated/0/Download", …,
  rootPath="/storage/emulated/0")` yields 2 items; `Breadcrumbs.kt:119` sets
  `clickable(enabled = !isLast)`, so index 0 is tappable. `FolderNavHost`
  (`FolderActivity.kt:113-185`) declares one `composable`, so the stack holds one folder entry;
  `levelsBack = 4 - 3 = 1`. A separate refutation pass read the resolved
  `androidx.navigation 2.9.8` sources: `NavControllerImpl.popBackStack()` returns
  `popped && dispatchOnDestinationChanged()`, and `dispatchOnDestinationChanged()` returns
  `lastBackStackEntry != null` — **false only after the pop has already happened** — so the guard
  `if (!navController.popBackStack()) return@…` fires too late.
  That refutation **disproved a stronger initial claim** (a permanently blank screen):
  `NavigatorState.kt:130` registers the entry in `_transitionsInProgress` before `pop()`, so
  `populateVisibleEntries()` keeps it and `NavHost` still renders. The surviving symptom is the
  freeze, not a blank screen. Attribution: all seven baseline `createIntent` call sites
  (`HomeScreen.kt:326,334,371,455,555`, `SearchScreen.kt:259,339`) pass `rootPath == path`, and the
  pop logic is byte-identical at baseline — the invariant only breaks in the new configuration.
  `StartupScreenTest.kt:120-137` pins that configuration as intended but never taps the breadcrumb;
  `NavigationIntegrationTest` and `BreadcrumbsIntegrationTest` use a fake `onNavigateToFolder` and
  never exercise the real `NavHost`. **Remaining assumption:** the library trace and the freeze are
  established by reading sources, not by running on a device — no emulator was available.
- **Suggested fix:** stop deriving the pop count from path depth. Either resolve the target against
  the actual back-stack entries and navigate forward when no entry matches, or clamp `levelsBack` to
  the current entry count minus one so the launch destination can never be popped.
