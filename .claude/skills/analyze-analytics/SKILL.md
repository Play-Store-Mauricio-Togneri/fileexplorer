---
name: analyze-analytics
description:
  Downloads the File Explorer app's Firebase Analytics data for the last 30 days from BigQuery, through the saved query "Events analyzer", as a parquet of event name, JSON params and count with app_version and os_version in the params, then analyzes it and writes a dated report of how people use the app, what fails or puts them off, what the newest release fixed or broke, and ranked recommendations explained from the code. Use whenever the user asks to analyze, review or summarize analytics, Firebase events, the BigQuery export or usage data, or asks what users do, what to improve or fix, or whether a release fixed something — even if they don't name the file.
---

# Analyzing the analytics export

The export is **aggregated**: one row per event name and parameter combination, with a count. There
are no user IDs and no dates, so every figure is a count of events, never of people, and there are
no funnels. The two dimensions that make it useful are `app_version` and `os_version`, which the
params always carry: they let a finding be judged on the code that is shipping now, and let a
failure be split by Android release.

Aggregated counts are easy to misread. The first analysis of this data (2026-10-30) nearly reported
a 6.9% delete failure rate that was really 4.9%, because 3,000 failures came from a pre-2.6 code
path that has since been fixed; and it nearly called the PDF viewer a failed feature when it only
opens files no other app can. Most of this skill exists to stop those misreadings: interpret every
event against what the current code means by it, and judge every rate on the newest release.

**This is a read-only analysis.** Download the data, read it, read the code, write one report file.
No code edits, no git operations, no builds, no emulator. The only write outside the analytics
folder is the rare update of the saved query described in Step 1.

---

## Step 0 — download, load and validate the file

**Input.** The path the user gave, used as it is. Otherwise
`/home/max/analytics/events-YYYY-MM-DD.parquet` with today's date, downloaded first (below) unless
it already exists. The report is written next to it.

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
the saved query no longer adds them — someone edited it in the console — and the release comparison
is the point of this analysis; a pooled report would quietly reintroduce the misreadings above.

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

**Release lines.** Group `app_version` by major.minor (`2.7.0`–`2.7.3` → `2.7`), sorted
numerically (2.10 after 2.9). A patch inside a line rarely changes behaviour, and grouping keeps
samples large; split by patch only when a metric moves inside a line. A line carrying under 5% of
the export's events is a **test build** — release builds on testers' devices, since debug, emulator
and Test Lab collection are off — and stays out of every comparison and every pool. The repo's
`versionName` in `app/build.gradle.kts` is usually that version, still in development. A release in
its first days can sit under 5% too, so the report's data line names every line treated as a test
build, and the owner can correct it. The **newest line** is the highest line that is not a test
build; the **previous line** is the next one down.

**Sample sizes.** Judge a rate on one line only when its denominator has at least 100 events.
Below that, pool the newest line with the previous ones until it does, say which lines were pooled,
and mark that metric "no comparison" — a pool that includes the previous line has nothing left to
compare against. A count under 20 is anecdotal: report it, but never build a recommendation on it
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

- **A param missing on a row** means the app version that sent it predates the param. Compare on
  rows that carry it.
- **`delete_completed` counts selected items**, not the files inside them.
- **`operation_failed` for deletes speaks two vocabularies.** The walked delete (10+ nodes, with a
  progress dialog) puts the *shape* in `error_type` (`partial`, `all_failed`, `structural`) and the
  cause in `errno`; the one-call delete puts a *cause label* in `error_type` (`not_empty`, `busy`,
  `permission_denied`, …) and the shape in `outcome`. Compare deletes on `errno` and `outcome`.
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

Compute each item on the newest line, with the previous line beside it. Express shares with their
denominator ("40% — 48.6k of 120k"). Skip rows that lack the param an item breaks down by, and say
how many were skipped.

File-type groups for U2, fixed so one report compares with the next:

| Group       | Extensions                                                                                   |
|-------------|----------------------------------------------------------------------------------------------|
| Images      | jpg, jpeg, png, webp, gif, heic, heif, bmp, svg, avif                                         |
| Video       | mp4, mkv, avi, mov, 3gp, webm, flv, m4v, ts                                                   |
| Audio       | mp3, m4a, opus, ogg, wav, aac, amr, flac, wma, mid                                            |
| PDF         | pdf                                                                                          |
| APK         | apk                                                                                          |
| App bundles | xapk, apks, apkm                                                                             |
| Documents   | doc, docx, xls, xlsx, xlsm, csv, ppt, pptx, odt, ods, odp, txt, rtf, md, epub, html, htm, json, xml |
| Archives    | zip, rar, 7z, gz, tar, bz2, xz                                                               |
| No extension | `unknown`                                                                                   |
| Other       | everything else                                                                              |

| #   | Behaviour             | Compute                                                                                                                                                                                                                                                                     |
|-----|-----------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| U1  | Core loop             | `folder_tapped_to_open`, `file_opened`; `file_opened` share by `source`                                                                                                                                                                                                     |
| U2  | File types            | `file_opened` by `extension`, in the groups above; top ten extensions                                                                                                                                                                                                       |
| U3  | Entry points          | `home_location_card_opened` by `location`; `home_storage_card_opened` by `storage`                                                                                                                                                                                          |
| U4  | Cleanup               | `delete_confirmed`; share of `delete_completed` with `item_count` 1; `bottom_sheet_delete` ÷ `bottom_sheet_opened`; `delete_cancelled` ÷ (`delete_confirmed` + `delete_cancelled`)                                                                                          |
| U5  | Organising            | `destination_picker_shown` by `action` (move vs copy); `rename_confirmed`; `create_folder_confirmed`; `folder_long_pressed_to_select`; `folder_toolbar_select_all`                                                                                                          |
| U6  | Search                | opens = `home_search_container_tapped` + `home_search_icon_tapped`; `search_close_without_typing` ÷ opens; `search_filter_kind_changed` by `kind`; search's share of `file_opened`                                                                                           |
| U7  | Favourites, recents   | favourite adds (`bottom_sheet_add_to_favorites` + `folder_context_menu_add_to_favorites`) against `file_opened` from `favorite`; recents' share of opens; `settings_recent_files_tracking` by `enabled`                                                                       |
| U8  | Settings              | each `settings_*` choice event by value against its default — theme, hidden files, recents tracking, swipe actions, second lines, startup screen, home section order (a confirm that keeps the default order is not a change), locations                                     |
| U9  | Sort                  | `folder_sort_by_selected` by `sort_mode` against the default                                                                                                                                                                                                                |
| U10 | Permission onboarding | `permission_grant_button_tapped` by `is_android_11_or_above`; `permission_returned_without_granting`, `permission_dialog_granted` / `_denied`, `permission_permanently_denied` — each per grant tap, split by Android version                                                |
| U11 | Analyzer              | `home_drawer_analyzer_tapped`; `delete_completed` with `source` `analyzer_category`                                                                                                                                                                                         |
| U12 | APK installs          | `apk_install_triggered`; `apk_permission_dialog_shown` → `apk_permission_open_settings` / `_cancelled`; app bundles (`xapk`, `apks`, `apkm`) among `file_opened`                                                                                                             |

---

## Step 3 — problem checklist

Each rate's definition is fixed so that one report compares with the next. Compute it on the newest
line, then the previous, then — for failures — split by Android version major.

| #   | Problem                    | Rate and breakdown                                                                                                                                                                                                                                                                                         |
|-----|----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| P1  | Delete failures            | `operation_failed` (`operation` delete) carrying `outcome` ÷ (that + `delete_completed` carrying `removed_count`) — rows without those params come from pre-2.6 code. By `source`; break down by `errno` × `outcome` × path and by Android version. Path: `error_type` `partial`, `all_failed`, `structural` or `exception` is the walked delete; a cause label is the one-call delete |
| P2  | Deletes of files already gone | `delete_completed` where `already_absent_count` = `item_count` > 0, ÷ `delete_completed`, by `source`. Analyzer rows clustered at multiples of 100 mean the same rows were deleted twice                                                                                                                |
| P3  | Move and copy failures     | rate: `destination_picker_operation_finished` `success` false ÷ all, by `action`. Causes: `operation_failed` for move/copy by `error_type` — the same failures, so never add the two                                                                                                                       |
| P4  | Rename failures            | `operation_failed` (`operation` rename) ÷ `rename_confirmed`, by `error_type`                                                                                                                                                                                                                             |
| P5  | Extraction failures        | `operation_failed` (`operation` uncompress) by `error_type`. `invalid_zip` ÷ attempts (`invalid_zip` + `uncompress_confirmed` + `uncompress_cancelled` + `password_uncompress_confirmed` + `password_uncompress_cancelled`); the other types ÷ confirms (`uncompress_confirmed` + `password_uncompress_confirmed`); `wrong_password` reported apart |
| P6  | Formats the app can't handle | `file_opened` for `rar`, `7z`, `gz`, `tar`, `bz2` against `uncompress_completed`; `file_open_failed` by `extension` and `reason`                                                                                                                                                                         |
| P7  | Files opened as "any type" | `file_opened` with `mime_type` `*/*` ÷ all opens; how many of those have `extension` `unknown`                                                                                                                                                                                                            |
| P8  | Feedback sending           | `feedback_submit_error` against `feedback_submit_success` (errors repeat with each retry tap)                                                                                                                                                                                                             |
| P9  | Viewer errors              | per viewer, `*_load_error` / `*_read_error` ÷ `*_viewer_opened`; a viewer whose error event is not in this export goes to the data notes instead                                                                                                                                                           |
| P10 | Accidental swipes          | `folder_swipe_action_tapped` ÷ (`folder_swiped_left` + `folder_swiped_right`); share of `settings_swipe_*` choices set to `none`                                                                                                                                                                         |
| P11 | Picker abandonment         | `destination_picker_closed` ÷ `destination_picker_shown`; navigation steps (`_folder_navigated` + `_navigated_up` + `_storage_selected` + `_breadcrumb_clicked`) per picker shown                                                                                                                         |
| P12 | Dialog abandonment         | cancel ÷ (confirm + cancel) for compress, uncompress, password uncompress, rename, create folder, delete; flag anything above ~30%                                                                                                                                                                       |
| P13 | Home size timing           | `location_sizes_measured` `duration_ms` p50 / p90 / p99, weighted by `count` (drop durations over an hour — the app was backgrounded); share with `capped_count` > 0; share with `had_placeholder` true                                                                                                  |
| P14 | Crashes in operations      | `operation_failed` with `error_type` `exception`, by `operation`                                                                                                                                                                                                                                          |

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
- Flag a failure rate of 5% or more, an abandonment rate of 30% or more, or a rate that changed by
  50% or more of itself against the previous line when both sides have at least 20 events.
- List events with at least 20 occurrences that appear only in the newest line, and new values of
  the low-cardinality params (`error_type`, `errno`, `reason`, `outcome`, `operation`, `action`,
  `mode`, `kind`) with at least 20. Leave out free-ranging params — `extension`, `mime_type`,
  `locations`, `order` — whose long tails are noise. These are new features or new failure modes,
  and the first report on them sets the baseline.

---

## Step 5 — explain the top findings from the code

Rank every candidate by **severity tier first, then by events affected in the newest line** within
a tier — never multiply the two, or a large pile of friction outranks a destructive failure. Tiers,
highest first:

1. Data lost, or a destructive action doing something other than asked.
2. An operation failing or only partly done — a failed delete, a move that left the original behind.
3. The user blocked, confused or misled — a success that looked like nothing happened, a delete of
   files already gone, an error message that names the wrong cause.
4. Friction or a missing feature — abandonment, accidental gestures, unsupported formats.

A fix shipped in the newest line (the rate dropped against the previous one) goes to Step 7's
"changed" section, not the ranking.

For the top five or so, find out *why* from the code. Hand the lookups to helper agents (the Explore
type), all in one message so they run in parallel. Give each the event, the param values in
question and these questions: which code path emits this value, under what conditions, what the
user sees at that moment, and whether that code changed between releases. Ask for `file:line`
references, and tell them the task is read-only — the repo and the analytics folder alike.

The checkout is often **ahead of the newest line**: when `versionName` is higher, the current code
may already hold a fix that has not shipped. Release branches are `release/<major.minor>.0`, and a
line's patch releases are later commits on that same branch. So compare the function on
`release/<newest>.0` with the previous line's branch to explain a change in the data, and with
`HEAD` to find a fix still unreleased — then the recommendation is "already fixed in the
unreleased X, ship it", not "fix this".

Label every cause you report:

- **Confirmed in code** — the code path demonstrably produces this outcome.
- **Likely** — consistent with the data and the code, not proven. Say what would prove it (an
  Android-version split, a reproduction, a log).
- **Unknown** — say what is missing.

Check the auto-memory and the code itself for decisions already made before recommending
something: a finding refuted on a device, or a change already merged, should not come back as new.

---

## Step 6 — check the previous report

Find the newest `analytics-report-*.md` in the same folder, other than today's. For each of its
recommendations, give a status from this export: **fixed** (the rate dropped in the newest line),
**still open**, or **worse**. A status, not a re-argument. With no previous report, the section says
this one is the baseline.

---

## Step 7 — write the report

Save it as `<input folder>/analytics-report-YYYY-MM-DD.md` with today's date (`-2` if that exists),
in this structure:

```markdown
# Analytics report — YYYY-MM-DD

Data: <file> and the 30 days it covers when downloaded this run · release lines and their event counts · newest <x> vs previous <y> · pooled metrics
and why · test builds left out

## How people use the app
| # | Behaviour | Newest | Previous | Reading |

## What to fix or improve
| # | Problem | Evidence (newest vs previous, with denominators) | Cause (confirmed / likely / unknown, file:line) | Recommendation |

## What changed since the previous release
Fixed, regressed, and new — each with both rates.

## Status of the previous report's recommendations
| # | Recommendation | Status | Evidence |

## Data notes
Events in the code but not sent in the 30 days · Firebase keys dropped from the saved query this run · truncated names · anecdotal counts · everything counts
events, not users
```

Every number carries its denominator. Say "events" or "times", never "users". The recommendations
say what to change and where, written for a developer who does not know this codebase.

**In chat**, give the report's path and a short summary in the user's usual report format: the
headline, then the few items that need their attention — the top problems and anything that got
worse — in plain words. The tables stay in the file.
