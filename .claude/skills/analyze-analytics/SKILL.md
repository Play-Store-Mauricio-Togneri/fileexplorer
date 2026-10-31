---
name: analyze-analytics
description:
  Downloads the File Explorer app's Firebase Analytics data for the last 30 days from BigQuery, through the saved query "Events analyzer", as a parquet of event name, JSON params and count with app_version and os_version in the params, then analyzes it across every app version together and writes a dated report of how people use the app, what fails or puts them off, and ranked recommendations explained from the code. Use whenever the user asks to analyze, review or summarize analytics, Firebase events, the BigQuery export or usage data, or asks what users do or what to improve or fix — even if they don't name the file.
---

# Analyzing the analytics export

The export is **aggregated**: one row per event name and parameter combination, with a count. There
are no user IDs and no dates, so every figure is a count of events, never of people, and there are
no funnels. The report describes how people use the app **whatever version they run**: every
figure pools every `app_version` in the export, and no section compares one release with another.
The params always carry two dimensions: `os_version` splits a failure by Android release, and
`app_version` checks a failure against the code shipping now before it becomes a recommendation.

Aggregated counts are easy to misread. The first analysis of this data (2026-10-30) nearly reported
a 6.9% delete failure rate that was really 4.9%, because 3,000 failures came from a pre-2.6 code
path that has since been fixed; and it nearly called the PDF viewer a failed feature when it only
opens files no other app can. Most of this skill exists to stop those misreadings: interpret every
event against what the current code means by it, and remember that a pooled failure rate also
carries every bug older versions still have — so check each failure on the newest versions before
recommending a fix for it.

**This is a read-only analysis.** Download the data, read it, read the code, write one report file.
No code edits, no git operations, no builds, no emulator. The only write outside the analytics
folder is the rare update of the saved query described in Step 1.

---

## Step 0 — download, load and validate the file

**Input.** Always a fresh download (below), saved as
`/home/max/analytics/events-YYYY-MM-DD.parquet` with today's date. Never analyze a file already on
disk, not even one downloaded earlier today: the new download replaces it. The report is written
next to it.

**Download.** The query is the saved query "Events analyzer" in BigQuery (project
`file-explorer-4fc1d`, location `europe-west6`), not a copy kept here: the owner runs and edits it
in the console too, so read it fresh every time. It takes every event the app logs itself except
`screen_view`, drops the parameters Firebase adds on its own, adds `app_version` and `os_version`,
and covers the 30 days ending yesterday. BigQuery keeps a saved query as a one-file Dataform
repository, so read it through the Dataform API, run it with `bq` — the result is small enough to
stream, so no bucket is needed — and write the parquet with DuckDB:

```bash
S=<session scratchpad>
R=https://dataform.googleapis.com/v1beta1/projects/file-explorer-4fc1d/locations/europe-west6/repositories/eba0cc4a-e0e8-4e7d-9267-4167f422c6a1
curl -s -H "Authorization: Bearer $(gcloud auth print-access-token)" "$R:readFile?path=content.sql" \
  | jq -ej 'if .contents then .contents | @base64d else error(tostring) end' > $S/query.sql
bq --project_id=file-explorer-4fc1d --location=europe-west6 --headless query \
  --use_legacy_sql=false --format=json --max_rows=500001 < $S/query.sql > $S/rows.json
```

```python
duckdb.sql(f"""copy (select * from read_json('{rows}', format='array',
             columns={{'event_name': 'VARCHAR', 'params': 'VARCHAR', 'count': 'BIGINT'}},
             maximum_object_size=1000000000)) to '{out}' (format parquet)""")
```

On 2026-10-30 this took about ten seconds and returned 75,752 rows. Stop instead of analyzing
anything else when:

- `gcloud`, `curl` or `bq` reports an authentication error — ask the owner to run
  `! gcloud auth login`. A failed read prints the API's error in place of the query.
- The file holds 500,001 rows: the result was cut off. The cap exists because `bq` holds every row
  in memory.

**Expected shape.** Columns `event_name`, `params` (a JSON object as a string, every value a
string) and `count`. Every row's params carry `app_version` (the `versionName`, e.g. `2.7.3`) and
`os_version` (e.g. `Android 8.1.0` or `Android 14`). If either key is missing from most rows, stop:
the saved query no longer adds them — someone edited it in the console. Without `app_version` no
failure can be checked on the newest versions, and an unchecked pooled report would quietly
reintroduce the misreadings above; without `os_version` no failure can be split by Android release.

**Tooling.** `python3` with `duckdb` (pandas is available; pyarrow is not). Both live in the
user's site-packages, so run Python **without `-I`** — with it, the import fails and reads as
"duckdb is not installed". The file comes from the owner's own BigQuery project, so it is not the
untrusted download that `-I` guards against. Write scripts in the session scratchpad and run them
from there. Load the file once into a table with the dimensions already pulled out, so no later
query has to parse them:

```python
import duckdb
c = duckdb.connect()
c.sql(f"""create table t as
  select event_name e, params::JSON j, count n,
         j->>'app_version' av,
         try_cast(regexp_extract(j->>'os_version', '(\\d+)', 1) as int) os
  from '{path}'""")
```

Four DuckDB traps cost time in the first analyses:

- `->>` binds **looser** than `=` and `AND`. `where e='x' and j->>'k'='v'` parses wrongly; write
  `(j->>'k')='v'`, or extract the key into a column first.
- Prefix **every** column alias (`n_rows`, `v_mode`). Words like `partial`, `large`, `small`,
  `rows` and `mode` fail to parse, and `isnull` is worse: it parses as a postfix operator and
  silently yields a wrong column.
- Every param value is a string, in the code's own casing (`sort_mode` is `NAME_ASC`, settings
  values are lowercase). Use `try_cast` for numbers (`item_count`, `errno`, `duration_ms`), because
  a value that is not a number should drop out rather than abort the query.
- Rows are aggregated, so any percentile or median must be **weighted by `count`** —
  `quantile_cont` over rows is unweighted and wrong. Order by the value, take a running sum of
  `count`, and read the value where the running sum crosses the target share of the total.

**Versions.** Every version in the export goes into every figure — old releases still installed and
testers' release builds alike (debug, emulator and Test Lab collection are off); nothing is left
out. `app_version` matters only for the newest-version check below. For it, group `app_version` by
major.minor (`2.7.0`–`2.7.3` → `2.7`), sorted numerically (2.10 after 2.9).

**The newest-version check.** Run it on every failure rate before ranking it: compute the same rate
on the **newest versions** — the highest line, plus the next lines down, added until the rate's
denominator holds at least 100 events — and name the lines used. The floor exists because the
highest line can be a tester's build of a version still in development (the repo's `versionName`
in `app/build.gradle.kts` is usually that version), and a handful of its events must not make a
failure look fixed. A rate on the newest versions of at least half the pooled rate means the problem
is current, and it is ranked. Under half means it comes mostly from older versions: it leaves the
ranking for the data notes' "older versions only" list, with both rates.

**Sample sizes.** Judge a rate only when its denominator has at least 100 events, pooled across
every version. A count under 20 is anecdotal: report it, but never build a recommendation on it
alone.

---

## Step 1 — map the events to the current code

`app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/AnalyticsTracker.kt` is the event
catalogue; its KDoc says what most params mean. Before interpreting anything:

1. List every event name the tracker can send — the string passed to `trackEvent(`. Some calls put
   it on the next line, so match across lines (a multi-line regex), not with a one-line grep.
   Screen views go through `logEvent(SCREEN_VIEW)` and are not in this export; leave them out.
   Compare with the names in the data. The query takes every event the app logs, so an event in the
   code but absent from the data was **not sent in the 30 days**: nobody triggered it, or the
   release that sends it has not shipped yet — check the release branches before calling the
   feature unused. An event in the data but not in the code was removed or renamed; it only matters
   for older lines.
2. Event names are cut at 40 characters, so the data holds names no source file has
   (`destination_picker_folder_creation_cance`). Match by prefix.
3. Find the defaults the settings events move away from. They start in
   `data/source/DataStorePreferencesSource.kt`, often as a reference to an enum or constant
   elsewhere (`HomeSection.DEFAULT_ORDER`, `LocationType.entries`, `ThemeMode`); follow each to its
   definition. A choice only means something against the default.
4. Compare the param keys in the data with the keys the tracker sends. A key the tracker no longer
   sends comes from an older line. A key no version of the code ever sent —
   `git log -S'"<key>"' --oneline -- app/src/main` prints nothing — was added by Firebase
   (`debug_event` was, on 2026-10-30), and the saved query exists to drop those. Add it to the
   query's list of Firebase keys, check it with `bq query --dry_run`, save it as a new version,
   download again, and tell the owner in chat which key was dropped. That is the only change this
   skill makes to the saved query; anything else about it goes into the report.

   ```bash
   jq -n --rawfile q $S/query.sql --arg n "$(git config user.name)" \
     --arg e "$(gcloud config get-value account 2>/dev/null)" --arg m "Drop Firebase key <key>" \
     '{commitMetadata: {author: {name: $n, emailAddress: $e}, commitMessage: $m},
       fileOperations: {"content.sql": {writeFile: {contents: ($q | @base64)}}}}' \
   | curl -s -X POST -H "Authorization: Bearer $(gcloud auth print-access-token)" \
       -H "Content-Type: application/json" -d @- "$R:commit"
   ```

**Reading traps.** Each was true on 2026-10-30. Code changes, so confirm the one you rely on
against the current code before building a finding on it:

- **A param missing on a row** means the app version that sent it predates the param. Compute on
  rows that carry it, and say how many events that leaves out.
- **`delete_completed` counts selected items**, not the files inside them.
- **`operation_failed` for deletes speaks two vocabularies.** The walked delete (10+ nodes, with a
  progress dialog) puts the *shape* in `error_type` (`partial`, `all_failed`, `structural`) and the
  cause in `errno`; the one-call delete puts a *cause label* in `error_type` (`not_empty`, `busy`,
  `permission_denied`, …) and the shape in `outcome`. Compare deletes on `cause` where present,
  else on `errno` and `outcome`.
- **Delete failures from 2.8.0 on carry five more params**: `cause` (the message the user saw:
  `permission_denied`, `read_only`, `not_empty`, `busy`, `storage_unavailable`, `errno_other`,
  `unknown`, `system_folder`, `hidden_contents`, on both delete paths), `system_folders` and
  `unlisted_folders` (`0`, `1`, `2`, `3+`: protected `Android/data`/`Android/obb` folders left in
  place, and folders the app could not list that stayed), and, for the node behind `errno`,
  `failed_listing` (`file`, `unlisted`, `empty`, `listed`) and `failed_location` (`android_data`,
  `android_obb`, `android`, `volume_root`, `other`). The one-call delete's `error_type` can now be
  `system_folder` or `hidden_contents` too. `errno` keeps its meaning, so `39` or `16` with a
  `system_folder`/`hidden_contents` cause is a failure Android explains, not one the user can fix.
  A protected folder alone makes no syscall, so such rows can have no `errno` at all.
- **Permission returns changed meaning with the hint release.**
  `permission_returned_without_granting` carries `settings_page`, `hint_shown` and `recreated` from
  then on. `recreated` true is a return into a screen rebuilt while Settings was open (process
  death, or a theme, language or font change), which older releases never counted, while the false
  return older releases logged after the "no Settings page could open" toast is gone. A pooled U10
  return rate mixes both definitions.
  `permission_granted_on_return` exists only from that release on.
- **Releases before 2.7 also collected on Play's pre-launch (Test Lab) devices**, whose robots tap
  the grant button and press back, so their rows inflate U10's returns.
- **`errno` is the first failure in depth-first order**, so a child's errno wins over its folder's.
  `39` (ENOTEMPTY) as the first failure means every file the app could see was removed and the
  folder still held something — contents the app cannot list, or files written during the delete.
  `16` EBUSY is a mount point; `13` EACCES across a whole selection is a location refusing writes;
  `30` EROFS is read-only storage. `error_type` `unknown` means no errno was attached.
- **Rename failures carry no cause** — the repository returns a bare failure, so `error_type` is
  always `unknown` unless that code changed.
- **The in-app viewers (image, PDF, media, text) open a file only when no installed app can.** Low
  counts are by design, not low adoption.
- **`file_open_failed` with `no_handler` from "open with" is paired** with a `file_opened`: the
  chooser started, so read the two together.
- **`bottom_sheet_opened` `mode`**: `icon` is the ⋮ button, `press` a long-press. On the folder and
  analyzer screens long-press selects, so those are always `icon`.
- **`folder_swiped_*` fires when a row settles open past 40dp**, re-drags included, and only reveals
  a button; `folder_swipe_action_tapped` is the tap on it. Defaults: left = rename, right = delete.
- **`uncompress` `invalid_zip` fires instead of the dialog opening** (the file could not be read as
  a ZIP; the user gets a toast), never after confirming. Only ZIP can be extracted. `zip_bomb` is a
  10 GB total-size limit. `wrong_password` is the user's mistake. A password-protected archive logs
  `password_uncompress_*`, not `uncompress_*`.
- **Feedback "success" is any HTTP reply**, including a server error.
- **`location_sizes_measured` is sent only when a card was walked** (sizes are cached five minutes);
  each walk stops at 10,000 files, which is what `capped_count` counts.
- **The analyzer's select-all selects every loaded row**, and rows load 100 at a time, so analyzer
  deletes cluster at multiples of 100 (100, 200, 300 …).
- **Move `source_delete_failed`** usually means the copy landed and the original stayed — a
  duplicate. It is also raised when a subfolder could not be listed, whose files were then never
  copied at all. The delete's errno is not sent.
- **A move or copy that fails logs both** `destination_picker_operation_finished` with `success`
  false and one `operation_failed`. They are one population: never add them together.
- **`git log` messages here are mostly "Improvements"**, so they say nothing about what changed.
  To learn whether code changed between releases, diff the function, not the messages.

---

## Step 2 — usage checklist

Compute each item on every version together. Express shares with their denominator ("40% — 48.6k
of 120k"). Skip rows that lack the param an item breaks down by, and say
how many were skipped.

File-type groups for U2, fixed so one report compares with the next:

| Group        | Extensions                                                                                          |
|--------------|-----------------------------------------------------------------------------------------------------|
| Images       | jpg, jpeg, png, webp, gif, heic, heif, bmp, svg, avif                                               |
| Video        | mp4, mkv, avi, mov, 3gp, webm, flv, m4v, ts                                                         |
| Audio        | mp3, m4a, opus, ogg, wav, aac, amr, flac, wma, mid                                                  |
| PDF          | pdf                                                                                                 |
| APK          | apk                                                                                                 |
| App bundles  | xapk, apks, apkm                                                                                    |
| Documents    | doc, docx, xls, xlsx, xlsm, csv, ppt, pptx, odt, ods, odp, txt, rtf, md, epub, html, htm, json, xml |
| Archives     | zip, rar, 7z, gz, tar, bz2, xz                                                                      |
| No extension | `unknown`                                                                                           |
| Other        | everything else                                                                                     |

| #   | Behaviour             | Compute                                                                                                                                                                                                                                  |
|-----|-----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| U1  | Core loop             | `folder_tapped_to_open`, `file_opened`; `file_opened` share by `source`                                                                                                                                                                  |
| U2  | File types            | `file_opened` by `extension`, in the groups above; top ten extensions                                                                                                                                                                    |
| U3  | Entry points          | `home_location_card_opened` by `location`; `home_storage_card_opened` by `storage`                                                                                                                                                       |
| U4  | Cleanup               | `delete_confirmed`; share of `delete_completed` with `item_count` 1; `bottom_sheet_delete` ÷ `bottom_sheet_opened`; `delete_cancelled` ÷ (`delete_confirmed` + `delete_cancelled`)                                                       |
| U5  | Organising            | `destination_picker_shown` by `action` (move vs copy); `rename_confirmed`; `create_folder_confirmed`; `folder_long_pressed_to_select`; `folder_toolbar_select_all`                                                                       |
| U6  | Search                | opens = `home_search_container_tapped` + `home_search_icon_tapped`; `search_close_without_typing` ÷ opens; `search_filter_kind_changed` by `kind`; search's share of `file_opened`                                                       |
| U7  | Favourites, recents   | favourite adds (`bottom_sheet_add_to_favorites` + `folder_context_menu_add_to_favorites`) against `file_opened` from `favorite`; recents' share of opens; `settings_recent_files_tracking` by `enabled`                                  |
| U8  | Settings              | each `settings_*` choice event by value against its default — theme, hidden files, recents tracking, swipe actions, second lines, startup screen, home section order (a confirm that keeps the default order is not a change), locations |
| U9  | Sort                  | `folder_sort_by_selected` by `sort_mode` against the default                                                                                                                                                                             |
| U10 | Permission onboarding | `permission_grant_button_tapped` by `is_android_11_or_above`; `permission_returned_without_granting`, `permission_dialog_granted` / `_denied`, `permission_permanently_denied` — each per grant tap, split by Android version            |
| U11 | Analyzer              | `home_drawer_analyzer_tapped`; `delete_completed` with `source` `analyzer_category`                                                                                                                                                      |
| U12 | APK installs          | `apk_install_triggered`; `apk_permission_dialog_shown` → `apk_permission_open_settings` / `_cancelled`; app bundles (`xapk`, `apks`, `apkm`) among `file_opened`                                                                         |
| U13 | Permission hint       | Android 11+ returns from Settings: first-try success, retry success and retry rate, each by `settings_page` — definitions below the table                                                                                                |

U13 measures the hint the permission screen shows after a return from Settings without access. Use
only rows with `os_version` 11 or later (below that, the App info route sends the same return
events but never shows a hint) that carry `hint_shown`; skip U13 while the export has none.

- **First-try success:** `permission_granted_on_return` with `hint_shown` false ÷ (that +
  `permission_returned_without_granting` with `hint_shown` false).
- **Retry success, the hint's effect:** the same with `hint_shown` true. Read it against first-try
  success: the hint helps if retries clearly succeed (for example, at least a third end in a
  grant); the owner sets the bar.
- **Retry rate:** `permission_grant_button_tapped` with `hint_shown` true ÷
  `permission_returned_without_granting`, both on rows carrying `hint_shown`.
- **By `settings_page`:** split both success rates by `app`, `list` and `app_details`. A large
  `list` share would justify its own wording, since that page shows every app.

Retry success's denominator is a subset of returns, so it may stay under the 100-event floor for
weeks.

---

## Step 3 — problem checklist

Each rate's definition is fixed so that one report compares with the next. Compute it on every
version together, split the failures by Android version major, then run the newest-version check
(Step 0) on every failure rate — P1 to P9 and P14.

| #   | Problem                       | Rate and breakdown                                                                                                                                                                                                                                                                                                                                                                     |
|-----|-------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| P1  | Delete failures               | `operation_failed` (`operation` delete) carrying `outcome` ÷ (that + `delete_completed` carrying `removed_count`) — rows without those params come from pre-2.6 code. By `source`; break down by `errno` × `outcome` × path and by Android version. Path: `error_type` `partial`, `all_failed`, `structural` or `exception` is the walked delete; a cause label is the one-call delete |
| P2  | Deletes of files already gone | `delete_completed` where `already_absent_count` = `item_count` > 0, ÷ `delete_completed`, by `source`. Analyzer rows clustered at multiples of 100 mean the same rows were deleted twice                                                                                                                                                                                               |
| P3  | Move and copy failures        | rate: `destination_picker_operation_finished` `success` false ÷ all, by `action`. Causes: `operation_failed` for move/copy by `error_type` — the same failures, so never add the two                                                                                                                                                                                                   |
| P4  | Rename failures               | `operation_failed` (`operation` rename) ÷ `rename_confirmed`, by `error_type`                                                                                                                                                                                                                                                                                                          |
| P5  | Extraction failures           | `operation_failed` (`operation` uncompress) by `error_type`. `invalid_zip` ÷ attempts (`invalid_zip` + `uncompress_confirmed` + `uncompress_cancelled` + `password_uncompress_confirmed` + `password_uncompress_cancelled`); the other types ÷ confirms (`uncompress_confirmed` + `password_uncompress_confirmed`); `wrong_password` reported apart                                    |
| P6  | Formats the app can't handle  | `file_opened` for `rar`, `7z`, `gz`, `tar`, `bz2` against `uncompress_completed`; `file_open_failed` by `extension` and `reason`                                                                                                                                                                                                                                                       |
| P7  | Files opened as "any type"    | `file_opened` with `mime_type` `*/*` ÷ all opens; how many of those have `extension` `unknown`                                                                                                                                                                                                                                                                                         |
| P8  | Feedback sending              | `feedback_submit_error` against `feedback_submit_success` (errors repeat with each retry tap)                                                                                                                                                                                                                                                                                          |
| P9  | Viewer errors                 | per viewer, `*_load_error` / `*_read_error` ÷ `*_viewer_opened`; a viewer whose error event is not in this export goes to the data notes instead                                                                                                                                                                                                                                       |
| P10 | Accidental swipes             | `folder_swipe_action_tapped` ÷ (`folder_swiped_left` + `folder_swiped_right`); share of `settings_swipe_*` choices set to `none`                                                                                                                                                                                                                                                       |
| P11 | Picker abandonment            | `destination_picker_closed` ÷ `destination_picker_shown`; navigation steps (`_folder_navigated` + `_navigated_up` + `_storage_selected` + `_breadcrumb_clicked`) per picker shown                                                                                                                                                                                                      |
| P12 | Dialog abandonment            | cancel ÷ (confirm + cancel) for compress, uncompress, password uncompress, rename, create folder, delete; flag anything above ~30%                                                                                                                                                                                                                                                     |
| P13 | Home size timing              | `location_sizes_measured` `duration_ms` p50 / p90 / p99, weighted by `count` (drop durations over an hour — the app was backgrounded); share with `capped_count` > 0; share with `had_placeholder` true                                                                                                                                                                                |
| P14 | Crashes in operations         | `operation_failed` with `error_type` `exception`, by `operation`                                                                                                                                                                                                                                                                                                                       |

---

## Step 4 — sweep for anything the checklists miss

The checklists cover what was known on 2026-10-30. New features bring new failure events, so after
them:

- Take every event whose name contains `failed`, `error`, `cancel`, `denied`, `dismissed`,
  `without` or `close`, and every `operation_failed` `operation` / `error_type` / `errno` value not
  already covered. Compute each against its natural denominator: the matching `_opened`, `_shown`,
  `_confirmed` or `_triggered` event. Names are not uniform (settings dialogs open as
  `settings_*_opened` or `settings_*_dialog_opened`), and some have no match because the screen
  view that would be the denominator is not exported — then use the closest entry event (for
  example, the tap that leads to the screen), and name it.
- Flag a failure rate of 5% or more or an abandonment rate of 30% or more, and run the
  newest-version check (Step 0) on every failure rate flagged.

---

## Step 5 — explain the top findings from the code

Rank every candidate by **severity tier first, then by events affected across all versions** within
a tier — never multiply the two, or a large pile of friction outranks a destructive failure. Tiers,
highest first:

1. Data lost, or a destructive action doing something other than asked.
2. An operation failing or only partly done — a failed delete, a move that left the original behind.
3. The user blocked, confused or misled — a success that looked like nothing happened, a delete of
   files already gone, an error message that names the wrong cause.
4. Friction or a missing feature — abandonment, accidental gestures, unsupported formats.

A failure the newest-version check (Step 0) found mostly on older versions goes to the data notes'
"older versions only" list, not the ranking.

For the top five or so, find out *why* from the code. Hand the lookups to helper agents (the Explore
type), all in one message so they run in parallel. Give each the event, the param values in
question and these questions: which code path emits this value, under what conditions, what the
user sees at that moment, and whether that code changed between releases. Ask for `file:line`
references, and tell them the task is read-only — the repo and the analytics folder alike.

The checkout is often **ahead of what is shipping**: `versionName` may name a release still in
development or only on testers' devices, and the current code may already hold a fix that has not
shipped. Release branches are `release/<major.minor>.0`, and a line's patch releases are later
commits on that same branch. So compare the function on the branch of the newest versions the check
used with `HEAD` to find a fix still unreleased — then the recommendation is "already fixed in the
unreleased X, ship it", not "fix this". When a failure on the "older versions only" list needs
explaining, the same diff between an older line's branch and the newest one says what fixed it.

Label every cause you report:

- **Confirmed in code** — the code path demonstrably produces this outcome.
- **Likely** — consistent with the data and the code, not proven. Say what would prove it (an
  Android-version split, a reproduction, a log).
- **Unknown** — say what is missing.

Check the auto-memory and the code itself for decisions already made before recommending
something: a finding refuted on a device, or a change already merged, should not come back as new.

---

## Step 6 — write the report

Save it as `<input folder>/analytics-report-YYYY-MM-DD.md` with today's date (`-2` if that exists),
in this structure:

```markdown
# Analytics report — YYYY-MM-DD

Data: <file> and the 30 days it covers · release lines and their event counts, all of them pooled
in every figure

## How people use the app

| # | Behaviour | All versions | Reading |

## What to fix or improve

| # | Problem | Evidence (all versions, then the newest versions and the lines they cover, with
denominators) | Cause (confirmed / likely / unknown, file:line) | Recommendation |

## Data notes

Older versions only: each failure the newest-version check moved out of the ranking, with both rates
and the lines the newest covered · events in the code but not sent in the 30 days · Firebase keys
dropped from the saved query this run · truncated names · anecdotal counts · everything counts
events, not users
```

Every number carries its denominator. Say "events" or "times", never "users". The recommendations
say what to change and where, written for a developer who does not know this codebase.

**In chat**, give the report's path and a short summary in the user's usual report format: the
headline, then the few items that need their attention — the top problems — in plain words. The
tables stay in the file.
