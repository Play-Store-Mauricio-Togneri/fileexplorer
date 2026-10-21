## Low

### [a/error-handling/review-prompt/user-visible-failures-do-not-block-ask] Several failures the user sees do not block the rating ask

**Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/home/HomeScreen.kt:535`
Related:
- `HomeScreen.kt:556` and `HomeViewModel.kt:634`, `:787`: a stale Recent or Favorite shows "file not found".
- `FolderViewModel.kt:1316-1326` with `FolderScreen.kt:240`: a folder listing failure. The calm check also ignores `state.error`.
- `PdfViewerViewModel.kt:344-353`: a PDF page fails to render.
- `PdfViewerScreen.kt:158-160`: a PDF link cannot be opened.
- `IntentUtil.kt:550-561`, `:582-638`: the "install unknown apps", "all files access" or app-details settings screen is unavailable.
- `ItemInfoScreen.kt:740-751`: the map cannot be opened.
- `ui/screens/analyzer/AnalyzerScreen.kt:81-84`: the analyzer scan fails.
- `AnalyticsTracker.kt:792`, `:833`: feedback submit fails, or an "other apps" entry cannot be opened.
- `AboutActivity.kt:235`, `:245`: the store page or app share fails.

**Severity:** Low
**Confidence:** High
**Likelihood:** Medium. These are error paths of secondary features plus one common case, a recent file deleted since it was listed. Any one of them earlier in the process leaves a later threshold-crossing success free to show the ask.

**Defect:** Blocking the ask after a failure is opt-in: each failure path has to call `ReviewPrompt.onActionFailed()` or one of the hooked AnalyticsTracker events. The paths listed above show an error toast or error state but do neither.

So an eligible user can be asked to rate the app in the same process, sometimes moments after, the app failed in front of them. The project rule says otherwise. AGENTS.md "In-App Review": "A failure must reach `ReviewPrompt.onActionFailed()` … a failure path that reports none of them calls it directly". The gate's KDoc says "Any failure blocks asking for the rest of the process".

The change already adds direct calls on comparable paths: share errors, create-folder errors, the copy pre-checks and viewer delete errors. A stale Recent is a failed "open a file", which is a counted action. The text viewer's equivalent "file gone" case does block, through its read-error event.

**Trigger:**
- Example A: tap a Recent whose file was deleted ("file not found" toast). Later, open a file that crosses the 15-action threshold. The Play dialog appears on Home or Folder.
- Example B: a copy succeeds and arms the ask, and the follow-up reload of the folder fails. The ask fires over "Unable to load files".

**Evidence / verification:**
- Each listed branch was read. None calls `onActionFailed` or a hooked event.
- `AnalyticsTrackerReviewHookTest` only checks the events that are hooked. It cannot notice a failure event or toast that lacks the hook.
- **Refutation attempt:**
  - Home stays idle after a toast, and Folder's calm check does not include `error`, so nothing stops the later ask.
  - A refuter confirmed every sub-item independently.
- **Assumption:** whether a few of these, such as map-open or feedback submit, count as "failures" is a product call. The stale-file and listing-failure cases plainly do.

**Suggested fix:**
- Call `ReviewPrompt.onActionFailed()` in each listed branch, or hook the corresponding AnalyticsTracker failure events.
- Add `state.error == null` to Folder's calm condition.
- Consider a guard test that fails when a `*Error`/`*Failed` analytics event is not hooked.
