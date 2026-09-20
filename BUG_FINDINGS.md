## Critical


## Low

### [c/api-or-library-misuse/file-opening/handler-query-on-main-thread] "Open with" runs a full PackageManager handler query on the main thread purely to pick an analytics row

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/util/IntentUtil.kt:222`
    - `app/src/main/java/com/mauriciotogneri/fileexplorer/util/IntentUtil.kt:335` (
      `handlerAvailability`)
    - Call sites: `HomeScreen.kt:391`, `HomeScreen.kt:463`, `SearchScreen.kt:335`,
      `FolderScreen.kt:512`,
      `AnalyzerCategoryScreen.kt:277`
- **Severity:** Low
- **Confidence:** High
- **Likelihood:** High. The block is unconditional on the "Open with" path, so it runs on 100% of
  such
  taps; whether the delay is perceptible depends on how many `ACTION_VIEW` handlers the device has
  installed.
- **Defect:** Every "Open with" tap now performs a synchronous
  `PackageManager.queryIntentActivities`
  plus a `checkSelfPermission` per returned candidate, across Binder, on the main thread — before
  the
  chooser is launched. The answer is used only to choose between
  `trackFileOpenFailed(..., "no_handler")`,
  `trackFileOpenFailed(..., "query_failed")` and no event at all; the launch that follows ignores it
  entirely. The user waits on an IPC round trip that buys them nothing, on a path the project's own
  rules require to stay off the main thread.
- **Trigger:** Tap "Open with" on any file from Home (recents or favorites), Search, a folder
  listing, or
  the analyzer category listing. All five call sites are Compose click callbacks, so they run on the
  main thread. Worst case is a file with an unrecognized extension, where `MimeTypeUtil.getMimeType`
  yields `*/*` and the query enumerates every `ACTION_VIEW` handler installed on the device.
- **Evidence / verification:** The new block at `IntentUtil.kt:222-230`, whose result feeds only
  telemetry:

  ```kotlin
  when (handlerAvailability(context, intent)) {
      HandlerAvailability.NONE    -> trackFileOpenFailed(file, mimeType, source, "no_handler")
      HandlerAvailability.UNKNOWN -> trackFileOpenFailed(file, mimeType, source, "query_failed")
      HandlerAvailability.AVAILABLE -> Unit
  }

  val opened = try { context.startActivity(Intent.createChooser(intent, null)); true } ...
  ```

  and `handlerAvailability` at `:335-343`, which iterates the full result list with a permission
  check
  per row. The method's own comment concedes the point: *"the launch is left alone"*.

  Introduced: confirmed against the baseline. `git show 9e87306:.../IntentUtil.kt` shows
  `openFileWith`
  going straight from building the intent to `startActivity(Intent.createChooser(intent, null))`
  with no
  PackageManager call at all. `hasLaunchableHandler` existed in the baseline but was reached only
  from
  the `SecurityException` recovery in `startActivityOrChooser`, not on the normal open path.

  Refutation attempt: `openFile` already performs one `resolveActivity` IPC on the same thread, so
  main-thread PackageManager work is not a new *class* of cost here — which is why this is Low and
  not
  Medium. It does not refute the finding: `resolveActivity` returns a single component, whereas
  `queryIntentActivities` returns the full list and this code then makes a `checkSelfPermission`
  call
  per entry, and unlike `resolveActivity` the result changes nothing the user sees.

  Remaining assumption: the absolute cost is device- and install-set-dependent and was not measured
  on
  hardware; the finding rests on the work being unnecessary and on the main thread, not on a
  measured
  frame drop.
- **Suggested fix:** Take the availability query off the launch path. Emit the analytics row from a
  coroutine on `Dispatchers.IO` after `startActivity` has returned, so the chooser opens without
  waiting
  on the package manager, and the `no_handler` / `query_failed` dimension still reaches the
  dashboard.

### [a/logic-errors/test-guards/helper-detection-reads-unblanked-source] Matcher-helper detection scans raw source, so a comment can fail the structural gate on correct code

- **Location:** `scripts/check_tests.py:351`
    - `scripts/check_tests.py:339` (`blanked = blank(text)`, computed two lines above and used
      everywhere
      else in the block)
- **Severity:** Low
- **Confidence:** High
- **Likelihood:** Low. Three things must coincide in one file: a comment or KDoc writing
  `someMatcher(identifier)`, a function in that same file taking a `String` parameter of that name,
  and
  a call to it with a literal equal to a translated resource value.
- **Defect:** Inside the new matcher-helper detection block, the promotion test reads `text` — the
  raw
  source, comments included — while the surrounding code reads `blanked`, the copy with comments and
  string bodies neutralised. A comment that merely *mentions* a Compose matcher is therefore enough
  to
  classify an unrelated same-file function as a matcher helper. Every literal that function is
  called
  with is then checked against translatable string values, and a match makes `check-tests.sh` exit
  1.
  The gate fails on code that is correct, blocking a change rather than letting a defect through.
- **Trigger:** A test file containing a comment such as
  `// Prefer waitForText(label) over onNodeWithText(label) so the wait is bounded.`, a function
  `private fun makeFolder(label: String)`, and a call `makeFolder("Analyzer")`. `"Analyzer"` is a
  translatable value as of this release (`drawer_analyzer`), and this release also emptied
  `FILESYSTEM_NAME_PREFIXES`, which widens the set of literals that can match.
- **Evidence / verification:** The line, against its neighbours:

  ```python
  blanked = blank(text)                                                  # :339
  for m in re.finditer(r"fun\s+([A-Za-z0-9_]+)\s*\(([^)]*)\)", blanked): # :341  blanked
      ...
              if re.search(rf"\b{sm}\s*\(\s*{re.escape(p_name)}\s*\)", text):  # :351  raw
                  helpers.add(fn_name)
  ...
      for cm in helper_pattern.finditer(blanked):                        # :356  blanked
  ```

  The one place in this file where raw source *is* intended carries an explicit note
  (`:606` — *"Read from the raw source rather than the blanked copy, because it is a comment."*).
  Line
  351 has no such note, which is what marks it as an oversight rather than a choice.

  Reproduced in a disposable copy of the worktree, with the counterfactual:

  | Tree | `python3 scripts/check_tests.py` |
    |---|---|
  | untouched | exit 0 |
  | + probe function, call, **and** the comment | **exit 1** — `EdgeCasesTest.kt:933: makeFolderProbe("Analyzer"` |
  | + probe function and call, comment removed | exit 0 |
  | + all three, with `text` → `blanked` on `:351` | exit 0 |

  The middle two rows isolate the comment as the sole cause; the last confirms the fix. The probe
  function was `private fun makeFolderProbe(label: String) = label`, which returns its argument and
  is
  not a matcher helper by any reading.

  Introduced: the whole block is new on this branch —
  `git diff main...HEAD -- scripts/check_tests.py` shows `+ blanked = blank(text)` and
  `+ helpers = set()` through `+ helper_pattern = ...` as added lines.

  Refutation attempt: whether reading raw text is needed to catch helpers declared in *another*
  file —
  it is not, `text` is the current file's source and the search is same-file only, so raw text buys
  no
  extra reach. Also checked whether the `strings=True` blanking variant would change the outcome:
  the
  promotion search matches a bare identifier argument and needs no literal, so it does not.
- **Suggested fix:** Read `blanked` rather than `text` on line 351, matching every other scan in the
  function. One-word change; the reproduction above confirms it keeps the guard working while
  removing
  the false failure.