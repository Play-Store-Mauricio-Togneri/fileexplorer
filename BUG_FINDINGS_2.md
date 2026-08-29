## Medium

### `[b/contract-mismatches/startup-routing/permission-grant-skips-configured-folder]`

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/MainActivity.kt:119`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/navigation/NavGraph.kt:67`
- **Confidence:** High
- **Trigger:** Configure a startup folder, revoke storage permission, cold-start, then grant
  permission on the permission screen.
- **Impact:** The app opens Home instead of the configured startup folder.
- **Cause:** `startupFolderPath()` maps missing permission to `null`, the same value used for “start
  at Home.” The grant callback only navigates to Home; it never retries startup resolution.
- **Baseline:** Startup-folder routing was added in the audited range.
- **Suggested fix:** Retain a pending startup request while permission is absent and resolve it
  after the grant, with the existing recreation/one-shot guards.

### `[c/dead-or-unreachable-behavior/settings-home-order/no-accessible-reorder-path]`

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/SettingsActivity.kt:1475`
- **Confidence:** High
- **Trigger:** Use TalkBack, Switch Access, a keyboard, or another semantics-driven input method to
  reorder Home sections.
- **Impact:** Section labels are reachable, but their ordering cannot be changed.
- **Cause:** The sole mutation path is raw `pointerInput` / `detectDragGestures`; the handle has no
  description, semantic action, or keyboard operation. `HomeSectionsOrderDialogTest.kt:31`
  explicitly records that no alternate actions exist.
- **Baseline:** The dialog was introduced in the audited range.
- **Suggested fix:** Add semantic Move up/Move down actions and equivalent keyboard controls that
  share the drag reorder operation.
