# Bug Findings

## Low

### [b/resource-and-configuration-parity/release-tooling/capture-tool-not-deleted] The screenshot capture tool is still in the tree, but the docs say it was deleted

**Location:** `website/AGENTS.md:60`
Related locations:
- `app/src/androidTest/java/com/mauriciotogneri/fileexplorer/website/WebsiteScreenshots.kt:93`
- `scripts/website-screenshots.sh`
- `scripts/play-screenshots.sh`
- `scripts/play_screenshots.py`
- `scripts/play_screenshots.html`
- `scripts/play_upload.py`
- `app/src/androidTest/assets/website/`
- `scripts/instrumentation-tests.sh:42`

**Severity:** Low
**Confidence:** High
**Likelihood:** Medium. It triggers on the next full instrumentation run or squash merge made before the planned deletion. Both are steps a release normally goes through.

**Defect:** This is an incomplete change.
- `website/AGENTS.md` states that the capture tool "was then deleted". It also says `main` never sees the tool, and gives a recipe to restore it from the commit that deleted it.
- At HEAD all seven tool paths, plus the test assets, are still present, and no commit on any branch deletes them.
- As a result, the screenshot class runs as part of the regular instrumentation suite: 20 languages × 8 tests, so 160 extra cases that switch the app's language and write full-screen PNGs to the emulator.
- The documented restore recipe resolves to an empty commit, so its `git checkout "$deleted^"` fails.

**Trigger:** Any of these, on the branch as it stands:
- running `./scripts/instrumentation-tests.sh` (any chunk) or `connectedDebugAndroidTest`;
- following the restore recipe in `website/AGENTS.md`;
- squash-merging `release/2.8.0` into `main`.

**Evidence / verification:**
- `git cat-file -e HEAD:<path>` succeeds for every tool path, and every one is absent on `main`.
- `git log --all -1 --format=%h --diff-filter=D -- scripts/website-screenshots.sh` prints nothing.
- Nothing excludes the class:
  - `app/build.gradle.kts` has no instrumentation filter;
  - `scripts/instrumentation-tests.sh` passes only sharding arguments;
  - the class's `@SdkSuppress(minSdkVersion = 33)` does not skip on the API 36 emulator.
- The class's own KDoc confirms that it joins the suite.
- Refutation attempt: the deletion is plausibly meant as a final commit, as the KDoc's "meant to be deleted once the screenshots are done" suggests. But the documentation describes it as already done, and nothing in the tree enforces it.
- Remaining assumption: the extra suite time (the 1.5 s settle per case, plus the language switch) was estimated, not measured.

**Suggested fix:** Before merging, commit the planned deletion of the seven tool paths and `app/src/androidTest/assets/website/` on `release/2.8.0`. No other file references them. The documented recipe then finds the deleting commit, as long as the branch is kept.

### [a/repeated-and-interrupted-operations/review-prompt/ask-spent-when-cancelled-after-save] A cancelled rating ask can be saved as used without Play's flow ever launching

**Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/util/ReviewPromptGate.kt:107`
Related locations:
- `app/src/main/java/com/mauriciotogneri/fileexplorer/util/ReviewPromptGate.kt:132`
- `app/src/main/java/com/mauriciotogneri/fileexplorer/util/ReviewPromptGate.kt:120`
- `app/src/main/java/com/mauriciotogneri/fileexplorer/data/source/DataStoreReviewPromptSource.kt:26`
- `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/components/ReviewPromptEffect.kt:46`
- `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/components/ReviewPromptEffect.kt:49`

**Severity:** Low
**Confidence:** High
**Likelihood:** Low. The cancellation has to land in the few milliseconds between the ask's save starting and its result reaching the caller.

**Defect:**
- The ask is saved before Play's flow is launched. The save sets the ask count to 1 and resets the active-day and action counters.
- If the asking coroutine is cancelled after the save has started writing but before it returns, the gate treats the call as unsettled. It releases its claim and leaves the ask armed.
- The next attempt re-reads the stored record and finds nothing due, because the counters were just reset. It then clears the armed state.
- The result: one of the user's two lifetime asks is spent, Play's review flow never launches, and `review_prompt_requested` is never reported.
- This contradicts the gate's own contract: a call cancelled before it settles "leaves [pending] as it was, so the ask can still happen". It also contradicts the record's definition of the ask count as the number of times the flow was launched.
- Any retry then needs the 120-day gap plus 5 active days and 15 actions earned again.

**Trigger:**
1. An armed ask fires after the 1.5 s settle delay on the home or folder screen.
2. Play answers, and the save of the ask begins.
3. During the write, one of three things cancels the effect:
   - the user leaves the screen (it stops being resumed);
   - a sheet, dialog or menu opens (the screen stops being calm);
   - a failure clears the armed ask.

**Evidence / verification:**
- Traced against the pinned libraries' sources:
  - In DataStore 1.3.0 (`DataStoreImpl.handleUpdate`/`transformAndWrite`), only the transform runs under the caller's job. The disk write runs under DataStore's own actor job, so cancelling the caller does not stop it.
  - The caller's `ack.await()` and the surrounding `withContext(Dispatchers.IO)` both throw `CancellationException` on a cancelled caller, even after the write committed (kotlinx-coroutines 1.11.0 prompt-cancellation guarantee).
  - `editSafely` catches only `IOException`, so the cancellation reaches `ask()`.
- There, `settled` is still false, so the `finally` block releases the claim and does not clear the armed state.
- On the next call, `ReviewEligibility.dueAttempt` returns null for the reset counters.
- Each of the three cancellation triggers was refuted separately; none is prevented. Nothing on the path uses `NonCancellable`.
- The only cancellation test cancels while Play is still answering, using a fake store whose write never suspends. It cannot reach this window.
- An independent refutation pass confirmed the path.

**Suggested fix:** Save the ask and decide whether to launch as one unit that cancellation cannot split:
1. Run the save under `withContext(NonCancellable)`.
2. If the call has been cancelled by the time the save returns, undo the save, also under `NonCancellable`, before rethrowing. The undo should lower the ask count by one and restore the previous first-ask time.
3. Merge the counters rather than overwriting them with old values, for example by taking the larger of the stored and previous counts, so an action recorded meanwhile is not lost.

The save has a single caller, so no other path is affected. Keep "nothing is launched if the save fails" intact.

### [a/error-handling/review-prompt/visible-failures-do-not-block-ask] Three user-visible failures do not block the rating ask

**Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/iteminfo/ItemInfoViewModel.kt:255`
Related locations:
- `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/iteminfo/ItemInfoViewModel.kt:259`
- `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/picker/PickerViewModel.kt:121`
- `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/MainActivity.kt:172`
- `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/MainActivity.kt:68`

**Severity:** Low
**Confidence:** High
**Likelihood:** Low. It needs a user who is already eligible, an ask armed in the same process, and one of these failures before the ask fires.

**Defect:** The rating rule says any failure blocks asking for the rest of the process. A failure path that reports none of the AnalyticsTracker failure events must call the block itself. The branch applied this to many sibling paths, but these three show an error and do neither:
- **Item info load:** the file is missing, or reading it throws. The info screen shows its error state.
- **Destination picker storage load:** listing the storages throws. The picker shows "unable to load files".
- **Startup folder unavailable:** the configured folder cannot be opened, for example because the SD card was removed. A toast says so.

In each case a success-armed ask can still open Play's dialog on Home or Folder right after the user saw the failure.

**Trigger:**
- **Item info:** delete or open something (arms the ask), then open Info on a stale search result whose file is gone. Go back to the folder or home screen: the ask fires 1.5 s later.
- **Startup folder:** on a cold launch the startup folder is unavailable, so the toast shows. The launch's disarm only clears an ask that was already armed; any later success in that process re-arms it and the ask fires.
- **Picker:** the storage list throws while the picker is open over a folder with an armed ask. The ask fires once the picker is dismissed.

**Evidence / verification:**
- None of the three paths calls the block hook directly or through any caller. Their error reporting goes to Crashlytics only, which does not touch the rating prompt.
- The siblings the branch did hook, which show the rule was meant to cover these cases:
  - the folder listing failure;
  - the recent/favorite "not found" toasts on the home screen;
  - the picker's create-folder failure;
  - the info screen's own "open in maps" failure.
- Independent refutation attempts confirmed the item info and startup paths.
- The picker path is real but rarely reachable: its storage listing throws only if the platform's external-files-directory lookup throws, because the per-volume lookups catch their own errors.

**Suggested fix:** Call `ReviewPrompt.onActionFailed()` in four places:
- both item info error branches;
- the picker's storage-load catch;
- beside the startup-folder-unavailable toast, inside the existing "started" check.

The call is a no-op until the prompt is initialised, so unit tests are unaffected.

### [a/user-interface-state/review-prompt/delete-confirm-double-tap-counts-twice] A second tap on Delete in the search and home confirmation dialogs counts the delete twice toward the rating ask

**Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/search/SearchViewModel.kt:333`
Related locations:
- `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/search/SearchViewModel.kt:352`
- `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/home/HomeViewModel.kt:674`
- `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/home/HomeViewModel.kt:727`
- `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/home/HomeViewModel.kt:829`
- `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/home/HomeViewModel.kt:883`

**Severity:** Low
**Confidence:** Medium
**Likelihood:** Low. It needs a second tap while a slow delete, such as a large folder, is still running.

**Defect:**
- The search delete and the home recent/favorite deletes keep their confirmation dialog up until the delete finishes. The item to delete is cleared only after the delete returns.
- A second tap on Delete during that time starts a second delete of the same path. That second delete normally finds the path already gone, which counts as success, so it records a second success.
- One user action therefore adds two actions toward the 15 the rating ask requires, against the rule of once per user action.

**Trigger:**
1. Confirm deletion of a large folder from search results, or from the home screen's recent files or favorites.
2. Tap Delete again before the dialog closes.

**Evidence / verification:**
- The dialog's Delete button calls the confirm handler on every tap and does not dismiss itself.
- Each handler reads the item to delete, launches the delete, and clears it only after success or failure.
- The folder screen, the viewers and the analyzer category screen clear their dialog state before launching, and are not affected.
- Remaining uncertainty: whether the second, concurrent delete reports success or a failure depends on timing. If it reports a failure, the ask is blocked instead, so the extra count is not certain on every double tap.

**Suggested fix:** In these three handlers, clear the item to delete, or mark a delete as in flight, before launching the delete, as the folder screen does. Make further confirms no-ops until it finishes. This also stops the duplicate delete itself.

### [a/resource-management/screenshot-scripts/temp-dir-leaked-on-early-exit] The website screenshot script leaves an empty temp directory when it exits early

**Location:** `scripts/website-screenshots.sh:26`
Related locations:
- `scripts/website-screenshots.sh:39`
- `scripts/website-screenshots.sh:55`

**Severity:** Low
**Confidence:** High
**Likelihood:** Low. Only runs without exactly one connected device, or whose first `adb` call fails, are affected.

**Defect:**
- The output temp directory is created at the top of the script.
- The cleanup trap that removes it is installed only after the device check and the first `adb` call.
- So when the script stops at either of those, under `set -e`, the directory is never removed.

**Trigger:** Run `./scripts/website-screenshots.sh` with no emulator running, or with more than one device attached.

**Evidence / verification:**
- `mktemp -d` is at line 26, the "exactly one emulator" exit at line 39, the night-mode `adb` read at line 42, and `trap restore EXIT` at line 55.
- The directory is first used after the trap.
- Refutation attempt: no other cleanup removes it.

**Suggested fix:** Move the `mktemp -d` line to just before `trap restore EXIT`. Every use of the directory comes after that point, so nothing else changes.

## Summary

By severity: Critical (0 findings), High (0 findings), Medium (0 findings), Low (5 findings).

By confidence: High (4 findings), Medium (1 finding), Low (0 findings).

| Severity | High | Medium | Low |
|----------|------|--------|-----|
| Critical | 0    | 0      | 0   |
| High     | 0    | 0      | 0   |
| Medium   | 0    | 0      | 0   |
| Low      | 4    | 1      | 0   |

By Likelihood: High (0 findings), Medium (1 finding), Low (4 findings).
