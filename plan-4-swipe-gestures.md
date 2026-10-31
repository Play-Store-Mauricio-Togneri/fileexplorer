# Plan 4 — Swipe gestures: measure them properly, then decide

Source: `todo.md` row 4 (analytics reports of 2026-10-30 and 2026-10-31). Branch: `release/2.8.0`
(2.8.0 unreleased). Nothing in this plan has been implemented.

## Problem

Dragging a row in a folder sideways slides it over and shows one button behind it: Rename for a left
drag and Delete for a right drag, unless the user changed that in Settings. Analytics says only about
a third of those drags end with someone tapping the button (31.1% overall, 38.3% on 2.7). Of the
people who change the swipe setting, about two-thirds switch the gesture off. Read as it stands, swipes
mostly get in the way.

The numbers can't carry that conclusion yet:

- The "swiped" event fires again every time someone drags a row that is already open, so the count
  of drags is too high.
- The tap event says which action was tapped, but not which side it was on.
- Nothing records how a row that was opened and never used got closed: by a scroll, a tap on the
  row, a drag back, or something else.

**Phase 1** fixes the measurement and changes nothing the user can see. **Phase 2** happens only if
the corrected numbers still show swipes getting in the way. It would make an accidental swipe less
likely, or turn swipes off by default for new installs, and it can be done for one side only.

## Verified cause

Every claim in the todo row, checked against `release/2.8.0` and the release branches.

| # | Claim | Verdict | Evidence |
|---|-------|---------|----------|
| 1 | A swipe event fires every time a row settles open past the 40dp threshold, re-drags of an open row included (`SwipeableFileListItem.kt:227-246`) | **Confirmed**, no line drift. **It also covers more cases than the claim says** | Threshold `SwipeThreshold = 40.dp` at `ui/components/SwipeableFileListItem.kt:56`. The events fire inside `onDragEnd` at `:231` (`trackFolderSwipedRight`) and `:236` (`trackFolderSwipedLeft`), chosen only by the final offset (`:230`, `:235`). Nothing checks where the row was when the drag began. The offset is clamped to ±80dp (`:104-105`, `:255-256`), so the event fires again even when the row can't move: pushing an open row further in the direction it already opened, or any sideways jitter past the touch slop while touching an open row |
| 2 | "So the share of real accidental swipes is lower than this reads" | **Confirmed** in direction. **There is also a smaller bias the other way** | Re-drags inflate the number of drags. In the other direction, the tap event is sent before the row's closing animation (`:160`, `:175`). A second tap during that ~300ms starts a new animation on the same `Animatable`, which cancels the coroutine of the first tap. The result is two tap events and one action. A tap on a button that is still visible while a sub-threshold drag slides shut also counts as a tap with no swipe. Both effects are small. The cancellation behaviour comes from Compose and has not been measured on a device |
| 3 | The tap event records the action, not the side | **Confirmed** | `trackFolderSwipeActionTapped(action)` is at `data/util/AnalyticsTracker.kt:489-492` and is called at `SwipeableFileListItem.kt:160` and `:175` with `action` only. The two swipe events have no params at all (`AnalyticsTracker.kt:481-487`), so no event links a side to an action. The same action can sit on both sides: both settings dialogs list every option (`activities/SettingsActivity.kt:1086-1124`), and `SwipeableFileListItemTest.theSameAction_canBeConfiguredOnBothDirections` covers it |
| 4 | "Rename per left swipe" assumes the default mapping | **Confirmed** | When nothing is stored, left = RENAME and right = DELETE (`data/source/DataStorePreferencesSource.kt:213-215`, read at `:112-126`). A remapped user shows up only in `settings_swipe_left`/`settings_swipe_right`. The `swipe_left_action`/`swipe_right_action` user properties are set only when the setting changes (`ui/screens/settings/SettingsViewModel.kt:170`, `:179`), and the saved BigQuery query doesn't export user properties anyway |
| 5 | The swipe code is the same in 2.6, 2.7 and 2.8 | **Corrected** | The code that fires the events and the 40dp threshold haven't changed since 2.5.0 (the row's last edit before 2.7.1 was `e918102a`, 2026-08-20). The row itself did change in **2.7.1**: commit `7595dafb` (2026-10-04, on `release/2.7.0`, shipped with the 2.7.1 version bump `cc549cb7`) added 46 lines to it (`isRevealed`/`onRevealedChange`, so the screen can close it). `git diff release/2.6.0 819ad9af` (2.6.3 → 2.7.0) on the row file is empty, and so is `git diff cc549cb7 HEAD` (2.7.1 → 2.8.0). So: 2.6 = 2.7.0, then a change in 2.7.1, and 2.8 = 2.7.1 |
| 6 | 2.7.1 closes an open row on scroll (`FolderScreen.kt:160-176`) | **Confirmed**, now at `ui/screens/folder/FolderScreen.kt:161-176`. **2.7.1 changed more than scrolling** | The same commit added the "one open row" rule. When a scroll starts, the open row closes (`:171-176`). Opening the top-bar menu, the sort sheet, the actions sheet or any dialog closes it (`:165-170`). Another row's drag *start* also closes it (`:486-492`, with the row's `onDragStart` at `SwipeableFileListItem.kt:226`). Entering selection mode already closed it before 2.7.1 (`:125-129`) |
| 7 | …which affects both sides equally | **Confirmed** | Nothing in that code reads the side |
| 8 | Unknown: why only the left side fell | **Still not found in code** | Nothing that depends on the side changed between 2.6 and 2.7.x. The row is identical up to 2.7.0. `FileListItem`'s 2.7 changes only dim hidden items and set the name's text direction. `targetSdk` stayed at 37. A lead from the 2026-10-30 report (row 10): on 2.7, rename fell at every entry point (action bar 2,256 → 1,341, sheet 1,845 → 1,037, swipe 993 → 220). That points at who was on 2.7 (the counts are events, not users) more than at the swipe itself. Phase 1 puts side and action on every event, so this can be measured directly |
| 9 | Other screens with swipeable rows | **None exist** | `SwipeableFileListItem` is used only at `FolderScreen.kt:447`. Rows on the analyzer, search and recents screens don't swipe |

Current inventory (what Phase 1 starts from):

- **Events.** `folder_swiped_left` and `folder_swiped_right` (no params). `folder_swipe_action_tapped`
  {`action`}. `settings_swipe_left` and `settings_swipe_right` {`action`}, sent on every pick,
  including re-picking the current value (`SettingsViewModel.kt:163-183`).
  `settings_swipe_left_opened` and `settings_swipe_right_opened`. `swipe_left_dialog_cancelled` and
  `swipe_right_dialog_cancelled` (`AnalyticsTracker.kt:651-667`, `:1123-1129`).
- **Settings.** The `user_preferences` DataStore keys `swipe_left_action` and `swipe_right_action`
  hold enum names (`DataStorePreferencesSource.kt:200-201`). `SwipeAction.NONE` is the off switch
  (`data/model/SwipeAction.kt:6-7`). The settings rows have no badge of their own
  (`PreferencesRepository.kt:186-187`). `allowBackup="false"` (`AndroidManifest.xml:43`), so a
  reinstall never restores old preferences.
- **Tests.** `androidTest/.../ui/components/SwipeableFileListItemTest.kt` (22 tests; gestures,
  threshold, RTL, the reveal callbacks). `androidTest/.../ui/screens/folder/FolderScreenTest.kt:400-445`
  (another row closes the open one; the overflow menu closes it; no scroll test).
  `test/.../FolderViewModelTest.kt:398-428`, `test/.../SettingsViewModelTest.kt:419-475` and
  `test/.../DataStorePreferencesSourceTest.kt:166-190` (settings plumbing). No test covers any swipe
  analytics event.

## Approach

### Phase 1 — fix the measurement (ships first, no visible change)

**What counts as one opening.** An opening is a row settling open on a side it was not already
settled open on. Every opening ends in exactly one of two ways: a tap on its button, or a close
event with a reason.

| # | What happens | Event(s) |
|---|--------------|----------|
| 1 | Closed row dragged past 40dp and released | `folder_swipe_opened` {side, action} |
| 2 | Closed row dragged less than 40dp | none |
| 3 | Open row dragged again and released open on the same side (including pushed against its limit) | none: still the same opening |
| 4 | Open row dragged across and released open on the other side | `folder_swipe_closed` reason `other_side`, then a new `folder_swipe_opened` |
| 5 | Open row dragged back under 40dp | `folder_swipe_closed` reason `drag_back` |
| 6 | Open row's body tapped (closes it instead of opening the file) | `folder_swipe_closed` reason `row_tap` |
| 7 | Revealed button tapped | `folder_swipe_action_tapped` {action, side}. Ends the opening. A second tap during the closing animation is not counted |
| 8 | Row closed, then opened again | two openings |
| 9 | Another row's drag starts while this one is open | `folder_swipe_closed` reason `other_row` |
| 10 | The list starts scrolling | reason `scroll` |
| 11 | Top-bar menu, sort sheet, actions sheet or a dialog opens | reason `overlay` |
| 12 | Selection mode starts (long-press on another row) | reason `selection` |
| 13 | The screen leaves composition (back, into a child folder, rotation, activity destroyed) | reason `left_screen` |
| 14 | The system cancels the drag | reason `cancelled` |
| 15 | That side switched off in Settings while the row is open | reason `setting_off`. Expected to be about 0: Settings can't be reached from a folder screen without leaving it |
| 16 | A row recycled or prefetched by the list | never an event. Rows are keyed by path. Recycling resets the row's own state together with its offset, and a prefetched row starts closed. A scroll closes the open row (row 10) before it can leave the screen. The session is kept by path, not by composition, so a reused row can't inherit another row's opening |

**Invariant the analysis can check:** opened = tapped + closed + lost. "Lost" means the process
died with a row open; it should stay under 5%.

**Where the bookkeeping lives.** There are three reasonable options:

1. *All in the row.* The row remembers which side it settled on, reports an opening only when that
   side changes, and adds the side to the tap event. Closes the screen causes are all reported as
   one reason, `dismissed`. It is the smallest change, but scroll, overlay and another row become
   indistinguishable, and a scroll right after an opening is the strongest sign that the opening was
   an accident.
2. **Row plus screen plus a small session tracker in `FolderViewModel` (recommended).** The row
   detects its own transitions (opened, other side, drag back, row tap, button tap, cancel) and
   reports them through one new callback. It stops calling analytics itself, so instrumentation
   tests can watch what it reports. The screen reports the closes only it knows about (scroll,
   overlay, another row, leaving). The ViewModel reports selection. A pure-Kotlin tracker matches
   them by path and sends exactly one opened event and one tapped-or-closed event per opening. It
   is unit-testable on the JVM and follows the AGENTS.md rule that logic goes in ViewModels.
3. *Move the open row's path (`revealedPath`) into ViewModel state.* Every transition would happen in
   one place. But every drag start would emit a new screen state and recompose the whole folder
   screen, and it refactors code that works. Not recommended.

**Event names.** There are two options, plus a rejected third:

- A. Keep `folder_swiped_left` and `folder_swiped_right`, change what they mean, and add `action`.
  The names carry on, but one name would mean two different things depending on the version, and
  any read that pools versions is silently wrong.
- **B. New `folder_swipe_opened` {side, action}, and stop sending the two old events
  (recommended).** The old names keep the old meaning for good, and the two can never be pooled by
  mistake.
- Sending both for one release (rejected): it doubles the volume, and the old events answer nothing
  the new ones don't.

**Params.**

| # | Event | Params | Values |
|---|-------|--------|--------|
| 1 | `folder_swipe_opened` (new) | `side`, `action` | side `left`/`right` (the physical drag direction, as in Settings); action `rename`/`delete`/`move_to`/`copy_to`/`info` |
| 2 | `folder_swipe_action_tapped` (kept; meaning unchanged) | `action`, **`side`** (new) | as above. Rows without `side` come from older versions |
| 3 | `folder_swipe_closed` (new) | `side`, `action`, `reason`, `open_for` | reason: one of the 10 values in the table above; open_for `under_1s`/`1_to_5s`/`over_5s` |

- `open_for` tells "opened and scrolled away within a second" (an accident) apart from "opened, looked,
  then moved on" (deliberate, then dropped). It is recommended because the Phase 2 decision depends
  on that difference, and it costs a timestamp. Dropping it is the lighter alternative.
- No extra "what the other side is set to" param. The action of the side that opened is the mapping
  that matters.
- **Optional, recommended:** add `previous` to `settings_swipe_left` and `settings_swipe_right`, so
  "switched rename off" can be told apart from "re-picked none" or "moved from move_to to none". The
  Phase 2 escalation reads this.
- All values are names of the app's own options. No event carries a file name, path or count of
  anything on the device (AGENTS.md, Analytics). The tracker keeps the row's path in memory only, to
  match a close to its row, and never sends it.
- None of these events is a success or a failure, so none touches `ReviewPrompt`.

### Phase 2 — conditional, decided only on Phase 1 data

Phase 2 runs only when the decision rule under Verification is met, and only for the side that met
it. These are the options:

| # | Option | How | Pros | Cons |
|---|--------|-----|------|------|
| A1 | **Larger reveal threshold** | `SwipeThreshold` 40dp → 56dp (70% of the 80dp button) | One constant. Applies to everyone, including the existing users who produce today's accidental openings. Reversible. No migration, strings or badges | Applies to both sides. Deliberate swipers barely notice, since they drag to the 80dp limit. Doesn't stop a mostly-vertical scroll that the row has already claimed |
| A2 | **Claim the drag only when it is clearly sideways** | Replace the claim inside `detectHorizontalDragGestures` with an `awaitEachGesture` loop. The row takes the gesture only if, when the finger passes the touch slop, the sideways movement is at least twice the vertical movement. Otherwise the events stay unconsumed and the list scrolls | Targets the "diagonal scroll opened a row" case directly | Custom gesture code. The RTL and selection-mode tests need re-checking |
| B | **Off by default for new installs** (per side) | First launch of a fresh install writes `NONE` explicitly for that side. Existing users get no write | Removes the gesture for people who never asked for it. Existing users unchanged | Does nothing for existing users, who produce all of today's data. New users can find swipes only through Settings. Mixes cohorts in the counts. Makes instrumentation tests depend on install state (see Risks) |
| C | Remote Config experiment for A1 or B | A threshold or default parameter, with the current behaviour as the fallback | A/B result without another release | Results live in the Firebase console, not in the count-based BigQuery reads the owner uses. Needs per-variant tagging. Heavy for one constant |
| D | First-use hint ("Swipe a row to rename or delete") | New UI and strings in 20 languages | Helps discovery | More UI for a gesture whose problem is being triggered too easily, not being found too rarely. Not recommended |

**Recommendation:** start with A1. Add A2 if `scroll` makes up more than half of the accidental
closes. Use B only as an escalation for a side that is still below the bar 30 days after A1 ships,
and never change settings for existing users.

The reasoning:

- The users who open rows by accident today are existing users, and B never reaches them.
- A1 can be undone with one line.
- B costs discoverability, and it costs the test determinism that A doesn't touch.

**How B detects a new install without touching existing users** (only if B is ever chosen):

- At the first launch after a fresh install, Android sets `PackageInfo.firstInstallTime` and
  `lastUpdateTime` to the same value. Any update makes `lastUpdateTime` later. The test runs once at
  startup.
- On a fresh install, write `NONE` explicitly for the chosen side, and only if the key is still
  absent.
- On an update, write nothing.
- The defaults used when nothing is stored stay RENAME/DELETE (`DataStorePreferencesSource.kt:213-215`).
  Every failure path (the PackageInfo read throws, the write fails, an IO error falls back to
  defaults) therefore ends in today's behaviour for everyone. It never silently switches an
  existing user off.
- Flipping the defaults to NONE and back-filling RENAME/DELETE for existing users is the riskier
  mirror image. A failed back-fill would turn swipes off for an existing user. Rejected.
- Edge cases:
  - Play updates an install before its first launch: it is treated as existing, so swipes stay on.
    Harmless.
  - "Clear data" on an install that was never updated: treated as new again. Consistent with a
    fresh state.
  - Backup restore: can't happen, because `allowBackup="false"`.
- Assumption to verify on the emulator before relying on it: the two timestamps are exactly equal
  after `adb uninstall` + `adb install`.

**Feature Discovery Badges (AGENTS.md).**

- **Phase 1:** nothing new to point at, so no badge changes.
- **A1/A2:** a change in feel, not a new feature, so no badge changes.
- **B:**
  - New installs have dismissed nothing, so the hamburger → drawer → Settings trail already shows
    for them.
  - Existing users see no change, so raising `BADGE_MENU_DRAWER` or `BADGE_DRAWER_SETTINGS` would
    point at nothing new. That is exactly what the rules forbid. Leave `BADGE_VERSIONS` and
    `PreferencesRepositoryTest.LAST_RELEASE_VERSIONS` alone.
- Adding a new setting would need the whole trail raised (`PreferencesRepository.kt:180-195`, where
  the trail now ends at the Settings screen) and `LAST_RELEASE_VERSIONS` updated after release. A
  "swipe sensitivity" setting would be one, which is one more reason not to add it.

## Step-by-step changes

### Phase 1 (target: 2.8.0, pending open question 1)

1. **`data/model/SwipeSide.kt` (new).** `enum class SwipeSide { LEFT, RIGHT }`. The KDoc says it is
   the physical direction the row was dragged, the same meaning as the settings
   (`PreferencesSource.kt:43-53`), and that the lowercased names are analytics vocabulary, so
   renaming an entry breaks continuity.
2. **`data/model/SwipeCloseReason.kt` (new).** `ROW_TAP, DRAG_BACK, CANCELLED, SETTING_OFF`, which
   the row reports; `OTHER_ROW, SCROLL, OVERLAY, LEFT_SCREEN`, which the screen reports; `SELECTION`,
   which the ViewModel reports; and `OTHER_SIDE`, which the tracker infers. The KDoc says who
   reports each one.
3. **`ui/components/SwipeableFileListItem.kt`.**
   - Add `sealed interface SwipeRowEvent { Opened(side, action); Tapped(side, action); Closed(reason) }`
     and a trailing param `onSwipeEvent: (SwipeRowEvent) -> Unit = {}`. The default does nothing, so
     the 24 existing call sites in tests compile unchanged.
   - Read it through `rememberUpdatedState`, as `currentOnRevealedChange` is read (`:102`): the drag
     detector is only rebuilt when its keys change.
   - Add `val settled = remember { SettledSide() }`, a private holder with `var side: SwipeSide?`.
     It is not snapshot state, because nothing reads it during composition. It records the side the
     row last *settled* on, which is set when the decision is made. That is more reliable than the
     geometry: a row still animating open after release is already open.
   - `onDragEnd` (`:227-246`): keep the decision inside the existing `scope.launch`. The
     `onHorizontalDrag` `snapTo` calls are launched too, and only run before this block if it is
     launched after them. Report before `animateTo`, because a newer animation on `offsetX` cancels
     the coroutine still waiting on the older one, and anything after it is skipped (claim 2). Remove
     both `trackFolderSwiped*` calls:

     ```kotlin
     val side = when {
         offsetX.value > swipeThresholdPx -> SwipeSide.RIGHT
         offsetX.value < -swipeThresholdPx -> SwipeSide.LEFT
         else -> null
     }
     val wasOpenOn = settled.side
     settled.side = side
     when {
         side == wasOpenOn -> Unit // a re-drag that settled where it was: the same opening
         side == null -> currentOnSwipeEvent(SwipeRowEvent.Closed(SwipeCloseReason.DRAG_BACK))
         else -> currentOnSwipeEvent(
             SwipeRowEvent.Opened(side, if (side == SwipeSide.RIGHT) rightAction else leftAction)
         )
     }
     // existing animateTo + currentOnRevealedChange, unchanged
     ```

   - `onDragCancel` (`:247-252`): if `settled.side != null`, report `Closed(CANCELLED)` and reset it.
   - Row body tap while open (`:190-194`): if `settled.side != null`, report `Closed(ROW_TAP)` and
     reset it, synchronously, before launching the animation.
   - Both buttons (`:154-182`): if `settled.side != null`, report `Tapped(RIGHT or LEFT, action)`
     and reset it, synchronously, before the launch. Remove the `AnalyticsTracker` calls. The reset
     is what stops a double tap from counting twice.
   - `LaunchedEffect(isRevealed)` (`:134-138`) and `LaunchedEffect(isSelectionMode)` (`:125-129`):
     reset `settled.side` silently before `animateTo`. The screen and the ViewModel report those
     closes, and reporting here too would count them twice.
   - `LaunchedEffect(leftAction, rightAction)` (`:142-146`): when it closes the row, report
     `Closed(SETTING_OFF)` if a side was settled, then reset.
   - Drop the `AnalyticsTracker` import. Extend the class KDoc (`:58-74`): the row reports what
     happened to it and leaves the counting to the caller.
4. **`data/util/AnalyticsTracker.kt:479-492`.** Remove `trackFolderSwipedRight` and
   `trackFolderSwipedLeft`. Add `trackFolderSwipeOpened(side: String, action: String)`. Change
   `trackFolderSwipeActionTapped(action: String, side: String)`. Add
   `trackFolderSwipeClosed(side, action, reason, openFor: String)`. Each KDoc lists the possible
   values and says when the event fires (once per opening, untapped closes only). No ReviewPrompt
   hook. String params, as the rest of the tracker uses.
5. **`ui/screens/folder/SwipeRevealTracker.kt` (new).**
   - `interface SwipeEvents { opened(side, action); tapped(side, action); closed(side, action, reason, openFor) }`.
   - `object AnalyticsSwipeEvents : SwipeEvents`, which lowercases enum names and calls the step 4
     functions.
   - `internal class SwipeRevealTracker(events: SwipeEvents, now: () -> Long = System::nanoTime)`.
     It uses `nanoTime` rather than `SystemClock`, so JVM tests need no Android stubs. It holds at
     most one `Opening(path, side, action, since)`:
     - `Opened` for the same path and side: ignore it. The row already dedupes; this only guards
       against the same event arriving twice.
     - `Opened` for the same path on the other side: close with `OTHER_SIDE`, then open.
     - `Opened` for a different path: close with `OTHER_ROW` (defensive, since the screen normally
       reports this at drag start), then open.
     - `Tapped`: always sent; clears the opening if the path matches.
     - `Closed(reason)` from a row: sent only if the opening is for that path.
     - `closeOpen(reason)` from the screen or the ViewModel: closes whatever is open. Does nothing
       when nothing is open, so calling it on every scroll start is free.
     - `open_for` buckets: < 1,000 ms → `under_1s`, < 5,000 ms → `1_to_5s`, otherwise `over_5s`.
6. **`ui/screens/folder/FolderViewModel.kt`.**
   - Constructor param `swipeEvents: SwipeEvents = AnalyticsSwipeEvents` after `countDispatcher`
     (`:194-195`). `private val swipeReveals = SwipeRevealTracker(swipeEvents)`.
   - Add `fun onSwipeRowEvent(path: String, event: SwipeRowEvent)` and
     `fun onSwipeRowsClosed(reason: SwipeCloseReason)`.
   - In `toggleSelection` (`:465-474`) and `selectAll` (`:476-480`): when the selection was empty
     before the update and isn't after it, call `swipeReveals.closeOpen(SELECTION)`.
   - `Factory` (`:1477`): optional `swipeEvents` param, passed through. It exists only so the
     folder-screen instrumentation test can record events.
7. **`ui/screens/folder/FolderScreen.kt`.**
   - The overlay effect (`:165-170`) adds `viewModel.onSwipeRowsClosed(OVERLAY)`.
   - The scroll collector (`:171-176`) adds `viewModel.onSwipeRowsClosed(SCROLL)`.
   - In `onRevealedChange` (`:486-492`): when `revealed` is true and `revealedPath` is another
     path, call `onSwipeRowsClosed(OTHER_ROW)` before reassigning it.
   - Pass `onSwipeEvent = { viewModel.onSwipeRowEvent(file.path, it) }` to the row (`:447`).
   - Add `DisposableEffect(viewModel) { onDispose { viewModel.onSwipeRowsClosed(LEFT_SCREEN) } }`.
     It covers back, child-folder navigation and rotation; rotation resets the rows' offsets because
     they aren't saved.
8. **Optional, recommended: `SettingsViewModel.kt:163-183` and `AnalyticsTracker.kt:655-667`.**
   - `setSwipeLeftAction` and `setSwipeRightAction` read the stored value with
     `preferencesRepository.swipeLeftAction.first()` inside the existing `viewModelScope.launch`,
     then track `(action, previous)` before writing.
   - Add the `previous` param to `trackSettingsSwipeLeft` and `trackSettingsSwipeRight`.
9. **`.claude/skills/analyze-analytics/SKILL.md`.**
   - Rewrite the reading trap at `:185-186`. `folder_swipe_opened` fires once per opening (2.8.0+).
     `folder_swiped_*` (up to 2.7.x) fired on every release past 40dp, re-drags included, so the two
     must never be pooled. Tap rows without `side` come from older versions. Define the
     `folder_swipe_closed` reasons.
   - Rewrite P10 at `:261`: per side and action, tapped ÷ opened, the share of each close reason,
     the `under_1s` share, and the lost share; plus the `none` share of settings choices, by
     `previous` when present.
   - Without this, the next report computes P10 from events that no longer arrive.
10. **Nothing else changes.**
    - Strings and translations: none.
    - Badges: none.
    - Website, README, changelog: none, since nothing visible changes.
    - `AGENTS.md`: no new rule.
    - UI: Phase 1 changes no pixels and no gesture behaviour, so the `frontend-design` skill is not
      needed for it.

### Phase 2 (only after the decision rule fires; the side or sides it names)

The owner requires the **`frontend-design` skill** for every Phase 2 option. Each one changes what
the list does under the finger, or what a new user sees.

- **A1.**
  - `SwipeableFileListItem.kt:56`: `SwipeThreshold = 56.dp`, with a KDoc on why (accidental
    openings, measured by Phase 1).
  - Update the 40dp mentions in the analyze-analytics skill and in the
    `partialSwipe_doesNotRevealAction` comment.
  - Optional: a website changelog line on all 20 `website/*/changelog.html` pages (open question 5).
- **A2.**
  - In the same file, replace the `detectHorizontalDragGestures` claim (`:221-260`) with an
    `awaitEachGesture` loop: `awaitFirstDown`, then `awaitTouchSlopOrCancellation`. Consume and
    claim only when |dx| ≥ 2·|dy|, otherwise return without consuming. Then follow with
    `horizontalDrag`.
  - Keep the `onDragStart`, `onDragEnd` and `onDragCancel` behaviour and the Phase 1 reports
    exactly as they are.
- **B** (one side or both):
  - New `util/InstallInfo.kt`: `isFreshInstall(context)` returns `firstInstallTime == lastUpdateTime`,
    read the way `ReviewPrompt.kt:88-100` reads PackageInfo. Any exception returns `false`, which
    keeps today's behaviour. Leave `ReviewPrompt` as it is.
  - `PreferencesSource` gets `suspend fun setSwipeActionsIfUnset(left: SwipeAction?, right: SwipeAction?)`.
    In `DataStorePreferencesSource` it writes only absent keys, in one `editSafely` transaction
    (all DataStore access goes through `DataStoreSafeAccess`).
  - `PreferencesRepository` gets `suspend fun applyNewInstallSwipeDefaults(isFreshInstall: Boolean)`.
  - `FileExplorerApplication.onCreate` (`:20-32`) calls it on the existing IO coroutine. It doesn't
    block startup. The first folder screen comes after onboarding and observes the preference as a
    flow, so a late write still lands.
  - Update the fakes: `test/.../FakePreferencesSource.kt`, `androidTest/.../InMemoryHomeSources.kt:65-69`,
    and the in-file fake in `HomeBadgeTrailTest.kt:368-372`.
  - No strings: "None" already exists in 20 languages. No badges (see above).

## Tests

Every new test uses JUnit 4 with mockk and Compose UI Test, and nothing adds a skip condition.
Instrumentation tests use only gestures and Compose, which run on the `Pixel_7_API_36` emulator.

### Phase 1, unit (`app/src/test/`)

- **`ui/screens/folder/SwipeRevealTrackerTest.kt` (new)**, with a recording `SwipeEvents` and a
  fake clock:
  - one opened per `Opened`, and a duplicate `Opened` for the same path and side sends nothing;
  - the other side gives `closed(other_side)` then `opened`;
  - a different path gives `closed(other_row)` then `opened`;
  - `Tapped` ends the session, so a later `closeOpen(SCROLL)` sends nothing;
  - `Tapped` with no session is still sent;
  - a row `Closed` for a path that isn't open sends nothing;
  - `closeOpen` with nothing open sends nothing;
  - each close reason reaches the sink unchanged;
  - `open_for` bucket edges at 999, 1,000, 4,999 and 5,000 ms;
  - the sink only ever receives side, action, reason and bucket values, never a path. The test
    asserts the exact value sets.
- **`ui/screens/folder/FolderViewModelTest.kt`**, passing a recording `SwipeEvents` to the
  constructor:
  - the first `toggleSelection` with an open session reports `selection`;
  - `selectAll` does too;
  - toggling inside an existing selection and `clearSelection` report nothing;
  - `onSwipeRowEvent` and `onSwipeRowsClosed` reach the tracker.
- **`data/util/AnalyticsTrackerReviewHookTest.kt`**: a new test, `swipe events leave the review
  prompt alone`. It runs `trackFolderSwipeOpened`, `trackFolderSwipeActionTapped` and
  `trackFolderSwipeClosed` and checks `verify(exactly = 0) { ReviewPrompt.onActionFailed() }`. An
  unused swipe must never count as a failure.
- **`ui/screens/settings/SettingsViewModelTest.kt`** (only with step 8): update `choosing a swipe
  action reports the choice` (`:465-475`) to expect `previous`, and add a case where the stored
  value differs from the default.

### Phase 1, instrumentation (`app/src/androidTest/`)

- **`ui/components/SwipeableFileListItemTest.kt`**: record `onSwipeEvent` into a list.
  - Re-dragging an open row gives a single `Opened(RIGHT, DELETE)`. Swipe right fully, then swipe
    right again from the centre.
  - Pushing an open row against its limit gives a single opening.
  - Dragging an open row to the other side gives `Opened(RIGHT)` then `Opened(LEFT)`, using a
    full-width left swipe.
  - Closing then reopening gives `Opened`, `Closed(ROW_TAP)`, `Opened`.
  - Dragging an open row back shut gives `Closed(DRAG_BACK)`.
  - Tapping the left button gives `Tapped(LEFT, RENAME)`. With DELETE on both sides, tapping the
    right button gives `Tapped(RIGHT, DELETE)`.
  - A partial swipe reports nothing. Extend `partialSwipe_doesNotRevealAction` (`:554-588`).
  - The caller taking the row away reports nothing, and a later swipe reports a new opening. Extend
    `swipe_reportsRevealed_andCollapsesWhenCallerTakesItAway` (`:838-877`).
  - Entering selection mode reports nothing.
  - RTL reports the same physical side as LTR. Extend
    `rtl_theRowFollowsTheFingerAndRevealsTheSameAction` (`:515-552`).
- **`ui/screens/folder/FolderScreenTest.kt`** and **`testutil/FolderScreenRobot.kt`**:
  - `render(swipeEvents = …)` passes `viewModel = viewModel(key = path, factory = FolderViewModel.Factory(app, path, null, swipeEvents))`.
  - Extend `swipingAnotherRow_closesTheOpenOne` to expect `closed(other_row)`.
  - Extend `openingOverflowMenu_closesTheOpenRow` to expect `closed(overlay)`.
  - New `scrollingTheList_closesTheOpenRowAsAScroll`: 40 fixture files, swipe a row open, swipe the
    list up, expect `closed(scroll)` with `open_for` `under_1s`.
  - New `tappingTheOpenRow_closesItAsARowTap`: expects `closed(row_tap)` and no navigation.

### Phase 2

- **A1.** In `SwipeableFileListItemTest`, add `aDragBetweenTheOldAndNewThreshold_doesNotOpen` (48dp)
  and `aDragPastTheThreshold_opens` (64dp), so the threshold is pinned from both sides. Use dp
  converted with `toPx()`, as the existing partial-swipe test does.
- **A2.** `aMostlyVerticalDrag_doesNotOpenTheRow` (dx = dy = 60dp) in `SwipeableFileListItemTest`,
  and `aDiagonalDragOnTheList_scrollsInsteadOfOpening` in `FolderScreenTest`. Keep the RTL and
  selection-mode tests green.
- **B.** Fresh install versus update can't be arranged on the emulator, so the device state is
  passed in as a boolean (AGENTS.md).
  - `DataStorePreferencesSourceTest`: `setSwipeActionsIfUnset` writes absent keys, leaves an
    explicit choice alone, and a failing store falls back to RENAME/DELETE.
  - `PreferencesRepositoryTest`: fresh → `NONE` for the configured side; update → nothing written.
  - `FolderScreenTest`: an `@Before` that pins both swipe actions in the real DataStore and an
    `@After` that restores them. Every connected run installs the APK fresh, and the app's own first
    launch would otherwise switch swipes off.

## Verification

1. **After every change, with the AGENTS.md flags:**

   ```bash
   ./gradlew -w --console=plain -I gradle/agent-quiet.init.gradle testDebugUnitTest
   ./gradlew -w --console=plain lintDebug
   ./scripts/check-tests.sh
   ```

2. **Instrumentation.** Only with the emulator already running. Hand it to the
   `instrumentation-runner` subagent, one class per run (a comma list runs only the first test):
   `SwipeableFileListItemTest`, then `FolderScreenTest`. Check `free -h` and cold-boot the emulator
   between the two.
3. **Run the `delta-review` skill** on the changed files.
4. **Manual checks on the emulator** (debug build). Analytics collection is off on debug builds, but
   every event is echoed to `adb logcat -s Analytics` (`AnalyticsTracker.kt:58-61`).
   - Swipe right: one `folder_swipe_opened side=right action=delete`.
   - Drag the open row again, or push it further: nothing.
   - Drag it across to the left: `closed … reason=other_side`, then `opened side=left action=rename`.
   - Tap the row: `closed reason=row_tap`, and the file does not open.
   - Open a row and scroll: `closed reason=scroll open_for=under_1s`.
   - Open a row and drag another row: `closed reason=other_row`.
   - Open a row and open the top-bar menu: `closed reason=overlay`.
   - Open a row and long-press another row: `closed reason=selection`.
   - Open a row and press back: `closed reason=left_screen`.
   - Double-tap the Delete button: one `folder_swipe_action_tapped action=delete side=right` and
     one dialog.
   - Arabic (RTL): a drag towards the right edge still reports `side=right`.
   - Light and dark themes look unchanged (Phase 1 changes nothing visible).
5. **Reading the analytics after release.**
   - Run the `analyze-analytics` skill on the first export where the Phase 1 version has been live
     for 30 days and has at least 1,000 `folder_swipe_opened` per side.
   - Use only the Phase 1 versions. List them explicitly: a string comparison breaks at 2.10.
   - A quick check on the parquet:

     ```sql
     select event_name,
            json_extract_string(params, '$.side')     as side,
            json_extract_string(params, '$.action')   as action,
            json_extract_string(params, '$.reason')   as reason,
            json_extract_string(params, '$.open_for') as open_for,
            sum(count) as n
     from read_parquet('/home/max/analytics/events-YYYY-MM-DD.parquet')
     where event_name in ('folder_swipe_opened', 'folder_swipe_action_tapped', 'folder_swipe_closed')
       and json_extract_string(params, '$.side') is not null
       and json_extract_string(params, '$.app_version') in ('2.8.0' /* , later Phase-1 versions */)
     group by all
     order by side, event_name, n desc;
     ```

   - Per side (and per action when it isn't the default):
     - T = tapped ÷ opened;
     - A = closes by `scroll` + `row_tap` + `drag_back` + `cancelled`, ÷ opened (the user's own next
       gesture, with the button unused);
     - the `under_1s` share of A;
     - Lost = 1 − (tapped + closed) ÷ opened.
6. **Decision rule for Phase 2** (proposed bars; the owner fixes them before the data arrives, so the
   data can't move them):

   | # | Condition (per side, Phase 1 versions only) | Decision |
   |---|---------------------------------------------|----------|
   | 1 | Lost > 5% | Nothing about swipes; the measurement is broken. Find the missing close before reading anything else |
   | 2 | T ≥ 50% | Swipes on this side are used. No Phase 2 for it; close the todo row for this side |
   | 3 | T < 35% **and** A ≥ 50% | In the way: Phase 2 A1 for the gesture. Add A2 if `scroll` is at least half of A |
   | 4 | Anything else | Inconclusive. No change; read again in the next report |
   | 5 | 30+ days after A1/A2 ships, with ≥ 1,000 openings on that side: T still < 35% **and** ≥ 60% of value-changing settings choices on that side go to `none` (from `previous`, ≥ 100 choices) | Escalate to B for that side only |

## Risks and edge cases

- **Comparing counts across versions.**
  - `folder_swiped_*` (up to 2.7.x) and `folder_swipe_opened` (2.8.0+) measure different things. Don't
    put them in one ratio and don't read the change between them as a change in behaviour. The
    Phase 1 baseline is the first Phase 1 version, not 2.7.
  - A 30-day window mixes versions, and the people who update first are the most engaged users.
    Split by `app_version`.
  - The counts are events, not users. One heavy renamer probably explains the 2.7 rename drop.
  - Tap rows without `side` come from older versions; leave them out of per-side rates.
- **Lost openings.** If the process dies with a row open, no close is ever sent. Rotation and
  activity destruction do report `left_screen`, through `onDispose`. Expect a few percent; above 5%
  means a close path is missing (rule 1).
- **Tiny double-count windows.**
  - A button tap in the one frame between a scroll start and the row's silent reset sends both
    `closed(scroll)` and `tapped`.
  - A row removed from the list while open (deleted by another app, then the folder reloads on
    return) keeps its session until the next close, which gets that close's reason.
  - Both are negligible.
- **Selection bias in the "none" share.** Only people who open Settings are counted, and re-picking
  the current value counts as a choice too. `previous` fixes the second problem but not the first.
- **Phase 2 B.**
  - Every `connectedDebugAndroidTest` run installs fresh, so tests that render the real folder
    screen against the real DataStore would see swipes off. Pin them (Tests → Phase 2 → B).
  - The exact equality of `firstInstallTime` and `lastUpdateTime` is an assumption; check it with
    `adb shell dumpsys package com.mauriciotogneri.fileexplorer | grep -E 'firstInstallTime|lastUpdateTime'`
    after a fresh install and after `adb install -r`.
  - Swipe volume falls as new installs arrive with swipes off. Rates per opening stay comparable;
    raw counts don't.
- **Phase 2 A1.** It applies to both sides even if only one side failed the bar. A threshold can't
  be set per side without making the two directions feel different, which isn't worth it. If only
  one side fails, B for that side is the per-side tool.
- **Related, not fixed here.** Today a double tap on a swipe button sends two tap events for one
  action, and the action itself runs once: the second tap's animation cancels the first tap's
  coroutine. Phase 1's dedupe removes the double count. The behaviour is otherwise left alone.

## Open questions for the owner

1. Ship Phase 1 in 2.8.0, which is still unreleased, or in a later 2.8.x? Recommended: 2.8.0. It
   changes nothing visible, and every release without it is a month without usable data.
2. Keep the two optional additions, `open_for` on the close event and `previous` on the settings
   choices? Recommended: both. Each answers a question the Phase 2 rule asks.
3. Stop sending `folder_swiped_left` and `folder_swiped_right` outright (recommended), or send them
   alongside the new events for one release?
4. Approve the Phase 2 bars in the decision rule (Lost 5%, T 50% / 35%, A 50%, settings `none` 60%)
   now, before the data arrives, or set your own?
5. If Phase 2 A1 ships, add a website changelog line on all 20 language pages, or treat it as a
   silent tweak?
